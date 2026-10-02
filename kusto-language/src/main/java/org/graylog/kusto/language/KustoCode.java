// Ported from: src/Kusto.Language/KustoCode.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graylog.kusto.language.binding.Binder;
import org.graylog.kusto.language.binding.LocalBindingCache;
import org.graylog.kusto.language.editor.CodeKinds;
import org.graylog.kusto.language.parsing.CommandGrammar;
import org.graylog.kusto.language.parsing.LexicalToken;
import org.graylog.kusto.language.parsing.Parser;
import org.graylog.kusto.language.parsing.QueryGrammar;
import org.graylog.kusto.language.parsing.QueryParser;
import org.graylog.kusto.language.parsing.SyntaxParsers;
import org.graylog.kusto.language.parsing.TextFacts;
import org.graylog.kusto.language.parsing.TokenParser;
import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.symbols.SymbolMatch;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.syntax.CommandBlock;
import org.graylog.kusto.language.syntax.DiagnosticsInclude;
import org.graylog.kusto.language.syntax.ExpressionStatement;
import org.graylog.kusto.language.syntax.QueryBlock;
import org.graylog.kusto.language.syntax.SeparatedElement1;
import org.graylog.kusto.language.syntax.Statement;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.SyntaxList1;
import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.syntax.SyntaxTree;
import org.graylog.kusto.language.utils.CancellationToken;
import org.graylog.kusto.language.utils.Interlocked;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.IntList;
import org.graylog.kusto.language.utils.dotnet.IntRef;

/// <summary>
/// A model of a Kusto code block, with the breakdown of its syntax, diagnostics and referenced symbols.
/// </summary>
public final class KustoCode
{
    // PORT: §3.13 VarHandles stand in for `ref this.field` of the CAS-published lazy fields
    private static final VarHandle DIAGNOSTICS = Interlocked.handle(KustoCode.class, "diagnostics", List.class);
    private static final VarHandle SYNTAX_DIAGNOSTICS = Interlocked.handle(KustoCode.class, "syntaxDiagnostics", List.class);
    private static final VarHandle LINE_STARTS = Interlocked.handle(KustoCode.class, "lineStarts", IntList.class);

    /// <summary>
    /// The text of the code.
    /// </summary>
    private final String text;
    public String text() { return this.text; }

    /// <summary>
    /// The kind of the code. See <see cref="CodeKinds"/>.
    /// </summary>
    private final String kind;
    public String kind() { return this.kind; }

    /// <summary>
    /// The <see cref="SyntaxTree"/> of the parsed code.
    /// </summary>
    private final SyntaxTree tree;
    @Internal
    public SyntaxTree tree() { return this.tree; }

    /// <summary>
    /// The root <see cref="SyntaxNode"/> of the parsed code.
    /// </summary>
    public SyntaxNode syntax() { return tree().root(); }

    /// <summary>
    /// The grammar rule used to parse the code.
    /// </summary>
    private final Parser<LexicalToken> grammar;
    @Internal
    public Parser<LexicalToken> grammar() { return this.grammar; }

    /// <summary>
    /// True if semantic analysis has been performed.
    /// </summary>
    public boolean hasSemantics() { return _analysisState == AnalysisState.Performed; }

    /// <summary>
    /// The resulting <see cref="TypeSymbol"/> of the query or control command in the code.
    /// This value is only available when semantic analysis has been performed.
    /// </summary>
    private final TypeSymbol resultType;
    public TypeSymbol resultType() { return this.resultType; }

    /// <summary>
    /// The <see cref="GlobalState"/> used during parsing and semantic analysis.
    /// </summary>
    private final GlobalState globals;
    public GlobalState globals() { return this.globals; }

    /// <summary>
    /// The deepest node depth of the syntax tree.
    /// </summary>
    public int maxDepth() { return tree().depth(); }

    /// <summary>
    /// The tokens produced by the lexer.
    /// These are kept around to make reparsing faster, and are used by completion.
    /// </summary>
    private final LexicalToken[] _lexerTokens;
    private final IntList _lexerTokenStarts; // PORT: §3.17 List<int> -> IntList

