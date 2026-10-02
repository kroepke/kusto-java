// Ported from: src/Kusto.Language/Parser/QueryParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import org.graylog.kusto.language.utils.dotnet.Out;
import org.graylog.kusto.language.utils.dotnet.Linq;
import org.graylog.kusto.language.QueryOperatorParameterValueKind;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.graylog.kusto.language.Aggregates;
import org.graylog.kusto.language.DiagnosticFacts;
import org.graylog.kusto.language.Functions;
import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.KustoFacts;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.QueryOperatorParameter;
import org.graylog.kusto.language.QueryOperatorParameters;
import org.graylog.kusto.language.editor.CompletionHint;
import org.graylog.kusto.language.syntax.*;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Internal;

public class QueryParser
{
    private final Source<LexicalToken> _source;
    private final ParseOptions _options;
    private int _pos;

    private QueryParser(Source<LexicalToken> source, int start, ParseOptions options)
    {
        _source = source;
        _options = options != null ? options : ParseOptions.Default; // PORT: §3.14 ??
        _pos = start;
    }

    private QueryParser(LexicalToken[] tokens, int start, ParseOptions options)
    {
        this(new ArraySource<LexicalToken>(Arrays.asList(tokens)), start, options); // PORT: §3.17 array as IReadOnlyList
    }

    public static Expression parseExpression(LexicalToken[] tokens, int start, ParseOptions options)
    {
        return new QueryParser(tokens, start, options).parseExpression();
    }

    public static Expression parseExpression(LexicalToken[] tokens, int start) // PORT: §3.12 optional parameter options = null
    {
        return parseExpression(tokens, start, null);
    }

    public static Expression parseExpression(LexicalToken[] tokens) // PORT: §3.12 optional parameters start = 0, options = null
    {
        return parseExpression(tokens, 0, null);
    }

    public static Expression parseExpression(String text, ParseOptions options)
    {
        return parseExpression(TokenParser.parseTokens(text, options), 0, options);
    }

    public static Expression parseExpression(String text) // PORT: §3.12 optional parameter options = null
    {
        return parseExpression(text, (ParseOptions) null);
    }

    public static QueryBlock parseQuery(LexicalToken[] tokens, int start, ParseOptions options)
    {
        return new QueryParser(tokens, start, options).parseQuery();
    }

    public static QueryBlock parseQuery(LexicalToken[] tokens, int start) // PORT: §3.12 optional parameter options = null
    {
        return parseQuery(tokens, start, null);
    }

    public static QueryBlock parseQuery(LexicalToken[] tokens) // PORT: §3.12 optional parameters start = 0, options = null
    {
        return parseQuery(tokens, 0, null);
    }

    public static QueryBlock parseQuery(String text, ParseOptions options)
    {
        return parseQuery(TokenParser.parseTokens(text, options), 0, options);
    }

    public static QueryBlock parseQuery(String text) // PORT: §3.12 optional parameter options = null
    {
        return parseQuery(text, (ParseOptions) null);
    }

    public static FunctionParameters parseFunctionParameters(LexicalToken[] tokens, int start, ParseOptions options)
    {
        return new QueryParser(tokens, start, options).parseFunctionParameters();
    }

    public static FunctionParameters parseFunctionParameters(LexicalToken[] tokens, int start) // PORT: §3.12 optional parameter options = null
    {
        return parseFunctionParameters(tokens, start, null);
    }

    public static FunctionParameters parseFunctionParameters(LexicalToken[] tokens) // PORT: §3.12 optional parameters start = 0, options = null
    {
        return parseFunctionParameters(tokens, 0, null);
    }

    public static FunctionParameters parseFunctionParameters(String text, ParseOptions options)
    {
        return parseFunctionParameters(TokenParser.parseTokens(text, options), 0, options);
    }

    public static FunctionParameters parseFunctionParameters(String text) // PORT: §3.12 optional parameter options = null
    {
        return parseFunctionParameters(text, (ParseOptions) null);
    }

    public static FunctionParameter parseFunctionParameter(LexicalToken[] tokens, int start, ParseOptions options)
    {
        return new QueryParser(tokens, start, options).parseFunctionParameter();
    }

    public static FunctionParameter parseFunctionParameter(LexicalToken[] tokens, int start) // PORT: §3.12 optional parameter options = null
    {
        return parseFunctionParameter(tokens, start, null);
    }

    public static FunctionParameter parseFunctionParameter(LexicalToken[] tokens) // PORT: §3.12 optional parameters start = 0, options = null
    {
        return parseFunctionParameter(tokens, 0, null);
    }

    public static FunctionParameter parseFunctionParameter(String text, ParseOptions options)
    {
        return parseFunctionParameter(TokenParser.parseTokens(text, options), 0, options);
    }

    public static FunctionParameter parseFunctionParameter(String text) // PORT: §3.12 optional parameter options = null
    {
        return parseFunctionParameter(text, (ParseOptions) null);
    }

    public static FunctionBody parseFunctionBody(LexicalToken[] tokens, int start, ParseOptions options)
    {
        return new QueryParser(tokens, start, options).parseFunctionBody();
    }

    public static FunctionBody parseFunctionBody(LexicalToken[] tokens, int start) // PORT: §3.12 optional parameter options = null
    {
        return parseFunctionBody(tokens, start, null);
    }

    public static FunctionBody parseFunctionBody(LexicalToken[] tokens) // PORT: §3.12 optional parameters start = 0, options = null
    {
        return parseFunctionBody(tokens, 0, null);
    }

    public static FunctionBody parseFunctionBody(String text, ParseOptions options)
    {
        return parseFunctionBody(TokenParser.parseTokens(text, options), 0, options);
    }

    public static FunctionBody parseFunctionBody(String text) // PORT: §3.12 optional parameter options = null
    {
        return parseFunctionBody(text, (ParseOptions) null);
    }

    public static Expression parseEntityPath(LexicalToken[] tokens, int start, ParseOptions options)
    {
        return new QueryParser(tokens, start, options).parseEntityPathExpression();
    }

    public static Expression parseEntityPath(LexicalToken[] tokens, int start) // PORT: §3.12 optional parameter options = null
    {
        return parseEntityPath(tokens, start, null);
    }

    public static Expression parseEntityPath(LexicalToken[] tokens) // PORT: §3.12 optional parameters start = 0, options = null
    {
        return parseEntityPath(tokens, 0, null);
    }

    public static Expression parseEntityPath(String text, ParseOptions options)
    {
        return parseEntityPath(TokenParser.parseTokens(text, options), 0, options);
    }

    public static Expression parseEntityPath(String text) // PORT: §3.12 optional parameter options = null
    {
        return parseEntityPath(text, (ParseOptions) null);
    }

    public static Expression parseEntityGroup(LexicalToken[] tokens, int start, ParseOptions options)
    {
        return new QueryParser(tokens, start, options).parseEntityGroup();
    }

    public static Expression parseEntityGroup(LexicalToken[] tokens, int start) // PORT: §3.12 optional parameter options = null
    {
        return parseEntityGroup(tokens, start, null);
    }

    public static Expression parseEntityGroup(LexicalToken[] tokens) // PORT: §3.12 optional parameters start = 0, options = null
    {
        return parseEntityGroup(tokens, 0, null);
    }

    public static Expression parseEntityGroup(String text, ParseOptions options)
    {
        return parseEntityGroup(TokenParser.parseTokens(text, options), 0, options);
    }

    public static Expression parseEntityGroup(String text) // PORT: §3.12 optional parameter options = null
    {
        return parseEntityGroup(text, (ParseOptions) null);
    }

    public static Expression parseLiteral(LexicalToken[] tokens, int start, ParseOptions options)
    {
        return new QueryParser(tokens, start, options).parseLiteral();
    }

    public static Expression parseLiteral(LexicalToken[] tokens, int start) // PORT: §3.12 optional parameter options = null
    {
        return parseLiteral(tokens, start, null);
    }

    public static Expression parseLiteral(LexicalToken[] tokens) // PORT: §3.12 optional parameters start = 0, options = null
    {
        return parseLiteral(tokens, 0, null);
    }

    public static Expression parseLiteral(String text, ParseOptions options)
    {
        return parseLiteral(TokenParser.parseTokens(text, options), 0, options);
    }

    public static Expression parseLiteral(String text) // PORT: §3.12 optional parameter options = null
    {
        return parseLiteral(text, (ParseOptions) null);
    }

    public static RowSchema parseRowSchema(LexicalToken[] tokens, int start, ParseOptions options)
    {
        return new QueryParser(tokens, start, options).parseRowSchema();
    }

    public static RowSchema parseRowSchema(LexicalToken[] tokens, int start) // PORT: §3.12 optional parameter options = null
    {
        return parseRowSchema(tokens, start, null);
    }

    public static RowSchema parseRowSchema(LexicalToken[] tokens) // PORT: §3.12 optional parameters start = 0, options = null
    {
        return parseRowSchema(tokens, 0, null);
    }

    public static RowSchema parseRowSchema(String text, ParseOptions options)
    {
        return parseRowSchema(TokenParser.parseTokens(text, options), 0, options);
    }

    public static RowSchema parseRowSchema(String text) // PORT: §3.12 optional parameter options = null
    {
        return parseRowSchema(text, (ParseOptions) null);
    }

    // region Stack Safe Parsing

    /// <summary>
    /// The maximum expression depth recursive parsing will allow before switching to a stack safe parsing strategy.
    /// </summary>
    private static final int MaxDepth = 300;

    /// <summary>
    /// The current expression depth.
    /// </summary>
    private int _depth;

    /// <summary>
    /// The safe safe grammar parser.
    /// </summary>
    private StackSafeParser<LexicalToken> _safeParser;

    /// <summary>
    /// The output list used for grammar parsing.
    /// </summary>
    private List<Object> _safeOutput;

    /// <summary>
    /// The grammar to parse with the stack safe parser
    /// </summary>
    private QueryGrammar _safeQueryGrammar;

    /// <summary>
    /// Parse using fnParse unless max depth has been exceeded then safe parse fnGrammar.
    /// </summary>
    @SuppressWarnings("unchecked")
    private <TResult> TResult stackSafeParse(
        Function<QueryParser, TResult> fnParse,
        Function<QueryGrammar, Parser2<LexicalToken, TResult>> fnGrammar)
    {
        if (_depth > MaxDepth)
        {
            if (_safeParser == null)
            {
                _safeOutput = new ArrayList<Object>();
                _safeParser = new StackSafeParser<LexicalToken>(_source, _safeOutput);
                _safeQueryGrammar = QueryGrammar.from(GlobalState.default_().withParseOptions(_options));
            }

            var grammar = fnGrammar.apply(_safeQueryGrammar);
            var len = _safeParser.parse(grammar, _pos, 0);
            if (len >= 0)
            {
                _pos += len;
                var result = (TResult) _safeOutput.get(0);
                _safeOutput.clear();
                return result;
            }
            else
            {
                return null; // PORT: §3.10 default(TResult)
            }
        }
        else
        {
            _depth++;
            var result = fnParse.apply(this);
            _depth--;
            return result;
        }
    }

    // endregion

    // region Reset Points

    /// <summary>
    /// Gets the reset point for the current input position.
    /// </summary>
    @Internal
    public int getResetPoint()
    {
        return _pos;
    }

    /// <summary>
    /// Resets the parser to the reset point.
    /// </summary>
    @Internal
    public void reset(int resetPoint)
    {
        _pos = resetPoint;
    }

    /// <summary>
    /// Returns the best result of the set of parsers
    /// </summary>
    private <TElement extends SyntaxElement> TElement parseBest(List<Function<QueryParser, TElement>> parsers)
    {
        var start = getResetPoint();
        var bestEnd = start;
        TElement bestResult = null;

        for (var parser : parsers)
        {
            reset(start);
            var result = parser.apply(this);
            var end = getResetPoint();
            if (end > bestEnd)
            {
                bestEnd = end;
                bestResult = result;
            }
        }

        reset(bestEnd);
        return bestResult;
    }

    // endregion

    // region Tokens

    /// <summary>
    /// Invokes the parser, but limits the amount of tokens it can consume.
    /// </summary>
    private <TSyntax> TSyntax limit(int limit, Function<QueryParser, TSyntax> fnParser)
    {
        var source = new LimitSource<LexicalToken>(_source, _pos + limit);
        var limitedParser = new QueryParser(source, _pos, _options);
        var result = fnParser.apply(limitedParser);
        _pos = limitedParser._pos;
        return result;
    }

    /// <summary>
    /// Returns the next <see cref="LexicalToken"/>
    /// </summary>
    private LexicalToken peekToken()
    {
        return !_source.isEnd(_pos) ? _source.peek(_pos) : NoToken;
    }

    /// <summary>
    /// Returns the next <see cref="LexicalToken"/>
    /// </summary>
    private LexicalToken peekToken(int offset)
    {
        var index = _pos + offset;
        return !_source.isEnd(index) ? _source.peek(index) : NoToken;
    }

    private static final LexicalToken NoToken =
        new LexicalToken(SyntaxKind.None, "", "");

    /// <summary>
    /// Returns the next <see cref="LexicalToken"/> as a <see cref="SyntaxToken"/>,
    /// or null if there are no more tokens.
    /// </summary>
    private SyntaxToken parseToken()
    {
        var tok = peekToken();
        if (tok.kind() != SyntaxKind.None)
        {
            _pos++;
            return SyntaxToken.from(tok);
        }
        else
        {
            return null;
        }
    }

    /// <summary>
    /// Returns the next <see cref="SyntaxToken"/> if it matches the kind or null.
    /// </summary>
    private SyntaxToken parseToken(SyntaxKind kind)
    {
        if (peekToken().kind() == kind)
        {
            return parseToken();
        }
        else
        {
            return null;
        }
    }

    /// <summary>
    /// Returns the next <see cref="SyntaxToken"/> if it matches one of the kinds or null.
    /// </summary>
    private SyntaxToken parseToken(List<SyntaxKind> kinds)
    {
        if (kinds.contains(peekToken().kind()))
        {
            return parseToken();
        }
        else
        {
            return null;
        }
    }

    /// <summary>
    /// Returns the next <see cref="SyntaxToken"/> if it matches the kind or a missing version of that token kind.
    /// </summary>
    private SyntaxToken parseRequiredToken(SyntaxKind kind)
    {
        var token = parseToken(kind); // PORT: §3.14 ??
        return token != null ? token : createMissingToken(kind);
    }

    private static SyntaxToken createMissingToken(SyntaxKind kind)
    {
        return SyntaxParsers.createMissingToken(kind);
    }

    /// <summary>
    /// Returns the next <see cref="SyntaxToken"/> if it matches one of the kinds or a missing version of that token kind.
    /// </summary>
    //private SyntaxToken ParseRequiredToken(IReadOnlyList<SyntaxKind> kinds)
    //{
    //    return ParseToken(kinds) ?? CreateMissingToken(kinds);
    //}

    private static SyntaxToken createMissingToken(List<SyntaxKind> kinds)
    {
        return SyntaxParsers.createMissingToken(kinds);
    }

    /// <summary>
    /// Scans one or more adjacent lexical tokens that together matches the text.
    /// </summary>
    private int scanToken(String text, int offset)
    {
        return SyntaxParsers.matchesText(_source, _pos + offset, text);
    }

    private int scanToken(String text) // PORT: §3.12 optional parameter offset = 0
    {
        return scanToken(text, 0);
    }

    /// <summary>
    /// Scans one or more adjacent lexical tokens that together matches the one of the texts.
    /// </summary>
    private int scanToken(List<String> texts, int offset)
    {
        for (int i = 0; i < texts.size(); i++)
        {
            var len = scanToken(texts.get(i), offset);
            if (len > 0)
                return len;
        }

        return -1;
    }

    private int scanToken(List<String> texts) // PORT: §3.12 optional parameter offset = 0
    {
        return scanToken(texts, 0);
    }

    /// <summary>
    /// Parses one or more adjacent lexical tokens that together matches the text into a single token,
    /// or returns null.
    /// </summary>
    private SyntaxToken parseTokenText(String text, SyntaxKind asKind) // PORT: §2.5 ParseToken(string, SyntaxKind?)
    {
        var len = SyntaxParsers.matchesText(_source, _pos, text);
        if (len > 0)
        {
            var token = SyntaxParsers.produceSyntaxToken(_source, _pos, len, text, asKind);
            _pos += len;
            return token;
        }

        return null;
    }

    private SyntaxToken parseTokenText(String text) // PORT: §2.5, §3.12 optional parameter asKind = null
    {
        return parseTokenText(text, null);
    }

    /// <summary>
    /// Converts the next count adjacent tokens into a single token
    /// or returns null.
    /// </summary>
    private SyntaxToken parseToken(int count, SyntaxKind asKind)
    {
        var token = SyntaxParsers.produceSyntaxToken(_source, _pos, count, asKind);

        if (token != null)
        {
            _pos += count;
        }

        return token;
    }

    private SyntaxToken parseToken(int count) // PORT: §3.12 optional parameter asKind = null
    {
        return parseToken(count, null);
    }

    private SyntaxToken parseTokenText(List<String> texts) // PORT: §2.5 ParseToken(IReadOnlyList<string>)
    {
        for (int i = 0; i < texts.size(); i++)
        {
            var token = parseTokenText(texts.get(i));
            if (token != null)
                return token;
        }

        return null;
    }

    /// <summary>
    /// Parses one or more adjacent lexical tokens that together matches the text into a single token,
    /// or returns a missing token.
    /// </summary>
    private SyntaxToken parseRequiredTokenText(String text, SyntaxKind kind) // PORT: §2.5 ParseRequiredToken(string, SyntaxKind)
    {
        var token = parseTokenText(text, kind); // PORT: §3.14 ??
        return token != null ? token : SyntaxParsers.createMissingToken(text);
    }

    private SyntaxToken parseRequiredTokenText(String text) // PORT: §2.5, §3.12 optional parameter kind = IdentifierToken
    {
        return parseRequiredTokenText(text, SyntaxKind.IdentifierToken);
    }

    private SyntaxToken parseRequiredTokenText(List<String> texts) // PORT: §2.5 ParseRequiredToken(IReadOnlyList<string>)
    {
        var token = parseTokenText(texts); // PORT: §3.14 ??
        return token != null ? token : SyntaxParsers.createMissingTokenText(texts);
    }

    private boolean scanIdentifierOrKeywordAsIdentifier(int offset)
    {
        var token = peekToken(offset);
        return token.kind() == SyntaxKind.IdentifierToken
            || (SyntaxFacts.isKeyword(token.kind()) && SyntaxFacts.canBeIdentifier(token.kind()));
    }

    private boolean scanIdentifierOrKeywordAsIdentifier() // PORT: §3.12 optional parameter offset = 0
    {
        return scanIdentifierOrKeywordAsIdentifier(0);
    }

    private static final Set<SyntaxKind> s_extendedKeyordsAsIdentifiers =
        new LinkedHashSet<SyntaxKind>(KustoFacts.ExtendedKeywordsAsIdentifiers); // PORT: §3.17 ToHashSetEx

    private boolean scanExtendedKeywordAsIdentifier(int offset)
    {
        var token = peekToken(offset);
        return SyntaxFacts.isKeyword(token.kind()) && s_extendedKeyordsAsIdentifiers.contains(token.kind());
    }

    private boolean scanExtendedKeywordAsIdentifier() // PORT: §3.12 optional parameter offset = 0
    {
        return scanExtendedKeywordAsIdentifier(0);
    }

    private SyntaxToken parseIdentiferOrKeywordAsIdentifier()
    {
        var kind = peekToken().kind();
        if (kind == SyntaxKind.IdentifierToken
            || (SyntaxFacts.isKeyword(kind) && SyntaxFacts.canBeIdentifier(kind)))
        {
            return parseToken();
        }
        else
        {
            return null;
        }
    }

    private Expression parseIdentifierOrKeywordTokenLiteral()
    {
        var token = parseIdentiferOrKeywordAsIdentifier();
        if (token != null)
        {
            return new LiteralExpression(SyntaxKind.TokenLiteralExpression, token);
        }

        return null;
    }

    // endregion


    // region Missing Nodes

