// Ported from: src/Kusto.Language/Parser/TokenParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.DiagnosticFacts;
import org.graylog.kusto.language.DiagnosticLocationKind;
import org.graylog.kusto.language.KustoFacts;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.syntax.SyntaxFacts;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.utils.StringTable;
import org.graylog.kusto.language.utils.SubstringMap;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetChars;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.IntRef;

/// <summary>
/// Parses <see cref="LexicalToken"/> for Kusto query language
/// </summary>
public class TokenParser
{
    private final StringTable _stringTable;

    private TokenParser(StringTable stringTable)
    {
        _stringTable = stringTable;
    }

    private TokenParser()
    {
        this(new StringTable());
    }

    /// <summary>
    /// A default instance of the <see cref="TokenParser"/>
    /// </summary>
    private static final TokenParser Default =
        new TokenParser(null);

    /// <summary>
    /// Parses the token at the starting position in the text.
    /// </summary>
    public static LexicalToken parseToken(String text, int start, ParseOptions options)
    {
        return Default.parse(text, start, options != null ? options : ParseOptions.Default); // PORT: §3.14
    }

    public static LexicalToken parseToken(String text, int start) // PORT: §3.12 optional parameter options = null
    {
        return parseToken(text, start, null);
    }

    public static LexicalToken parseToken(String text) // PORT: §3.12 optional parameter start = 0
    {
        return parseToken(text, 0, null);
    }

    /// <summary>
    /// Parses all the tokens in the text.
    /// </summary>
    public static LexicalToken[] parseTokens(String text, ParseOptions options)
    {
        var tokens = new ArrayList<LexicalToken>();
        parseTokens(text, tokens, options != null ? options : ParseOptions.Default); // PORT: §3.14
        return tokens.toArray(new LexicalToken[0]);
    }

    public static LexicalToken[] parseTokens(String text) // PORT: §3.12 optional parameter options = null
    {
        return parseTokens(text, (ParseOptions) null);
    }

    /// <summary>
    /// Parses all the tokens in the text.
    /// </summary>
    public static void parseTokens(String text, List<LexicalToken> tokens, ParseOptions options)
    {
        options = options != null ? options : ParseOptions.Default; // PORT: §3.14
        var parser = new TokenParser();

        LexicalToken token;
        var pos = 0;
        do
        {
            token = parser.parse(text, pos, options);
            if (token == null)
                break;
            tokens.add(token);
            pos += token.length();
        }
        while (token.kind() != SyntaxKind.EndOfTextToken);
    }

    public static void parseTokens(String text, List<LexicalToken> tokens) // PORT: §3.12 optional parameter options = null
    {
        parseTokens(text, tokens, null);
    }

    /// <summary>
    /// Parses the token at the starting offset in the text.
    /// </summary>
    private LexicalToken parse(String text, int start, ParseOptions options)
    {
        LexicalToken tok;
        var pos = start;
        var trivia = parseTrivia(text, pos);
        pos += trivia.length();

        var ch = peek(text, pos);
        char ch2;

        if (!TextFacts.isLetterOrDigit(ch))
        {
            var info = getPunctuationTokenInfo(text, pos);
            if (info != null)
                return info.getToken(trivia);

            if (isStringLiteralStartQuote(ch))
            {
                tok = parseStringLiteral(text, pos, trivia);
                if (tok != null)
                    return tok;
            }
            else if (ch == '@')
            {
                ch2 = peek(text, pos + 1);
                if (isStringLiteralStartQuote(ch2))
                {
                    tok = parseStringLiteral(text, pos, trivia);
                    if (tok != null)
                        return tok;
                }
            }
            else if (ch == '#')
            {
                var directiveEnd = TextFacts.getLineEnd(text, pos);
                return new LexicalToken(SyntaxKind.DirectiveToken, trivia, getSubstring(text, pos, directiveEnd - pos));
            }
            else if (isAtEnd(text, pos))
            {
                if (trivia.length() > 0 || options.alwaysProduceEndToken())
                    return new LexicalToken(SyntaxKind.EndOfTextToken, trivia, "");
                return null;
            }
        }

        // keywords or other unhandled punctuation
        var keywordMatch = s_tokenInfoSubstringMap.getLongestMatch(text, pos);
        if (keywordMatch.getKey().length() > 0)
        {
            var nextChar = peek(text, pos + keywordMatch.getKey().length());

            // goo literals?
            if (nextChar == '(')
            {
                var gooKind = getGooLiteralTokenKind(keywordMatch.getValue().Kind);
                if (gooKind != SyntaxKind.None)
                {
                    var gooLen = scanGoo(text, pos + keywordMatch.getKey().length(), options);
                    if (gooLen > 0)
                    {
                        var gooText = getSubstring(text, pos, keywordMatch.getKey().length() + gooLen);

                        // validate the expected last character is correct
                        var dx = gooText.endsWith(")") // PORT: §5.4 culture-sensitive EndsWith(string) -> ordinal
                            ? null
                            : DiagnosticFacts.getMissingText(")").withLocationKind(DiagnosticLocationKind.RelativeEnd);

                        return new LexicalToken(gooKind, trivia, gooText, dx);
                    }
                }
            }

            if (!isIdentifierChar(nextChar))
            {
                return keywordMatch.getValue().getToken(trivia);
            }
        }

        if (isIdentifierStartChar(ch))
        {
            // check for identifier-like literal values
            var literalMatch = s_literalValueMap.getLongestMatch(text, pos);
            if (literalMatch.getKey().length() > 0 && !isIdentifierChar(peek(text, pos + literalMatch.getKey().length())))
                return new LexicalToken(literalMatch.getValue(), trivia, literalMatch.getKey());

            // it might be a uuid literal
            var rawGuidLen = scanRawGuidLiteral(text, pos);
            if (rawGuidLen > 0)
                return new LexicalToken(SyntaxKind.RawGuidLiteralToken, trivia, getSubstring(text, pos, rawGuidLen));

            var idLen = scanIdentifier(text, pos);

            // is this a hidden string?
            if (idLen == 1 && (ch == 'h' || ch == 'H'))
            {
                ch2 = peek(text, pos + 1);
                if (isStringLiteralStartQuote(ch2) || ch2 == '@')
                {
                    tok = parseStringLiteral(text, pos, trivia);
                    if (tok != null)
                        return tok;
                }
            }

            return new LexicalToken(SyntaxKind.IdentifierToken, trivia, getSubstring(text, pos, idLen));
        }
        else if (DotNetChars.isDigit(ch)) // PORT: §5.1 char.IsDigit (Unicode Nd, D21)
        {
            var rawGuidLen = scanRawGuidLiteral(text, pos);
            if (rawGuidLen > 0)
                return new LexicalToken(SyntaxKind.RawGuidLiteralToken, trivia, getSubstring(text, pos, rawGuidLen));
            var realLen = scanRealLiteral(text, pos);
            if (realLen >= 0)
                return new LexicalToken(SyntaxKind.RealLiteralToken, trivia, getSubstring(text, pos, realLen));
            var timeLen = scanTimespanLiteral(text, pos);
            if (timeLen >= 0)
                return new LexicalToken(SyntaxKind.TimespanLiteralToken, trivia, getSubstring(text, pos, timeLen));
            var longLen = scanLongLiteral(text, pos);
            if (longLen > 0)
                return new LexicalToken(SyntaxKind.LongLiteralToken, trivia, getSubstring(text, pos, longLen));
            var identifierLen = scanIdentifier(text, pos);
            if (identifierLen >= 0)
                return new LexicalToken(SyntaxKind.IdentifierToken, trivia, getSubstring(text, pos, identifierLen));
        }

        // this character is not part of the language
        var subtext = getSubstring(text, pos, 1);
        return new LexicalToken(SyntaxKind.BadToken, trivia, subtext, Arrays.asList(new Diagnostic[] { DiagnosticFacts.getUnexpectedCharacter(subtext) })); // PORT: §3.17 new[] { d } as IReadOnlyList
    }

