# Java 21 translation blockers and hot-path concerns

Scope: `upstream/kusto-query-language/src/Kusto.Language`, excluding `Editor/`.
That is 181 `.cs` files and 68,474 lines.
All paths below are relative to `src/Kusto.Language/` unless noted.
Counts come from regex scans over comment- and string-stripped source. Each count says whether it is exact or approximate.

## Recommendation (TL;DR)

**Stay on Java 21.** None of the hard blockers go away on Java 25.

- **Flexible constructor bodies (JEP 513, Java 25).** No constructor chain needs them. C# already bans `this` in `: base(...)`/`: this(...)` arguments, the same rule as `super(...)` in Java 21. All 42 computed-argument chains translate as inline expressions.
- **Unnamed patterns `_` (JEP 456, final in Java 22).** These are the only Java 22+ feature with real use here. There are 24 `case T _:` labels, 16 of them stacked. That is a few switch statements in two files.
- **Things that need a design decision on any Java version:**
  - The 64 KB method limit, likely hit by `QueryGrammar.Initialize`.
  - Generic-arity type-name overloading, with 11 families.
  - Overloads that clash after erasure, about 15 groups.
  - Reified generics.
  - `out`/`ref` parameters.
  - Lambda capture of mutable locals.
  - Value-type semantics.
  - Stack depth.
- **Valhalla value classes are not in Java 25 either.** Structs become classes on any version.

Target Java 21. Write code that compiles unchanged on 25. Bump later at no cost if wanted.

---

## 1. Constructor chaining with computed arguments

**Counts (exact, from scanning every `) : base(` and `) : this(`):**
- 152 constructor chains in scope.
- 110 pass parameters or constants straight through.
- **42 compute their arguments.** That means object creation, a method call, a conditional, `??`, a lambda, `as`, or interpolation.

**Key fact:** C# rejects `this` and instance members inside a constructor-initializer argument list. Java 21 has the same rule for `super(...)` and `this(...)` arguments. So every C# chain translates one-to-one into a Java 21 explicit constructor call. A Java 25 prologue is never required.

**Field-initializer order was checked.** C# runs derived-class field initializers *before* the base constructor. Java runs them *after* `super()`. This only matters when a base constructor calls a virtual member that reads a derived field initializer. Only three in-scope constructors call a virtual member:
- `SyntaxList` (Syntax/SyntaxList.cs:27, `this.Init()`)
- `SeparatedElement` (Syntax/SeparatedElement.cs:26)
- `CustomNode` (Syntax/CustomNode.cs:50)

Their subclasses, `SyntaxList<T>` (:78) and `SeparatedElement<T>` (:67), declare no fields. Generated syntax nodes call `this.Init()` as the *last* statement of the leaf constructor (Kusto.Language.Generators/SyntaxNodeGenerator.cs:458-479). `CommandGrammar(GlobalState)` calls the virtual `CreateCommandParsers` (Parser/CommandGrammar.cs:46, declared :499). It has no subclasses and is out of scope anyway. **No ordering hazard was found.**

### The 42 computed chains

Legend:
- **Inline**: a plain Java 21 expression works as the `super`/`this` argument.
- **Helper**: works inline, but a `private static` helper reads better or avoids double evaluation.
- **J25**: Java 25 would be materially better. **No site is J25.**