    private NameDeclaration createMissingNameDeclaration()
    {
        var token = this.peekToken(); // PORT: §3.15 `is LexicalToken token` is always true for a non-null result
        var dx = SyntaxFacts.isKeyword(token.kind())
                    ? DiagnosticFacts.getMissingNameWithKeyword(token.text())
                    : DiagnosticFacts.getMissingName();
        return new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken), Arrays.asList(dx));
    }

    private NameReference createMissingNameReference()
    {
        var token = this.peekToken(); // PORT: §3.15
        var dx = SyntaxFacts.isKeyword(token.kind())
                    ? DiagnosticFacts.getMissingNameWithKeyword(token.text())
                    : DiagnosticFacts.getMissingName();

        return new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken), Arrays.asList(dx));
    }

    private Supplier<NameReference> _fnCreateMissingNameReference;
    private Supplier<NameReference> fnCreateMissingNameReference() // PORT: §2.3 property FnCreateMissingNameReference
    {
        if (_fnCreateMissingNameReference == null)
            _fnCreateMissingNameReference = this::createMissingNameReference;
        return _fnCreateMissingNameReference;
    }


    private Supplier<Expression> _fnCreateMissingNameReferenceAsExpression;
    private Supplier<Expression> fnCreateMissingNameReferenceAsExpression() // PORT: §2.3 property FnCreateMissingNameReferenceAsExpression
    {
        if (_fnCreateMissingNameReferenceAsExpression == null)
            _fnCreateMissingNameReferenceAsExpression = () -> (Expression)this.createMissingNameReference();
        return _fnCreateMissingNameReferenceAsExpression;
    }

    private static SyntaxToken createMissingNameToken(List<String> texts)
    {
        return SyntaxToken.missing(SyntaxKind.IdentifierToken, DiagnosticFacts.getTokenExpected(texts));
    }

    private static final Supplier<Expression> CreateMissingStringLiteral = () ->
        new LiteralExpression(
            SyntaxKind.StringLiteralExpression,
            SyntaxToken.missing(SyntaxKind.StringLiteralToken),
            Arrays.asList(DiagnosticFacts.getMissingString()));

    private static final Supplier<Expression> CreateMissingBoolLiteral = () ->
        new LiteralExpression(
            SyntaxKind.BooleanLiteralExpression,
            SyntaxToken.missing(SyntaxKind.BooleanLiteralToken),
            Arrays.asList(DiagnosticFacts.getMissingBoolean()));

    private static final Supplier<Expression> CreateMissingLongLiteral = () ->
        new LiteralExpression(SyntaxKind.LongLiteralExpression,
            SyntaxToken.missing(SyntaxKind.LongLiteralToken),
            Arrays.asList(DiagnosticFacts.getMissingNumber()));

    private static final Supplier<Expression> CreateMissingRealLiteral = () ->
        new LiteralExpression(SyntaxKind.RealLiteralExpression,
            SyntaxToken.missing(SyntaxKind.RealLiteralToken),
            Arrays.asList(DiagnosticFacts.getMissingNumber()));

    private static Expression createMissingTokenLiteral(List<String> tokens)
    {
        return new LiteralExpression(SyntaxKind.TokenLiteralExpression,
            SyntaxToken.missing(SyntaxKind.IdentifierToken),
            Arrays.asList(tokens != null && tokens.size() > 0
                ? DiagnosticFacts.getTokenExpected(tokens)
                : DiagnosticFacts.getTokenExpected("token")));
    }

    private static Expression createMissingTokenLiteral() // PORT: §3.12 optional parameter tokens = null
    {
        return createMissingTokenLiteral(null);
    }

    private static Expression createMissingTypeOfLiteral()
    {
        return new TypeOfLiteralExpression(
            SyntaxToken.missing(SyntaxKind.TypeOfKeyword),
            SyntaxToken.missing(SyntaxKind.OpenParenToken),
            SyntaxList1.<SeparatedElement1<Expression>>empty(),
            SyntaxToken.missing(SyntaxKind.CloseParenToken),
            Arrays.asList(DiagnosticFacts.getMissingTypeOfLiteral()));
    }

    private static final Supplier<Expression> CreateMissingJsonValue = () ->
        new LiteralExpression(
            SyntaxKind.StringLiteralExpression,
            SyntaxToken.missing(SyntaxKind.StringLiteralToken),
            Arrays.asList(DiagnosticFacts.getMissingJsonValue()));

    private static final Supplier<JsonPair> CreateMissingJsonPair = () ->
        new JsonPair(
            SyntaxToken.missing(SyntaxKind.StringLiteralToken),
            SyntaxToken.missing(SyntaxKind.ColonToken),
            new LiteralExpression(SyntaxKind.StringLiteralExpression, SyntaxToken.missing(SyntaxKind.StringLiteralToken)),
            Arrays.asList(DiagnosticFacts.getMissingJsonPair()));

    private static final Supplier<TypeExpression> CreateMissingType = () ->
        new PrimitiveTypeExpression(SyntaxToken.missing(SyntaxKind.IdentifierToken), Arrays.asList(DiagnosticFacts.getMissingTypeName()));

    private static final Supplier<Expression> CreateMissingTypeExpression = () ->
        QueryParser.CreateMissingType.get();

    private static final Supplier<NameAndTypeDeclaration> CreateMissingNameAndTypeDeclaration = () ->
        new NameAndTypeDeclaration(
            new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
            SyntaxToken.missing(SyntaxKind.ColonToken),
            new PrimitiveTypeExpression(SyntaxToken.missing(SyntaxKind.StringKeyword)),
            Arrays.asList(DiagnosticFacts.getMissingParameter()));

    private static final Supplier<Expression> CreateMissingNameAndTypeDeclarationExpression = () ->
        QueryParser.CreateMissingNameAndTypeDeclaration.get();

    private static final Supplier<Expression> CreateMissingValue = () ->
        new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken), Arrays.asList(DiagnosticFacts.getMissingValue()));

    private Expression createMissingExpression()
    {
        // check to see if following token was a keyword and if so report enhanced diagnostic
        var token = this.peekToken(); // PORT: §3.15
        var dx = SyntaxFacts.isKeyword(token.kind())
            ? DiagnosticFacts.getMissingExpressionWithKeyword(token.text())
            : DiagnosticFacts.getMissingExpression();

        return new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken), Arrays.asList(dx));
    }

    // PORT: §3.14 `x ?? CreateMissingExpression()` as an argument expression (one upstream expression per Java expression, left-to-right order kept)
    private Expression missingExpressionIfNull(Expression expression)
    {
        return expression != null ? expression : createMissingExpression();
    }

    private static final Supplier<Name> CreateMissingIdentifierName = () ->
        new TokenName(SyntaxToken.missing(SyntaxKind.IdentifierToken), Arrays.asList(DiagnosticFacts.getMissingExpression()));

    private static final Supplier<SchemaTypeExpression> CreateMissingSchemaType = () ->
        new SchemaTypeExpression(
            SyntaxToken.missing(SyntaxKind.OpenParenToken),
            SyntaxList1.<SeparatedElement1<Expression>>empty(),
            SyntaxToken.missing(SyntaxKind.CloseParenToken),
            Arrays.asList(DiagnosticFacts.getMissingSchemaDeclaration()));

    private static final Supplier<RowSchema> CreateMissingRowSchema = () ->
        new RowSchema(
            SyntaxToken.missing(SyntaxKind.OpenParenToken),
            null,
            SyntaxList1.<SeparatedElement1<NameAndTypeDeclaration>>empty(),
            SyntaxToken.missing(SyntaxKind.CloseParenToken),
            Arrays.asList(DiagnosticFacts.getMissingSchemaDeclaration()));

    private static final Supplier<RowSchema> CreateOmittedRowSchema = () ->
        new RowSchema(
            SyntaxToken.missing(SyntaxKind.OpenParenToken),
            null,
            SyntaxList1.<SeparatedElement1<NameAndTypeDeclaration>>empty(),
            SyntaxToken.missing(SyntaxKind.CloseParenToken));

    private static final Supplier<EvaluateRowSchema> CreateMissingEvaluateRowSchema = () ->
        new EvaluateRowSchema(
            SyntaxToken.missing(SyntaxKind.OpenParenToken),
            null,
            null,
            null,
            SyntaxList1.<SeparatedElement1<NameAndTypeDeclaration>>empty(),
            SyntaxToken.missing(SyntaxKind.CloseParenToken),
            Arrays.asList(DiagnosticFacts.getMissingSchemaDeclaration()));

    private static final Supplier<NamedParameter> CreateMissingNamedParameter = () ->
        new NamedParameter(
            new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
            SyntaxToken.missing(SyntaxKind.EqualToken),
            new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
            CompletionHint.None, // PORT: §3.12 named argument diagnostics: skips expressionHint (default CompletionHint.None)
            Arrays.asList(DiagnosticFacts.getMissingParameter()));

    private static ExpressionList createMissingArgumentList()
    {
        return new ExpressionList(
            SyntaxToken.missing(SyntaxKind.OpenParenToken, DiagnosticFacts.getTokenExpected(SyntaxKind.OpenParenToken)),
            SyntaxList1.<SeparatedElement1<Expression>>empty(),
            SyntaxToken.missing(SyntaxKind.CloseParenToken, DiagnosticFacts.getTokenExpected(SyntaxKind.CloseParenToken)));
    }

    private static FunctionCallExpression createMissingFunctionCallExpression()
    {
        return new FunctionCallExpression(
            new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
            new ExpressionList(
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                SyntaxList1.<SeparatedElement1<Expression>>empty(),
                SyntaxToken.missing(SyntaxKind.CloseParenToken)),
            Arrays.asList(DiagnosticFacts.getMissingFunctionCall()));
    }

    private static MaterializedViewCombineClause createMissingMaterializedViewCombineClause(String name)
    {
        return new MaterializedViewCombineClause(
            SyntaxToken.missing(SyntaxKind.MaterializedViewCombineClause),
            SyntaxToken.missing(SyntaxKind.OpenParenToken),
            new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
            SyntaxToken.missing(SyntaxKind.CloseParenToken),
            Arrays.asList(DiagnosticFacts.getMissingClause(name)));
    }

    private static final Supplier<QueryOperator> CreateMissingQueryOperator = () ->
        new BadQueryOperator(SyntaxToken.missing(SyntaxKind.IdentifierToken), Arrays.asList(DiagnosticFacts.getQueryOperatorExpected()));

    private static final Supplier<Expression> CreateMissingQueryOperatorExpression = () ->
        new BadQueryOperator(SyntaxToken.missing(SyntaxKind.IdentifierToken), Arrays.asList(DiagnosticFacts.getQueryOperatorExpected()));

    // endregion

    // region Lists

    private <TElement extends SyntaxElement> SyntaxList1<SeparatedElement1<TElement>> parseCommaList(
        Function<QueryParser, TElement> elementParser,
        Supplier<TElement> createMissingElement,
        Predicate<QueryParser> fnScanEnd,
        boolean oneOrMore,
        boolean allowTrailingComma)
    {
        var list = new ArrayList<SeparatedElement1<TElement>>();

        var element = (fnScanEnd == null || !fnScanEnd.test(this))
            ? elementParser.apply(this)
            : null;

        if (element == null
            && peekToken().kind() == SyntaxKind.CommaToken
            && createMissingElement != null)
        {
            element = createMissingElement.get();
        }

        while (element != null)
        {
            var token = peekToken();
            var kind = token.kind();

            if (kind == SyntaxKind.EndOfTextToken
                || kind == SyntaxKind.None
                || (fnScanEnd != null && fnScanEnd.test(this)))
            {
                list.add(new SeparatedElement1<TElement>(element, null));
                break;
            }
            else if (kind == SyntaxKind.CommaToken)
            {
                var comma = parseToken();
                list.add(new SeparatedElement1<TElement>(element, comma));

                element = (fnScanEnd == null || !fnScanEnd.test(this))
                    ? elementParser.apply(this)
                    : null;
                if (element != null)
                {
                    continue;
                }
                else if (allowTrailingComma)
                {
                    break;
                }
                else if (createMissingElement != null)
                {
                    list.add(new SeparatedElement1<TElement>(createMissingElement.get(), null));
                }

                break;
            }
            else if (fnScanEnd != null)
            {
                var nextElement = elementParser.apply(this);
                if (nextElement != null)
                {
                    list.add(new SeparatedElement1<TElement>(element, SyntaxParsers.createMissingToken(SyntaxKind.CommaToken)));
                }
                else
                {
                    list.add(new SeparatedElement1<TElement>(element, null));
                }

                element = nextElement;
                continue;
            }
            else
            {
                // no command and no end function.. so just be done
                list.add(new SeparatedElement1<TElement>(element, null));
                break;
            }
        }


        if (oneOrMore && list.size() == 0 && createMissingElement != null)
        {
            list.add(new SeparatedElement1<TElement>(createMissingElement.get()));
        }

        return new SyntaxList1<SeparatedElement1<TElement>>(list);
    }

    // PORT: §3.12 telescoping overloads of ParseCommaList(elementParser, createMissingElement = null, fnScanEnd = null, oneOrMore = false, allowTrailingComma = false)
    private <TElement extends SyntaxElement> SyntaxList1<SeparatedElement1<TElement>> parseCommaList(
        Function<QueryParser, TElement> elementParser,
        Supplier<TElement> createMissingElement,
        Predicate<QueryParser> fnScanEnd,
        boolean oneOrMore)
    {
        return parseCommaList(elementParser, createMissingElement, fnScanEnd, oneOrMore, false);
    }

    private <TElement extends SyntaxElement> SyntaxList1<SeparatedElement1<TElement>> parseCommaList(
        Function<QueryParser, TElement> elementParser,
        Supplier<TElement> createMissingElement,
        Predicate<QueryParser> fnScanEnd)
    {
        return parseCommaList(elementParser, createMissingElement, fnScanEnd, false, false);
    }

    private <TElement extends SyntaxElement> SyntaxList1<SeparatedElement1<TElement>> parseCommaList(
        Function<QueryParser, TElement> elementParser,
        Supplier<TElement> createMissingElement)
    {
        return parseCommaList(elementParser, createMissingElement, null, false, false);
    }

    private <TElement extends SyntaxElement> SyntaxList1<SeparatedElement1<TElement>> parseCommaList(
        Function<QueryParser, TElement> elementParser)
    {
        return parseCommaList(elementParser, null, null, false, false);
    }

    private <TElement extends SyntaxElement> SyntaxList1<TElement> parseList(
        Function<QueryParser, TElement> elementParser,
        Supplier<TElement> createMissingElement,
        Predicate<QueryParser> fnScanEnd,
        boolean oneOrMore)
    {
        var list = new ArrayList<TElement>();

        while (fnScanEnd == null || !fnScanEnd.test(this))
        {
            var element = elementParser.apply(this);
            if (element == null)
                break;
            list.add(element);
        }

        if (oneOrMore && list.size() == 0 && createMissingElement != null)
        {
            list.add(createMissingElement.get());
        }

        return new SyntaxList1<TElement>(list);
    }

    // PORT: §3.12 telescoping overloads of ParseList(elementParser, createMissingElement = null, fnScanEnd = null, oneOrMore = false)
    private <TElement extends SyntaxElement> SyntaxList1<TElement> parseList(
        Function<QueryParser, TElement> elementParser,
        Supplier<TElement> createMissingElement,
        Predicate<QueryParser> fnScanEnd)
    {
        return parseList(elementParser, createMissingElement, fnScanEnd, false);
    }

    private <TElement extends SyntaxElement> SyntaxList1<TElement> parseList(
        Function<QueryParser, TElement> elementParser,
        Supplier<TElement> createMissingElement)
    {
        return parseList(elementParser, createMissingElement, null, false);
    }

    private <TElement extends SyntaxElement> SyntaxList1<TElement> parseList(
        Function<QueryParser, TElement> elementParser)
    {
        return parseList(elementParser, null, null, false);
    }

    private static final Predicate<QueryParser> FnScanCommonListEnd =
        qp -> qp.scanCommonListEnd();

    private boolean scanCommonListEnd(int offset)
    {
        switch (peekToken(offset).kind())
        {
            case CloseParenToken:
            case CloseBracketToken:
            case CloseBraceToken:
            case BarToken:
            case SemicolonToken:
            case EndOfTextToken:
            case None:
                return true;
            default:
                return false;
        }
    }

    private boolean scanCommonListEnd() // PORT: §3.12 optional parameter offset = 0
    {
        return scanCommonListEnd(0);
    }

    private boolean scanCustomListEnd(SyntaxKind kind, int offset)
    {
        if (peekToken(offset).kind() == kind)
            return true;
        return scanCommonListEnd(); // PORT-BUG: upstream drops `offset` here (and in the list overload); mirrored
    }

    private boolean scanCustomListEnd(SyntaxKind kind) // PORT: §3.12 optional parameter offset = 0
    {
        return scanCustomListEnd(kind, 0);
    }

    private boolean scanCustomListEnd(List<SyntaxKind> kinds, int offset)
    {
        var kind = peekToken(offset).kind();

        for (int i = 0; i < kinds.size(); i++)
        {
            if (kind == kinds.get(i))
                return true;
        }

        return scanCommonListEnd(); // PORT-BUG: upstream drops `offset`; mirrored
    }

    private boolean scanCustomListEnd(List<SyntaxKind> kinds) // PORT: §3.12 optional parameter offset = 0
    {
        return scanCustomListEnd(kinds, 0);
    }

    // endregion


    // region Names

    private Name parseName()
    {
        switch (peekToken().kind())
        {
            case OpenBracketToken:
                return parseBracketedName();
            case OpenBraceToken:
                return parseBracedName();
            default:
                return parseIdentifierName();
        }
    }

    private Name parseExtendedName()
    {
        switch (peekToken().kind())
        {
            case OpenBracketToken:
                return parseBracketedName();
            case OpenBraceToken:
                return parseBracedName();
            default:
            {
                var name = parseIdentifierName(); // PORT: §3.14 ??
                return name != null ? name : parseExtendedKeyordAsIdentifierName();
            }
        }
    }

    private SyntaxToken parseKeywordOrIdentifier()
    {
        var tok = peekToken();
        if (tok.kind() == SyntaxKind.IdentifierToken
            || (SyntaxFacts.isKeyword(tok.kind()) && SyntaxFacts.canBeIdentifier(tok.kind())))
        {
            return parseToken();
        }

        return null;
    }

    private Name parseIdentifierName()
    {
        var token = parseKeywordOrIdentifier(); // PORT: §3.15 `is SyntaxToken token` null test
        if (token != null)
            return new TokenName(token);
        return null;
    }

    private Name parseExtendedKeyordAsIdentifierName()
    {
        if (scanExtendedKeywordAsIdentifier())
        {
            return new TokenName(parseToken());
        }

        return null;
    }

    private Name parseBracketedName()
    {
        if (scanBracketedName() > 0)
        {
            var open = parseToken();
            var expr = parseStringOrCompoundStringLiteral();
            var close = parseRequiredToken(SyntaxKind.CloseBracketToken);
            return new BracketedName(open, expr, close);
        }

        return null;
    }

    private int scanName(int offset)
    {
        var tok = peekToken(offset);
        switch (tok.kind())
        {
            case IdentifierToken:
                return 1;
            case OpenBracketToken:
                return scanBracketedName(offset);
            case OpenBraceToken:
                if (scanIdentifierOrKeywordAsIdentifier(offset + 1)
                    && peekToken(offset + 2).kind() == SyntaxKind.CloseBraceToken)
                {
                    return 3;
                }
                return -1;
            default:
                return scanIdentifierOrKeywordAsIdentifier(offset) ? 1 : -1;
        }
    }

    private int scanName() // PORT: §3.12 optional parameter offset = 0
    {
        return scanName(0);
    }

    private int scanExtendedName(int offset)
    {
        var result = scanName(offset);
        if (result >= 0)
            return result;
        return scanExtendedKeywordAsIdentifier(offset) ? 1 : -1;
    }

    private int scanExtendedName() // PORT: §3.12 optional parameter offset = 0
    {
        return scanExtendedName(0);
    }

    private int scanBracketedName(int offset)
    {
        if (peekToken(offset).kind() == SyntaxKind.OpenBracketToken)
        {
            int len = scanStringOrCompoundStringLiteral(offset + 1); // PORT: §3.15 `is int len` pattern → local
            if (len > 0
                && peekToken(offset + 1 + len).kind() == SyntaxKind.CloseBracketToken)
            {
                return len + 2;
            }
        }

        return -1;
    }

    private int scanBracketedName() // PORT: §3.12 optional parameter offset = 0
    {
        return scanBracketedName(0);
    }

    private int scanWildcardedName(int offset)
    {
        var start = offset;

        var token = peekToken(offset);

        // must start with asterisk or a single name/keyword and then an asterisk
        if (token.kind() == SyntaxKind.AsteriskToken)
        {
            offset++;
        }
        else if ((token.kind() == SyntaxKind.IdentifierToken || scanExtendedKeywordAsIdentifier(offset))
            && peekToken(offset + 1) != null // PORT: §3.15 `is LexicalToken nextToken` (PeekToken never returns null)
            && peekToken(offset + 1).kind() == SyntaxKind.AsteriskToken
            && (peekToken(offset + 1).trivia().length() == 0 || _options.allowNonAdjacentWildcardParts()))
        {
            offset += 2;
        }
        else
        {
            return -1;
        }

        // then followed by zero or more additional identifiers, keywords or asterisks.
        while (
            ((token = peekToken(offset)).kind() == SyntaxKind.IdentifierToken
                || token.kind() == SyntaxKind.LongLiteralToken
                || token.kind() == SyntaxKind.AsteriskToken
                || scanExtendedKeywordAsIdentifier(offset))
            && (token.trivia().length() == 0 || _options.allowNonAdjacentWildcardParts()))
        {
            offset++;
        }

        return offset > start ? offset - start : -1;
    }

    private int scanWildcardedName() // PORT: §3.12 optional parameter offset = 0
    {
        return scanWildcardedName(0);
    }

    private SyntaxToken parseWildcardedIdentifier()
    {
        var len = scanWildcardedName();
        if (len > 0)
        {
            var trivia = peekToken().trivia();
            var text = getCombinedTokenText(0, len);
            var valueText = _options.allowNonAdjacentWildcardParts()
                ? getCombinedTokenText(0, len, false)
                : text;
            var lit = SyntaxToken.identifier(trivia, text, valueText);
            _pos += len;
            return lit;
        }

        return null;
    }

    private NameReference parseWildcardedNameReference()
    {
        var id = parseWildcardedIdentifier();
        if (id != null)
        {
            return new NameReference(new WildcardedName(id));
        }
        return null;
    }

    private boolean scanBracketedWildcardedName(int offset)
    {
        return peekToken(offset).kind() == SyntaxKind.OpenBracketToken
            && scanWildcardedName(offset + 1) > 0;
    }

    private boolean scanBracketedWildcardedName() // PORT: §3.12 optional parameter offset = 0
    {
        return scanBracketedWildcardedName(0);
    }

    private NameReference parseBracketedWildcardedNameReference()
    {
        if (scanBracketedWildcardedName())
        {
            var open = parseToken();
            var wildcard = parseWildcardedIdentifier();
            var close = parseRequiredToken(SyntaxKind.CloseBracketToken);
            return new NameReference(new BracketedWildcardedName(open, wildcard, close));
        }

        return null;
    }

    private String getCombinedTokenText(int start, int length, boolean includeInnerTrivia)
    {
        if (length == 1)
        {
            return peekToken(start).text();
        }

        var builder = new StringBuilder();
        for (int i = 0; i < length; i++)
        {
            var token = peekToken(start + i);
            if (i > 0 && includeInnerTrivia)
                builder.append(token.trivia());
            builder.append(token.text());
        }

        return builder.toString();
    }

    private String getCombinedTokenText(int start, int length) // PORT: §3.12 optional parameter includeInnerTrivia = true
    {
        return getCombinedTokenText(start, length, true);
    }

    private String getCombinedTokenText(int start) // PORT: §3.12 optional parameters length = 1, includeInnerTrivia = true
    {
        return getCombinedTokenText(start, 1, true);
    }

    private String getCombinedTokenText() // PORT: §3.12 optional parameters start = 0, length = 1, includeInnerTrivia = true
    {
        return getCombinedTokenText(0, 1, true);
    }

    private NameReference parseBracketedNameReference()
    {
        var name = parseBracketedName();
        if (name != null)
        {
            return new NameReference(name);
        }

        return null;
    }

    /// <summary>
    /// Includes bracketed names, identifiers and limited keywords-as-identifiers
    /// </summary>
    private NameReference parseNameReference()
    {
        var name = parseName();
        return name != null ? new NameReference(name) : null;
    }

    private static final Function<QueryParser, Expression> FnParseNameReference =
        qp -> qp.parseNameReference();

    /// <summary>
    /// Includes bracketed names, identifiers and extended keywords-as-identifiers
    /// </summary>
    private NameReference parseExtendedNameReference()
    {
        var name = parseExtendedName();
        return name != null ? new NameReference(name) : null;
    }

    /// <summary>
    /// Includes bracketed names, identifiers and limited keywords-as-identifiers
    /// </summary>
    private NameDeclaration parseNameDeclaration()
    {
        var name = parseName();
        return name != null ? new NameDeclaration(name) : null;
    }

    /// <summary>
    /// Includes bracketed names, identifiers and extended keywords-as-identifiers
    /// </summary>
    private NameDeclaration parseExtendedNameDeclaration()
    {
        var name = parseExtendedName();
        return name != null ? new NameDeclaration(name) : null;
    }

    /// <summary>
    /// Scan a braced name (client parameter) and returns amount of tokens it consumes.
    /// </summary>
    private int scanBracedName(int offset)
    {
        var pos = offset;
        var token = peekToken(pos);
        if (token.kind() != SyntaxKind.OpenBraceToken)
            return -1;
        pos++;

        token = peekToken(pos);
        if ((token.kind() != SyntaxKind.IdentifierToken
               && SyntaxFacts.getCategory(token.kind()) != SyntaxCategory.Keyword)
            || token.trivia().length() > 0)
            return -1;
        pos++;

        token = peekToken(pos);
        if (token.kind() == SyntaxKind.OpenBracketToken
            && token.trivia().length() == 0)
        {
            pos++;

            token = peekToken(pos);
            if (token.kind() == SyntaxKind.MinusToken
                && token.trivia().length() == 0)
                pos++;

            token = peekToken(pos);
            if (token.kind() != SyntaxKind.LongLiteralToken
                || token.trivia().length() > 0)
                return -1;
            pos++;

            token = peekToken(pos);
            if (token.kind() != SyntaxKind.CloseBracketToken
                || token.trivia().length() > 0)
                return -1;
            pos++;
        }

        token = peekToken(pos);
        if (token.kind() != SyntaxKind.CloseBraceToken
            || token.trivia().length() > 0)
            return -1;
        pos++;

        return pos - offset;
    }

    private int scanBracedName() // PORT: §3.12 optional parameter offset = 0
    {
        return scanBracedName(0);
    }

    private Name parseBracedName()
    {
        var len = scanBracedName();
        if (len > 2)
        {
            var open = SyntaxToken.from(peekToken(0));
            var nameText = getCombinedTokenText(1, len - 2);
            var close = SyntaxToken.from(peekToken(len - 1));
            var nameToken = SyntaxToken.identifier("", nameText, nameText);
            _pos += len;
            return new BracedName(open, nameToken, close);
        }
        return null;
    }

    private Expression parseBracedNameReference()
    {
        var name = parseBracedName();
        return name != null ? new NameReference(name) : null;
    }

    @Internal
    public static boolean isKeywordInNamePosition(Source<LexicalToken> source, int start)
    {
        var token = source.peek(start); // PORT: §3.15 `is LexicalToken token` null test (Source.peek yields null past the end)
        LexicalToken nextToken;
        if (token != null && SyntaxFacts.isKeyword(token.kind())
            && (nextToken = source.peek(start + 1)) != null)
        {
            // look for token following keyword that only happens after names in expressions
            // like infix binary operators, etc.
            switch (nextToken.kind())
            {
                // infix binary operators
                case SyntaxKind.AndKeyword:
                case SyntaxKind.OrKeyword:
                case SyntaxKind.EqualEqualToken:
                case SyntaxKind.BangEqualToken:
                case SyntaxKind.EqualTildeToken:
                case SyntaxKind.BangTildeToken:
                case SyntaxKind.GreaterThanToken:
                case SyntaxKind.GreaterThanOrEqualToken:
                case SyntaxKind.LessThanToken:
                case SyntaxKind.LessThanOrEqualToken:
                case SyntaxKind.AsteriskToken:
                case SyntaxKind.SlashToken:
                case SyntaxKind.PercentToken:
                case SyntaxKind.HasKeyword:
                case SyntaxKind.NotHasKeyword:
                case SyntaxKind.HasCsKeyword:
                case SyntaxKind.NotHasCsKeyword:
                case SyntaxKind.HasPrefixKeyword:
                case SyntaxKind.NotHasPrefixKeyword:
                case SyntaxKind.HasPrefixCsKeyword:
                case SyntaxKind.NotHasPrefixCsKeyword:
                case SyntaxKind.HasSuffixKeyword:
                case SyntaxKind.NotHasSuffixKeyword:
                case SyntaxKind.HasSuffixCsKeyword:
                case SyntaxKind.NotHasSuffixCsKeyword:
                case SyntaxKind.LikeKeyword:
                case SyntaxKind.NotLikeKeyword:
                case SyntaxKind.LikeCsKeyword:
                case SyntaxKind.NotLikeCsKeyword:
                case SyntaxKind.ContainsKeyword:
                case SyntaxKind.NotContainsKeyword:
                case SyntaxKind.NotBangContainsKeyword:
                case SyntaxKind.ContainsCsKeyword:
                case SyntaxKind.Contains_CsKeyword:
                case SyntaxKind.NotContainsCsKeyword:
                case SyntaxKind.NotBangContainsCsKeyword:
                case SyntaxKind.StartsWithKeyword:
                case SyntaxKind.NotStartsWithKeyword:
                case SyntaxKind.StartsWithCsKeyword:
                case SyntaxKind.NotStartsWithCsKeyword:
                case SyntaxKind.EndsWithKeyword:
                case SyntaxKind.NotEndsWithKeyword:
                case SyntaxKind.EndsWithCsKeyword:
                case SyntaxKind.NotEndsWithCsKeyword:
                case SyntaxKind.MatchesRegexKeyword:
                case SyntaxKind.InKeyword:
                case SyntaxKind.InCsKeyword:
                case SyntaxKind.NotInKeyword:
                case SyntaxKind.NotInCsKeyword:
                case SyntaxKind.HasAnyKeyword:
                case SyntaxKind.HasAllKeyword:
                case SyntaxKind.BetweenKeyword:
                case SyntaxKind.NotBetweenKeyword:

                // these could be prefix unary starting an expression after a keyword starting a clause
                //case SyntaxKind.MinusToken:  
                //case SyntaxKind.PlusToken:

                // tokens that would only occur at after names in expressions
                case SyntaxKind.CloseParenToken:
                case SyntaxKind.CloseBracketToken:
                case SyntaxKind.CloseBraceToken:
                case SyntaxKind.DotToken:
                case SyntaxKind.CommaToken:

                // not really related to expressions but do indicate preceeding keyword was likely meant as a name
                case SyntaxKind.ColonToken:
                case SyntaxKind.BarToken:
                    return true;

                // this could be start of parenthesized expression after a keyword starting a clause
                case SyntaxKind.OpenParenToken:

                // this could be start of bracketted name after keyword
                case SyntaxKind.OpenBracketToken:
                    return false;
            }
        }

        return false;
    }

    private boolean isKeywordInNamePosition()
    {
        return isKeywordInNamePosition(_source, _pos);
    }

    private NameReference parseInvalidKeywordAsNameReference()
    {
        if (isKeywordInNamePosition())
        {
            var token = parseToken();
            return new NameReference(new TokenName(token), Arrays.asList(DiagnosticFacts.getNameRequiresBrackets(token.text())));
        }

        return null;
    }

    // endregion


    // region Literals

    private int scanStringOrCompoundStringLiteral(int offset)
    {
        var start = offset;
        while (peekToken(offset).kind() == SyntaxKind.StringLiteralToken)
        {
            offset++;
        }

        return offset > start ? offset - start : -1;
    }

    private int scanStringOrCompoundStringLiteral() // PORT: §3.12 optional parameter offset = 0
    {
        return scanStringOrCompoundStringLiteral(0);
    }

    private LiteralExpression parseStringLiteral()
    {
        if (peekToken().kind() == SyntaxKind.StringLiteralToken)
        {
            return new LiteralExpression(SyntaxKind.StringLiteralExpression, parseToken());
        }

        return null;
    }

    private Expression parseStringOrCompoundStringLiteral()
    {
        if (peekToken().kind() == SyntaxKind.StringLiteralToken)
        {
            if (peekToken(1).kind() != SyntaxKind.StringLiteralToken)
            {
                return new LiteralExpression(SyntaxKind.StringLiteralExpression, parseToken());
            }

            var tokens = new ArrayList<SyntaxToken>();
            while (peekToken().kind() == SyntaxKind.StringLiteralToken)
            {
                tokens.add(parseToken());
            }

            return new CompoundStringLiteralExpression(new SyntaxList1<SyntaxToken>(tokens));
        }

        return null;
    }

    private Expression parseLiteral()
    {
        switch (peekToken().kind())
        {
            case StringLiteralToken:
                return parseStringOrCompoundStringLiteral();
            case BooleanLiteralToken:
                return new LiteralExpression(SyntaxKind.BooleanLiteralExpression, parseToken());
            case LongLiteralToken:
                return new LiteralExpression(SyntaxKind.LongLiteralExpression, parseToken());
            case RealLiteralToken:
                return new LiteralExpression(SyntaxKind.RealLiteralExpression, parseToken());
            case DecimalLiteralToken:
                return new LiteralExpression(SyntaxKind.DecimalLiteralExpression, parseToken());
            case IntLiteralToken:
                return new LiteralExpression(SyntaxKind.IntLiteralExpression, parseToken());
            case GuidLiteralToken:
                return new LiteralExpression(SyntaxKind.GuidLiteralExpression, parseToken());
            case RawGuidLiteralToken:
                return new LiteralExpression(SyntaxKind.GuidLiteralExpression, parseToken(), Arrays.asList(DiagnosticFacts.getRawGuidLiteralNotAllowed()));
            case DateTimeLiteralToken:
                return new LiteralExpression(SyntaxKind.DateTimeLiteralExpression, parseToken());
            case TimespanLiteralToken:
                return new LiteralExpression(SyntaxKind.TimespanLiteralExpression, parseToken());
            case DynamicKeyword:
                return parseDynamicLiteral();
            case PlusToken:
            case MinusToken:
                return parseSignedNumericLiteral();
            case TypeOfKeyword:
                return parseTypeOfLiteral();
            case OpenBraceToken:
                return parseBracedNameReference();
            default:
                break;
        }

        return null;
    }

    private static final Function<QueryParser, Expression> FnParseExpression =
        qp -> qp.parseExpression();

    private static final Function<QueryParser, Expression> FnParseLiteral =
        qp -> qp.parseLiteral();

    private int scanSignedNumericLiteral(int offset)
    {
        var sign = peekToken(offset);
        var number = peekToken(offset + 1);
        if ((sign.kind() == SyntaxKind.PlusToken || sign.kind() == SyntaxKind.MinusToken)
            && number.trivia().length() == 0
            && (number.kind() == SyntaxKind.LongLiteralToken
            || number.kind() == SyntaxKind.RealLiteralToken
            || number.kind() == SyntaxKind.TimespanLiteralToken)
            && number.text().length() > 0 && Character.isDigit(number.text().charAt(0))) // PORT: §5.1 char.IsDigit
        {
            return 2;
        }
        else
        {
            return -1;
        }
    }

    private int scanSignedNumericLiteral() // PORT: §3.12 optional parameter offset = 0
    {
        return scanSignedNumericLiteral(0);
    }

    private Expression parseSignedNumericLiteral()
    {
        var len = scanSignedNumericLiteral();
        if (len == 2)
        {
            var sign = parseToken();
            var number = parseToken();
            var signedNumberToken = SyntaxToken.literal(sign.trivia(), sign.text() + number.text(), number.kind());

            switch (number.kind())
            {
                case LongLiteralToken:
                    return new LiteralExpression(SyntaxKind.LongLiteralExpression, signedNumberToken);
                case RealLiteralToken:
                    return new LiteralExpression(SyntaxKind.RealLiteralExpression, signedNumberToken);
                case TimespanLiteralToken:
                    return new LiteralExpression(SyntaxKind.TimespanLiteralExpression, signedNumberToken);
                default:
                    break;
            }
        }

        return null;
    }