    /// <summary>
    /// Returns true if the character is the starting quote character
    /// of string literal.
    /// </summary>
    private static boolean isStringLiteralStartQuote(char ch)
    {
        return ch == '\''
            || ch == '"'
            || ch == '`'
            || ch == '~';
    }

    private LexicalToken parseStringLiteral(String text, int start, String trivia)
    {
        // Note: this function repeats logic found in ScanStringLiteral in order to correctly identity when the end quote is not found
        // and apply the diagnostic without requiring ScanStringLiteral to return two values which would perform poorly when translated to javascript.

        var pos = start;
        Diagnostic dx = null;

        var ch = peek(text, pos);
        if (ch == 'h' || ch == 'H')
        {
            pos++;
            ch = peek(text, pos);
        }

        var isVerbatim = false;
        if (ch == '@')
        {
            isVerbatim = true;
            pos++;
            ch = peek(text, pos);
        }

        if (ch == '\'')
        {
            pos++;

            var contentLength = scanStringLiteralContent(text, pos, ch, isVerbatim);
            pos += contentLength;

            if (peek(text, pos) == ch)
            {
                pos++;
            }
            else
            {
                dx = DiagnosticFacts.getMissingText("'").withLocationKind(DiagnosticLocationKind.RelativeEnd);
            }
        }
        else if (ch == '"')
        {
            pos++;

            var contentLength = scanStringLiteralContent(text, pos, ch, isVerbatim);
            pos += contentLength;

            if (peek(text, pos) == ch)
            {
                pos++;
            }
            else
            {
                dx = DiagnosticFacts.getMissingText("\"").withLocationKind(DiagnosticLocationKind.RelativeEnd);
            }
        }
        else if (matches(text, pos, KustoFacts.MultiLineStringQuote))
        {
            pos += KustoFacts.MultiLineStringQuote.length();
            pos += scanMultiLineStringLiteralContent(text, pos, KustoFacts.MultiLineStringQuote);

            if (matches(text, pos, KustoFacts.MultiLineStringQuote))
            {
                pos += KustoFacts.MultiLineStringQuote.length();
            }
            else
            {
                dx = DiagnosticFacts.getMissingText(KustoFacts.MultiLineStringQuote).withLocationKind(DiagnosticLocationKind.RelativeEnd);
            }
        }
        else if (matches(text, pos, KustoFacts.AlternateMultiLineStringQuote))
        {
            pos += KustoFacts.AlternateMultiLineStringQuote.length();
            pos += scanMultiLineStringLiteralContent(text, pos, KustoFacts.AlternateMultiLineStringQuote);

            if (matches(text, pos, KustoFacts.AlternateMultiLineStringQuote))
            {
                pos += KustoFacts.AlternateMultiLineStringQuote.length();
            }
            else
            {
                dx = DiagnosticFacts.getMissingText(KustoFacts.AlternateMultiLineStringQuote).withLocationKind(DiagnosticLocationKind.RelativeEnd);
            }
        }
        else
        {
            return null;
        }

        return new LexicalToken(SyntaxKind.StringLiteralToken, trivia, getSubstring(text, start, pos - start), dx);
    }