    /// <summary>
    /// The local cache to use for binding.  Stored here to aid debugging.
    /// </summary>
    @Internal
    public final LocalBindingCache _localCache;

    private enum AnalysisState
    {
        NotRequested,
        Performed,
        NotSafe
    }

    private final AnalysisState _analysisState;

    private KustoCode(
        String text, 
        String kind, 
        GlobalState globals, 
        Parser<LexicalToken> grammar, 
        SyntaxTree tree, 
        AnalysisState analysisState, 
        TypeSymbol resultType,
        LexicalToken[] lexerTokens, 
        IntList lexerTokenStarts,
        LocalBindingCache localCache)
    {
        this.text = text;
        this.kind = kind;
        this.globals = globals;
        this.grammar = grammar;
        this.tree = tree;
        this.resultType = resultType;
        _lexerTokens = lexerTokens;
        _lexerTokenStarts = lexerTokenStarts;
        _localCache = localCache;
        _analysisState = analysisState;
    }

    /// <summary>
    /// The language dialect of the code.
    /// </summary>
    public KustoDialect dialect()
    {
        switch (kind())
        {
            case CodeKinds.Command:
                switch (this.globals().serverKind())
                {
                    case ServerKinds.ClusterManager:
                        return KustoDialect.ClusterManagerCommand;
                    case ServerKinds.DataManager:
                        return KustoDialect.DataManagerCommand;
                    case ServerKinds.Engine:
                    default:
                        return KustoDialect.EngineCommand;
                }

            case CodeKinds.Query:
            case CodeKinds.Directive:
                return KustoDialect.Query;

            default:
                return KustoDialect.Unknown;
        }
    }

    /// <summary>
    /// Create a new <see cref="KustoCode"/> instance from the text and globals. Does not perform semantic analysis.
    /// </summary>
    /// <param name="text">The code text</param>
    /// <param name="globals">The globals to use for parsing and semantic analysis. Defaults to <see cref="GlobalState.Default"/></param>.
    public static KustoCode parse(String text, GlobalState globals)
    {
        if (text == null)
            throw new NullPointerException("text" /* nameof */); // PORT: §3.16
        globals = globals != null ? globals : GlobalState.default_(); // PORT: §3.14 ??; §2.3 Default → default_()
        globals = globals.withParseOptions(globals.parseOptions().withAlwaysProduceEndTokens(true));
        var tokens = TokenParser.parseTokens(text, globals.parseOptions());
        var starts = getTokenStarts(tokens);
        return create(text, globals, tokens, starts, false, CancellationToken.NONE); // PORT: §3.12 analyze: false, cancellationToken: default
    }

    public static KustoCode parse(String text) // PORT: §3.12 globals = null
    {
        return parse(text, null);
    }

    /// <summary>
    /// Create a new <see cref="KustoCode"/> instance from the text and globals and performs semantic analysis.
    /// </summary>
    /// <param name="text">The code text</param>
    /// <param name="globals">The globals to use for parsing and semantic analysis. Defaults to <see cref="GlobalState.Default"/></param>.
    /// <param name="cancellationToken">A <see cref="CancellationToken"/> that can be used to cancel parsing and semantic analysis.</param>
    public static KustoCode parseAndAnalyze(String text, GlobalState globals, CancellationToken cancellationToken)
    {
        if (text == null)
            throw new NullPointerException("text" /* nameof */); // PORT: §3.16
        globals = globals != null ? globals : GlobalState.default_(); // PORT: §3.14 ??; §2.3 Default → default_()
        globals = globals.withParseOptions(globals.parseOptions().withAlwaysProduceEndTokens(true));
        var tokens = TokenParser.parseTokens(text, globals.parseOptions());
        var starts = getTokenStarts(tokens);
        // PORT-BUG: upstream passes default(CancellationToken) here instead of the caller's cancellationToken, so cancellation never reaches Create
        return create(text, globals, tokens, starts, true, CancellationToken.NONE); // PORT: §3.12 analyze: true, cancellationToken: default(CancellationToken)
    }

    public static KustoCode parseAndAnalyze(String text, GlobalState globals) // PORT: §3.12 cancellationToken = default
    {
        return parseAndAnalyze(text, globals, CancellationToken.NONE);
    }

