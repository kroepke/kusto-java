# Fidelity, tests, public API (upstream Kusto.Language 12.4.1)

Upstream: `upstream/kusto-query-language` @ 9d95a2d5. `src/Kusto.Language/version.txt` = `12.4.1`. No git tags.
All paths below are relative to `upstream/kusto-query-language/src/Kusto.Language/` unless prefixed `src/`.
Not executed: `dotnet` is not installed here. Every claim is from reading code. Nothing was run.

## Headline findings

1. The tree is full-fidelity by construction. Trivia is leading-only. Trailing source trivia lives on the `EndOfTextToken`. `ToString()` (= `IncludeTrivia.All`) reproduces the input exactly.
2. No tests exist in this repo. Not one. The test project `Kusto.Language.UT` is private.
3. The repo does not build as-is. The syntax node classes, visitors and engine command symbols are T4-generated and the output is not committed. The Java port must run its own generator.
4. There are two query parsers. A hand-written recursive-descent `QueryParser` (default) and a combinator `QueryGrammar` (`ParserKind.Grammar`). The combinator grammar is also needed for commands and for completion.
5. `GlobalState.AddOrUpdateDatabase` does not exist. It is `ClusterSymbol.AddOrUpdateDatabase`.
6. `Binder` is `internal`. `SyntaxNode.GetSemanticInfo()` is `internal`. The public surface is `KustoCode` plus `ReferencedSymbol`/`ResultType` on nodes.

---

## TASK A: Syntax tree fidelity

### A1. Where trivia is stored: leading only

- `SyntaxToken.Trivia` is "Any whitespace or comments preceding this token" (`Syntax/SyntaxToken.cs:22-25`).
- `LexicalToken.Trivia` is "preceeds the proper text of the token" (`Parser/LexicalToken.cs:22-25`).
- There is no trailing-trivia field anywhere. `FullWidth = Trivia.Length + Text.Length` (`SyntaxToken.cs:52`). `TriviaWidth` is the leading trivia of the first token (`Syntax/SyntaxElement.cs:1127-1128`).
- Trailing text in the source (spaces, comments after the last token) is the trivia of the end token. `TokenParser.Parse` emits `EndOfTextToken` with the pending trivia when `trivia.Length > 0 || options.AlwaysProduceEndToken` (`Parser/TokenParser.cs:111-114`).
- `KustoCode.Parse` and `ParseAndAnalyze` force `WithAlwaysProduceEndTokens(true)` (`KustoCode.cs:152`, `169`). So `KustoCode` trees always have an end token. Even for `""`.
- Without that option, empty text yields zero tokens (`TokenParser.cs:53-64`, `ParseTokens` loop breaks on null). Direct `QueryParser.ParseQuery(string)` callers get no end token when there is no trailing trivia. Nothing is lost either way.
- Trivia chars: `TextFacts.IsWhitespace` (`Parser/TextFacts.cs:12-45`). This includes `\r \n \t`, form feed, NBSP, U+1680, U+180E, U+2000-200B, U+202F, U+205F, U+3000, and **U+FEFF (BOM)** (`TextFacts.cs:39`). Comments are `//` to end of line (`TokenParser.cs:573-583`). The comment includes its line break (`GetNextLineStart`, `TokenParser.cs:585-589`).
- `ParseTrivia` (`TokenParser.cs:457-508`) returns substrings of the source verbatim. Only interning of spaces-only trivia is a shortcut (`s_spaces`). Comment-bearing trivia is never interned (`intern: false`).

### A2. Does `ToString(IncludeTrivia)` reproduce the source exactly? Yes, for `All`.

- `SyntaxElement.ToString()` calls `ToString(IncludeTrivia.All)` (`SyntaxElement.cs:1161-1164`).
- The walk is `WalkTokens(token => token.Write(builder, includeTrivia, start))` (`SyntaxElement.cs:1171-1189`). `Write` appends `Trivia` then `Text` for `All` (`SyntaxToken.cs:88-136`, case at `97-99`).
- `WalkTokens` skips zero-width tokens (`GetNextToken(..., includeZeroWidthTokens: false)`, `SyntaxElement.cs:713-731`). Missing tokens and an empty end token have FullWidth 0, so no text is lost.
- Token text is exact:
  - Keywords, punctuation, operators: `KindToken.Text = SyntaxFacts.GetText(kind)` (`SyntaxToken.cs:300`). The lexer only produces these kinds on an exact, case-sensitive longest match (`TokenParser.cs:119-148`, `SubstringMap.GetLongestMatch` in `Utils/SubstringMap.cs:23-43`). So the canonical text equals the source text. `SyntaxToken.From` drops `LexicalToken.Text` for these categories (`SyntaxToken.cs:166-173`) and that is safe only because of that exact match.
  - Identifiers, literals, "Other" (bad, directive, end): original text kept (`SyntaxToken.cs:165`, `169`, `176`).
- Multi-token merges keep text. `SyntaxParsers.ProduceSyntaxToken` (`Parser/SyntaxParsers.cs:203-258`) concatenates `token.Text` and refuses when a non-first token has trivia (`:213-214`). `QueryParser.GetCombinedTokenText` (`Parser/QueryParser.cs:1036-1053`) includes inner trivia by default. The wildcard path stores `text` (with inner trivia) and `valueText` (without) (`QueryParser.cs:994-999`). Braced names require zero trivia (`QueryParser.cs:1117-1145`).
- Other modes are lossy by design (`SyntaxElement.cs:1195-1217`):
  - `Interior` drops leading trivia of the first token.
  - `Minimal` replaces trivia with one `\n` or one space. Also drops end-token trivia (`SyntaxToken.cs:108-123`).
  - `SingleLine` replaces with a single space.
- Per-token `SyntaxToken.ToString(IncludeTrivia)` returns `Trivia+Text` only for `All`, else `Text` (`SyntaxToken.cs:76-86`).
- Cached text: `fullWidth` is precomputed per token (`SyntaxToken.cs:266`). Node `FullWidth` = sum of children (`SyntaxElement.cs:1106-1121`, `SyntaxNode.cs:24-30`). So `root.FullWidth == text.Length` is the invariant. I found no assertion of it in the repo. Add it to the Java test suite.

### A3. Missing and skipped tokens

