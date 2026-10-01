# Upstream code generation: analysis and Java port design

Upstream pin: `9d95a2d5` (2026-09-29). All paths are relative to `upstream/kusto-query-language/` unless noted.
`src/` means `upstream/kusto-query-language/src/`.

## TL;DR

- Upstream has two T4 generators. **SyntaxNodeGenerator** emits all 225 syntax node classes plus 4 visitor classes into one file. **CommandGenerator** emits the control-command grammars and command symbol tables.
- **No generated output is checked in any more.** Commit `cd24dcf3` (2026-07-08) deleted all 9 generated `.cs` files (30,376 lines). Generation now happens at build time through an internal MSBuild target that is not in the public repo. The public repo cannot build on its own.
- The last checked-in `GeneratedSyntaxNodes.cs` (`git show cd24dcf3^:src/Kusto.Language/Syntax/CodeGen/GeneratedSyntaxNodes.cs`) is **exactly** what the current `SyntaxNodeInfos.cs` produces. A throwaway re-implementation of the generator reproduced all 18,859 lines from `namespace` to the closing brace. The only difference was the trailing newline that T4 appends.
- Query parsing and binding never need the command generator outputs. Excluding commands needs boundary edits in only 2 places: `GlobalState.cs:869-884` and `CommandGrammar.cs:447-463`.
- `grammar/*.g4` is documentation only. No code or build file uses it, and it already lags the C# parser.
- **Recommendation:** add a Java emitter in a separate `kusto-language-generator` module and **check in** its output. Add a drift test that fails the build when the checked-in files differ from what the emitter produces. Run regeneration on demand through a Maven profile, not on every build.

---

## 1. What is generated, by what, from what

### 1.1 Mechanism: dual-mode `.cs` files included by T4

The generator sources are written to work two ways: as normal C# and as T4 "class feature blocks":

- Each file opens with `// <#+` and closes with `// #>`. Examples: `src/Kusto.Language.Generators/SyntaxNodeGenerator.cs:1` and `:682`, and `SyntaxNodeInfos.cs:1` and `:3344`.
- The namespace and usings are wrapped in `#if !T4` (`SyntaxNodeGenerator.cs:2-12`, `:679-681`).
- Each template compiles with `compilerOptions="/d:T4"` (`src/Kusto.Language/Syntax/CodeGen/GeneratedSyntaxNodes.tt:1`, `src/Kusto.Language/Parser/CodeGen/CommandGenerator.t4:1`). Under T4 the namespace wrappers therefore disappear and the classes become template members.
- Side effect: the leading `// ` before each `<#+` ends up in the output. That is why generated files start with one `// ` line per included file (3 for syntax nodes, 4 for commands).

`src/Kusto.Language.Generators/Kusto.Language.Generators.csproj` is just `<Project Sdk="Microsoft.NET.Sdk">` with `AllowUnsafeBlocks`. It has no `TargetFramework` and no entry point, and it is not in `src/Kusto.Language.sln`. It only exists so the IDE can edit the generator sources. Nothing invokes it as a program.

### 1.2 Template -> generator -> input -> output

| Template (`src/Kusto.Language/...`) | Includes | Call | Input data | Output (deleted in `cd24dcf3`) | Lines at `cd24dcf3^` |
|---|---|---|---|---|---|
| `Syntax/CodeGen/GeneratedSyntaxNodes.tt` | `CodeGenerator.cs`, `SyntaxNodeGenerator.cs`, `SyntaxNodeInfos.cs` (`.tt:11-13`) | `SyntaxNodeGenerator.Generate(SyntaxNodeInfos.All, SyntaxNodeInfos.KnownTypes)` (`.tt:14`) | `SyntaxNodeInfos.cs` | `Syntax/CodeGen/GeneratedSyntaxNodes.cs` | 18,882 |
| `Parser/CodeGen/EngineCommandGrammar.tt` | `CommandGenerator.t4`, `EngineCommandInfos.cs` | `new CommandGenerator().GenerateParser("EngineCommandGrammar", typeof(EngineCommandInfos))` | `EngineCommandInfos.cs` + `Grammar.cs` | `Parser/CodeGen/EngineCommandGrammar.cs` | 8,555 |
| `Parser/CodeGen/EngineCommands.tt` | same | `GenerateSymbols("EngineCommands", typeof(EngineCommandInfos))` | `EngineCommandInfos.cs` | `Parser/CodeGen/EngineCommands.cs` | 2,635 |
| `Parser/CodeGen/DataManagerCommandGrammar.tt` / `DataManagerCommands.tt` | `CommandGenerator.t4`, `DataManagerCommandInfos.cs` | `GenerateParser` / `GenerateSymbols` | `DataManagerCommandInfos.cs` (1 command) | `DataManagerCommandGrammar.cs` / `DataManagerCommands.cs` | 46 / 28 |
| `Parser/CodeGen/ClusterManagerCommandGrammar.tt` / `ClusterManagerCommands.tt` | ... `ClusterManagerCommandInfos.cs` | same | 1 command (`show version`) | `ClusterManagerCommandGrammar.cs` / `ClusterManagerCommands.cs` | 46 / 28 |
| `Parser/CodeGen/AriaBridgeCommandGrammar.tt` / `AriaBridgeCommands.tt` | ... `AriaBridgeCommandInfos.cs` | same | 1 command (`show version`) | `AriaBridgeCommandGrammar.cs` / `AriaBridgeCommands.cs` | 46 / 28 |