    public static KustoCode parseAndAnalyze(String text) // PORT: §3.12 globals = null
    {
        return parseAndAnalyze(text, null, CancellationToken.NONE);
    }

    /// <summary>
    /// Gets the starting offsets for each token
    /// </summary>
    /// <param name="tokens"></param>
    /// <returns></returns>
    private static IntList getTokenStarts(LexicalToken[] tokens)
    {
        var starts = new IntList(tokens.length + 1);
        int start = 0;
        
        for (int i = 0; i < tokens.length; i++)
        {
            starts.add(start);
            start = start + tokens[i].length();
        }

        // add one more for the end
        starts.add(start);

        return starts;
    }

    /// <summary>
    /// Creates a new <see cref="KustoCode"/> form the already parsed lexical tokens.
    /// </summary>
    private static KustoCode create(String text, GlobalState globals, LexicalToken[] tokens, IntList tokenStarts, boolean analyze, CancellationToken cancellationToken)
    {
        Parser<LexicalToken> grammar;
        SyntaxNode syntax;

        globals = globals != null ? globals : GlobalState.default_(); // PORT: §3.14 ??; §2.3 Default → default_()

        var kind = getKind(text);
        switch (kind)
        {
            case CodeKinds.Command:
                var commandBlock = CommandGrammar.from(globals).commandBlock(); // PORT: §2.3 property CommandBlock
                grammar = commandBlock;
                syntax = SyntaxParsers.parseFirst(commandBlock, Arrays.asList(tokens)); // PORT: §3.5 extension method; §3.17 array as IReadOnlyList
                break;
            case CodeKinds.Query:
            default:
                var queryBlock = QueryGrammar.from(globals).queryBlock(); // PORT: §2.3 property QueryBlock
                grammar = queryBlock;
                switch (globals.parseOptions().parserKind())
                {
                    case Default:
                    default:
                        syntax = QueryParser.parseQuery(tokens, 0, globals.parseOptions()); // PORT: §3.12 start = 0, options: globals.ParseOptions
                        break;
                    case Grammar:
                        syntax = SyntaxParsers.parseFirst(queryBlock, Arrays.asList(tokens)); // PORT: §3.5 extension method; §3.17
                        break;
                }
                break;
        }

        var tree = new SyntaxTree(syntax);

        LocalBindingCache localCache = null;
        TypeSymbol resultType = null;
        var analysisState = AnalysisState.NotRequested;

        if (analyze)
        {
            cancellationToken.throwIfCancellationRequested();

            localCache = new LocalBindingCache();
            if (Binder.tryBind(tree, globals, localCache, null, cancellationToken))
            {
                resultType = determineResultType(syntax);
                analysisState = AnalysisState.Performed;
            }
            else
            {
                // if the tree is too deep, then don't bother trying to analyze it
                // because it will likely fail.
                analysisState = AnalysisState.NotSafe;
            }
        }

        return new KustoCode(text, kind, globals, grammar, tree, analysisState, resultType, tokens, tokenStarts, localCache);
    }

    /// <summary>
    /// Determines the result type of a query or control command block.
    /// </summary>
    private static TypeSymbol determineResultType(SyntaxNode root)
    {
        SyntaxList1<SeparatedElement1<Statement>> statements;

        if (root instanceof QueryBlock qb)
        {
            statements = qb.statements();
        }
        else if (root instanceof CommandBlock cb)
        {
            statements = cb.statements();
        }
        else
        {
            return null;
        }

        // get the last expression's type
        if (statements.size() > 0
            && statements.get(statements.size() - 1).element() instanceof ExpressionStatement es)
        {
            return es.expression().resultType();
        }
        else
        {
            return null;
        }
    }

    /// <summary>
    /// Returns a new <see cref="KustoCode"/> with semantic analysis performed
    /// or the current instance if semantic analysis has already been performed.
    /// </summary>
    public KustoCode analyze(GlobalState globals, CancellationToken cancellationToken)
    {
        if (globals == null)
        {
            globals = this.globals();
        }

        if (this.hasSemantics() && this.globals() == globals)
        {
            return this;
        }
        else
        {
            return create(this.text(), globals, _lexerTokens, _lexerTokenStarts, true, cancellationToken); // PORT: §3.12 analyze: true
        }
    }

