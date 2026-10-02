// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: pins TextFacts character classes and line tables (PORTING.md 5.1).

package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.graylog.kusto.language.utils.dotnet.IntList;
import org.graylog.kusto.language.utils.dotnet.IntRef;
import org.junit.jupiter.api.Test;

class TextFactsTest {
    private static final char[] WHITESPACE = {
        '\t', ' ', '\r', '\n', '\u000c', '\u00a0', '\u1680', '\u180e',
        '\u2000', '\u2001', '\u2002', '\u2003', '\u2004', '\u2005', '\u2006', '\u2007',
        '\u2008', '\u2009', '\u200a', '\u200b', '\u202f', '\u205f', '\u3000', '\uFEFF',
    };

    @Test
    void whitespaceIsExactlyThe24CharSwitch() {
        assertEquals(24, WHITESPACE.length);
        int count = 0;
        for (int c = 0; c <= 0xFFFF; c++) {
            boolean expected = false;
            for (char w : WHITESPACE) {
                if (w == c) {
                    expected = true;
                }
            }
            assertEquals(expected, TextFacts.isWhitespace((char) c), String.format("U+%04X", c));
            if (TextFacts.isWhitespace((char) c)) {
                count++;
            }
        }
        assertEquals(24, count);
    }

    @Test
    void whitespaceIncludesZeroWidthAndBomButNotNelOrVt() {
        assertTrue(TextFacts.isWhitespace('\u200b'));
        assertTrue(TextFacts.isWhitespace('\uFEFF'));
        assertFalse(TextFacts.isWhitespace('\u0085'));
        assertFalse(TextFacts.isWhitespace('\u000B'));
        assertFalse(TextFacts.isWhitespace('\u2028'));
        assertFalse(TextFacts.isWhitespace('\u2029'));
    }

    @Test
    void lineBreakStartSet() {
        int count = 0;
        for (int c = 0; c <= 0xFFFF; c++) {
            if (TextFacts.isLineBreakStart((char) c)) {
                count++;
            }
        }
        assertEquals(4, count);
        assertTrue(TextFacts.isLineBreakStart('\r'));
        assertTrue(TextFacts.isLineBreakStart('\n'));
        assertTrue(TextFacts.isLineBreakStart('\u2028'));
        assertTrue(TextFacts.isLineBreakStart('\u2029'));
        assertFalse(TextFacts.isLineBreakStart('\u0085'));
        assertFalse(TextFacts.isLineBreakStart('\u000B'));
    }

    @Test
    void lineBreakLengthPairsCrLf() {
        assertEquals(2, TextFacts.getLineBreakLength("a\r\nb", 1));
        assertEquals(1, TextFacts.getLineBreakLength("a\r\nb", 2));
        assertEquals(1, TextFacts.getLineBreakLength("a\rb", 1));
        assertEquals(1, TextFacts.getLineBreakLength("a\r", 1));
        assertEquals(1, TextFacts.getLineBreakLength("\n\r", 0));
        assertEquals(1, TextFacts.getLineBreakLength("\u2028", 0));
        assertEquals(1, TextFacts.getLineBreakLength("\u2029", 0));
        assertEquals(0, TextFacts.getLineBreakLength("ab", 0));
        assertEquals(0, TextFacts.getLineBreakLength("ab", 5));
    }

    @Test
    void asciiOnlyLetterAndDigitClasses() {
        for (int c = 0; c <= 0xFFFF; c++) {
            char ch = (char) c;
            boolean letter = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
            boolean digit = ch >= '0' && ch <= '9';
            boolean hex = digit || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
            assertEquals(letter, TextFacts.isLetter(ch));
            assertEquals(digit, TextFacts.isDigit(ch));
            assertEquals(letter || digit, TextFacts.isLetterOrDigit(ch));
            assertEquals(hex, TextFacts.isHexDigit(ch));
        }
        assertFalse(TextFacts.isDigit('\u0663'));
        assertFalse(TextFacts.isLetter('\u00e9'));
    }

    private static List<Integer> starts(String text) {
        IntList list = TextFacts.getLineStarts(text);
        Integer[] a = new Integer[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return List.of(a);
    }

    @Test
    void lineStartTables() {
        assertEquals(List.of(0), starts(""));
        assertEquals(List.of(0), starts("abc"));
        assertEquals(List.of(0, 2, 4), starts("a\rb\rc"));
        assertEquals(List.of(0, 3, 6), starts("a\r\nb\r\nc"));
        assertEquals(List.of(0, 2, 4, 6, 8, 10), starts("a\nb\r\r\nc\u2028d\u2029"));
        assertEquals(List.of(0, 1, 2), starts("\n\r"));
        assertEquals(List.of(0, 2), starts("\r\n"));
    }

    @Test
    void lineAndOffsetRoundTrip() {
        String text = "ab\r\ncd\nef";
        IntRef line = new IntRef();
        IntRef offset = new IntRef();
        assertTrue(TextFacts.tryGetLineAndOffset(text, 5, line, offset));
        assertEquals(2, line.value);
        assertEquals(2, offset.value);
        IntRef pos = new IntRef();
        assertTrue(TextFacts.tryGetPosition(text, 2, 2, pos));
        assertEquals(5, pos.value);
        assertTrue(TextFacts.tryGetPosition(TextFacts.getLineStarts(text), 3, 1, pos));
        assertEquals(7, pos.value);
        assertFalse(TextFacts.tryGetPosition(text, 4, 1, pos));
        assertFalse(TextFacts.tryGetLineAndOffset(TextFacts.getLineStarts(text), -1, line, offset));
        assertEquals(0, line.value);
    }

    @Test
    void lineHelpers() {
        String text = "ab\r\ncd\nef";
        assertEquals(2, TextFacts.getLineEnd(text, 0));
        assertEquals(4, TextFacts.getNextLineStart(text, 0));
        assertEquals(-1, TextFacts.getNextLineStart(text, 7));
        assertEquals(4, TextFacts.getLineStart(text, 5));
        assertEquals(0, TextFacts.getLineStart(text, 1));
        assertEquals(4, TextFacts.getLineStart(text, 4));
        IntRef prev = new IntRef();
        assertTrue(TextFacts.tryGetPreviousLineEnd(text, 3, prev));
        assertEquals(2, prev.value);
        assertEquals(6, TextFacts.getLastLineBreakStart(text));
        assertEquals(7, TextFacts.getLastLineBreakEnd(text));
        assertTrue(TextFacts.hasLineBreaks("a\u2029"));
        assertFalse(TextFacts.hasLineBreaks("a\u0085b"));
        assertEquals(List.of("ab", "cd", "ef"), TextFacts.getLineTexts(text));
        assertEquals("cd", TextFacts.getLineText(text, 2));
        assertEquals("", TextFacts.getLineText(text, 9));
        assertEquals(4, TextFacts.getLineLength(text, 0, true));
    }

    @Test
    void trimAndExpandRanges() {
        String text = "  ab  ";
        IntRef start = new IntRef(0);
        IntRef length = new IntRef(6);
        TextFacts.trimRange(text, start, length);
        assertEquals(2, start.value);
        assertEquals(2, length.value);
        assertEquals(4, TextFacts.trimEnd(text));

        String lines = "ab\ncdef\ngh";
        IntRef s = new IntRef(4);
        IntRef l = new IntRef(1);
        TextFacts.expandRangeToStartAndEndOfLines(lines, s, l);
        // PORT-BUG mirrored: ExpandRangeToStartOfFirstLine never grows the length
        assertEquals(3, s.value);
        assertEquals(4, l.value);
    }
}