`CommandGenerator.t4:11-13` includes `Grammar.cs`, `CommandInfo.cs` and `CommandGenerator.cs`. `Grammar.cs` (2,426 lines) is a parser and analyzer for the command-grammar mini-language: `GrammarParser` at :46, `Grammar` nodes at :709, `GrammarWriter` at :1589, `GrammarAnalysis` at :1866. It is used only by the generator. No runtime code in `Kusto.Language` references it.

### 1.3 Are the outputs checked in? No, not since 2026-07-08.

- `git ls-files` lists only the `.tt` and `.t4` files under both `CodeGen/` directories.
- `git show --stat cd24dcf3` deleted the 9 outputs listed above. The same commit removed 81 lines of `<Compile Update=... DependentUpon=...tt>` and `TextTemplatingFileGenerator` items from `Kusto.Language.csproj`.
- Generation now happens through `src/Kusto.Language/Directory.Build.targets:3-6`. It imports `..\..\..\BuildProcessTemplates\Kusto.Build.targets` or `...\TextTemplating\Kusto.T4Transform.targets`, and both imports are guarded by `Exists(...)`. These live in Microsoft's internal monorepo, not in this repo.
- `src/Kusto.Language.Bridge/Kusto.Language.Bridge.csproj:31-44` says this explicitly: the generated `.cs` files go under `obj/`, and `CodeGen/*.cs` is excluded from the compile glob.
- The generated header itself says `Do not check-in this file!` (`CodeGenerator.cs:46-62`).
- **Consequence:** a fresh clone of the public repo has no `GeneratedSyntaxNodes.cs`. Classes such as `BinaryExpression` exist nowhere in the working tree, so `dotnet build` would fail. Our C# reference for the generated code is git history (`cd24dcf3^`) or the published NuGet assembly.

Other files that say they are "generated" are **checked-in outputs of external tools** that are not in the repo. Port them as ordinary hand-written sources:
- `src/Kusto.Language/Functions.Convert.cs:2-5`: "auto-generated by the ConverUnits.CodeGen utility class ... DO NOT MODIFY" (342 lines).
- `src/Kusto.Language/Options.cs:10-13`: "generated by running tests: Kusto.Engine.UT.exe -run:TestQueryOptions" (255 lines).

### 1.4 Are the historical outputs in sync with the current generator?

**Syntax nodes: yes, exactly.**

History:
- `SyntaxNodeInfos.cs`, `SyntaxNodeGenerator.cs` and `CodeGenerator.cs` each have one commit: `6c977323` (2025-11-12), where they were added to the public repo.
- `GeneratedSyntaxNodes.cs` was last changed in `904f4a95` (2025-08-22).
- `git diff --stat cd24dcf3 HEAD -- src/Kusto.Language.Generators` shows only `EngineCommandInfos.cs` changing after the deletion (+45/-5).

Full verification: `dotnet` is not installed, so I re-implemented `SyntaxNodeGenerator` plus the `CodeGenerator` and `IndentedTextWriter` semantics in about 200 lines of Python. The Python reads `SyntaxNodeInfos.cs` with regexes. I diffed its output against `git show cd24dcf3^:.../GeneratedSyntaxNodes.cs` from the `namespace Kusto.Language.Syntax` line onward:

