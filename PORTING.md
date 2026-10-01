# PORTING.md — translation rules for the Kusto.Language mirror port

Status: Phase 0 (rules settled, no code ported). Upstream pin: see `UPSTREAM.md`.
Inventory evidence: `porting/inventory/` (per-area JSON, `traps.md`, `java21-blockers.md`,
`generators.md`, `dependency-cut.md`, `fidelity-tests-api.md`, `critic.md`).
Line references below are into the pinned upstream commit, relative to `src/Kusto.Language/`.

This file is normative. Every rule here is "decide once, apply everywhere".
A deviation from upstream that a rule does not already cover needs a `// PORT:` comment at the
site and a row in section 9.

---

## 1. Layout

```
kusto-java/
  pom.xml                          parent (Java 21, Maven multi-module)
  upstream/kusto-query-language/   git submodule, read-only
  kusto-language/                  the port. ZERO runtime dependencies.
  kusto-language-generator/        Java port of the T4 syntax-node generator. Build/test-time only.
  kusto-language-conformance/      JUnit 5 + Jackson (test scope). Goldens, corpora, invariants.
  kusto-language-benchmarks/       JMH (informational).
  oracle/Kusto.Oracle/             .NET console tool. Test tooling only. Not a Maven module.
  porting/                         manifest.json, scope.json, tools/, inventory/, upstream-diff
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

The package follows the **namespace declared in the file, not the folder**. Four files differ:
`Parser/ParseOptions.cs`, `Parser/KustoFacts.cs`, `Parser/KustoFacts_Keywords.cs` and
`Binder/FunctionCallExpansion.cs` declare `namespace Kusto.Language` and land in
`org.graylog.kusto.language`.

.NET-compatibility helpers that upstream gets from the BCL live in
`org.graylog.kusto.language.utils.dotnet` (section 7). They have no upstream file and are listed
in the manifest with `upstreamPath: null`.

---

## 2. Mirror rules

### 2.1 File header

Every Java file starts with:

```java
// Ported from: src/Kusto.Language/Parser/TokenParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
```

Merged partial classes list every upstream file, one `Ported from:` line each.
Generated files get the same header from the generator, with the `.tt` as the upstream path,
plus the `Copyright (c) Microsoft Corporation` line the upstream generator emits.

### 2.2 One file per type

C# allows many top-level types per file; Java allows one public type per file. Rule:

- Every **top-level** C# type becomes its own Java file named after the type. 37 in-scope files
  split this way (`Syntax/SyntaxNode.cs` splits into 17, `Diagnostics/Diagnostic.cs` into 4).
  The manifest records all Java paths of a C# file, in declaration order.
- **Nested** types stay nested.
- **Partial** classes merge into one file. Parts are concatenated in **ordinal file-name order**
  (the C# compiler's default), each part introduced by `// ===== upstream part: <file> =====`.
  This puts `Functions.Convert.cs` before `Functions.cs`, which the static initialiser of
  `Functions.All` requires (`Functions.cs:3734` reads fields declared in `Functions.Convert.cs`).
  `Binder` merges 12 files; `KustoFacts` 2; `Functions` 2; `SyntaxNode`, `SyntaxElement`,
  `Expression` merge `SyntaxNode.cs` with `SyntaxNode_Semantics.cs`.
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
| local / parameter | same spelling | `@operator` → `operator_` only if it is a Java keyword |
| generic parameter `TInput` | same spelling | |

Property → `foo()` (record style) rather than `getFoo()`: it never collides with upstream
methods (`Parameters` vs `GetParameters()` coexist upstream), and it keeps call sites one token
away from the C# text. `getX` is never used for a property.

Java keywords that appear as upstream identifiers get a trailing underscore: `default_`,
`operator` is **not** a Java keyword and stays.

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

Ugly on purpose. An idiomatic facade can alias these later. The manifest builder detects
families and fails if a new one appears after a bump.

### 2.5 Overloads that clash after erasure

About 15 overload groups differ only in a generic argument (`IReadOnlyList<SyntaxKind>` vs
`IReadOnlyList<string>`). Rule: the overload declared **first** keeps the name; each later
clashing overload appends the simple name of the distinguishing element type.

```csharp
public static Parser<LexicalToken, SyntaxToken> Token(IReadOnlyList<SyntaxKind> kinds, ...)   // :150
public static Parser<LexicalToken, SyntaxToken> Token(IReadOnlyList<string> texts, ...)       // :309
```
```java
public static Parser2<LexicalToken, SyntaxToken> token(List<SyntaxKind> kinds, ...)
public static Parser2<LexicalToken, SyntaxToken> tokenString(List<String> texts, ...)   // PORT: erasure clash
```

Known groups (from `java21-blockers.md` §3D): `Parsers.Convert` ×3, `Produce`, `Map`;
`SyntaxParsers.CreateMissingToken`, `Token`, `RequiredToken`, `GetCompletionItems`;
`QueryParser.ParseToken`; `Signature.GetArgumentParameters`; `ParameterLayout.GetArgumentParameters`
(virtual, rename across the whole hierarchy); `Binder.GetCommonColumns`,
`AddDeclarationsToLocalScope`, `BindParameterDeclarations`, `BindColumnDeclarations`,
`CheckQueryOperatorParameters`. Each rename gets a `// PORT: erasure clash` comment.

