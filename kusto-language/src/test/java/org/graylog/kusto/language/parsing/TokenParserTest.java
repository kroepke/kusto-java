// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit checks of TokenParser paths the goldens do not isolate.

package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.*;

import org.graylog.kusto.language.DiagnosticLocationKind;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.utils.dotnet.IntRef;
import org.junit.jupiter.api.Test;

class TokenParserTest {
    private static LexicalToken[] lex(String text) {
        return TokenParser.parseTokens(text, ParseOptions.Default.withAlwaysProduceEndTokens(true));
    }

    private static String ch(int c) {
        return String.valueOf((char) c);
    }

    @Test
    void punctuationTokensShareInstancesForEmptyAndSingleSpaceTrivia() {
        LexicalToken[] a = lex("(");
        LexicalToken[] b = lex("x (");
        LexicalToken[] c = lex("x  (");
        assertSame(a[0], TokenParser.parseToken("(", 0));
        assertSame(b[1], TokenParser.parseToken(" (", 0));
        assertNotSame(b[1], c[1]);
        assertEquals("  ", c[1].trivia());
    }

    @Test
    void endOfTextRules() {
        LexicalToken[] withEnd = lex("x");
        assertEquals(SyntaxKind.EndOfTextToken, withEnd[withEnd.length - 1].kind());
        LexicalToken[] noEnd = TokenParser.parseTokens("x", ParseOptions.Default.withAlwaysProduceEndTokens(false));
        assertEquals(1, noEnd.length);
        LexicalToken[] trailingTrivia = TokenParser.parseTokens("x ", ParseOptions.Default.withAlwaysProduceEndTokens(false));
        assertEquals(2, trailingTrivia.length);
        assertEquals(SyntaxKind.EndOfTextToken, trailingTrivia[1].kind());
        assertEquals(" ", trailingTrivia[1].trivia());
        assertEquals(1, lex("").length);
        assertEquals(0, TokenParser.parseTokens("", ParseOptions.Default.withAlwaysProduceEndTokens(false)).length);
    }

    @Test
    void badTokenPerUtf16CodeUnit() {
        String pair = new String(Character.toChars(0x1F600));
        LexicalToken[] t = lex(pair);
        assertEquals(3, t.length);
        assertEquals(SyntaxKind.BadToken, t[0].kind());
        assertEquals(ch(0xD83D), t[0].text());
        assertEquals(SyntaxKind.BadToken, t[1].kind());
        assertEquals(ch(0xDE00), t[1].text());
        assertEquals("KS002", t[0].diagnostics().get(0).code());
    }

    @Test
    void unicodeDigitLexesAsLongLiteral() {
        LexicalToken[] t = lex(ch(0x0663));
        assertEquals(SyntaxKind.LongLiteralToken, t[0].kind());
        assertEquals(ch(0x0663), t[0].text());
    }

    @Test
    void whitespaceSwitchDrivesTrivia() {
        LexicalToken[] t = lex(ch(0x200B) + "x" + ch(0xFEFF));
        assertEquals(SyntaxKind.IdentifierToken, t[0].kind());
        assertEquals(ch(0x200B), t[0].trivia());
        assertEquals(ch(0xFEFF), t[1].trivia());
        LexicalToken[] nel = lex(ch(0x0085));
        assertEquals(SyntaxKind.BadToken, nel[0].kind());
    }

    @Test
    void gooLiteralMissingCloseParen() {
        LexicalToken[] t = lex("datetime(2020-01-01\n)");
        assertEquals(SyntaxKind.DateTimeLiteralToken, t[0].kind());
        assertEquals("datetime(2020-01-01", t[0].text());
        assertEquals("KS001", t[0].diagnostics().get(0).code());
        assertEquals(DiagnosticLocationKind.RelativeEnd, t[0].diagnostics().get(0).locationKind());
        LexicalToken[] allowed = TokenParser.parseTokens("datetime(2020-01-01\n)",
                ParseOptions.Default.withAllowLiteralsWithLineBreaks(true));
        assertEquals("datetime(2020-01-01\n)", allowed[0].text());
        assertTrue(allowed[0].diagnostics().isEmpty());
    }

    @Test
    void directiveAndStrings() {
        LexicalToken[] t = lex("#connect cluster('a')\nT");
        assertEquals(SyntaxKind.DirectiveToken, t[0].kind());
        assertEquals("#connect cluster('a')", t[0].text());
        assertEquals("\n", t[1].trivia());

        assertEquals(SyntaxKind.StringLiteralToken, lex("h'secret'")[0].kind());
        assertEquals(SyntaxKind.StringLiteralToken, lex("H@\"x\"\"y\"")[0].kind());
        assertEquals("H@\"x\"\"y\"", lex("H@\"x\"\"y\"")[0].text());
        assertEquals("```a\nb```", lex("```a\nb```")[0].text());
        LexicalToken[] open = lex("~~~abc");
        assertEquals("~~~abc", open[0].text());
        assertEquals("Missing: ~~~", open[0].diagnostics().get(0).message());
        assertEquals("'abc", lex("'abc\n'")[0].text());
    }

    @Test
    void identifierStringsAreInternedPerParse() {
        LexicalToken[] t = lex("abc abc");
        assertSame(t[0].text(), t[1].text());
    }

    @Test
    void scanHelpers() {
        assertEquals(4, TokenParser.scanStringLiteral("'ab'"));
        assertEquals(3, TokenParser.scanStringLiteral("'ab"));
        assertEquals(-1, TokenParser.scanStringLiteral("'ab", 0, true));
        assertEquals(-1, TokenParser.scanStringLiteral("abc"));
        assertEquals(4, TokenParser.scanBooleanLiteral("true"));
        assertEquals(-1, TokenParser.scanBooleanLiteral("trueX"));
        assertEquals(4, TokenParser.scanTimespanLiteral("1day"));
        assertEquals(5, TokenParser.scanTimespanLiteral("1.5ms"));
        assertEquals(-1, TokenParser.scanTimespanLiteral("1dayz"));
        assertEquals(4, TokenParser.scanLongLiteral("0x1F"));
        assertEquals(-1, TokenParser.scanLongLiteral("12a"));
        assertEquals(3, TokenParser.scanRealLiteral("1.5"));
        assertEquals(2, TokenParser.scanRealLiteral("1...", 0));
        assertEquals(-1, TokenParser.scanRealLiteral("1..2"));
        assertEquals(3, TokenParser.scanIdentifier("1ab"));
        assertEquals(-1, TokenParser.scanIdentifier("12"));
        assertEquals(6, TokenParser.scanTrivia("  // x", 0));
        assertEquals(2, TokenParser.scanWhitespace("  x", 0));
        assertEquals(5, TokenParser.scanComment("// x\ny", 0));
    }

    @Test
    void clientParameter() {
        IntRef nameStart = new IntRef();
        IntRef nameLength = new IntRef();
        IntRef indexStart = new IntRef();
        IntRef indexLength = new IntRef();
        assertEquals(5, TokenParser.scanClientParameter("{abc}"));
        assertEquals(9, TokenParser.scanClientParameter("{abc[-1]}", 0, nameStart, nameLength, indexStart, indexLength));
        assertEquals(1, nameStart.value);
        assertEquals(3, nameLength.value);
        assertEquals(5, indexStart.value);
        assertEquals(2, indexLength.value);
        assertEquals(-1, TokenParser.scanClientParameter("{abc[x]}"));
    }
}
