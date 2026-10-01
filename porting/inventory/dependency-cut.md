# Dependency cut: Kusto.Language -> Java 21

Upstream pin: `9d95a2d5`. All paths are relative to `upstream/kusto-query-language/src/Kusto.Language/` unless prefixed.
Machine-readable form: `dependency-cut.json` (192 include, 108 exclude, 9 stubs).

## TL;DR

- The cut is clean. In-scope code touches **9 Editor types**. All are small. Port 8 as-is. Stub 1 (`EditString`).
- Commands can be stubbed. Command text still parses, as `UnknownCommand`. Eight generated classes become empty stubs.
- **One required deviation.** Base `CommandGrammar` with zero command parsers likely throws. Add a guard.
- **Blocker outside this cut:** concrete syntax node classes are T4-generated and not in the repo. The generator inputs are on the include list.
- `KustoCache`, `Options`, `Properties`: required. `TestHelpers`: exclude (test-only).
- Runtime include: ~69.8k C# lines. Of that, 1,236 lines come from Editor. 682 lines are optional and off-path.

## Method

1. Started at `KustoCode.Parse` (KustoCode.cs:147) and `ParseAndAnalyze` (164). Followed `Create` (200-257).
2. Collected all 122 type names declared under `Editor/`. Grepped each, word-bounded, across root `*.cs`, `Parser`, `Syntax`, `Binder`, `Symbols`, `Diagnostics`, `Utils`.
3. Cross-checked with a grep for qualified `Editor.X` references, and for files that import the Editor namespace.
4. Did the same for the command surface: `CommandGrammar`, `CommandSymbol`, `*Commands`, `*CommandGrammar`, `KustoDialect`, `ServerKinds`, plus the command syntax nodes.
5. Checked the result against the `referencesEditor` and `referencesCommands` fields in the sibling inventories. They agree. This pass adds `CompletionRank`, `CompletionText` and `EditString`.

## 0. Generated code is absent (read this first)

`Syntax/CodeGen/GeneratedSyntaxNodes.tt` emits every concrete `SyntaxNode` class plus the `SyntaxVisitor` Visit* methods. The output is not checked in. `grep "class BinaryExpression"` finds nothing.
The same is true for `EngineCommands`, `EngineCommandGrammar` and the DM/CM/AriaBridge variants (`Parser/CodeGen/*.tt`).

Consequence: the include list carries these as build-time inputs:
- `Kusto.Language.Generators/SyntaxNodeInfos.cs`: 226 node definitions.
- `Kusto.Language.Generators/SyntaxNodeGenerator.cs`
- `Kusto.Language.Generators/CodeGenerator.cs`

The generator emits `using CompletionHint=Kusto.Language.Editor.CompletionHint` (SyntaxNodeGenerator.cs:292-293). It also emits `GetCompletionHintCore` overrides (549-561). So generated nodes depend on `CompletionHint` too.
`Syntax/SyntaxNode.cs` holds 16 `partial class` extensions of generated nodes (for example `Directive` at 262 and `NamedParameter` at 247). Each must merge into its generated Java class.

## 1. Editor types referenced by in-scope code

Classes: **(a)** port as-is. **(b)** boundary stub. **(c)** reference sits in a droppable member.