### 2.6 Visibility

| C# | Java |
|---|---|
| `public` | `public` |
| `internal` | `public` + `@Internal` (annotation in `org.graylog.kusto.language.utils`) |
| `protected internal` | `public` + `@Internal` |
| `private` | `private` |
| `protected` | `protected` |

Java package-private cannot span the `parsing`/`binding`/`syntax` packages the way a C#
assembly does. `@Internal` keeps the intent visible and lets a later facade exclude it.
`InternalsVisibleTo("Kusto.Language.UT")` is dropped; test code uses `@Internal` members
directly.

### 2.7 Deviation markers

| Marker | Meaning |
|---|---|
| `// PORT: <reason>` | deliberate deviation from upstream logic or shape. Also a row in section 9. |
| `// PORT-PENDING: <wave>` | member stubbed with `throw new UnsupportedOperationException` until a later wave. Must be zero at the end of each layer gate. |
| `// PORT-BUG: <note>` | upstream behaviour that looks wrong but is mirrored verbatim. Never fix silently. |

---

## 3. Translation rules

### 3.1 Properties

```csharp
public SyntaxKind Kind { get; }
public int Length => this.Trivia.Length + this.Text.Length;
public override string Trivia => this.trivia;
```
```java
private final SyntaxKind kind;
public SyntaxKind kind() { return kind; }
public int length() { return this.trivia().length() + this.text().length(); }
@Override public String trivia() { return this.trivia; }
```

Auto-properties become a private final field plus accessor. A `{ get; private set; }` becomes a
non-final field plus accessor; the setter is a private method only if upstream sets it outside
the constructor.

### 3.2 Structs

Rule: immutable structs become **records**; mutable structs become final classes.
Records give C#'s value equality for free. Audit (from `traps.md` §5):

| Struct | Java |
|---|---|
| `ParseResult<TOutput>`, `OffsetValue<T>`, `RightParser<I,O>`, `ElementAndSeparator<E,S>`, `TextKeyedDictionary.Key`, `FunctionCallResult` | `record` |
| `CancellationToken` (Utils) | `record` with a `NONE` constant replacing `default` |
| `Optional<T>` (Utils) | final class. **Must hold null**: `new Optional<>(null)` means "set to null", which `GlobalState.With` relies on (`GlobalState.cs:256-271`). Never `java.util.Optional`. |
| `LeftValue<T>` | empty final class (marker for inference) |
| `SafeList<T>.Enumerator` (mutable) | `Iterator<T>` class |
| `PartialParser.ScanInput` / `ScanOutput` (lazy cache) | record / final class |
| `PartialParser.BestPathKey` (dictionary key) | final class with `equals`/`hashCode` |
| `Interlocked.CacheLineSeparated` and padding structs | dropped: dead code (`// PORT:` in the file header note) |

`default(ParseResult<T>)` has `Length == 0` and so `Succeeded == true` in C#. No upstream code
relies on that today; Java `null` must never be treated as a result.

### 3.3 `ref` / `out` parameters

```csharp
public static bool TryGetKind(string text, out SyntaxKind kind)
if (SyntaxFacts.TryGetKind(text, out var kind)) { use(kind); }
```
```java
public static boolean tryGetKind(String text, Out<SyntaxKind> kind)
Out<SyntaxKind> kind = new Out<>();
if (SyntaxFacts.tryGetKind(text, kind)) { use(kind.value); }
```

- `Out<T>` (`utils.dotnet.Out`) is a one-field mutable holder, used for both `out` and `ref` of
  reference types and boxed values.
- `ref int` on hot paths (`TextFacts.cs:557-606`, `KustoFacts.DecodeHex/DecodeOctal`,
  `ParameterLayouts.GetArgumentParameters`) uses `IntRef` (unboxed `int value`).
- `Dictionary.TryGetValue(k, out v)` becomes `v = map.get(k); if (v != null)` when values are
  never null, else `containsKey` + `get`. The porter must check which.
- `Interlocked.CompareExchange(ref field, …)`: section 3.13.

### 3.4 Iterators (`yield`)

Four methods, none per-token or per-node (`traps.md` §7). Rule: **eager lists**, marked
`// PORT: yield → eager list`. `SyntaxFacts.GetKinds`/`GetKindsWithFixedText` run once at
static init. `ColumnMap.GetColumns` is materialised by its only caller. `SyntaxParsers.ParseAll`
is a utility; eager evaluation does not change results.

### 3.5 Extension methods

Static methods in a class of the same name; call sites become static calls.

```csharp
public static bool IsKeyword(this SyntaxKind kind) => …;      // SyntaxFacts.cs:830
if (kind.IsKeyword())
```
```java
public static boolean isKeyword(SyntaxKind kind) { … }
if (SyntaxFacts.isKeyword(kind))
```

`Utils/ListExtensions.cs` (21), `Symbols/TypeFacts.cs` (19) and the rest keep their class names.

### 3.6 LINQ

No `java.util.stream` in `kusto-language`. Reasons: LINQ sequences are re-enumerable and
streams are not; upstream materialises at specific points (`ToReadOnly`, `ToList`) that pooled
lists depend on; stream pipelines do not line up with the C# text.