| # | Site | Argument shape | Class |
|---|------|----------------|-------|
| 1 | Parser/LexicalToken.cs:46 | `diagnostic != null ? new[]{diagnostic} : null` | Inline |
| 2 | Parser/QueryParser.cs:42 | `new ArraySource<LexicalToken>(tokens)` | Inline |
| 3 | Parser/TokenParser.cs:22 | `new StringTable()` | Inline |
| 4 | Parser/Combinators/Parsers/MatchParser.cs:21 | lambda captures ctor param `predicate` | Inline |
| 5 | Parser/Combinators/Parsers/MatchParser.cs:84 | 2 lambdas capturing params | Inline |
| 6 | Parser/Combinators/Parsers/ConvertParser.cs:40 | multi-statement lambda with try/finally using **static generic** `s_inputListPool` (:108) | Helper (the static generic pool needs a raw type; see §G) |
| 7 | Parser/Combinators/Parsers/MapParser.cs:16 | `Node.From(keyValuePairs)` | Inline |
| 8 | Utils/SafeList.cs:27 | `items != null ? new List<T>(items) : new List<T>(0)` | Inline |
| 9-22 | Symbols/FunctionSymbol.cs:138, 143, 148, 153, 158, 163, 168, 173, 178, 183, 188, 193, 198, 203 (14) | `new[]{ new Signature(...) }`, some with `Parameter.ParseList(...)` | Inline |
| 23 | Symbols/Parameter.cs:169 | `new[]{ type ?? ScalarTypes.Unknown }` | Inline |
| 24 | Symbols/Parameter.cs:214 | same | Inline |
| 25 | Symbols/OptionSymbol.cs:37 | extension call `type.CheckArgumentNull(nameof(type))` becomes a static call | Inline |
| 26 | Symbols/DynamicSymbol.cs:30 | `$"dynamic"` (a constant) | Inline |
| 27 | Symbols/GraphModelSymbol.cs:61 | 3 LINQ `Select` projections behind null checks | Helper (readability) |
| 28 | Symbols/GraphModelSymbol.cs:75 | conditionals plus array creation | Inline |
| 29 | Symbols/GraphSymbol.cs:61 | conditionals plus `TableSymbol.From(...)` | Inline |
| 30 | Symbols/Signature.cs:223 | `declaration.Expression?.ResultType as TypeSymbol` | Helper (`?.` plus `as` would evaluate twice inline) |
| 31 | Symbols/Signature.cs:228 | same | Helper |
| 32 | Symbols/OperatorSymbol.cs:25 | `kind.ToString()` | Inline (`kind.name()`, if Java enum constant names mirror C#) |
| 33 | Symbols/OperatorSymbol.cs:42 | `new[]{ new Signature(resultType.CheckArgumentNull(...)) }` | Inline |
| 34 | Symbols/TableSymbol.cs:64 | `TableSymbol.From(schema).Columns` | Inline |
| 35 | Symbols/TableSymbol.cs:538 | `TableSymbol.From(columns).Columns` | Inline |
| 36 | Symbols/TableSymbol.cs:576 | same | Inline |
| 37 | Binder/SemanticInfo.cs:136 | `diagnostic != null ? new List<Diagnostic>{d}.AsReadOnly() : Diagnostic.NoDiagnostics` | Inline |
| 38 | Binder/SemanticInfo.cs:141 | same | Inline |
| 39 | Syntax/SyntaxList.cs:82 | `elements.ToArray()` (IEnumerable<TElement> to array) | Helper (generic array creation; pass `SyntaxElement[]`) |
| 40 | Syntax/CustomNode.cs:59 | static `GetDefaultShape(elements.Length)` | Inline |
| 41 | Syntax/SyntaxNode.cs:197 | `new TokenName(nameToken)` | Inline |
| 42 | Syntax/SyntaxNode.cs:212 | same | Inline |

**Totals:** 37 Inline, 5 Helper (rows 6, 27, 30, 31, 39), **0 J25**. No site needs instance state. No site has several `out` values.

---

## 2. Generics over primitives and the `TInput` question

### Headline: no hot-path generic is instantiated over `char`

- **The lexer does not use the combinators.** `TokenParser` (Parser/TokenParser.cs) is hand-written over `string` plus `int` positions (`Parse`, :74-200). Character access is `text[pos]`, which becomes Java `charAt`. There is no boxing.
- **The default query parser does not use the combinators either.** `QueryParser` (Parser/QueryParser.cs) is hand-written recursive descent over `Source<LexicalToken>`, and `LexicalToken` is a reference type.
- **The combinator stack is only ever instantiated with `TInput = LexicalToken` in scope.** Occurrences per file:

  | File | Occurrences |
  |------|-------------|
  | Parser/QueryGrammar.cs | 121 |
  | Parser/CommandGrammar.cs | 113 |
  | Parser/SyntaxParsers.cs | 56 |
  | Parser/PredefinedRuleParsers.cs | 51 |
  | Parser/Combinators/ParserVisitors.cs (`IsParentVisitor`, :89) | 23 |
  | Parser/QueryParser.cs | 9 |
  | KustoCode.cs | 3 |

- **`Parser<char>`/`Parsers<char>` live only in a few places:**
  - Parser/CharScanners.cs
  - Parser/ScannerExtensions.cs
  - Parser/Combinators/ParserExtensions.cs
  - Parser/Combinators/TextSource.cs
  - the char-only helpers inside `Parsers<TInput>`: Parsers.cs:85, 640, 657, 1452, 1458, 1464

  Their only consumer is `Editor/Kusto/Analyzers/AvoidUsingHasWithIPv4Strings.cs` (:11-12, :92-102), which is out of scope.

**Where the combinators are on the in-scope path:**
1. `KustoCode.Create` always builds `QueryGrammar.From(globals).QueryBlock` (KustoCode.cs:217). It is stored as `KustoCode.Grammar`.
2. `ParserKind.Grammar` parses with it (KustoCode.cs:225-227).
3. **`QueryParser` falls back to `StackSafeParser` over `QueryGrammar` once nesting passes 300** (QueryParser.cs:141-200). This makes the combinators and `SafeParse.cs`/`SafeScan.cs` required for correctness on deep inputs.

`PartialParser.cs` is used only by `CommandGrammar` (:57, :65) and Editor. It is out of scope.

### How pervasive `TInput` is (exact)

| Measure | Value |
|---|---|
| Files in Parser/Combinators | 37 (7,488 lines) |
| Files that mention `TInput` | 32 of 37 |
| `TInput` token occurrences | 1,069 |
| Type declarations parameterised over `TInput` | 39 (Parser.cs:14, :224; ParserVisitors.cs:10, :36, :62; 22 parser classes in Parsers/; ArraySource, LimitSource; plus private helpers: SafeScan.cs:24, :31, SafeParse.cs:24, :31, Describer.cs:23, PartialParser.cs:51) |
| `Source<T>` | 1 more (Source.cs:9) |
| Public static members of `Parsers<TInput>` | 84 (Parsers.cs:13-1497) |
| Abstract `Visit*` methods across the 3 `ParserVisitor` bases | 69 |
| `TInput` outside Combinators | 0 in scope (only `Editor/Kusto/ParserPath.cs`, `AnnotatedParserFinder.cs`) |

### Proposed Java shape

**Keep `Parser<TInput>` generic. Do not specialise to `char`.**
- With `TInput = LexicalToken` there is nothing to box.
- A `CharParser` hierarchy would fork 39 types and 84 factory methods for one Editor analyzer.
- Port the char helpers as `Parser<Character>` (they are cold), or defer them with the Editor.
- `TextSource.Peek` returning `Character` hits the JDK cache for code points ≤127, so it does not allocate.

**What does need a decision for the mirror:**
- **`using static Parsers<LexicalToken>`.** Used in QueryGrammar.cs:10, SyntaxParsers.cs:13, PredefinedRuleParsers.cs:9 and CommandGrammar.cs:10.
  - Java cannot `import static` from a parameterised type.
  - Turning each member into a static generic `<TInput>` method breaks inference for implicitly typed lambdas such as `Match(t -> t.Kind == X)`. Witnesses like `Parsers.<LexicalToken>Match(...)` would be needed. That is about 2,400 call sites in `Initialize` alone.
  - The static field `Parsers<TInput>.Any` (Parsers.cs:25) cannot use `TInput` in Java.
  - **Recommended:** mirror `Parsers<TInput>` as a Java class of static generic methods, plus a generated non-generic facade `LexicalTokenParsers`. The facade has the same 84 member names with `TInput` fixed. Grammar files then `import static ...LexicalTokenParsers.*`. Member order and names still mirror.
  - **Alternative:** make `Parsers<TInput>` an instance class and have the grammar classes extend `Parsers<LexicalToken>`. This adds a base class C# does not have.
- **`ParseResult<TOutput>`** is a struct (ParseResult.cs:3). In Java it allocates once per `Parse(source, start)` call. This only affects the grammar and fallback path, which is cold. Keep it as a small final class.

### Actual primitive and boxing hot spots (outside Combinators)

| Site | Issue | Java shape |
|---|---|---|
| Utils/SubstringMap.cs:145-205 (`ArrayCharMap` uses `KeyValuePair<char,Node>[]`; `DictionaryMap` uses `Dictionary<char,Node>`) | Keyword trie used **for every token** (TokenParser.cs:120, :155, :407, :781; maps at :398, :802, :1283) | Parallel `char[]`/`Node[]` arrays, plus a 128-slot ASCII array for wide nodes. Keep the class names. |
| Utils/TextKeyedDictionary.cs:120 (`struct Key`) via Utils/StringTable.cs:17 | Interning **per identifier token** (`new TokenParser()` creates a `StringTable`, TokenParser.cs:22, :55). A Java `HashMap<Key,..>` lookup allocates a `Key` every time. | Open-addressing table keyed on `(text, start, len)` with no key object |
| KustoCode.cs:71, :180-195, :441-455; Parser/TextFacts.cs:384-458, :496 | `List<int>` token and line starts plus `BinarySearch` | `int[]` or an `IntList`. `Arrays.binarySearch` returns the same `~insertionPoint` encoding as .NET. |
| `HashSet<SyntaxKind>` / `Dictionary<SyntaxKind,...>` (QueryParser.cs:447, :2439; TokenParser.cs:1280; SyntaxParsers.cs:153, :550) | Enum keys | `EnumSet`/`EnumMap` |
| 53 `Func<…, bool>` types | `Function<T,Boolean>` would unbox | Map `Func<T,bool>` to `Predicate<T>` (or a project `Func1Bool`) |
| Utils/SubstringMap.cs:45 `NoValue = KVP("", default(TValue))` with `TValue = bool`/`SyntaxKind` | `default(T)` is `false`/`None` in C# but `null` in Java | Safe here: every caller checks `Key.Length` first (TokenParser.cs:121, :156, :408, :782) |

---

## 3. C# features with no direct Java analogue

The counts are from regex scans over comment- and string-stripped source. "≈" means heuristic.

| Feature | Count | Representative site | Java 21 mapping |
|---|---|---|---|
| Default interface members | **0** (no `interface` declared in scope at all) | — | — |
| `in` parameters | 0 | — | — |
| `readonly struct` | 1 | Utils/Interlocked.cs:102 (inside the unused `CacheLineSeparated`) | Drop. `CacheLineSeparated` has no users. |
| `ref struct`, Span/ReadOnlySpan/Memory, stackalloc, unsafe/fixed | 0 | (`AllowUnsafeBlocks` is set in the csproj but unused) | — |
| `init` accessors, records, `with` | 0 | — | — |
| Switch expressions | 0 | — | — |
| Switch statements | 153 | QueryParser.cs (42) | direct |
| `is Type x` declaration pattern | ≈409 | FunctionHelpers.cs:49 | Java 16 `instanceof` pattern. 8 sites use `is SyntaxKind k`/`is SymbolKind` on a non-null enum expression (e.g. QueryParser.cs:2982); rewrite those as a plain local assignment. |
| `case Type x:` patterns | ≈113 | KustoCode.cs:268 | Java 21 pattern switch (JEP 441) |
| `case Type _:` discards | 24 (16 stacked as fall-through labels) | Parser/KustoFacts.cs:1361-1370, Symbols/Symbol.cs:272-273, Binder/Binder_NodeBinder.cs | **Java 21: fall-through into a pattern label is illegal.** Rewrite as `if (x instanceof A \|\| x instanceof B)`. Java 22+: `case A _, B _ ->`. |
| `is null` / `is not` / property patterns / `when` guards | 0 | — | — |
| Local functions | 13 | Parser/QueryGrammar.cs:895, 913, 1034, 1051, 1059, 1534, 1714, 1721, 1727, 1734, 1737, 1957; Functions.cs:3510 | Lambdas held in local functional variables. All are declared before first use (checked). |
| Named arguments | ≈1,186 | Functions.cs:27 (`minOccurring: 0`). Top names: `minOccurring` 234, `source` 105, `oneOrMore` 78, `maxOccurring` 78 | Positional arguments. Keep overloads, or use a `Parameter` builder for the 12-optional-parameter constructor. |
| Optional parameters | ≈572 | QueryParser.cs (83), SyntaxParsers.cs (50) | Overloads, or explicit defaults at call sites |
| `params` arrays | 70 | FunctionHelpers.cs:31; Parsers.cs (10) | Varargs. Generic varargs (`params Parser<TInput,TOutput>[]`) need `@SafeVarargs`. |
| Extension methods | 93 declarations | Utils/ListExtensions.cs (21), Symbols/TypeFacts.cs (19) | Static utilities. Call sites change from `x.Foo()` to `Ext.Foo(x)`. |
| Operator overloading | 0 | — | — |
| Implicit conversions | 4 | Binder/FunctionCallResult.cs:25 (`TypeSymbol` to `FunctionCallResult`, returned implicitly by `GetFunctionCallResult` paths, Binder_FunctionCalls.cs:409/437/1137/1808); Utils/Optional.cs:17 (used for `GlobalState.With(...)`, GlobalState.cs:257-266); Utils/Cancellation.cs:30; Parser/Combinators/PartialParser.cs:959 (out of scope) | Explicit `new X(...)` at each use |
| Declared variance (`in T`/`out T`) | 0 | — | — |
| **Reliance on BCL covariance** (`IReadOnlyList<Base>`/`IEnumerable<Base>` parameters receiving subtype lists) | ≈290 typed sites: `IReadOnlyList<TypeSymbol>` 50, `<Expression>` 50, `<Diagnostic>` 38, `<Symbol>` 33, `<TableSymbol>` 24, `IEnumerable<Symbol>` 17, … | Binder/Binder_Misc.cs:230, :269 | Use `List<? extends X>` in parameter positions. This is a mechanical rule. |
| `#nullable` / nullable reference annotations | 0 | — | — |
| Nullable value types (`int?`, `bool?`, `SyntaxKind?`, `CompletionKind?`) | ≈38 | Parser/SyntaxParsers.cs:150 | Boxed references |
| String interpolation | 148 | Diagnostics/DiagnosticFacts.cs (87) | Concatenation or `String.format`. No `{x:fmt}` specifiers were found. |
| `goto` | 1 | Syntax/SyntaxElement.cs:1030 (`goto retry`, label at :1016) | Labeled `while (true)` / `continue` |
| `out` parameters | 46 declarations; ≈291 argument uses (147 `out var`); 95 `.TryGetValue(` calls | Binder/Binder_FunctionCalls.cs:696 `TryGetLiteralValue<T>(…, out T)` | `Map.get` plus a null check for dictionaries. A small `Out<T>` holder, or a nullable return, for the 46 declared methods. |
| `ref` parameters | 12 declarations (Parser/TextFacts.cs:557-606, Parser/KustoFacts.cs:975, :988, Symbols/ParameterLayouts.cs:130); plus ≈30 `Interlocked(ref field)` | | `int[]` or a mutable holder. See §4 for Interlocked. |
| Iterators (`yield`) | 7 | Parser/SyntaxParsers.cs:628, Binder/ColumnMap.cs (4), Syntax/SyntaxFacts.cs (2) | Eager list or hand-written `Iterator` |
| `?.` / `??` | 187 / 462 | QueryParser.cs (228 `??`) | Explicit null checks |
| `nameof` | 109 | Symbols/Signature.cs (21) | String literals |
| ValueTuple | 1 method plus 2 deconstructions | Parser/Combinators/PartialParser.cs:595, :609, :688 (out of scope) | Small record/class |
| Custom delegate types | 5 | Parser/Combinators/Parsers/SourceProducer.cs:16, SourceConsumer.cs | `@FunctionalInterface` |
| Indexers `this[...]` | 3 | Syntax/SyntaxList.cs:37, :110; Utils/SafeList.cs:33 | `get(int)` |
| `new` member hiding | 11 | Parser/Combinators/Parser.cs:234-249; Syntax/SyntaxList.cs:110, :112, :125; Syntax/SeparatedElement.cs:73; Syntax/SyntaxNode.cs:35; Syntax/SyntaxToken.cs:74; Symbols/TupleSymbol.cs:38 | Covariant overrides. **Exception:** `SyntaxList<T>.GetEnumerator()` returning `IEnumerator<TElement>` (:112) cannot override the base `IEnumerator<SyntaxElement>` (:44) because generics are invariant. The base must return `Iterator<? extends SyntaxElement>`. |
| `[Flags]` enums | 9 | Symbols/SymbolMatch.cs:9 (public, OR-combined), FunctionBodyFacts.cs:314, KustoCode.cs:494, Symbols/ScalarSymbol.cs:85, Symbols/TableSymbol.cs:26 (`: ushort`), Symbols/TableState.cs:12, Symbols/FunctionSymbol.cs:60, Syntax/SyntaxElement.cs:178, Syntax/SyntaxNode_Semantics.cs:95 | `int` constants holder (keeps the bit math) or `EnumSet` |
| Structs | 16 (8 in scope and live) | Parser/Combinators/ParseResult.cs:3, OffsetValue.cs:6, RightParser.cs:8, Parsers.cs:1348 (`LeftValue`), :1506 (`ElementAndSeparator`), Binder/FunctionCallResult.cs:17, Utils/Optional.cs:5, Utils/Cancellation.cs:7, Utils/SafeList.cs:102, Utils/TextKeyedDictionary.cs:120 | Immutable final classes. None rely on mutable-copy semantics (all have get-only state). |
| `#if` | 18 | Utils/Interlocked.cs:21, Syntax/SyntaxToken.cs:641, … | Take the `!BRIDGE` / non-DEBUG branch |
| Generic constraints `where T : …` | 36 | Syntax/SyntaxElement.cs (11) | Bounds. `where T : new()` (KustoCache.cs:35, Source.cs:46) needs a `Supplier<T>`. |
| `lock` | 8 | Binder/Binder_API.cs:170, 289, 316, 351, 385; Binder/Binder_FunctionCalls.cs:2030; Utils/MostRecentlyUsedCache.cs:32, :59 | `synchronized` |
| `checked`/`unchecked` | 3 | Utils/TextKeyedDictionary.cs:153, :172; Utils/ListExtensions.cs:425 | Java int arithmetic already wraps |
| Unsigned types | 1 | Symbols/TableSymbol.cs:26 (`enum : ushort`) | int |
| `decimal` | 5 | Syntax/SyntaxToken.cs:621-634; Utils/ValueComparer.cs:61 | `BigDecimal`. `Decimal.MinValue`/`MaxValue` must be reproduced as constants. |

### Java-specific structural blockers the feature list does not capture

**A. JVM 64 KB bytecode-per-method limit (highest risk).**
- **`QueryGrammar.Initialize`** (Parser/QueryGrammar.cs:119 to about 3680) is a single method:
  - 3,561 lines
  - 360 locals (more than 255 locals forces `wide` load/store instructions)
  - about 2,387 calls, 393 lambdas, about 10,365 identifier references
- A javac spike scaled from a synthetic method (63 KB of source compiled to 27.4 KB of bytecode) puts it at **about 35-56 KB**. Expanding named and optional arguments into positional ones pushes it higher. **Plan to split it.** Turn the forward/core locals into fields, and break `Initialize` into region methods that follow the existing `#region` boundaries.
- **`Functions.cs` static initializer**: 426 fields, about 1,362 `new` expressions. A synthetic `<clinit>` with 1,368 constructions compiled to **27.6 KB**, and token-weighted estimates are 24-31 KB. That is under the limit, with less than 2× headroom. If overloads are replaced by fully positional 12-argument `Parameter` calls, move each field's construction into a `private static` factory.
- **No problem**: Aggregates.cs (≈4-5 KB), PlugIns.cs (≈5 KB), Operators.cs (≈7 KB), QueryOperatorParameters.cs (≈3 KB), and the SyntaxFacts static constructor (605 `SyntaxData` entries, Syntax/SyntaxFacts.cs:44-797).

**B. Lambdas capturing mutated locals.**
- QueryGrammar.cs:121-136 declares 16 `…Core = null` locals. `Forward(() => XCore)` captures them (17 sites), and they are assigned later.
- This is legal in C# because closures capture variables. It is a Java compile error because captured locals must be effectively final.
- Fix: one-element holders or fields. Doing (A) with fields fixes both.
- CommandGrammar.cs:32-33 has 2 more, but it is out of scope.

**C. Generic-arity type-name overloading (11 families).** Java cannot declare `Foo<T>` and `Foo<T,U>` in one package. Each family needs a naming rule, for example a numeric suffix for the higher arity. That rule must be applied consistently to keep the mirror predictable.

| Family | Declarations |
|---|---|
| `Parser` | Parser.cs:14, :224 |
| `ParserVisitor` | ParserVisitors.cs:10, :36, :62 |
| `BestParser` | Parsers/BestParser.cs:9, :105 |
| `FirstParser` | Parsers/FirstParser.cs:8, :95 |
| `IfParser` | Parsers/IfParser.cs:10, :80 |
| `MatchParser` | Parsers/MatchParser.cs:11, :69 |
| `SyntaxList` | Syntax/SyntaxList.cs:13, :78 |
| `SeparatedElement` | Syntax/SeparatedElement.cs:9, :67 |
| `SyntaxVisitor` | Syntax/SyntaxVisitor.cs:10, :39 |
| `DefaultSyntaxVisitor` | Syntax/SyntaxVisitor.cs:21, :46 |
| `GlobalStateProperty` | GlobalState.cs:1198, :1208 |

**D. Overloads that clash after erasure (about 15 groups need distinct Java names):**
- **Parsers.cs.** `Convert` at :79, :85, :110 all erase to `(Parser, Function)`. Also `Produce` :783/:789 and `Map` :558/:564.
- **Parser/SyntaxParsers.cs.** `CreateMissingToken` :33/:54, `Token` :150/:309, `RequiredToken` :372/:384 and `GetCompletionItems` :450/:456. In each pair one takes `IReadOnlyList<SyntaxKind>` and the other `IReadOnlyList<string>`.
- **Parser/QueryParser.cs.** `ParseToken` :320/:414, same pattern.
- **Symbols/Signature.cs.** `GetArgumentParameters` :353/:367 (`IReadOnlyList<Expression>` vs `IReadOnlyList<TypeSymbol>`).
- **Symbols/ParameterLayout.cs.** The same `GetArgumentParameters` split at :16/:21, **virtual**, overridden across Symbols/ParameterLayouts.cs (:57/62, :105/122, :317/329, :477). The rename must cover the whole hierarchy.
- **Binder/Binder_TablesAndColumns.cs.** `GetCommonColumns` :366/:387 (`List<Symbol>` vs `List<ColumnSymbol>`).
- **Binder/Binder_Misc.cs.** `AddDeclarationsToLocalScope` :450/:459, `BindParameterDeclarations` :476/:490, `BindColumnDeclarations` :532/:541 and `CheckQueryOperatorParameters` :1278/:1294. Each pair differs only in the `SyntaxList<…>` element type.

**E. Reified generics.**
- `typeof(T)`: 11 sites.
  - Type-keyed caches: KustoCache.cs:37-64 and Parser/Combinators/Source.cs:48-75 (`SourceCache`). All keys are non-generic types (`GlobalBindingCache`, `CommandGrammar`, `CachedGrammar`, `MemoizedInfo`), so `Class<T>` keys are safe.
  - Syntax/SyntaxList.cs:93 `ElementType => typeof(TElement)`.
- `new T()`: 2 sites. `new T[]`: 4 sites (Utils/ObjectPool.cs:19; Syntax/SyntaxList.cs:97, :130, :140).
- `is T` on a type parameter: 12 sites. Syntax/SyntaxElement.cs:480-668 covers `GetFirstAncestor<T>`, `GetDescendants<T>` and the rest. Also Symbols/SymbolMatch.cs:224, Syntax/SyntaxExtensions.cs:21, Binder/Binder_FunctionCalls.cs:699.
- These need a `Class<T>` argument. That changes about 80 in-scope call sites: `GetFirstAncestor<>` 23, `GetFirstAncestorOrSelf<>` 22, `GetDescendants<>` 21, `GetFirstDescendant(OrSelf)<>` 11, `GetDescendantsOrSelf<>` 4, `GetParameterLiteralValue<>` 6, `TryGetLiteralValue<>` 2. Add 30 `OfType<>` and 19 `Cast<>` LINQ calls.
- `default(T)` has 68 sites. Review those where T can be a value type, e.g. `GetParameterLiteralValue<bool?>` (Binder_TreeBinder.cs:109) and `TryGetLiteralValue<bool>` (Binder_FunctionCalls.cs:604).

**F. Generic static fields.** C# keeps one copy per closed type. Java keeps one copy per class. Fifteen sites are affected (e.g. ListPrimaryParser.cs:15, ConvertParser.cs:108, SafeParse.cs:26, SafeScan.cs:26, Parsers.cs:25, :530, :799, Utils/EmptyReadOnlyList.cs:8, Utils/SafeList.cs:133, Utils/SubstringMap.cs:45, Utils/ListExtensions.cs:386).
- Pools and empty singletons work as raw shared statics with unchecked casts.
- **One semantic change:** `[ThreadStatic] static int s_callDepth` in `ForwardParser<TInput,TOutput>` (Parsers/ForwardParser.cs:48-50). In C# it counts per `(TInput,TOutput)` pair. In Java it becomes one `ThreadLocal` shared by all instantiations, so the `MaxCallDepth = 30` fallback fires earlier. To mirror exactly, key the counter by output type; otherwise accept and document the difference.

**G. Generated syntax nodes are not checked in.**
- `Syntax/CodeGen/GeneratedSyntaxNodes.tt` (14 lines) includes `Kusto.Language.Generators/SyntaxNodeGenerator.cs` (681 lines) and `SyntaxNodeInfos.cs` (3,343 lines, 226 node infos).
- The Java port needs its own generator, or a re-targeted C# one, before any of the Syntax, Binder or Parser code compiles.
- Generated constructors use optional parameters (`= default`) and forward plain parameters to `base(...)`. They raise no §1 concerns.

**H. Dictionary iteration order.** .NET `Dictionary`/`HashSet` enumerate in insertion order (an implementation detail when nothing is removed). Java `HashMap` does not. There are 70 `new Dictionary/HashSet` sites and 51 `.Keys/.Values` enumeration sites. Default to `LinkedHashMap`/`LinkedHashSet` so symbol and diagnostic order matches.

---

## 4. BCL dependencies

| Namespace | In-scope usage | Notes |
|---|---|---|
| System.Collections.Immutable | **None** | — |
| System.Linq | `using` in 148 files. About 350 operator calls (`Select` 83, `Where` 39, `ToList` 37, `Any` 37, `ToArray` 33, `Concat` 33, `FirstOrDefault` 30, `Distinct` 9, `Max` 8, `All` 8, `Min` 7, `OrderBy` 6, `SelectMany` 5, …) plus `OfType<>` 30 and `Cast<>` 19. Heaviest in-scope files: PlugIns.cs 25, Parser/QueryGrammar.cs 21, Binder/Binder_NodeBinder.cs 20, Parser/SyntaxParsers.cs 17, Binder/Binder_Misc.cs 14, GlobalState.cs 13. | **Do not map to `Stream`.** Streams are single-use, and LINQ `IEnumerable`s can be enumerated more than once. Use a small mirror `Linq` static utility returning eager `List`s or re-iterable `Iterable`s. Parser/Combinators/PartialParser.cs has 29 calls but is out of scope. |
| System.Text.RegularExpressions | **None** (the "Regex" hits are KQL function and keyword names, e.g. Functions.cs:174) | — |
| System.Numerics | **None** | — |
| System.Globalization | No `using`. **Implicit culture-sensitive APIs, listed after this table.** | — |
| System.Threading | Listed after this table | — |
| System.Reflection | Listed after this table | No red flags for a mirror port |

### System.Globalization: implicit culture-sensitive APIs

- **Literal value parsing** uses the current culture: `Int32/Int64/Double/Decimal.TryParse` at Syntax/SyntaxToken.cs:584, 600, 616, 632, 667. Java: `Long.parseLong`/`Double.parseDouble` are invariant, which matches the intended behavior.
- **`TimeSpan.TryParse`** at SyntaxToken.cs:643, **`DateTime.TryParse`** at :731 and **`Guid.TryParse`** at :740.
  - `TimeSpan` and `DateTime` parsing are very permissive and have **no JDK equivalent**. They need a hand port of the accepted formats.
  - The value types `TimeSpan`/`DateTime` (100 ns ticks) and `decimal` leak into `ValueInfo`/`ConstantValue`, Utils/ValueComparer.cs:19-75.
- **`bool.TryParse`**: PlugIns.cs:394, 425, 456, 486.
- **`string.Compare` with no `StringComparison`** compares by culture: Parser/Combinators/TextSource.cs:63, :68; Parser/KustoFacts.cs:1304, 1310, 1322, 1345; Parser/SyntaxParsers.cs:188; Syntax/SyntaxToken.cs:552, 563; Utils/StringAndNumberComparer.cs:114; Binder/Binder_FunctionCalls.cs:824-825 (`ignoreCase:true`). Java `regionMatches` is ordinal. That is almost certainly the intended semantics; note it as a deliberate deviation.
- **Culture-sensitive `ToLower`/`ToUpper`**: Parser/Combinators/Parsers.cs:644-645, 666-667; PlugIns.cs:594, 683; Parser/SyntaxParsers.cs:99; Syntax/SyntaxToken.cs:244. Use `toLowerCase(Locale.ROOT)`.
- **Character classes**: `char.IsDigit` (16) and `char.IsLetter` (2) map to `Character.isDigit`/`isLetter` (same Unicode categories). TextFacts has its own `IsLetterOrDigit` (Parser/TextFacts.cs:371).

### System.Threading

- **`using System.Threading`** appears in Utils/HashSetExtensions.cs:7, Utils/ObjectPool.cs:4 and Parser/TriviaFacts.cs:4. `using System.Threading.Tasks` appears in 13 files but is unused (IDE leftovers, e.g. ServerKinds.cs:5).
- **Real uses:**
  - **`Interlocked.CompareExchange`/`Exchange(ref …)`, 30 sites.** Most are lazy-init publication:
    - GlobalState.cs: 616, 667, 694, 715, 744, 772, 862, 921, 939, 1012, 1043, 1078, 1190
    - KustoCode.cs: 378, 394, 452
    - Symbols: ClusterSymbol.cs:109, DynamicSymbol.cs:113, PatternSymbol.cs:111, TableSymbol.cs:343, GraphModelSymbol.cs:98
    - Syntax: SyntaxElement.cs:196, SyntaxNode.cs:289
    - Parser: Combinators/Source.cs:28, QueryGrammar.cs:58, CommandGrammar.cs:404, :438
    - Utils: SafeList.cs:52, :72, ObjectPool.cs:29, :52
  - **Java mapping for Interlocked:**
    - Java has no `ref`. For the lazy-init sites the race is benign. Use a `volatile` field with a plain write, or a `VarHandle` per field to keep CAS semantics.
    - `ObjectPool` (array slots): `AtomicReferenceArray`.
    - `SafeList._isOwner` (ownership handoff, must be a true CAS): `AtomicInteger` or a `VarHandle`.
  - **`[ThreadStatic]`**: Parsers/ForwardParser.cs:48. Map to `ThreadLocal` (see §3F for the generic-static subtlety).
  - **`ConcurrentDictionary`**: Utils/ThreadSafeDictionary.cs:12-21. Map to `ConcurrentHashMap`. Note `GetOrAdd`/`AddOrUpdate` factory semantics (Binder_FunctionCalls.cs:2110).
  - **`lock`**: 8 sites (see §3). The global `GlobalBindingCache` lock serialises binding per `GlobalState` (Binder/Binder_API.cs:165-170).
  - **`CancellationToken`**: wrapped in Utils/Cancellation.cs:7-36. In Java use a `BooleanSupplier`, i.e. the BRIDGE branch at :9-20.

### System.Reflection

- **Only `Properties/AssemblyInfo.cs:1`** (`InternalsVisibleTo`, :5). Drop it.
- Runtime type use only:
  - `GetType()` equality in Utils/ValueComparer.cs:19-75 and Symbols/TableSymbol.cs:451. Map to `getClass()`.
  - `Enum.GetValues<SyntaxKind>()` in Syntax/SyntaxFacts.cs:776/778. Map to `SyntaxKind.values()`. Note that `(int)kind` indexing at :789-896 becomes `ordinal()`. SyntaxKind has no explicit values except `None`, so declaration order must be mirrored exactly.
  - The type-keyed caches from §3E.
  - `[DebuggerDisplay]` attributes. Drop them.
- **No `Activator`, `GetMethod`, `GetProperty`, `MethodInfo` or attribute scanning.** Nothing reflection-driven.
- `System.Runtime.InteropServices` (`StructLayout`) appears only in the dead `CacheLineSeparated` (Utils/Interlocked.cs:74-114).

---

## 5. Recursion depth

### Parser: per-level recursion, with a guard and a fallback

- **`QueryParser` recurses once per nesting level.** One parenthesised level goes through 18 frames:
  `ParseParenthesizedExpression` (2481) → `ParseExpression` (6429) → `ParsePipeExpression` (6374) → `ParseUnnamedExpression` (3236) → `StackSafeParse` (166) → lambda → `ParseUnnamedExpression_Unsafe` (3242) → `ParseLogicalOrExpression` (3221) → `ParseLogicalAndExpression` (3206) → `ParseEqualityExpression` (3067) → `ParseRelationalExpresion` (3036) → `ParseAdditiveExpression` (3007) → `ParseMultiplicativeExpression` (2976) → `ParseStringOperation` (2878) → `ParseUnaryPlusOrMinusExpression` (2860) → `ParseFunctionCallOrPath` (2829) → `ParsePrimaryExpression` (1934) → back to the start.
  Function-call and argument nesting adds list-parsing frames.
- **Guard.** `const int MaxDepth = 300` (QueryParser.cs:141). `_depth` is counted only inside `StackSafeParse`, which is used at QueryParser.cs:1515 (JSON values) and :3237 (unnamed expressions).
- **Fallback.** Past 300, parsing switches to `StackSafeParser<LexicalToken>` over `QueryGrammar` (QueryParser.cs:170-191). That parser uses an explicit heap stack (Parser/Combinators/SafeParse.cs:31, SafeScan.cs:31), so input depth is unbounded.
- **Combinator path.** `ForwardParser` counts depth per thread and switches to `SafeParser`/`SafeScanner` past `MaxCallDepth = 30` (Parsers/ForwardParser.cs:48-106).
- **Tree walks are iterative.** `WalkElements`/`WalkNodes` follow parent pointers (Syntax/SyntaxElement.cs:754-858). So are `ToString` (via `WalkTokens`, :1171), `SyntaxTree.Depth` (Syntax/SyntaxTree.cs:59-75) and `Init` (no recursion).
- **Unguarded recursion:** `Clone`/`CloneCore` (Syntax/SyntaxElement.cs:1155, Syntax/SeparatedElement.cs:82, Syntax/CustomNode.cs:140, generated nodes). On a tree deeper than the fallback threshold this can overflow in both C# and Java.

### Binder: per-node recursion with a depth gate

- **Gate.** `Binder.TryBind` returns `false` (no analysis) when `tree.Depth > MaxAnalysisDepth`. The default is 500 (Properties.cs:31-32, checked at Syntax/SyntaxTree.cs:53-54 and Binder/Binder_API.cs:165, 225, 312, 347, 381). `KustoCode` records this as `AnalysisState.NotSafe` (KustoCode.cs:241-247).
- **Per-level cost.** `TreeBinder` recurses through about 4 frames per tree level: `Accept` → `Visit*` → `DefaultVisit` → `VisitChildren` (Binder/Binder_TreeBinder.cs:26-48). That is up to about 2,000 frames, plus a `NodeBinder` call chain at each node.
- **Function expansion.** Bodies are bound by a nested binder on the same stack (Binder_FunctionCalls.cs:2085-2124). Each body is gated at 500 (Binder_API.cs:225). Direct self-recursion is cut by `SignaturesComputingExpansion`. Chains of *distinct* nested function calls add up with no global cap.

### Java stack sizing

**Measured with a javac spike** on Temurin 21.0.9 / Linux x64. The spike is an 18-method chain mirroring the frames above, with small locals, at 300 nesting levels:

| Mode | Smallest passing `-Xss` |
|---|---|
| Interpreted (`-Xint`) | 768 KB (512 KB overflowed) |
| C1 only | 256-512 KB (noisy) |
| C2 after warm-up | 192 KB |

That works out to about 110-140 bytes per interpreted frame for trivial methods. The real `QueryParser` methods have more locals and operand stack, and function-call nesting adds frames. **Estimate 1-2 MB for a cold parse at the 300-level threshold.** That is at or above Java's default 1 MB thread stack on Linux x64. Binding runs after parsing, so it does not stack on top: 500 levels × about 4 frames plus `NodeBinder` work is roughly 0.3-0.6 MB interpreted. Nested function expansions add to that.

**Recommendation:**
- Keep the upstream constants unchanged for the mirror: 300 at QueryParser.cs:141, 30 at ForwardParser.cs:50, 500 at Properties.cs:32.
- Run parse and bind on threads with **4 MB stacks**, either a `ThreadFactory` with an explicit `stackSize` or a documented `-Xss4m`. That gives 2-4× headroom over the estimate.
- Add a regression test at 299/301/1,000 nesting levels on a cold JVM (`-Xint`) to confirm.

---

## Summary of what must be decided before porting starts

1. Naming rule for the 11 generic-arity families (§3C).
2. Renames for about 15 erasure-clash overload groups (§3D), including the virtual `GetArgumentParameters` family.
3. A `Class<T>` convention for reified-generic APIs (§3E).
4. How to split `QueryGrammar.Initialize` (fields plus region methods) (§3A/B).
5. The `LexicalTokenParsers` facade for `using static Parsers<LexicalToken>` (§2).
6. The `out`/`ref` holder convention (§3), and the Interlocked strategy (`volatile`/`VarHandle`) (§4).
7. Value types: `TimeSpan`, `DateTime`, `Guid`, `decimal` and their parsers (§4).
8. A Java syntax-node generator (§3G).
9. `LinkedHashMap` by default (§3H). A mirror `Linq` utility instead of streams (§4).
10. 4 MB stacks for parse and bind threads (§5).