```
gen: 18859 lines   hist: 18860 lines
diff: 18859a18860  > (empty line)      <- only the trailing newline T4 appends
```

The script was scratch work in `/tmp` and has been deleted. It parsed 225 classes (16 abstract) and 687 properties. This proves two things. The historical file can serve as the golden reference for the Java emitter. And the generator logic is deterministic and small enough to port.

Three spot checks (SyntaxNodeInfos line -> generated line at `cd24dcf3^`):

1. **`NameReference`** (`SyntaxNodeInfos.cs:236-246` -> generated `:996-1055`). `Kind = "NameReference"` becomes `public override SyntaxKind Kind => SyntaxKind.NameReference;`. `Name` (IsSyntax) becomes `Attach(name)`. `Match` (`IsSyntax=false`, type `Kusto.Language.Symbols.SymbolMatch`) becomes a plain assignment, is excluded from children (`ChildCount => 1`), and is passed through unchanged in `CloneCore`. No `Completion` on `Name`, so no `GetCompletionHintCore` override. Matches.
2. **`BinaryExpression`** (`SyntaxNodeInfos.cs:417-428`, no `Kind` -> generated `:1973-2050`). Emits `private readonly SyntaxKind kind;` and `Kind => this.kind`. The ctor takes a leading `SyntaxKind kind`, and `CloneCore` passes `this.Kind` first. `Operator` has `Completion="Syntax"`, so `GetCompletionHintCore` has `case 1: return CompletionHint.Syntax;`. The parameter `operator` is escaped to `@operator` (`CodeGenerator.GetCamelCase`, `CodeGenerator.cs:336-357`). Matches.
3. **`NamedParameter`** (`SyntaxNodeInfos.cs:715-727`). `ExpressionHint` has `DefaultValue="CompletionHint.None"` and `IsSyntax=false`, which produces ctor parameter `CompletionHint expressionHint = CompletionHint.None`. The field is assigned without `Attach`, and `ChildCount => 3`. Matches.

**Commands: the historical outputs are stale.** `EngineCommandInfos.cs` gained 8 commands after the deletion: `ShowDatabasePolicyRowId`, `AlterDatabasePolicyRowId`, `DeleteDatabasePolicyRowId`, `Show/ShowStar/Alter/DeleteExternalTablePolicyRowLevelSecurity` and `ShowClockDiagnostics`. This does not matter if commands are excluded (section 4).

---

## 2. How SyntaxNodeGenerator works

### 2.1 Description model (`SyntaxNodeGenerator.cs:16-182`)

| Type | Fields | Used by the generator? |
|---|---|---|
| `KnownTypeInfo` (:16-37) | `Name`, `Namespace`, `Immutable`, `CopyByValue` | Copied into `m_knownTypes` (:214-222) but **never read**. `SyntaxNodeInfos.KnownTypes` is empty (:3336-3338). Dead. |
| `SyntaxNodeInfo` (:42-94) | `Name`, `Doc`, `Remarks`, `Base`, `Abstract`, `Sealed`, `ConstructionOptions`, `CloneOptions`, `Properties[]`, `Kind` | All read except `ConstructionOptions`. |
| `ConstructorGenerationOptions` (:99-110) | `Default`, `SuppressAllPropertiesConstructor` | **Never consulted.** Dead. |
| `SyntaxNodeCloneOptions` (:115-136) | `Default`, `AppendExpressionResultTuple`, `AppendExpressionResultAndSymbolicName`, `Custom` | Only `Custom` is checked (:588). Never set in `SyntaxNodeInfos.cs`. |
| `SyntaxNodeProperty` (:141-182) | `Name`, `Type`, `Doc`, `PublicSetter`, `Optional`, `DefaultValue`, `IsSyntax` (default `true`), `Completion` | All read. `PublicSetter` is never set in data. `Remarks` is never set either. |

Feature usage in `SyntaxNodeInfos.cs`: 225 nodes, 16 abstract. 7 non-abstract nodes have no `Kind`: `LiteralExpression`, `PrefixUnaryExpression`, `BinaryExpression`, `InExpression`, `HasAnyExpression`, `HasAllExpression`, `BetweenExpression`. Across 687 properties:
- 93 are `Optional`.
- 1 has a `DefaultValue` (`:727`).
- 4 have `IsSyntax=false`: `NameReference.Match` `:246`, `NamedParameter.ExpressionHint` `:727`, `CustomCommand.CommandKind` `:3271`, `PartialCommand.CommandKinds` `:3286`.