- **Hot paths** (`TokenParser`, `QueryParser`, `Binder_*`, `SyntaxElement` walkers): plain loops,
  shaped like the C# expression, with the LINQ call quoted in a comment.
- **Elsewhere**: `utils.dotnet.Linq` static helpers with the LINQ names and eager `List`
  results: `select`, `where`, `any`, `all`, `first`, `firstOrDefault`, `concat`, `distinct`
  (insertion-ordered), `ofType(Class<T>)`, `cast`, `toList`, `toArray`, `max`, `min`, `orderBy`
  (stable, explicit comparator), `sequenceEqual`, `zip`, `selectMany`, `take`, `skip`, `count`.
- `Max`/`Min` on an empty input throw `InvalidOperationException` as in .NET.

```csharp
var names = columns.Select(c => c.Name).Where(n => n != null).ToList();
```
```java
List<String> names = Linq.toList(Linq.where(Linq.select(columns, c -> c.name()), n -> n != null));
```

### 3.7 Nullable value types, unsigned, decimal

| C# | Java |
|---|---|
| `int?`, `bool?`, `SyntaxKind?` | `Integer`, `Boolean`, `SyntaxKind` (null = no value) |
| `ushort`, `uint`, `ulong` | `int`/`long`; only `TableSymbol.cs:26` (`enum : ushort`) and dead `ValueComparer` branch |
| `decimal` | `java.math.BigDecimal` wrapped by `utils.dotnet.DotNetDecimal` for parse/format/range (96-bit, scale ≤ 28). `Decimal.MinValue/MaxValue` are constants. |
| `TimeSpan`, `DateTime`, `Guid` | value classes in `utils.dotnet` (section 7.2) |

### 3.8 Delegates, functional types, events, operators

| C# | Java |
|---|---|
| `Func<T,bool>` (53 sites) | `java.util.function.Predicate<T>` (no boxing) |
| `Func<T,R>`, `Func<A,B,R>` | `Function<T,R>`, `BiFunction<A,B,R>` |
| `Func<T1..T9,R>` (combinator `Rule`) | project `Func3..Func9` interfaces in `utils.dotnet` |
| `Action<T>` | `Consumer<T>` |
| `Func<T>` | `Supplier<T>` |
| custom delegate `SourceProducer`, `SourceConsumer`, `CustomAvailability` | `@FunctionalInterface` with the upstream name, own file |
| `event` | none upstream |
| operator overloads | none upstream |
| implicit conversions (4 sites) | explicit factory at each use: `FunctionCallResult.of(type)`, `Optional.of(v)`, `CancellationToken.of(...)`, `EditString.of(s)` / `.toString()` |

### 3.9 Static initialisation

- Field initialisers stay in place and in order. Partial parts merge in ordinal file order
  (section 2.2), which is what makes `Functions.All` safe.
- The two explicit static constructors (`ScalarTypes.cs:365`, `SyntaxFacts.cs:43`) become
  `static {}` blocks at the same position.
- `KustoFacts.KnownQueryOperatorParameterNames` stays lazy (`KustoFacts.cs:79-93`); it breaks the
  only cycle (`KustoFacts` ⇄ `QueryOperatorParameters`).
- `GlobalState.Default` stays a CAS-published lazy singleton, not a static field initialiser.
- Static fields in generic classes (15 sites) become shared raw statics with unchecked casts.
  Exception: `ForwardParser.s_callDepth` (`[ThreadStatic]`, per closed type) becomes a
  `ThreadLocal<Map<Class<?>, int[]>>` keyed by output type, so `MaxCallDepth = 30` triggers per
  type pair as upstream.
- A conformance test instantiates every catalog class in both orders (`Functions` first and
  `Symbols` first) and asserts no null catalog entries.

### 3.10 Generics

- **Reified type tests** (`is T`, `typeof(T)`, `new T[]`, `OfType<T>`): add a leading
  `Class<T>` parameter. `GetDescendants<NameReference>(pred)` becomes
  `getDescendants(NameReference.class, pred)`. About 80 call sites.
- **`where T : new()`** (`KustoCache`, `Source`): add a `Supplier<T>` parameter.
- **Covariance**: `IReadOnlyList<Base>` parameters that receive subtype lists become
  `List<? extends Base>`. Return types stay `List<Base>`.
- **`params T[]`** → varargs; generic varargs get `@SafeVarargs` on static/final methods.
- **`new` member hiding** (11 sites) → covariant overrides. `SyntaxList<T>.GetEnumerator()` cannot
  override the base; the base returns `Iterator<? extends SyntaxElement>`.
- **`default(T)`** (68 sites): `null` for reference types; porter checks each value-type use
  (`GetParameterLiteralValue<bool?>` is `Boolean`, so `null` is right).
- **`using static Parsers<LexicalToken>`**: Java cannot static-import from a parameterised
  type. `Parsers` is mirrored as static generic methods; the generator module also emits a
  non-generic facade `LexicalTokenParsers` with the same 84 member names and `TInput` fixed.
  Grammar files `import static …LexicalTokenParsers.*`. `// PORT:` on the facade file only.

### 3.11 Constructors

C# `: base(expr)` / `: this(expr)` → Java `super(expr)` / `this(expr)` as the first statement.
All 42 computed-argument chains translate inline on Java 21 (`java21-blockers.md` §1). Five
read better through a `private static` helper (`ConvertParser.cs:40`, `GraphModelSymbol.cs:61`,
`Signature.cs:223/228`, `SyntaxList.cs:82`). **No site needs Java 25 flexible constructor
bodies.** Decision: stay on Java 21 (section 10).