    private static TokenInfo getPunctuationTokenInfo(String text, int start)
    {
        int pos = start;
        var ch = peek(text, pos);
        char ch2;

        switch (ch)
        {
            case '(':
                return OpenParenTokenInfo;
            case ')':
                return CloseParenTokenInfo;
            case '[':
                return OpenBracketTokenInfo;
            case ']':
                return CloseBracketTokenInfo;
            case '{':
                return OpenBraceTokenInfo;
            case '}':
                return CloseBraceTokenInfo;
            case '|':
                return BarTokenInfo;
            case '.':
                if (peek(text, pos + 1) == '.')
                    return DotDotTokenInfo;
                return DotTokenInfo;
            case '+':
                return PlusTokenInfo;
            case '-':
                return MinusTokenInfo;
            case '*':
                return AsteriskTokenInfo;
            case '/':
                return SlashTokenInfo;
            case '%':
                return PercentTokenInfo;
            case '<':
                ch2 = peek(text, pos + 1);
                if (ch2 == '=')
                    return LessThanOrEqualTokenInfo;
                else if (ch2 == '|')
                    return LessThanBarTokenInfo;
                else if (ch2 == '>')
                    return LessThanGreaterThanTokenInfo;
                return LessThanTokenInfo;
            case '>':
                if (peek(text, pos + 1) == '=')
                    return GreaterThanOrEqualTokenInfo;
                return GreaterThanTokenInfo;
            case '=':
                ch2 = peek(text, pos + 1);
                if (ch2 == '=')
                    return EqualEqualTokenInfo;
                else if (ch2 == '>')
                    return FatArrowTokenInfo;
                else if (ch2 == '~')
                    return EqualTildeTokenInfo;
                return EqualTokenInfo;
            case '!':
                ch2 = peek(text, pos + 1);
                if (ch2 == '=')
                    return BangEqualTokenInfo;
                else if (ch2 == '~')
                    return BangTildeTokenInfo;
                break;
            case ':':
                return ColonTokenInfo;
            case ';':
                return SemicolonTokenInfo;
            case ',':
                return CommaTokenInfo;
            case '@':
                ch2 = peek(text, pos + 1);
                if (!isStringLiteralStartQuote(ch2))
                {
                    return AtTokenInfo;
                }
                break;
            case '?':
                return QuestionTokenInfo;
        }

        return null;
    }

    private static final String[] s_booleanValues = new String[]
    {
        "true", "True", "TRUE",
        "false", "False", "FALSE"
    };

    private static final SubstringMap<SyntaxKind> s_literalValueMap =
        new SubstringMap<SyntaxKind>(booleanValuePairs()); // PORT: §3.6 s_booleanValues.Select(v => new KeyValuePair<string, SyntaxKind>(v, SyntaxKind.BooleanLiteralToken))

    private static List<Map.Entry<String, SyntaxKind>> booleanValuePairs() // PORT: §3.6 Select as a plain loop
    {
        var pairs = new ArrayList<Map.Entry<String, SyntaxKind>>(s_booleanValues.length);
        for (var v : s_booleanValues)
        {
            pairs.add(new AbstractMap.SimpleImmutableEntry<String, SyntaxKind>(v, SyntaxKind.BooleanLiteralToken));
        }
        return pairs;
    }

    /// <summary>
    /// Scans raw boolean literals: true or false.
    /// Does not scan bool(xxx).
    /// </summary>
    public static int scanBooleanLiteral(String text, int start)
    {
        var literalMatch = s_literalValueMap.getLongestMatch(text, start);
        if (literalMatch.getKey().length() > 0
            && literalMatch.getValue() == SyntaxKind.BooleanLiteralToken
            && !isIdentifierChar(peek(text, start + literalMatch.getKey().length())))
        {
            return literalMatch.getKey().length();
        }

        return -1;
    }

    public static int scanBooleanLiteral(String text) // PORT: §3.12 optional parameter start = 0
    {
        return scanBooleanLiteral(text, 0);
    }

    private static SyntaxKind getGooLiteralTokenKind(SyntaxKind keywordKind)
    {
        switch (keywordKind)
        {
            case LongKeyword:
            case Int64Keyword:
                return SyntaxKind.LongLiteralToken;
            case IntKeyword:
            case Int32Keyword:
                return SyntaxKind.IntLiteralToken;
            case RealKeyword:
            case DoubleKeyword:
                return SyntaxKind.RealLiteralToken;
            case DecimalKeyword:
                return SyntaxKind.DecimalLiteralToken;
            case BoolKeyword:
                return SyntaxKind.BooleanLiteralToken;
            case DateTimeKeyword:
            case DateKeyword:
                return SyntaxKind.DateTimeLiteralToken;
            case TimeKeyword:
            case TimespanKeyword:
                return SyntaxKind.TimespanLiteralToken;
            case GuidKeyword:
                return SyntaxKind.GuidLiteralToken;
            default:
                return SyntaxKind.None;
        }
    }

    /// <summary>
    /// Table of blank strings of varying sizes
    /// </summary>
    private static final String[] s_spaces =
        spaces(); // PORT: §3.6 System.Linq.Enumerable.Range(0, 32).Select(n => new string(' ', n)).ToArray()

    private static String[] spaces() // PORT: §3.6 Range/Select/ToArray as a plain loop
    {
        var result = new String[32];
        for (int n = 0; n < 32; n++)
        {
            result[n] = " ".repeat(n);
        }
        return result;
    }