`Completion` values are names of `Kusto.Language.Editor.CompletionHint` members. The most common are `Syntax`, `Keyword`, `Scalar`, `None` and `Clause`.

Property types are either node types (including generics such as `SyntaxList<SeparatedElement<Expression>>`), `SyntaxToken`/`SyntaxElement`, or 4 non-node types: `string`, `IReadOnlyList<string>`, `CompletionHint` and `Kusto.Language.Symbols.SymbolMatch`. The Java emitter needs a small type map for those 4. No property name collides with a Java reserved word after camel-casing (checked against the Java 21 keyword list).

**Array order matters.** It sets the emitted class order, the `Visit*` order in the visitors, and the child index order (`GetChild`/`GetName`/`IsOptional`/`GetCompletionHintCore` cases) within each node. Command nodes sit at the end (`SyntaxNodeInfos.cs:3199` "Commands" through `:3333`): `CommandWithClause`, `CommandWithValueClause`, `CommandWithPropertyListClause`, `Command`, `UnknownCommand`, `CustomCommand`, `PartialCommand`, `CommandAndSkippedTokens`, `BadCommand`, `CommandBlock`.

### 2.2 Output structure

`Generate` (:198-232) validates the data, writes the header (`WriteHeader(".tt", "CslTreeGenerator.t4")`, which is a stale template name, :286) and a using block (`System`, `System.Collections.Generic`, plus aliases `CompletionKind`/`CompletionHint` from `Kusto.Language.Editor`, :288-296). It then emits `namespace Kusto.Language.Syntax { #region SyntaxNodes ... #region Visitors ... }`.

Per node (`WriteClassesImpl`, :384-619), inside `#region class X`:

1. Doc comment from `Doc`/`Remarks` (:401). Then `public [abstract |sealed ]partial class X : Base` (:402, `GetAbstractOrSealed` :646-661).
2. **Kind** (non-abstract only, :407-419). With `Kind` set: `public override SyntaxKind Kind => SyntaxKind.<Kind>;`. Without: a `private readonly SyntaxKind kind;` field and `Kind => this.kind`.
3. **Properties** (:421-433): `public T Name { get; }`, or `{ get; set; }` if `PublicSetter`.
4. **Constructor** (:436-481). It is `internal`. Parameters are the flattened properties of the whole lineage, base first (`GetLineage` :633-644), plus `IReadOnlyList<Diagnostic> diagnostics = null`. A leading `SyntaxKind kind` is added when `Kind == null`. The call is `: base(<base-lineage props>, diagnostics)`. In practice only `SyntaxNode` and props-less abstract bases are used, so it reduces to `: base(diagnostics)`.
   The body runs only for non-abstract nodes. It assigns `kind`, then each property as `this.P = Attach(p[, optional: true])` if `IsSyntax`, else `this.P = p`, then calls `this.Init();`. Abstract nodes get an empty-body ctor.
5. Non-abstract only (:485-565):
   - `ChildCount => <number of IsSyntax props>`
   - `GetChild(int)` switch, returning the property or throwing `ArgumentOutOfRangeException`
   - `GetName(int)` switch with `nameof(P)`
   - `IsOptional(int)`, only if some syntax property is `Optional`: grouped `case i:` labels, then `return true;`, `default: return false;`
   - `protected override CompletionHint GetCompletionHintCore(int)`, only if some syntax property has `Completion`: `case i: return CompletionHint.<C>;`, `default: return CompletionHint.Inherit;`
6. Non-abstract only (:568-615):
   - `Accept(SyntaxVisitor)` calls `visitor.VisitX(this)`
   - `Accept<TResult>(SyntaxVisitor<TResult>)` returns `visitor.VisitX(this)`. A 3-arg context visitor variant exists but is compiled out with `#if false`, :581-586.
   - `protected override SyntaxElement CloneCore(bool includeDiagnostics)`, unless `CloneOptions == Custom`. It does `new X([this.Kind, ]<(T)P?.Clone(includeDiagnostics) for syntax | P for non-syntax>, (includeDiagnostics ? this.SyntaxDiagnostics : null))`.
   - There is **no** `Clone()`, `Kind` enum or factory emitted beyond this. `Clone` itself is hand-written (`SyntaxElement.cs:1155-1157`, `SyntaxNode.cs:35`).