| Editor type | File (lines) | Referenced from (file:line) | Class | Why |
|---|---|---|---|---|
| `CodeKinds` | CodeKinds.cs (32) | KustoCode.cs:120,132,133,210,215,341,358 | a | Four string constants. Drive the query/command split. |
| `CompletionHint` | CompletionHint.cs (173) | Binder_Names.cs:851-857; SyntaxElement.cs:306,311; SyntaxNode.cs:249-257; CustomNode.cs:108-234; SeparatedElement.cs:43-48; QueryParser.cs:3409-3417; QueryGrammar.cs:859,1019,1028,1520,1526,3448,3573-3603,3912; PredefinedRuleParsers.cs:179-315; SyntaxParsers.cs:142-144; CommandGrammar.cs:12,710,716; generated `NamedParameter.ExpressionHint` (Generators/SyntaxNodeInfos.cs:727) | a | **Semantic.** The binder reads it to decide whether a name sits in tabular context (Binder_Names.cs:851). `[Flags]` enum, so use int constants or an `EnumSet` wrapper. |
| `CompletionKind` | CompletionKind.cs (153) | SyntaxParsers.cs:105-456 (23); QueryGrammar.cs (148); CommandGrammar.cs:13,749-779 | a | Plain enum. Used in grammar annotations. |
| `CompletionPriority` | CompletionPriority.cs (35) | SyntaxParsers.cs:105-456; QueryGrammar.cs:1538-2624 (32) | a | Enum. `Default = Normal` is an alias, so in Java it becomes a static constant. |
| `CompletionRank` | CompletionPriority.cs:8-22 | QueryGrammar.cs:578,586,587,594,601,608,620,626,782 | a | Same file as `CompletionPriority`. |
| `CompletionItem` | CompletionItem.cs (434) | SyntaxParsers.cs:114,134-136,366,390-413,450-456,664; QueryGrammar.cs:578-3601 (18 sites); CommandGrammar.cs:190 (`DisplayText`) | a | An opaque parser annotation. Only Editor reads it, apart from `DisplayText` in CommandGrammar's TagFinder. Self-contained. A stub would need the same constructors anyway. |
| `CompletionText` | CompletionText.cs (101) | transitive: CompletionItem.cs:38,205-211,284-311; reached via SyntaxParsers.cs:413 `WithApplyTexts(string)` | a | Depends only on Utils. |
| `ClientDirective` | ClientDirective.cs (218) | SyntaxNode.cs:282-295 (`Directive.Info` -> `TryParse`) | a | **Semantic.** `#database`/`#connect` change the current cluster and database (Binder_Misc.cs:656-760). They also emit diagnostics (Binder_NodeBinder.cs:4665). |
| `ClientDirectiveArgument` | ClientDirectiveArgument.cs (29) | SyntaxNode.cs:277; Binder_Misc.cs:711-781 | a | Value class: `Name`, `Text`, `Value`. |
| `EditString` | EditString.cs (825) | transitive via ClientDirective.cs:13,23,28,38,41-46,60,91,99,146,154,198; SyntaxNode.cs:272 | **b** | Editor change-tracking. It would also pull in `TextEdit`, `PositionBias` and `TextRange`. ClientDirective uses only a read-only slice of it. |
| `TextRange` | TextRange.cs (55) | ScriptFacts.cs:90,121,123,134,145 | a (optional) | Its only user is `ScriptFacts`, which nothing in scope reaches. Port both or drop both. |

No type in category (c) exists. No in-scope Editor reference sits in a droppable member.
Unused imports: `GlobalState.cs:7` and `Parser/TriviaFacts.cs:5` import `Kusto.Language.Editor` but use none of its types. Drop them in Java.
Nothing in `Symbols/` or `Diagnostics/` references Editor.

## 2. Commands

### What KustoCode does with command text

- `GetKind` (KustoCode.cs:331-359) skips `#directive` lines. If the first remaining token is `.`, the text is `CodeKinds.Command`.
- `Create` (210-214) then calls `CommandGrammar.From(globals).CommandBlock.ParseFirst(tokens)`. This is the combinator grammar, never `QueryParser`.
- `CommandGrammar.From` (378-415) returns a cached default grammar when `ServerKind == GlobalState.Default.ServerKind` (`Engine`, GlobalState.cs:1171).
- `CreateCommandGrammar` (448-463) switches on `ServerKind`. It returns the generated `EngineCommandGrammar` / `DataManagerCommandGrammar` / `ClusterManagerCommandGrammar` / `AriaBridgeCommandGrammar`, or a plain `CommandGrammar` (461).
- The base `CommandGrammar` ctor (30-161) always builds `QueryGrammar.From(globals)` (42) and `PredefinedRuleParsers` (45). It then calls the virtual `CreateCommandParsers` (499-503). The base version returns an empty array.
- Fallback chain (110-115): `First(commandAndSkippedTokens, UnknownCommand, BadCommand)`. `UnknownCommand` (519-525) is a dot followed by every token up to `|`, `<|`, `;` or end of text. `|` pipes into query operators still work (117-126).
- The query path (215-229) always builds `QueryGrammar.From(globals).QueryBlock`, even when `ParserKind.Default` routes the actual parse to `QueryParser` (223). `KustoCode.Grammar` (42) stores it. Only Editor reads it.

