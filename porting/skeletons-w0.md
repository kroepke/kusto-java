# Wave 0 skeletons (PORTING.md 2.8)

Types owned by later waves that wave-0 code references. Each file carries the full header and
`// PORT-SKELETON: W<n>`; pending members throw `UnsupportedOperationException("PORT-PENDING: W<n>")`.
The owning wave replaces the whole file. Paths are under
`kusto-language/src/main/java/org/graylog/kusto/language/`.

Computed by scanning every wave-0 upstream file (manifest `wave: 0`, plus `SyntaxNodeInfos.cs`
property types) for identifiers naming types of other waves, then confirmed by `javac` on a scratch
copy with the hand-written fragments injected (0 errors).

| Type | Wave | Java path | Members (upstream order) | Needed by (wave-0 file) |
|---|---|---|---|---|
| `Symbol` | W1 | `symbols/Symbol.java` | `name()` | `DiagnosticFacts` (`type.Name`, `p.Name`), `SyntaxElement` (`GatherDiagnostics`: `expr.ReferencedSymbol?.Name`), `SyntaxExtensions` (`parameter.Name`) |
| `TypeSymbol` | W1 | `symbols/TypeSymbol.java` | type only (`extends Symbol`) | `DiagnosticFacts`, `FakeExpression`, `Expression` fragment, `SemanticInfo` skeleton |
| `ScalarSymbol` | W1 | `symbols/ScalarSymbol.java` | type only (`extends TypeSymbol`) | base type of `TupleSymbol` |
| `TupleSymbol` | W1 | `symbols/TupleSymbol.java` | `columns()`, `isReducibleToScalar()` | `Expression` fragment (`SyntaxNode_Semantics.cs:245-247`) |
| `ColumnSymbol` | W1 | `symbols/ColumnSymbol.java` | `type()` | `Expression` fragment (`ts.Columns[0].Type`) |
| `EntityGroupElementSymbol` | W1 | `symbols/EntityGroupElementSymbol.java` | `underlyingSymbol()` | `Expression` fragment (`SyntaxNode_Semantics.cs:249-251`) |
| `VariableSymbol` | W1 | `symbols/VariableSymbol.java` | `isConstant()`, `constantValueInfo()` | `Expression` fragment (`ConstantValueInfo`) |
| `Signature` | W1 | `symbols/Signature.java` | type only | `SyntaxNode.referencedSignature()`, `SemanticInfo` skeleton |
| `SymbolMatch` | W1 | `symbols/SymbolMatch.java` | all 23 constants, real (`[Flags]` → int holder, D23) | generated `NameReference` (`Match`), `NameReference` fragment (`SymbolMatch.Default`) |
| `OperatorKind` | W1 | `symbols/OperatorKind.java` | whole enum, real | `SyntaxFacts` (`SyntaxData.OperatorKind`, table) |
| `Properties` | W1 | `Properties.java` | `MaxAnalysisDepth` (real field, `GlobalStateProperty1<Integer>`, default 500) | `SyntaxTree.isSafeToRecurse` |
| `LexicalToken` | W2 | `parsing/LexicalToken.java` | `kind()`, `trivia()`, `text()`, `diagnostics()` | `SyntaxToken.from` |
| `TextFacts` | W2 | `parsing/TextFacts.java` | `isWhitespace(char)`, `hasLineBreaks(String)`, `getLineEnd(String,int)`, `getNextLineStart(String,int)`, `isLetter(char)` | `SyntaxToken` (`isWhitespace`, `isLetter`), `SyntaxElement.setLocation` (`hasLineBreaks`), `editor/ClientDirective` (`isWhitespace`, `getLineEnd`, `getNextLineStart`) |
| `TokenParser` | W2 | `parsing/TokenParser.java` | `scanWhitespace(String,int)`, `scanTrivia(String,int)`, `scanIdentifier(String[,int])`, `scanLongLiteral(String[,int])`, `scanRealLiteral(String[,int])`, `scanStringLiteral(String[,int[,boolean]])` | `SyntaxToken.destringify` (`scanStringLiteral`), `editor/ClientDirective` (trivia, whitespace, identifier, long, real), `utils/ConnectionInfo` (`scanWhitespace`) |
| `KustoFacts` | W2 | `KustoFacts.java` (partial group `KustoFacts.cs` + `KustoFacts_Keywords.cs`) | `getBracketedName(String)`, `getStringLiteralValue(String)` | `DiagnosticFacts` (KS006, KS228), `SyntaxToken` (string literal values) |
| `FunctionBodyFacts` | W3 | `FunctionBodyFacts.java` | type only | `SyntaxNode.getCalledFunctionFacts()`, `FunctionCallInfo` skeleton |
| `QueryOperatorParameter` | W3 | `QueryOperatorParameter.java` | `aliases()` (`name()` inherited from `Symbol`) | `SyntaxExtensions` |
| `GlobalState` | W4 | `GlobalState.java` | `getProperty(GlobalStateProperty1<T>)` | `SyntaxTree.isSafeToRecurse` |
| `GlobalStateProperty` | W4 | `GlobalStateProperty.java` | whole type, real (`name()`, ctor) | base of `GlobalStateProperty1` |
| `GlobalStateProperty1<T>` | W4 | `GlobalStateProperty1.java` | whole type, real (`defaultValue()`, 2 ctors) | `Properties.MaxAnalysisDepth` |
| `Binder` | W6 | `binding/Binder.java` (12-file partial group) | `defaultSetSemanticInfo(SyntaxNode, SemanticInfo)` | `FakeExpression` |
| `SemanticInfo` | W6 | `binding/SemanticInfo.java` | `referencedSymbol()`, `referencedSignature()`, `resultType()`, `isConstant()`, `diagnostics()`, `calledFunctionInfo()`, `alternates()`, ctor `SemanticInfo(TypeSymbol)` | `SyntaxElement.ExtendedData`, `SyntaxNode` (semantics part), `Expression` fragment, `FakeExpression` (ctor) |
| `FunctionCallInfo` | W6 | `binding/FunctionCallInfo.java` | `facts()`, `expansion()`, `hasErrors()`, `diagnostics()` | `SyntaxNode` (`GetCalledFunctionBody/Facts/Diagnostics`, `CalledFunctionHasErrors`) |
| `FunctionCallExpansion` | W6 | `FunctionCallExpansion.java` (namespace `Kusto.Language`) | whole type, real (3 ctors forwarding to `SyntaxTree`) | `SyntaxNode` (`Expansion?.Root`) |

## Listed in the W0 brief but not referenced by any wave-0 file (no skeleton created)

| Type | Wave | Why not needed |
|---|---|---|
| `Parameter` | W1 | only hit is the enum member `CompletionKind.Parameter` (`Editor/CompletionKind.cs:107`) |
| `Scope` | W1 | no reference in wave-0 files |
| `SymbolKind` | W1 | no reference in wave-0 files |
| `Aggregates`, `Operators` | W3 | no reference; `SyntaxFacts.Operators` is a property name |
| `Source` | W3 | no reference; `ConnectionInfo.DataSource` is a property name |
| `TextRange` | W7 | only `EditString.cs` uses it, and the `EditString` stub keeps none of those members |

## Gate note

`PLAN.md` W0 gate expects `check_pending.py` to list only W1/W2/W4/W6 skeletons. Two W3 skeletons
are unavoidable: `FunctionBodyFacts` (`SyntaxNode_Semantics.cs:46`) and `QueryOperatorParameter`
(`SyntaxExtensions.cs:143-164`).