    /// <summary>
    /// Parses the sequence of trivia in the string from the specified start position.
    /// </summary>
    public String parseTrivia(String text, int start)
    {
        // first check for spaces only
        var len = scanSpaces(text, start);
        int pos = start + len;

        // if next is something that should also be trivia, then use more extensive scan
        var ch = peek(text, pos);
        if (TextFacts.isWhitespace(ch))
        {
            // scan additional whitespace
            var wsLen = scanWhitespace(text, pos);
            pos += wsLen;

            ch = peek(text, pos);
            if (ch == '/' && peek(text, pos + 1) == '/')
            {
                var tlen = scanTrivia(text, pos);

                // don't reuse trivia with comments
                return getSubstring(text, start, len + wsLen + tlen, false); // PORT: §3.12 named argument intern: false
            }
            else
            {
                return getSubstring(text, start, len + wsLen);
            }
        }
        else if(ch == '/' && peek(text, pos + 1) == '/')
        {
            var tlen = scanTrivia(text, pos);

            // don't reuse trivia with comments
            return getSubstring(text, start, len + tlen, false); // PORT: §3.12 named argument intern: false
        }
        else if (len == 0)
        {
            return "";
        }
        else if (len == 1)
        {
            return " ";
        }
        else if (len < s_spaces.length)
        {
            // spaces only and we know these strings already
            return s_spaces[len];
        }
        else
        {
            return getSubstring(text, start, len);
        }
    }

    private static int scanSpaces(String text, int start)
    {
        int pos = start;

        while (peek(text, pos) == ' ')
        {
            pos++;
        }

        return pos - start;
    }

    /// <summary>
    /// Returns the number of consecutive whitespace characters from the start position.
    /// </summary>
    public static int scanWhitespace(String text, int start)
    {
        int pos = start;

        while (TextFacts.isWhitespace(peek(text, pos)))
        {
            pos++;
        }

        return pos - start;
    }

    /// <summary>
    /// Returns the number of consecutive trivia characters from the start position.
    /// </summary>
    public static int scanTrivia(String text, int start)
    {
        var pos = start;
        char ch;

        while (!isAtEnd(text, pos))
        {
            ch = peek(text, pos);

            if (TextFacts.isWhitespace(ch))
            {
                pos++;
                continue;
            }

            var commentLen = scanComment(text, pos);
            if (commentLen > 0)
            {
                pos += commentLen;
                continue;
            }
            else
            {
                break;
            }
        }

        return pos - start;
    }

    /// <summary>
    /// Returns the number of characters in a comment starting at the start position.
    /// </summary>
    public static int scanComment(String text, int start)
    {
        if (peek(text, start) == '/'
            && peek(text, start + 1) == '/')
        {
            var end = getNextLineStart(text, start);
            return end - start;
        }

        return 0;
    }

    private static int getNextLineStart(String text, int start)
    {
        var end = TextFacts.getNextLineStart(text, start);
        return end >= 0 ? end : text.length();
    }

    private static boolean isIdentifierStartChar(char ch)
    {
        return TextFacts.isLetter(ch) || ch == '_' || ch == '$';
    }

    private static boolean isIdentifierChar(char ch)
    {
        return TextFacts.isLetterOrDigit(ch) || ch == '_';
    }

    /// <summary>
    /// Returns the number of characters in the identifier or -1 if there is no
    /// identitifer at the starting position.
    /// </summary>
    public static int scanIdentifier(String text, int start)
    {
        int pos = start;

        var ch = peek(text, pos);
        if (isIdentifierStartChar(ch))
        {
            pos++;

            while (!isAtEnd(text, pos))
            {
                ch = peek(text, pos);
                if (isIdentifierChar(ch))
                {
                    pos++;
                }
                else
                {
                    break;
                }
            }
        }
        else if (DotNetChars.isDigit(ch)) // PORT: §5.1 char.IsDigit (Unicode Nd, D21)
        {
            int len = scanDigits(text, pos);
            if (len > 0)
            {
                // must have at least one one letter or _ after digits
                ch = peek(text, pos + len);
                if (TextFacts.isLetter(ch) || ch == '_')
                {
                    pos += len;

                    while (pos < text.length())
                    {
                        ch = text.charAt(pos);
                        if (isIdentifierChar(ch))
                        {
                            pos++;
                        }
                        else
                        {
                            break;
                        }
                    }
                }
            }
        }

        return pos > start ? pos - start : -1;
    }

    public static int scanIdentifier(String text) // PORT: §3.12 optional parameter start = 0
    {
        return scanIdentifier(text, 0);
    }

    private static int scanDigits(String text, int start)
    {
        int pos = start;

        while (DotNetChars.isDigit(peek(text, pos))) // PORT: §5.1 char.IsDigit (Unicode Nd, D21)
        {
            pos++;
        }

        return pos > start ? pos - start : -1;
    }

    private static int scanHexIntegerLiteral(String text, int start)
    {
        int pos = start;

        if (peek(text, start) == '0'
            && (peek(text, start + 1) == 'x' || peek(text, start + 1) == 'X'))
        {
            pos += 2;

            if (!TextFacts.isHexDigit(peek(text, pos)))
                return -1;

            pos++;

            while (TextFacts.isHexDigit(peek(text, pos)))
            {
                pos++;
            }
        }

        if (isIdentifierChar(peek(text, pos)))
            return -1;

        return pos > start ? pos - start : -1;
    }

    /// <summary>
    /// Scans raw long literal values.
    /// Does not scan long(xxx).
    /// </summary>
    public static int scanLongLiteral(String text, int start)
    {
        var hexLen = scanHexIntegerLiteral(text, start);
        if (hexLen > 0)
            return hexLen;
        var len = scanDigits(text, start);
        if (len > 0 && !isIdentifierChar(peek(text, start + len)))
            return len;
        return -1;
    }

    public static int scanLongLiteral(String text) // PORT: §3.12 optional parameter start = 0
    {
        return scanLongLiteral(text, 0);
    }