### What GlobalState holds about commands

- `ServerKind` string (59, default `ServerKinds.Engine` at 195).
- `commandMap` (118), lazily filled by `GetCommand(name)` (847-865) from `GetCommands(serverKind)` (869-884). That method reads `EngineCommands.All` / `DataManagerCommands.All` / `ClusterManagerCommands.All` / `AriaBridgeCommands.All`.
- `WithServerKind` (839-842).
- Only one consumer: `Binder_NodeBinder.VisitCustomCommand` (4631-4640). It turns `CustomCommand.CommandKind` into a `SemanticInfo` carrying `CommandSymbol.ResultType`.

### Other command references in in-scope code (all kept, no stubs needed)

- Syntax nodes `Command`, `CustomCommand`, `UnknownCommand`, `PartialCommand`, `BadCommand`, `CommandBlock`, `CommandAndSkippedTokens`, `CommandWithValueClause`, `CommandWithPropertyListClause`: generated. They are part of the tree model.
- Binder: Binder_ContextBuilder.cs:256-277 and 474-498; Binder_TreeBinder.cs:1031-1056 (`$command_results` variable); Binder_NodeBinder.cs:4606-4652; Binder_Names.cs:282-378 (create-function detection via `CustomNode`).
- `KustoFacts.GetSyntaxTabularity` (Parser/KustoFacts.cs:1363) and `KustoFacts.CanBeIdentifier` (607-672). The latter uses `KustoDialect` and the static keyword set in `KustoFacts_Keywords.cs:14`. That set is not generated.
- `DiagnosticFacts.GetMissingCommand` (Diagnostics/DiagnosticFacts.cs:1121).
- `Symbols/CommandSymbol.cs` (71 lines): port as-is.

### Decision

Exclude the command grammar generator and the 642 engine command definitions. Stub the 8 generated classes:

```java
// org/graylog/kusto/language/EngineCommands.java  (likewise DataManagerCommands, ClusterManagerCommands, AriaBridgeCommands)
public final class EngineCommands {
    private EngineCommands() {}
    public static final List<CommandSymbol> All = List.of();
}

// org/graylog/kusto/language/parsing/EngineCommandGrammar.java  (likewise the other three)
public class EngineCommandGrammar extends CommandGrammar {
    public EngineCommandGrammar(GlobalState globals) { super(globals); }
    // createCommandParsers NOT overridden -> empty array from base
}
```

`CommandGrammar.CreateCommandGrammar` then stays a verbatim mirror. Real generated grammars can replace the stubs later without touching callers.

### Required deviation: empty command-parser set

The base `CommandGrammar` has a likely crash with zero command parsers. Evidence:
- `partialCommand` (CommandGrammar.cs:53-78) calls `PartialParser.ScanPartialBest(commandParsers, ...)` (57).
- That leads to `PathFinder.FindBestPath` (Combinators/PartialParser.cs:351-389). It runs `results.Max(r => r.path.InputLength)` (365) on an empty `List`. `InputLength` is a non-nullable `int` (869).
- In .NET, `Enumerable.Max` over an empty sequence throws `InvalidOperationException`.
- `bestCommandOrPartial` scans `partialCommand` for every input that starts with `.`.

Upstream reaches this only through the `default:` branch (461), which handles unrecognised server kinds. With our stubs, every server kind reaches it.
Fix: in Java `CommandGrammar`, mark a `// PORT-DEVIATION` guard. Either return `-1` from the `partialCommand` scanner when `commandParsers.length == 0`, or leave `commandAndSkippedTokens` out of the `First(...)`.
I could not confirm this by running it: there is no dotnet toolchain on this machine. Add a Java test.

### Observable behaviour vs upstream (Engine)