The visitors (`WriteVisitorsImpl`, :314-380) emit 4 partial classes over the 209 non-abstract nodes, in array order:
- `public partial class SyntaxVisitor`: `public abstract void VisitX(X node);`
- `public partial class DefaultSyntaxVisitor : SyntaxVisitor`: `protected abstract void DefaultVisit(SyntaxNode node);` plus `VisitX => this.DefaultVisit(node)`
- `public partial class SyntaxVisitor<TResult>` and `DefaultSyntaxVisitor<TResult> : SyntaxVisitor<TResult>`: the same with a return value.

Formatting rules matter if we want byte-level golden tests. They live in `CodeGenerator.cs:179-310`. `WriteEmptyLineIfNeeded` writes a blank line unless the previous line was blank or ended in `{`. `WriteRegion` resets `m_emptyAbove`. Blank lines carry trailing indentation, because `IndentedTextWriter.OutputTabs` (:504-515) runs before empty `WriteLine()`s.

### 2.3 Hand-written partial halves the generator relies on

The generated classes are `partial`. These hand-written partials must merge into the same Java class:

| Generated class | Hand-written half |
|---|---|
| `Expression` | `src/Kusto.Language/Syntax/SyntaxNode.cs:107-123` (`IsLiteral`, `LiteralValueInfo`, `LiteralValue`) and `Syntax/SyntaxNode_Semantics.cs:233+` (`ResultType`, `RawResultType`, ...) |
| `LiteralExpression`, `CompoundStringLiteralExpression`, `TypeOfLiteralExpression`, `DynamicExpression` | `SyntaxNode.cs:125-190`. Note the cached private fields `_literalValue` and `_literalInfo`. |
| `NameDeclaration`, `NameReference` | `SyntaxNode.cs:192-215`. These add **extra public constructors** chaining to the generated one, and `SimpleName`. |
| `Name`, `TokenName`, `BracketedName`, `BracedName`, `WildcardedName`, `BracketedWildcardedName` | `SyntaxNode.cs:217-245` (`SimpleName`) |
| `NamedParameter` | `SyntaxNode.cs:247-260` (overrides `GetCompletionHint`, returning `ExpressionHint` for index 2) |
| `Directive` | `SyntaxNode.cs:262+` |
| `SyntaxVisitor`, `DefaultSyntaxVisitor`, `SyntaxVisitor<TResult>`, `DefaultSyntaxVisitor<TResult>` | `Syntax/SyntaxVisitor.cs:10-60` (`VisitCustom`, `VisitList`, `VisitSeparatedElement`). The `abstract` modifier only appears on the hand-written half. |

Hooks the generated code calls, all hand-written:
- `SyntaxElement.Init()` (`SyntaxElement.cs:53`), overridden in `SyntaxNode.cs:24`
- `Attach(...)`
- the virtual `ChildCount`, `GetName`, `IsOptional`, `GetCompletionHintCore` (`SyntaxElement.cs:270-311`)
- `CloneCore` (`SyntaxElement.cs:1157`)
- `Accept` (`SyntaxNode.cs:37-39`)

`CompletionHint` (`src/Kusto.Language/Editor/CompletionHint.cs`, 173 lines) lives in the Editor namespace. Every generated node with a `Completion` references it, and so do `SyntaxElement.cs:306-311`. It must be ported as a boundary type even though the editor is out of scope.

---

## 3. Java port of the generator: design and recommendation

### 3.1 Options

| | A. Build-time generation | B. Checked-in output plus drift test (recommended) | C. Hand-port the 18.9k-line C# output |
|---|---|---|---|
| Source of truth | `SyntaxNodeInfos.java` | `SyntaxNodeInfos.java` | the Java files |
| IDE experience | Needs `generate-sources` before navigation works. The generator module must build first. | Plain sources; works immediately | Plain sources |
| Hand-written partial merge | Needs fragment files outside the compiled tree, which the IDE cannot check | Protected regions in real Java files, compiled and refactorable | Manual |
| Upstream bump | Automatic | Run `-Pregenerate` and review the diff | Painful (225 classes x 7 members) |
| Matches upstream practice | Matches post-2026-07 internal | Matches upstream through 2026-07 (checked in with `DependentUpon`) | n/a |