    private static int scanExponent(String text, int start)
    {
        int pos = start;
        var expch = peek(text, pos);
        if (expch == 'e' || expch == 'E')
        {
            pos++;
            var signch = peek(text, pos);
            if (signch == '+' || signch == '-')
                pos++;
            var exponentLen = scanDigits(text, pos);
            if (exponentLen <= 0)
                return -1;
            pos += exponentLen;
        }

        return pos > start ? pos - start : -1;
    }

    /// <summary>
    /// Scans raw real literals: 1.0, etc.
    /// Does not scan real(xxx).
    /// </summary>
    public static int scanRealLiteral(String text, int start)
    {
        var digitLen = scanDigits(text, start);
        if (digitLen <= 0)
            return -1;
        var pos = start + digitLen;
        if (peek(text, pos) == '.' && (peek(text, pos + 1) != '.' || peek(text, pos + 2) == '.'))
        {
            pos++;
            var fractionLen = scanDigits(text, pos);
            if (fractionLen > 0)
                pos += fractionLen;

            var expLen = scanExponent(text, pos);
            if (expLen > 0)
                pos += expLen;
        }
        else
        {
            var expLen = scanExponent(text, pos);
            if (expLen <= 0)
                return -1;
            pos += expLen;
        }

        if (isIdentifierChar(peek(text, pos)))
            return -1;

        return pos > start ? pos - start : -1;
    }

    public static int scanRealLiteral(String text) // PORT: §3.12 optional parameter start = 0
    {
        return scanRealLiteral(text, 0);
    }

    /// <summary>
    /// Scans raw timespan literals, 1day, etc.
    /// Does not scan timespan(xxx).
    /// </summary>
    public static int scanTimespanLiteral(String text, int start)
    {
        var numberLen = scanDigits(text, start);
        if (numberLen <= 0)
            return -1;
        if (peek(text, start + numberLen) == '.')
        {
            var fractionLen = scanDigits(text, start + numberLen + 1);
            if (fractionLen >= 0)
            {
                numberLen += fractionLen + 1;
            }
        }
        var suffixMatch = TimespanSuffixMap.getLongestMatch(text, start + numberLen);
        if (suffixMatch.getKey().length() <= 0)
            return -1;
        var len = numberLen + suffixMatch.getKey().length();
        if (isIdentifierChar(peek(text, start + len)))
            return -1;
        return len;
    }

    public static int scanTimespanLiteral(String text) // PORT: §3.12 optional parameter start = 0
    {
        return scanTimespanLiteral(text, 0);
    }

    private static final String[] TimespanSuffixes = new String[]
    {
        "m", "min", "minute", "minutes",
        "s", "sec", "second", "seconds",
        "d", "day", "days",
        "h", "hr", "hrs", "hour", "hours",
        "ms", "milli", "millis", "millisec", "millisecond", "milliseconds",
        "micro", "micros", "microsec", "microsecond", "microseconds",
        "nano", "nanos", "nanosec", "nanosecond", "nanoseconds",
        "tick", "ticks"
    };

    private static final SubstringMap<Boolean> TimespanSuffixMap =
        new SubstringMap<Boolean>(timespanSuffixPairs()); // PORT: §3.6 TimespanSuffixes.Select(s => new KeyValuePair<string, bool>(s, true))

    private static List<Map.Entry<String, Boolean>> timespanSuffixPairs() // PORT: §3.6 Select as a plain loop
    {
        var pairs = new ArrayList<Map.Entry<String, Boolean>>(TimespanSuffixes.length);
        for (var s : TimespanSuffixes)
        {
            pairs.add(new AbstractMap.SimpleImmutableEntry<String, Boolean>(s, true));
        }
        return pairs;
    }

    /// <summary>
    /// Returns the number of characters that are part of the string literal, or -1 if the text
    /// at the starting position is not a string literal.
    /// </summary>
    public static int scanStringLiteral(String text, int start, boolean failWhenMissingEndQuote)
    {
        var pos = start;

        var ch = peek(text, pos);
        if (ch == 'h' || ch == 'H')
        {
            pos++;
            ch = peek(text, pos);
        }

        var isVerbatim = false;
        if (ch == '@')
        {
            isVerbatim = true;
            pos++;
            ch = peek(text, pos);
        }

        if (ch == '\'' || ch == '"')
        {
            pos++;

            var contentLength = scanStringLiteralContent(text, pos, ch, isVerbatim);
            pos += contentLength;

            if (peek(text, pos) == ch)
            {
                pos++;
            }
            else if (failWhenMissingEndQuote)
            {
                return -1;
            }
        }
        else if (matches(text, pos, KustoFacts.MultiLineStringQuote))
        {
            pos += KustoFacts.MultiLineStringQuote.length();
            pos += scanMultiLineStringLiteralContent(text, pos, KustoFacts.MultiLineStringQuote);

            if (matches(text, pos, KustoFacts.MultiLineStringQuote))
            {
                pos += KustoFacts.MultiLineStringQuote.length();
            }
            else if (failWhenMissingEndQuote)
            {
                return -1;
            }
        }
        else if (matches(text, pos, KustoFacts.AlternateMultiLineStringQuote))
        {
            pos += KustoFacts.AlternateMultiLineStringQuote.length();
            pos += scanMultiLineStringLiteralContent(text, pos, KustoFacts.AlternateMultiLineStringQuote);

            if (matches(text, pos, KustoFacts.AlternateMultiLineStringQuote))
            {
                pos += KustoFacts.AlternateMultiLineStringQuote.length();
            }
            else if (failWhenMissingEndQuote)
            {
                return -1;
            }
        }
        else
        {
            return -1;
        }

        return pos > start ? pos - start : -1;
    }