- `.show tables` upstream gives `CustomCommand(CommandKind=ShowTables)` with a typed result schema. Ours gives `UnknownCommand`. Neither emits syntax diagnostics.
- Binding: `VisitUnknownCommand` returns null (Binder_NodeBinder.cs:4647). `$command_results` gets the error type (Binder_TreeBinder.cs:1047). Upstream gets the command schema.
- `.cmd | where ...`: the query side parses identically. It binds against an error row scope, so semantic diagnostics may differ. Verify with a test.
- `create function` bodies inside commands no longer get `CustomNode` parents. So `IsInsideCreateFunctionCommand` (Binder_Names.cs:282) is always false.
- Queries are unaffected. `QueryParser` and `QueryGrammar` hold no command references beyond `SkippedTokens`.

Restoring command support later means porting `Generators/CommandGenerator.cs` (2109 lines), `Generators/Grammar.cs` (2426) and `EngineCommandInfos.cs` (3777), or running the T4 templates once and mirroring the C# output.

## 3. TestHelpers, KustoCache, Options, Properties

| File | Needed? | Evidence |
|---|---|---|
| `TestHelpers.cs` | **No** | `internal static class` (11). No caller in the library. It is exposed to `Kusto.Language.UT` via `Properties/AssemblyInfo.cs` (`InternalsVisibleTo`). Port it into Java test sources if the cache tests are ported. Keep `KustoCode._localCache` (76) package-private. |
| `KustoCache.cs` | **Yes** | `Binder.TryBind` runs `globals.WithCache()` then `Cache.GetOrCreate<GlobalBindingCache>()` (Binder_API.cs:168-169; also 283/288, 314-315, 349-350, 383-384). Also `QueryGrammar.From` (QueryGrammar.cs:41-43) and `CommandGrammar.From` (385, 411). In Java, key it by `Class<?>`. `GetOrCreate<T>() where T : new()` needs a `Supplier<T>`. |
| `Options.cs` | **Yes** | `GlobalState.Default` passes `Language.Options.All` (GlobalState.cs:1174). |
| `Properties.cs` | **Yes** | `MaxAnalysisDepth` (Syntax/SyntaxTree.cs:54, the binder safety gate), `MaxCachedResultTypes` (Binder_FunctionCalls.cs:1992), `MaxCachedExpansions` (2202), `AllowClientParameters` (Binder_NodeBinder.cs:586). `MaxParseTextSize` is Editor-only (KustoCodeService.cs:74) but it is the same static class, so keep it. |
| `Properties/AssemblyInfo.cs` | No | Holds only `InternalsVisibleTo`. |

## 4. Include, exclude, stub lists

The full per-file list is in `dependency-cut.json`. Summary:

**Include (runtime):**
- Root: `KustoCode`, `GlobalState`, `Functions` (+ `Functions.Convert` merged), `Aggregates`, `PlugIns`, `Operators`, `FunctionHelpers`, `FunctionBodyFacts`, `QueryOperatorParameters`, `Options`, `Properties`, `KustoCache`, `KustoDialect`, `ServerKinds`.
- All of `Binder/`, `Symbols/` (including `CommandSymbol`), `Diagnostics/`, `Utils/` and `Syntax/*.cs`.
- All of `Parser/` and `Parser/Combinators/`. That includes `CommandGrammar` (with the guard), `PredefinedRuleParsers` (built at CommandGrammar.cs:45) and `PartialParser` (CommandGrammar.cs:57,65).
- Editor (a): `CodeKinds`, `CompletionHint`, `CompletionKind`, `CompletionPriority` (+ `CompletionRank`), `CompletionItem`, `CompletionText`, `ClientDirective`, `ClientDirectiveArgument`.

**Include, optional (no path from the entry points; 682 lines):**
- `Parser/CommandFacts.cs`, `ScriptFacts.cs`, `CommentFacts.cs`, `TriviaFacts.cs`, `CharScanners.cs`, `ScannerExtensions.cs`, `Combinators/ParserExtensions.cs`, and `Editor/TextRange.cs`.
- None of them is referenced from in-scope code. Their only users are Editor or each other.

**Include, build-time only:** `Syntax/CodeGen/GeneratedSyntaxNodes.tt`, plus `Generators/SyntaxNodeInfos.cs`, `SyntaxNodeGenerator.cs` and `CodeGenerator.cs`.

