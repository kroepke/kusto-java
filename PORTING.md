# PORTING.md — translation rules for the Kusto.Language mirror port

Status: Phase 0 (rules settled, no code ported). Upstream pin: see `UPSTREAM.md`.
Evidence: `porting/inventory/` (per-area JSON, `traps.md`, `java21-blockers.md`, `generators.md`,
`dependency-cut.md`, `fidelity-tests-api.md`, `critic.md`). Line references are into the pinned
upstream commit, relative to `src/Kusto.Language/` unless prefixed.

This file is normative. Rules are numbered so a `// PORT:` comment can cite one.
**Every site whose Java text is not a direct transliteration of the C# text carries a
`// PORT: §<rule>` comment**, even when a rule prescribes the change. Site-specific deviations
that no rule covers get `// PORT: D<n>` and a row in section 9.

---

## 1. Layout

```
kusto-java/
  pom.xml                          parent org.graylog.kusto:kusto-parent (Java 21, Maven)
  LICENSE, NOTICE                  Apache-2.0; derived-work notice
  upstream/kusto-query-language/   git submodule, read-only
  kusto-language/                  the port. ZERO runtime dependencies (enforcer rule).
  kusto-language-generator/        Java port of the T4 syntax-node generator. Build/test-time only.
  kusto-language-conformance/      JUnit 5 + Jackson (test scope). Goldens, corpora, invariants.
  kusto-language-benchmarks/       JMH (informational).
  oracle/kusto-oracle/             .NET console tool (spec name `kusto-oracle`). Not a Maven module.
  porting/                         manifest.json, scope.json, status.json, golden-format.md,
                                   tools/, upstream-diff/, inventory/, reference/
```

Packages mirror upstream namespaces beneath `org.graylog.kusto`:

| C# namespace | Java package |
|---|---|
| `Kusto.Language` | `org.graylog.kusto.language` |
| `Kusto.Language.Syntax` | `org.graylog.kusto.language.syntax` |
| `Kusto.Language.Parsing` | `org.graylog.kusto.language.parsing` |
| `Kusto.Language.Binding` | `org.graylog.kusto.language.binding` |
| `Kusto.Language.Symbols` | `org.graylog.kusto.language.symbols` |
| `Kusto.Language.Utils` | `org.graylog.kusto.language.utils` |
| `Kusto.Language.Editor` | `org.graylog.kusto.language.editor` (boundary types only) |
| `Kusto.Language.Generator` | `org.graylog.kusto.language.generator` (module `kusto-language-generator`) |

The package follows the **namespace declared in the file, not the folder**. Three Parser files
(`ParseOptions.cs`, `KustoFacts.cs`, `KustoFacts_Keywords.cs`) and `Binder/FunctionCallExpansion.cs`
declare `namespace Kusto.Language` and land in `org.graylog.kusto.language`.

Java files with no upstream source (section 7, the `LexicalTokenParsers` facade, the `P`
builder) are listed in the manifest as `scope: synthetic` with `upstreamPath: null`.

---

## 2. Mirror rules

### 2.1 File header

```java
// Ported from: src/Kusto.Language/Parser/TokenParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
```

- The commit on line 2 is the upstream commit this file was last synced to. It equals the
  manifest entry's `syncedAt` and is rewritten by the bump procedure (section 11).
- Merged partial classes list every upstream file, one `Ported from:` line each.
- Generated files carry the same header with the `.tt` path, plus the
  `Copyright (c) Microsoft Corporation` line the upstream generator emits.
- Files with no upstream source use the second form:
  ```java
  // Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
  // Copyright (c) 2026 Graylog, Inc. Purpose: <one line>
  ```
- A gate script (`porting/tools/check_headers.py`) accepts exactly these two forms and checks
  the commit against the manifest.

### 2.2 One file per type

- Every **top-level** C# type becomes its own Java file named after the type (35 in-scope files
  in `kusto-language` and 2 in the generator split this way; `Syntax/SyntaxNode.cs` splits
  into 17). The manifest records all Java paths of a C# file in declaration order. `// PORT: §2.2`
  is not needed at the site; the header's `Ported from:` line records it.