**Recommend B.** Reasons:
1. `SyntaxNodeInfos.cs` changes rarely. It has had 1 commit since it was published, and the old generated file changed 3 times in 2025. Running a generator on every build buys almost nothing.
2. 16 classes plus 4 visitors need hand-written halves merged in. That is only practical when those halves sit in compiled, IDE-visible Java files.
3. Reviewers can read generated Java diffs on upstream bumps.
4. Library consumers never see the generator, and the main module keeps a plain Maven build.

### 3.2 Module layout

```
kusto-java/
  pom.xml                                  (parent, <modules>)
  kusto-language-generator/                (jar, Java 21, zero deps)
    src/main/java/org/graylog/kusto/language/generator/
      CodeGenerator.java                   mirror of Kusto.Language.Generators/CodeGenerator.cs (writer utilities)
      SyntaxNodeGenerator.java             mirror of SyntaxNodeGenerator.cs (model types + emitter, same member order)
      SyntaxNodeInfos.java                 mirror of SyntaxNodeInfos.cs (data), line-for-line
      GenerateSyntaxNodes.java             main(): args = target src/main/java dir
    src/test/java/.../SyntaxNodeInfosMatchUpstreamTest.java   (optional, see 3.5)
  kusto-language/
    pom.xml                                depends on kusto-language-generator, <scope>test</scope>
    src/main/java/org/graylog/kusto/language/syntax/*.java    (generated, checked in)
    src/test/java/.../GeneratedSyntaxNodesUpToDateTest.java
```

The existing `porting/inventory/generators-and-bridge.json` proposes putting `SyntaxNodeInfos.java` under `kusto-language/src/main/java/.../generator/`. I recommend a **separate module** instead. That keeps generator code out of the shipped jar, and the test-scope dependency is enough for the drift test.

### 3.3 Maven mechanics

- **Drift test (every build):** `GeneratedSyntaxNodesUpToDateTest` calls `SyntaxNodeGenerator.generate(SyntaxNodeInfos.ALL, ...)` in memory. It gets one string per output file, reads the hand-written protected regions from the checked-in file, splices them in, and asserts the result equals the checked-in file. On failure it prints the regenerate command. This needs only `maven-surefire-plugin` and the test-scope dependency.
- **Regeneration (on demand):** a `regenerate` profile in `kusto-language/pom.xml` binds `org.codehaus.mojo:exec-maven-plugin` goal `java` to `generate-sources`, with `mainClass=org.graylog.kusto.language.generator.GenerateSyntaxNodes`, `classpathScope=test` (so the test-scoped generator is on the classpath), and `arguments=${project.basedir}/src/main/java`. Command: `mvn -pl kusto-language -am -Pregenerate generate-sources`. The `-am` flag builds the generator first.
- Do **not** use `build-helper-maven-plugin add-source`. With option B the output already lives in `src/main/java`.

### 3.4 Emitter specifics (C# template to Java output)

The emitter must follow the project-wide naming conventions decided elsewhere (getter style and so on). Below are the generator-specific mappings.