**Exclude:**
- The other 79 `Editor/**` files (16,753 lines).
- `TestHelpers.cs` and `Properties/AssemblyInfo.cs`.
- The 8 command `.tt` files and `CommandGenerator.t4`.
- The command generator inputs (`CommandGenerator.cs`, `Grammar.cs`, `CommandInfo.cs`, `*CommandInfos.cs`).
- `Kusto.Language.Bridge/`, and build or docs files.

**Stubs (9):**

| Stub | Java path | Members | Referenced from |
|---|---|---|---|
| `EngineCommands` | `EngineCommands.java` | `All = List.of()` | GlobalState.cs:874 |
| `DataManagerCommands` | `DataManagerCommands.java` | same | GlobalState.cs:876 |
| `ClusterManagerCommands` | `ClusterManagerCommands.java` | same | GlobalState.cs:878 |
| `AriaBridgeCommands` | `AriaBridgeCommands.java` | same | GlobalState.cs:880 |
| `EngineCommandGrammar` | `parsing/EngineCommandGrammar.java` | ctor `(GlobalState)` only | CommandGrammar.cs:452 |
| `DataManagerCommandGrammar` | `parsing/DataManagerCommandGrammar.java` | same | CommandGrammar.cs:454 |
| `ClusterManagerCommandGrammar` | `parsing/ClusterManagerCommandGrammar.java` | same | CommandGrammar.cs:456 |
| `AriaBridgeCommandGrammar` | `parsing/AriaBridgeCommandGrammar.java` | same | CommandGrammar.cs:458 |
| `EditString` | `editor/EditString.java` | see below | ClientDirective.cs (12 sites); SyntaxNode.cs:272 |

`EditString` stub surface. Line numbers are in EditString.cs:

```java
public final class EditString {
    public static final EditString Empty = new EditString("");   // 52
    public EditString(String text)                                 // 44
    public String getCurrentText()                                 // 24
    public int length()                                            // 57 (Length)
    public char charAt(int index)                                  // 62 (indexer)
    public EditString substring(int start, int length)             // 174
    public EditString substring(int start)                         // 201
    @Override public String toString()                             // 129
    public static EditString of(String text)                       // replaces implicit string->EditString (80)
}
```

In C#, ClientDirective passes `EditString` to `TokenParser.Scan*(string, int)` and `TextFacts.GetLineEnd/GetNextLineStart(string, int)` via the implicit conversion at line 72. In Java those calls need an explicit `.toString()`.
Cheaper option: retype ClientDirective's `EditString` members as `String` and skip the stub. That is a documented signature deviation.

## Verification tests to add in the Java port

1. `KustoCode.parse(".show tables")`: no exception. Root is `CommandBlock`, first statement is `ExpressionStatement(UnknownCommand)`, and there are no diagnostics. This guards the empty-parser fix.
2. `KustoCode.parseAndAnalyze(".show tables | where x > 1")`: no exception. Record the diagnostics as a golden baseline.
3. `#database "c" "db"\nT`: the binder resolves `T` against database `db` when the cluster is defined in `GlobalState`. This exercises ClientDirective, the EditString stub and Binder_Misc.
4. `T | where x > 1` with `ParserKind.Grammar` versus `ParserKind.Default`: identical trees. This exercises QueryGrammar with completion annotations.
5. `GlobalState.Default.withServerKind("Unknown")`, then parse `.foo`. This hits the `default:` branch of `CreateCommandGrammar`.

## Open risks

- **Empty-parser crash.** This is static analysis only, with no dotnet available. Upstream's `default:` branch suggests it was never exercised. Test 1 confirms it either way.
- **Ported vs stubbed CompletionItem.** If the team wants an even smaller cut, `CompletionItem` and `CompletionText` (535 lines) could become a stub with the two public constructors, `WithApplyTexts(String)` and `DisplayText`. Nothing in scope reads the other members. The table above recommends porting as-is, because the stub surface is nearly the same size.
- **Generated nodes.** The Java port is blocked on generating them, from `SyntaxNodeInfos.cs` or by running the T4 once. The other inventories (`generators-and-bridge.json`, `syntax.json`) own that work.