    public static int scanStringLiteral(String text, int start) // PORT: §3.12 optional parameter failWhenMissingEndQuote = false
    {
        return scanStringLiteral(text, start, false);
    }

    public static int scanStringLiteral(String text) // PORT: §3.12 optional parameter start = 0
    {
        return scanStringLiteral(text, 0, false);
    }

    private static int scanStringLiteralContent(String text, int start, char quote, boolean isVerbatim)
    {
        int pos = start;

        char ch;
        while (!isAtEnd(text, pos))
        {
            ch = peek(text, pos);

            if (ch == quote && isVerbatim && peek(text, pos + 1) == quote)
            {
                pos += 2;
                continue;
            }
            else if (ch == '\\' && !isVerbatim)
            {
                var escapeLen = scanStringEscape(text, pos);
                if (escapeLen > 0)
                {
                    pos += escapeLen;
                    continue;
                }
                else
                {
                    break;
                }
            }
            else if (ch == quote
                || ch == '\r'       // string only sees \r & \n as line breaks
                || ch == '\n')
            {
                break;
            }
            else
            {
                pos++;
            }
        }

        return pos - start;
    }

    private static int scanMultiLineStringLiteralContent(String text, int start, String endQuote)
    {
        var pos = start;

        while (pos < text.length() && !matches(text, pos, endQuote))
        {
            pos++;
        }

        return pos - start;
    }

    private static int scanStringEscape(String text, int start)
    {
        var ch = peek(text, start);
        if (ch == '\\')
        {
            ch = peek(text, start + 1);
            switch (ch)
            {
                case '\\':
                case '\'':
                case '"':
                case 'a':
                case 'b':
                case 'f':
                case 'n':
                case 'r':
                case 't':
                case 'v':
                    return 2;
                case 'u':
                    var len = scanFourHexDigits(text, start + 2);
                    if (len > 0)
                        return len + 2;
                    return -1;
                case 'U':
                    len = scanEightHexDigits(text, start + 2);
                    if (len > 0)
                        return len + 2;
                    return -1;
                case 'x':
                    len = scanTwoHexDigits(text, start + 2);
                    if (len > 0)
                        return len + 2;
                    return -1;
                default:
                    len = scanOctalCode(text, start + 1);
                    if (len > 0)
                        return len + 1;
                    break;
            }
        }

        // not an escape
        return -1;
    }

    private static int scanTwoHexDigits(String text, int start)
    {
        if (start + 1 < text.length()
            && TextFacts.isHexDigit(text.charAt(start))
            && TextFacts.isHexDigit(text.charAt(start + 1)))
        {
            return 2;
        }
        else
        {
            return -1;
        }
    }

    private static int scanFourHexDigits(String text, int start)
    {
        if (start + 3 < text.length()
            && TextFacts.isHexDigit(text.charAt(start))
            && TextFacts.isHexDigit(text.charAt(start + 1))
            && TextFacts.isHexDigit(text.charAt(start + 2))
            && TextFacts.isHexDigit(text.charAt(start + 3)))
        {
            return 4;
        }
        else
        {
            return -1;
        }
    }

    private static int scanEightHexDigits(String text, int start)
    {
        if (start + 7 < text.length()
            && TextFacts.isHexDigit(text.charAt(start))
            && TextFacts.isHexDigit(text.charAt(start + 1))
            && TextFacts.isHexDigit(text.charAt(start + 2))
            && TextFacts.isHexDigit(text.charAt(start + 3))
            && TextFacts.isHexDigit(text.charAt(start + 4))
            && TextFacts.isHexDigit(text.charAt(start + 5))
            && TextFacts.isHexDigit(text.charAt(start + 6))
            && TextFacts.isHexDigit(text.charAt(start + 7)))
        {
            return 8;
        }
        else
        {
            return -1;
        }
    }

    private static int scanTwelveHexDigits(String text, int start)
    {
        if (start + 11 < text.length()
            && TextFacts.isHexDigit(text.charAt(start))
            && TextFacts.isHexDigit(text.charAt(start + 1))
            && TextFacts.isHexDigit(text.charAt(start + 2))
            && TextFacts.isHexDigit(text.charAt(start + 3))
            && TextFacts.isHexDigit(text.charAt(start + 4))
            && TextFacts.isHexDigit(text.charAt(start + 5))
            && TextFacts.isHexDigit(text.charAt(start + 6))
            && TextFacts.isHexDigit(text.charAt(start + 7))
            && TextFacts.isHexDigit(text.charAt(start + 8))
            && TextFacts.isHexDigit(text.charAt(start + 9))
            && TextFacts.isHexDigit(text.charAt(start + 10))
            && TextFacts.isHexDigit(text.charAt(start + 11)))
        {
            return 12;
        }
        else
        {
            return -1;
        }
    }

    private static int scanOctalCode(String text, int start)
    {
        var ch1 = peek(text, start);
        if (ch1 >= '0' && ch1 <= '7')
        {
            var ch2 = peek(text, start + 1);
            if (ch2 >= '0' && ch2 <= '7')
            {
                var ch3 = peek(text, start + 2);
                if (ch3 >= '0' && ch3 <= '7' && ch1 <= '3')
                {
                    return 3;
                }
                else
                {
                    return 2;
                }
            }
            else
            {
                return 1;
            }
        }
        else
        {
            return -1;
        }
    }