- **Missing token** = `SyntaxToken.MissingToken` (`SyntaxToken.cs:745-769`). Kind set, `Text == ""`, `IsMissing => true`, FullWidth = trivia length only. Factories: `SyntaxToken.Missing(...)` (`:230-238`), `SyntaxParsers.CreateMissingToken` (`Parser/SyntaxParsers.cs:23-56`). Every caller passes `""` trivia. Missing tokens carry a diagnostic. They consume no source.
- `SyntaxElement.IsMissing` default: `Width == 0 && ContainsSyntaxDiagnostics` (`SyntaxElement.cs:213`). `HasMissingChildren` (`:316-331`).
- A missing token is inserted when the parser fails to find a required token. The parser does not consume anything for it (`QueryParser.ParseRequiredToken`, `QueryParser.cs:335-338`).
- **Skipped tokens**: a `SkippedTokens` node holding a `SyntaxList<SyntaxToken>` of real tokens.
  - Query: `QueryParser.ParseSkippedTokens` (`QueryParser.cs:7011-7032`) consumes every remaining token until end. It tags only the first with `DiagnosticFacts.GetIncompleteFragment()`. `ParseQuery` order is directives, statements, skipped, end token (`QueryParser.cs:7050-7058`). Grammar twin at `Parser/QueryGrammar.cs:3654-3677`.
  - Command: `commandSkippedTokens` and `commandBlockSkippedTokens` (`Parser/CommandGrammar.cs:86-90`, `129-133`, `146`). The block-level one has "no diagnostic".
- **Bad character**: `TokenParser` emits one `BadToken` per UTF-16 code unit (`GetSubstring(text, pos, 1)`) with `GetUnexpectedCharacter` (`TokenParser.cs:199-201`). A surrogate pair becomes two bad tokens. Text still round-trips. Java `String` is UTF-16 so offsets match.
- A bad token that appears mid-statement is just a token the parser may skip or fold into `SkippedTokens`. It is never dropped.

### A4. Cases where text is normalised or dropped

I found none that lose source text. Behaviours to mirror exactly:

| Case | Behaviour | Cite |
|---|---|---|
| Unterminated `'..'` / `".."` string | Token runs to just before the next `\r` or `\n`, or EOT. Text excludes the line break. `StringLiteralToken` gets "missing `'`" diagnostic with `RelativeEnd` location. The line break becomes the next token's trivia. | `TokenParser.cs:216-247`, content scan `:880-920` (`\r`/`\n` stop at `:905-908`) |
| Unterminated multi-line string (```` ``` ```` or `~~~`) | Runs to EOT. Diagnostic on missing closing quote. | `TokenParser.cs:249-285`, `ScanMultiLineStringLiteralContent` `:922-932` |
| Lone `` ` `` or `~` | `IsStringLiteralStartQuote` accepts them (`:208-214`) but `ParseStringLiteral` returns null unless it is the triple form (`:301`). Falls through to `BadToken`. | `TokenParser.cs:81-97`, `:216-301` |
| `h"..."`, `H'..'`, `@"..."` | Prefix is part of the token text. | `TokenParser.cs:152-160`, `:216-232` |
| Parenthesised literals `datetime(...)` etc. | Scanned to `)` or end of line (or end of text if `AllowLiteralsWithLineBreaks`). Missing `)` gives a diagnostic, text kept. | `TokenParser.cs:122-143`, `ScanGoo` `:1086-1106` |
| Directive `#...` | Token text is the rest of the line, excluding the line break. | `TokenParser.cs:104-110` |
| BOM U+FEFF | Whitespace trivia. Kept. Not stripped. | `TextFacts.cs:39` |
| CR / LF / CRLF / U+2028 / U+2029 | Never normalised. They are trivia chars (or comment tail). `GetLineBreakLength` treats `\r\n` as one break (`TextFacts.cs:132-153`). Line break set: `IsLineBreakStart` (`:117-130`). Single-quoted strings only stop at `\r`/`\n`, not U+2028/9. | `TokenParser.cs:905-908` |
| `Value`/`ValueText` for literals | Computed lazily from `Text`. Original `Text` stays. | `SyntaxToken.cs:373-457` |
| `valueText` on wildcard names | Drops inner trivia when `AllowNonAdjacentWildcardParts`. `Text` keeps it. | `QueryParser.cs:994-999` |
| `DateTime.TryParse`, `TimeSpan.TryParse`, `Double.TryParse` | Culture-dependent, default culture, failures return default. Java port needs explicit fixed-culture rules. Not a fidelity issue for text, but a semantic one. | `SyntaxToken.cs:584-743` |

Gotcha: `#if !BRIDGE` at `SyntaxToken.cs:641-647`. The Bridge (JS) build skips `TimeSpan.TryParse` and uses only the hand-rolled suffix parser. Decide which behaviour the Java port mirrors. Ask for a golden answer from the .NET build.

### A5. Other facts that affect the port

- `TriviaStart` is lazily computed from `OffsetInParent` chains, or eagerly via `InitializeTriviaStarts` (`SyntaxElement.cs:1062-1100`). Offsets are in UTF-16 code units.
- `SyntaxNode.Init()` sets `OffsetInParent`/`IndexInParent` and `fullWidth` (`SyntaxElement.cs:53-67`, `SyntaxNode.cs:24-28`). Nodes are immutable after construction. Parent is set by `Attach` (`SyntaxElement.cs:72-91`). A element can have only one parent, so `Clone` is needed to reuse (`:1155-1157`).
- Diagnostics are stored in `ExtendedData` (`SyntaxElement.cs:184-188`), alongside `SemanticInfo`. Both are mutable after tree creation (binder writes via `Binder.DefaultSetSemanticInfo`, `Binder/Binder_API.cs:204-214`). This is the one mutable part of an otherwise immutable tree.
- Diagnostic locations may be `RelativeEnd` or relative to an element until resolved (`Diagnostics/Diagnostic.cs:70-90`, `DiagnosticLocationKind` at `:222`). `GetContainedDiagnostics` resolves them (`Syntax/SyntaxNode_Semantics.cs:108-230`).
- Generated nodes: 226 `SyntaxNodeInfo` entries (16 abstract) in `src/Kusto.Language.Generators/SyntaxNodeInfos.cs` (3343 lines). Generated shape: ctor `Attach`es children, `ChildCount`, `GetChild`, `GetName`, `IsOptional`, `GetCompletionHint`, `Accept` x2, `CloneCore` (`SyntaxNodeGenerator.cs:402-630`). A third `Accept<TContext,TResult>` is behind `#if false` (`:581-586`).
- `SyntaxToken.From` category switch (`SyntaxToken.cs:154-178`) is the single lexer-to-tree bridge.
- Round-trip risk list for the Java test suite: comments without trailing newline, `\r`-only files, trailing whitespace only, empty input, lone `'`, lone `` ` ``, NUL char mid-text, lone surrogate, BOM at start, `datetime(` unterminated, wildcard with `AllowNonAdjacentWildcardParts`, 10k-deep nesting (stack-safe path, `QueryParser.cs:136-200`, `MaxDepth = 300`).

---

## TASK B: Upstream testing inventory

### B1. What exists

- `TestHelpers.cs` (62 lines). `internal static class TestHelpers` in namespace `Kusto.Language`. Not tests. Hooks for the private test project:
  - `GetGlobalExpansionCacheSize(GlobalState)` (`:13-21`)
  - `GetGlobalCachedExpansionCount(GlobalState, Signature)` (`:23-33`)
  - `GetGlobalCachedResultTypeCount(GlobalState, Signature)` (`:35-45`)
  - `GetLocalCachedExpansionCount(KustoCode, Signature)` (`:47-50`, reads `code._localCache`)
  - `GetLocalCachedResultTypeCount(KustoCode, Signature)` (`:52-55`)
  - `Bind(SyntaxNode, GlobalState)` (`:57-61`, `new SyntaxTree(syntax)` then `Binder.TryBind`)
  They reveal what the real suite pokes at: binder caches (`GlobalBindingCache.CallSiteToExpansionMap`, `CallSiteToResultTypeMap`, `LocalBindingCache`).
- `Properties/AssemblyInfo.cs:5`: `[assembly: InternalsVisibleTo("Kusto.Language.UT")]`. Confirms the unit test assembly is named `Kusto.Language.UT` and uses internals (`KustoCode.Tree`, `KustoCode.Grammar`, `KustoCode._localCache`, `SyntaxNode.GetSemanticInfo`, `Binder`).
- `src/Kusto.Language.Bridge/runtests.cmd`: one line, `..\..\..\test\ut\Kusto.Language.JS.UT\runtests.html`. Opens an HTML runner in a `test/ut/Kusto.Language.JS.UT` folder that is **outside this repo** (three levels up from `src/Kusto.Language.Bridge` is above the repo root).
- `bridge.json`: Bridge.NET compiler config (JS output to `$(OutDir)/bridge/`, console on, source maps, `generateTypeScript: true`). No test config.
- `package.json`: npm package `@kusto/language-service-next`, `main: ./Kusto.Language.Bridge.js`, types `.d.ts`. No scripts, no test entry.
- `Kusto.Language.Bridge.csproj`: `net472`, `BRIDGE` define, compiles `..\Kusto.Language\**\*.cs` minus `CodeGen` folders; T4 output goes to this project's `obj`.
- `src/Kusto.Language.sln` has two projects only: `Kusto.Language` and `Kusto.Language.Bridge`. No test project. The `Generators` project is not in the sln.
- `.github/workflows/` has one file: CodeQL (`build-mode: none`). No build, no test CI.
- `readme.md` (`src/Kusto.Language/readme.md`). The only runnable-looking samples. Pseudo-C# with `Assert.AreEqual` snippets (sections: parse, ParseAndAnalyze, diagnostics, columns/tables referenced, `GetCalledFunctionBody`, `GetTable`/`GetDatabase`/`GetCluster`, declaring schemas). Good seed cases for smoke tests. Example data: `T | project a = a + b | where a > 10.0` with `TableSymbol("T","(a: real, b: real)")`; `Shapes`/`TallShapes`/`ShortShapes`.
- `grammar/Kql.g4` (1550 lines), `grammar/KqlTokens.g4` (485), plus generated ANTLR4 Java in `grammar/.antlr/` (`KqlParser.java` 23096 lines, `KqlLexer.java`, listeners, `.interp`). Not a test. Possibly useful as an independent grammar oracle for accept/reject. It is a separate artefact and not the source of truth. Git log shows it is auto-synced (`cfef9efe`). It is already Java, but it is a different (ANTLR) design. Do not mirror it.
- Parameter `examples:` metadata on built-in functions (e.g. `Functions.cs:1073` `KustoFacts.AgoExamples`) and `Parameter.Examples` (`Symbols/Parameter.cs:67`). Only a sliver of sample values. Not a corpus.
- Large generated data inputs (not tests): `src/Kusto.Language.Generators/EngineCommandInfos.cs` (3777 lines) holds command schemas.

### B2. Is any of the private suite mirrored here?

No. Evidence:
- `find` for `*test*`, `*.kql`, `*.csl`, `*sample*` finds only `TestHelpers.cs` and `runtests.cmd`.
- `git log --name-only` over all 1262 commits matches only: `doc/welch-testfunction.md` (a doc about a function named "test"), `src/TestHelpers.cs` (older location of the same helper), `src/Kusto.Language/TestHelpers.cs`, `runtests.cmd`. No `*.UT`, no test directory ever existed in this history.
- Commits are all `Auto-sync from Azure-Kusto-Service` (plus a few merged PRs). This repo is a one-way export from the private Azure-Kusto-Service repo. Tests stay there.
- `runtests.cmd` and `InternalsVisibleTo` point at test locations not present.

### B3. What the visible material covers, and what it does not

Covers: nothing automated. Documents the API by example (readme) and exposes cache-introspection hooks.
Does not cover: lexer, parser, round-trip, diagnostics text/codes/positions, binder semantics, function/operator signatures, result types, schema parsing, command parsing, IntelliSense, formatter, JS output.

### B4. Consequences for the port

- No golden corpus exists. We must build one. Needs a .NET toolchain to generate expected output, or hand-derived expectations.
- Strategy options (decision for the plan):
  1. Install .NET SDK, build `Kusto.Language` with T4 output (needs `dotnet-t4` run over the three `.tt` templates' includes under `src/Kusto.Language.Generators`, see headline finding 3), write a small C# dumper (tokens, tree shape, diagnostics, result types as JSON) and diff against Java for a query corpus.
  2. Or use the NuGet package `Microsoft.Azure.Kusto.Language` (README names it) at the matching version as the oracle. This is cheaper: no T4 step. Pin the same version as the submodule if one exists on nuget (12.4.1 by `version.txt`; verify on nuget.org, not checked here).
  3. Seed queries from Microsoft Learn KQL docs, readme.md, `Examples` metadata. Licensing needs a check.
- Java must expose package-private hooks equivalent to `TestHelpers` for cache tests, if cache behaviour is ported.
- Always add the invariants: `root.toString() == source`, `root.fullWidth == source.length`, token starts monotonic.

---

## TASK C: Public API surface for translators

Namespaces: `Kusto.Language` (root), `.Syntax`, `.Symbols`, `.Parsing`, `.Binding`, `.Utils`, `.Editor`.

### C1. Entry points: `KustoCode.cs`

| Member | Signature | Line |
|---|---|---|
| class | `public sealed class KustoCode` | 17 |
| `Text` | `string Text { get; }` | 22 |
| `Kind` | `string Kind { get; }` (`CodeKinds.Query` / `Command` / `Directive`) | 27 |
| `Tree` | `internal SyntaxTree Tree { get; }` | 32 |
| `Syntax` | `SyntaxNode Syntax => Tree.Root` | 37 |
| `HasSemantics` | `bool` | 47 |
| `ResultType` | `TypeSymbol ResultType { get; }` (only after analysis; last `ExpressionStatement`'s `Expression.ResultType`, `DetermineResultType` `:262-290`) | 54 |
| `Globals` | `GlobalState Globals { get; }` | 59 |
| `MaxDepth` | `int MaxDepth => Tree.Depth` | 64 |
| `Dialect` | `KustoDialect Dialect` | 114 |
| `Parse` | `static KustoCode Parse(string text, GlobalState globals = null)` | 147 |
| `ParseAndAnalyze` | `static KustoCode ParseAndAnalyze(string text, GlobalState globals = null, CancellationToken cancellationToken = default)` | 164 |
| `Analyze` | `KustoCode Analyze(GlobalState globals = null, CancellationToken cancellationToken = default)` | 296 |
| `WithGlobals` | `KustoCode WithGlobals(GlobalState globals, CancellationToken cancellationToken = default)` | 316 |
| `GetKind` | `static string GetKind(string text)` | 331 |
| `GetDiagnostics` | `IReadOnlyList<Diagnostic> GetDiagnostics(CancellationToken = default)` (syntax + semantic) | 366 |
| `GetSyntaxDiagnostics` | `IReadOnlyList<Diagnostic> GetSyntaxDiagnostics(CancellationToken = default)` | 389 |
| `GetSymbolsInScope` | `(int position, SymbolMatch match = Any, IncludeFunctionKind include = All, CancellationToken)` | 403 |
| `GetSpeculativeReferencedSymbol` | `Symbol (int position, string name, SymbolMatch match = Any, CancellationToken)` | 418 |
| `GetColumnsInScope` | `TableSymbol (int position, CancellationToken)` | 431 |
| `TryGetLineAndOffset` | `bool (int position, out int line, out int lineOffset)` 1-based | 446 |
| `GetTokenIndex` | `int (int position)` | 468 |
| `GetLexicalTokens` | `IReadOnlyList<LexicalToken>` | 488 |
| enums | `IncludeFunctionKind` (`:494-504`), `ParserKind { Grammar, Default }` (`:506-517`) | |

Flow: `Parse`/`ParseAndAnalyze` -> force `AlwaysProduceEndTokens` -> `TokenParser.ParseTokens` -> `Create` (`:200-257`) -> `GetKind` picks `CommandGrammar` or `QueryGrammar`/`QueryParser` (`:207-230`) -> `new SyntaxTree(syntax)` -> optional `Binder.TryBind` (`:243`) -> `DetermineResultType`. If the tree is too deep, `AnalysisState.NotSafe` and `GetDiagnostics` appends `GetQuerySyntaxDepthExceeded` (`:373-376`).

Upstream bug to decide on (mirror or fix): `ParseAndAnalyze` accepts `cancellationToken` but passes `default(CancellationToken)` to `Create` (`KustoCode.cs:172`). `Parse` does the same, harmlessly (`:155`).

Command parsing is on the path: `KustoCode.Create` always references `CommandGrammar.From(globals)` for text starting with `.` (`KustoCode.cs:210-213`). Commands depend on generated `*Commands.cs` (`Parser/CodeGen/*.tt` with `src/Kusto.Language.Generators/*CommandInfos.cs`). If commands are out of scope, the Java `KustoCode` must stub the Command branch.

### C2. Syntax tree API

`Syntax/SyntaxElement.cs` (`public abstract partial class SyntaxElement`):

| Member | Signature | Line |
|---|---|---|
| `Kind` | `virtual SyntaxKind Kind` | 37 |
| `SyntaxDiagnostics` / `ContainsSyntaxDiagnostics` / `HasSyntaxDiagnostics` | | 122 / 112 / 117 |
| `GetContainedSyntaxDiagnostics()` | | 127 |
| `WithDiagnostics` / `WithAdditionalDiagnostics` | | 155 / 165,173 |
| `IsToken`, `IsMissing` | | 208, 213 |
| `Parent`, `Root`, `Tree` | `SyntaxNode`, `SyntaxElement`, `SyntaxTree` | 220, 225, 245 |
| `IndexInParent`, `ChildCount`, `GetChild(int)` | | 265, 270, 275 |
| `IsOptional(int)`, `GetName(int)`, `GetCompletionHint(int)` | | 296, 301, 306 |
| `HasMissingChildren()`, `GetChildIndex`, `Depth`, `GetCommonAncestor`, `GetDescendantIndex`, `IsAncestorOf`, `NameInParent` | | 316, 336, 361, 379, 410, 441, 457 |
| `GetFirstAncestor<T>(Func<T,bool> predicate = null)` and `...OrSelf`, `GetAncestors<T>`, `GetAncestorsOrSelf<T>` | | 475, 492, 509, 533 |
| `GetFirstDescendant<T>(Func<T,bool> = null)` / `...OrSelf` | | 557, 566 |
| `GetDescendants<TElement>(Func<TElement,bool> predicate = null)` / `GetDescendantsOrSelf` | returns `IReadOnlyList<TElement>` | 622, 631 |
| `GetTokens(bool includeZeroWidthTokens = false)` | | 697 |
| `WalkTokens(Action<SyntaxToken>)`, `WalkTokens(int start, int end, Action<SyntaxToken>)` | | 713, 722 |
| `WalkElements(Action<SyntaxElement>)` (instance) | | 742 |
| `static WalkElements(SyntaxElement root, Action<SyntaxElement> fnBefore = null, Action<SyntaxElement> fnAfter = null, Func<SyntaxElement,bool> fnDescend = null)` | stack-safe | 754 |
| `static WalkNodes(SyntaxNode root, Action<SyntaxNode> fnBefore = null, fnAfter = null, Func<SyntaxNode,bool> fnDescend = null)` | stack-safe | 811 |
| `GetNextSibling` / `GetPreviousSibling(bool includeZeroWidthElements = false)` | | 864, 882 |
| `GetFirstToken` / `GetLastToken(bool includeZeroWidthTokens = false)` | | 900, 908 |
| `GetTokenAt(int position)`, `GetNodeAt(int position, int length)` | | 996, 1044 |
| `TriviaStart`, `FullWidth`, `TriviaWidth`, `TextStart`, `End`, `Width` | | 1062, 1106, 1127, 1133, 1138, 1143 |
| `Clone(bool includeDiagnostics = true)` | | 1155 |
| `ToString()`, `ToString(IncludeTrivia)`, `ToString(IncludeTrivia, int maxLength)` | | 1161, 1166, 1171 |
| `enum IncludeTrivia { All, Interior, Minimal, SingleLine }` | | 1195 |

`Syntax/SyntaxNode.cs`: `abstract void Accept(SyntaxVisitor)`, `abstract TResult Accept<TResult>(SyntaxVisitor<TResult>)` (`:37`, `:39`); `WalkNodes(Action<SyntaxNode>)` instance (`:45`); `GetOriginalNode()` (`:54`); `GetPositionInOriginalTree(int)` (`:77`); extension `CopyAsFragment<T>(this T node)` (`:97`). Hand-written partials on generated nodes: `Expression.IsLiteral/LiteralValueInfo/LiteralValue` (`:107-123`), `NameDeclaration.SimpleName`, `NameReference.SimpleName` (`:192-215`), `Name.SimpleName` + subclasses (`:217-245`), `Directive.Name/ArgumentsText/Arguments` (`:262-296`).

`Syntax/SyntaxNode_Semantics.cs` (partial `SyntaxNode`): `Symbol ReferencedSymbol` (`:19`), `Signature ReferencedSignature` (`:24`), `GetCalledFunctionBody()` (`:38`), `GetCalledFunctionFacts()` (`:46`), `GetCalledFunctionDiagnostics()` (`:54`), `bool CalledFunctionHasErrors` (`:62`), `IReadOnlyList<SyntaxNode> Alternates` (`:70`), `IReadOnlyList<Diagnostic> SemanticDiagnostics` (`:76`), `internal SemanticInfo GetSemanticInfo()` (`:82`), `internal bool IsBound` (`:90`). `GetExpansion()` is `[Obsolete(error: true)]` (`:30`). `enum DiagnosticsInclude { Syntactic=1, Semantic=2, Expansion=4 }` (`:96-101`). `SyntaxElement.GetContainedDiagnostics(DiagnosticsInclude include = Syntactic|Semantic, CancellationToken = default)` (`:108`). On `Expression`: `TypeSymbol ResultType` (`:238`, collapses single-column reducible tuple to its scalar and `EntityGroupElementSymbol` to `UnderlyingSymbol`), `RawResultType` (`:261`), `IsConstant` (`:266`), `ConstantValueInfo` (`:271`), `ConstantValue` (`:279`).

Tokens: `Syntax/SyntaxToken.cs`: `Trivia` `Text` `Value` `ValueText` `Prefix` `IsLiteral` (`:25-69`), `GetNextToken/GetPreviousToken(bool includeZeroWidthTokens = false)` (`:141`, `:149`), factories `From(LexicalToken, Diagnostic = null)` (`:154`), `Keyword` (`:180`), `Identifier` (`:194`), `Punctuation` (`:206`), `Operator` (`:212`), `Literal` (`:218`), `Other` (`:224`), `Missing` (`:230`, `:235`). Public nested `MissingToken` (`:745`).

Lists: `SyntaxList` (`Syntax/SyntaxList.cs:13`; indexer `:37`, `Count` `:42`), `SyntaxList<TElement> : IReadOnlyList<TElement>` (`:78`; ctors `:81`, `:86`; `Empty()` `:140`). `SeparatedElement` (`Syntax/SeparatedElement.cs:9`; `Element` `:14`, `Separator` `:19`), `SeparatedElement<TElement>` (`:67`, ctor `:75`).

Tree: `SyntaxTree(SyntaxNode root, SyntaxTree original = null, int offsetInOriginal = 0)` (`Syntax/SyntaxTree.cs:22`), `Root` `:10`, `Original` `:15`, `OffsetInOriginal` `:20`, `Depth` `:36`, `internal IsSafeToRecurse(GlobalState)` `:53`.

Generated (not in repo, see headline 3): 226 node classes with public get-only properties named per `SyntaxNodeInfos.cs`, e.g. `QueryBlock { Directives, Statements, SkippedTokens, EndOfQuery }` (`src/Kusto.Language.Generators/SyntaxNodeInfos.cs:71-78`). Plus `SyntaxVisitor`, `DefaultSyntaxVisitor`, `SyntaxVisitor<TResult>`, `DefaultSyntaxVisitor<TResult>` (generated, `SyntaxNodeGenerator.cs:320-380`), with hand-written bases for lists/custom/separated elements in `Syntax/SyntaxVisitor.cs:10-58`. There is one `Visit{Name}` per concrete node (210 of 226).

`Syntax/SyntaxFacts.cs` (public static): `GetText`, `GetCategory`, `IsKeyword/IsPunctuation/IsOperator/IsLiteral/IsType`, `GetOperatorKind`, `TryGetKind` x2, `GetKinds`, `GetKindsWithFixedText`, `CanBeIdentifier`, `Keywords`, `Punctuation`, `Operators` (`:821-927`). `SyntaxKind` enum 735 lines (`Syntax/SyntaxKind.cs`).

Lexer (also public): `TokenParser.ParseToken(string, int start = 0, ParseOptions = null)` `:35`, `ParseTokens(string, ParseOptions = null)` `:43`, `ParseTokens(string, List<LexicalToken>, ParseOptions = null)` `:53`, plus public `Scan*` helpers (`:405`, `525`, `540`, `573`, `605`, `699`, `733`, `768`, `809`, `1140`, `1150`). `LexicalToken(SyntaxKind kind, string trivia, string text, IReadOnlyList<Diagnostic> diagnostics = null)` `Parser/LexicalToken.cs:37`; props `Kind Trivia Text Diagnostics Length`.

Parser (public): `QueryParser.ParseQuery(string|LexicalToken[], ...)` and `ParseExpression`, `ParseFunctionParameters`, `ParseFunctionParameter`, `ParseFunctionBody`, `ParseEntityPath`, `ParseEntityGroup`, `ParseLiteral`, `ParseRowSchema` (`Parser/QueryParser.cs:46-134`). `TextFacts` public static (`Parser/TextFacts.cs`). `ParseOptions` with `AlwaysProduceEndToken`, `AllowLiteralsWithLineBreaks`, `AllowNonAdjacentWildcardParts`, `ParserKind`, `Default`, `With*` (`Parser/ParseOptions.cs:6-110`).

### C3. `GlobalState` (`GlobalState.cs`, `public sealed class`, immutable)

Properties: `Clusters :19`, `Cluster :24`, `Database :29`, `Domain :34`, `Functions :39`, `Aggregates :44`, `PlugIns :49`, `Operators :54`, `ServerKind :59`, `AmbientSymbols :64`, `ClientSymbols :72`, `Options :77`, `Cache :88` (`KustoCache`), `ParseOptions :93`, `Parameters :932`.

`public static GlobalState Default { get; }` `:1154`. Built from `Language.Functions.All`, `Aggregates.All`, `PlugIns.All`, `Operators.All`, `Options.All`, `KustoFacts.KustoWindowsNet`, `ClusterSymbol.Unknown`, `DatabaseSymbol.Unknown`, `ServerKinds.Engine`. Lazy singleton via `Interlocked.CompareExchange`.

Methods (all return a new `GlobalState` unless noted):
- `Copy()` `:220`; `WithCache()` `:343`; `WithParseOptions(ParseOptions)` `:358`
- Clusters: `WithClusterList(IReadOnlyList<ClusterSymbol>)` `:373`, `WithClusterList(params ClusterSymbol[])` `:394`, `AddOrReplaceCluster(ClusterSymbol)` `:404`, `WithCluster(ClusterSymbol)` `:422`, `WithCluster(string)` `:449`, `WithDomain(string)` `:457`
- Database: `WithDatabase(DatabaseSymbol)` `:503`, `WithDatabase(string databaseName)` `:538`. If the db is in no known cluster it synthesises `new ClusterSymbol(database.Name + ":cluster", database)` (`:520-528`).
- **There is no `GlobalState.AddOrUpdateDatabase`.** The nearest is `ClusterSymbol.AddOrUpdateDatabase` (below), used as `globals.WithCluster(globals.Cluster.AddOrUpdateDatabase(db))` or `AddOrReplaceCluster`.
- Queries: `IsDatabaseTable(TableSymbol)` `:546`, `IsDatabaseFunction(FunctionSymbol)` `:554`, `IsDatabaseSymbol(Symbol)` `:562`, `GetCluster(string)` `:578`, `GetCluster(DatabaseSymbol)` `:599`, `GetDatabase(TableSymbol|FunctionSymbol|EntityGroupSymbol|Symbol)` `:626-650`, `GetTable(ColumnSymbol)` `:677`, `GetFunction(string)` `:704`, `GetAggregate(string)` `:733`, `GetPlugIn(string)` `:764`, `IsAggregateFunction` `:782`, `IsBuiltInFunction` `:791`, `IsBuiltInFunctionName` `:801`, `GetOperator(OperatorKind)` `:822`, `GetCommand(string)` `:847`, `GetAmbientSymbol` `:913`, `GetClientSymbol` `:1004`, `GetOption` `:1030`
- Replace catalogs: `WithFunctions` `:570`, `WithAggregates` `:725`, `WithPlugIns` `:754`, `WithOperators` `:814`, `WithServerKind` `:839`, `WithAmbientSymbols` `:889`, `AddOrUpdateAmbientSymbols` x2 `:897,905`, `WithParameters` `:952`, `AddParameters` x2 `:963,972`, `WithClientSymbols` `:980`, `AddOrUpdateClientSymbols` x2 `:988,996`, `WithOptions` `:1022`
- Properties bag: `GetProperty<T>(GlobalStateProperty<T>)` `:1065`, `WithProperty<T>` `:1094`; `GlobalStateProperty<T>(string name, T defaultValue = default)` `:1208-1212`.
- The ctor is private/internal (long positional list at `:1157-1185`). All maps are lazily built per instance. `Default` is shared, so Java needs lazy-holder init and care with static-init order (`Functions`/`Symbols` reference `GlobalState.Default`, e.g. `Signature.AllowsNamedArguments` at `Symbols/Signature.cs:335`).

### C4. Symbols

- `Symbol` (`Symbols/Symbol.cs:10`): `Name :21`, `AlternateName :26`, `Kind :31`, `IsHidden :41` (names starting `__`), `IsError :46`, `Tabularity :51`, `IsScalar :56`, `IsTabular :74`, `Members :92`, `GetMembers(string, SymbolMatch, List<Symbol>, bool ignoreCase = false)` `:98`, `GetMembers(SymbolMatch, List<Symbol>, bool)` `:112`, `GetFirstMember` `:120`, `static GetResultType(Symbol)` `:136`, `IsAssignableTo(Symbol, Conversion = None)` `:188`, `IsAssignableToAny(...)` `:196`.
- `ClusterSymbol : TypeSymbol` (`Symbols/ClusterSymbol.cs:12`): ctors `(string name, IEnumerable<DatabaseSymbol> databases, bool isOpen = false)` `:35`, `(string name, params DatabaseSymbol[])` `:43`; `AddMembers` x2 `:57,66`; `GetDatabase(string)` `:93`; `WithDatabases` `:119`; `AddDatabase` `:127`; `UpdateDatabase(existing, new)` `:136`; **`AddOrUpdateDatabase(DatabaseSymbol newDatabase)` `:145`**; `RemoveDatabase` `:161`; `RemoveDatabases` `:170`; `static readonly ClusterSymbol Unknown` `:179`; `IsOpen :19`.
- `DatabaseSymbol : TypeSymbol` (`Symbols/DatabaseSymbol.cs:13`): ctors `(string name, string alternateName, IEnumerable<Symbol> members, bool isOpen = false)` `:36`, `(string name, IEnumerable<Symbol> members, bool isOpen = false)` `:47`, `(string name, string alternateName, params Symbol[] members)` `:55`, `(string name, params Symbol[] members)` `:63`. Members can be `TableSymbol` (and subclasses), `FunctionSymbol`, `EntityGroupSymbol`, `StoredQueryResultSymbol`, `GraphModelSymbol`. Accessors: `GetMember :193`, `GetTable :201`, `GetAnyTable :209`, `GetExternalTable :219`, `GetMaterializedView :227`, `GetFunction :235`, `GetEntityGroup :243`, `GetStoredQueryResult :251`, `GetGraphModel :259`; `WithAlternateName :267`, `WithMembers :277`, `AddMembers` x2 `:285,293`, `Contains :301`, `static readonly Unknown :316`, `IsOpen :21`.
- `TableSymbol : TypeSymbol` (`Symbols/TableSymbol.cs:14`): ctors `(string name, IEnumerable<ColumnSymbol> columns, string description = null)` `:58`, **`(string name, string schema, string description = null)` `:63`** (schema text like `"(a: real, b: string)"`, parsed by `static TableSymbol From(string schema)` `:86`), `(string name, params ColumnSymbol[])` `:68`, `(IEnumerable<ColumnSymbol>)` `:73`, `(params ColumnSymbol[])` `:78`. `Columns :19`, `Description :24`, `IsSorted/IsSerialized/IsOpen :121-131`. Fluent: `WithName :186`, `WithColumns` x2 `:202,210`, `AddColumns` x2 `:218,229`, `WithIsSerialized :245`, `WithIsSorted :253`, `WithIsOpen :261`, `WithIsExternal :269`, `WithIsMaterializedView :288`, `WithInheritableProperties :314`, `GetColumn :353`, `TryGetColumn :369`, `GetMatchingColumns :377`, `WithSource(SyntaxNode) :418`, `static readonly Empty :426`, `static Combine(CombineKind, ...)` x2 `:431,439`, `AreEquivalent :447`, `AreResultEquivalent :464`, `AreColumnsEquivalent :476`. Subclasses in same file: `MaterializedViewSymbol` `:510` (ctors `:532`, `:537`), `ExternalTableSymbol` `:553` (ctors `:565,570,575`), `StoredQueryResultSymbol` `:586` (ctors `:588,593`).
- `ColumnSymbol : Symbol` (`Symbols/ColumnSymbol.cs:13`): `ColumnSymbol(string name, TypeSymbol type, string description = null, IReadOnlyList<ColumnSymbol> originalColumns = null, SyntaxNode source = null, IReadOnlyList<string> examples = null)` `:45`. A null or error `type` becomes `ScalarTypes.Unknown` (`:53`). `OriginalColumns` flattened to truly original ones (`:55-58`). Props `Type :18`, `Description :23`, `OriginalColumns :30`, `Source :36`, `Examples :41`. `With*` `:132-180`; `static Combine(CombineKind, ...)` x2 `:188,218`. Reference identity matters: README compares columns by `ReferenceEquals` (`readme.md:72`, `n.ReferencedSymbol == columnA`). Java must not override `equals` on symbols.
- `FunctionSymbol : TypeSymbol` (`Symbols/FunctionSymbol.cs:14`): ctors at `:118` (`name, IEnumerable<Signature>, description`), `:132` (`name, params Signature[]`), `:137,142` (`name, TypeSymbol returnType, parameters`), `:147,152` (`ReturnTypeKind`), `:157,162` (`CustomReturnType, Tabularity`), `:167,172` (`string body, Tabularity, parameters`), `:177,182` (`string body, parameters`), **`:187` (`string name, string parameterList, string body, string description = null)`**, `:192` (`..., Tabularity`), `:197,202` (`FunctionBody declaration, parameters`). Example from readme: `new FunctionSymbol("ShortShapes", "(maxHeight: real)", "{ Shapes | height < maxHeight; }")`. Props `Signatures :21`, `Description :26`, `ResultNameKind :31`, `ResultNamePrefix :36`, `Alternative :42`, `OptimizedAlternative :53`, `CustomAvailability :58`, `IsHidden :77`, `IsConstantFoldable :82`, `IsView :88`. Fluent `Hide :262`, `WithIsHidden :270`, `ConstantFoldable :281`, `WithIsConstantFoldable :289`, `WithIsView :300`, `WithResultNamePrefix :311`, `WithResultNameKind :319`, `WithDescription :327`, `Obsolete :335`, `WithIsObsolete :343`, `WithOptimizedAlternative :352`, `WithCustomAvailability :361`, `GetReturnType(GlobalState) :374`. A string body is parsed lazily into a `FunctionBody` (binder expands per call site).
- `Signature` (`Symbols/Signature.cs:15`): ctors `:174-227` (return kind / return type / custom return type / string body / `FunctionBody`, each with list and params overloads). Props `Symbol :23` (internal set), `ReturnKind :28`, `Parameters :33`, `MinArgumentCount :38`, `MaxArgumentCount :43`, `Declaration :48`, `CustomReturnType :53`, `Layout :58`, `IsHidden :63`, `Alternative :69`, `IsObsolete :74`, `HasRepeatableParameters :79`, `HasOptionalParameters :84`, `HasAggregateParameters :89`. Methods `WithLayout x2 :236,245`, `WithIsHidden :253`, `Hide :261`, `WithAlternative :269`, `Obsolete :277`, `Body :285`, `DeclaredReturnType :301`, `GetParameter :309`, `GetArgumentParameters x3 :340-367`, `GetNextPossibleParameters :381`, `IsValidArgumentCount :395`, `Tabularity :400`, `IsScalar :441`, `IsTabular :456`, `GetReturnType x3 :475,524,551`, `ComputeTabularity :634`.
- `Parameter` (`Symbols/Parameter.cs:14`): ctors at `:129` (`name, ParameterTypeKind, ArgumentKind = Expression, values, examples, isCaseSensitive, defaultValueIndicator, minOccurring = 1, maxOccurring = 1, defaultValue, description`), `:157` (`TypeSymbol type`, same tail), `:185` (`TypeSymbol[] types`), `:213` (`name, TypeSymbol type`). Statics `From(ParameterSymbol, bool isOptional, Expression defaultValue) :221`, `From(FunctionParameter) :229`, `From(FunctionParameters) :240`, `ParseList(string) :255`, `GetDeclaration :266`, `GetParameterListDeclaration :300`.
- `ParameterSymbol(string name, TypeSymbol type, string description = null)` `Symbols/ParameterSymbol.cs:20`.
- Scalar types: `ScalarTypes` static fields `Dynamic :11`, `Bool :17`, `Int :23`, `Long :29`, `Real :35`, `Decimal :41`, `DateTime :47`, `TimeSpan :53`, `Guid :59`, `Type :65`, `String :71`, `Null :77`, `Unknown :83`, plus dynamic-flavoured and array/bag forms to `:209`; `GetDynamic :217`, `GetDynamicArray :249`, `GetDynamicBag x3 :297,313,321`, `GetTuple x3 :327,333,340`, `All :346`, `GetSymbol(string typeName) :381`. Others in `Symbols/`: `TupleSymbol`, `GroupSymbol`, `VariableSymbol`, `PatternSymbol`, `OptionSymbol`, `OperatorSymbol`, `CommandSymbol`, `EntityGroupSymbol`, `EntityGroupElementSymbol`, `GraphSymbol`, `GraphModelSymbol`, `ErrorSymbol`, `VoidSymbol`, `DynamicSymbol`.
- Built-in catalogs (public statics): `Functions.All` (`Functions.cs:3734`, file 4226 lines), `Aggregates.All` (`Aggregates.cs:796`), `PlugIns.All` (`PlugIns.cs:1111`), `Operators.All` (`Operators.cs:354`), `Options.All` (`Options.cs:193`). Together about 8.1k lines of declarative data. Candidates for mechanical translation.

### C5. Semantic info and diagnostics

- `SemanticInfo` (`Binder/SemanticInfo.cs`, public): `ReferencedSymbol :22`, `ReferencedSignature :41`, `ResultType :72`, `IsConstant :77`, `Diagnostics :82`, `CalledFunctionInfo :87`, `Alternates :92`; ctors `:110-150`; `With*` `:155-227`; `static Empty :242`.
- `Diagnostic` (`Diagnostics/Diagnostic.cs:10`, `IEquatable<Diagnostic>`): `Code :15`, `Category :20`, `Severity :25`, `Description :30`, `Message :35`, `HasLocation :70`, `LocationKind :75`, `Start :80`, `Length :85`, `End :90`; ctors `:40,45,50`; `With*` `:128-163`; `NoDiagnostics :168`. `DiagnosticSeverity` consts `Error/Warning/Suggestion/Information/Hidden` (`:194-219`). Messages are built in `Diagnostics/DiagnosticFacts.cs` (large). Port it whole: tests will compare text.
- `Binder` class and `Binder.TryBind(SyntaxTree, GlobalState, LocalBindingCache = null, Action<SyntaxNode,SemanticInfo> semanticInfoSetter = null, CancellationToken = default)` (`Binder/Binder_API.cs:158`) are **internal**. The binder runs under `lock (bindingCache)` on the global cache (`:168-170`), so the first analysis on a `GlobalState` instance serialises. `TryBind` returns false when `!tree.IsSafeToRecurse(globals)` (`:164`). Also internal-in-practice: `GetReferencedSymbol` `:310`, `GetRowScope` `:345`, `GetSymbolsInScope` `:379`, `TryBindCalledFunctionBody` `:216`, `GetComputedReturnType` `:278`.
- `KustoCache` (public): `Globals :9`, `GetOrCreate<T>() :34`, `GetOrCreate<T>(Func<T>) :48`.
- Helpers a translator will want: `KustoFacts` (`Parser/KustoFacts.cs`, public static partial): `BracketNameIfNecessary :658,671`, `GetStringLiteralValue :774`, `GetStringLiteral :686`, `GetExpressionResultName :766`, `GetSyntaxTabularity x3 :1355,1397,1413`, `GetLiteralType :1430`, `Matches(pattern, text, ignoreCase) :1254`, host-name helpers `:1043-1240`. `Utils.CancellationToken` is a custom struct (`Utils/Cancellation.cs:7`) wrapping `System.Threading.CancellationToken` (implicit conversion) or, under `BRIDGE`, a `Func<bool>`.

### C6. Typical translator recipe (from readme)

```
globals = GlobalState.Default.WithDatabase(
    new DatabaseSymbol("db", new TableSymbol("T", "(a: real, b: real)")));
code = KustoCode.ParseAndAnalyze(query, globals);
code.Syntax.GetDescendants<NameReference>(n => n.ReferencedSymbol is ColumnSymbol)
code.GetDiagnostics()
SyntaxElement.WalkNodes(code.Syntax, fnBefore: n => { ... n.ReferencedSymbol / n.GetCalledFunctionBody() }, fnDescend: n => !(n is FunctionDeclaration));
expr.ResultType
```

### C7. Porting implications of the API surface

- Make `Binder`, `SemanticInfo` setters, `GetSemanticInfo()`, `KustoCode.Tree`, `KustoCode.Grammar` package-private in Java (same package as the tests), mirroring `InternalsVisibleTo`.
- `Func<T,bool> predicate = null` and optional parameters become Java overloads. The `GetDescendants<TElement>` type filter needs a `Class<T>` argument in Java (no reified generics). Decide the signature once. Example: `getDescendants(Class<T> type, Predicate<T> predicate)`.
- `Action<>`/`Func<>` map to `Consumer`/`Predicate`/`Function`.
- C# `partial` generated nodes plus hand-written partials (`SyntaxNode_Semantics.cs`, `SyntaxNode.cs` partial blocks, `FakeExpression.cs`, `ValueInfo.cs`) merge into the generated Java class. Mirror rule says one Java file per C# file, and generated code has no C# file. Plan for a Java emitter that ports `SyntaxNodeGenerator.cs` + `SyntaxNodeInfos.cs`, with hand-written partials folded in via an agreed hook (abstract base or generated-class-plus-manual-superclass).
- `IReadOnlyList<T>` -> `List<T>` (unmodifiable). `IEquatable<Diagnostic>` -> `equals/hashCode` (`Diagnostic.cs:170-181`).
- Identity semantics for symbols and `==` on `DatabaseSymbol` etc. (e.g. `this.Database == database` at `GlobalState.cs:507`) must become `==` in Java, not `equals`.
- Use UTF-16 `String` and `char`. Do not use code points anywhere in the lexer.
- Culture-sensitive parsing in `SyntaxToken.LiteralToken` (A4 gotcha).