- **Nested** types stay nested.
- **Partial** classes with only hand-written parts merge into one file. Parts are concatenated
  in **ordinal file-name order** (the C# compiler's default), each part introduced by
  `// ===== upstream part: <file> =====`. This puts `Functions.Convert.cs` before `Functions.cs`,
  which `Functions.All` requires (`Functions.cs:4177-4184` reads fields from `Functions.Convert.cs`).
  Groups: `Binder` (12 files); `KustoFacts` (2); `Functions` (2); `SyntaxNode`, `SyntaxElement`
  (`SyntaxNode.cs` + `SyntaxNode_Semantics.cs`).
- Partial classes with a **generated** part (`Expression` and the 14 other node partials in
  `SyntaxNode.cs:107-296`, `Expression` in `SyntaxNode_Semantics.cs:233+`, the four visitor
  classes in `SyntaxVisitor.cs`) follow section 4.3 only: the hand-written part lives in a
  protected region of the generated file. Manifest status for those files is `generated+handwritten`.
- Member order inside a part is upstream's. Comments, `#region` markers (as `// region`) and
  blank-line structure are kept, so a side-by-side diff lines up.

### 2.3 Names

| C# | Java | Example |
|---|---|---|
| type `Foo` | `Foo` | `TokenParser` |
| property `Foo { get; }` | method `foo()` | `token.Kind` → `token.kind()`; `IsOpen` → `isOpen()` |
| property setter | `setFoo(v)` | |
| method `DoThing()` | `doThing()` | |
| field `_pos`, `s_cache`, `m_x` | same spelling | mirror diffs matter more than style |
| constant `MaxDepth` | same spelling | `static final int MaxDepth = 300` |
| enum member `Query` | same spelling | `SyntaxKind.None`, `CodeKinds.Query` |
| local / parameter | same spelling | |
| generic parameter `TInput` | same spelling | |
| `GetHashCode()` / `Equals(object)` / `ToString()` | `hashCode()` / `equals(Object)` / `toString()` | the Java protocol names, so collections work |
| `nameof(x.Prop)` | the C# identifier as a string literal, case unchanged, `/* nameof */` | generated `GetName(i)` returns the C# spelling |

- Property → `foo()` (record style) rather than `getFoo()`: it never collides with upstream
  methods (`Parameters` vs `GetParameters()` coexist upstream), and it keeps call sites one
  token from the C# text. `getX` is never used for a property.
- Method and property lowercasing is a systematic rename. It is applied uniformly so the
  upstream-diff tool can map `Foo` ↔ `foo` mechanically. (Listed as a decision in `PLAN.md` §9.)
- Any member, accessor or local whose Java name would be a reserved word gets a trailing
  underscore: `default_()`, `new_`, `if_`. `operator` is not a Java keyword and stays.

Static get-only auto-properties that are pure catalog data (`Functions.All`, `Aggregates.All`,
`Operators.All`, `PlugIns.All`, `QueryOperatorParameters.AllParameters`, `ScalarTypes.All`) stay
`public static final` fields with the upstream spelling (W3 decision; `// PORT: §2.3`).

### 2.4 Generic-arity families

Java cannot declare `Foo<T>` and `Foo<T,U>` side by side. The **lowest** arity keeps the
upstream name; each higher arity appends its type-parameter count:

| Upstream | Java |
|---|---|
| `Parser<TInput>` / `Parser<TInput,TOutput>` | `Parser` / `Parser2` |
| `ParserVisitor<I>` / `<I,R>` / `<I,A,R>` | `ParserVisitor` / `ParserVisitor2` / `ParserVisitor3` |
| `BestParser`, `FirstParser`, `IfParser`, `MatchParser` (1 and 2) | `…` / `…2` |
| `SyntaxList` / `SyntaxList<T>` | `SyntaxList` / `SyntaxList1` |
| `SeparatedElement` / `SeparatedElement<T>` | `SeparatedElement` / `SeparatedElement1` |
| `SyntaxVisitor` / `SyntaxVisitor<R>` | `SyntaxVisitor` / `SyntaxVisitor1` |
| `DefaultSyntaxVisitor` / `<R>` | `DefaultSyntaxVisitor` / `DefaultSyntaxVisitor1` |
| `GlobalStateProperty` / `<T>` | `GlobalStateProperty` / `GlobalStateProperty1` |

Ugly on purpose; an idiomatic facade can alias these later. `build_manifest.py` detects
families and fails if a new one appears after a bump. Register row D1.

### 2.5 Overloads that clash after erasure

Overload groups that differ only in a generic argument get explicit Java names from this table.
The first-declared overload keeps the name. `build_manifest.py` will fail on any erasure clash
not listed here (added in T0).

| File | C# overloads | Java names |
|---|---|---|
| `Combinators/Parsers.cs:79,85,110` | `Convert(Parser<I>, Func<I,O>)`, `Convert(Parser<I>, Func<IReadOnlyList<I>,O>)`, `Convert(Parser<I>, Func<string,O>)` | `convert`, `convertList`, `convertText` |
| `Combinators/Parsers.cs:558,564` | `Map` ×2 | `map`, `mapList` |
| `Combinators/Parsers.cs:783,789` | `Produce` ×2 | `produce`, `produceList` |
| `SyntaxParsers.cs:33,54` | `CreateMissingToken(IReadOnlyList<SyntaxKind>)`, `(IReadOnlyList<string>)` | `createMissingToken`, `createMissingTokenText` |
| `SyntaxParsers.cs:150,309` | `Token(kinds)`, `Token(texts)` | `token`, `tokenText` |
| `SyntaxParsers.cs:372,384` | `RequiredToken` ×2 | `requiredToken`, `requiredTokenText` |
| `SyntaxParsers.cs:450,456` | `GetCompletionItems` ×2 | `getCompletionItems`, `getCompletionItemsText` |
| `QueryParser.cs:320,414` | `ParseToken` ×2 | `parseToken`, `parseTokenText` |
| `Symbols/Signature.cs:353,367` | `GetArgumentParameters(IReadOnlyList<Expression>)`, `(IReadOnlyList<TypeSymbol>)` | `getArgumentParameters`, `getArgumentParametersForTypes` |
| `Symbols/ParameterLayout.cs:16,21` and overrides in `ParameterLayouts.cs:57,62,105,122,317,329,477` | same pair, virtual | same two names across the hierarchy |
| `Binder_TablesAndColumns.cs:366,387` | `GetCommonColumns(List<Symbol>)`, `(List<ColumnSymbol>)` | `getCommonColumns`, `getCommonColumnsOfColumns` |
| `Binder_Misc.cs:450,459` | `AddDeclarationsToLocalScope` ×2 | `addDeclarationsToLocalScope`, `addDeclarationsToLocalScopeOfParameters` |
| `Binder_Misc.cs:476,490` | `BindParameterDeclarations` ×2 | `bindParameterDeclarations`, `bindParameterDeclarationsOfFunctionParameters` |
| `Binder_Misc.cs:532,541` | `BindColumnDeclarations` ×2 | `bindColumnDeclarations`, `bindColumnDeclarationsOfSchema` |
| `Binder_Misc.cs:1278,1294` | `CheckQueryOperatorParameters` ×2 | `checkQueryOperatorParameters`, `checkQueryOperatorParametersOfNamedParameters` |
| `Symbols/GraphModelSymbol.cs` ctors | `(string, IEnumerable<Signature> edges, IEnumerable<Signature> nodes, IEnumerable<GraphSnapshotSymbol>)` vs `(string, IEnumerable<string>, IEnumerable<string>, IEnumerable<string>)` | first keeps `Iterable<Signature>`; the string form takes `List<String> edges, List<String> nodes, Iterable<String> snapshots` (W1) |
| `Combinators/Parsers/ConvertParser.cs:37,61` ctors | `(Parser, Func<IReadOnlyList<I>,O>)` vs `(Parser, Func<I,O>)` | the list form is the static factory `ConvertParser.ofList(pattern, producer)` (constructors cannot be renamed) |
| `Symbols/ClusterSymbol.cs` ctors | private `(string, IReadOnlyList<Symbol>, bool)` vs public `(string, IEnumerable<DatabaseSymbol>, bool)` | private form takes `List<Symbol>`; public takes `Iterable<? extends DatabaseSymbol>` and copies instead of throwing `InvalidCastException` (W1) |

The suffix after `Of`/`Text`/`List` is the simple name of the distinguishing element type when
that reads well; otherwise the porter proposes a name and adds it here before merging.
Each site: `// PORT: §2.5`. Register row D2.

### 2.6 Visibility

| C# | Java |
|---|---|
| `public` | `public` |
| `internal`, `protected internal` | `public` + `@Internal` (`org.graylog.kusto.language.utils.dotnet.Internal`, `CLASS` retention) |
| `private` | `private` |
| `protected` | `protected` |

Java package-private cannot span `parsing`/`binding`/`syntax` the way a C# assembly does.
`@Internal` keeps the intent visible and lets a later facade exclude it.
`InternalsVisibleTo("Kusto.Language.UT")` is dropped; tests use `@Internal` members directly.

### 2.7 Markers

| Marker | Meaning |
|---|---|
| `// PORT: §<rule>` | Java differs from a transliteration because rule `<rule>` says so. |
| `// PORT: D<n>` | site-specific deviation; row `D<n>` in section 9. |
| `// PORT-PENDING: W<n>` | member stubbed with `throw new UnsupportedOperationException("PORT-PENDING W<n>")` until wave `n`. Zero on the gated path at each layer gate. |
| `// PORT-SKELETON: W<n>` | first line after the header of a type file created ahead of its wave (section 2.8). |
| `// PORT-BUG: <note>` | upstream behaviour that looks wrong but is mirrored verbatim. Never fix silently. |

### 2.8 Skeleton types

The in-scope code is one strongly connected component. A wave may reference a type owned by a
later wave. Rule: the porter creates that type's Java file **as a skeleton**: header, the
`// PORT-SKELETON: W<n>` marker, the type declaration with upstream base type and generic
parameters, and only the members the current wave needs, each `// PORT-PENDING: W<n>`, in
upstream member order. The manifest entry gets `status: partial`. The owning wave later
**replaces the whole file**. `porting/tools/check_pending.py` lists skeletons and pending
members per wave; a layer gate requires none on its path (section 6 of `PLAN.md`).

---

## 3. Translation rules

### 3.1 Properties

```csharp
public SyntaxKind Kind { get; }                                   // LexicalToken.cs:19
public int Length => this.Trivia.Length + this.Text.Length;      // LexicalToken.cs:57
```
```java
private final SyntaxKind kind;
public SyntaxKind kind() { return kind; }
public int length() { return this.trivia().length() + this.text().length(); }
```

Auto-properties become a private final field plus accessor. `{ get; private set; }` becomes a
non-final field plus accessor; a private setter method only if upstream sets it outside the
constructor. Indexers `this[int i]` become `get(int i)` (`SyntaxList.cs:37`, `SafeList.cs:33`).

### 3.2 Structs

| Struct | Java | Why |
|---|---|---|
| `ParseResult<TOutput>`, `OffsetValue<T>`, `RightParser<I,O>`, `ElementAndSeparator<E,S>`, `FunctionCallResult` | `record` | immutable, default member-wise equality unused or harmless |
| `CancellationToken` (Utils) | final class with `NONE` for `default` | section 3.13 |
| `Optional<T>` (Utils) | final class with `NONE()` for `default(Optional<T>)`. **Must hold null**: `new Optional<>(null)` means "set to null" (`GlobalState.cs:256-271`). Never `java.util.Optional`. | |
| `TextKeyedDictionary.Key`, `PartialParser.BestPathKey` | final class with `equals(Object)`/`hashCode()` from the upstream `IEquatable`/`GetHashCode` bodies. **Never a record**: records would replace upstream's custom equality (`TextKeyedDictionary.cs:120-176` compares by substring and FNV hash). | |
| `LeftValue<T>` | empty final class (inference marker) | |
| `SafeList<T>.Enumerator` (mutable) | `Iterator<T>` class | |
| `PartialParser.ScanInput` / `ScanOutput` | record / final class (lazy cache) | |
| `Interlocked.CacheLineSeparated` + padding structs | dropped (`scope.json droppedTypes`, D15) | dead code |

`default(ParseResult<T>)` has `Length == 0`, so `Succeeded == true` in C#. No upstream code
relies on it; Java `null` is never a result. ValueTuples (`PartialParser.cs:595,609,688`)
become a private record named after the deconstruction (`// PORT: §3.2`).

### 3.3 `ref` / `out` parameters

```csharp
public static bool TryGetKind(string text, out SyntaxKind kind)   // SyntaxFacts.cs:851
if (SyntaxFacts.TryGetKind(text, out var kind)) { use(kind); }
```
```java
public static boolean tryGetKind(String text, Out<SyntaxKind> kind)   // PORT: §3.3
Out<SyntaxKind> kind = new Out<>();
if (SyntaxFacts.tryGetKind(text, kind)) { use(kind.value); }
```

- `Out<T>` (`utils.dotnet.Out`) is a one-field mutable holder for `out` and `ref` of reference
  and boxed types.
- `ref int` on hot paths (`TextFacts.cs:557-606`, `KustoFacts.DecodeHex/DecodeOctal`,
  `ParameterLayouts.GetArgumentParameters`) uses `IntRef` (unboxed `int value`).
- `Dictionary.TryGetValue(k, out v)` becomes `v = map.get(k); if (v != null)` when values are
  never null (the porter checks), else `containsKey` + `get`.
- `Interlocked.CompareExchange(ref field, …)`: section 3.13.

### 3.4 Iterators (`yield`)

Four methods, none per-token or per-node (`traps.md` §7). Rule: **eager lists**, `// PORT: §3.4`.

```csharp
public static IEnumerable<SyntaxKind> GetKindsWithFixedText() {        // SyntaxFacts.cs:881
    foreach (var datum in kindToDataMap) { if (datum.Text.Length > 0) yield return datum.Kind; } }
```
```java
public static List<SyntaxKind> getKindsWithFixedText() {               // PORT: §3.4 yield → eager list
    List<SyntaxKind> result = new ArrayList<>();
    for (SyntaxData datum : kindToDataMap) { if (datum.text().length() > 0) result.add(datum.kind()); }
    return result; }
```
`ColumnMap.GetColumns` (`ColumnMap.cs:231`) is materialised by its only caller before the map
is mutated; eager evaluation keeps that order. `SyntaxParsers.ParseAll` re-parses lazily
upstream; eager evaluation gives the same list.

### 3.5 Extension methods

Static methods in a class of the same name; call sites become static calls.

```csharp
public static bool IsKeyword(this SyntaxKind kind) => …;      // SyntaxFacts.cs:830
if (kind.IsKeyword())
```
```java
public static boolean isKeyword(SyntaxKind kind) { … }
if (SyntaxFacts.isKeyword(kind))                               // PORT: §3.5
```

### 3.6 LINQ

No `java.util.stream` in `kusto-language` (gate grep). LINQ sequences are re-enumerable and
streams are not; upstream materialises at specific points (`ToReadOnly`, `ToList`) that pooled
lists depend on.

- **Hot paths** (`TokenParser`, `QueryParser`, `Binder_*`, `SyntaxElement` walkers): plain
  loops shaped like the C# expression, with the LINQ call quoted in a comment.
- **Elsewhere**: `utils.dotnet.Linq` static helpers with LINQ names and eager `List` results:
  `select`, `select(BiFunction<T,Integer,R>)` (indexed), `where`, `any`, `all`, `first`,
  `firstOrDefault`, `last`, `lastOrDefault`, `concat`, `append`, `distinct` (first wins,
  insertion order), `distinct(keyFn)`, `except` (set semantics, first occurrence order),
  `ofType(Class<T>)`, `cast`, `toList`, `toArray`, `toDictionary` (`LinkedHashMap`, throws
  `IllegalStateException` on duplicate), `max`, `min` (throw `IllegalStateException` on empty),
  `orderBy`, `orderByDescending` (stable; descending = `sort(cmp.reversed())`), `sequenceEqual`,
  `zip`, `selectMany`, `take`, `skip`, `count`, `range`, `reverse` (copy).
  `ListExtensions.DistinctFirst/DistinctLast` are ported verbatim on top.

```csharp
var names = columns.Select(c => c.Name).Where(n => n != null).ToList();
```
```java
List<String> names = Linq.toList(Linq.where(Linq.select(columns, c -> c.name()), n -> n != null));  // PORT: §3.6
```

### 3.7 Nullable value types, unsigned, decimal

| C# | Java |
|---|---|
| `int?`, `bool?`, `SyntaxKind?` | `Integer`, `Boolean`, `SyntaxKind` (null = no value) |
| `ushort`, `uint`, `ulong` | `int`/`long`; only `TableSymbol.cs:27` (`enum : ushort`) and a dead `ValueComparer` branch |
| `decimal` | `java.math.BigDecimal` via `utils.dotnet.DotNetDecimal` for parse/format/range (96-bit, scale ≤ 28); `Decimal.MinValue/MaxValue` constants |
| `TimeSpan`, `DateTime`, `Guid` | `utils.dotnet.TimeSpan`, `DateTime` (100 ns ticks), `java.util.UUID` parsed by `DotNetGuid` |

### 3.8 Delegates, functional types, events, operators, conversions

| C# | Java |
|---|---|
| `Func<T,bool>` (53 sites) | `Predicate<T>`; `Func<A,B,bool>` → `BiPredicate<A,B>` |
| `Func<T,R>`, `Func<A,B,R>` | `Function<T,R>`, `BiFunction<A,B,R>` |
| `Func<T1..Tn,R>`, n ≥ 3 (combinator `Rule`) | `utils.dotnet.Func3..Func9` (`FuncN` = N inputs plus result) |
| `Action<T>`, `Action<A,B>` | `Consumer<T>`, `BiConsumer<A,B>` |
| `Func<T>` | `Supplier<T>` |
| custom delegates `SourceProducer`, `SourceConsumer` (`Combinators/Parsers/`), `CustomAvailability` (`Symbols/CustomAvailabilty.cs`), `CustomReturnType`, `ParameterLayoutBuilder` | `@FunctionalInterface` with the upstream name, own file; the single method is named `invoke` (W1 decision; W3 follows it) |
| `event`, operator overloads | none upstream |
| implicit conversions (5 sites) | explicit at each use: `FunctionCallResult.of(type)` (`FunctionCallResult.cs:25`), `Optional.of(v)` (`Optional.cs:17`), `CancellationToken.of(…)` (`Cancellation.cs:30`), `EditString.of(s)`/`.toString()` (`EditString.cs:72,80`), `ScanOutput.from(ScanInput)` (`PartialParser.cs:959`). `// PORT: §3.8` |

### 3.9 Static initialisation

- Field initialisers stay in place and in order. Partial parts merge in ordinal file order
  (2.2), which makes `Functions.All` safe.
- The two explicit static constructors become `static {}` blocks at the same position:

```csharp
static ScalarTypes() {                                            // ScalarTypes.cs:365
    foreach (var type in All) { s_typeMap.Add(type.Name, type); … } }
```
```java
static {                                                          // PORT: §3.9 static ctor
    for (ScalarSymbol type : All) { DotNet.dictionaryAdd(s_typeMap, type.name(), type); … } }
```
  (`Dictionary.Add` throws on duplicates; `DotNet.dictionaryAdd` reproduces that.)
- `KustoFacts.KnownQueryOperatorParameterNames` stays lazy (`KustoFacts.cs:79-93`); it breaks
  the only cycle (`KustoFacts` ⇄ `QueryOperatorParameters`).
- `GlobalState.Default` stays a CAS-published lazy singleton, not a static field initialiser.
- Static fields in generic classes (15 sites) become shared raw statics with unchecked casts.
  `ForwardParser.s_callDepth` (`[ThreadStatic]`, per closed type upstream) becomes one
  `ThreadLocal<int[]>` shared by all instantiations. The fallback it triggers
  (`SafeParser`/`SafeScanner`) is result-equivalent to the direct path, so the earlier trigger
  is not observable; row D9. The `ParserKind.Grammar ≡ Default` conformance test guards this.
- **Enum-typed fields without an initialiser** default to the zero-valued member in C# and to
  `null` in Java. Rule: every such field, array element or `default(E)` gets
  `= E.<zero member>` with `// PORT: §3.9 default(enum)`. Example: `Binder._scopeKind`
  (`Binder_API.cs:88`) is never set in the constructor and is switched on in `BindName`.
  The porter greps `^\s*(private|internal|protected|public)?\s*(static )?(readonly )?<Enum> \w+;`
  for every in-scope enum.
- A conformance test loads the catalog classes in both orders and asserts no null entries.

### 3.10 Generics

- **Reified type tests** (`is T`, `typeof(T)`, `new T[]`, `OfType<T>`): add a leading
  `Class<T>` parameter. `GetDescendants<NameReference>(pred)` becomes
  `getDescendants(NameReference.class, pred)`. About 80 call sites. `// PORT: §3.10`.
- **`where T : new()`**: when the method already receives `Class<T>` (the reified rule above),
  instantiate reflectively via `DotNet.newInstance(cls)`; so `KustoCache.GetOrCreate<T>()`
  becomes `getOrCreate(Class<T>)` and `GetOrCreate<T>(Func<T>)` becomes
  `getOrCreate(Class<T>, Supplier<T>)`. No clash. Elsewhere add a `Supplier<T>`.
- **Covariance**: `IReadOnlyList<Base>` parameters that receive subtype lists become
  `List<? extends Base>`. A method declared to return `IReadOnlyList<Symbol>` that upstream
  implements by returning a subtype list (`TableSymbol.cs:114` `Members => this.Columns`)
  returns `Collections.unmodifiableList(this.columns())` (`// PORT: §3.10`).
- **`params T[]`** → varargs; generic varargs get `@SafeVarargs` on static/final methods.
- **`new` member hiding** (11 sites) → covariant overrides. `SyntaxList<T>.GetEnumerator()`
  cannot override the base; the base returns `Iterator<? extends SyntaxElement>`.
- **`default(T)`** (68 sites): `null` for reference types; porter checks each value-type use.
- **`using static Parsers<LexicalToken>`**: Java cannot static-import from a parameterised
  type. `Parsers` is mirrored as static generic methods; `kusto-language-generator` also emits
  the non-generic facade `LexicalTokenParsers` (77 methods, 35 names, plus the field `Any`; the 7 `Character`-only and nested-producer members are skipped)
  with `TInput` fixed. Grammar files `import static …LexicalTokenParsers.*`. Row D7.
- Types implementing `IReadOnlyList<T>` (`SyntaxList<T>`, `SafeList<T>`) implement
  `utils.dotnet.ReadOnlyList<T>` (a minimal interface: `size()`, `get(int)`, `iterator()`)
  **not** `java.util.List`, so `equals`/`hashCode` stay identity as upstream. A `toList()` view
  is provided for API users. `Count` → `size()`. `// PORT: §3.10`.
- `IEqualityComparer<T>` → `utils.dotnet.EqualityComparer<T>` (`equals(a,b)`, `hashCode(a)`);
  a comparer-keyed dictionary wraps keys in `EqualityComparer.Key`.

### 3.11 Constructors

C# `: base(expr)` / `: this(expr)` → Java `super(expr)` / `this(expr)` as the first statement.
All 44 computed-argument chains translate inline on Java 21 (`java21-blockers.md` §1 lists 42;
`Editor/CompletionItem.cs` adds 2). Five read better through a `private static` helper
(`ConvertParser.cs:40`, `GraphModelSymbol.cs:61`, `Signature.cs:223,228`, `SyntaxList.cs:82`);
`// PORT: §3.11`. **No site needs Java 25 flexible constructor bodies.** Decision: Java 21.

Field-initialiser order differs (C# runs derived initialisers before the base constructor,
Java after `super()`). The only virtual calls from constructors (`SyntaxList`, `SeparatedElement`,
`CustomNode`, generated nodes' `Init()`, `CommandGrammar.cs:46` whose stub subclasses do not
override) touch no derived initialised field.

### 3.12 Optional and named arguments

- Optional parameters (~572) become telescoping overloads in upstream member order, full
  signature first. **A telescoped overload whose signature already exists upstream is skipped.**
  Call sites that skip an optional parameter by name pass the upstream default expression
  verbatim; `default(Optional<T>)` is `Optional.NONE()`.
- Named arguments (~1,186, mostly catalogs) become positional. `Parameter`'s three
  11-parameter public constructors (`Parameter.cs:129,157,185`) get the mirror-named builder
  `symbols.P`, used **only** in catalog files, one upstream line per Java line:

```csharp
new Parameter("name", ScalarTypes.String, minOccurring: 0)                    // Functions.cs:27
```
```java
new Parameter("name", ScalarTypes.String, P.minOccurring(0))                  // PORT: §3.12
```
- Local functions with optional parameters or used before declaration become `private`
  methods placed right after the enclosing member, with telescoping overloads
  (`// PORT: §3.12 local function`). Other local functions (`QueryGrammar.cs:895-1957`,
  `Functions.cs:3510`) become lambdas in local variables declared before use.

### 3.13 Threading

`Utils/Interlocked.cs` is ported as a class with the same member names over `VarHandle`s:
```csharp
Interlocked.CompareExchange(ref this.diagnostics, diagnostics, null);     // KustoCode.cs:378
```
```java
Interlocked.compareExchange(DIAGNOSTICS, this, diagnostics, null);        // PORT: §3.13
// where: private static final VarHandle DIAGNOSTICS = Interlocked.handle(KustoCode.class, "diagnostics", List.class);
```
Overloads: `(VarHandle, Object owner, T newValue, T comparand)` for instance fields,
`(VarHandle, T newValue, T comparand)` for statics, an `int` variant for `SafeList._isOwner`,
and `exchange(AtomicReferenceArray, int, T)` for `ObjectPool`. CAS-published fields are
`volatile`. Non-null comparands (the two grammar caches, `QueryGrammar.cs:58`,
`CommandGrammar.cs:404,438`) use the same call.

| C# | Java |
|---|---|
| `lock (x)` | `synchronized (x)` |
| `ConcurrentDictionary.GetOrAdd(k, f)` | `ConcurrentHashMap`: `get` then `putIfAbsent` (never `computeIfAbsent`; .NET may run `f` twice and so may we). Null values are never stored upstream (checked at port time). |
| `[ThreadStatic]` | `ThreadLocal` (3.9) |
| `Utils.CancellationToken` | final class wrapping a `BooleanSupplier` (the `BRIDGE` shape, `Cancellation.cs:9-20`) because Java has no `System.Threading.CancellationToken`; row D22 |
| `Utils.ThreadSafeDictionary` | the `!BRIDGE` branch over `ConcurrentHashMap` |

### 3.14 Null semantics

- `string == string` → `Objects.equals(a, b)`; against a literal → `"lit".equals(a)`.
- `$"…{x}…"`, `s + x`, `string.Join`, `string.Concat`, `StringBuilder.Append(x)`: .NET prints
  `""` for null, Java prints `"null"`. All go through `DotNet.str(x)` (null → `""`, else
  .NET `ToString` formatting, section 5.2) and `DotNetStrings.join/concat`. Gate grep bans
  `String.join`, `String.valueOf(Object)` in `kusto-language`.
- `switch (s)` on a possibly-null string: C# falls to `default`; Java throws. Use a Java 21
  pattern switch with `case null, default ->`, or guard first. `Symbols/SchemaDisplay.cs:18`
  and `DebugDisplay.cs:21` have explicit `case null`.
- `?.` and `??` (187 / 462 sites) → explicit null checks; one upstream expression per Java
  statement where possible.
- `string.IsNullOrWhiteSpace` (8 sites) → `DotNetStrings.isNullOrWhiteSpace` (.NET white-space
  set). `isBlank`/`strip` are banned.

### 3.15 Pattern matching

- `is Type x` → `instanceof Type x`.
- `x is SyntaxKind k` where `x` is a **non-nullable** enum (the six `QueryParser.cs` sites
  1786, 2881, 2889, 2982, 3013, 3041) → plain assignment. Where `x` is `SyntaxKind?` /
  `SymbolKind?` (`SyntaxParsers.cs:235`, `Binder_NodeBinder.cs:918`) it is a has-value test:
  `if (x != null) { SyntaxKind k = x; … }`. `// PORT: §3.15`.
- `case Type x:` → Java 21 pattern switch.
- `case Type _:` fall-through stacks (24 sites) → `if (x instanceof A || x instanceof B)`.
- `goto retry` (`SyntaxElement.cs:1030`) → labelled `while (true)` with `continue`.

### 3.16 Exceptions

| C# | Java |
|---|---|
| `ArgumentNullException` | `NullPointerException` via `Objects.requireNonNull(x, "name")` |
| `ArgumentException` | `IllegalArgumentException` |
| `ArgumentOutOfRangeException`, `IndexOutOfRangeException` | `IndexOutOfBoundsException` |
| `InvalidOperationException` | `IllegalStateException` |
| `NotImplementedException` | `UnsupportedOperationException` |
| `InvalidCastException` | `ClassCastException` |
| `OverflowException` | `ArithmeticException` |
| `OperationCanceledException` | `utils.dotnet.OperationCanceledException extends RuntimeException` |
| `catch (Exception)` (`Binder_FunctionCalls.cs:2103`) | `catch (RuntimeException)`; a `StackOverflowError` propagates, as the process death does in .NET |

The same table is `porting/exception-map.json`, used by the conformance `outcome` comparison.
Implicit BCL throws upstream relies on are reproduced explicitly (`char.ConvertFromUtf32`
surrogate check, `TimeSpan.FromX` overflow, `Dictionary.Add` duplicate key, `Enumerable.Max`
on empty, `Convert.ChangeType` via `DotNet.changeType`).

### 3.17 Collections

| C# | Java |
|---|---|
| `IReadOnlyList<T>` (parameters, returns) | `java.util.List<T>` (unmodifiable where upstream returns `ToReadOnly()`) |
| `IEnumerable<T>` | `Iterable<T>` for parameters, `List<T>` for returns |
| `List<T>` | `ArrayList<T>` |
| `Dictionary<K,V>` | `LinkedHashMap<K,V>` always (insertion order is observable in 8 places, `traps.md` §3a). `d[k] = v` → `put`; `d.Add(k, v)` → `DotNet.dictionaryAdd` (throws on duplicate). |
| `HashSet<T>` | `LinkedHashSet<T>` always |
| `Dictionary<SyntaxKind,…>` | still `LinkedHashMap`. **Never `EnumMap`/`EnumSet`** where enumerated: `QueryGrammar.StringOperatorMap` order drives parse alternatives. `EnumSet` is allowed for membership-only sets. |
| `Dictionary(StringComparer.OrdinalIgnoreCase)` | `LinkedHashMap` with `EqualityComparer.Key` wrapper (3.10) |
| `List<int>` on hot paths (token starts, line starts) | `utils.dotnet.IntList`; `Arrays.binarySearch` has .NET's `~insertionPoint` encoding |
| `KeyValuePair<K,V>` | `Map.Entry` or a record |
| `[Flags]` enums (10, including `CompletionHint`) | `int` constants holder class with the upstream member names; bit math unchanged. `// PORT: §3.17`; row D23. |
| plain enums | Java enums, **declaration order identical** (`SyntaxFacts` indexes by `(int)kind`). An alias member (`ResultNameKind.Default = None`, `CompletionPriority.Default = Normal`) becomes `public static final E Default = None;` inside the enum (`// PORT: §3.17 enum alias`). Enums with explicit values get a `final int value` field; `(int)e` uses `value()`, never `ordinal()`. |
| `string.Split(char)` | `DotNetStrings.split(s, ch)` (literal, keeps empties); `String.split` banned |

Removals never precede enumeration upstream, so `LinkedHashMap` is exact.
`SubstringMap` and `TextKeyedDictionary` are ported **with their upstream shapes**
(`Dictionary<char,Node>`, `Dictionary<Key,V>` → `LinkedHashMap`). Boxing-free rewrites are
deferred until JMH shows a need and would then get D-rows.

### 3.18 Hot-path primitives

| Site | Rule |
|---|---|
| `Func<T,bool>` | `Predicate<T>` |
| `List<int>` token/line starts | `IntList` |
| `ParseResult<T>` | record (allocates; grammar/fallback path only) |
| `char` arithmetic, `text[i]` | `charAt`, UTF-16 code units; never code points |

### 3.19 The 64 KB method limit

`QueryGrammar.Initialize` (`QueryGrammar.cs:119-3680`, 360 locals, ~2,400 calls) will not
compile as one Java method. Rule:

- `Initialize` is split into `initialize_<Region>()` methods called in order. Split points are
  the upstream `#region` boundaries (`:121 Forwards`, `:208 Names`, `:429 Schema and Types`,
  `:561 Literals`, `:851 Query Operator Parameters`, `:1068 Expressions`, `:1594 Query
  Operators`) **plus** sub-region splits inside `Query Operators` (1,700 lines), one per query
  operator group, each marked `// PORT: §3.19 split`.
- **Every local variable or local function referenced across a split boundary becomes a
  private field or private method**, declared in upstream order at the top of the class. That
  covers the 16 `…Core` forward locals (`:121-136`, which also fixes the lambdas capturing
  mutated locals) and about 70 others. `porting/tools/check_grammar_split.py` computes the set
  from the region table so two porters produce the same fields.
- Bytecode size is measured in W4 (`javap`) before the split is finalised.
- `Functions.<clinit>` is estimated at 24-31 KB; if `P`-builder calls push it over, each
  field's construction moves into a `private static` factory, one per field, `// PORT: §3.19`.

### 3.20 Unused, debug and Bridge-only code

- `#if BRIDGE` branches: port the `!BRIDGE` (.NET) branch, except `CancellationToken` (3.13).
- `[Conditional("DEBUG")]` `Ensure.*` and `Debug.Assert`: ported as plain methods guarded by
  `Ensure.ENABLED` (a `static final boolean` from system property `kusto.ensure`, default
  **false** so behaviour matches the Release oracle). Never Java `assert`. A separate
  checks-on test run exists but does not compare `outcome`.
- Unused `using Kusto.Language.Editor` (`GlobalState.cs:7`, `Parser/TriviaFacts.cs:5`): dropped.
- `[DebuggerDisplay]` dropped; `[Obsolete]` → `@Deprecated`.

---

## 4. Generated code

Facts (`porting/inventory/generators.md`): upstream generates 225 syntax node classes and 4
visitor classes from `Kusto.Language.Generators/SyntaxNodeInfos.cs` via
`SyntaxNodeGenerator.cs` + `CodeGenerator.cs`. **The output is not checked in**; the public
repo does not compile without it. The last checked-in output (`porting/reference/
GeneratedSyntaxNodes.cs`, 18,882 lines) is structurally identical to what the current generator
produces from the namespace line onward (verified by a re-implementation; T0 verifies
byte-for-byte with the real generator).

### 4.1 Decisions

1. **Port the generator**, not its output. Module `kusto-language-generator` mirrors the three
   files; `SyntaxNodeInfos.java` is data, one upstream line per Java line via a fluent builder.
2. **Check in the generated Java** under `kusto-language/src/main/java/…/syntax/`, one file per
   node (forced by Java). `GeneratedSyntaxNodesUpToDateTest` regenerates in memory and fails on
   drift. Regeneration: `mvn -pl kusto-language-generator install` then
   `mvn -pl kusto-language -Pregenerate generate-sources`, where the profile binds
   `exec-maven-plugin:java` with `mainClass=org.graylog.kusto.language.generator.GenerateSyntaxNodes`
   and argument `${project.basedir}/src/main/java`.
3. Generated constructors are `public`. `diagnostics = null` and
   `expressionHint = CompletionHint.None` defaults become overloads (3.12).
4. The command generator is not ported (section 8).

### 4.2 Data mirror example

```csharp
new SyntaxNodeInfo {                                                       // SyntaxNodeInfos.cs:18
    Name = "DirectiveBlock", Base = "SyntaxNode", Sealed = true, Kind = "DirectiveBlock",
    Properties = new [] {
        new SyntaxNodeProperty { Name = "Directives", Type = "SyntaxList<Directive>", Completion="None"},
```
```java
node().name("DirectiveBlock").base("SyntaxNode").sealed_(true).kind("DirectiveBlock")
    .properties(
        prop().name("Directives").type("SyntaxList<Directive>").completion("None"),
```

### 4.3 Emitted node example (`BinaryExpression`, no `Kind`, `SyntaxNodeInfos.cs:417-428`)

```java
// Ported from: src/Kusto.Language/Syntax/CodeGen/GeneratedSyntaxNodes.tt (node BinaryExpression)
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5…
// SPDX-License-Identifier: Apache-2.0
// Copyright (c) Microsoft Corporation. All rights reserved.
// Upstream license: Apache-2.0. This file is GENERATED by kusto-language-generator; do not edit outside <hand-written> regions.
package org.graylog.kusto.language.syntax;

public final class BinaryExpression extends Expression {
    private final SyntaxKind kind;
    @Override public SyntaxKind kind() { return this.kind; }
    private final Expression left;
    public Expression left() { return left; }
    private final SyntaxToken operator;
    public SyntaxToken operator() { return operator; }
    private final Expression right;
    public Expression right() { return right; }

    public BinaryExpression(SyntaxKind kind, Expression left, SyntaxToken operator, Expression right, List<Diagnostic> diagnostics) {
        super(diagnostics);
        this.kind = kind;
        this.left = attach(left);
        this.operator = attach(operator);
        this.right = attach(right);
        this.init();
    }
    public BinaryExpression(SyntaxKind kind, Expression left, SyntaxToken operator, Expression right) { this(kind, left, operator, right, null); }

    @Override public int childCount() { return 3; }
    @Override public SyntaxElement getChild(int index) { switch (index) { case 0: return left; case 1: return operator; case 2: return right; default: throw new IndexOutOfBoundsException(); } }
    @Override public String getName(int index) { switch (index) { case 0: return "Left"; case 1: return "Operator"; case 2: return "Right"; default: throw new IndexOutOfBoundsException(); } }
    @Override protected CompletionHint getCompletionHintCore(int index) { switch (index) { case 1: return CompletionHint.Syntax; default: return CompletionHint.Inherit; } }
    @Override public void accept(SyntaxVisitor visitor) { visitor.visitBinaryExpression(this); }
    @Override public <TResult> TResult accept(SyntaxVisitor1<TResult> visitor) { return visitor.visitBinaryExpression(this); }
    @Override @SuppressWarnings("unchecked") protected SyntaxElement cloneCore(boolean includeDiagnostics) {
        return new BinaryExpression(this.kind(), left != null ? (Expression) left.clone(includeDiagnostics) : null,
            operator != null ? (SyntaxToken) operator.clone(includeDiagnostics) : null,
            right != null ? (Expression) right.clone(includeDiagnostics) : null,
            includeDiagnostics ? this.syntaxDiagnostics() : null);
    }
}
```
`NamedParameter` additionally gets a third constructor without `expressionHint`, and its
`IsSyntax=false` property is assigned without `attach`. Property type map in the emitter:
`string` → `String`, `IReadOnlyList<string>` → `List<String>`, `CompletionHint` → `int` and
`SymbolMatch` → `int` (both are `[Flags]` holders, 3.17/D23), generic node types get the arity
suffix (`SyntaxList<SeparatedElement<X>>` → `SyntaxList1<SeparatedElement1<X>>`). Region files
also carry a `// <hand-written-imports>` region after the generated imports and one
`// Ported from:` line per upstream origin (W0 decision). Member order is the upstream emit order
(kind, properties, constructor, child accessors, visitor, clone).

### 4.4 Hand-written regions

Classes with hand-written partial halves get regions at the **end** of the generated body, in
ordinal upstream-file order, generated members first:
```java
    // <hand-written from="src/Kusto.Language/Syntax/SyntaxNode.cs:107-123">
    …
    // </hand-written>
    // <hand-written from="src/Kusto.Language/Syntax/SyntaxNode_Semantics.cs:233-290">
    …
    // </hand-written>
```
The generator preserves region contents on regeneration, emits an empty region for classes in
its fixed origin table (from `generators.md` §2.3) when none exists, and fails on a region it
does not expect. `SyntaxVisitor`'s hand-written `VisitCustom/VisitList/VisitSeparatedElement`
(`SyntaxVisitor.cs:10-60`) are injected the same way. Manifest status `generated+handwritten`
with the region line ranges in `notes`.

### 4.5 Structural check

`porting/tools/check_generated_structure.py` extracts, from both
`porting/reference/GeneratedSyntaxNodes.cs` and the generated Java, the tuple per node
(name, base, abstract/sealed, kind, ordered `[childName, optional, completionHint]`,
constructor parameter order) and the visitor method order, and diffs them. Normalisation:
strip BOM, the leading `// ` lines and the trailing newline. Runs in the W0 gate.

---

## 5. Behavioural traps: policies

Each policy has a named test (`TrapsTest.*`) and corpus cases in `traps.jsonl`.

### 5.1 Character classification

- `TextFacts.IsLetter/IsDigit/IsLetterOrDigit/IsHexDigit` are **ASCII-only** (`TextFacts.cs:361-379`).
  Port verbatim. Identifiers are ASCII.
- `TextFacts.IsWhitespace` is a literal 24-character switch including U+200B and U+FEFF
  (`TextFacts.cs:12-45`); `IsLineBreakStart` is `\r \n U+2028 U+2029`. Port the switch from the
  source file (the copy in `traps.md` is lossy). Never `Character.isWhitespace`.
- `char.IsDigit` (16 sites, lexer dispatch at `TokenParser.cs:180,627,661`) and `char.IsLetter`
  (2 sites) → `Character.isDigit(char)` / `Character.isLetter(char)`. `٣` lexes as a long
  literal on both sides; its **value** is 0 (5.2). Table drift between Java 21 (Unicode 15.0)
  and .NET 10 `CharUnicodeInfo` is accepted (D21); `kusto-oracle dotnet-facts` emits the
  category sets so the drift is known.
- `string.Trim()` (11 sites) → `DotNetStrings.trim` (.NET `char.IsWhiteSpace` set:
  U+0009–U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000–U+200A, U+2028, U+2029, U+202F,
  U+205F, U+3000). Java `trim()`/`strip()` banned.
- `ToLower()/ToUpper()` (6 sites) → `Locale.ROOT`; `Parsers.cs:666-667` maps per `char`.
- `char.ConvertFromUtf32` → explicit range and surrogate check before `Character.toChars`.

### 5.2 Literal values and formatting

Policy: **invariant culture, Release oracle, UTC**. `utils.dotnet` reproduces .NET semantics:

| Upstream call | Java |
|---|---|
| `Int32/Int64.TryParse` (`SyntaxToken.cs:584,600`) | `DotNetNumber.tryParseInt/Long`: ASCII digits, leading/trailing white space, sign; any failure → 0 (upstream discards the bool). Hex text → 0. |
| `Double.TryParse` (`:616,667`) | `DotNetNumber.tryParseDouble`: `NumberStyles.Float \| AllowThousands`, invariant separators; rejects Java-only forms (`0x1p3`, `1.5d`); overflow → ±∞. |
| `Decimal.TryParse` (`:632`) | `DotNetDecimal.tryParse`: `NumberStyles.Number`, no exponent, 28-digit scale, range → 0 on failure. |
| `TimeSpan.TryParse` (`:643`) then unit suffixes (`:658-712`) | `TimeSpan.tryParse` with `[-][d.]hh:mm[:ss[.fffffff]]` (bare integer = days), then the suffix switch; `FromSeconds` etc. throw `ArithmeticException` on overflow/NaN; `(long)number` saturates (.NET 9+). |
| `DateTime.TryParse` (`:731`) | `DateTime.tryParse` for the invariant formats .NET accepts; `Z`/offset values convert to **UTC**, not local (D11). |
| `Guid.TryParse` (`:740`) | `DotNetGuid.tryParse` for N, D, B, P, X; never `UUID.fromString`. |
| `bool.TryParse` (`PlugIns.cs:394-486`) | `DotNetBoolean.tryParse`: case-insensitive, trimmed. |
| `Convert.ChangeType` (`ValueComparer.cs:26-27`) | `DotNet.changeType(Object, Class)`: `ClassCastException` for TimeSpan/Guid→String, `DotNet.str` otherwise, numeric widening per `IConvertible`. |
| implicit `object.ToString()` (10 visible sites) | `DotNet.str(Object)`: double = shortest round-trip, no `.0`, `E+XX`; bool = `True`/`False`; decimal keeps scale; `TimeSpan` = `c`; `DateTime` = `MM/dd/yyyy HH:mm:ss`; `Guid` = `D` lowercase. |
| `0.0.Equals(-0.0)` true in .NET | `ValueComparer` compares doubles with `==` |

Reference values come from `kusto-oracle dotnet-facts` over `porting/dotnet-facts-cases.txt`
(one `api<TAB>input` per line, drawn from every literal form in the corpora plus edge cases);
output `{api, input, ok, value}` is committed as `dotnet-facts.json` and every case must match.

### 5.3 Collection ordering and sorting

- `LinkedHashMap`/`LinkedHashSet` everywhere (3.17). `Distinct()` is insertion-ordered.
- `List.Sort` is unstable in .NET; the single site (`SyntaxFacts.cs:773`) sorts 605 entries,
  287 with text `""`, then `GetOrAddValue` keeps the first per text. Only the `""` key is
  affected. The oracle answers `TryGetKind("") == (true, SyntaxKind.None)` (`run.sh probe`,
  2026-10-02); Java registers `""` → `None` explicitly after the sort (D13) and
  `TrapsTest.tryGetKindEmpty` pins it.
- `OrderBy` (stable) → `List.sort` (stable). `StringComparer.OrdinalIgnoreCase` → upper-case
  per char then compare (`'_'` vs `'a'` differs from `String.CASE_INSENSITIVE_ORDER`). .NET's
  invariant upper-casing leaves `ı` (U+0131) and `ſ` (U+017F) unchanged where Java maps them to
  `I`/`S`; `DotNetChars.toUpperInvariant` carries those exclusions and `traps/0395-0396` pin
  the resulting sort order.
- `StringAndNumberComparer` quirks (`:97-115`) mirrored with `// PORT-BUG`.
- `ColumnMap.cs:81-85` same-name column with three types: mirrored, `// PORT-BUG`, traps case.

### 5.4 String comparison

- `==`, `string.Equals`, `Contains(string)`, `IndexOf(char)` are ordinal upstream → Java
  `equals`, `contains`, `indexOf`.
- `string.Compare` without `StringComparison` (17 sites), `StartsWith/EndsWith(string)` (10),
  `IndexOf(string)` (2), `OrderBy` on strings (2) are culture-sensitive upstream; under
  invariant globalization they are ordinal, and Java uses ordinal (D12).
- `string.Compare(a, ia, b, ib, len)` compares `min(len, remaining)`; `DotNetStrings.compare`
  mirrors the clamp (`SyntaxToken.cs:552-563` depends on it).
- `OrdinalIgnoreCase` equality → `DotNetStrings.equalsOrdinalIgnoreCase` (upper-case only;
  Java `equalsIgnoreCase` also tries lower-case and differs on `K`/`K`). Banned in `kusto-language`.
- `(start, length)` vs `(begin, end)`: every `Substring(start, length)` (48 sites),
  `RemoveRange`, `InsertRange` call is translated with explicit `start + length`; the gate
  (`SourceRulesTest`) flags `substring(` calls whose second argument is not of the form `x + y`,
  except a literal `0` start and lines marked `// PORT: §5.4`, which is required on every
  `EditString.substring(start, length)` call (the stub keeps upstream's `(start, length)` shape).

---

## 6. Stack depth

Constants stay: `QueryParser.MaxDepth = 300`, `ForwardParser.MaxCallDepth = 30`,
`Properties.MaxAnalysisDepth = 500`. Estimated need on Java 21: 1-2 MB for a cold parse at
300 levels (spike measured 768 KB, `java21-blockers.md` §5). Policy:

- `kusto-language` does not spawn threads. It documents a 4 MB stack requirement.
- The conformance harness and the example translator run parse/analyze on a thread created
  with `new Thread(null, r, "kql", 16L << 20)`.
- Tests at 299/301 (parser), 30/31 (forward), 499/501 (binder) nesting on `-Xint`, recording
  the smallest passing `-Xss`.
- Invariants: `KustoCode.parse` never throws (no `Throwable` on a 4 MB thread, every corpus and
  fuzz input). `parseAndAnalyze` and lazy `SyntaxToken.value()` follow "Java throws iff the
  oracle throws", recorded in the golden `outcome`. T0 measures on the oracle whether
  `ParseAndAnalyze` ever throws over the corpora. **Result (2026-10-02): zero throws over
  readme, docs and sentinel (6,266 records), zero token-value throws.** The Java invariant is
  therefore "never throws" for analysis too; `outcome.analyze` still records any throw.

---

## 7. `utils.dotnet` compatibility package (no upstream file; manifest `synthetic`)

| Class | Contents |
|---|---|
| `Out<T>`, `IntRef`, `IntList` | holders / primitive list (3.3, 3.17) |
| `Func3..Func9` | functional interfaces (3.8) |
| `Linq` | eager LINQ helpers (3.6) |
| `ReadOnlyList<T>`, `EqualityComparer<T>` | collection protocol (3.10) |
| `DotNetChars` | `isWhiteSpace` (.NET set), `convertFromUtf32` |
| `DotNetStrings` | `trim`, `isNullOrWhiteSpace`, `compare` (clamped region), `equalsOrdinalIgnoreCase`, `compareOrdinalIgnoreCase`, `toUpperInvariant`, `join`, `concat`, `split` |
| `DotNetNumber`, `DotNetDecimal`, `DotNetBoolean`, `DotNetGuid` | TryParse semantics (5.2) |
| `TimeSpan`, `DateTime` | 100 ns tick value classes with .NET range, `MinValue/MaxValue`, parse, format |
| `DotNet` | `str(Object)`, `changeType`, `dictionaryAdd`, `newInstance(Class)` |
| `Interlocked` | ported from `Utils/Interlocked.cs` (3.13); listed here because its body is VarHandle code |
| `OperationCanceledException`, `Internal` | |

Each class has unit tests pinned to `dotnet-facts.json`.

---

## 8. Scope cut and boundary stubs

Full lists: `porting/scope.json` and `porting/manifest.json`. Summary
(`porting/inventory/dependency-cut.md`):

- **Included**: root files (`KustoCode`, `GlobalState`, catalogs, `Options`, `Properties`,
  `KustoCache`, `KustoDialect`, `ServerKinds`), all of `Binder/`, `Symbols/`, `Diagnostics/`,
  `Utils/`, `Syntax/`, `Parser/` and `Parser/Combinators/`, plus nine Editor types in eight
  files (1,180 lines) the core references (`CodeKinds`, `CompletionHint`, `CompletionKind`,
  `CompletionPriority`+`Rank`, `CompletionItem`, `CompletionText`, `ClientDirective`,
  `ClientDirectiveArgument`). `CompletionHint` is semantic (`Binder_Names.cs:851`).
- **Optional** (no path from the entry points, 682 lines): `CommandFacts`, `ScriptFacts`,
  `CommentFacts`, `TriviaFacts`, `CharScanners`, `ScannerExtensions`, `ParserExtensions`,
  `Editor/TextRange`. **Decision: ported in W7**, status `ported`; they are public upstream API.
- **Excluded**: the other 79 `Editor/**` files (16.7k lines), `TestHelpers.cs`,
  `AssemblyInfo.cs`, the command generator and its inputs, `Kusto.Language.Bridge/`,
  `grammar/`, build files.
- **Stubs (9)**:

| Stub | Shape | Why |
|---|---|---|
| `EngineCommands`, `DataManagerCommands`, `ClusterManagerCommands`, `AriaBridgeCommands` | `public static final List<CommandSymbol> All = List.of();` | T4 outputs of excluded command infos; read only by `GlobalState.GetCommands` |
| `EngineCommandGrammar`, `DataManagerCommandGrammar`, `ClusterManagerCommandGrammar`, `AriaBridgeCommandGrammar` | `extends CommandGrammar`, constructor only | read only by `CommandGrammar.CreateCommandGrammar` |
| `Editor/EditString` | `Empty`, ctor, `currentText()`, `length()`, `charAt`, `substring(start,length)`, `substring(start)`, `toString()`, `of(String)` | `ClientDirective` uses a read-only slice (12 call sites verified) |

Command text (`.`-prefixed), decided:

1. **Settled in T0 (`oracle/run.sh probe`, 2026-10-02):** with zero command parsers,
   `.foo` under `GlobalState.Default.WithServerKind("Unknown")` throws
   `InvalidOperationException` ("Sequence contains no elements") from
   `PartialParser.FindBestPath` (`PartialParser.cs:365`) for both `Parse` and
   `ParseAndAnalyze`. D10 is adopted: `CommandGrammar.partialCommand` returns `-1` when
   `commandParsers.length == 0`, so Java commands parse as `UnknownCommand` without throwing.
2. Commands parse as `UnknownCommand`; `.show tables | where …` binds against a null row scope
   and reports false "name does not refer to any known item" errors. **Mirror upstream's
   `UnknownCommand` path unchanged.** Command records are compared on tokens and fidelity
   only (`porting/golden-format.md`); their count is reported. Command text is out of scope
   for Graylog.
3. Queries embedded in commands (`.set-or-append T <| …`) are swallowed into `SkippedTokens`
   without diagnostics. A conformance test pins it.
4. Java entry points mirror `KustoCode`, not `KustoCodeService`: no 4 MiB text cap, no
   internal-failure diagnostic, no analyzers.

---

## 9. Deviation register

| # | Where | Deviation | Reason |
|---|---|---|---|
| D1 | generic-arity families | numeric suffix names (2.4) | Java |
| D2 | overload groups in 2.5 | erasure renames | Java |
| D3 | 37 multi-type files | one file per top-level type (2.2) | Java |
| D4 | partial classes | merged files, ordinal part order (2.2) | Java |
| D5 | `internal` members | `public @Internal` (2.6) | Java packages |
| D6 | generated nodes | one file per node; hand-written partials in protected regions (4) | Java |
| D7 | `Parsers<TInput>` | static generic methods + `LexicalTokenParsers` facade (3.10) | no static import from generic type |
| D8 | `QueryGrammar.Initialize` | split into region methods; cross-boundary locals become fields (3.19) | 64 KB |
| D9 | `ForwardParser.s_callDepth` | one shared `ThreadLocal` counter; fallback is result-equivalent (3.9) except upstream's own `StackSafeParser/Scanner.VisitZeroOrMore` dropping a successful `ZeroOrOne` item's length (`SafeParse.cs:684-698`, `SafeScan.cs:680-693`), mirrored as `PORT-BUG` | erasure |
| D10 | `CommandGrammar.partialCommand` | guard for zero command parsers (8); T0 confirmed the throw | stub |
| D11 | `DateTime.TryParse` | `Z`/offset → UTC, not local (5.2) | determinism |
| D12 | culture-sensitive string APIs | ordinal (5.4) | invariant policy |
| D13 | `SyntaxFacts.textToKindMap` | `""` registered explicitly as `None`, matching the oracle (5.3) | unstable sort |
| D14 | yield methods | eager lists (3.4) | cold paths |
| D15 | `Interlocked.CacheLineSeparated` | dropped (`scope.json droppedTypes`) | dead code |
| D16 | catalog named arguments | `P` builder (3.12) | Java |
| D17 | `KustoCode.ParseAndAnalyze` cancellation | mirrored bug: token ignored (`KustoCode.cs:172`) | `// PORT-BUG` |
| D18 | `MostRecentlyUsedCache.AddOrUpdate` | mirrored bug: keeps old value (`:66`) | `// PORT-BUG` |
| D19 | `KustoFacts.HasInteriorQuote` | mirrored bug: ignores `start` (`:878`) | `// PORT-BUG` |
| D20 | `ParserVisitors.IsParentVisitor.VisitFails` | mirrored `!=` (`:106`) | `// PORT-BUG` |
| D21 | Unicode tables | Java 21 Unicode 15.0 vs .NET 10 `CharUnicodeInfo` | accepted |
| D22 | `Utils.CancellationToken` | `BRIDGE` shape over `BooleanSupplier` (3.13) | no .NET token type |
| D23 | `[Flags]` enums (10) | int-constant holder classes (3.17) | Java enums cannot OR |
| D24 | `IEquatable<T>`-only types (`CallSiteInfo.cs`, `Diagnostic.cs`, `Key` structs) | add `equals(Object)` delegating to the typed method (2.3) | collections call `equals(Object)` |
| D25 | `ColumnMap.cs:81-85` | mirrored bug: same-name column with three types | `// PORT-BUG` |
| D26 | `IReadOnlyList<T>` implementers | `ReadOnlyList<T>` instead of `java.util.List` (3.10) | identity equality |
| D27 | telescoping / optional parameters | overloads; `Optional.NONE()`; local functions with defaults → private methods (3.12) | Java |
| D28 | enum aliases, explicit enum values | static alias constants; `value()` field (3.17) | Java enums |
| D29 | `Ensure`/`Debug.Assert` | runtime flag, default off (3.20) | Release oracle parity |
| D30 | module/tool names | `oracle/kusto-oracle` is not a Maven module; `porting/upstream-diff/` holds the tool | .NET is not Maven |
| D31 | time-only `datetime(...)` literals | both sides fill in the run date (`DateTime.TryParse` semantics); goldens pin the oracle's generation day, so such records are known differences (`KD-001`) rather than corpus edits | date-dependent behaviour |
| D32 | `PlugIns` `ArgumentKind.Column \| ArgumentKind.Literal` (4 `ai_*` sites) | `ArgumentKind` is not `[Flags]`; the value `(ArgumentKind)13` names no member and behaves like `Expression` everywhere; Java uses `ArgumentKind.Expression` with `// PORT-BUG` (oracle globals show `"13"`) | Java enums cannot hold 13 |
| D33 | QueryGrammar name-declaration fallback | upstream grammar-mode bug: a bare `=` in name position yields an `IdentifierToken "="` (KS228) AND the `EqualToken` at the same offset, so `root.toString()` gains one `=` (`print f(=)` → `print f(==)`); verified identical in .NET `ParserKind.Grammar`; mirrored with `// PORT-BUG`; the local fuzz round-trip invariant exempts exact reduced texts listed in `fuzz-known-failures.json` | upstream bug |

Rows are added as porting proceeds. The manifest `notes` field points at the row.

Site audit (W7, 2026-10-03): rows D7, D9, D10, D12, D13, D15, D16, D17-D26, D29, D33 have at
least one `// PORT:`/`// PORT-BUG:` site citing the row. The remaining rows are rule-level and
are cited through their section number at every site: D1 (2.4), D2 (2.5), D3 and D4 (2.2),
D5 (2.6), D6 (4), D8 (3.19), D11 (5.2), D14 (3.4), D27 (3.12), D28 (3.17). D30 is tooling
layout (no Java site); D31 is cited by `known-differences.json` (KD-001, KD-002); D32 is cited
in `PlugIns.java`.

---

## 10. Decision log

| Decision | Choice | Why |
|---|---|---|
| Java version | **21 LTS** | no Java 25 blocker; all computed `base(...)` chains inline-legal. Revisit only for a concrete blocker. |
| Oracle runtime | **.NET 10 LTS**, `-c Release`, `InvariantGlobalization=true`, `TZ=UTC`, built from the submodule sources at the pin | NuGet 12.4.1 is 10 commits behind the pin. Source build is exact and bump-proof. |
| Oracle code generation | compile `Kusto.Language.Generators/*.cs` as a library (valid C# under `#if !T4`; no T4-only construct, `critic`/review verified) and call `SyntaxNodeGenerator.Generate` / `CommandGenerator.GenerateParser/GenerateSymbols` from a runner | avoids T4; `dotnet-t4` is the fallback |
| Generated Java | checked in + drift test (4) | reviewable diffs on bumps |
| Core dependencies | none; `maven-enforcer` `bannedDependencies` on compile/runtime scope | spec |
| Test JSON | Jackson, conformance module only | |
| Tooling language | Python 3 stdlib for `porting/tools/*`; PyYAML (pinned) allowed for the Sentinel extractor only | YAML has no stdlib parser |
| Property accessors | `foo()` | collision-free with upstream methods (2.3) |
| Stack | 4 MB thread documented; harness uses 16 MB | section 6 |
| Optional files | ported in W7 | public upstream API; 682 lines |

---

## 11. Upstream bump procedure

1. `git -C upstream/kusto-query-language fetch && git -C upstream/kusto-query-language checkout <new>`;
   update `UPSTREAM.md`.
2. `python3 porting/upstream-diff/upstream_diff.py <old> <new>`: lists changed upstream files,
   maps them through `porting/manifest.json`, classifies each (`pending/ported/partial → patch`;
   `generated`/`generated+handwritten → regenerate`; `stubbed → re-check stub`;
   `excluded → re-check exclusion`; `new → triage`; `deleted → remove`). Catalog diffs are split
   into data lines and logic lines. Exit code non-zero when triage is needed.
3. Triage new files in `porting/scope.json`.
4. Mirror any `SyntaxNodeInfos.cs` change into `SyntaxNodeInfos.java`; regenerate (4.1).
5. Apply patches to ported files in upstream diff order, keeping member order. Update the
   header commit line of every touched file and `syncedAt` in `porting/status.json`
   (`porting/tools/sync_headers.py` does both).
6. Rebuild the oracle at the new pin; regenerate goldens: `oracle/run.sh regenerate`. Review
   the golden diff; it is the behavioural changelog. Re-justify expired `known-differences.json`
   entries.
7. `mvn verify`. Conformance must be green against the baseline, or the failures must be
   explained in the commit.
8. `python3 porting/tools/build_manifest.py` to refresh blob hashes; `check_headers.py`;
   commit with the message `chore: bump upstream to <sha>` naming the manifest entries touched.