- **Files.** Upstream emits one file with 229 classes. Java needs one public top-level type per file: emit `syntax/<Name>.java` for each of the 225 nodes, plus the 4 visitor files. This is a documented deviation from the "one Java file per C# file" rule, and it is forced by Java. `GeneratedSyntaxNodes.java` does not exist.
- **Partials, via protected regions.** The emitter writes `// <hand-written from="Syntax/SyntaxNode.cs:107-123"> ... // </hand-written>` blocks in each affected class. On regeneration it preserves existing region contents verbatim. Put them at the end of the class body, matching C# merge order: generated half first, hand-written partial last. For `Expression` there are two regions, one per source file. The emitter keeps a fixed table of class to upstream origin, taken from the table in section 2.3. When a region is missing, the generator emits a stub region for the classes in that table and fails anything else.
- **Generic-arity name clash.** `SyntaxVisitor` and `SyntaxVisitor<TResult>` (and the `Default*` pair) cannot share a simple name in Java. The proposal is `SyntaxVisitor` (void) / `SyntaxVisitorT<TResult>`, or `...ResultVisitor`. This must be one global naming rule, because the same clash appears elsewhere, for example `Parser<TInput>` and `Parser<TInput,TOutput>`.
- **Constructor visibility.** C# `internal` spans the assembly, and the parser that calls these constructors is in `Kusto.Language.Parsing` (`Parser/QueryGrammar.cs:8`, `Parser/CommandGrammar.cs:7`). In Java that is a different package, so emit `public` constructors. A package-private constructor plus a factory would break the mirror.
- **Default parameters.** `diagnostics = null` and `expressionHint = CompletionHint.None` become telescoping overloads: the full constructor, plus one without `diagnostics`, plus (for `NamedParameter`) one without both trailing defaults.
- `Attach(x, optional: true)` -> `attach(x, true)`. `nameof(P)` -> `"P"`. `ArgumentOutOfRangeException` -> `IndexOutOfBoundsException`. `sealed` -> `final`. `(T)P?.Clone(d)` -> `P != null ? (T) P.clone(d) : null`. Generic casts such as `(SyntaxList<NamedParameter>)` are unchecked, so add `@SuppressWarnings("unchecked")` on `cloneCore`.
- `GetCamelCase` escapes only `operator` -> `@operator`. In Java, `operator` is a legal identifier. No property collides with a Java keyword.
- `Accept<TResult>` -> `public <TResult> TResult accept(SyntaxVisitorT<TResult> visitor)`.
- `this.Init()` as the last ctor statement keeps the same semantics: a virtual call from the constructor, after the leaf's fields are assigned. All nodes with a kind are leaves (`sealed`), so Java field-initializer ordering is not a hazard. The hand-written cached fields have no initializers.
- The type map covers only `string` -> `String`, `IReadOnlyList<string>` -> `List<String>`, `Kusto.Language.Symbols.SymbolMatch` -> `org.graylog.kusto.language.symbols.SymbolMatch` (import), and `CompletionHint` -> the ported editor enum. Every other type name passes through unchanged.
- Drop the dead model features (`KnownTypes`, `ConstructionOptions`, unused `CloneOptions` values, `GetPublicOrProtected`, `IsDerivedFromCslNode`), or mirror them as unused members. Mirroring keeps diffs against upstream trivial, so that is recommended.
- **Data mirror style.** C# object initializers become a fluent builder so each upstream line maps to one Java line. For example, `new SyntaxNodeProperty { Name = "Left", Type = "Expression", Doc = "..." }` -> `prop().name("Left").type("Expression").doc("...")`. Keep the upstream comments, including `:2797`, `:2874` and `:3199`.

### 3.5 Verifying the port

1. **Golden comparison against upstream C#.** A test-only "C# mode" is not needed. Instead, compare the Java emitter's **structure** against the historical C# file: class order, kinds, child names and order, `IsOptional` sets, completion hints, and constructor parameter order.
   Do this once, at port time, with `git show cd24dcf3^:src/Kusto.Language/Syntax/CodeGen/GeneratedSyntaxNodes.cs`, which is proven equal to the current generator's output (section 1.4).
2. **`SyntaxNodeInfosMatchUpstreamTest` (optional, cheap).** It parses `upstream/kusto-query-language/src/Kusto.Language.Generators/SyntaxNodeInfos.cs` from the submodule with about 60 lines of regex. The scratch Python proved regexes parse all 225 nodes and 687 properties correctly. It then asserts that `SyntaxNodeInfos.java` holds the same data in the same order. That catches a missed data change after an upstream bump. Skip the test when the submodule is absent.
3. **Long-term C# oracle.** Git history will not reflect future upstream changes, because upstream no longer commits the output. For a fresh C# reference, decompile the generated types from the `Microsoft.Azure.Kusto.Language` NuGet package that matches the pin.

---

## 4. Command generator: needed for query parsing and binding?

**No.** Every runtime reference to the generated command classes:

| Reference | Purpose |
|---|---|
| `src/Kusto.Language/Parser/CommandGrammar.cs:447-463` `CreateCommandGrammar` | `new EngineCommandGrammar(globals)` / `DataManager...` / `ClusterManager...` / `AriaBridge...`. The default branch is `new CommandGrammar(globals)` ("no defined commands"). |
| `src/Kusto.Language/GlobalState.cs:869-884` `GetCommands` | `EngineCommands.All`, ... The default branch is `EmptyReadOnlyList<CommandSymbol>.Instance`. |