Field-initialiser order differs (C# runs derived initialisers before the base constructor,
Java after `super()`). The only virtual calls from constructors (`SyntaxList`, `SeparatedElement`,
`CustomNode`, generated nodes' `Init()`) touch no derived initialised field. The porter keeps
hand-written cached fields without initialisers.

### 3.12 Optional and named arguments

Optional parameters (~572) become telescoping overloads in upstream member order, the full
signature first. Named arguments (~1,186, mostly the catalogs) become positional; `Parameter`'s
12-parameter constructor gets a mirror-named builder used **only** in catalog files:

```csharp
new Parameter("name", ScalarTypes.String, minOccurring: 0)
```
```java
new Parameter("name", ScalarTypes.String, P.minOccurring(0))   // PORT: named-argument builder
```

The builder keeps one upstream line per Java line, which the upstream-diff tool needs.

### 3.13 Threading

| C# | Java |
|---|---|
| `Interlocked.CompareExchange(ref field, value, null)` (31 lazy-publish sites) | `VarHandle` per field with `compareAndSet`; the field is `volatile`. A helper `Lazy.publish(VarHandle, this, value)` keeps call sites one line. |
| `Interlocked.Exchange` on array slots (`ObjectPool`) | `AtomicReferenceArray` |
| `SafeList._isOwner` CAS | `AtomicInteger` |
| `lock (x)` | `synchronized (x)` |
| `ConcurrentDictionary.GetOrAdd(k, f)` | `get` then `putIfAbsent` (never `computeIfAbsent`: it locks the bin and forbids re-entrancy; .NET may run `f` twice and so may we) |
| `[ThreadStatic]` | `ThreadLocal` (see 3.9 for the per-type key) |
| `System.Threading.CancellationToken` | upstream's own `Utils.CancellationToken` wraps a `BooleanSupplier` (the `BRIDGE` branch shape) |

### 3.14 Null semantics

- `string == string` → `Objects.equals(a, b)`; against a literal → `"lit".equals(a)`.
- `$"…{x}…"` and `s + x` where `x` may be null: .NET prints `""`, Java prints `"null"`.
  All interpolations go through `DotNet.str(x)` (null → `""`, otherwise .NET `ToString`
  formatting, section 7.3). `StringBuilder.append(x)` likewise via `DotNet.str`.
- `switch (s)` on a string that may be null: C# falls to `default`; Java throws. Guard with
  `if (s == null) { <default body> } else switch (s) { … }`, or use `case null` in a Java 21
  pattern switch. `Symbols/SchemaDisplay.cs:18` and `DebugDisplay.cs:21` have explicit `case null`.
- `?.` and `??` (187 / 462 sites) → explicit null checks; keep one upstream expression per Java
  statement where possible.

### 3.15 Pattern matching

- `is Type x` → `instanceof Type x`. The 8 sites that test an enum value (`is SyntaxKind k`)
  become a plain assignment.
- `case Type x:` → Java 21 pattern switch.
- `case Type _:` fall-through stacks (24 sites) → `if (x instanceof A || x instanceof B)`.
  Java 21 forbids fall-through into a pattern label; Java 22's `case A _, B _` is not used.
- `goto retry` (`SyntaxElement.cs:1030`) → labelled `while (true)` with `continue`.
- Local functions (13 sites) → lambdas in local variables, declared before use.

### 3.16 Exceptions

| C# | Java |
|---|---|
| `ArgumentNullException` | `NullPointerException` via `Objects.requireNonNull(x, "name")` |
| `ArgumentException`, `ArgumentOutOfRangeException` | `IllegalArgumentException`, `IndexOutOfBoundsException` |
| `InvalidOperationException` | `IllegalStateException` |
| `NotImplementedException` | `UnsupportedOperationException` |
| `OperationCanceledException` | `utils.dotnet.OperationCanceledException extends RuntimeException` |
| `catch (Exception)` (`Binder_FunctionCalls.cs:2103`) | `catch (RuntimeException)`. Not `Throwable`: a `StackOverflowError` propagates, as the process death does in .NET. |

Implicit BCL throws that upstream relies on are reproduced explicitly (`char.ConvertFromUtf32`
surrogate check, `TimeSpan.FromX` overflow, `Dictionary.Add` duplicate key, `Enumerable.Max`
on empty).

### 3.17 Collections

| C# | Java |
|---|---|
| `IReadOnlyList<T>` | `java.util.List<T>` (unmodifiable where upstream returns `ToReadOnly()`) |
| `IEnumerable<T>` | `Iterable<T>` for parameters, `List<T>` for returns |
| `List<T>` | `ArrayList<T>` |
| `Dictionary<K,V>` | `LinkedHashMap<K,V>` always (insertion order is observable in 8 places, `traps.md` §3a) |
| `HashSet<T>` | `LinkedHashSet<T>` always |
| `Dictionary<SyntaxKind,…>` | still `LinkedHashMap`. **Never `EnumMap`/`EnumSet`** where enumerated: `QueryGrammar.StringOperatorMap` order drives parse alternatives. `EnumSet` is allowed for membership-only sets. |
| `Dictionary(StringComparer.OrdinalIgnoreCase)` | `LinkedHashMap` keyed by `DotNetStrings.toUpperInvariant(key)` with a wrapper |
| `List<int>` on hot paths (token starts, line starts) | `int[]` / `IntList`; `Arrays.binarySearch` has .NET's `~insertionPoint` encoding |
| `KeyValuePair<K,V>` | `Map.Entry` or a record |
| `[Flags]` enums (9) | `int` constants holder class with the upstream member names; bit math unchanged |
| plain enums | Java enums, **declaration order identical** (`SyntaxFacts` indexes by `(int)kind`) |

Removals never precede enumeration upstream, so `LinkedHashMap` is exact.

### 3.18 Hot-path primitives

| Site | Rule |
|---|---|
| `Utils/SubstringMap.cs` (keyword trie, every token) | parallel `char[]`/`Node[]`, 128-slot ASCII fast path; class and member names unchanged |
| `Utils/TextKeyedDictionary.cs` + `StringTable` (per identifier) | open-addressing table keyed by `(text, start, len)`; no key object per lookup; same FNV-1a hash (`-2128831035` offset, `16777619` prime) |
| `Func<T,bool>` | `Predicate<T>` |
| `ParseResult<T>` | record (allocates; cold path only) |

### 3.19 The 64 KB method limit

`QueryGrammar.Initialize` (`QueryGrammar.cs:119-3680`, 360 locals, ~2,400 calls) will not
compile as one Java method. Rule: the 16 `…Core` forward locals become fields; `Initialize`
is split along upstream's `#region` boundaries into `initialize_<Region>()` methods called in
order. Each split point gets `// PORT: 64KB split`. This also fixes the 17 lambdas that
capture mutated locals (`QueryGrammar.cs:121-136`), which Java forbids.
`Functions.<clinit>` is estimated at 24-31 KB; if positional `Parameter` calls push it over,
each field's construction moves into a `private static` factory, one per field.

### 3.20 Unused and Bridge-only code

- `#if BRIDGE` branches: port the `!BRIDGE` (.NET) branch. `#if DEBUG`/`[Conditional("DEBUG")]`
  (`Utils/Ensure.cs`): keep as plain methods guarded by a `static final boolean` set from a
  system property, so conformance can run with checks on.
- Unused `using` of `Kusto.Language.Editor` (`GlobalState.cs:7`, `Parser/TriviaFacts.cs:5`): dropped.
- `[DebuggerDisplay]`, `[Obsolete]`: `@Deprecated` for `Obsolete`; `DebuggerDisplay` dropped.

---

## 4. Generated code

Facts (`porting/inventory/generators.md`): upstream generates 225 syntax node classes and 4
visitor classes from `Kusto.Language.Generators/SyntaxNodeInfos.cs` via
`SyntaxNodeGenerator.cs` + `CodeGenerator.cs`. **The output is not checked in**; the public
repo does not compile without it. The last checked-in output
(`git show cd24dcf3^:src/Kusto.Language/Syntax/CodeGen/GeneratedSyntaxNodes.cs`, 18,882 lines)
is byte-identical to what the current generator produces.

Decisions:

1. **Port the generator**, not its output. Module `kusto-language-generator` mirrors the three
   files (`SyntaxNodeInfos.java` is data, one upstream line per Java line via a fluent builder).
2. **Check in the generated Java** under `kusto-language/src/main/java/…/syntax/`, one file per
   node (forced by Java). A test in `kusto-language` regenerates in memory and fails on drift.
   A Maven profile `-Pregenerate` rewrites the files.
3. **Hand-written partial halves** of generated classes (`SyntaxNode.cs:107-296`,
   `SyntaxNode_Semantics.cs:233+`, `SyntaxVisitor.cs`) live inside the generated file in
   protected regions:
   ```java
   // <hand-written from="src/Kusto.Language/Syntax/SyntaxNode.cs:107-123">
   …
   // </hand-written>
   ```
   The generator preserves region contents on regeneration. The manifest maps those upstream
   partials to the generated file.
4. Generated constructors are `public` (C# `internal` cannot cross Java packages).
   `diagnostics = null` and `expressionHint = CompletionHint.None` defaults become overloads.
5. The historical C# output is extracted to `porting/reference/GeneratedSyntaxNodes.cs` with
   its blob hash, so a shallow submodule clone still has the structural golden.
6. The command generator (`CommandGenerator.cs`, `Grammar.cs`, `*CommandInfos.cs`, 8 `.tt`) is
   **not** ported (section 8).

---

## 5. Behavioural traps: policies

Each policy has a named test in `kusto-language-conformance` (`TrapsTest.*`) and a golden case.

### 5.1 Character classification

- `TextFacts.IsLetter/IsDigit/IsLetterOrDigit/IsHexDigit` are **ASCII-only** upstream
  (`TextFacts.cs:361-379`). Port verbatim. Identifiers are ASCII.
- `TextFacts.IsWhitespace` is a literal 26-character switch including U+200B and U+FEFF
  (`TextFacts.cs:12-45`); `IsLineBreakStart` is `\r \n U+2028 U+2029`. Port the switch verbatim
  from the source file (not from `traps.md`, whose copy is lossy). Never `Character.isWhitespace`.
- `char.IsDigit` (16 sites, lexer dispatch at `TokenParser.cs:180,627,661`) and `char.IsLetter`
  (2 sites) → `Character.isDigit(char)` / `Character.isLetter(char)`: same Unicode categories.
  So `٣` lexes as a long literal in both; its **value** is 0 in .NET (ASCII-only `Int64.TryParse`)
  and must be 0 in Java (section 5.2). Unicode table version drift (Java 21 = Unicode 15.0 vs
  the oracle's ICU) is accepted and listed as a known difference.
- `string.Trim()` (11 sites) → `DotNetStrings.trim` with .NET's `char.IsWhiteSpace` set.
  Neither Java `trim()` nor `strip()` matches.
- `ToLower()/ToUpper()` (6 sites) → `Locale.ROOT`; `Parsers.cs:666-667` maps per `char` to keep
  index alignment.
- `char.ConvertFromUtf32` → explicit range and surrogate check before `Character.toChars`.

### 5.2 Literal values and formatting

Policy: **invariant culture**. The oracle runs with `InvariantGlobalization=true` and
`TZ=UTC`; the Java port reproduces .NET invariant-culture semantics in `utils.dotnet`:

| Upstream call | Java |
|---|---|
| `Int32/Int64.TryParse` (`SyntaxToken.cs:584,600`) | `DotNetNumber.tryParseInt/Long`: ASCII digits only, leading/trailing white space, sign; overflow and any other failure → 0 (upstream discards the bool). Hex text (`0x1F`) → 0, as upstream. |
| `Double.TryParse` (`:616,667`) | `DotNetNumber.tryParseDouble`: `NumberStyles.Float \| AllowThousands` with invariant separators; rejects Java-only forms (`0x1p3`, `1.5d`, `NaN` spellings other than .NET's); overflow → ±∞ (.NET Core 3.0+). |
| `Decimal.TryParse` (`:632`) | `DotNetDecimal.tryParse`: `NumberStyles.Number`, no exponent, 28-digit scale clamp, range check → 0 on failure. |
| `TimeSpan.TryParse` (`:643`) then unit suffixes (`:658-712`) | `TimeSpan.tryParse` with .NET's `[-][d.]hh:mm[:ss[.fffffff]]` grammar (bare integer = days), then the same suffix switch; `FromSeconds` etc. throw on overflow/NaN as .NET does. `(long)number` saturates (.NET 9+ semantics). |
| `DateTime.TryParse` (`:731`) | `DateTime.tryParse` accepting the invariant-culture formats .NET accepts (ISO 8601, `M/d/yyyy`, `yyyy-MM-dd HH:mm:ss`, with optional `Z`/offset). `Z`/offset values are converted to **UTC**, not local time. `// PORT:` and listed in section 9. The oracle runs under `TZ=UTC` so goldens agree. |
| `Guid.TryParse` (`:740`) | `DotNetGuid.tryParse` for formats N, D, B, P, X; never `UUID.fromString`. |
| `bool.TryParse` (`PlugIns.cs:394-486`) | `DotNetBoolean.tryParse`: case-insensitive, trimmed. |
| implicit `object.ToString()` (10 visible sites, column names and messages) | `DotNet.str(Object)`: double = shortest round-trip, no `.0`, `E+XX` exponent; bool = `True`/`False`; decimal keeps scale; `TimeSpan` = `c` format; `DateTime` = invariant `MM/dd/yyyy HH:mm:ss`; `Guid` = `D` lowercase. |
| `0.0.Equals(-0.0)` is true in .NET | `ValueComparer` compares doubles with `==`, not `Double.equals` |

### 5.3 Collection ordering and sorting

- `LinkedHashMap`/`LinkedHashSet` everywhere (3.17). `Distinct()` is insertion-ordered.
- `List.Sort` is unstable in .NET; the single site (`SyntaxFacts.cs:773`) sorts 605 entries of
  which 287 have text `""`, then `GetOrAddValue` keeps the first per text. Only the `""` key is
  affected. Java: do not register `""` in `textToKindMap` and test that `TryGetKind("")` returns
  what the oracle returns.
- `OrderBy` (stable) → `List.sort` (stable). `StringComparer.OrdinalIgnoreCase` → upper-case
  per char then compare (`'_'` vs `'a'` ordering differs from `String.CASE_INSENSITIVE_ORDER`).
- `StringAndNumberComparer` quirks (`:97-115`) are mirrored with `// PORT-BUG`.

### 5.4 String comparison

- `==`, `string.Equals`, `Contains(string)`, `IndexOf(char)` are ordinal upstream → Java
  `equals`, `contains`, `indexOf`.
- `string.Compare` without `StringComparison` (17 sites), `StartsWith/EndsWith(string)` (10),
  `IndexOf(string)` (2), `OrderBy` on strings (2) are **culture-sensitive** upstream. Under the
  invariant-globalization policy they behave ordinally, and Java uses ordinal. Deviation from a
  culture-enabled .NET host is accepted and listed in section 9.
- `string.Compare(a, ia, b, ib, len)` compares `min(len, remaining)`; `DotNetStrings.compare`
  mirrors the clamp. `SyntaxToken.cs:552-563` depends on it.
- `OrdinalIgnoreCase` equality → `DotNetStrings.equalsOrdinalIgnoreCase` (upper-case only;
  Java `equalsIgnoreCase` also tries lower-case and differs on `K`/`K`).

---

## 6. Stack depth

Constants stay: `QueryParser.MaxDepth = 300`, `ForwardParser.MaxCallDepth = 30`,
`Properties.MaxAnalysisDepth = 500`. Measured need on Java 21: 1-2 MB for a cold parse at
300 levels (`java21-blockers.md` §5). Policy:

- `kusto-language` does not spawn threads. It documents a 4 MB stack requirement.
- The conformance harness and the example translator run parse/analyze on a thread created with
  `new Thread(null, r, "kql", 16L << 20)`.
- Tests at 299/301 (parser), 30/31 (forward), 499/501 (binder) nesting, on `-Xint`.
- Invariant "the parser never throws" is defined as: `KustoCode.parse`/`parseAndAnalyze` on a
  4 MB-stack thread returns for every input in the corpus and the fuzz set, with no
  `Throwable`. Lazy value conversion (`SyntaxToken.value()`) follows "Java throws iff the oracle
  throws", recorded in the golden as an outcome.

---

## 7. `utils.dotnet` compatibility package (no upstream file)

| Class | Contents |
|---|---|
| `Out<T>`, `IntRef` | holders (3.3) |
| `Func3..Func9` | functional interfaces (3.8) |
| `Linq` | eager LINQ helpers (3.6) |
| `DotNetStrings` | `trim`, `compare` (clamped region), `equalsOrdinalIgnoreCase`, `compareOrdinalIgnoreCase`, `toUpperInvariant` |
| `DotNetNumber`, `DotNetDecimal`, `DotNetBoolean`, `DotNetGuid` | TryParse semantics (5.2) |
| `TimeSpan`, `DateTime` | 100 ns tick value classes with .NET range, `MinValue/MaxValue`, parse, `c`/invariant format |
| `DotNet` | `str(Object)` (5.2), `hashCombine` |
| `Lazy` | `VarHandle` CAS publish helper (3.13) |
| `OperationCanceledException`, `Internal` (annotation) | |

Each class has unit tests pinned to .NET outputs captured by the oracle (`oracle --dotnet-facts`
emits a JSON of reference values for these helpers).

---

## 8. Scope cut and boundary stubs

Full lists: `porting/scope.json` and `porting/manifest.json`. Summary
(`porting/inventory/dependency-cut.md`):

- **Included**: root files (`KustoCode`, `GlobalState`, catalogs, `Options`, `Properties`,
  `KustoCache`, `KustoDialect`, `ServerKinds`), all of `Binder/`, `Symbols/`, `Diagnostics/`,
  `Utils/`, `Syntax/`, `Parser/` and `Parser/Combinators/`, plus nine Editor types the core
  references (`CodeKinds`, `CompletionHint`, `CompletionKind`, `CompletionPriority`+`Rank`,
  `CompletionItem`, `CompletionText`, `ClientDirective`, `ClientDirectiveArgument`).
  `CompletionHint` is semantic: the binder reads it (`Binder_Names.cs:851`).
- **Optional** (no path from the entry points, 682 lines): `CommandFacts`, `ScriptFacts`,
  `CommentFacts`, `TriviaFacts`, `CharScanners`, `ScannerExtensions`, `ParserExtensions`,
  `Editor/TextRange`. Ported last, or not at all.
- **Excluded**: the other 79 `Editor/**` files (16.7k lines), `TestHelpers.cs`,
  `AssemblyInfo.cs`, the command generator and its inputs (`CommandGenerator.cs`, `Grammar.cs`,
  `CommandInfo.cs`, `*CommandInfos.cs`, `CommandGenerator.t4`), `Kusto.Language.Bridge/`,
  `grammar/` (documentation only), build files.
- **Stubs (9)**:

| Stub | Shape | Why |
|---|---|---|
| `EngineCommands`, `DataManagerCommands`, `ClusterManagerCommands`, `AriaBridgeCommands` | `public static final List<CommandSymbol> All = List.of();` | T4 outputs of excluded command infos; read only by `GlobalState.GetCommands` |
| `EngineCommandGrammar`, `DataManagerCommandGrammar`, `ClusterManagerCommandGrammar`, `AriaBridgeCommandGrammar` | `extends CommandGrammar`, constructor only | read only by `CommandGrammar.CreateCommandGrammar` |
| `Editor/EditString` | `Empty`, ctor, `currentText()`, `length()`, `charAt`, `substring(start,length)`, `substring(start)`, `toString()`, `of(String)` | `ClientDirective` uses a read-only slice; the real class drags in editor types |

Consequences for `.`-prefixed (command) text, decided:

1. `CommandGrammar` with zero command parsers would throw in `PartialParser.FindBestPath`
   (`Enumerable.Max` on empty, `PartialParser.cs:365`). Guard in `CommandGrammar`:
   `partialCommand` returns `-1` when `commandParsers.length == 0`. `// PORT:`.
2. Commands parse as `UnknownCommand`; `VisitUnknownCommand` returns null, so a piped query
   (`.show tables | where …`) binds against a null row scope and reports false
   "name does not refer to any known item" errors. Decision: **mirror upstream's
   `UnknownCommand` path unchanged** and record the diagnostics as a documented, golden-pinned
   deviation. No synthetic open-table scope. Command text is out of scope for Graylog.
3. Queries embedded in commands (`.set-or-append T <| …`) are swallowed into
   `SkippedTokens` without diagnostics. Documented; a conformance test pins it.
4. Java entry points mirror `KustoCode`, not `KustoCodeService`: no 4 MiB text cap, no
   internal-failure diagnostic, no analyzers.

---

## 9. Deviation register

| # | Where | Deviation | Reason |
|---|---|---|---|
| D1 | all generic-arity families | numeric suffix names (2.4) | Java |
| D2 | ~15 overload groups | erasure renames (2.5) | Java |
| D3 | 37 multi-type files | one file per top-level type (2.2) | Java |
| D4 | partial classes | merged files, ordinal part order (2.2) | Java |
| D5 | `internal` members | `public @Internal` (2.6) | Java packages |
| D6 | generated nodes | one file per node; hand-written partials in protected regions (4) | Java |
| D7 | `Parsers<TInput>` | static generic methods + `LexicalTokenParsers` facade (3.10) | no static import from generic type |
| D8 | `QueryGrammar.Initialize` | split into region methods; forward locals become fields (3.19) | 64 KB |
| D9 | `ForwardParser.s_callDepth` | per-output-type `ThreadLocal` map (3.9) | erasure |
| D10 | `CommandGrammar.partialCommand` | guard for zero command parsers (8) | stub |
| D11 | `DateTime.TryParse` | `Z`/offset → UTC, not local (5.2) | determinism |
| D12 | culture-sensitive string APIs | ordinal (5.4) | invariant policy |
| D13 | `SyntaxFacts.textToKindMap` | `""` not registered (5.3) | unstable sort |
| D14 | yield methods | eager lists (3.4) | cold paths |
| D15 | `Interlocked.CacheLineSeparated` | dropped | dead code |
| D16 | catalog named arguments | `P.minOccurring(…)` builder (3.12) | Java |
| D17 | `KustoCode.ParseAndAnalyze` cancellation | mirrored bug: token ignored (`KustoCode.cs:172`) | `// PORT-BUG` |
| D18 | `MostRecentlyUsedCache.AddOrUpdate` | mirrored bug: keeps old value (`:64`) | `// PORT-BUG` |
| D19 | `KustoFacts.HasInteriorQuote` | mirrored bug: ignores `start` (`:878`) | `// PORT-BUG` |
| D20 | `ParserVisitors.IsParentVisitor.VisitFails` | mirrored `!=` (`:106`) | `// PORT-BUG` |
| D21 | Unicode tables | Java 21 (15.0) vs oracle ICU | accepted |

Rows are added as porting proceeds. The manifest `notes` field points at the row.

---

## 10. Decision log

| Decision | Choice | Why |
|---|---|---|
| Java version | **21 LTS** | no Java 25 blocker found; all 42 computed `base(...)` chains are inline-legal on 21 (`java21-blockers.md` §1). Revisit only for a concrete blocker. |
| Oracle runtime | **.NET 10 LTS**, `InvariantGlobalization=true`, `TZ=UTC`, built from the submodule sources at the pin | NuGet `Microsoft.Azure.Kusto.Language` 12.4.1 is 10 commits behind the pin (`critic.md` §1.5). Source build is exact and bump-proof. |
| Oracle code generation | compile `Kusto.Language.Generators/*.cs` as a library (they are valid C# under `#if !T4`) and call `SyntaxNodeGenerator.Generate` / `CommandGenerator.GenerateParser/GenerateSymbols` from a runner that writes the `.cs` outputs | avoids the T4 toolchain; `dotnet-t4` is the fallback |
| Generated Java | checked in + drift test (4) | reviewable diffs on bumps |
| Core dependencies | none | spec |
| Test JSON | Jackson in the conformance module only | |
| Tooling language | Python 3 stdlib for `porting/tools/*` | git/JSON plumbing; no build step |
| Property accessors | `foo()` | collision-free with upstream methods (2.3) |
| Stack | 4 MB thread documented; harness uses 16 MB | section 6 |

---

## 11. Upstream bump procedure

1. `git -C upstream/kusto-query-language fetch && git -C upstream/kusto-query-language checkout <new>`;
   update `UPSTREAM.md`.
2. `python3 porting/tools/upstream_diff.py <old> <new>`: lists changed upstream files, maps them
   through `porting/manifest.json`, classifies each (ported → patch needed; generated → regenerate;
   stubbed → re-check the stub; excluded → re-check the exclusion; new → triage). Exit code is
   non-zero when triage is needed.
3. Triage new files in `porting/scope.json`.
4. Regenerate: `mvn -pl kusto-language -am -Pregenerate generate-sources` after mirroring any
   `SyntaxNodeInfos.cs` change into `SyntaxNodeInfos.java`.
5. Apply patches to ported files in upstream diff order, keeping the member order.
6. Rebuild the oracle at the new pin; regenerate goldens:
   `oracle/run.sh regenerate`. Review the golden diff; it is the behavioural changelog.
7. `mvn verify`. Conformance must be green or the failures must be explained in the commit.
8. `python3 porting/tools/build_manifest.py` to refresh blob hashes; commit with the message
   `chore: bump upstream to <sha>` naming the manifest entries touched.
