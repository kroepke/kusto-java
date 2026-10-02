// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests pinning StringAndNumberComparer and its mirrored quirks (Utils/StringAndNumberComparer.cs).
package org.graylog.kusto.language.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StringAndNumberComparerTest {
    private static int ord(String x, String y) {
        return StringAndNumberComparer.Ordinal.compare(x, y);
    }

    private static int ci(String x, String y) {
        return StringAndNumberComparer.OrdinalIgnoreCase.compare(x, y);
    }

    @Test
    void numbersOfDifferentLengthOrderByDigitCount() {
        // CompareNumberSegment returns xLength - yLength (StringAndNumberComparer.cs:117)
        assertEquals(-1, ord("a2", "a10"));
        assertEquals(1, ord("a10", "a2"));
        assertEquals(-2, ord("a9", "a100"));
        assertEquals(0, ord("a2", "a2"));
    }

    @Test
    void embeddedNumbersCompareNumericallyWithinOneSegmentLength() {
        assertTrue(ord("file2x", "file10x") < 0);
        assertTrue(ord("file10x", "file2x") > 0);
        assertTrue(ord("a123", "a124") < 0);
        assertTrue(ord("a124", "a123") > 0);
    }

    @Test
    void portBugLeadingZerosDoNotShortenTheNumber() {
        // PORT-BUG pin, StringAndNumberComparer.cs:105-110: the zero skips never reduce xLength/yLength,
        // so unequal-length numbers return the difference of the ORIGINAL lengths.
        // "a02" vs "a2": 2 - 1 = 1, although both are numerically 2.
        assertEquals(1, ord("a02", "a2"));
        assertEquals(-1, ord("a2", "a02"));
        // "a007" vs "a8": 3 - 1 = 2, although 7 < 8.
        assertEquals(2, ord("a007", "a8"));
        // equal length falls back to a text comparison (:113-114): "007" < "008"
        assertTrue(ord("a007", "a008") < 0);
        assertEquals(0, ord("a007", "a007"));
    }

    @Test
    void numberVersusTextReturnsRawCharacterDifference() {
        // StringAndNumberComparer.cs:63: 'b' (98) - '1' (49)
        assertEquals(49, ord("ab", "a1"));
        assertEquals(-49, ord("a1", "ab"));
        assertEquals('!' - '9', ord("!", "9"));
    }

    @Test
    void remainingTextOrdersAfterAndComparesToPlusMinusOne() {
        // StringAndNumberComparer.cs:70-85
        assertEquals(1, ord("abc", "ab"));
        assertEquals(-1, ord("ab", "abc"));
        assertEquals(1, ord("a1", "a"));
        assertEquals(-1, ord("a", "a1"));
        assertEquals(0, ord("", ""));
        assertEquals(-1, ord("", "a"));
        assertEquals(1, ord("a", ""));
    }

    @Test
    void textSegmentsStopAtTheFirstDigit() {
        // the text run "ab" is compared up to the shared digit-free length, then the numbers
        assertTrue(ord("ab1", "ab2") < 0);
        assertTrue(ord("ab9", "abc") < 0); // digit vs letter: '9' - 'c' < 0
    }

    @Test
    void ordinalIsCaseSensitiveAndIgnoreCaseIsNot() {
        assertTrue(ord("A1", "a1") < 0); // 'A' (65) < 'a' (97)
        assertEquals(0, ci("A1", "a1"));
        assertEquals(0, ci("abc", "ABC"));
        assertEquals(-1, ci("a2", "A10"));
    }

    @Test
    void nonAsciiDigitsAreDigitsButComparedAsText() {
        // char.IsDigit accepts U+0663 (ARABIC-INDIC DIGIT THREE); both segments are numbers of length 1,
        // so the segments are compared as text, not by numeric value (:114)
        assertEquals(0, ord("a٣", "a٣"));
        assertTrue(ord("a٣", "a3") > 0);
        assertTrue(ord("a3", "a٣") < 0);
    }
}