Nothing in `QueryGrammar.cs` or `QueryParser.cs` touches commands. The only "command" hit is a comment at `QueryParser.cs:755`. Commands are reached only when `KustoCode.GetKind` sees a leading `DotToken` (`KustoCode.cs:339-341`). That routes to `CommandGrammar.From(globals).CommandBlock` (`KustoCode.cs:209-213`).

### Boundary edits

Mark each with a `PORT-NOTE` comment:

1. `GlobalState.java` `getCommands`: keep the switch shape, but every arm returns the empty list. Equivalently, keep only the default branch. Keep `getCommand` (`GlobalState.cs:847-867`) as is.
2. `CommandGrammar.java` `createCommandGrammar`: always `return new CommandGrammar(globals);`.
3. **Port** `CommandGrammar.cs` (871 lines; base class with the empty `CreateCommandParsers`, `:499-503`). `KustoCode` needs it for any `.`-prefixed input. With zero command parsers, such input produces `UnknownCommand`/`BadCommand` nodes with diagnostics, not a crash. Also port `PredefinedRuleParsers.cs` (395 lines). Its only consumers are the generated parsers, `CommandGrammar.cs:45` and `Editor/Kusto/KustoCompleter.cs:3333`, but it is plain combinator code over `QueryGrammar`. Porting it keeps the `CommandGrammar` mirror exact and leaves the door open for commands later.
4. **Keep** `Symbols/CommandSymbol.cs` (71 lines). It is used by `GlobalState.GetCommand` and `Binder/Binder_NodeBinder.cs:4631-4640` (`VisitCustomCommand`). It is never instantiated without the generated tables.
5. **Keep** the 10 command syntax nodes in `SyntaxNodeInfos` (`:3199-3333`). The generated visitors and the binder's `VisitCommandBlock`, `VisitCustomCommand` and similar overrides (`Binder_NodeBinder.cs:4621-4655`) need them, and they cost nothing.
6. Keep the `KustoDialect` command values and `ServerKinds`. They are plain constants. `KustoCode.Dialect` (`KustoCode.cs:113-125`) switches on them.

Do **not** port: `CommandGenerator.cs`, `Grammar.cs`, `CommandInfo.cs`, `{Engine,DataManager,ClusterManager,AriaBridge}CommandInfos.cs` (about 640 `CommandInfo` entries in Engine), or `Parser/CodeGen/*.tt` / `*.t4`.

If commands come into scope later, the generated `EngineCommandGrammar` is 8.5k lines produced by a real grammar compiler: `GrammarParser`, `GrammarAnalysis`/`MergeAnalysis` and shape maps (`CommandGenerator.cs:321-505`). Porting that generator would be its own project.

---

## 5. `grammar/` (Kql.g4, KqlTokens.g4): documentation only

- Contents: `grammar/Kql.g4` (1,550 lines) and `grammar/KqlTokens.g4` (485 lines). There is also `grammar/.antlr/`, which holds ANTLR 4.13.1 output (`KqlParser.java`, `KqlLexer.java`, listeners, `.interp`). Its header reads `// Generated from c:/Kusto1/dev/Src/Grammar/Kql.g4 by ANTLR 4.13.1`, which is IDE (VS Code ANTLR extension) cache leftover.
- No `.cs`, `.csproj`, `.props`, `.targets`, `.json` or `.md` file references `Kql.g4`, `KqlTokens` or ANTLR. The only "Antlr" hit is the unrelated `GrammarStyle.Antlr` text style in `src/Kusto.Language.Generators/Grammar.cs:1582`. The C# parser is the hand-written `QueryParser.cs` plus the combinator `QueryGrammar.cs`.
- It already lags the C# parser. Last grammar commit: `cfef9efe` (2025-10-12). `QueryParser.cs` and `QueryGrammar.cs` were last changed in `516d6b20` (2026-06-16). Query-operator keywords that are in `SyntaxFacts.cs` and `QueryParser.cs` but missing from `KqlTokens.g4` include `graph-where-nodes`, `graph-where-edges` and `project-by-names`. Most other missing keywords are command-only.
- **Recommendation:** ignore it for the port, and do not add an ANTLR dependency. At most, use it as a reading aid or a source of fuzz-test inputs. It is not a spec.