    public KustoCode analyze(GlobalState globals) // PORT: §3.12 cancellationToken = default
    {
        return analyze(globals, CancellationToken.NONE);
    }

    public KustoCode analyze() // PORT: §3.12 globals = null
    {
        return analyze(null, CancellationToken.NONE);
    }

    /// <summary>
    /// Creates a new instance of <see cref="KustoCode"/> with the specified <see cref="GlobalState"/>
    /// </summary>
    public KustoCode withGlobals(GlobalState globals, CancellationToken cancellationToken)
    {
        if (this.globals() == globals)
        {
            return this;
        }
        else
        {
            return create(this.text(), globals, this._lexerTokens, this._lexerTokenStarts, this.hasSemantics(), cancellationToken); // PORT: §3.12 analyze: this.HasSemantics
        }
    }

    public KustoCode withGlobals(GlobalState globals) // PORT: §3.12 cancellationToken = default
    {
        return withGlobals(globals, CancellationToken.NONE);
    }

    /// <summary>
    /// Determines the code kind from the text. See <see cref="CodeKinds"/>.
    /// </summary>
    public static String getKind(String text)
    {
        var position = 0;

        while (position < text.length())
        {
            var token = TokenParser.parseToken(text, position);
            if (token != null)
            {
                if (token.kind() == SyntaxKind.DotToken)
                    return CodeKinds.Command;

                if (token.kind() == SyntaxKind.DirectiveToken)
                {
                    // skip directive line and continue looking
                    var nextStart = TextFacts.getNextLineStart(text, position + token.length());
                    if (nextStart > position)
                    {
                        position = nextStart;
                        continue;
                    }
                }
            }
            break;
        }

        // not a command, so it must be a query.
        return CodeKinds.Query;
    }

    private volatile List<Diagnostic> diagnostics; // PORT: §3.13 CAS-published

    /// <summary>
    /// Gets all diagnostics in the code (syntactic and semantic)
    /// </summary>
    public List<Diagnostic> getDiagnostics(CancellationToken cancellationToken)
    {
        if (this.diagnostics == null)
        {
            var include = DiagnosticsInclude.Syntactic | DiagnosticsInclude.Semantic;
            List<Diagnostic> diagnostics = this.syntax().getContainedDiagnostics(include, cancellationToken);

            if (_analysisState == AnalysisState.NotSafe)
            {
                diagnostics = ListExtensions.toSafeList(diagnostics).addItem(DiagnosticFacts.getQuerySyntaxDepthExceeded()).asList(); // PORT: §3.10 SafeList is not a List
            }

            Interlocked.compareExchange(DIAGNOSTICS, this, diagnostics, null); // PORT: §3.13
        }

        return this.diagnostics;
    }

    public List<Diagnostic> getDiagnostics() // PORT: §3.12 cancellationToken = default
    {
        return getDiagnostics(CancellationToken.NONE);
    }

    private volatile List<Diagnostic> syntaxDiagnostics; // PORT: §3.13 CAS-published

    /// <summary>
    /// Gets syntax diagnostics in the code.
    /// </summary>
    public List<Diagnostic> getSyntaxDiagnostics(CancellationToken cancellationToken)
    {
        if (this.syntaxDiagnostics == null)
        {
            var diagnostics = this.syntax().getContainedSyntaxDiagnostics();
            Interlocked.compareExchange(SYNTAX_DIAGNOSTICS, this, diagnostics, null); // PORT: §3.13
        }

        return this.syntaxDiagnostics;
    }

    public List<Diagnostic> getSyntaxDiagnostics() // PORT: §3.12 cancellationToken = default
    {
        return getSyntaxDiagnostics(CancellationToken.NONE);
    }

    /// <summary>
    /// Gets a list of all the symbols in the scope related to the specified text position.
    /// </summary>
    public List<Symbol> getSymbolsInScope(int position, int match, int include, CancellationToken cancellationToken) // PORT: §3.17 SymbolMatch, IncludeFunctionKind are int flag holders
    {
        var symbols = new ArrayList<Symbol>();

        if (this.hasSemantics())
        {
            Binder.getSymbolsInScope(this.tree(), position, this.globals(), match, include, symbols, cancellationToken);
        }

        return ListExtensions.toReadOnly(symbols);
    }