    /// <summary>
    /// Scans the content of a parenthesized literal
    /// </summary>
    private static int scanGoo(String text, int start, ParseOptions options)
    {
        var pos = start;

        if (peek(text, pos) == '(')
        {
            pos++;

            char ch;
            while (pos < text.length()
                && (ch = text.charAt(pos)) != ')'
                // better intellisense if we stop the insanity at like break (no literal spans multiple lines since dynamic is now an expression)
                // probably can do even better for numeric literals too since we know the domain fully.
                && (options.allowLiteralsWithLineBreaks() || !TextFacts.isLineBreakStart(ch)))
            {
                pos++;
            }

            if (peek(text, pos) == ')')
                pos++;
        }

        return pos > start ? pos - start : -1;
    }

    /// <summary>
    /// Scans raw guid literals only.
    /// Does not scan guid(xxx) literals.
    /// </summary>
    public int scanRawGuidLiteral(String text, int start)
    {
        if (start + 35 < text.length()
            && scanEightHexDigits(text, start) == 8
            && text.charAt(start + 8) == '-'
            && scanFourHexDigits(text, start + 9) == 4
            && text.charAt(start + 13) == '-'
            && scanFourHexDigits(text, start + 14) == 4
            && text.charAt(start + 18) == '-'
            && scanFourHexDigits(text, start + 19) == 4
            && text.charAt(start + 23) == '-'
            && scanTwelveHexDigits(text, start + 24) == 12)
        {
            return 36;
        }
        else
        {
            return -1;
        }
    }

    /// <summary>
    /// Returns the number of characters in the client parameter of -1 if there is no client parameter.
    /// This is not a normal token, but a special case for the client editor.
    /// </summary>
    public static int scanClientParameter(
        String text, int start)
    {
        return scanClientParameter(text, start, new IntRef(), new IntRef(), new IntRef(), new IntRef()); // PORT: §3.3 out _ discards
    }

    public static int scanClientParameter(String text) // PORT: §3.12 optional parameter start = 0
    {
        return scanClientParameter(text, 0);
    }

    /// <summary>
    /// Returns the number of characters in the client parameter of -1 if there is no client parameter.
    /// This is not a normal token, but a special case for the client editor.
    /// </summary>
    public static int scanClientParameter(
        String text,
        int start,
        IntRef nameStart, // PORT: §3.3 out int
        IntRef nameLength,
        IntRef indexStart,
        IntRef indexLength)
    {
        nameStart.value = -1;
        nameLength.value = 0;
        indexStart.value = 0;
        indexLength.value = 0;

        if (peek(text, start) == '{')
        {
            nameStart.value = start + 1;
            nameLength.value = scanIdentifier(text, nameStart.value);
            if (nameLength.value > 0)
            {
                if (peek(text, nameStart.value + nameLength.value) == '}')
                {
                    return nameLength.value + 2;
                }
                else if (peek(text, nameStart.value + nameLength.value) == '[')
                {
                    indexStart.value = nameStart.value + nameLength.value + 1;
                    var indexEnd = indexStart.value;

                    // If it's a negative number, skip the negative sign
                    if (peek(text, indexEnd) == '-')
                        indexEnd++;

                    var litLen = scanLongLiteral(text, indexEnd);
                    if (litLen <= 0)
                        return -1;

                    indexEnd += litLen;

                    if (peek(text, indexEnd) == ']'
                        && peek(text, indexEnd + 1) == '}')
                    {
                        indexLength.value = indexEnd - indexStart.value;
                        return (indexEnd + 2) - start;
                    }
                }
            }
        }

        return -1;
    }

    private static char peek(String text, int position)
    {
        if (position >= 0 && position < text.length())
        {
            return text.charAt(position);
        }
        else
        {
            return '\0';
        }
    }

    private static boolean isAtEnd(String text, int position)
    {
        return position >= text.length();
    }

    private static boolean matches(String text, int start, String match)
    {
        if (start < 0 || start + match.length() > text.length())
            return false;

        switch (match.length())
        {
            case 1:
                return match.charAt(0) == text.charAt(start);
            case 2:
                return match.charAt(0) == text.charAt(start)
                    && match.charAt(1) == text.charAt(start + 1);
            case 3:
                return match.charAt(0) == text.charAt(start)
                    && match.charAt(1) == text.charAt(start + 1)
                    && match.charAt(2) == text.charAt(start + 2);
            default:
                return match.charAt(0) == text.charAt(start)
                && DotNetStrings.compare(text, start, match, 0, match.length()) == 0; // PORT: §5.4 string.Compare(..., StringComparison.Ordinal)
        }
    }

    private String getSubstring(String text, int start, int len, boolean intern)
    {
        if (_stringTable != null && intern)
        {
            return _stringTable.add(text, start, len);
        }
        else
        {
            return text.substring(start, start + len); // PORT: §5.4 Substring(start, length)
        }
    }

    private String getSubstring(String text, int start, int len) // PORT: §3.12 optional parameter intern = true
    {
        return getSubstring(text, start, len, true);
    }

    // region resusable tokens

    private static final class TokenInfo
    {
        public final SyntaxKind Kind;
        public final String Text;
        public final LexicalToken ZeroTriviaToken;
        public final LexicalToken SingleWhitespaceToken;

        public TokenInfo(SyntaxKind kind)
        {
            this.Kind = kind;
            this.Text = SyntaxFacts.getText(kind); // PORT: §3.5
            this.ZeroTriviaToken = new LexicalToken(kind, "", this.Text);
            this.SingleWhitespaceToken = new LexicalToken(kind, " ", this.Text);
        }

        public LexicalToken getToken(String trivia)
        {
            if (trivia.length() == 0)
                return this.ZeroTriviaToken;
            else if (" ".equals(trivia)) // PORT: §3.14 string == is value equality
                return this.SingleWhitespaceToken;
            else
                return new LexicalToken(this.Kind, trivia, this.Text);
        }
    }