// #if false
//     private Expression ParseNumericLiteral()
//     {
//         var token = PeekToken();
//         switch (token.Kind)
//         {
//             case SyntaxKind.LongLiteralToken:
//                 return new LiteralExpression(SyntaxKind.LongLiteralExpression, ParseToken());
//             case SyntaxKind.RealLiteralToken:
//                 return new LiteralExpression(SyntaxKind.RealLiteralExpression, ParseToken());
//             case SyntaxKind.DecimalLiteralToken:
//                 return new LiteralExpression(SyntaxKind.DecimalLiteralExpression, ParseToken());
//             case SyntaxKind.IntLiteralToken:
//                 return new LiteralExpression(SyntaxKind.IntLiteralExpression, ParseToken());
//             case SyntaxKind.DateTimeLiteralToken:
//                 return new LiteralExpression(SyntaxKind.DateTimeLiteralExpression, ParseToken());
//             case SyntaxKind.TimespanLiteralToken:
//                 return new LiteralExpression(SyntaxKind.TimespanLiteralExpression, ParseToken());
//             case SyntaxKind.PlusToken:
//             case SyntaxKind.MinusToken:
//                 return ParseSignedNumericLiteral();
//         }
//
//         return null;
//     }
// #endif
    private int scanForcedRealLiteral(int offset)
    {
        var number = peekToken(offset);
        if ((number.kind() == SyntaxKind.LongLiteralToken
            || number.kind() == SyntaxKind.RealLiteralToken)
            && number.text().length() > 0 && Character.isDigit(number.text().charAt(0))) // PORT: §5.1 char.IsDigit
        {
            return 1;
        }
        else
        {
            return -1;
        }
    }

    private int scanForcedRealLiteral() // PORT: §3.12 optional parameter offset = 0
    {
        return scanForcedRealLiteral(0);
    }

    private Expression parseForcedRealLiteral()
    {
        var token = peekToken();
        switch (token.kind())
        {
            case LongLiteralToken:
            case RealLiteralToken:
                if (token.text().length() > 0 && Character.isDigit(token.text().charAt(0))) // PORT: §5.1 char.IsDigit
                    return new LiteralExpression(SyntaxKind.RealLiteralExpression, parseToken());
                break;
            case PlusToken:
            case MinusToken:
                return parseSignedForcedRealLiteral();
            case OpenBraceToken:
                return parseBracedNameReference();
            default:
                break;
        }

        return null;
    }

    private Expression parseSignedForcedRealLiteral()
    {
        var len = scanSignedNumericLiteral();
        if (len == 2)
        {
            var sign = parseToken();
            var number = parseToken();
            var signedNumberToken = SyntaxToken.literal(sign.trivia(), sign.text() + number.text(), number.kind());

            switch (number.kind())
            {
                case LongLiteralToken:
                case RealLiteralToken:
                    return new LiteralExpression(SyntaxKind.RealLiteralExpression, signedNumberToken);
                default:
                    break;
            }
        }

        return null;
    }

    private Expression parseDynamicLiteral()
    {
        if (peekToken().kind() == SyntaxKind.DynamicKeyword
            && peekToken(1).kind() == SyntaxKind.OpenParenToken)
        {
            var keyword = parseToken();
            var open = parseToken();
            var value = parseJsonValue();
            if (value == null)
                value = CreateMissingJsonValue.get(); // PORT: §3.14 ??
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new DynamicExpression(keyword, open, value, close);
        }

        return null;
    }

    private Expression parseJsonValue()
    {
        return stackSafeParse(
            q -> q.parseJsonValue_Unsafe(),
            g -> g.jsonValue()); // PORT: §2.3 property QueryGrammar.JsonValue
    }

    private Expression parseJsonValue_Unsafe()
    {
        switch (peekToken().kind())
        {
            case LongLiteralToken:
            case RealLiteralToken:
            case MinusToken:
                return parseJsonNumber();
            case BooleanLiteralToken:
            case DateTimeLiteralToken:
            case TimespanLiteralToken:
            case GuidLiteralToken:
            case RawGuidLiteralToken:
            case DecimalLiteralToken:
            case StringLiteralToken:
            case DynamicKeyword:
                return parseLiteral();
            case NullKeyword:
                return parseNullLiteral();
            case OpenBracketToken:
                return parseJsonArray();
            case OpenBraceToken:
                if (scanBracedName() > 0)
                {
                    return parseBracedNameReference();
                }
                return parseJsonObject();
            default:
                break;
        }

        return null;
    }

    private Expression parseJsonNumber()
    {
        switch (peekToken().kind())
        {
            case LongLiteralToken:
            case RealLiteralToken:
                return parseLiteral();
            case MinusToken:
            {
                var numberToken = peekToken(1);
                if (numberToken.kind() == SyntaxKind.LongLiteralToken
                    || numberToken.kind() == SyntaxKind.RealLiteralToken)
                {
                    var op = parseToken();
                    var literal = parseLiteral();
                    return new PrefixUnaryExpression(SyntaxKind.UnaryMinusExpression, op, literal);
                }
                break;
            }
            default:
                break;
        }

        return null;
    }

    private static final Function<QueryParser, Expression> FnParseJsonValue =
        qp -> qp.parseJsonValue();

    private Expression parseJsonArray()
    {
        if (peekToken().kind() == SyntaxKind.OpenBracketToken)
        {
            var open = parseToken();
            var list = parseCommaList(FnParseJsonValue, CreateMissingJsonValue, FnScanCommonListEnd);
            var close = parseRequiredToken(SyntaxKind.CloseBracketToken);
            return new JsonArrayExpression(open, list, close);
        }

        return null;
    }

    private static final Function<QueryParser, JsonPair> FnParseJsonPair =
        qp -> qp.parseJsonPair();

    private Expression parseJsonObject()
    {
        if (peekToken().kind() == SyntaxKind.OpenBraceToken)
        {
            var open = parseToken();
            var list = parseCommaList(FnParseJsonPair, CreateMissingJsonPair, FnScanCommonListEnd);
            var close = parseRequiredToken(SyntaxKind.CloseBraceToken);
            return new JsonObjectExpression(open, list, close);
        }

        return null;
    }

    private JsonPair parseJsonPair()
    {
        // PORT: §3.15 C# switch-section locals (name, colon, value) are shared across sections; hoisted
        SyntaxToken name;
        SyntaxToken colon;
        Expression value;
        switch (peekToken().kind())
        {
            case StringLiteralToken:
                name = parseToken();
                colon = parseRequiredToken(SyntaxKind.ColonToken);
                value = parseJsonValue();
                if (value == null)
                    value = CreateMissingJsonValue.get(); // PORT: §3.14 ??
                return new JsonPair(name, colon, value);
            case ColonToken:
                name = SyntaxToken.missing(SyntaxKind.StringLiteralToken, DiagnosticFacts.getMissingString());
                colon = parseToken();
                value = parseJsonValue();
                if (value == null)
                    value = CreateMissingJsonValue.get(); // PORT: §3.14 ??
                return new JsonPair(name, colon, value);
            default:
                value = parseJsonValue();
                if (value != null)
                {
                    name = SyntaxToken.missing(SyntaxKind.StringLiteralToken, DiagnosticFacts.getMissingString());
                    colon = SyntaxParsers.createMissingToken(SyntaxKind.ColonToken);
                    return new JsonPair(name, colon, value);
                }
                else
                {
                    return null;
                }
        }
    }

    private Expression parseNullLiteral()
    {
        var nullToken = parseToken(SyntaxKind.NullKeyword);
        return nullToken != null ? new LiteralExpression(SyntaxKind.NullLiteralExpression, nullToken) : null;
    }

    private boolean scanTypeOfScalar(int offset)
    {
        return peekToken(offset).kind() == SyntaxKind.TypeOfKeyword
            && peekToken(offset + 1).kind() == SyntaxKind.OpenParenToken
            && (scanParamTypeExtended(offset + 2)
                 || (peekToken(offset + 2).kind() == SyntaxKind.IdentifierToken
                     && peekToken(offset + 3).kind() == SyntaxKind.CloseParenToken));
    }

    private boolean scanTypeOfScalar() // PORT: §3.12 optional parameter offset = 0
    {
        return scanTypeOfScalar(0);
    }

    private boolean scanTypeOfLiteral(int offset)
    {
        return scanTypeOfScalar(offset)
            || (peekToken(offset).kind() == SyntaxKind.TypeOfKeyword && peekToken(offset + 1).kind() == SyntaxKind.OpenParenToken);
    }

    private boolean scanTypeOfLiteral() // PORT: §3.12 optional parameter offset = 0
    {
        return scanTypeOfLiteral(0);
    }

    private Expression parseTypeOfLiteral()
    {
        if (peekToken().kind() == SyntaxKind.TypeOfKeyword)
        {
            if (scanTypeOfScalar())
            {
                var keyword = parseToken();
                var open = parseRequiredToken(SyntaxKind.OpenParenToken);
                TypeExpression type = parseParamTypeExtended(); // PORT: §3.14 ?? chain
                if (type == null)
                    type = parseIdentifierTypeExpression();
                if (type == null)
                    type = CreateMissingType.get();
                var close = parseRequiredToken(SyntaxKind.CloseParenToken);
                return new TypeOfLiteralExpression(keyword, open, new SyntaxList1<SeparatedElement1<Expression>>(new SeparatedElement1<Expression>(type)), close);
            }
            else if (peekToken(1).kind() == SyntaxKind.OpenParenToken)
            {
                var keyword = parseToken();
                var open = parseToken();
                var list = parseCommaList(FnParseTypeOfElement, CreateMissingTypeExpression, FnScanCommonListEnd);
                var close = parseRequiredToken(SyntaxKind.CloseParenToken);
                return new TypeOfLiteralExpression(keyword, open, list, close);
            }
        }

        return null;
    }

    private Expression parseStarExpression()
    {
        var token = parseToken(SyntaxKind.AsteriskToken);
        if (token != null)
        {
            return new StarExpression(token);
        }

        return null;
    }

    private Expression parseTypeOfElement()
    {
        var expr = parseStarExpression(); // PORT: §3.14 ??
        return expr != null ? expr : parseNameAndTypeDeclaration();
    }

    private static final Function<QueryParser, Expression> FnParseTypeOfElement =
        qp -> qp.parseTypeOfElement();

    // endregion

    // region Schemas
    private boolean scanParamType(int offset)
    {
        switch (peekToken(offset).kind())
        {
            case BoolKeyword:
            case BooleanKeyword:
            case DateKeyword:
            case DateTimeKeyword:
            case DecimalKeyword:
            case DoubleKeyword:
            case DynamicKeyword:
            case GuidKeyword:
            case IntKeyword:
            case Int64Keyword:
            case Int8Keyword:
            case LongKeyword:
            case RealKeyword:
            case StringKeyword:
            case TimeKeyword:
            case TimespanKeyword:
            case UniqueIdKeyword:
                return true;
            default:
                return false;
        }
    }

    private boolean scanParamType() // PORT: §3.12 optional parameter offset = 0
    {
        return scanParamType(0);
    }

    private boolean scanParamTypeExtended(int offset)
    {
        if (scanParamType(offset))
            return true;

        switch (peekToken(offset).kind())
        {
            case FloatKeyword:
            case Int16Keyword:
            case Int32Keyword:
            case UIntKeyword:
            case UInt16Keyword:
            case UInt32Keyword:
            case UInt64Keyword:
            case UInt8Keyword:
            case ULongKeyword:
                return true;
            default:
                return false;
        }
    }

    private boolean scanParamTypeExtended() // PORT: §3.12 optional parameter offset = 0
    {
        return scanParamTypeExtended(0);
    }

    private TypeExpression parseParamType()
    {
        if (scanParamType())
        {
            return new PrimitiveTypeExpression(parseToken());
        }

        return null;
    }

    private TypeExpression parseParamTypeExtended()
    {
        if (scanParamTypeExtended())
        {
            return new PrimitiveTypeExpression(parseToken());
        }

        return null;
    }

    private TypeExpression parseIdentifierTypeExpression()
    {
        var kind = peekToken().kind();
        if (kind == SyntaxKind.IdentifierToken
            || (SyntaxFacts.isKeyword(kind) && SyntaxFacts.canBeIdentifier(kind)))
        {
            return new PrimitiveTypeExpression(parseToken());
        }

        return null;
    }

    private TypeExpression parseInvalidParamType()
    {
        var token = peekToken(); // PORT: §3.15 `is LexicalToken token` / `is SyntaxKind kind` always match (non-nullable enum)
        var kind = token.kind();
        if (scanParamTypeExtended()
                || kind == SyntaxKind.IdentifierToken
                || (SyntaxFacts.isKeyword(kind) && SyntaxFacts.canBeIdentifier(kind)))
        {
            return new PrimitiveTypeExpression(parseToken(), Arrays.asList(DiagnosticFacts.getInvalidTypeName(token.text())));
        }

        return null;
    }

    private NameAndTypeDeclaration parseNameAndTypeDeclaration()
    {
        var name = parseExtendedNameDeclaration();
        if (name != null)
        {
            var colon = parseRequiredToken(SyntaxKind.ColonToken);

            TypeExpression type = peekToken().kind() == SyntaxKind.OpenParenToken
                ? parseSchemaType()
                : parseParamTypeOrInvalidOrMissing();

            return new NameAndTypeDeclaration(name, colon, type);
        }
        else if (peekToken().kind() == SyntaxKind.ColonToken)
        {
            // name is missing
            var colon = parseToken();
            var type = parseParamTypeOrInvalidOrMissing();
            return new NameAndTypeDeclaration(createMissingNameDeclaration(), colon, type);
        }

        return null;
    }

    // PORT: §3.14 `ParseParamType() ?? ParseInvalidParamType() ?? CreateMissingType()` (repeated at 4 sites)
    private TypeExpression parseParamTypeOrInvalidOrMissing()
    {
        var type = parseParamType();
        if (type == null)
            type = parseInvalidParamType();
        if (type == null)
            type = CreateMissingType.get();
        return type;
    }

    private Expression parseNameAndOptionalTypeDeclaration()
    {
        if (peekToken().kind() == SyntaxKind.ColonToken)
        {
            var colon = parseToken();
            var type = parseParamTypeOrInvalidOrMissing();
            return new NameAndTypeDeclaration(createMissingNameDeclaration(), colon, type);
        }

        var name = parseExtendedNameDeclaration();
        if (name != null && peekToken().kind() == SyntaxKind.ColonToken)
        {
            var colon = parseToken();
            var type = parseParamTypeOrInvalidOrMissing();
            return new NameAndTypeDeclaration(name, colon, type);
        }

        return name;
    }

    private boolean scanSchemaTypeStart(int offset)
    {
        var len = scanName(offset);
        return len > 0
            && peekToken(offset + len + 1).kind() == SyntaxKind.ColonToken
            && peekToken(offset + len + 2).kind() == SyntaxKind.OpenParenToken;
    }

    private boolean scanSchemaTypeStart() // PORT: §3.12 optional parameter offset = 0
    {
        return scanSchemaTypeStart(0);
    }

    /// <summary>
    /// Parses a schema delaration:  (name: type, name: type, ...)
    /// </summary>
    private SchemaTypeExpression parseSchemaType()
    {
        if (peekToken().kind() == SyntaxKind.OpenParenToken)
        {
            if (peekToken(1).kind() == SyntaxKind.AsteriskToken)
            {
                var open = parseToken();
                var asterisk = new StarExpression(parseToken());
                var close = parseRequiredToken(SyntaxKind.CloseParenToken);
                return new SchemaTypeExpression(
                    open,
                    new SyntaxList1<SeparatedElement1<Expression>>(new SeparatedElement1<Expression>(asterisk)),
                    close);
            }
            else
            {
                return parseSchemaMultipartType();
            }
        }

        return null;
    }

    private static final Function<QueryParser, NameAndTypeDeclaration> FnParseNameAndTypeDeclaration =
        qp -> qp.parseNameAndTypeDeclaration();

    private static final Function<QueryParser, Expression> FnParseNameAndTypeDeclarationExpression =
        qp -> (Expression)qp.parseNameAndTypeDeclaration();

    /// <summary>
    /// Parses a multi-part schema declaration:  (name: type, name: type, ...)
    /// </summary>
    private SchemaTypeExpression parseSchemaMultipartType()
    {
        if (peekToken().kind() == SyntaxKind.OpenParenToken)
        {
            var open = parseToken();
            var list = parseCommaList(FnParseNameAndTypeDeclarationExpression, CreateMissingNameAndTypeDeclarationExpression, FnScanCommonListEnd, false, true);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new SchemaTypeExpression(open, list, close);
        }

        return null;
    }

    /// <summary>
    /// Parses a tabular row schema
    /// </summary>
    private RowSchema parseRowSchema(boolean oneOrMore)
    {
        if (peekToken().kind() == SyntaxKind.OpenParenToken)
        {
            var open = parseToken();
            var leadingComma = parseToken(SyntaxKind.CommaToken);
            var list = parseCommaList(FnParseNameAndTypeDeclaration, CreateMissingNameAndTypeDeclaration, FnScanCommonListEnd, oneOrMore, true);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new RowSchema(open, leadingComma, list, close);
        }

        return null;
    }

    private RowSchema parseRowSchema() // PORT: §3.12 optional parameter oneOrMore = false
    {
        return parseRowSchema(false);
    }

    private EvaluateRowSchema parseEvaluateRowSchema()
    {
        if (peekToken().kind() == SyntaxKind.OpenParenToken)
        {
            var open = parseToken();
            var leadingComma = parseToken(SyntaxKind.CommaToken);
            var asteriskToken = parseToken(SyntaxKind.AsteriskToken);
            var asteriskTokenComma = parseToken(SyntaxKind.CommaToken);
            var list = parseCommaList(FnParseNameAndTypeDeclaration, CreateMissingNameAndTypeDeclaration, FnScanCommonListEnd, false, true);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new EvaluateRowSchema(open, leadingComma, asteriskToken, asteriskTokenComma, list, close);
        }

        return null;
    }

    // endregion


    // region Non-Query Expressions

    private Expression parsePrimaryExpression()
    {
        switch (peekToken().kind())
        {
            case LongLiteralToken:
            case RealLiteralToken:
            case BooleanLiteralToken:
            case IntLiteralToken:
            case DecimalLiteralToken:
            case TimespanLiteralToken:
            case DateTimeLiteralToken:
            case GuidLiteralToken:
            case RawGuidLiteralToken:
            case StringLiteralToken:
            case DynamicKeyword:
                return parseLiteral();
            case DataTableKeyword:
                return parseDataTableExpression();
            case ContextualDataTableKeyword:
                return parseContextualDataTableExpression();
            case ExternalDataKeyword:
            case External_DataKeyword:
                return parseExternalDataExpression();

            case MaterializedViewCombineKeyword:
                return parseMaterializedViewCombineExpression();
            case OpenParenToken:
                return parseParenthesizedExpression();
            case ToScalarKeyword:
                return parseToScalarExpression();
            default:
            {
                if (scanTypeOfLiteral()) // typeof can be an identifier so need to scan further than just the typeof keyword
                    return parseTypeOfLiteral();
                if (scanFunctionCallStart())
                    return parseFunctionCallExpression();
                var selector = parsePrimaryPathSelector(); // PORT: §3.14 ??
                return selector != null ? selector : parseInvalidKeywordAsNameReference();
            }
        }
    }

    private static final Map<String, QueryOperatorParameter> s_dataTableParameters =
        createQueryOperatorParameterMap(QueryOperatorParameters.DataTableParameters);

    private static final Map<String, QueryOperatorParameter> s_inlineExternalTableParameters =
        createQueryOperatorParameterMap(QueryOperatorParameters.InlineExternalTableProperties);

    private DataTableExpression parseDataTableExpression()
    {
        var keyword = parseToken(SyntaxKind.DataTableKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_dataTableParameters);
            var schema = parseRowSchema();
            if (schema == null)
                schema = CreateMissingRowSchema.get(); // PORT: §3.14 ??
            var open = parseRequiredToken(SyntaxKind.OpenBracketToken);
            var leadingComma = parseToken(SyntaxKind.CommaToken);
            var values = parseCommaList(FnParseLiteral, CreateMissingValue, FnScanCommonListEnd, false, true);
            var close = parseRequiredToken(SyntaxKind.CloseBracketToken);
            return new DataTableExpression(keyword, parameters, schema, open, leadingComma, values, close);
        }

        return null;
    }

    private ContextualDataTableExpression parseContextualDataTableExpression()
    {
        var keyword = parseToken(SyntaxKind.ContextualDataTableKeyword);
        if (keyword != null)
        {
            var id = parseLiteral(); // PORT: §3.14 ?? chain
            if (id == null)
                id = parseUnnamedExpression();
            if (id == null)
                id = createMissingExpression();
            var schema = parseRowSchema();
            if (schema == null)
                schema = CreateMissingRowSchema.get();
            return new ContextualDataTableExpression(keyword, id, schema);
        }

        return null;
    }

    private ExternalDataExpression parseExternalDataExpression()
    {
        switch (peekToken().kind())
        {
            case ExternalDataKeyword:
            case External_DataKeyword:
            {
                var keyword = parseToken();
                var parameters = parseQueryOperatorParameterList(s_dataTableParameters);
                var schema = parseRowSchema();
                if (schema == null)
                    schema = CreateMissingRowSchema.get(); // PORT: §3.14 ??
                var open = parseRequiredToken(SyntaxKind.OpenBracketToken);
                var values = parseCommaList(FnParseExpression, CreateMissingValue, FnScanCommonListEnd, false, true);
                var close = parseRequiredToken(SyntaxKind.CloseBracketToken);
                var clause = parseExternalDataWithClause();
                return new ExternalDataExpression(keyword, parameters, schema, open, values, close, clause);
            }

            default:
                return null;
        }
    }

    private Expression parseExternalDataPropertyValue()
    {
        switch (peekToken().kind())
        {
            case StringLiteralToken:
            case LongLiteralToken:
            case RealLiteralToken:
            case BooleanLiteralToken:
            case DateTimeLiteralToken:
            case TypeOfLiteralExpression: // PORT-BUG: a node kind in a token-kind switch; never matches a token. Mirrored.
                return parseLiteral();
            case RawGuidLiteralToken:
            case GuidLiteralToken:
                return new LiteralExpression(SyntaxKind.GuidLiteralExpression, parseToken());
            default:
                return parseRenameNameDeclaration();
        }
    }

    private NamedParameter parseExternalDataProperty()
    {
        var name = parseRenameNameDeclaration();
        if (name != null)
        {
            var equal = parseRequiredToken(SyntaxKind.EqualToken);
            var value = parseExternalDataPropertyValue();
            if (value == null)
                value = CreateMissingValue.get(); // PORT: §3.14 ??
            return new NamedParameter(name, equal, value);
        }

        return null;
    }

    private static final Function<QueryParser, NamedParameter> FnParseExternalDataProperty =
        qp -> qp.parseExternalDataProperty();

    private ExternalDataWithClause parseExternalDataWithClause()
    {
        var keyword = parseToken(SyntaxKind.WithKeyword);
        if (keyword != null)
        {
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var properties = parseCommaList(FnParseExternalDataProperty, CreateMissingNamedParameter, FnScanCommonListEnd);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new ExternalDataWithClause(keyword, open, properties, close);
        }

        return null;
    }

    // Inline External Table handling

    private boolean scanExternalTableSchema(int offset)
    {
        if (peekToken(offset).kind() == SyntaxKind.OpenParenToken)
        {
            var index = offset + 1;
            while (true)
            {
                var token = peekToken(index);
                if (token == NoToken || token.kind() == SyntaxKind.ColonToken)
                {
                    return true;
                }
                else if (token.kind() == SyntaxKind.CloseParenToken)
                {
                    break;
                }
                index++;
            }
        }
        return false;
    }

    private boolean scanExternalTableSchema() // PORT: §3.12 optional parameter offset = 0
    {
        return scanExternalTableSchema(0);
    }

    private boolean scanInlineExternalTableKindClause(int offset)
    {
        return peekToken(offset).kind() == SyntaxKind.KindKeyword;
    }

    private boolean scanInlineExternalTableKindClause() // PORT: §3.12 optional parameter offset = 0
    {
        return scanInlineExternalTableKindClause(0);
    }

    private InlineExternalTableExpression parseInlineExternalTableExpression()
    {
        // Support usage of inline_external_table as identifier
        if (peekToken().kind() == SyntaxKind.InlineExternalTableKeyword
            && (scanExternalTableSchema(1) || scanInlineExternalTableKindClause(1)))
        {
            var keyword = parseToken();
            var parameters = parseQueryOperatorParameterList(s_inlineExternalTableParameters, AllowedNameKind.DeclaredOnly);
            RowSchema schema = null;
            if (scanExternalTableSchema())
            {
                schema = parseRowSchema(true);
            }

            var kind = parseInlineExternalTableKindClause();
            if (schema == null)
            {
                schema = isInlineExternalTableDeltaKind(kind)
                    ? CreateOmittedRowSchema.get()
                    : CreateMissingRowSchema.get();
            }
            var partitionClause = parseInlineExternalTablePartitionClause();
            var pathFormatClause = parseInlineExternalTablePathFormatClause();
            var dataFormat = parseInlineExternalDataFormatClause(kind);
            var connectionStrings = parseInlineExternalTableConnectionStringsClause();
            var withClause = parseExternalDataWithClause();
            return new InlineExternalTableExpression(keyword, parameters, schema, kind, partitionClause, pathFormatClause, dataFormat, connectionStrings, withClause);
        }
        return null;
    }

    private InlineExternalTablePartitionClause parseInlineExternalTablePartitionClause()
    {
        if (peekToken().kind() == SyntaxKind.PartitionKeyword)
        {
            var partition = parseRequiredToken(SyntaxKind.PartitionKeyword);
            var by = parseRequiredToken(SyntaxKind.ByKeyword);
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var optionalComma = parseToken(SyntaxKind.CommaToken);
            var partitionColumns = parseCommaList(FnParsePartitionColumnDeclaration, FnCreateMissingPartitionColumnDeclaration, FnScanCommonListEnd, false, true);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new InlineExternalTablePartitionClause(partition, by, open, optionalComma, partitionColumns, close);
        }
        return null;
    }

    private static final Supplier<PartitionColumnDeclaration> FnCreateMissingPartitionColumnDeclaration =
        () -> new PartitionColumnDeclaration(
                    new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                    SyntaxToken.missing(SyntaxKind.ColonToken),
                    new PrimitiveTypeExpression(SyntaxToken.missing(SyntaxKind.StringKeyword)),
                    SyntaxToken.missing(SyntaxKind.EqualToken),
                    null,
                    Arrays.asList(DiagnosticFacts.getMissingPartitionColumnDeclaration()));

    private static final Function<QueryParser, PartitionColumnDeclaration> FnParsePartitionColumnDeclaration =
        qp -> qp.parsePartitionColumnDeclaration();

    private PartitionColumnDeclaration parsePartitionColumnDeclaration()
    {
        var name = parseExtendedNameDeclaration();

        var colon = parseRequiredToken(SyntaxKind.ColonToken);

        var type = parsePartitionColumnType(); // PORT: §3.14 ??
        if (type == null)
            type = CreateMissingType.get();

        var equal = parseToken(SyntaxKind.EqualToken);
        var expression = equal != null ? parseUnnamedExpression() : null;
        return new PartitionColumnDeclaration(name, colon, type, equal, expression);
    }

    private TypeExpression parsePartitionColumnType()
    {
        switch (peekToken().kind())
        {
            case DateTimeKeyword:
            case LongKeyword:
            case StringKeyword:
                return new PrimitiveTypeExpression(parseToken());
            default:
                break;
        }
        return new PrimitiveTypeExpression(parseToken(), Arrays.asList(DiagnosticFacts.getWrongPartitionColumnType()));
    }

    private InlineExternalTablePathFormatClause parseInlineExternalTablePathFormatClause()
    {
        if (peekToken().kind() == SyntaxKind.PathFormatKeyword)
        {
            var pathFormat = parseRequiredToken(SyntaxKind.PathFormatKeyword);
            var equal = parseRequiredToken(SyntaxKind.EqualToken);
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var optionalSeparator = parseStringLiteral();
            var pathFormatElements = parseList(FnParseExternalTablePathFormatToken, FnCreateMissingExternalTablePathFormatToken, FnScanCommonListEnd, true);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new InlineExternalTablePathFormatClause(pathFormat, equal, open, optionalSeparator, pathFormatElements, close);
        }
        return null;
    }

    private static final Supplier<InlineExternalTablePathFormatPartitionColumnReference> FnCreateMissingExternalTablePathFormatToken =
        () -> new InlineExternalTablePathFormatPartitionColumnReference(
            CreateMissingValue.get(),
            (LiteralExpression)CreateMissingStringLiteral.get(),
            Arrays.asList(DiagnosticFacts.getMissingPathFormatTokens()));

    private static final Function<QueryParser, InlineExternalTablePathFormatPartitionColumnReference> FnParseExternalTablePathFormatToken =
        qp -> qp.parseExternalTablePathFormatToken();

    private InlineExternalTablePathFormatPartitionColumnReference parseExternalTablePathFormatToken()
    {
        Expression partitionColumnReference = peekToken().kind() == SyntaxKind.DateTimePatternKeyword
            ? new DateTimePattern(
                parseRequiredToken(SyntaxKind.DateTimePatternKeyword), // date_time_pattern token
                parseRequiredToken(SyntaxKind.OpenParenToken), // (
                parseStringLiteral(), // literal containing the date time pattern
                parseRequiredToken(SyntaxKind.CommaToken), // ,
                parseNameReference(), // partition column name
                parseRequiredToken(SyntaxKind.CloseParenToken)) // )
            : (Expression)parseNameReference();

        if (partitionColumnReference == null)
        {
            //TODO: Is there better way to make token as skipped, need this to avoid endless loop in ParseList
            parseToken();
        }
        return partitionColumnReference == null
            ? new InlineExternalTablePathFormatPartitionColumnReference(
                CreateMissingValue.get(),
                (LiteralExpression)CreateMissingStringLiteral.get(),
                Arrays.asList(DiagnosticFacts.getUnknownTokenInPathFormatDefinition()))
            : new InlineExternalTablePathFormatPartitionColumnReference(
                partitionColumnReference,
                parseStringLiteral());
    }


    private InlineExternalTableConnectionStringsClause parseInlineExternalTableConnectionStringsClause()
    {
        if (peekToken().kind() == SyntaxKind.OpenParenToken)
        {
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var values = parseCommaList(FnParseExpression, CreateMissingValue, FnScanCommonListEnd, false, true);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new InlineExternalTableConnectionStringsClause(open, values, close);
        }
        return new InlineExternalTableConnectionStringsClause(
            SyntaxToken.missing(SyntaxKind.OpenParenToken),
            SyntaxList1.<SeparatedElement1<Expression>>empty(),
            SyntaxToken.missing(SyntaxKind.CloseParenToken),
            Arrays.asList(DiagnosticFacts.getMissingConnectionStrings()));
    }

    private InlineExternalTableKindClause parseInlineExternalTableKindClause()
    {
        if (peekToken().kind() == SyntaxKind.KindKeyword)
        {
            var keyword = parseToken();
            var equal = parseRequiredToken(SyntaxKind.EqualToken);
            var value = parseRequiredTokenText(KustoFacts.InlineExternalTableKinds);
            return new InlineExternalTableKindClause(keyword, equal, value);
        }

        return new InlineExternalTableKindClause(
            SyntaxToken.missing(SyntaxKind.KindKeyword),
            SyntaxToken.missing(SyntaxKind.EqualToken),
            SyntaxToken.missing(SyntaxKind.StringLiteralToken),
            Arrays.asList(DiagnosticFacts.getMissingExternalTableKind()));
    }

    private static boolean isInlineExternalTableDeltaKind(InlineExternalTableKindClause kindClause)
    {
        // PORT: §3.14 ?. chain; string.Equals(…, OrdinalIgnoreCase)
        return kindClause != null
            && kindClause.value() != null
            && DotNetStrings.equalsOrdinalIgnoreCase(kindClause.value().valueText(), "delta");
    }

    private static InlineExternalTableDataFormatClause createMissingInlineExternalTableDataFormatClause()
    {
        return new InlineExternalTableDataFormatClause(
            SyntaxToken.missing(SyntaxKind.DataFormatKeyword),
            SyntaxToken.missing(SyntaxKind.EqualToken),
            SyntaxToken.missing(SyntaxKind.StringLiteralToken),
            Arrays.asList(DiagnosticFacts.getMissingDataFormat()));
    }

    private static InlineExternalTableDataFormatClause createOmittedInlineExternalTableDataFormatClause()
    {
        return new InlineExternalTableDataFormatClause(
            SyntaxToken.missing(SyntaxKind.DataFormatKeyword),
            SyntaxToken.missing(SyntaxKind.EqualToken),
            SyntaxToken.missing(SyntaxKind.StringLiteralToken));
    }

    private InlineExternalTableDataFormatClause parseInlineExternalDataFormatClause(InlineExternalTableKindClause kindClause)
    {
        if (peekToken().kind() == SyntaxKind.DataFormatKeyword)
        {
            var keyword = parseToken();
            var equal = parseRequiredToken(SyntaxKind.EqualToken);
            var value = parseRequiredToken(SyntaxKind.IdentifierToken);
            return new InlineExternalTableDataFormatClause(keyword, equal, value);
        }

        return isInlineExternalTableDeltaKind(kindClause)
            ? createOmittedInlineExternalTableDataFormatClause()
            : createMissingInlineExternalTableDataFormatClause();
    }


    // End of Inline External Table handling

    private MaterializedViewCombineNameClause parseRequiredMaterializedViewNameClause()
    {
        return new MaterializedViewCombineNameClause(
            parseRequiredToken(SyntaxKind.OpenParenToken),
            missingExpressionIfNull(parseExpression()),
            parseRequiredToken(SyntaxKind.CloseParenToken));
    }

    private MaterializedViewCombineClause parseRequiredMaterializedViewCombineClause(String keywordName)
    {
        var keyword = parseTokenText(keywordName);
        if (keyword != null)
        {
            if ("aggregations".equals(keywordName)) // PORT: §3.14 string ==
            {
                return new MaterializedViewCombineClause(
                    keyword,
                    parseRequiredToken(SyntaxKind.OpenParenToken),
                    missingExpressionIfNull(parseSummarizeOperator()),
                    parseRequiredToken(SyntaxKind.CloseParenToken));
            }
            else
            {
                return new MaterializedViewCombineClause(
                    keyword,
                    parseRequiredToken(SyntaxKind.OpenParenToken),
                    missingExpressionIfNull(parseExpression()),
                    parseRequiredToken(SyntaxKind.CloseParenToken));
            }
        }
        else
        {
            return createMissingMaterializedViewCombineClause(keywordName);
        }
    }

    private MaterializedViewCombineExpression parseMaterializedViewCombineExpression()
    {
        var keyword = parseToken(SyntaxKind.MaterializedViewCombineKeyword);
        if (keyword != null)
        {
            var nameClause = parseRequiredMaterializedViewNameClause();
            var baseClause = parseRequiredMaterializedViewCombineClause("base");
            var deltaClause = parseRequiredMaterializedViewCombineClause("delta");
            var aggregationsClause = parseRequiredMaterializedViewCombineClause("aggregations");
            return new MaterializedViewCombineExpression(keyword, nameClause, baseClause, deltaClause, aggregationsClause);
        }

        return null;
    }

    private Expression parsePrimaryPathSelector()
    {
        var selector = parsePathElementSelector();
        if (selector != null)
        {
            var dataScope = parseDataScopeClause();
            if (dataScope != null)
            {
                return new DataScopeExpression(selector, dataScope);
            }
        }

        return selector;
    }

    private DataScopeClause parseDataScopeClause()
    {
        if (peekToken().kind() == SyntaxKind.DataScopeKeyword)
        {
            var keyword = parseToken();
            var equal = parseRequiredToken(SyntaxKind.EqualToken);
            var value = parseRequiredTokenText(KustoFacts.DataScopeValues);
            return new DataScopeClause(keyword, equal, value);
        }

        return null;
    }

    private Expression parsePathElementSelector()
    {
        if (peekToken().kind() == SyntaxKind.OpenBracketToken)
        {
            return parseBracketedPathElementSelector();
        }
        else
        {
            return parseBarePathElementSelector();
        }
    }

    private Expression parsePathElementSelectorOrFunctionCall()
    {
        if (scanFunctionCallStart())
        {
            return parseFunctionCallExpression();
        }
        else
        {
            return parsePathElementSelector();
        }
    }

    private Expression parseBarePathElementSelector()
    {
        var token = peekToken();
        if (token.kind() == SyntaxKind.AtToken)
        {
            return new AtExpression(parseToken());
        }
        else if (isSpecialKeywordAsIdentifier(token))
        {
            return new NameReference(new TokenName(parseToken()));
        }
        else
        {
            return parseNameReference();
        }
    }

    private static final Function<QueryParser, Expression> FnParseBarePathElementSelector =
        qp -> qp.parseBarePathElementSelector();

    private static final Set<SyntaxKind> _specialKeywordsAsIdentifiers =
        new LinkedHashSet<SyntaxKind>(KustoFacts.SpecialKeywordsAsIdentifiers); // PORT: §3.17 ToHashSetEx

    private static boolean isSpecialKeywordAsIdentifier(LexicalToken token)
    {
        return SyntaxFacts.isKeyword(token.kind()) && _specialKeywordsAsIdentifiers.contains(token.kind());
    }

    private Expression parseRootBracketedPathElementSelector()
    {
        if (peekToken().kind() == SyntaxKind.OpenBracketToken)
        {
            var selector = parseBracketedWildcardedNameReference(); // PORT: §3.14 ??
            return selector != null ? selector : parseBracketedNameReference();
        }

        return null;
    }

    private Expression parseBracketedPathElementSelector()
    {
        if (peekToken().kind() == SyntaxKind.OpenBracketToken)
        {
            Expression selector = parseBracketedWildcardedNameReference(); // PORT: §3.14 ?? chain
            if (selector == null)
                selector = parseBracketedNameReference();
            if (selector == null)
                selector = parseBracketedExpression();
            return selector;
        }

        return null;
    }

    private Expression parseBracketedExpression()
    {
        if (peekToken().kind() == SyntaxKind.OpenBracketToken)
        {
            var open = parseToken();
            Expression expr = parseUnnamedExpression(); // PORT: §3.14 ??
            if (expr == null)
                expr = createMissingNameReference();
            var close = parseRequiredToken(SyntaxKind.CloseBracketToken);
            return new BracketedExpression(open, expr, close);
        }

        return null;
    }

    private Expression parseParenthesizedExpression()
    {
        if (peekToken().kind() == SyntaxKind.OpenParenToken)
        {
            var open = parseToken();
            var expr = missingExpressionIfNull(parseExpression());
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new ParenthesizedExpression(open, expr, close);
        }

        return null;
    }

    private int scanRenameName(int offset)
    {
        var tok = peekToken(offset);
        switch (tok.kind())
        {
            case IdentifierToken:
                return 1;
            case OpenBracketToken:
                return scanBracketedName(offset);
            case OpenBraceToken:
                if (scanIdentifierOrKeywordAsIdentifier(offset + 1)
                    && peekToken(offset + 2).kind() == SyntaxKind.CloseBraceToken)
                {
                    return 3;
                }
                return -1;
            default:
                return scanExtendedKeywordAsIdentifier(offset) ? 1 : -1;
        }
    }

    private int scanRenameName() // PORT: §3.12 optional parameter offset = 0
    {
        return scanRenameName(0);
    }

    private int scanRenameList(int offset)
    {
        if (peekToken(offset).kind() == SyntaxKind.OpenParenToken)
        {
            int start = offset;
            offset++;

            while (!scanCommonListEnd(offset))
            {
                var len = scanRenameName(offset);
                if (len > 0)
                {
                    offset += len;

                    if (peekToken(offset).kind() == SyntaxKind.CommaToken)
                    {
                        offset += 1;
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }
                else
                {
                    return -1;
                }
            }

            if (peekToken(offset).kind() == SyntaxKind.CloseParenToken)
            {
                offset++;
                return offset - start;
            }
        }

        return -1;
    }

    private int scanRenameList() // PORT: §3.12 optional parameter offset = 0
    {
        return scanRenameList(0);
    }

    private Name parseRenameName()
    {
        switch (peekToken().kind())
        {
            case OpenBracketToken:
                return parseBracketedName();
            case OpenBraceToken:
                return parseBracedName();
            default:
            {
                var name = parseIdentifierName(); // PORT: §3.14 ??
                return name != null ? name : parseExtendedKeyordAsIdentifierName();
            }
        }
    }

    private NameDeclaration parseRenameNameDeclaration()
    {
        var name = parseRenameName();
        return name != null ? new NameDeclaration(name) : null;
    }

    private static final Function<QueryParser, NameDeclaration> FnParseRenameNameDeclaration =
        qp -> qp.parseRenameNameDeclaration();

    @Internal
    public static int scanDashedName(Source<LexicalToken> source, int start)
    {
        int position = start;

        var token = source.peek(position);
        if (token != null
            && (token.kind() == SyntaxKind.IdentifierToken
                || SyntaxFacts.isKeyword(token.kind())))
        {
            position++;

            while (true)
            {
                token = source.peek(position);

                if (token == null
                    || token.trivia().length() > 0
                    || (token.kind() != SyntaxKind.IdentifierToken
                        && !SyntaxFacts.isKeyword(token.kind())
                        && token.kind() != SyntaxKind.MinusToken))
                {
                    break;
                }

                position++;
            }
        }

        return position - start;
    }

    private int scanDashedName()
    {
        return scanDashedName(_source, _pos);
    }

    private TokenName parseDashedName()
    {
        var len = scanDashedName();

        if (len > 0)
        {
            var token = parseToken(len); // PORT: §3.15 `is SyntaxToken token` null test
            if (token != null)
                return new TokenName(token);
        }

        return null;
    }

    private Expression parseNamedExpression()
    {
        // PORT: §3.15 `is int` pattern variables become locals; each later condition is evaluated only when the earlier ones failed
        int nameLen = scanRenameName();
        if (nameLen > 0
            && peekToken(nameLen).kind() == SyntaxKind.EqualToken)
        {
            var name = parseRenameNameDeclaration();
            var equal = parseToken(SyntaxKind.EqualToken);
            var expr = missingExpressionIfNull(parseUnnamedExpression());
            return new SimpleNamedExpression(name, equal, expr);
        }
        else
        {
            int dashNameLen = scanDashedName();
            if (dashNameLen > 0
                && peekToken(dashNameLen).kind() == SyntaxKind.EqualToken)
            {
                // special case of illegal name being used as named-expression name.
                var name = parseDashedName();
                var nameDecl = new NameDeclaration(name, Arrays.asList(DiagnosticFacts.getNameRequiresBrackets(name.name().text())));
                var equal = parseToken(SyntaxKind.EqualToken);
                var expr = missingExpressionIfNull(parseUnnamedExpression());
                return new SimpleNamedExpression(nameDecl, equal, expr);
            }
            else
            {
                int nameListLen = scanRenameList();
                if (nameListLen > 0
                    && peekToken(nameListLen).kind() == SyntaxKind.EqualToken)
                {
                    var open = parseToken();
                    var list = parseCommaList(FnParseRenameNameDeclaration, this::createMissingNameDeclaration, FnScanCommonListEnd, true);
                    var close = parseRequiredToken(SyntaxKind.CloseParenToken);
                    var equal = parseRequiredToken(SyntaxKind.EqualToken);
                    var expr = missingExpressionIfNull(parseUnnamedExpression());
                    return new CompoundNamedExpression(new RenameList(open, list, close), equal, expr);
                }
                else
                {
                    return parseUnnamedExpression();
                }
            }
        }
    }

    private static final Function<QueryParser, Expression> FnParseNamedExpression =
        qp -> qp.parseNamedExpression();

    private Expression parseArgument()
    {
        if (peekToken().kind() == SyntaxKind.AsteriskToken
            && (peekToken(1).kind() == SyntaxKind.CloseParenToken
                || peekToken(1).kind() == SyntaxKind.CommaToken))
        {
            return new StarExpression(parseToken());
        }
        else
        {
            return parseNamedExpression();
        }
    }

    private static final Function<QueryParser, Expression> FnParseArgument =
        qp -> qp.parseArgument();

    private ExpressionList parseArgumentList()
    {
        if (peekToken().kind() == SyntaxKind.OpenParenToken)
        {
            var open = parseToken();
            var args = parseCommaList(FnParseArgument, this::createMissingExpression, FnScanCommonListEnd);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new ExpressionList(open, args, close);
        }

        return null;
    }

    private static final List<String> s_functionsWithKeywordNames = createFunctionsWithKeywordNames();

    // PORT: §3.6 static initializer expression as a helper:
    // Functions.All.Select(f => f.Name).Concat(Aggregates.All.Select(f => f.Name))
    //     .Where(n => (SyntaxFacts.IsKeyword(n) && !SyntaxFacts.IsKeywordThatCanBeIdentifier(n)) || IsMultiTokenName(n)).ToList()
    private static List<String> createFunctionsWithKeywordNames()
    {
        var result = new ArrayList<String>();
        for (var f : Functions.All)
        {
            var n = f.name();
            if ((SyntaxFacts.isKeyword(n) && !SyntaxFacts.isKeywordThatCanBeIdentifier(n))
                || isMultiTokenName(n))
                result.add(n);
        }
        for (var f : Aggregates.All)
        {
            var n = f.name();
            if ((SyntaxFacts.isKeyword(n) && !SyntaxFacts.isKeywordThatCanBeIdentifier(n))
                || isMultiTokenName(n))
                result.add(n);
        }
        return result;
    }

    private int scanFunctionCallName(int offset)
    {
        var len = scanName(offset);
        if (len > 0)
            return len;

        for (int i = 0; i < s_functionsWithKeywordNames.size(); i++)
        {
            len = scanToken(s_functionsWithKeywordNames.get(i), offset);
            if (len > 0)
                return len;
        }

        return -1;
    }

    private int scanFunctionCallName() // PORT: §3.12 optional parameter offset = 0
    {
        return scanFunctionCallName(0);
    }

    private boolean scanFunctionCallStart(int offset)
    {
        var len = scanFunctionCallName(offset);
        if (len > 0 && peekToken(offset + len).kind() == SyntaxKind.OpenParenToken)
            return true;

        return false;
    }

    private boolean scanFunctionCallStart() // PORT: §3.12 optional parameter offset = 0
    {
        return scanFunctionCallStart(0);
    }

    private NameReference parseFunctionCallName()
    {
        var len = scanName();
        if (len > 0)
        {
            return parseNameReference();
        }

        // special case for known functions with names that are keywords that
        // cannot normally be used as identifiers
        for (int i = 0; i < s_functionsWithKeywordNames.size(); i++)
        {
            var token = parseTokenText(s_functionsWithKeywordNames.get(i));
            if (token != null)
            {
                return new NameReference(new TokenName(token));
            }
        }

        return null;
    }

    private FunctionCallExpression parseFunctionCallExpression()
    {
        if (scanFunctionCallStart())
        {
            var name = parseFunctionCallName();
            var arguments = parseArgumentList();
            return new FunctionCallExpression(name, arguments);
        }

        return null;
    }

    private FunctionCallExpression parseRequiredFunctionCallExpression()
    {
        if (scanFunctionCallStart())
        {
            var name = parseFunctionCallName();
            var arguments = parseArgumentList();
            return new FunctionCallExpression(name, arguments);
        }
        else if (scanFunctionCallName() > 0)
        {
            var name = parseFunctionCallName();
            var arguments = createMissingArgumentList();
            return new FunctionCallExpression(name, arguments);
        }
        else
        {
            return createMissingFunctionCallExpression();
        }
    }

    private Expression parseDotCompositeFunctionCall()
    {
        Expression expr = parseFunctionCallExpression();

        if (expr != null)
        {
            while (peekToken().kind() == SyntaxKind.DotToken && scanFunctionCallStart(1))
            {
                var dot = parseToken();
                var call = parseFunctionCallExpression();
                expr = new PathExpression(expr, dot, call);
            }
        }

        return expr;
    }

    private Expression parseToTableExpression()
    {
        var keyword = parseToken(SyntaxKind.ToTableKeyword);
        if (keyword != null)
        {
            var kind = parseQueryOperatorParameter(QueryOperatorParameters.ToTableKindParameter);
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var expr = missingExpressionIfNull(parseExpression());
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new ToTableExpression(keyword, kind, open, expr, close);
        }

        return null;
    }

    private Expression parseToScalarExpression()
    {
        var keyword = parseToken(SyntaxKind.ToScalarKeyword);
        if (keyword != null)
        {
            var kind = parseQueryOperatorParameter(QueryOperatorParameters.ToScalarKindParameter);
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var expr = missingExpressionIfNull(parseExpression());
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new ToScalarExpression(keyword, kind, open, expr, close);
        }

        return null;
    }

    private Expression parseFunctionCallOrPath()
    {
        if (peekToken().kind() == SyntaxKind.ToTableKeyword)
        {
            return parseToTableExpression();
        }

        var expr = parsePrimaryExpression();

        while (expr != null)
        {
            var kind = peekToken().kind();
            if (kind == SyntaxKind.DotToken)
            {
                var dot = parseToken();
                Expression selector = parsePathElementSelectorOrFunctionCall(); // PORT: §3.14 ??
                if (selector == null)
                    selector = createMissingNameReference();
                expr = new PathExpression(expr, dot, selector);
            }
            else if (kind == SyntaxKind.OpenBracketToken)
            {
                expr = new ElementExpression(expr, parseBracketedExpression());
            }
            else
            {
                break;
            }
        }

        return expr;
    }


    private Expression parseUnaryPlusOrMinusExpression()
    {
        switch (peekToken().kind())
        {
            case MinusToken:
                return new PrefixUnaryExpression(SyntaxKind.UnaryMinusExpression, parseToken(), missingExpressionIfNull(parseFunctionCallOrPath()));
            case PlusToken:
                return new PrefixUnaryExpression(SyntaxKind.UnaryPlusExpression, parseToken(), missingExpressionIfNull(parseFunctionCallOrPath()));
            default:
                return parseFunctionCallOrPath();
        }
    }

    private Expression parseInvocationExpression()
    {
        return parseUnaryPlusOrMinusExpression();
    }

    private Expression parseStringOperation()
    {
        if (peekToken().kind() == SyntaxKind.AsteriskToken)
        {
            // PORT: §3.15 `GetStringOperationKind(...) is SyntaxKind starOpKind && starOpKind != None`: non-nullable enum → plain assignment
            var starOpKind = getStringOperationKind(peekToken(1).kind());
            if (starOpKind != SyntaxKind.None)
            {
                return new BinaryExpression(starOpKind, new StarExpression(parseToken()), parseToken(), missingExpressionIfNull(parseUnaryPlusOrMinusExpression()));
            }
        }

        var expr = parseUnaryPlusOrMinusExpression();
        if (expr != null)
        {
            var opKind = getStringOperationKind(peekToken().kind()); // PORT: §3.15
            if (opKind != SyntaxKind.None)
            {
                expr = new BinaryExpression(opKind, expr, parseToken(), missingExpressionIfNull(parseUnaryPlusOrMinusExpression()));
            }
        }

        return expr;
    }

    private static SyntaxKind getStringOperationKind(SyntaxKind tokenKind)
    {
        switch (tokenKind)
        {
            case SyntaxKind.EqualTildeToken:
                return SyntaxKind.EqualTildeExpression;
            case SyntaxKind.BangTildeToken:
                return SyntaxKind.BangTildeExpression;
            case SyntaxKind.HasKeyword:
                return SyntaxKind.HasExpression;
            case SyntaxKind.ColonToken:
                return SyntaxKind.SearchExpression;
            case SyntaxKind.NotHasKeyword:
                return SyntaxKind.NotHasExpression;
            case SyntaxKind.HasCsKeyword:
                return SyntaxKind.HasCsExpression;
            case SyntaxKind.NotHasCsKeyword:
                return SyntaxKind.NotHasCsExpression;
            case SyntaxKind.HasPrefixKeyword:
                return SyntaxKind.HasPrefixExpression;
            case SyntaxKind.NotHasPrefixKeyword:
                return SyntaxKind.NotHasPrefixExpression;
            case SyntaxKind.HasPrefixCsKeyword:
                return SyntaxKind.HasPrefixCsExpression;
            case SyntaxKind.NotHasPrefixCsKeyword:
                return SyntaxKind.NotHasPrefixCsExpression;
            case SyntaxKind.HasSuffixKeyword:
                return SyntaxKind.HasSuffixExpression;
            case SyntaxKind.NotHasSuffixKeyword:
                return SyntaxKind.NotHasSuffixExpression;
            case SyntaxKind.HasSuffixCsKeyword:
                return SyntaxKind.HasSuffixCsExpression;
            case SyntaxKind.NotHasSuffixCsKeyword:
                return SyntaxKind.NotHasSuffixCsExpression;
            case SyntaxKind.LikeKeyword:
                return SyntaxKind.LikeExpression;
            case SyntaxKind.NotLikeKeyword:
                return SyntaxKind.NotLikeExpression;
            case SyntaxKind.LikeCsKeyword:
                return SyntaxKind.LikeCsExpression;
            case SyntaxKind.NotLikeCsKeyword:
                return SyntaxKind.NotLikeCsExpression;
            case SyntaxKind.ContainsKeyword:
                return SyntaxKind.ContainsExpression;
            case SyntaxKind.NotContainsKeyword:
                return SyntaxKind.NotContainsExpression;
            case SyntaxKind.NotBangContainsKeyword:
                return SyntaxKind.NotContainsExpression;
            case SyntaxKind.ContainsCsKeyword:
                return SyntaxKind.ContainsCsExpression;
            case SyntaxKind.Contains_CsKeyword:
                return SyntaxKind.ContainsCsExpression;
            case SyntaxKind.NotContainsCsKeyword:
                return SyntaxKind.NotContainsCsExpression;
            case SyntaxKind.NotBangContainsCsKeyword:
                return SyntaxKind.NotContainsCsExpression;
            case SyntaxKind.StartsWithKeyword:
                return SyntaxKind.StartsWithExpression;
            case SyntaxKind.NotStartsWithKeyword:
                return SyntaxKind.NotStartsWithExpression;
            case SyntaxKind.StartsWithCsKeyword:
                return SyntaxKind.StartsWithCsExpression;
            case SyntaxKind.NotStartsWithCsKeyword:
                return SyntaxKind.NotStartsWithCsExpression;
            case SyntaxKind.EndsWithKeyword:
                return SyntaxKind.EndsWithExpression;
            case SyntaxKind.NotEndsWithKeyword:
                return SyntaxKind.NotEndsWithExpression;
            case SyntaxKind.EndsWithCsKeyword:
                return SyntaxKind.EndsWithCsExpression;
            case SyntaxKind.NotEndsWithCsKeyword:
                return SyntaxKind.NotEndsWithCsExpression;
            case SyntaxKind.MatchesRegexKeyword:
                return SyntaxKind.MatchesRegexExpression;
            default:
                return SyntaxKind.None;
        }
    }

    private Expression parseMultiplicativeExpression()
    {
        var expr = parseStringOperation();

        if (expr != null)
        {
            SyntaxKind opKind; // PORT: §3.15 non-nullable enum `is` → plain assignment
            while ((opKind = getMultiplicativeExpressionKind(peekToken().kind())) != SyntaxKind.None)
            {
                expr = new BinaryExpression(opKind, expr, parseToken(), missingExpressionIfNull(parseStringOperation()));
            }
        }

        return expr;
    }

    private static SyntaxKind getMultiplicativeExpressionKind(SyntaxKind tokenKind)
    {
        switch (tokenKind)
        {
            case AsteriskToken:
                return SyntaxKind.MultiplyExpression;
            case SlashToken:
                return SyntaxKind.DivideExpression;
            case PercentToken:
                return SyntaxKind.ModuloExpression;
            default:
                return SyntaxKind.None;
        }
    }

    private Expression parseAdditiveExpression()
    {
        var expr = parseMultiplicativeExpression();

        if (expr != null)
        {
            SyntaxKind opKind; // PORT: §3.15
            while ((opKind = getAdditiveExpressionKind(peekToken().kind())) != SyntaxKind.None)
            {
                expr = new BinaryExpression(opKind, expr, parseToken(), missingExpressionIfNull(parseMultiplicativeExpression()));
            }
        }

        return expr;
    }

    private static SyntaxKind getAdditiveExpressionKind(SyntaxKind tokenKind)
    {
        switch (tokenKind)
        {
            case PlusToken:
                return SyntaxKind.AddExpression;
            case MinusToken:
                return SyntaxKind.SubtractExpression;
            default:
                return SyntaxKind.None;
        }
    }

    private Expression parseRelationalExpresion()
    {
        var expr = parseAdditiveExpression();

        if (expr != null)
        {
            var opKind = getRelationalExpressionKind(peekToken().kind()); // PORT: §3.15
            if (opKind != SyntaxKind.None)
            {
                expr = new BinaryExpression(opKind, expr, parseToken(), missingExpressionIfNull(parseAdditiveExpression()));
            }
        }

        return expr;
    }

    private static SyntaxKind getRelationalExpressionKind(SyntaxKind tokenKind)
    {
        switch (tokenKind)
        {
            case LessThanToken:
                return SyntaxKind.LessThanExpression;
            case LessThanOrEqualToken:
                return SyntaxKind.LessThanOrEqualExpression;
            case GreaterThanToken:
                return SyntaxKind.GreaterThanExpression;
            case GreaterThanOrEqualToken:
                return SyntaxKind.GreaterThanOrEqualExpression;
            default:
                return SyntaxKind.None;
        }
    }

    private Expression parseEqualityExpression()
    {
        if (peekToken().kind() == SyntaxKind.AsteriskToken
            && peekToken(1).kind() == SyntaxKind.EqualEqualToken)
        {
            return new BinaryExpression(SyntaxKind.EqualExpression, new StarExpression(parseToken()), parseToken(), missingExpressionIfNull(parseRelationalExpresion()));
        }

        var expr = parseRelationalExpresion();

        if (expr != null)
        {
            switch (peekToken().kind())
            {
                case EqualEqualToken:
                    expr = new BinaryExpression(SyntaxKind.EqualExpression, expr, parseToken(), missingExpressionIfNull(parseRelationalExpresion()));
                    break;
                case BangEqualToken:
                {
                    var op = parseToken();
                    var right = parseRelationalExpresion(); // PORT: §3.14 ?? CreateMissingTokenLiteral()
                    expr = new BinaryExpression(SyntaxKind.NotEqualExpression, expr, op, right != null ? right : createMissingTokenLiteral());
                    break;
                }
                case LessThanGreaterThanToken:
                {
                    var op = parseToken();
                    var right = parseRelationalExpresion(); // PORT: §3.14 ?? CreateMissingTokenLiteral()
                    expr = new BinaryExpression(SyntaxKind.NotEqualExpression, expr, op, right != null ? right : createMissingTokenLiteral());
                    break;
                }
                case InKeyword:
                    if (peekToken(1).kind() != SyntaxKind.RangeKeyword)
                        expr = new InExpression(SyntaxKind.InExpression, expr, parseToken(), parseRequiredInOperatorExpressionList());
                    break;
                case InCsKeyword:
                    expr = new InExpression(SyntaxKind.InCsExpression, expr, parseToken(), parseRequiredInOperatorExpressionList());
                    break;
                case NotInKeyword:
                    expr = new InExpression(SyntaxKind.NotInExpression, expr, parseToken(), parseRequiredInOperatorExpressionList());
                    break;
                case NotInCsKeyword:
                    expr = new InExpression(SyntaxKind.NotInCsExpression, expr, parseToken(), parseRequiredInOperatorExpressionList());
                    break;
                case HasAnyKeyword:
                    expr = new HasAnyExpression(SyntaxKind.HasAnyExpression, expr, parseToken(), parseRequiredInOperatorExpressionList());
                    break;
                case HasAllKeyword:
                    expr = new HasAllExpression(SyntaxKind.HasAllExpression, expr, parseToken(), parseRequiredInOperatorExpressionList());
                    break;
                case BetweenKeyword:
                    expr = new BetweenExpression(SyntaxKind.BetweenExpression, expr, parseToken(), parseRequiredExpressionCouple());
                    break;
                case NotBetweenKeyword:
                    expr = new BetweenExpression(SyntaxKind.NotBetweenExpression, expr, parseToken(), parseRequiredExpressionCouple());
                    break;
                default:
                    break;
            }
        }

        return expr;
    }

    @Internal
    public boolean scanIsQueryExpression()
    {
        // if it starts with a query operator, then its a query expression.
        if (scanPossibleQueryOperator())
            return true;

        // if is there a pipe/bar before we get to end of the expression then it is a query expression.
        if (scanIsPipeBeforeEndOfExpression())
            return true;

        return false;
    }

    private boolean scanIsPipeBeforeEndOfExpression()
    {
        int offset = 0;
        int parenDepth = 0;

        while (true)
        {
            var token = peekToken(offset);
            switch (token.kind())
            {
                case None:
                case SemicolonToken:
                    // end of expression
                    return false;
                case CommaToken:
                    // comma ends expression 
                    if (parenDepth == 0)
                        return false;
                    break;
                case CloseParenToken:
                    // close paren can end expression
                    if (parenDepth == 0)
                        return false;
                    parenDepth--;
                    break;
                case OpenParenToken:
                    parenDepth++;
                    break;
                case BarToken:
                    // looks like pipe expression starts here
                    if (parenDepth == 0)
                        return true;
                    break;
                default:
                    break;
            }

            offset++;
        }
    }

    private ExpressionList parseRequiredInOperatorExpressionList()
    {
        var openParen = parseRequiredToken(SyntaxKind.OpenParenToken);
        SyntaxList1<SeparatedElement1<Expression>> exprList;

        if (scanIsQueryExpression())
        {
            // if query, then list is one query expression
            var query = missingExpressionIfNull(parseExpression());
            exprList = new SyntaxList1<SeparatedElement1<Expression>>(
                new SeparatedElement1<Expression>(query));
        }
        else
        {
            // expression list of unnamed expressions (not queries)
            exprList = parseCommaList(FnParseUnnamedExpression, this::createMissingExpression, FnScanCommonListEnd, true);
        }

        var closeParen = parseRequiredToken(SyntaxKind.CloseParenToken);

        return new ExpressionList(openParen, exprList, closeParen);
    }

    private ExpressionCouple parseRequiredExpressionCouple()
    {
        return new ExpressionCouple(
            parseRequiredToken(SyntaxKind.OpenParenToken),
            missingExpressionIfNull(parseUnnamedExpression()),
            parseRequiredToken(SyntaxKind.DotDotToken),
            missingExpressionIfNull(parseUnnamedExpression()),
            parseRequiredToken(SyntaxKind.CloseParenToken));
    }

    private Expression parseLogicalAndExpression()
    {
        var expr = parseEqualityExpression();

        if (expr != null)
        {
            while (peekToken().kind() == SyntaxKind.AndKeyword)
            {
                expr = new BinaryExpression(SyntaxKind.AndExpression, expr, parseToken(), missingExpressionIfNull(parseEqualityExpression()));
            }
        }

        return expr;
    }

    private Expression parseLogicalOrExpression()
    {
        var expr = parseLogicalAndExpression();

        if (expr != null)
        {
            while (peekToken().kind() == SyntaxKind.OrKeyword)
            {
                expr = new BinaryExpression(SyntaxKind.OrExpression, expr, parseToken(), missingExpressionIfNull(parseLogicalAndExpression()));
            }
        }

        return expr;
    }

    private Expression parseUnnamedExpression()
    {
        return stackSafeParse(
            q -> q.parseUnnamedExpression_Unsafe(),
            g -> g.unnamedExpression()); // PORT: §2.3 property QueryGrammar.UnnamedExpression
    }

    private Expression parseUnnamedExpression_Unsafe()
    {
        // shortcut for identifier/literal followed by punctuation that would end an expression
        switch (peekToken(1).kind())
        {
            case CommaToken:
            case CloseParenToken:
            case CloseBraceToken:
            case CloseBracketToken:
            case BarToken:
                switch (peekToken().kind())
                {
                    case IdentifierToken:
                    case BooleanLiteralToken:
                    case LongLiteralToken:
                    case RealLiteralToken:
                    case DecimalLiteralToken:
                    case DateTimeLiteralToken:
                    case TimespanLiteralToken:
                    case GuidLiteralToken:
                    case RawGuidLiteralToken:
                    case IntLiteralToken:
                        return parsePrimaryExpression();
                    default:
                        break;
                }
                break;
            default:
                break;
        }

        return parseLogicalOrExpression();
    }

    private static final Function<QueryParser, Expression> FnParseUnnamedExpression =
        qp -> qp.parseUnnamedExpression();

    // endregion

    // part B: QueryParser.cs:3277-7062
    // region Query operator parameters

    private Expression parseAnyQueryOperatorParameterValue()
    {
        var expr = parseLiteral();

        if (expr == null)
            expr = parseIdentifierOrKeywordTokenLiteral();

        if (expr == null)
            expr = parseNameReference();

        return expr;
    }

    private Expression parseAnyQueryOperatorParameterForcedRealValue()
    {
        var expr = parseForcedRealLiteral();

        if (expr == null)
            expr = parseLiteral();

        if (expr == null)
            expr = parseIdentifierOrKeywordTokenLiteral();

        if (expr == null)
            expr = parseNameReference();

        return expr;
    }

    private Expression parseTokenLiteral(List<String> texts)
    {
        var token = parseTokenText(texts); // PORT: §2.5
        if (token != null)
            return new LiteralExpression(SyntaxKind.TokenLiteralExpression, token);
        return null;
    }

    private Expression parseQueryOperatorParameterValue(QueryOperatorParameter queryParameter, Predicate<QueryParser> fnEndNameList)
    {
        Expression result;
        switch (queryParameter.valueKind())
        {
            case QueryOperatorParameterValueKind.Any:
                return parseAnyQueryOperatorParameterValue();
            case QueryOperatorParameterValueKind.StringLiteral:
                result = parseAnyQueryOperatorParameterValue();
                if (result == null)
                    result = CreateMissingStringLiteral.get();
                return result;
            case QueryOperatorParameterValueKind.BoolLiteral:
                result = parseAnyQueryOperatorParameterValue();
                if (result == null)
                    result = CreateMissingBoolLiteral.get();
                return result;
            case QueryOperatorParameterValueKind.IntegerLiteral:
            case QueryOperatorParameterValueKind.NumericLiteral:
            case QueryOperatorParameterValueKind.SummableLiteral:
                result = parseAnyQueryOperatorParameterValue();
                if (result == null)
                    result = CreateMissingLongLiteral.get();
                return result;
            case QueryOperatorParameterValueKind.ForcedRealLiteral:
                result = parseAnyQueryOperatorParameterForcedRealValue();
                if (result == null)
                    result = CreateMissingRealLiteral.get();
                return result;
            case QueryOperatorParameterValueKind.ScalarLiteral:
                result = parseAnyQueryOperatorParameterValue();
                if (result == null)
                    result = CreateMissingValue.get();
                return result;
            case QueryOperatorParameterValueKind.String:
                result = parseFunctionCallOrPath();
                if (result == null)
                    result = CreateMissingValue.get();
                return result;
            case QueryOperatorParameterValueKind.Word:
            case QueryOperatorParameterValueKind.WordOrNumber:
                if (queryParameter.values().size() > 0)
                {
                    result = parseTokenLiteral(queryParameter.values());
                    if (result == null)
                        result = parseAnyQueryOperatorParameterValue();
                    if (result == null)
                        result = createMissingTokenLiteral(queryParameter.values());
                    return result;
                }
                result = parseAnyQueryOperatorParameterValue();
                if (result == null)
                    result = CreateMissingValue.get();
                return result;
            case QueryOperatorParameterValueKind.NameDeclaration:
                result = parseNameDeclaration();
                if (result == null)
                    result = parseAnyQueryOperatorParameterValue();
                if (result == null)
                    result = createMissingNameDeclaration();
                return result;
            case QueryOperatorParameterValueKind.Column:
                result = parseNameReference();
                if (result == null)
                    result = parseAnyQueryOperatorParameterValue();
                if (result == null)
                    result = createMissingNameReference();
                return result;
            case QueryOperatorParameterValueKind.ColumnList:
                return parseNameReferenceList(fnEndNameList != null ? fnEndNameList : FnScanQueryOperatorParameterNameListEnd); // PORT: §3.14 ??
            default:
                result = parseAnyQueryOperatorParameterValue();
                if (result == null)
                    result = CreateMissingValue.get();
                return result;
        }
    }

    // PORT: §3.12 telescoped overload
    private Expression parseQueryOperatorParameterValue(QueryOperatorParameter queryParameter)
    {
        return parseQueryOperatorParameterValue(queryParameter, null);
    }

    private boolean scanQueryOperatorParameterNameListEnd()
    {
        if (peekToken().kind() == SyntaxKind.CommaToken)
        {
            return scanKnownQueryOperatorParameterName(false, 1) > 0
                || scanCommonListEnd(1);
        }
        else
        {
            return scanKnownQueryOperatorParameterName(true) > 0
                || scanCommonListEnd();
        }
    }

    private static final Predicate<QueryParser> FnScanQueryOperatorParameterNameListEnd =
        qp -> qp.scanQueryOperatorParameterNameListEnd();

    private NamedParameter parseQueryOperatorParameter(QueryOperatorParameter parameter, Predicate<QueryParser> fnEndNameList)
    {
        var len = scanToken(parameter.name());
        if (len > 0)
        {
            if (parameter.hasNoEquals())
            {
                return new NamedParameter(
                    new NameDeclaration(new TokenName(parseTokenText(parameter.name()))),
                    SyntaxToken.missing(SyntaxKind.EqualToken),
                    parseQueryOperatorParameterValue(parameter, fnEndNameList),
                    getExpressionHint(parameter));
            }
            else if (peekToken(len).kind() == SyntaxKind.EqualToken)
            {
                return new NamedParameter(
                    new NameDeclaration(new TokenName(parseTokenText(parameter.name()))),
                    parseRequiredToken(SyntaxKind.EqualToken),
                    parseQueryOperatorParameterValue(parameter, fnEndNameList),
                    getExpressionHint(parameter));
            }
        }

        return null;
    }

    // PORT: §3.12 telescoped overload
    private NamedParameter parseQueryOperatorParameter(QueryOperatorParameter parameter)
    {
        return parseQueryOperatorParameter(parameter, null);
    }

    private static int getExpressionHint(QueryOperatorParameter parameter)
    {
        switch (parameter.valueKind())
        {
            case QueryOperatorParameterValueKind.Column:
            case QueryOperatorParameterValueKind.ColumnList:
                return CompletionHint.Column;
            default:
                return CompletionHint.None;
        }
    }

    private NamedParameter parseQueryOperatorParameter()
    {
        var nameToken = parseQueryOperatorParameterName(_queryOperatorParameterNamesAllowed, _queryOperatorParameterEqualsNeeded);
        if (nameToken != null)
        {
            var name = new NameDeclaration(new TokenName(nameToken));
            var equal = parseRequiredToken(SyntaxKind.EqualToken);

            // PORT: §3.3 Dictionary.TryGetValue(out) -> get() != null (values are never null)
            QueryOperatorParameter queryParameter = null;
            if (_operatorSpecificNameToQueryOperatorParameterMap != null)
                queryParameter = _operatorSpecificNameToQueryOperatorParameterMap.get(nameToken.text());
            if (queryParameter == null)
                queryParameter = s_nameToDefaultQueryOperatorParameterMap.get(nameToken.text());

            if (queryParameter != null)
            {
                var value = parseQueryOperatorParameterValue(queryParameter);
                return new NamedParameter(name, equal, value, getExpressionHint(queryParameter));
            }

            // not a known parameter, but parse it anyway
            var anyValue = parseAnyQueryOperatorParameterValue(); // PORT: §3.14 ??
            if (anyValue == null)
                anyValue = CreateMissingValue.get();
            return new NamedParameter(name, equal, anyValue);
        }

        return null;
    }

    private static final Function<QueryParser, NamedParameter> FnParseQueryOperatorParameter =
        qp -> qp.parseQueryOperatorParameter();

    private static final Map<String, QueryOperatorParameter> s_nameToDefaultQueryOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.AllParameters);

    private static Map<String, QueryOperatorParameter> createQueryOperatorParameterMap(List<QueryOperatorParameter> parameters)
    {
        var map = new LinkedHashMap<String, QueryOperatorParameter>();

        for (int i = 0; i < parameters.size(); i++)
        {
            var p = parameters.get(i);
            map.put(p.name(), p);

            if (p.aliases() != null && p.aliases().size() > 0)
            {
                for (var aliasName : p.aliases())
                {
                    map.put(aliasName, p);
                }
            }
        }

        return map;
    }

    private static boolean isMultiTokenName(String name)
    {
        // if its not a keyword or a legal identifier, then assume it is a multi-token name like foo-bar
        return !SyntaxFacts.isKeyword(name) && !KustoFacts.canBeIdentifier(name);
    }

    /// <summary>
    /// The set of known query operator parameter names
    /// </summary>
    private static final Set<String> s_knownQueryOperaterParameterNames =
        new LinkedHashSet<String>(
            Linq.concat(s_nameToDefaultQueryOperatorParameterMap.keySet(),
                KustoFacts.knownQueryOperatorParameterNames())); // PORT: §3.6 Concat; §3.17 HashSet

    /// <summary>
    /// The list of known query parameters names that are likely to be parsed as multiple lexical tokens
    /// </summary>
    private static final List<String> s_multiTokenQueryOperatorParameterNames =
        Linq.toList(Linq.where(s_knownQueryOperaterParameterNames, QueryParser::isMultiTokenName)); // PORT: §3.6 Where/ToList

    private boolean tryGetSpecificQueryOperatorParameter(String name, Out<QueryOperatorParameter> parameter)
    {
        if (_operatorSpecificNameToQueryOperatorParameterMap != null)
        {
            // PORT: §3.3 Dictionary.TryGetValue(out) -> get() != null (values are never null)
            parameter.value = _operatorSpecificNameToQueryOperatorParameterMap.get(name);
            return parameter.value != null;
        }

        parameter.value = null;
        return false;
    }

    private boolean isSpecificQueryOperatorParameterName(String name)
    {
        return tryGetSpecificQueryOperatorParameter(name, new Out<QueryOperatorParameter>()); // PORT: §3.3 out _
    }

    private boolean scanSpecificQueryOperatorParameterName(boolean equalsNeeded, int offset)
    {
        var lt = peekToken(offset);
        var parameterOut = new Out<QueryOperatorParameter>(); // PORT: §3.3 out var

        if ((lt.kind() == SyntaxKind.IdentifierToken || SyntaxFacts.isKeyword(lt.kind()))
            && tryGetSpecificQueryOperatorParameter(lt.text(), parameterOut))
        {
            var parameter = parameterOut.value;
            if (parameter.hasNoEquals() || !equalsNeeded)
                return true;

            // allow this to be a query operator parameter name if it is followed by equals
            return peekToken(offset + 1).kind() == SyntaxKind.EqualToken;
        }
        else
        {
            return false;
        }
    }

    // PORT: §3.12 telescoped overload
    private boolean scanSpecificQueryOperatorParameterName(boolean equalsNeeded)
    {
        return scanSpecificQueryOperatorParameterName(equalsNeeded, 0);
    }

    private SyntaxToken parseSpecificQueryOperatorParameterName()
    {
        if (scanSpecificQueryOperatorParameterName(false))
        {
            return parseToken();
        }

        return null;
    }

    private boolean isKnownQueryOperatorParameterName(String name)
    {
        return s_knownQueryOperaterParameterNames.contains(name)
            || isSpecificQueryOperatorParameterName(name);
    }

    private int scanKnownQueryOperatorParameterName(boolean equalasNeeded, int offset)
    {
        var token = peekToken(offset);

        int len = -1;

        if ((token.kind() == SyntaxKind.IdentifierToken || SyntaxFacts.isKeyword(token.kind()))
            && isKnownQueryOperatorParameterName(token.text()))
        {
            len = 1;
        }
        else
        {
            // check for any special names that don't conform to identifiers
            for (int i = 0; i < s_multiTokenQueryOperatorParameterNames.size(); i++)
            {
                len = scanToken(s_multiTokenQueryOperatorParameterNames.get(i));
                if (len > 0)
                    break;
            }
        }

        if (len > 0
            && equalasNeeded
            && peekToken(offset + len).kind() != SyntaxKind.EqualToken)
            return -1;

        return len;
    }

    // PORT: §3.12 telescoped overload
    private int scanKnownQueryOperatorParameterName(boolean equalasNeeded)
    {
        return scanKnownQueryOperatorParameterName(equalasNeeded, 0);
    }

    private SyntaxToken parseKnownQueryOperatorParameterName()
    {
        var lt = peekToken();
        if ((lt.kind() == SyntaxKind.IdentifierToken || SyntaxFacts.isKeyword(lt.kind()))
            && isKnownQueryOperatorParameterName(lt.text()))
        {
            return parseToken();
        }
        else
        {
            // check for any special names that don't conform to identifiers
            for (int i = 0; i < s_multiTokenQueryOperatorParameterNames.size(); i++)
            {
                var tok = parseTokenText(s_multiTokenQueryOperatorParameterNames.get(i));
                if (tok != null)
                    return tok;
            }
        }

        return null;
    }

    private int scanQueryOperatorParameterName(AllowedNameKind namesAllowed, boolean equalsNeeded, int offset)
    {
        int len = -1;

        if (namesAllowed == AllowedNameKind.DeclaredOnly || namesAllowed == AllowedNameKind.DeclaredOrKnown)
        {
            len = scanSpecificQueryOperatorParameterName(equalsNeeded, offset) ? 1 : -1;
        }

        if (len < 0 && (namesAllowed == AllowedNameKind.KnownOnly || namesAllowed == AllowedNameKind.DeclaredOrKnown))
        {
            len = scanKnownQueryOperatorParameterName(equalsNeeded, offset);
        }

        return len;
    }

    // PORT: §3.12 telescoped overload
    private int scanQueryOperatorParameterName(AllowedNameKind namesAllowed, boolean equalsNeeded)
    {
        return scanQueryOperatorParameterName(namesAllowed, equalsNeeded, 0);
    }

    private SyntaxToken parseQueryOperatorParameterName(AllowedNameKind namesAllowed, boolean equalsNeeded)
    {
        var len = scanQueryOperatorParameterName(namesAllowed, equalsNeeded);
        if (len > 0)
        {
            switch (namesAllowed)
            {
                case AllowedNameKind.DeclaredOnly:
                    return parseSpecificQueryOperatorParameterName();
                case AllowedNameKind.KnownOnly:
                    return parseKnownQueryOperatorParameterName();
                case AllowedNameKind.DeclaredOrKnown:
                default:
                    SyntaxToken result = parseSpecificQueryOperatorParameterName();
                    if (result == null)
                        result = parseKnownQueryOperatorParameterName();
                    return result;
            }
        }

        return null;
    }

    private NameReference parseNameReferenceListName()
    {
        return scanSpecificQueryOperatorParameterName(true)
            ? null // don't consume other known query operator parameter names as names in the name list
            : parseExtendedNameReference();
    }

    private static final Function<QueryParser, NameReference> FnParseNameReferenceListName =
        qp -> qp.parseNameReferenceListName();

    private NameReferenceList parseNameReferenceList(Predicate<QueryParser> fnEndList)
    {
        var names = parseCommaList(FnParseNameReferenceListName, fnCreateMissingNameReference(), fnEndList, true);
        return new NameReferenceList(names);
    }

    private Map<String, QueryOperatorParameter> _operatorSpecificNameToQueryOperatorParameterMap;
    private AllowedNameKind _queryOperatorParameterNamesAllowed = AllowedNameKind.DeclaredOrKnown; // PORT: §3.9 default(enum)
    private boolean _queryOperatorParameterEqualsNeeded;

    private SyntaxList1<NamedParameter> parseQueryOperatorParameterList(Map<String, QueryOperatorParameter> nameToParameterMap, AllowedNameKind namesAllowed, boolean equalsNeeded)
    {
        var oldParameters = _operatorSpecificNameToQueryOperatorParameterMap;
        var oldNamesAllowed = _queryOperatorParameterNamesAllowed;
        var oldEqualsNeeded = _queryOperatorParameterEqualsNeeded;

        _operatorSpecificNameToQueryOperatorParameterMap = nameToParameterMap;
        _queryOperatorParameterNamesAllowed = namesAllowed;
        _queryOperatorParameterEqualsNeeded = equalsNeeded;

        var list = parseList(FnParseQueryOperatorParameter);

        _operatorSpecificNameToQueryOperatorParameterMap = oldParameters;
        _queryOperatorParameterNamesAllowed = oldNamesAllowed;
        _queryOperatorParameterEqualsNeeded = oldEqualsNeeded;

        return list;
    }

    // PORT: §3.12 telescoped overload
    private SyntaxList1<NamedParameter> parseQueryOperatorParameterList(Map<String, QueryOperatorParameter> nameToParameterMap, AllowedNameKind namesAllowed)
    {
        return parseQueryOperatorParameterList(nameToParameterMap, namesAllowed, false);
    }

    // PORT: §3.12 telescoped overload
    private SyntaxList1<NamedParameter> parseQueryOperatorParameterList(Map<String, QueryOperatorParameter> nameToParameterMap)
    {
        return parseQueryOperatorParameterList(nameToParameterMap, AllowedNameKind.DeclaredOrKnown, false);
    }

    // PORT: §3.12 telescoped overload
    private SyntaxList1<NamedParameter> parseQueryOperatorParameterList()
    {
        return parseQueryOperatorParameterList(null, AllowedNameKind.DeclaredOrKnown, false);
    }

    private SyntaxList1<SeparatedElement1<NamedParameter>> parseQueryOperatorParameterCommaList(Map<String, QueryOperatorParameter> nameToParameterMap, Predicate<QueryParser> fnScanEnd, AllowedNameKind namesAllowed)
    {
        var oldParameters = _operatorSpecificNameToQueryOperatorParameterMap;
        var oldNamesAllowed = _queryOperatorParameterNamesAllowed;

        _operatorSpecificNameToQueryOperatorParameterMap = nameToParameterMap;
        _queryOperatorParameterNamesAllowed = namesAllowed;

        // PORT-BUG: the fnScanEnd parameter is ignored (FnScanCommonListEnd is always used) and equalsNeeded is not propagated; upstream behaviour mirrored
        var list = parseCommaList(FnParseQueryOperatorParameter, CreateMissingNamedParameter, FnScanCommonListEnd);

        _operatorSpecificNameToQueryOperatorParameterMap = oldParameters;
        _queryOperatorParameterNamesAllowed = oldNamesAllowed;

        return list;
    }

    // PORT: §3.12 telescoped overload
    private SyntaxList1<SeparatedElement1<NamedParameter>> parseQueryOperatorParameterCommaList(Map<String, QueryOperatorParameter> nameToParameterMap, Predicate<QueryParser> fnScanEnd)
    {
        return parseQueryOperatorParameterCommaList(nameToParameterMap, fnScanEnd, AllowedNameKind.DeclaredOrKnown);
    }

    // PORT: §3.12 telescoped overload
    private SyntaxList1<SeparatedElement1<NamedParameter>> parseQueryOperatorParameterCommaList(Map<String, QueryOperatorParameter> nameToParameterMap)
    {
        return parseQueryOperatorParameterCommaList(nameToParameterMap, null, AllowedNameKind.DeclaredOrKnown);
    }

    // PORT: §3.12 telescoped overload
    private SyntaxList1<SeparatedElement1<NamedParameter>> parseQueryOperatorParameterCommaList()
    {
        return parseQueryOperatorParameterCommaList(null, null, AllowedNameKind.DeclaredOrKnown);
    }

    // endregion
    // region Entity Names

    private Expression parseBracketedEntityNamePathElementSelector()
    {
        Expression result = parseBracketedWildcardedNameReference();
        if (result == null)
            result = parseBracketedNameReference();
        return result;
    }

    private static Function<QueryParser, Expression> FnParseBracketedEntityNamePathElementSelector =
        qp -> qp.parseBracketedEntityNamePathElementSelector();

    private Expression parseEntityPathExpression()
    {
        var expr = parsePathElementSelectorOrFunctionCall();

        if (expr != null)
        {
            while (true)
            {
                var kind = peekToken().kind();
                if (kind == SyntaxKind.DotToken)
                {
                    var dot = parseToken(); // PORT: §3.14 ?? (argument evaluation order kept)
                    var selector = parsePathElementSelectorOrFunctionCall();
                    if (selector == null)
                        selector = createMissingNameReference();
                    expr = new PathExpression(expr, dot, selector);
                }
                else if (kind == SyntaxKind.OpenBracketToken)
                {
                    expr = new ElementExpression(expr, parseBracketedPathElementSelector());
                }
                else
                {
                    break;
                }
            }
        }

        return expr;
    }

    private boolean scanQualifiedEntityStart()
    {
        return ((Objects.equals(peekToken().text(), Functions.Database.name()) // PORT: §3.14 string ==
            || Objects.equals(peekToken().text(), Functions.Cluster.name()))
            && peekToken(1).kind() == SyntaxKind.OpenParenToken);
    }

    private Expression parseSimplePathExpression()
    {
        var expr = parsePathElementSelector();

        if (expr != null)
        {
            while (true)
            {
                var kind = peekToken().kind();
                if (kind == SyntaxKind.DotToken)
                {
                    var dot = parseToken(); // PORT: §3.14 ?? (argument evaluation order kept)
                    var selector = parsePathElementSelector();
                    if (selector == null)
                        selector = createMissingNameReference();
                    expr = new PathExpression(expr, dot, selector);
                }
                else if (kind == SyntaxKind.OpenBracketToken)
                {
                    expr = new ElementExpression(expr, parseBracketedPathElementSelector());
                }
                else
                {
                    break;
                }
            }
        }

        return expr;
    }

    private Expression parseEntityReferenceExpression()
    {
        return parseSimplePathExpression();
    }

    private static final Function<QueryParser, Expression> FnParseSimplePathExpression =
        qp -> qp.parseSimplePathExpression();

    private Expression parseWildcardedEntityExpression()
    {
        if (scanWildcardedName() > 0)
        {
            return parseWildcardedNameReference();
        }
        else
        {
            var expr = parsePathElementSelectorOrFunctionCall();

            if (expr != null)
            {
                while (true)
                {
                    var kind = peekToken().kind();
                    if (kind == SyntaxKind.DotToken)
                    {
                        var dot = parseToken();
                        if (scanWildcardedName() > 0)
                        {
                            expr = new PathExpression(expr, dot, parseWildcardedNameReference());
                            return expr;
                        }
                        else
                        {
                            var selector = parsePathElementSelectorOrFunctionCall(); // PORT: §3.14 ??
                            if (selector == null)
                                selector = createMissingNameReference();
                            expr = new PathExpression(expr, dot, selector);
                        }
                    }
                    else if (kind == SyntaxKind.OpenBracketToken)
                    {
                        expr = new ElementExpression(expr, parseBracketedPathElementSelector());
                    }
                    else
                    {
                        break;
                    }
                }
            }

            return expr;
        }
    }

    private static Function<QueryParser, Expression> FnParseWildcardedEntityExpression =
        qp -> qp.parseWildcardedEntityExpression();


    // endregion
    // region Query Operators

    // region consume

    private static final Map<String, QueryOperatorParameter> s_consumeOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.ConsumeParameters);

    private ConsumeOperator parseConsumeOperator()
    {
        var keyword = parseToken(SyntaxKind.ConsumeKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_consumeOperatorParameterMap);
            return new ConsumeOperator(keyword, parameters);
        }

        return null;
    }

    // endregion
    // region count

    private CountAsIdentifierClause parseCountAsIdentifierClause()
    {
        var keyword = parseToken(SyntaxKind.AsKeyword);
        if (keyword != null)
        {
            var id = parseRequiredToken(SyntaxKind.IdentifierToken);
            return new CountAsIdentifierClause(keyword, id);
        }

        return null;
    }

    private CountOperator parseCountOperator()
    {
        // don't collide with count() function
        if (peekToken().kind() == SyntaxKind.CountKeyword
            && peekToken(1).kind() != SyntaxKind.OpenParenToken)
        {
            var keyword = parseToken();
            var asClause = parseCountAsIdentifierClause();
            return new CountOperator(keyword, asClause);

        }

        return null;
    }

    // endregion
    // region executeAndCache

    private ExecuteAndCacheOperator parseExecuteAndCacheOperator()
    {
        var keyword = parseToken(SyntaxKind.ExecuteAndCacheKeyword);
        if (keyword != null)
        {
            return new ExecuteAndCacheOperator(keyword);
        }

        return null;
    }

    // endregion
    // region extend

    private ExtendOperator parseExtendOperator()
    {
        var keyword = parseToken(SyntaxKind.ExtendKeyword);
        if (keyword != null)
        {
            var expressions = parseCommaList(FnParseNamedExpression, this::createMissingExpression, FnScanCommonListEnd, true);
            return new ExtendOperator(keyword, expressions);
        }

        return null;
    }

    // endregion
    // region facet

    private FacetWithClause parseFacetWithClause()
    {
        var keyword = parseToken(SyntaxKind.WithKeyword);
        if (keyword != null)
        {
            var open = parseToken(SyntaxKind.OpenParenToken);
            if (open != null)
            {
                var expr = parseForkPipeExpression();
                if (expr == null)
                    expr = CreateMissingQueryOperatorExpression.get();
                var close = parseRequiredToken(SyntaxKind.CloseParenToken);
                return new FacetWithExpressionClause(keyword, open, expr, close);
            }
            else
            {
                var op = parseForkPipeQueryOperator();
                if (op == null)
                    op = CreateMissingQueryOperator.get();
                return new FacetWithOperatorClause(keyword, op);
            }
        }

        return null;
    }

    private static final Predicate<QueryParser> FnScanFacetExpressionListEnd =
        qp -> qp.scanCustomListEnd(SyntaxKind.WithKeyword);


    private FacetOperator parseFacetOperator()
    {
        var keyword = parseToken(SyntaxKind.FacetKeyword);
        if (keyword != null)
        {
            var byKeyword = parseRequiredToken(SyntaxKind.ByKeyword);
            var expressions = parseCommaList(FnParseSimplePathExpression, fnCreateMissingNameReferenceAsExpression(), FnScanFacetExpressionListEnd, true);
            var withClause = parseFacetWithClause();
            return new FacetOperator(keyword, byKeyword, expressions, withClause);
        }

        return null;
    }

    // endregion
    // region filter / where

    private static final List<SyntaxKind> s_filterOperatorKeywords =
        Arrays.asList(SyntaxKind.WhereKeyword, SyntaxKind.FilterKeyword);

    private static final Map<String, QueryOperatorParameter> s_filterOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.FilterParameters);

    private FilterOperator parseFilterOperator()
    {
        var keyword = parseToken(SyntaxKind.WhereKeyword);
        if (keyword == null)
            keyword = parseToken(SyntaxKind.FilterKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_filterOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expr = parseNamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new FilterOperator(keyword, parameters, expr);
        }

        return null;
    }

    // endregion
    // region getschema

    private GetSchemaOperator parseGetSchemaOperator()
    {
        var keyword = parseToken(SyntaxKind.GetSchemaKeyword);
        if (keyword != null)
        {
            var kind = parseQueryOperatorParameter(QueryOperatorParameters.GetSchemaKind);
            return new GetSchemaOperator(keyword, kind);
        }
        return null;
    }

    // endregion
    // region find

    private Expression parseFindOperand_NameWithOptionalAsOperator()
    {
        Expression expr = parseBracketedEntityNamePathElementSelector();
        if (expr == null)
            expr = parseBarePathElementSelector();

        if (expr != null
            && parseToken(SyntaxKind.BarToken) instanceof SyntaxToken barToken)
        {
            QueryOperator asOp = parseAsOperator();
            if (asOp == null)
                asOp = CreateMissingQueryOperator.get();
            expr = new PipeExpression(expr, barToken, asOp);
        }

        return expr;
    }

    private static Function<QueryParser, Expression> FnParseFindOperand_NameWithOptionalAsOperator =
        qp -> qp.parseFindOperand_NameWithOptionalAsOperator();

    private static List<Function<QueryParser, Expression>> FindOperandParsers =
        Arrays.asList(
            FnParseFindOperand_NameWithOptionalAsOperator,
            FnParseWildcardedEntityExpression);

    private Expression parseFindOperand()
    {
        return parseBest(FindOperandParsers);
    }

    private static final Function<QueryParser, Expression> FnParseFindOperand =
        qp -> qp.parseFindOperand();

    private FindInClause parseFindInClause()
    {
        var keyword = parseToken(SyntaxKind.InKeyword);
        if (keyword != null)
        {
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var expressions = parseCommaList(FnParseFindOperand, this::createMissingExpression, FnScanCommonListEnd, true);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new FindInClause(keyword, open, expressions, close);
        }

        return null;
    }

    private Expression parseTypedColumnNameReference()
    {
        var name = parseNameReference();

        if (name != null && peekToken().kind() == SyntaxKind.ColonToken)
        {
            var colon = parseToken();
            var type = parseParamTypeExtended();
            if (type == null)
                type = parseInvalidParamType();
            if (type == null)
                type = CreateMissingType.get();
            return new TypedColumnReference(name, colon, type);
        }

        return name;
    }

    private static final Function<QueryParser, Expression> FnParseTypeColumnNameReference =
        qp -> qp.parseTypedColumnNameReference();

    private PackExpression parsePackExpression()
    {
        var keyword = parseToken(SyntaxKind.PackKeyword);
        if (keyword != null)
        {
            return new PackExpression(
                keyword,
                parseRequiredToken(SyntaxKind.OpenParenToken),
                parseRequiredToken(SyntaxKind.AsteriskToken),
                parseRequiredToken(SyntaxKind.CloseParenToken));
        }

        return null;
    }

    private Expression parseFindProjectColumn()
    {
        Expression result = parsePackExpression();
        if (result == null)
            result = parseStarExpression();
        if (result == null)
            result = parseTypedColumnNameReference();
        return result;
    }

    private static final Function<QueryParser, Expression> FnParseFindProjectColumn =
        qp -> qp.parseFindProjectColumn();

    private FindProjectClause parseFindProjectClause()
    {
        switch (peekToken().kind())
        {
            case SyntaxKind.ProjectKeyword:
                return new FindProjectClause(parseToken(), parseCommaList(FnParseFindProjectColumn, this::createMissingExpression, null, true));
            case SyntaxKind.ProjectSmartKeyword:
                return new FindProjectClause(parseToken(), SyntaxList1.<SeparatedElement1<Expression>>empty());
            default:
                return null;
        }
    }

    private FindProjectClause parseFindProjectAwayClause()
    {
        var keyword = parseToken(SyntaxKind._ProjectAwayKeyword);
        if (keyword != null)
        {
            var columns = parseCommaList(FnParseFindProjectColumn, this::createMissingExpression, null, true);
            return new FindProjectClause(keyword, columns);
        }

        return null;
    }

    private static final Map<String, QueryOperatorParameter> s_findOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.FindParameters);

    private FindOperator parseFindOperator()
    {
        var keyword = parseToken(SyntaxKind.FindKeyword);
        if (keyword != null)
        {
            var dataScope = parseDataScopeClause();
            var parameters = parseQueryOperatorParameterList(s_findOperatorParameterMap);
            var inClause = parseFindInClause();
            var where = (parameters.size() > 0 || inClause != null)
                ? parseRequiredToken(SyntaxKind.WhereKeyword)
                : parseToken(SyntaxKind.WhereKeyword);
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            var project = parseFindProjectClause();
            var projectAway = parseFindProjectAwayClause();
            return new FindOperator(keyword, dataScope, parameters, inClause, where, expr, project, projectAway);
        }

        return null;
    }

    // endregion
    // region search

    private Expression parseSearchPredicate()
    {
        var expr = parseUnnamedExpression();
        if (expr == null)
        {
            expr = parseStarExpression();
            if (expr != null && parseToken(SyntaxKind.AndKeyword) instanceof SyntaxToken andKeyword)
            {
                var right = parseUnnamedExpression(); // PORT: §3.14 ??
                if (right == null)
                    right = createMissingExpression();
                return new BinaryExpression(SyntaxKind.AndExpression, expr, andKeyword, right);
            }
        }

        return expr;
    }

    private static final Map<String, QueryOperatorParameter> s_searchOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.SearchParameters);

    private SearchOperator parseSearchOperator()
    {
        var keyword = parseToken(SyntaxKind.SearchKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_searchOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var dataScope = parseDataScopeClause();
            var inClause = parseFindInClause();
            var condition = parseSearchPredicate();
            if (condition == null)
                condition = createMissingExpression();
            return new SearchOperator(keyword, parameters, dataScope, inClause, condition);
        }

        return null;
    }

    // endregion
    // region fork

    private int scanNameEqualsClause(int offset)
    {
        var len = scanName(offset);
        return len > 0 && peekToken(len).kind() == SyntaxKind.EqualToken
            ? len + 1
            : -1;
    }

    // PORT: §3.12 telescoped overload
    private int scanNameEqualsClause()
    {
        return scanNameEqualsClause(0);
    }

    private NameEqualsClause parseNameEqualsClause()
    {
        if (scanNameEqualsClause() > 0)
        {
            var name = parseNameDeclaration();
            var equal = parseRequiredToken(SyntaxKind.EqualToken);
            return new NameEqualsClause(name, equal);
        }

        return null;
    }

    private ForkExpression parseForkExpression()
    {
        if (scanNameEqualsClause() > 0)
        {
            var nameEqual = parseNameEqualsClause();
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var expr = parseForkPipeExpression();
            if (expr == null)
                expr = parseExpression();
            if (expr == null)
                expr = createMissingExpression();
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new ForkExpression(nameEqual, open, expr, close);
        }
        else if (peekToken().kind() == SyntaxKind.OpenParenToken)
        {
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var expr = parseForkPipeExpression();
            if (expr == null)
                expr = parseExpression();
            if (expr == null)
                expr = createMissingExpression();
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new ForkExpression(null, open, expr, close);
        }

        return null;
    }

    private static final Function<QueryParser, ForkExpression> FnParseForkExpression =
        qp -> qp.parseForkExpression();

    private ForkExpression createMissingForkExpression()
    {
        return new ForkExpression(
            null,
            createMissingToken(SyntaxKind.OpenParenToken),
            createMissingExpression(),
            createMissingToken(SyntaxKind.CloseParenToken));
    }

    private ForkOperator parseForkOperator()
    {
        var keyword = parseToken(SyntaxKind.ForkKeyword);
        if (keyword != null)
        {
            var expressions = parseList(FnParseForkExpression, this::createMissingForkExpression, null, true);
            return new ForkOperator(keyword, expressions);
        }

        return null;
    }

    private Expression parseForkPipeExpression()
    {
        Expression expr = parseForkPipeQueryOperator();
        if (expr != null)
        {
            while (peekToken().kind() == SyntaxKind.BarToken)
            {
                var pipe = parseToken();
                var pipedOperator = parseRequiredQueryOperator();
                expr = new PipeExpression(expr, pipe, pipedOperator);
            }
        }

        return expr;
    }

    private QueryOperator parseForkPipeQueryOperator()
    {
        QueryOperator result = parsePipedQueryOperator();
        if (result == null)
            result = parseUnpipedQueryOperator();
        return result;
    } // not legal, but let semantic analyzer flag the error.

    // endregion
    // region partition

    private PartitionQuery parsePartitionQuery()
    {
        var open = parseToken(SyntaxKind.OpenBraceToken);
        if (open != null)
        {
            var expr = parseExpression();
            if (expr == null)
                expr = createMissingExpression();
            var close = parseRequiredToken(SyntaxKind.CloseBraceToken);
            return new PartitionQuery(open, expr, close);
        }

        return null;
    }

    private PartitionSubquery parsePartitionSubquery()
    {
        var open = parseToken(SyntaxKind.OpenParenToken);
        if (open != null)
        {
            var expr = parsePipeSubExpression();
            if (expr == null)
                expr = parseExpression();
            if (expr == null)
                expr = createMissingExpression();
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new PartitionSubquery(open, expr, close);
        }

        return null;
    }

    private PartitionScope parsePartitionScope()
    {
        var inKeyword = parseToken(SyntaxKind.InKeyword);
        if (inKeyword != null)
        {
            Expression scope = parseFunctionCallExpression();
            if (scope == null)
                scope = parseDynamicLiteral();
            if (scope == null)
                scope = createMissingExpression();
            return new PartitionScope(inKeyword, scope);
        }

        return null;
    }

    private PartitionOperand parsePartitionOperand()
    {
        switch (peekToken().kind())
        {
            case SyntaxKind.OpenBraceToken:
                return parsePartitionQuery();
            case SyntaxKind.OpenParenToken:
                return parsePartitionSubquery();
            default:
                return null;
        }
    }

    private PartitionOperand createMissingPartitionOperand()
    {
        return new PartitionSubquery(
            createMissingToken(SyntaxKind.OpenParenToken),
            createMissingExpression(),
            createMissingToken(SyntaxKind.CloseParenToken));
    }

    private static final Map<String, QueryOperatorParameter> s_partitionOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.PartitionParameters);


    private PartitionOperator parsePartitionOperator()
    {
        var keyword = parseToken(SyntaxKind.PartitionKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_partitionOperatorParameterMap);
            var byKeyword = parseRequiredToken(SyntaxKind.ByKeyword);
            Expression byExpr = parseSimplePathExpression();
            if (byExpr == null)
                byExpr = createMissingNameReference();
            var scope = parsePartitionScope();
            var operand = parsePartitionOperand();
            if (operand == null)
                operand = createMissingPartitionOperand();
            return new PartitionOperator(keyword, parameters, byKeyword, byExpr, scope, operand);
        }

        return null;
    }

    // endregion
    // region join

    private JoinConditionClause parseJoinOnClause()
    {
        var keyword = parseToken(SyntaxKind.OnKeyword);
        if (keyword != null)
        {
            var expressions = parseCommaList(FnParseUnnamedExpression, this::createMissingExpression, null, true);
            return new JoinOnClause(keyword, expressions);
        }

        return null;
    }

    private JoinConditionClause parseJoinWhereClause()
    {
        var keyword = parseToken(SyntaxKind.WhereKeyword);
        if (keyword != null)
        {
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new JoinWhereClause(keyword, expr);
        }

        return null;
    }

    private static JoinOnClause createMissingJoinOnClause()
    {
        return new JoinOnClause(
            SyntaxToken.missing(SyntaxKind.JoinOnClause),
            SyntaxList1.<SeparatedElement1<Expression>>empty(),
            Arrays.asList(DiagnosticFacts.getMissingJoinOnClause()));
    }


    private static final Map<String, QueryOperatorParameter> s_joinOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.JoinParameters);

    private static final Map<String, QueryOperatorParameter> s_GraphMarkComponentsParametersMap =
                    createQueryOperatorParameterMap(QueryOperatorParameters.GraphMarkComponentsParameters);

    private JoinOperator parseJoinOperator()
    {
        var keyword = parseToken(SyntaxKind.JoinKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_joinOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            var condition = parseJoinOnClause();
            if (condition == null)
                condition = parseJoinWhereClause();
            return new JoinOperator(keyword, parameters, expr, condition);
        }

        return null;
    }

    // endregion
    // region lookup

    private static final Map<String, QueryOperatorParameter> s_lookupOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.LookupParameters);

    private LookupOperator parseLookupOperator()
    {
        var keyword = parseToken(SyntaxKind.LookupKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_lookupOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expression = parseUnnamedExpression();
            if (expression == null)
                expression = createMissingExpression();
            JoinConditionClause condition = parseJoinOnClause();
            if (condition == null)
                condition = createMissingJoinOnClause();
            return new LookupOperator(keyword, parameters, expression, condition);
        }

        return null;
    }

    // endregion
    // region make-series

    private DefaultExpressionClause parseDefaultExpressionClause()
    {
        var keyword = parseToken(SyntaxKind.DefaultKeyword);
        if (keyword != null)
        {
            var equal = parseRequiredToken(SyntaxKind.EqualToken);
            var expr = parseNamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new DefaultExpressionClause(keyword, equal, expr);
        }

        return null;
    }

    private MakeSeriesByClause parseMakeSeriesByClause()
    {
        var keyword = parseToken(SyntaxKind.ByKeyword);
        if (keyword != null)
        {
            var expressions = parseCommaList(FnParseNamedExpression, this::createMissingExpression, null, true);
            return new MakeSeriesByClause(keyword, expressions);
        }

        return null;
    }

    private MakeSeriesExpression parseMakeSeriesExpression()
    {
        var expr = parseNamedExpression();
        if (expr != null)
        {
            var defClause = parseDefaultExpressionClause();
            return new MakeSeriesExpression(expr, defClause);
        }

        return null;
    }

    private static final Function<QueryParser, MakeSeriesExpression> FnParseMakeSeriesExpression =
        qp -> qp.parseMakeSeriesExpression();

    private MakeSeriesExpression createMissingMakeSeriesExpression()
    {
        return new MakeSeriesExpression(createMissingExpression(), null);
    }

    private static final Map<String, QueryOperatorParameter> s_makeSeriesOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.MakeSeriesParameters);

    private static final List<SyntaxKind> s_makeSeriesExpressionListEnds =
        Arrays.asList(SyntaxKind.OnKeyword, SyntaxKind.FromKeyword, SyntaxKind.InKeyword);

    private static final Predicate<QueryParser> FnScanMakeSeriesExpressionListEnd =
        qp -> qp.scanCustomListEnd(s_makeSeriesExpressionListEnds);

    private MakeSeriesRangeClause parseRequiredMakeSeriesInRangeClause()
    {
        var inKeyword = parseRequiredToken(SyntaxKind.InKeyword);
        var rangeKeyword = parseRequiredToken(SyntaxKind.RangeKeyword);
        var open = parseRequiredToken(SyntaxKind.OpenParenToken);
        var expressions = parseCommaList(FnParseNamedExpression, this::createMissingExpression, FnScanCommonListEnd);
        var close = parseRequiredToken(SyntaxKind.CloseParenToken);
        return new MakeSeriesInRangeClause(inKeyword, rangeKeyword, new ExpressionList(open, expressions, close));
    }

    private MakeSeriesFromClause parseMakeSeriesFromClause()
    {
        var keyword = parseToken(SyntaxKind.FromKeyword);
        if (keyword != null)
        {
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new MakeSeriesFromClause(keyword, expr);
        }

        return null;
    }

    private MakeSeriesToClause parseMakeSeriesToClause()
    {
        var keyword = parseToken(SyntaxKind.ToKeyword);
        if (keyword != null)
        {
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new MakeSeriesToClause(keyword, expr);
        }

        return null;
    }

    private MakeSeriesStepClause parseRequiredMakeSeriesStepClause()
    {
        var keyword = parseRequiredToken(SyntaxKind.StepKeyword);
        var expr = parseUnnamedExpression();
        if (expr == null)
            expr = createMissingExpression();
        return new MakeSeriesStepClause(keyword, expr);
    }

    private MakeSeriesRangeClause parseMakeSeriesFromToStepClause()
    {
        var kind = peekToken().kind();
        if (kind == SyntaxKind.FromKeyword || kind == SyntaxKind.ToKeyword || kind == SyntaxKind.StepKeyword)
        {
            var fromClause = parseMakeSeriesFromClause();
            var toClause = parseMakeSeriesToClause();
            var stepClause = parseRequiredMakeSeriesStepClause();
            return new MakeSeriesFromToStepClause(fromClause, toClause, stepClause);
        }

        return null;
    }

    private MakeSeriesOperator parseMakeSeriesOperator()
    {
        var keyword = parseToken(SyntaxKind.MakeSeriesKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_makeSeriesOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expressions = parseCommaList(FnParseMakeSeriesExpression, this::createMissingMakeSeriesExpression, FnScanMakeSeriesExpressionListEnd, true);
            var onKeyword = parseRequiredToken(SyntaxKind.OnKeyword); // PORT: §3.14 ?? (argument evaluation order kept)
            var onExpr = parseNamedExpression();
            if (onExpr == null)
                onExpr = createMissingExpression();
            var onClause = new MakeSeriesOnClause(onKeyword, onExpr);
            var rangeClause = parseMakeSeriesFromToStepClause();
            if (rangeClause == null)
                rangeClause = parseRequiredMakeSeriesInRangeClause();
            var byClause = parseMakeSeriesByClause();
            return new MakeSeriesOperator(keyword, parameters, expressions, onClause, rangeClause, byClause);
        }

        return null;
    }

    // endregion
    // region mv-expand

    private ToTypeOfClause parseToTypeOfClause()
    {
        var keyword = parseToken(SyntaxKind.ToKeyword);
        if (keyword != null)
        {
            var typeOfLiteral = parseTypeOfLiteral(); // PORT: §3.14 ??
            if (typeOfLiteral == null)
                typeOfLiteral = createMissingTypeOfLiteral();
            var typeOf = (TypeOfLiteralExpression)typeOfLiteral;
            return new ToTypeOfClause(keyword, typeOf);
        }

        return null;
    }

    private MvExpandExpression parseMvExpandExpression()
    {
        if (peekToken().kind() == SyntaxKind.ToKeyword)
        {
            return new MvExpandExpression(createMissingExpression(), parseToTypeOfClause());
        }

        var expr = parseNamedExpression();
        if (expr != null)
        {
            var typeOf = parseToTypeOfClause();
            return new MvExpandExpression(expr, typeOf);
        }

        return null;
    }

    private static final Function<QueryParser, MvExpandExpression> FnParseMvExpandExpression =
        qp -> qp.parseMvExpandExpression();

    private MvExpandExpression createMissingMvExpandExpression()
    {
        return new MvExpandExpression(createMissingExpression(), null);
    }

    private static final List<SyntaxKind> s_mvExpandExpressionListEnd =
        Arrays.asList(SyntaxKind.LimitKeyword);

    private static final Predicate<QueryParser> FnScanMvExpandExpressionListEnd =
        qp -> qp.scanCustomListEnd(s_mvExpandExpressionListEnd);

    private SyntaxList1<SeparatedElement1<MvExpandExpression>> parseMvExpandExpressionList()
    {
        return parseCommaList(FnParseMvExpandExpression, this::createMissingMvExpandExpression, FnScanMvExpandExpressionListEnd, true);
    }

    private MvExpandRowLimitClause parseMvExpandRowLimitClause()
    {
        var keyword = parseToken(SyntaxKind.LimitKeyword);
        if (keyword != null)
        {
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new MvExpandRowLimitClause(keyword, expr);
        }

        return null;
    }

    private static final Map<String, QueryOperatorParameter> s_mvExpandOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.MvExpandParameters);

    private MvExpandOperator parseMvExpandOperator()
    {
        var keyword = parseToken(SyntaxKind.MvExpandKeyword);
        if (keyword == null)
            keyword = parseToken(SyntaxKind.MvDashExpandKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_mvExpandOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expressions = parseMvExpandExpressionList();
            var rowLimit = parseMvExpandRowLimitClause();
            return new MvExpandOperator(keyword, parameters, expressions, rowLimit);
        }

        return null;
    }

    // endregion
    // region mv-apply

    private MvApplyExpression parseMvApplyExpression()
    {
        if (peekToken().kind() == SyntaxKind.ToKeyword)
        {
            var toTypeOf = parseToTypeOfClause();
            return new MvApplyExpression(createMissingExpression(), toTypeOf);
        }

        var expr = parseNamedExpression();
        if (expr != null)
        {
            var toTypeOf = parseToTypeOfClause();
            return new MvApplyExpression(expr, toTypeOf);
        }

        return null;
    }

    private static final Function<QueryParser, MvApplyExpression> FnParseMvApplyExpression =
        qp -> qp.parseMvApplyExpression();

    private MvApplyExpression createMissingMvApplyExpression()
    {
        return new MvApplyExpression(createMissingExpression(), null);
    }

    private static final List<SyntaxKind> s_mvApplyExpressionListEnd =
        Arrays.asList(SyntaxKind.LimitKeyword, SyntaxKind.IdKeyword, SyntaxKind.OnKeyword);

    private boolean scanMvApplyExpressionListEnd()
    {
        // don't allow use of one of the expected sub-clause keywords as a expression name
        // unless it is is obvious it is part of the expression
        if (scanCustomListEnd(s_mvApplyExpressionListEnd))
        {
            if (peekToken(1) instanceof LexicalToken nextToken
               && nextToken.kind() != SyntaxKind.CommaToken
               && nextToken.kind() != SyntaxKind.ToKeyword
               && nextToken.kind() != SyntaxKind.EqualToken)
            {
                return true;
            }
        }

        return scanCommonListEnd();
    }

    private static final Predicate<QueryParser> FnScanMvApplyExpressionListEnd =
        qp -> qp.scanMvApplyExpressionListEnd();

    private SyntaxList1<SeparatedElement1<MvApplyExpression>> parseMvApplyExpressionList()
    {
        if (peekToken().kind() == SyntaxKind.ToKeyword)
        {
            var position = getResetPoint();

            // if only one item that is just "to typeof(xxx)" then allow expression to be null w/o error
            var clause = parseToTypeOfClause();
            if (peekToken().kind() != SyntaxKind.CommaToken)
            {
                return new SyntaxList1<SeparatedElement1<MvApplyExpression>>(Arrays.asList(
                    new SeparatedElement1<MvApplyExpression>(new MvApplyExpression(null, clause))));
            }
            else
            {
                reset(position);
            }
        }

        return parseCommaList(FnParseMvApplyExpression, this::createMissingMvApplyExpression, FnScanMvApplyExpressionListEnd, true);
    }

    private MvApplyRowLimitClause parseMvApplyRowLimitClause()
    {
        var keyword = parseToken(SyntaxKind.LimitKeyword);
        if (keyword != null)
        {
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new MvApplyRowLimitClause(keyword, expr);
        }

        return null;
    }

    private MvApplyContextIdClause parseMvApplyContextIdClause()
    {
        var keyword = parseToken(SyntaxKind.IdKeyword);
        if (keyword != null)
        {
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new MvApplyContextIdClause(keyword, expr);
        }

        return null;
    }

    private MvApplySubqueryExpression parseMvApplySubqueryExpression()
    {
        var open = parseToken(SyntaxKind.OpenParenToken);
        if (open != null)
        {
            var expr = parseContextualSubExpression();
            if (expr == null)
                expr = createMissingExpression();
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new MvApplySubqueryExpression(open, expr, close);
        }

        return null;
    }

    private static final Map<String, QueryOperatorParameter> s_mvApplyOperatorParmeterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.MvApplyParameters);

    private MvApplySubqueryExpression createMissingMvApplySubqueryExpression()
    {
        return new MvApplySubqueryExpression(
            createMissingToken(SyntaxKind.OpenParenToken),
            createMissingExpression(),
            createMissingToken(SyntaxKind.CloseParenToken));
    }

    private MvApplyOperator parseMvApplyOperator()
    {
        var keyword = parseToken(SyntaxKind.MvApplyKeyword);
        if (keyword == null)
            keyword = parseToken(SyntaxKind.MvDashApplyKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_mvApplyOperatorParmeterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expressions = parseMvApplyExpressionList();
            var rowLimit = parseMvApplyRowLimitClause();
            var idClause = parseMvApplyContextIdClause();
            var onKeyword = parseRequiredToken(SyntaxKind.OnKeyword);
            var subquery = parseMvApplySubqueryExpression();
            if (subquery == null)
                subquery = createMissingMvApplySubqueryExpression();
            return new MvApplyOperator(keyword, parameters, expressions, rowLimit, idClause, onKeyword, subquery);
        }

        return null;
    }

    // endregion
    // region evaluate

    private EvaluateSchemaClause parseEvaluateSchemaClause()
    {
        var colon = parseToken(SyntaxKind.ColonToken);
        if (colon != null)
        {
            var schema = parseEvaluateRowSchema();
            if (schema == null)
                schema = CreateMissingEvaluateRowSchema.get();
            return new EvaluateSchemaClause(colon, schema);
        }

        return null;
    }

    private static final Map<String, QueryOperatorParameter> s_evaluateOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.EvaluateParameters);

    private EvaluateOperator parseEvaluateOperator()
    {
        var keyword = parseToken(SyntaxKind.EvaluateKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_evaluateOperatorParameterMap);
            var functionCall = parseRequiredFunctionCallExpression();
            var schema = parseEvaluateSchemaClause();
            return new EvaluateOperator(keyword, parameters, functionCall, schema);
        }

        return null;
    }

    // endregion
    // region parse / parse-where

    private SyntaxNode parseParseWithExpression()
    {
        SyntaxNode result = parseStarExpression();
        if (result == null)
            result = parseStringOrCompoundStringLiteral();
        if (result == null)
            result = parseNameAndOptionalTypeDeclaration();
        return result;
    }

    private static final Function<QueryParser, SyntaxNode> FnParseParseWithExpression =
        qp -> qp.parseParseWithExpression();

    private static final Map<String, QueryOperatorParameter> s_parseOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.ParseParameters);

    private ParseOperator parseParseOperator()
    {
        var keyword = parseToken(SyntaxKind.ParseKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_parseOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            var with = parseRequiredToken(SyntaxKind.WithKeyword);
            var withExprs = parseList(FnParseParseWithExpression);
            return new ParseOperator(keyword, parameters, expr, with, withExprs);
        }

        return null;
    }

    private ParseWhereOperator parseParseWhereOperator()
    {
        var keyword = parseToken(SyntaxKind.ParseWhereKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_parseOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            var with = parseRequiredToken(SyntaxKind.WithKeyword);
            var withExprs = parseList(FnParseParseWithExpression);
            return new ParseWhereOperator(keyword, parameters, expr, with, withExprs);
        }

        return null;
    }

    // endregion
    // region parse-kv
    private static final Map<String, QueryOperatorParameter> s_parseKvOperatorWithParametersMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.ParseKvWithProperties);

    private ParseKvWithClause parseParseKvWithClause()
    {
        var keyword = parseToken(SyntaxKind.WithKeyword);
        if (keyword != null)
        {
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var props = parseQueryOperatorParameterCommaList(s_parseKvOperatorWithParametersMap, null, AllowedNameKind.DeclaredOnly);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new ParseKvWithClause(keyword, open, props, close);
        }

        return null;
    }

    private ParseKvOperator parseParseKvOperator()
    {
        var keyword = parseToken(SyntaxKind.ParseKvKeyword);
        if (keyword != null)
        {
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            var asKeyword = parseRequiredToken(SyntaxKind.AsKeyword);
            var keys = parseRowSchema();
            if (keys == null)
                keys = CreateMissingRowSchema.get();
            var withClause = parseParseKvWithClause();
            return new ParseKvOperator(keyword, expr, asKeyword, keys, withClause);
        }

        return null;
    }
    // endregion
    // region project / project-rename / project-away / project-keep / project-reorder

    private ProjectOperator parseProjectOperator()
    {
        var keyword = parseToken(SyntaxKind.ProjectKeyword);
        if (keyword != null)
        {
            var expressions = parseCommaList(FnParseNamedExpression, this::createMissingExpression, FnScanCommonListEnd);
            return new ProjectOperator(keyword, expressions);
        }

        return null;
    }

    private ProjectRenameOperator parseProjectRenameOperator()
    {
        var keyword = parseToken(SyntaxKind.ProjectRenameKeyword);
        if (keyword != null)
        {
            var expressions = parseCommaList(FnParseNamedExpression, this::createMissingExpression, FnScanCommonListEnd);
            return new ProjectRenameOperator(keyword, expressions);
        }

        return null;
    }

    private static final Function<QueryParser, Expression> FnParseSimpleOrWildcardedNameReferenceExpression =
        qp ->
        {
            Expression name = qp.parseWildcardedNameReference(); // PORT: §3.14 ??
            return name != null ? name : qp.parseNameReference();
        };

    private ProjectAwayOperator parseProjectAwayOperator()
    {
        var keyword = parseToken(SyntaxKind.ProjectAwayKeyword);
        if (keyword != null)
        {
            var expressions = parseCommaList(FnParseSimpleOrWildcardedNameReferenceExpression, this::createMissingExpression, FnScanCommonListEnd);
            return new ProjectAwayOperator(keyword, expressions);
        }

        return null;
    }

    private ProjectKeepOperator parseProjectKeepOperator()
    {
        var keyword = parseToken(SyntaxKind.ProjectKeepKeyword);
        if (keyword != null)
        {
            var expressions = parseCommaList(FnParseSimpleOrWildcardedNameReferenceExpression, this::createMissingExpression, FnScanCommonListEnd);
            return new ProjectKeepOperator(keyword, expressions);
        }

        return null;
    }

    private Expression parseProjectReorderExpression()
    {
        Expression expr = parseWildcardedNameReference();
        if (expr == null)
            expr = parseNameReference();
        if (expr != null)
        {
            var kind = peekToken().kind();
            if (kind == SyntaxKind.AscKeyword
                || kind == SyntaxKind.DescKeyword
                || kind == SyntaxKind.GrannyAscKeyword
                || kind == SyntaxKind.GrannyDescKeyword)
            {
                expr = new OrderedExpression(expr, parseRequiredOrderingNoNullsClause());
            }
        }

        return expr;
    }

    private static final Function<QueryParser, Expression> FnParseProjectReorderExpression =
        qp -> qp.parseProjectReorderExpression();

    private ProjectReorderOperator parseProjectReorderOperator()
    {
        var keyword = parseToken(SyntaxKind.ProjectReorderKeyword);
        if (keyword != null)
        {
            var expressions = parseCommaList(FnParseProjectReorderExpression, this::createMissingExpression, FnScanCommonListEnd);
            return new ProjectReorderOperator(keyword, expressions);
        }

        return null;
    }

    private ProjectByNamesOperator parseProjectByNamesOperator()
    {
        var keyword = parseToken(SyntaxKind.ProjectByNamesKeyword);
        if (keyword != null)
        {
            var expressions = parseCommaList(FnParseUnnamedExpression, this::createMissingExpression, FnScanCommonListEnd);
            return new ProjectByNamesOperator(keyword, expressions);
        }

        return null;
    }

    // endregion
    // region sample / sample-distinct

    private static final Map<String, QueryOperatorParameter> s_sampleOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.SampleParameters);

    private SampleOperator parseSampleOperator()
    {
        var keyword = parseToken(SyntaxKind.SampleKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_sampleOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expr = parseNamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new SampleOperator(keyword, parameters, expr);
        }

        return null;
    }

    private static final Map<String, QueryOperatorParameter> s_sampleDistinctOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.SampleDistinctParameters);

    private SampleDistinctOperator parseSampleDistinctOperator()
    {
        var keyword = parseToken(SyntaxKind.SampleDistinctKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_sampleDistinctOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expr = parseNamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            var ofKeyword = parseRequiredToken(SyntaxKind.OfKeyword);
            var ofExpr = parseNamedExpression();
            if (ofExpr == null)
                ofExpr = createMissingExpression();
            return new SampleDistinctOperator(keyword, parameters, expr, ofKeyword, ofExpr);
        }

        return null;
    }

    // endregion
    // region reduce

    private static final Map<String, QueryOperatorParameter> s_reduceOperatorWithParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.ReduceWithParameters);

    private ReduceByWithClause parseReduceByWithClause()
    {
        var keyword = parseToken(SyntaxKind.WithKeyword);
        if (keyword != null)
        {
            var withParameters = parseQueryOperatorParameterCommaList(s_reduceOperatorWithParameterMap);
            return new ReduceByWithClause(keyword, withParameters);
        }

        return null;
    }

    // PORT-BUG: the reduce operator parameter map is built from ReduceWithParameters (the with-clause set), same as s_reduceOperatorWithParameterMap; mirrored
    private static final Map<String, QueryOperatorParameter> s_reduceOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.ReduceWithParameters);

    private ReduceByOperator parseReduceByOperator()
    {
        var keyword = parseToken(SyntaxKind.ReduceKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_reduceOperatorParameterMap);
            var byKeyword = parseRequiredToken(SyntaxKind.ByKeyword);
            var expr = parseNamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            var withClause = parseReduceByWithClause();
            return new ReduceByOperator(keyword, parameters, byKeyword, expr, withClause);
        }

        return null;
    }

    // endregion
    // region summarize

    private NamedExpression parseSummarizeByBinClause()
    {
        // this is support for legacy syntax
        if (peekToken().kind() == SyntaxKind.BinKeyword
            && peekToken(1).kind() == SyntaxKind.EqualToken)
        {
            var keyword = parseToken(SyntaxKind.BinKeyword);
            var equal = parseToken(SyntaxKind.EqualToken);
            var value = parseUnnamedExpression();
            if (value == null)
                value = createMissingExpression();
            return new SimpleNamedExpression(
                new NameDeclaration(new TokenName(keyword)),
                equal,
                value);
        }

        return null;
    }

    private boolean scanSummarizeByClauseExpressionListEnd()
    {
        // don't consume expression if it looks like legacy bin=value syntax
        if (peekToken().kind() == SyntaxKind.BinKeyword
            && peekToken(1).kind() == SyntaxKind.EqualToken
            && SyntaxFacts.isLiteral(peekToken(2).kind()))
            return true;
        return scanCommonListEnd();
    }

    private static final Predicate<QueryParser> FnScanSummarizeByClauseExpressionListEnd =
        qp -> qp.scanSummarizeByClauseExpressionListEnd();

    private SummarizeByClause parseSummarizeByClause()
    {
        var keyword = parseToken(SyntaxKind.ByKeyword);
        if (keyword != null)
        {
            var expressions = parseCommaList(FnParseNamedExpression, this::createMissingExpression, FnScanSummarizeByClauseExpressionListEnd, true);
            var binClause = parseSummarizeByBinClause();
            return new SummarizeByClause(keyword, expressions, binClause);
        }

        return null;
    }


    private static final Map<String, QueryOperatorParameter> s_summarizeOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.SummarizeParameters);

    private static final Predicate<QueryParser> FnScanSummarizeExpressionListEnd =
        qp -> qp.scanCustomListEnd(SyntaxKind.ByKeyword);

    private SummarizeOperator parseSummarizeOperator()
    {
        var keyword = parseToken(SyntaxKind.SummarizeKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_summarizeOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expressions = parseCommaList(FnParseNamedExpression, this::createMissingExpression, FnScanSummarizeExpressionListEnd);
            var byClause = parseSummarizeByClause();
            return new SummarizeOperator(keyword, parameters, expressions, byClause);
        }

        return null;
    }

    // endregion
    // region distinct

    private Expression parseDistinctExpression()
    {
        Expression result = parseStarExpression();
        if (result == null)
            result = parseNamedExpression();
        return result;
    }

    private static final Function<QueryParser, Expression> FnParseDistinctExpression =
        qp -> qp.parseDistinctExpression();

    private static final Map<String, QueryOperatorParameter> s_distinctOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.DistinctParameters);

    private DistinctOperator parseDistinctOperator()
    {
        var keyword = parseToken(SyntaxKind.DistinctKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_distinctOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expressions = parseCommaList(FnParseDistinctExpression, this::createMissingExpression, FnScanCommonListEnd, true);
            return new DistinctOperator(keyword, parameters, expressions);
        }

        return null;
    }

    // endregion
    // region take

    private static final Map<String, QueryOperatorParameter> s_takeOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.TakeParameters);

    private TakeOperator parseTakeOperator()
    {
        var keyword = parseToken(SyntaxKind.TakeKeyword);
        if (keyword == null)
            keyword = parseToken(SyntaxKind.LimitKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_takeOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expr = parseNamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new TakeOperator(keyword, parameters, expr);
        }

        return null;
    }

    // endregion
    // region order / sort

    private SyntaxToken createMissingFirstOrLastToken()
    {
        return SyntaxToken.missing("", SyntaxKind.FirstKeyword, Arrays.asList(DiagnosticFacts.getMissingFirstOrLast()));
    }

    private OrderingNullsClause parseOrderingNullsClause()
    {
        var keyword = parseToken(SyntaxKind.NullsKeyword);
        if (keyword != null)
        {
            var firstOrLast = parseToken(SyntaxKind.FirstKeyword);
            if (firstOrLast == null)
                firstOrLast = parseToken(SyntaxKind.LastKeyword);
            if (firstOrLast == null)
                firstOrLast = createMissingFirstOrLastToken();
            return new OrderingNullsClause(keyword, firstOrLast);
        }

        return null;
    }

    private OrderingClause parseRequiredOrderingClause()
    {
        var keyword = parseToken(SyntaxKind.AscKeyword);
        if (keyword == null)
            keyword = parseToken(SyntaxKind.DescKeyword);
        if (keyword == null)
            keyword = parseToken(SyntaxKind.GrannyAscKeyword);
        if (keyword == null)
            keyword = parseToken(SyntaxKind.GrannyDescKeyword);
        var nulls = parseOrderingNullsClause();
        return new OrderingClause(keyword, nulls);
    }

    private OrderingClause parseRequiredOrderingNoNullsClause()
    {
        var keyword = parseToken(SyntaxKind.AscKeyword);
        if (keyword == null)
            keyword = parseToken(SyntaxKind.DescKeyword);
        if (keyword == null)
            keyword = parseToken(SyntaxKind.GrannyAscKeyword);
        if (keyword == null)
            keyword = parseToken(SyntaxKind.GrannyDescKeyword);
        return new OrderingClause(keyword, null);
    }

    private Expression parseOrderedExpression()
    {
        var expr = parseNamedExpression();
        if (expr != null)
        {
            var kind = peekToken().kind();
            switch (kind)
            {
                case SyntaxKind.AscKeyword:
                case SyntaxKind.DescKeyword:
                case SyntaxKind.GrannyAscKeyword:
                case SyntaxKind.GrannyDescKeyword:
                case SyntaxKind.NullsKeyword:
                    expr = new OrderedExpression(expr, parseRequiredOrderingClause());
                    break;
            }
        }

        return expr;
    }

    private static final Function<QueryParser, Expression> FnParseOrderedExpression =
        qp -> qp.parseOrderedExpression();

    private static final Map<String, QueryOperatorParameter> s_sortOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.SortParameters);

    private SortOperator parseSortOperator()
    {
        var keyword = parseToken(SyntaxKind.OrderKeyword);
        if (keyword == null)
            keyword = parseToken(SyntaxKind.SortKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_sortOperatorParameterMap);
            var byKeyword = parseRequiredToken(SyntaxKind.ByKeyword);
            var expressions = parseCommaList(FnParseOrderedExpression, this::createMissingExpression, FnScanCommonListEnd, true);
            return new SortOperator(keyword, parameters, byKeyword, expressions);
        }

        return null;
    }

    // endregion
    // region scan

    private ScanAssignment parseScanAssignment()
    {
        var name = parseExtendedNameReference();
        if (name != null)
        {
            var equal = parseRequiredToken(SyntaxKind.EqualToken);
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new ScanAssignment(name, equal, expr);
        }

        return null;
    }

    private static final Function<QueryParser, ScanAssignment> FnParseScanAssignment =
        qp -> qp.parseScanAssignment();

    private static final Supplier<ScanAssignment> CreateMissingScanAssignment = () ->
        new ScanAssignment(
            new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
            SyntaxToken.missing(SyntaxKind.EqualEqualToken),
            new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
            Arrays.asList(DiagnosticFacts.getMissingName()));

    private ScanComputationClause parseScanComputationClause()
    {
        var arrow = parseToken(SyntaxKind.FatArrowToken);
        if (arrow != null)
        {
            var assignments = parseCommaList(FnParseScanAssignment, CreateMissingScanAssignment, FnScanCommonListEnd, true);
            return new ScanComputationClause(arrow, assignments);
        }

        return null;
    }

    private ScanStepOutput parseScanStepOutput()
    {
        var output = parseToken(SyntaxKind.OutputKeyword);
        if (output != null)
        {
            var equality = parseRequiredToken(SyntaxKind.EqualToken);
            var outputKind = parseRequiredTokenText(KustoFacts.ScanStepOutputValues);
            return new ScanStepOutput(output, equality, outputKind);
        }

        return null;
    }

    private ScanStep parseScanStep()
    {
        var keyword = parseToken(SyntaxKind.StepKeyword);
        if (keyword != null)
        {
            var name = parseExtendedNameDeclaration();
            if (name == null)
                name = createMissingNameDeclaration();
            var optional = parseToken(SyntaxKind.OptionalKeyword);
            var output = parseScanStepOutput();
            var colon = parseRequiredToken(SyntaxKind.ColonToken);
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            var computation = parseScanComputationClause();
            var semicolon = parseRequiredToken(SyntaxKind.SemicolonToken);
            return new ScanStep(keyword, name, optional, output, colon, expr, computation, semicolon);
        }

        return null;
    }

    private static final Function<QueryParser, ScanStep> FnParseScanStep =
        qp -> qp.parseScanStep();

    private static final List<SyntaxKind> s_scanListEnd =
        Arrays.asList(SyntaxKind.PartitionKeyword, SyntaxKind.OrderKeyword, SyntaxKind.ByKeyword, SyntaxKind.DeclareKeyword, SyntaxKind.WithKeyword);

    private static final Predicate<QueryParser> FnScanScanListEnd =
        qp -> qp.scanCustomListEnd(s_scanListEnd);

    private ScanOrderByClause parseScanOrderByClause()
    {
        var keyword = parseToken(SyntaxKind.OrderKeyword);
        if (keyword != null)
        {
            var byKeyword = parseRequiredToken(SyntaxKind.ByKeyword);
            var expressions = parseCommaList(FnParseOrderedExpression, this::createMissingExpression, FnScanScanListEnd, true);
            return new ScanOrderByClause(keyword, byKeyword, expressions);
        }

        return null;
    }

    private ScanPartitionByClause parseScanPartitionByClause()
    {
        var keyword = parseToken(SyntaxKind.PartitionKeyword);
        if (keyword != null)
        {
            var byKeyword = parseRequiredToken(SyntaxKind.ByKeyword);
            var expressions = parseCommaList(FnParseUnnamedExpression, this::createMissingExpression, FnScanScanListEnd, true);
            return new ScanPartitionByClause(keyword, byKeyword, expressions);
        }

        return null;
    }

    private ScanDeclareClause parseScanDeclareClause()
    {
        var keyword = parseToken(SyntaxKind.DeclareKeyword);
        if (keyword != null)
        {
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var declarations = parseCommaList(FnParseFunctionParameter, CreateMissingFunctionParameter, FnScanCommonListEnd);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new ScanDeclareClause(keyword, open, declarations, close);
        }

        return null;
    }

    private static final Map<String, QueryOperatorParameter> s_scanOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.ScanParameters);

    private ScanOperator parseScanOperator()
    {
        var keyword = parseToken(SyntaxKind.ScanKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_scanOperatorParameterMap);
            var order = parseScanOrderByClause();
            var partition = parseScanPartitionByClause();
            var declare = parseScanDeclareClause();
            var with = parseRequiredToken(SyntaxKind.WithKeyword);
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var steps = parseList(FnParseScanStep, null, FnScanCommonListEnd);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new ScanOperator(keyword, parameters, order, partition, declare, with, open, steps, close);
        }

        return null;
    }

    private PartitionByOperator parsePartitionByOperator()
    {
        if (parseToken(SyntaxKind.PartitionByKeyword) instanceof SyntaxToken keyword)
        {
            var parameters = parseQueryOperatorParameterList(s_partitionByParameters);
            Expression entity = parseSimplePathExpression();
            if (entity == null)
                entity = createMissingNameReference();
            var idClause = parsePartitionByIdClause();
            var openParen = parseRequiredToken(SyntaxKind.OpenParenToken);
            var subQuery = parseContextualSubExpression();
            if (subQuery == null)
                subQuery = createMissingExpression();
            var closeParen = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new PartitionByOperator(keyword, parameters, entity, idClause, openParen, subQuery, closeParen);
        }

        return null;
    }

    private static final Map<String, QueryOperatorParameter> s_partitionByParameters =
        createQueryOperatorParameterMap(QueryOperatorParameters.PartitionByParameters);

    private PartitionByIdClause parsePartitionByIdClause()
    {
        if (parseToken(SyntaxKind.IdKeyword) instanceof SyntaxToken keyword)
        {
            var value = parseLiteral();
            if (value == null)
                value = CreateMissingValue.get();
            return new PartitionByIdClause(keyword, value);
        }

        return null;
    }

    // endregion
    // region top / top-nested / top-hitters

    private TopHittersByClause parseTopHittersByClause()
    {
        var keyword = parseToken(SyntaxKind.ByKeyword);
        if (keyword != null)
        {
            var expr = parseNamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new TopHittersByClause(keyword, expr);
        }

        return null;
    }

    private TopHittersOperator parseTopHittersOperator()
    {
        var keyword = parseToken(SyntaxKind.TopHittersKeyword);
        if (keyword != null)
        {
            var expr = parseNamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            var ofKeyword = parseRequiredToken(SyntaxKind.OfKeyword);
            var ofExpr = parseNamedExpression();
            if (ofExpr == null)
                ofExpr = createMissingExpression();
            var byClause = parseTopHittersByClause();
            return new TopHittersOperator(keyword, expr, ofKeyword, ofExpr, byClause);
        }

        return null;
    }

    private static final Map<String, QueryOperatorParameter> s_topOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.TopParameters);

    private TopOperator parseTopOperator()
    {
        var keyword = parseToken(SyntaxKind.TopKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_topOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expr = parseNamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            var byKeyword = parseRequiredToken(SyntaxKind.ByKeyword);
            var byExpr = parseOrderedExpression();
            if (byExpr == null)
                byExpr = createMissingExpression();
            return new TopOperator(keyword, parameters, expr, byKeyword, byExpr);
        }

        return null;
    }

    private TopNestedWithOthersClause parseTopNestedWithOthersClause()
    {
        var keyword = parseToken(SyntaxKind.WithKeyword);
        if (keyword != null)
        {
            var others = parseRequiredToken(SyntaxKind.OthersKeyword);
            var equal = parseRequiredToken(SyntaxKind.EqualToken);
            var value = parseLiteral();
            if (value == null)
                value = parseUnnamedExpression();
            if (value == null)
                value = createMissingExpression();
            return new TopNestedWithOthersClause(keyword, others, equal, value);
        }

        return null;
    }

    private TopNestedClause parseTopNestedClause()
    {
        var keyword = parseToken(SyntaxKind.TopNestedKeyword);
        if (keyword != null)
        {
            var expr = parseNamedExpression();
            var ofKeyword = parseRequiredToken(SyntaxKind.OfKeyword);
            var ofExpr = parseNamedExpression();
            if (ofExpr == null)
                ofExpr = createMissingExpression();
            var withClause = parseTopNestedWithOthersClause();
            var byKeyword = parseRequiredToken(SyntaxKind.ByKeyword);
            var byExpr = parseOrderedExpression();
            if (byExpr == null)
                byExpr = createMissingExpression();
            return new TopNestedClause(keyword, expr, ofKeyword, ofExpr, withClause, byKeyword, byExpr);
        }

        return null;
    }

    private static final Function<QueryParser, TopNestedClause> FnParseTopNestedClause =
        qp -> qp.parseTopNestedClause();

    private static final Supplier<TopNestedClause> CreateMissingTopNestedClause = () ->
        new TopNestedClause(
            SyntaxToken.missing(SyntaxKind.TopNestedKeyword),
            null,
            SyntaxToken.missing(SyntaxKind.OfKeyword),
            new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
            null,
            SyntaxToken.missing(SyntaxKind.ByKeyword),
            new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
            Arrays.asList(DiagnosticFacts.getMissingClause())
            );

    private TopNestedOperator parseTopNestedOperator()
    {
        if (peekToken().kind() == SyntaxKind.TopNestedKeyword)
        {
            var clauses = parseCommaList(FnParseTopNestedClause, CreateMissingTopNestedClause, FnScanCommonListEnd, true);
            return new TopNestedOperator(clauses);
        }

        return null;
    }

    // endregion
    // region union

    private static List<Function<QueryParser, Expression>> ParseUnionExpressionParsers =
        Arrays.asList(
            FnParseBracketedEntityNamePathElementSelector,
            FnParseWildcardedEntityExpression,
            FnParseBarePathElementSelector);

    private Expression parseUnionExpression()
    {
        Expression result = parseParenthesizedExpression();
        if (result == null)
            result = parseBest(ParseUnionExpressionParsers);
        return result;
    }

    private static final Function<QueryParser, Expression> FnParseUnionExpression =
        qp -> qp.parseUnionExpression();

    private static final Map<String, QueryOperatorParameter> s_unionOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.UnionParameters);

    private UnionOperator parseUnionOperator()
    {
        var keyword = parseToken(SyntaxKind.UnionKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_unionOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expressions = parseCommaList(FnParseUnionExpression, this::createMissingExpression, FnScanCommonListEnd, true);
            return new UnionOperator(keyword, parameters, expressions);
        }

        return null;
    }

    // endregion
    // region as

    private static final Map<String, QueryOperatorParameter> s_asOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.AsParameters);

    private AsOperator parseAsOperator()
    {
        var keyword = parseToken(SyntaxKind.AsKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_asOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var name = parseNameDeclaration();
            if (name == null)
                name = createMissingNameDeclaration();
            return new AsOperator(keyword, parameters, name);
        }

        return null;
    }

    // endregion
    // region serialize

    private static final Map<String, QueryOperatorParameter> s_serializeOperatorParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.SerializedParameters);

    private SerializeOperator parseSerializeOperator()
    {
        var keyword = parseToken(SyntaxKind.SerializeKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_serializeOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var expressions = parseCommaList(FnParseNamedExpression, this::createMissingExpression, FnScanCommonListEnd);
            return new SerializeOperator(keyword, parameters, expressions);
        }

        return null;
    }

    // endregion
    // region range

    private RangeOperator parseRangeOperator()
    {
        // don't parse as range operator if it looks like the range function
        if (peekToken().kind() == SyntaxKind.RangeKeyword && peekToken(1).kind() != SyntaxKind.OpenParenToken)
        {
            var keyword = parseToken();
            var name = parseNameDeclaration();
            if (name == null)
                name = createMissingNameDeclaration();
            var fromKeyword = parseRequiredToken(SyntaxKind.FromKeyword);
            var fromExpr = parseUnnamedExpression();
            if (fromExpr == null)
                fromExpr = createMissingExpression();
            var toKeyword = parseRequiredToken(SyntaxKind.ToKeyword);
            var toExpr = parseUnnamedExpression();
            if (toExpr == null)
                toExpr = createMissingExpression();
            var stepKeyword = parseRequiredToken(SyntaxKind.StepKeyword);
            var stepExpr = parseUnnamedExpression();
            if (stepExpr == null)
                stepExpr = createMissingExpression();
            return new RangeOperator(keyword, name, fromKeyword, fromExpr, toKeyword, toExpr, stepKeyword, stepExpr);
        }

        return null;
    }

    // endregion
    // region invoke

    private InvokeOperator parseInvokeOperator()
    {
        var keyword = parseToken(SyntaxKind.InvokeKeyword);
        if (keyword != null)
        {
            var functionCall = parseDotCompositeFunctionCall();
            if (functionCall == null)
                functionCall = createMissingExpression();
            return new InvokeOperator(keyword, functionCall);
        }

        return null;
    }

    // endregion
    // region render

    private static final Map<String, QueryOperatorParameter> s_renderOperatorWithPropertiesMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.RenderWithProperties);

    private RenderWithClause parseRenderWithClause()
    {
        var keyword = parseToken(SyntaxKind.WithKeyword);
        if (keyword != null)
        {
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var leadingComma = parseToken(SyntaxKind.CommaToken); // optional
            var props = parseQueryOperatorParameterCommaList(s_renderOperatorWithPropertiesMap, null, AllowedNameKind.DeclaredOnly);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new RenderWithClause(keyword, open, leadingComma, props, close);
        }

        return null;
    }

    private static final List<String> s_renderDeprecatedParameterNameListEndNames =
        Arrays.asList("kind", "title", "accumulate", "with", "by");

    private static final Predicate<QueryParser> FnRenderDeprecatedParameterNameListEnd =
        qp -> qp.scanToken(s_renderDeprecatedParameterNameListEndNames) > 0;

    private NamedParameter parseDeprecatedRenderProperty()
    {
        NamedParameter result = parseQueryOperatorParameter(QueryOperatorParameters.RenderKind);
        if (result == null)
            result = parseQueryOperatorParameter(QueryOperatorParameters.RenderTitle);
        if (result == null)
            result = parseQueryOperatorParameter(QueryOperatorParameters.RenderAccumulate);
        if (result == null)
            result = (peekToken().kind() == SyntaxKind.WithKeyword && peekToken(1).kind() != SyntaxKind.OpenParenToken
                ? parseQueryOperatorParameter(QueryOperatorParameters.RenderWithDeprecated) : null);
        if (result == null)
            result = parseQueryOperatorParameter(QueryOperatorParameters.RenderByDeprecated, FnRenderDeprecatedParameterNameListEnd);
        return result;
    }

    private static final Function<QueryParser, NamedParameter> FnParseDeprecatedRenderProperty =
        qp -> qp.parseDeprecatedRenderProperty();

    private RenderOperator parseRenderOperator()
    {
        var keyword = parseToken(SyntaxKind.RenderKeyword);
        if (keyword != null)
        {
            var chartType = parseTokenText(KustoFacts.ChartTypes);
            if (chartType == null)
                chartType = parseToken(SyntaxKind.IdentifierToken);
            if (chartType == null)
                chartType = createMissingNameToken(KustoFacts.ChartTypes);
            var parameters = parseList(FnParseDeprecatedRenderProperty);
            var withClause = parseRenderWithClause();
            return new RenderOperator(keyword, chartType, parameters, withClause);
        }

        return null;
    }

    // endregion
    // region print

    private PrintOperator parsePrintOperator()
    {
        var keyword = parseToken(SyntaxKind.PrintKeyword);
        if (keyword != null)
        {
            var expressions = parseCommaList(FnParseNamedExpression, this::createMissingExpression, FnScanCommonListEnd, true);
            return new PrintOperator(keyword, expressions);
        }

        return null;
    }

    // endregion
    // region MacroExpand
    private Expression parseEntityGroupReference()
    {
        var explicitOrFunctionCallEntityGroup = parseEntityGroup();
        if (explicitOrFunctionCallEntityGroup != null)
        {
            return explicitOrFunctionCallEntityGroup;
        }

        if (scanQualifiedEntityStart())
            return parseEntityPathExpression();

        return parseNameReference();
    }

    private MacroExpandScopeReferenceName optionalParseMacroExpandScopeReferenceName()
    {
        var asKeyword = parseToken(SyntaxKind.AsKeyword);
        if (asKeyword != null)
        {
            var scopeReferenceName = parseNameDeclaration();
            if (scopeReferenceName == null)
                scopeReferenceName = createMissingNameDeclaration();
            return new MacroExpandScopeReferenceName(asKeyword, scopeReferenceName);
        }

        return null;
    }

    private MacroExpandOperator parseMacroExpand()
    {
        var keyword = parseToken(SyntaxKind.MacroExpandKeyword);
        if (keyword != null)
        {
            var parameters = parseQueryOperatorParameterList(s_unionOperatorParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var entityGroupExpression = parseEntityGroupReference();
            if (entityGroupExpression == null)
                entityGroupExpression = createMissingExpression();
            var macroExpandScopeReferenceName = optionalParseMacroExpandScopeReferenceName();
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var queryBlocksStatementList = parseQueryBlockStatementList();
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new MacroExpandOperator(keyword, parameters, entityGroupExpression, macroExpandScopeReferenceName, open, queryBlocksStatementList, close);
        }

        return null;
    }
    // endregion
    // region assert-schema
    private AssertSchemaOperator parseAssertSchemaOperator()
    {
        var keyword = parseToken(SyntaxKind.AssertSchemaKeyword);
        if (keyword != null)
        {
            var schema = parseRowSchema();
            if (schema == null)
                schema = CreateMissingRowSchema.get();
            return new AssertSchemaOperator(keyword, schema);
        }

        return null;
    }
    // endregion
    // region make-graph
    private QueryOperator parseMakeGraphOperator()
    {
        if (parseToken(SyntaxKind.MakeGraphKeyword) instanceof SyntaxToken makeGraphKeyword)
        {
            var parameters = parseQueryOperatorParameterList(s_graphMakeParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var sourceColumn = parseNameReference();
            if (sourceColumn == null)
                sourceColumn = createMissingNameReference();
            var directionToken = parseTokenText("-->", SyntaxKind.DashDashGreaterThanToken);
            if (directionToken == null)
                directionToken = parseTokenText("--", SyntaxKind.DashDashToken);
            if (directionToken == null)
                directionToken = CreateMissingDirectionToken.get();
            var targetColumn = parseNameReference();
            if (targetColumn == null)
                targetColumn = createMissingNameReference();
            var withClause = parseMakeGraphWithImplicitIdClause();
            if (withClause == null)
                withClause = parseMakeGraphWithTablesAndKeysClause();
            var partitionedByClause = parseMakeGraphPartitionedByClause();

            return new MakeGraphOperator(makeGraphKeyword, parameters, sourceColumn, directionToken, targetColumn, withClause, partitionedByClause);
        }

        return null;
    }

    private static final Map<String, QueryOperatorParameter> s_graphMakeParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.GraphMakeParameters);

    private static Supplier<SyntaxToken> CreateMissingDirectionToken = () ->
        createMissingToken(Arrays.asList(SyntaxKind.DashDashGreaterThanToken, SyntaxKind.DashDashToken));

    private MakeGraphWithClause parseMakeGraphWithTablesAndKeysClause()
    {
        if (parseToken(SyntaxKind.WithKeyword) instanceof SyntaxToken withKeyword)
        {
            var tablesAndKeys = parseCommaList(FnParseMakeGraphTableAndKeyClause, CreateMissingMakeGraphTableAndKeyClause, FnScanMakeGraphWhereClauseListEnd, true);
            return new MakeGraphWithTablesAndKeysClause(withKeyword, tablesAndKeys);
        }

        return null;
    }

    private static Supplier<MakeGraphTableAndKeyClause> CreateMissingMakeGraphTableAndKeyClause = () ->
        new MakeGraphTableAndKeyClause(
            new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
            SyntaxToken.missing(SyntaxKind.OnKeyword),
            new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
            Arrays.asList(DiagnosticFacts.getMissingExpression())
            );


    private static Function<QueryParser, MakeGraphTableAndKeyClause> FnParseMakeGraphTableAndKeyClause =
        qp -> qp.parseMakeGraphTableAndKeyClause();

    private static final Predicate<QueryParser> FnScanMakeGraphWhereClauseListEnd =
        qp -> qp.scanMakeGraphTableAndKeyClauseListEnd();

    private boolean scanMakeGraphTableAndKeyClauseListEnd(int offset)
    {
        return scanCommonListEnd() || peekToken(offset).kind() == SyntaxKind.PartitionedByKeyword;
    }

    // PORT: §3.12 telescoped overload
    private boolean scanMakeGraphTableAndKeyClauseListEnd()
    {
        return scanMakeGraphTableAndKeyClauseListEnd(0);
    }

    private MakeGraphTableAndKeyClause parseMakeGraphTableAndKeyClause()
    {
        if (parseInvocationExpression() instanceof Expression table)
        {
            var onKeyword = parseRequiredToken(SyntaxKind.OnKeyword);
            var column = parseNameReference();
            if (column == null)
                column = createMissingNameReference();
            return new MakeGraphTableAndKeyClause(table, onKeyword, column);
        }

        return null;
    }

    private MakeGraphWithClause parseMakeGraphWithImplicitIdClause()
    {
        if (peekToken().kind() == SyntaxKind.WithNodeIdKeyword)
        {
            var withNodeIdKeyword = parseToken();
            var equalToken = parseRequiredToken(SyntaxKind.EqualToken);
            var name = parseNameDeclaration();
            if (name == null)
                name = createMissingNameDeclaration();
            return new MakeGraphWithImplicitIdClause(withNodeIdKeyword, equalToken, name);
        }
        return null;
    }

    private MakeGraphPartitionedByClause parseMakeGraphPartitionedByClause()
    {
        if (parseToken(SyntaxKind.PartitionedByKeyword) instanceof SyntaxToken partitionedByKeyword)
        {
            Expression entity = parseSimplePathExpression();
            if (entity == null)
                entity = createMissingNameReference();
            var openParen = parseRequiredToken(SyntaxKind.OpenParenToken);
            var subQuery = parseContextualSubExpression();
            if (subQuery == null)
                subQuery = createMissingExpression();
            var closeParen = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new MakeGraphPartitionedByClause(partitionedByKeyword, (NameReference)entity, openParen, subQuery, closeParen);
        }

        return null;
    }
    // endregion
    // region GraphMatchOperator
    private GraphMatchOperator parseGraphMatchOperator()
    {
        if (parseToken(SyntaxKind.GraphMatchKeyword) instanceof SyntaxToken keyword)
        {
            var parameters = parseQueryOperatorParameterList(s_graphMatchParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var patterns = parseCommaList(FnParseGraphMatchPattern, FnCreateMissingGraphMatchPattern, null, true);
            var whereClause = parseWhereClause();
            var projectClause = parseProjectClause();

            return new GraphMatchOperator(keyword, parameters, patterns, whereClause, projectClause);
        }

        return null;
    }

    private GraphShortestPathsOperator parseGraphShortestPathsOperator()
    {
        if (parseToken(SyntaxKind.GraphShortestPathsKeyword) instanceof SyntaxToken keyword)
        {
            var parameters = parseQueryOperatorParameterList(s_graphShortestPathsParameterMap, AllowedNameKind.DeclaredOrKnown, true);
            var patterns = parseCommaList(FnParseGraphMatchPattern, FnCreateMissingGraphMatchPattern, null, true);
            var whereClause = parseWhereClause();
            var projectClause = parseProjectClause();

            return new GraphShortestPathsOperator(keyword, parameters, patterns, whereClause, projectClause);
        }

        return null;
    }

    private static final List<SyntaxKind> s_rangeEndTokens = Arrays.asList(
        SyntaxKind.BracketDashGreaterThanToken,
        SyntaxKind.BracketDashToken);

    private static Supplier<GraphMatchPatternNotation> FnCreateMissingGraphMatchPatternNotation =
        () -> new GraphMatchPatternNode(
            SyntaxToken.missing(SyntaxKind.OpenParenToken),
            new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
            SyntaxToken.missing(SyntaxKind.CloseParenToken),
            Arrays.asList(DiagnosticFacts.getMissingGraphMatchPattern())
        );

    private static Supplier<GraphMatchPattern> FnCreateMissingGraphMatchPattern =
        () -> new GraphMatchPattern(new SyntaxList1<GraphMatchPatternNotation>(FnCreateMissingGraphMatchPatternNotation.get()));

    private static Function<QueryParser, GraphMatchPatternNotation> FnParseGraphMatchPatternNotation = qp ->
    {
        var node = qp.parseGraphMatchPatternNode(); // PORT: §3.14 ??
        return node != null ? node : qp.parseGraphMatchPatternEdge();
    };

    private static Function<QueryParser, GraphMatchPattern> FnParseGraphMatchPattern = qp -> new GraphMatchPattern(qp.parseList(FnParseGraphMatchPatternNotation, FnCreateMissingGraphMatchPatternNotation, null, true));

    private GraphMatchPatternNotation parseGraphMatchPatternNode()
    {
        if (parseToken(SyntaxKind.OpenParenToken) instanceof SyntaxToken open)
        {
            var name = parseNameDeclaration();
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new GraphMatchPatternNode(open, name, close);
        }

        return null;
    }

    private GraphMatchPatternNotation parseGraphMatchPatternEdge()
    {
        var firstToken = parseTokenText("-->", SyntaxKind.DashDashGreaterThanToken);
        if (firstToken == null)
            firstToken = parseTokenText("<--", SyntaxKind.LessThanDashDashToken);
        if (firstToken == null)
            firstToken = parseTokenText("--", SyntaxKind.DashDashToken);

        if (firstToken != null)
        {
            return new GraphMatchPatternEdge(firstToken, null, null, null);
        }

        firstToken = parseTokenText("-[", SyntaxKind.DashBracketToken);
        if (firstToken == null)
            firstToken = parseTokenText("<-[", SyntaxKind.LessThanDashBracketToken);

        if (firstToken != null)
        {
            var name = parseNameDeclaration();
            var range = parseGraphMatchPatternEdgeRange();

            var lastToken = parseTokenText("]->", SyntaxKind.BracketDashGreaterThanToken);
            if (lastToken == null)
                lastToken = parseTokenText("]-", SyntaxKind.BracketDashToken);
            if (lastToken == null)
                lastToken = createMissingToken(s_rangeEndTokens);

            return new GraphMatchPatternEdge(firstToken, name, range, lastToken);
        }

        return null;
    }

    private GraphMatchPatternEdgeRange parseGraphMatchPatternEdgeRange()
    {
        if (parseToken(SyntaxKind.AsteriskToken) instanceof SyntaxToken asterisk)
        {
            var rangeStart = parseInvocationExpression();
            if (rangeStart == null)
                rangeStart = CreateMissingValue.get();
            var dotDotToken = parseRequiredToken(SyntaxKind.DotDotToken);
            var rangeEnd = parseInvocationExpression();
            if (rangeEnd == null)
                rangeEnd = createMissingExpression();
            return new GraphMatchPatternEdgeRange(asterisk, rangeStart, dotDotToken, rangeEnd);
        }

        return null;
    }

    private WhereClause parseWhereClause()
    {
        if (parseToken(SyntaxKind.WhereKeyword) instanceof SyntaxToken keyword)
        {
            var expression = parseExpression();
            if (expression == null)
                expression = createMissingExpression();
            return new WhereClause(keyword, expression);
        }

        return null;
    }

    private ProjectClause parseProjectClause()
    {
        if (parseToken(SyntaxKind.ProjectKeyword) instanceof SyntaxToken keyword)
        {
            var expressions = parseCommaList(FnParseNamedExpression, this::createMissingExpression, FnScanCommonListEnd, true);
            return new ProjectClause(keyword, expressions);
        }

        return null;
    }
    // endregion
    // region GraphToTableOperator
    private GraphToTableOperator parseGraphToTableOperator()
    {
        if (parseToken(SyntaxKind.GraphToTableKeyword) instanceof SyntaxToken keyword)
        {
            var outputClause = parseCommaList(FnParseGraphToTableOutputClause, CreateGraphToTableOutputClause, null, true);
            return new GraphToTableOperator(keyword, outputClause);
        }

        return null;
    }

    private static Supplier<GraphToTableOutputClause> CreateGraphToTableOutputClause = () ->
        new GraphToTableOutputClause(
            SyntaxToken.missing(SyntaxKind.OpenParenToken),
            new GraphToTableAsClause(
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken))
            ),
            null,
            Arrays.asList(DiagnosticFacts.getIncorrectNumberOfOutputGraphToTableEntities())
        );

    private static Function<QueryParser, GraphToTableOutputClause> FnParseGraphToTableOutputClause =
        qp -> qp.parseGraphToTableOutputClause();

    private static final Map<String, QueryOperatorParameter> s_graphMatchParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.GraphMatchParameters);

    private static final Map<String, QueryOperatorParameter> s_graphShortestPathsParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.GraphShortestPathsParameters);

    private static final Map<String, QueryOperatorParameter> s_graphToTableOperatorEdgesParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.GraphToTableEdgesParameters);

    private static final Map<String, QueryOperatorParameter> s_graphToTableOperatorNodesParameterMap =
        createQueryOperatorParameterMap(QueryOperatorParameters.GraphToTableNodesParameters);

    private GraphToTableOutputClause parseGraphToTableOutputClause()
    {
        var keywordToken = parseToken(SyntaxKind.NodesKeyword);
        if (keywordToken == null)
            keywordToken = parseToken(SyntaxKind.GraphEdgesKeyword);
        if (!(keywordToken instanceof SyntaxToken keyword))
        {
            return null;
        }

        var queryParams = keyword.kind() == SyntaxKind.NodesKeyword
            ? s_graphToTableOperatorNodesParameterMap
            : s_graphToTableOperatorEdgesParameterMap;

        var asClause = parseGraphToTableAsClause();
        var parameters = parseQueryOperatorParameterList(queryParams, AllowedNameKind.DeclaredOrKnown, true);

        return new GraphToTableOutputClause(keyword, asClause, parameters);
    }

    private GraphToTableAsClause parseGraphToTableAsClause()
    {
        if (parseToken(SyntaxKind.AsKeyword) instanceof SyntaxToken keyword)
        {
            var name = parseNameDeclaration();
            if (name == null)
                name = createMissingNameDeclaration();
            return new GraphToTableAsClause(keyword, name);
        }

        return null;
    }
    // endregion
    // region GraphMarkComponentsOperator
    private GraphMarkComponentsOperator parseGraphMarkComponentsOperator()
    {
        if (parseToken(SyntaxKind.GraphMarkComponentsKeyword) instanceof SyntaxToken keyword)
        {
            var parameters = parseQueryOperatorParameterList(s_GraphMarkComponentsParametersMap, AllowedNameKind.DeclaredOrKnown, true);
            return new GraphMarkComponentsOperator(keyword, parameters);
        }

        return null;
    }
    // endregion
    // region GraphWhereNodesOperator
    private GraphWhereNodesOperator parseGraphWhereNodesOperator()
    {
        if (parseToken(SyntaxKind.GraphWhereNodesKeyword) instanceof SyntaxToken keyword)
        {
            var expression = parseUnnamedExpression();
            if (expression == null)
                expression = createMissingExpression();
            return new GraphWhereNodesOperator(keyword, expression);
        }

        return null;
    }
    // endregion
    // region GraphWhereEdgesOperator
    private GraphWhereEdgesOperator parseGraphWhereEdgesOperator()
    {
        if (parseToken(SyntaxKind.GraphWhereEdgesKeyword) instanceof SyntaxToken keyword)
        {
            var expression = parseUnnamedExpression();
            if (expression == null)
                expression = createMissingExpression();
            return new GraphWhereEdgesOperator(keyword, expression);
        }

        return null;
    }
    // endregion
    // endregion
    // region Query Expressions

    /// <summary>
    /// Query operators that can occur at the start of a query
    /// </summary>
    private QueryOperator parseUnpipedQueryOperator()
    {
        switch (peekToken().kind())
        {
            case SyntaxKind.EvaluateKeyword:        // can be identifier
                return parseEvaluateOperator();
            case SyntaxKind.FindKeyword:
                return parseFindOperator();
            case SyntaxKind.MacroExpandKeyword:
                return parseMacroExpand();
            case SyntaxKind.PrintKeyword:
                return parsePrintOperator();
            case SyntaxKind.SearchKeyword:
                return parseSearchOperator();
            case SyntaxKind.UnionKeyword:
                return parseUnionOperator();
        }

        return null;
    }

    /// <summary>
    /// Query operators that occur after a pipe
    /// </summary>
    /// <returns></returns>
    private QueryOperator parsePipedQueryOperator()
    {
        switch (peekToken().kind())
        {
            case SyntaxKind.AsKeyword:
                return parseAsOperator();
            case SyntaxKind.ConsumeKeyword:
                return parseConsumeOperator();
            case SyntaxKind.CountKeyword:
                return parseCountOperator();
            case SyntaxKind.DistinctKeyword:
                return parseDistinctOperator();
            case SyntaxKind.EvaluateKeyword:
                return parseEvaluateOperator();         // can be identifier
            case SyntaxKind.ExecuteAndCacheKeyword:
                return parseExecuteAndCacheOperator();
            case SyntaxKind.ExtendKeyword:
                return parseExtendOperator();
            case SyntaxKind.FacetKeyword:               // can be identifier
                return parseFacetOperator();
            case SyntaxKind.FilterKeyword:
            case SyntaxKind.WhereKeyword:
                return parseFilterOperator();
            case SyntaxKind.FindKeyword:
                return parseFindOperator();
            case SyntaxKind.ForkKeyword:                // can be identifier
                return parseForkOperator();
            case SyntaxKind.GetSchemaKeyword:
                return parseGetSchemaOperator();
            case SyntaxKind.InvokeKeyword:
                return parseInvokeOperator();
            case SyntaxKind.JoinKeyword:
                return parseJoinOperator();
            case SyntaxKind.LookupKeyword:              // can be identifier
                return parseLookupOperator();
            case SyntaxKind.MakeSeriesKeyword:
                return parseMakeSeriesOperator();
            case SyntaxKind.MvApplyKeyword:
            case SyntaxKind.MvDashApplyKeyword:
                return parseMvApplyOperator();
            case SyntaxKind.MvExpandKeyword:
            case SyntaxKind.MvDashExpandKeyword:
                return parseMvExpandOperator();
            case SyntaxKind.ParseKeyword:
                return parseParseOperator();
            case SyntaxKind.ParseWhereKeyword:
                return parseParseWhereOperator();
            case SyntaxKind.ParseKvKeyword:
                return parseParseKvOperator();
            case SyntaxKind.PartitionByKeyword:         // can be identifier
                return parsePartitionByOperator();
            case SyntaxKind.PartitionKeyword:           // can be identifier
                return parsePartitionOperator();
            case SyntaxKind.ProjectKeyword:
                return parseProjectOperator();
            case SyntaxKind.ProjectAwayKeyword:
                return parseProjectAwayOperator();
            case SyntaxKind.ProjectKeepKeyword:
                return parseProjectKeepOperator();
            case SyntaxKind.ProjectRenameKeyword:
                return parseProjectRenameOperator();
            case SyntaxKind.ProjectReorderKeyword:
                return parseProjectReorderOperator();
            case SyntaxKind.ProjectByNamesKeyword:
                return parseProjectByNamesOperator();
            case SyntaxKind.RangeKeyword:               // can be identifier
                return parseRangeOperator();
            case SyntaxKind.InlineExternalTableKeyword:  // can be identifier
                return parseInlineExternalTableExpression();
            case SyntaxKind.ReduceKeyword:              // can be identifier
                return parseReduceByOperator();
            case SyntaxKind.RenderKeyword:              // can be identifier
                return parseRenderOperator();
            case SyntaxKind.SampleKeyword:
                return parseSampleOperator();
            case SyntaxKind.SampleDistinctKeyword:
                return parseSampleDistinctOperator();
            case SyntaxKind.ScanKeyword:
                return parseScanOperator();
            case SyntaxKind.SearchKeyword:
                return parseSearchOperator();
            case SyntaxKind.SerializeKeyword:
                return parseSerializeOperator();
            case SyntaxKind.SortKeyword:
            case SyntaxKind.OrderKeyword:
                return parseSortOperator();
            case SyntaxKind.SummarizeKeyword:
                return parseSummarizeOperator();
            case SyntaxKind.TakeKeyword:
            case SyntaxKind.LimitKeyword:
                return parseTakeOperator();
            case SyntaxKind.TopKeyword:
                return parseTopOperator();
            case SyntaxKind.TopHittersKeyword:
                return parseTopHittersOperator();
            case SyntaxKind.TopNestedKeyword:
                return parseTopNestedOperator();
            case SyntaxKind.UnionKeyword:
                return parseUnionOperator();
            case SyntaxKind.MakeGraphKeyword:
                return parseMakeGraphOperator();
            case SyntaxKind.GraphMatchKeyword:
                return parseGraphMatchOperator();
            case SyntaxKind.GraphShortestPathsKeyword:
                return parseGraphShortestPathsOperator();
            case SyntaxKind.GraphToTableKeyword:
                return parseGraphToTableOperator();
            case SyntaxKind.GraphMarkComponentsKeyword:
                return parseGraphMarkComponentsOperator();
            case SyntaxKind.GraphWhereNodesKeyword:
                return parseGraphWhereNodesOperator();
            case SyntaxKind.GraphWhereEdgesKeyword:
                return parseGraphWhereEdgesOperator();
            case SyntaxKind.AssertSchemaKeyword:
                return parseAssertSchemaOperator();
            default:
                return null;
        }
    }

    /// <summary>
    /// Returns true if it looks like it is a query operator
    /// </summary>
    private boolean scanPossibleQueryOperator()
    {
        int nameLen;
        var kind = peekToken().kind();

        switch (kind)
        {
            // keywords can be identifier so might not actually be query operators
            case SyntaxKind.EvaluateKeyword:
                // evaluate <parameter-name> ...  or evaluate <plugin-name> ...
                return scanName(1) > 0
                    || scanQueryOperatorParameterName(AllowedNameKind.KnownOnly, false, 1) > 0;

            case SyntaxKind.FacetKeyword:
                // facet by
                return peekToken(1).kind() == SyntaxKind.ByKeyword;

            case SyntaxKind.PartitionKeyword:
                // partition by  or  partition <parameter-name>
                return peekToken(1).kind() == SyntaxKind.ByKeyword
                    || scanQueryOperatorParameterName(AllowedNameKind.KnownOnly, false, 1) > 0;

            case SyntaxKind.RangeKeyword:
                // range <name> from ...
                return (nameLen = scanName(1)) > 0
                    && peekToken(1 + nameLen).kind() == SyntaxKind.FromKeyword;

            case SyntaxKind.InlineExternalTableKeyword:
                // inline_external_table ( ...  or inline_external_table kind=...
                return peekToken(1).kind() == SyntaxKind.OpenParenToken
                    || peekToken(1).kind() == SyntaxKind.KindKeyword;

            case SyntaxKind.ReduceKeyword:
                // reduce by  or  reduce xxx
                return peekToken(1).kind() == SyntaxKind.ByKeyword
                    || scanQueryOperatorParameterName(AllowedNameKind.KnownOnly, false, 1) > 0;

            case SyntaxKind.RenderKeyword:
                // render <chart-type>
                return scanToken(KustoFacts.ChartTypes, 1) > 0
                    || scanIdentifierOrKeywordAsIdentifier(1); // other unknown chart type?

            case SyntaxKind.ForkKeyword:
            case SyntaxKind.LookupKeyword:
            case SyntaxKind.PartitionByKeyword:
                // can be identifier but too complex for look ahead
                return false;

            case SyntaxKind.AsKeyword:
            case SyntaxKind.AssertSchemaKeyword:
            case SyntaxKind.ConsumeKeyword:
            case SyntaxKind.CountKeyword:
            case SyntaxKind.DistinctKeyword:
            case SyntaxKind.ExecuteAndCacheKeyword:
            case SyntaxKind.ExtendKeyword:
            case SyntaxKind.FilterKeyword:
            case SyntaxKind.FindKeyword:
            case SyntaxKind.GetSchemaKeyword:
            case SyntaxKind.GraphMatchKeyword:
            case SyntaxKind.GraphToTableKeyword:
            case SyntaxKind.InvokeKeyword:
            case SyntaxKind.JoinKeyword:
            case SyntaxKind.LimitKeyword:
            case SyntaxKind.MacroExpandKeyword:
            case SyntaxKind.MakeGraphKeyword:
            case SyntaxKind.GraphMarkComponentsKeyword:
            case SyntaxKind.MakeSeriesKeyword:
            case SyntaxKind.MvApplyKeyword:
            case SyntaxKind.MvDashApplyKeyword:
            case SyntaxKind.MvExpandKeyword:
            case SyntaxKind.MvDashExpandKeyword:
            case SyntaxKind.OrderKeyword:
            case SyntaxKind.ParseKeyword:
            case SyntaxKind.ParseWhereKeyword:
            case SyntaxKind.ParseKvKeyword:
            case SyntaxKind.PrintKeyword:
            case SyntaxKind.ProjectKeyword:
            case SyntaxKind.ProjectAwayKeyword:
            case SyntaxKind.ProjectKeepKeyword:
            case SyntaxKind.ProjectRenameKeyword:
            case SyntaxKind.ProjectByNamesKeyword:
            case SyntaxKind.ProjectReorderKeyword:
            case SyntaxKind.SampleKeyword:
            case SyntaxKind.SampleDistinctKeyword:
            case SyntaxKind.ScanKeyword:
            case SyntaxKind.SearchKeyword:
            case SyntaxKind.SerializeKeyword:
            case SyntaxKind.SortKeyword:
            case SyntaxKind.SummarizeKeyword:
            case SyntaxKind.TakeKeyword:
            case SyntaxKind.TopKeyword:
            case SyntaxKind.TopHittersKeyword:
            case SyntaxKind.TopNestedKeyword:
            case SyntaxKind.UnionKeyword:
            case SyntaxKind.WhereKeyword:
                // if cannot be identifier it must be query operator
                return !SyntaxFacts.canBeIdentifier(kind);

            default:
                return false;
        }
    }

    private BadQueryOperator parseBadQueryOperator()
    {
        var id = parseToken(SyntaxKind.IdentifierToken);
        if (id != null)
        {
            return new BadQueryOperator(id, Arrays.asList(DiagnosticFacts.getQueryOperatorExpected()));
        }

        return null;
    }

    private QueryOperator parseRequiredQueryOperator()
    {
        QueryOperator result = parsePipedQueryOperator();
        if (result == null)
            result = parseUnpipedQueryOperator(); // allow unpiped to parse here and deal with errors during semantic analysis
        if (result == null)
            result = parseBadQueryOperator(); // not a query operator after a pipe, parse anyway?
        if (result == null)
            result = CreateMissingQueryOperator.get();
        return result;
    }

    private Expression parsePipeExpression()
    {
        Expression expr; // PORT: §3.14 ?? inside conditional
        if (scanPossibleQueryOperator())
        {
            expr = parseUnpipedQueryOperator();
            if (expr == null)
                expr = parsePipedQueryOperator();
            if (expr == null)
                expr = parseUnnamedExpression();
        }
        else
        {
            expr = parseUnnamedExpression();
            if (expr == null)
                expr = parseUnpipedQueryOperator();
            if (expr == null)
                expr = parsePipedQueryOperator();
        }

        if (expr != null)
        {
            while (peekToken().kind() == SyntaxKind.BarToken)
            {
                var pipe = parseToken();
                var pipedOperator = parseRequiredQueryOperator();
                expr = new PipeExpression(expr, pipe, pipedOperator);
            }
        }

        return expr;
    }

    private Expression parsePipeSubExpression()
    {
        Expression expr = parseRequiredQueryOperator();

        if (expr != null)
        {
            while (peekToken().kind() == SyntaxKind.BarToken)
            {
                var pipe = parseToken();
                var pipedOperator = parseRequiredQueryOperator();
                expr = new PipeExpression(expr, pipe, pipedOperator);
            }
        }

        return expr;
    }

    private Expression parseContextualSubExpression()
    {
        Expression expr = (Expression)parseContextualDataTableExpression();
        if (expr == null)
            expr = parseRequiredQueryOperator();

        if (expr != null)
        {
            while (peekToken().kind() == SyntaxKind.BarToken)
            {
                var pipe = parseToken();
                var pipedOperator = parseRequiredQueryOperator();
                expr = new PipeExpression(expr, pipe, pipedOperator);
            }
        }

        return expr;
    }

    private Expression parseExpression()
    {
        return parsePipeExpression();
    }

    // endregion
    // region Statements

    // region alias

    private AliasStatement parseAliasStatement()
    {
        var keyword = parseToken(SyntaxKind.AliasKeyword);
        if (keyword != null)
        {
            var database = parseRequiredToken(SyntaxKind.DatabaseKeyword);
            var name = parseNameDeclaration();
            if (name == null)
                name = createMissingNameDeclaration();
            var equal = parseRequiredToken(SyntaxKind.EqualToken);
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new AliasStatement(keyword, database, name, equal, expr);
        }

        return null;
    }

    // endregion
    // region let

    private MaterializeExpression parseMaterializeExpression()
    {
        var keyword = parseToken(SyntaxKind.MaterializeKeyword);
        if (keyword != null)
        {
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var expr = parsePipeExpression();
            if (expr == null)
                expr = createMissingExpression();
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new MaterializeExpression(keyword, open, expr, close);
        }

        return null;
    }

    private DefaultValueDeclaration parseDefaultValueDeclaration()
    {
        var equal = parseToken(SyntaxKind.EqualToken);
        if (equal != null)
        {
            var expr = parseLiteral();
            if (expr == null)
                expr = parseIdentifierOrKeywordTokenLiteral();
            if (expr == null)
                expr = createMissingExpression();
            return new DefaultValueDeclaration(equal, expr);
        }

        return null;
    }

    private FunctionParameter parseFunctionParameter()
    {
        var name = parseNameAndTypeDeclaration();
        if (name != null)
        {
            var defValue = parseDefaultValueDeclaration();
            return new FunctionParameter(name, defValue);
        }

        return null;
    }

    private static final Function<QueryParser, FunctionParameter> FnParseFunctionParameter =
        qp -> qp.parseFunctionParameter();

    private static final Supplier<FunctionParameter> CreateMissingFunctionParameter = () ->
        new FunctionParameter(
            new NameAndTypeDeclaration(
                new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                SyntaxToken.missing(SyntaxKind.ColonToken),
                new PrimitiveTypeExpression(SyntaxToken.missing(SyntaxKind.StringKeyword))),
             null,
             Arrays.asList(DiagnosticFacts.getMissingParameter()));


    /// <summary>
    /// Parses function parameter list:  (name: type [= value], name: type [= value], ...)
    /// </summary>
    private FunctionParameters parseFunctionParameters()
    {
        var open = parseToken(SyntaxKind.OpenParenToken);
        if (open != null)
        {
            var parameters = parseCommaList(FnParseFunctionParameter, CreateMissingFunctionParameter, FnScanCommonListEnd);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new FunctionParameters(open, parameters, close);
        }

        return null;
    }

    private SeparatedElement1<Statement> parseFunctionBodyStatement()
    {
        Statement statement = parseLetStatement();
        if (statement == null)
            statement = parseQueryParametersStatement();

        if (statement != null)
        {
            return new SeparatedElement1<Statement>(
                statement,
                parseRequiredToken(SyntaxKind.SemicolonToken));
        }

        return null;
    }

    private static final Function<QueryParser, SeparatedElement1<Statement>> FnParseFunctionBodyStatements =
        qp -> qp.parseFunctionBodyStatement();

    private static final Supplier<Statement> CreateMissingStatement = () ->
        new ExpressionStatement(
            new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
            Arrays.asList(DiagnosticFacts.getMissingStatement()));

    private static final Supplier<SeparatedElement1<Statement>> CreateMissingStatementElement = () ->
        new SeparatedElement1<Statement>(
            new ExpressionStatement(
                new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                Arrays.asList(DiagnosticFacts.getMissingStatement())));


    private FunctionBody parseFunctionBody()
    {
        var open = parseToken(SyntaxKind.OpenBraceToken);
        if (open != null)
        {
            var statements = parseList(FnParseFunctionBodyStatements, CreateMissingStatementElement, FnScanCommonListEnd);
            var expr = parseExpression();
            var semicolon = parseToken(SyntaxKind.SemicolonToken);
            var close = parseRequiredToken(SyntaxKind.CloseBraceToken);
            return new FunctionBody(open, statements, expr, semicolon, close);
        }

        return null;
    }

    private boolean scanFunctionDeclarationStart(int offset)
    {
        // optional view keyword
        var token = peekToken(offset);
        if (token.kind() == SyntaxKind.ViewKeyword)
        {
            offset++;
            token = peekToken(offset);
        }

        // if this looks like parameter list then it must be a function declaration
        if (token.kind() == SyntaxKind.OpenParenToken)
        {
            var nextKind = peekToken(offset + 1).kind();
            if (nextKind == SyntaxKind.CloseParenToken
                || nextKind == SyntaxKind.AsteriskToken)
                return true;
            var nameLen = scanExtendedName(offset + 1);
            return nameLen > 0 && peekToken(offset + 1 + nameLen).kind() == SyntaxKind.ColonToken;
        }

        return false;
    }

    // PORT: §3.12 telescoped overload
    private boolean scanFunctionDeclarationStart()
    {
        return scanFunctionDeclarationStart(0);
    }

    private static FunctionParameters createMissingFunctionParameters()
    {
        return new FunctionParameters(
            createMissingToken(SyntaxKind.OpenParenToken),
            SyntaxList1.<SeparatedElement1<FunctionParameter>>empty(),
            createMissingToken(SyntaxKind.CloseParenToken));
    }

    private static FunctionBody createMissingFunctionBody()
    {
        return new FunctionBody(
            createMissingToken(SyntaxKind.OpenBraceToken),
            SyntaxList1.<SeparatedElement1<Statement>>empty(),
            null,
            null,
            createMissingToken(SyntaxKind.CloseBraceToken));
    }

    private FunctionDeclaration parseFunctionDeclaration()
    {
        switch (peekToken().kind())
        {
            case SyntaxKind.ViewKeyword:
            {
                // PORT: §3.14 ?? (argument evaluation order kept)
                var viewKeyword = parseToken();
                var parameters = parseFunctionParameters();
                if (parameters == null)
                    parameters = createMissingFunctionParameters();
                var body = parseFunctionBody();
                if (body == null)
                    body = createMissingFunctionBody();
                return new FunctionDeclaration(viewKeyword, parameters, body);
            }
            case SyntaxKind.OpenParenToken:
            {
                var parameters = parseFunctionParameters(); // PORT: §3.14 ??
                if (parameters == null)
                    parameters = createMissingFunctionParameters();
                var body = parseFunctionBody();
                if (body == null)
                    body = createMissingFunctionBody();
                return new FunctionDeclaration(null, parameters, body);
            }
            default:
                return null;
        }
    }

    private Expression parseEntityGroup()
    {
        if (peekToken().kind() == SyntaxKind.EntityGroupKeyword)
        {
            // try parse it as a function call entity_group("eg").
            var functionCallExpression = parseFunctionCallExpression();
            if (functionCallExpression != null)
            {
                return functionCallExpression;
            }

            // if the above didn't work, try to parse explicit entity group: entity_group[cluster('c1').database('db1'), ...]
            var keyword = parseToken();
            var openBracket = parseRequiredToken(SyntaxKind.OpenBracketToken);
            var expressions = parseCommaList(FnParseUnnamedExpression, this::createMissingExpression, FnScanCommonListEnd, true);
            var closeBracket = parseRequiredToken(SyntaxKind.CloseBracketToken);
            return new EntityGroup(keyword, openBracket, expressions, closeBracket);
        }

        return null;
    }

    private Statement parseLetStatement()
    {
        var keyword = parseToken(SyntaxKind.LetKeyword);
        if (keyword != null)
        {
            var name = parseNameDeclaration();
            if (name == null)
                name = createMissingNameDeclaration();
            var equal = parseRequiredToken(SyntaxKind.EqualToken);

            if (peekToken().kind() == SyntaxKind.MaterializeKeyword)
            {
                return new LetStatement(keyword, name, equal, parseMaterializeExpression());
            }
            else if (peekToken().kind() == SyntaxKind.EntityGroupKeyword)
            {
                return new LetStatement(keyword, name, equal, parseEntityGroup());
            }
            else if (scanFunctionDeclarationStart())
            {
                return new LetStatement(keyword, name, equal, parseFunctionDeclaration());
            }
            else
            {
                var value = parseExpression(); // PORT: §3.14 ??
                if (value == null)
                    value = createMissingExpression();
                return new LetStatement(keyword, name, equal, value);
            }
        }

        return null;
    }

    // endregion
    // region set option

    private OptionValueClause parseOptionValueClause()
    {
        var equal = parseToken(SyntaxKind.EqualToken);
        if (equal != null)
        {
            var expr = parseUnnamedExpression();
            if (expr == null)
                expr = createMissingExpression();
            return new OptionValueClause(equal, expr);
        }

        return null;
    }

    private SetOptionStatement parseSetOptionStatement()
    {
        var keyword = parseToken(SyntaxKind.SetKeyword);
        if (keyword != null)
        {
            var name = parseNameDeclaration();
            if (name == null)
                name = createMissingNameDeclaration();
            var valueClause = parseOptionValueClause();
            return new SetOptionStatement(keyword, name, valueClause);
        }

        return null;
    }

    // endregion
    // region declare query_parameters

    private QueryParametersStatement parseQueryParametersStatement()
    {
        var keyword = parseToken(SyntaxKind.DeclareKeyword);
        if (keyword != null)
        {
            var parametersKeyword = parseRequiredToken(SyntaxKind.QueryParametersKeyword);
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var parameters = parseCommaList(FnParseFunctionParameter, CreateMissingFunctionParameter, FnScanCommonListEnd, true);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new QueryParametersStatement(keyword, parametersKeyword, open, parameters, close);
        }

        return null;
    }

    // endregion
    // region restrict
    private static final List<Function<QueryParser, Expression>> ParseRestrictExpressionParsers =
        Arrays.asList(
            FnParseWildcardedEntityExpression,
            FnParseNameReference);

    private Expression parseRestrictExpression()
    {
        return parseBest(ParseRestrictExpressionParsers);
    }

    private static final Function<QueryParser, Expression> FnParseRestrictExpression =
        qp -> qp.parseRestrictExpression();

    private RestrictStatement parseRestrictStatement()
    {
        var keyword = parseToken(SyntaxKind.RestrictKeyword);
        if (keyword != null)
        {
            var accessKeyword = parseRequiredToken(SyntaxKind.AccessKeyword);
            var toKeyword = parseRequiredToken(SyntaxKind.ToKeyword);
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var expressions = parseCommaList(FnParseRestrictExpression, this::createMissingExpression, FnScanCommonListEnd, true);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            var withClause = parseRestrictStatementWithClause();
            return new RestrictStatement(keyword, accessKeyword, toKeyword, open, expressions, close, withClause);
        }

        return null;
    }

    private RestrictStatementWithClause parseRestrictStatementWithClause()
    {
        var keyword = parseToken(SyntaxKind.WithKeyword);
        if (keyword != null)
        {
            var open = parseRequiredToken(SyntaxKind.OpenParenToken);
            var props = parseCommaList(parser -> parser.parseRestrictProperty(), CreateMissingNamedParameter, FnScanCommonListEnd);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            return new RestrictStatementWithClause(keyword, open, props, close);
        }

        return null;
    }

    private NamedParameter parseRestrictProperty()
    {
        var name = parseRenameNameDeclaration();
        if (name != null)
        {
            var equal = parseRequiredToken(SyntaxKind.EqualToken);
            var value = parseExternalDataPropertyValue();
            if (value == null)
                value = CreateMissingValue.get();
            return new NamedParameter(name, equal, value);
        }

        return null;
    }
    // endregion
    // region declare pattern

    private PatternPathValue parsePatternPathValue()
    {
        var dot = parseToken(SyntaxKind.DotToken);
        if (dot != null)
        {
            var open = parseRequiredToken(SyntaxKind.OpenBracketToken);
            var value = parseLiteral();
            if (value == null)
                value = CreateMissingStringLiteral.get();
            var close = parseRequiredToken(SyntaxKind.CloseBracketToken);
            return new PatternPathValue(dot, open, value, close);
        }

        return null;
    }

    private SeparatedElement1<Statement> parsePatternMatchStatementElement()
    {
        var statement = parseLetStatement();
        if (statement != null)
        {
            var semicolon = parseRequiredToken(SyntaxKind.SemicolonToken);
            return new SeparatedElement1<Statement>(statement, semicolon);
        }

        return null;
    }

    private static final Function<QueryParser, SeparatedElement1<Statement>> FnParsePatternMatchStatementElement =
        qp -> qp.parsePatternMatchStatementElement();

    private FunctionBody parseRequiredPatternMatchFunctionBody()
    {
        var open = parseRequiredToken(SyntaxKind.OpenBraceToken);
        var statements = parseList(FnParsePatternMatchStatementElement, CreateMissingStatementElement, FnScanCommonListEnd);
        var expr = parseExpression();
        var semicolon = parseToken(SyntaxKind.SemicolonToken);
        var close = parseRequiredToken(SyntaxKind.CloseBraceToken);
        return new FunctionBody(open, statements, expr, semicolon, close);
    }

    private PatternMatch parsePatternMatch()
    {
        var open = parseToken(SyntaxKind.OpenParenToken);
        if (open != null)
        {
            var expressions = parseCommaList(FnParseUnnamedExpression, CreateMissingStringLiteral, FnScanCommonListEnd, true);
            var close = parseRequiredToken(SyntaxKind.CloseParenToken);
            var pathValue = parsePatternPathValue();
            var equal = parseRequiredToken(SyntaxKind.EqualToken);
            var body = parseRequiredPatternMatchFunctionBody();
            var semicolon = parseRequiredToken(SyntaxKind.SemicolonToken);
            return new PatternMatch(new ExpressionList(open, expressions, close), pathValue, equal, body, semicolon);
        }

        return null;
    }

    private static final Function<QueryParser, PatternMatch> FnParsePatternMatch =
        qp -> qp.parsePatternMatch();

    private PatternPathParameter parsePatternPathParameter()
    {
        var open = parseToken(SyntaxKind.OpenBracketToken);
        if (open != null)
        {
            var name = parseNameAndTypeDeclaration();
            if (name == null)
                name = CreateMissingNameAndTypeDeclaration.get();
            var close = parseRequiredToken(SyntaxKind.CloseBracketToken);
            return new PatternPathParameter(open, name, close);
        }

        return null;
    }

    private static final Supplier<PatternMatch> CreateMissingPatternMatch = () ->
        new PatternMatch(
            new ExpressionList(
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                SyntaxList1.<SeparatedElement1<Expression>>empty(),
                SyntaxToken.missing(SyntaxKind.CloseParenToken)),
            null, // path value okay to be null
            SyntaxToken.missing(SyntaxKind.EqualToken),
            new FunctionBody(
                SyntaxToken.missing(SyntaxKind.OpenBraceToken),
                SyntaxList1.<SeparatedElement1<Statement>>empty(),
                null, // expression okay to be null
                null, // semicolon okay to be null
                SyntaxToken.missing(SyntaxKind.CloseBraceToken)),
            SyntaxToken.missing(SyntaxKind.SemicolonToken),
            Arrays.asList(DiagnosticFacts.getMissingPatternMatch()));

    private PatternDeclaration parsePatternDeclaration()
    {
        var equal = parseToken(SyntaxKind.EqualToken);
        if (equal != null)
        {
            var openP = parseRequiredToken(SyntaxKind.OpenParenToken);
            var decls = parseCommaList(FnParseNameAndTypeDeclaration, CreateMissingNameAndTypeDeclaration, FnScanCommonListEnd);
            var closeP = parseRequiredToken(SyntaxKind.CloseParenToken);
            var path = parsePatternPathParameter();
            var openB = parseRequiredToken(SyntaxKind.OpenBraceToken);
            var matches = parseList(FnParsePatternMatch, CreateMissingPatternMatch, FnScanCommonListEnd, true);
            var closeB = parseRequiredToken(SyntaxKind.CloseBraceToken);
            return new PatternDeclaration(equal, openP, decls, closeP, path, openB, matches, closeB);
        }

        return null;
    }

    private PatternStatement parsePatternStatement()
    {
        if (peekToken().kind() == SyntaxKind.DeclareKeyword && peekToken(1).kind() == SyntaxKind.PatternKeyword)
        {
            var declare = parseToken();
            var pattern = parseToken();
            var name = parseNameDeclaration();
            if (name == null)
                name = createMissingNameDeclaration();
            var declaration = parsePatternDeclaration();
            return new PatternStatement(declare, pattern, name, declaration);
        }

        return null;
    }

    // endregion
    // endregion
    // region Query Block

    private Statement parseQueryBlockStatement()
    {
        if (scanPossibleStatement())
        {
            switch (peekToken().kind())
            {
                case SyntaxKind.AliasKeyword:
                    return parseAliasStatement();
                case SyntaxKind.LetKeyword:
                    return parseLetStatement();
                case SyntaxKind.SetKeyword:
                    return parseSetOptionStatement();
                case SyntaxKind.DeclareKeyword:
                    if (peekToken(1).kind() == SyntaxKind.PatternKeyword)
                        return parsePatternStatement();
                    else
                        return parseQueryParametersStatement();
                case SyntaxKind.RestrictKeyword:
                    return parseRestrictStatement();
            }
        }

        var expr = parseExpression();
        if (expr != null)
            return new ExpressionStatement(expr);
        return null;
    }

    /// <summary>
    /// Returns true if it looks like it is a statement
    /// </summary>
    private boolean scanPossibleStatement()
    {
        switch (peekToken().kind())
        {
            case SyntaxKind.AliasKeyword:
                return peekToken(1).kind() == SyntaxKind.DatabaseKeyword;

            case SyntaxKind.LetKeyword:
                return scanName(1) > 0;

            case SyntaxKind.DeclareKeyword:
                return peekToken(1).kind() == SyntaxKind.PatternKeyword
                    || peekToken(1).kind() == SyntaxKind.QueryParametersKeyword;

            case SyntaxKind.RestrictKeyword:
                return peekToken(1).kind() == SyntaxKind.AccessKeyword;

            case SyntaxKind.SetKeyword:
                // cannot be identifier, must be set statement.
                return true;

            default:
                return false;
        }
    }


    private SyntaxList1<SeparatedElement1<Statement>> parseQueryBlockStatementList()
    {
        var list = new ArrayList<SeparatedElement1<Statement>>();

        var statement = parseQueryBlockStatement();
        while (statement != null)
        {
            var semicolon = parseToken(SyntaxKind.SemicolonToken);
            if (semicolon != null)
            {
                list.add(new SeparatedElement1<Statement>(statement, semicolon));
                statement = parseQueryBlockStatement();
                continue;
            }
            else
            {
                var nextStatement = parseQueryBlockStatement();
                if (nextStatement != null)
                {
                    list.add(new SeparatedElement1<Statement>(statement, createMissingToken(SyntaxKind.SemicolonToken)));
                }
                else
                {
                    list.add(new SeparatedElement1<Statement>(statement, null));
                }

                statement = nextStatement;
            }
        }

        return new SyntaxList1<SeparatedElement1<Statement>>(list);
    }

    private SkippedTokens parseSkippedTokens()
    {
        List<SyntaxToken> tokens = null;

        SyntaxKind kind;
        while ((kind = peekToken().kind()) != SyntaxKind.EndOfTextToken && kind != SyntaxKind.None)
        {
            if (tokens == null)
                tokens = new ArrayList<SyntaxToken>();

            tokens.add(parseToken());
        }

        if (tokens != null)
        {
            // just add the diagnostic to the first token to avoid the overwhemling underlines in the editor
            tokens.set(0, (SyntaxToken)tokens.get(0).withAdditionalDiagnostics(DiagnosticFacts.getIncompleteFragment())); // PORT: §3.1 indexer
            return new SkippedTokens(new SyntaxList1<SyntaxToken>(tokens));
        }

        return null;
    }

    private SyntaxList1<Directive> parseDirectiveList()
    {
        List<Directive> directives = null;

        while (parseToken(SyntaxKind.DirectiveToken) instanceof SyntaxToken directive)
        {
            if (directives == null)
                directives = new ArrayList<Directive>();
            directives.add(new Directive(directive));
        }

        return directives != null
            ? new SyntaxList1<Directive>(directives)
            : SyntaxList1.<Directive>empty();
    }

    /// <summary>
    /// Parses and entire query
    /// </summary>
    private QueryBlock parseQuery()
    {
        return new QueryBlock(
            parseDirectiveList(),
            parseQueryBlockStatementList(),
            parseSkippedTokens(),
            parseToken(SyntaxKind.EndOfTextToken));
    }

    // endregion
}