    public List<Symbol> getSymbolsInScope(int position, int match, int include) // PORT: §3.12 cancellationToken = default
    {
        return getSymbolsInScope(position, match, include, CancellationToken.NONE);
    }

    public List<Symbol> getSymbolsInScope(int position, int match) // PORT: §3.12 include = IncludeFunctionKind.All
    {
        return getSymbolsInScope(position, match, IncludeFunctionKind.All, CancellationToken.NONE);
    }

    public List<Symbol> getSymbolsInScope(int position) // PORT: §3.12 match = SymbolMatch.Any
    {
        return getSymbolsInScope(position, SymbolMatch.Any, IncludeFunctionKind.All, CancellationToken.NONE);
    }

    /// <summary>
    /// Gets the symbol that would be referenced by the name, if the name were to occur at the position in the text.
    /// </summary>
    public Symbol getSpeculativeReferencedSymbol(int position, String name, int match, CancellationToken cancellationToken) // PORT: §3.17
    {
        if (this.hasSemantics())
        {
            return Binder.getReferencedSymbol(this.tree(), position, name, this.globals(), match, cancellationToken);
        }

        return null;
    }

    public Symbol getSpeculativeReferencedSymbol(int position, String name, int match) // PORT: §3.12 cancellationToken = default
    {
        return getSpeculativeReferencedSymbol(position, name, match, CancellationToken.NONE);
    }

    public Symbol getSpeculativeReferencedSymbol(int position, String name) // PORT: §3.12 match = SymbolMatch.Any
    {
        return getSpeculativeReferencedSymbol(position, name, SymbolMatch.Any, CancellationToken.NONE);
    }

    /// <summary>
    /// Gets the <see cref="TableSymbol"/> that holds the columns that are implicitly in scope at the position within the query.
    /// </summary>
    public TableSymbol getColumnsInScope(int position, CancellationToken cancellationToken)
    {
        if (this.hasSemantics())
        {
            return Binder.getRowScope(this.tree(), position, this.globals(), cancellationToken);
        }

        return null;
    }

    public TableSymbol getColumnsInScope(int position) // PORT: §3.12 cancellationToken = default
    {
        return getColumnsInScope(position, CancellationToken.NONE);
    }

    private volatile IntList lineStarts; // PORT: §3.13 CAS-published; §3.17 List<int> -> IntList

    /// <summary>
    /// Gets the 1-based line and lineOffset for a position in the text.
    /// </summary>
    public boolean tryGetLineAndOffset(int position, IntRef line, IntRef lineOffset) // PORT: §3.3 out int
    {
        if (lineStarts == null)
        {
            var tmp = new IntList();
            TextFacts.getLineStarts(this.text(), tmp);
            Interlocked.compareExchange(LINE_STARTS, this, tmp, null); // PORT: §3.13
        }

        var success = TextFacts.tryGetLineAndOffset(this.lineStarts, position, line, lineOffset);

        if (success && (position < 0 || position > this.text().length()))
        {
            success = false;
        }

        return success;
    }

    /// <summary>
    /// Gets the index of the token that includes the text position.
    /// </summary>
    public int getTokenIndex(int position)
    {
        if (this._lexerTokens.length == 0)
            return 0;

        var lastTokenIndex = this._lexerTokens.length - 1;
        var lastToken = this._lexerTokens[lastTokenIndex];
        var lastTokenStart = this._lexerTokenStarts.get(lastTokenIndex);
        if (position >= lastTokenStart + lastToken.length())
            return this._lexerTokens.length - 1;

        var index = this._lexerTokenStarts.binarySearch(position);
        index = index >= 0 ? index : ~index - 1;

        return index;
    }

    /// <summary>
    /// The lexical tokens produced during parsing.
    /// </summary>
    public List<LexicalToken> getLexicalTokens()
    {
        return Collections.unmodifiableList(Arrays.asList(this._lexerTokens)); // PORT: §3.17 array as IReadOnlyList; O(1) view, no copy
    }
}