    private static final LinkedHashMap<SyntaxKind, TokenInfo> s_kindToTokenInfoMap =
        kindToTokenInfoMap(); // PORT: §3.6 SyntaxFacts.GetKindsWithFixedText().ToDictionary(k => k, k => new TokenInfo(k))

    private static LinkedHashMap<SyntaxKind, TokenInfo> kindToTokenInfoMap() // PORT: §3.6 ToDictionary as a plain loop; §3.17 LinkedHashMap
    {
        var map = new LinkedHashMap<SyntaxKind, TokenInfo>();
        for (var k : SyntaxFacts.getKindsWithFixedText())
        {
            DotNet.dictionaryAdd(map, k, new TokenInfo(k)); // PORT: §3.17 ToDictionary throws on a duplicate key
        }
        return map;
    }

    private static final SubstringMap<TokenInfo> s_tokenInfoSubstringMap =
        new SubstringMap<TokenInfo>(tokenInfoPairs()); // PORT: §3.6 s_kindToTokenInfoMap.Select(kvp => new KeyValuePair<string, TokenInfo>(kvp.Key.GetText(), kvp.Value))

    private static List<Map.Entry<String, TokenInfo>> tokenInfoPairs() // PORT: §3.6 Select as a plain loop
    {
        var pairs = new ArrayList<Map.Entry<String, TokenInfo>>(s_kindToTokenInfoMap.size());
        for (var kvp : s_kindToTokenInfoMap.entrySet())
        {
            pairs.add(new AbstractMap.SimpleImmutableEntry<String, TokenInfo>(SyntaxFacts.getText(kvp.getKey()), kvp.getValue())); // PORT: §3.5
        }
        return pairs;
    }

    private static final TokenInfo OpenParenTokenInfo = tokenInfo(SyntaxKind.OpenParenToken);
    private static final TokenInfo CloseParenTokenInfo = tokenInfo(SyntaxKind.CloseParenToken);
    private static final TokenInfo OpenBracketTokenInfo = tokenInfo(SyntaxKind.OpenBracketToken);
    private static final TokenInfo CloseBracketTokenInfo = tokenInfo(SyntaxKind.CloseBracketToken);
    private static final TokenInfo OpenBraceTokenInfo = tokenInfo(SyntaxKind.OpenBraceToken);
    private static final TokenInfo CloseBraceTokenInfo = tokenInfo(SyntaxKind.CloseBraceToken);
    private static final TokenInfo BarTokenInfo = tokenInfo(SyntaxKind.BarToken);
    private static final TokenInfo DotDotTokenInfo = tokenInfo(SyntaxKind.DotDotToken);
    private static final TokenInfo DotTokenInfo = tokenInfo(SyntaxKind.DotToken);
    private static final TokenInfo PlusTokenInfo = tokenInfo(SyntaxKind.PlusToken);
    private static final TokenInfo MinusTokenInfo = tokenInfo(SyntaxKind.MinusToken);
    private static final TokenInfo AsteriskTokenInfo = tokenInfo(SyntaxKind.AsteriskToken);
    private static final TokenInfo SlashTokenInfo = tokenInfo(SyntaxKind.SlashToken);
    private static final TokenInfo PercentTokenInfo = tokenInfo(SyntaxKind.PercentToken);
    private static final TokenInfo LessThanOrEqualTokenInfo = tokenInfo(SyntaxKind.LessThanOrEqualToken);
    private static final TokenInfo LessThanBarTokenInfo = tokenInfo(SyntaxKind.LessThanBarToken);
    private static final TokenInfo LessThanGreaterThanTokenInfo = tokenInfo(SyntaxKind.LessThanGreaterThanToken);
    private static final TokenInfo LessThanTokenInfo = tokenInfo(SyntaxKind.LessThanToken);
    private static final TokenInfo GreaterThanOrEqualTokenInfo = tokenInfo(SyntaxKind.GreaterThanOrEqualToken);
    private static final TokenInfo GreaterThanTokenInfo = tokenInfo(SyntaxKind.GreaterThanToken);
    private static final TokenInfo EqualEqualTokenInfo = tokenInfo(SyntaxKind.EqualEqualToken);
    private static final TokenInfo FatArrowTokenInfo = tokenInfo(SyntaxKind.FatArrowToken);
    private static final TokenInfo EqualTildeTokenInfo = tokenInfo(SyntaxKind.EqualTildeToken);
    private static final TokenInfo EqualTokenInfo = tokenInfo(SyntaxKind.EqualToken);
    private static final TokenInfo BangEqualTokenInfo = tokenInfo(SyntaxKind.BangEqualToken);
    private static final TokenInfo BangTildeTokenInfo = tokenInfo(SyntaxKind.BangTildeToken);
    private static final TokenInfo ColonTokenInfo = tokenInfo(SyntaxKind.ColonToken);
    private static final TokenInfo SemicolonTokenInfo = tokenInfo(SyntaxKind.SemicolonToken);
    private static final TokenInfo CommaTokenInfo = tokenInfo(SyntaxKind.CommaToken);
    private static final TokenInfo AtTokenInfo = tokenInfo(SyntaxKind.AtToken);
    private static final TokenInfo QuestionTokenInfo = tokenInfo(SyntaxKind.QuestionToken);

    private static TokenInfo tokenInfo(SyntaxKind kind) // PORT: §3.3 Dictionary indexer s_kindToTokenInfoMap[kind] throws KeyNotFoundException when absent
    {
        var info = s_kindToTokenInfoMap.get(kind);
        if (info == null)
            throw new NoSuchElementException("The given key '" + DotNet.str(kind) + "' was not present in the dictionary."); // PORT: §3.16 KeyNotFoundException (porting/exception-map.json)
        return info;
    }

    // endregion
}
