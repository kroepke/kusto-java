// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for TextSource (Combinators/TextSource.cs).

package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TextSourceTest {
    @Test
    void peekAndIsEndRespectSlice() {
        var source = new TextSource("abcdef", 1, 3); // "bcd"
        assertEquals('b', source.peek());
        assertEquals('b', source.peek(0));
        assertEquals('d', source.peek(2));
        assertEquals('\0', source.peek(3)); // past the slice end
        assertFalse(source.isEnd());
        assertFalse(source.isEnd(2));
        assertTrue(source.isEnd(3));
        assertEquals(1, source.position());
    }

    @Test
    void peekTextInternsSubstrings() {
        var source = new TextSource("abcabc");
        var t1 = source.peekText(0, 3);
        var t2 = source.peekText(3, 3);
        assertEquals("abc", t1);
        assertSame(t1, t2); // StringTable returns the instance already in the table
        assertEquals("ab", source.peekText(2));
    }

    @Test
    void matchesIsOrdinalAndChecksFirstChar() {
        var source = new TextSource("xx Hello", 3, 5);
        assertTrue(source.matches(0, "Hello"));
        assertTrue(source.matches(0, "He"));
        assertFalse(source.matches(0, "hello"));
        assertFalse(source.matches(0, ""));
        assertFalse(source.matches(5, "x")); // past the end of the underlying string
        // Matches compares against the whole underlying string, not the slice (mirrors upstream)
        assertTrue(new TextSource("Hello world", 0, 5).matches(0, "Hello world"));
        // the clamped region compare: "Hello" vs "Hello!" compares 5 vs 6 chars -> not equal
        assertFalse(source.matches(0, "Hello!"));
    }

    @Test
    void matchesIgnoreCase() {
        var source = new TextSource("Hello");
        assertTrue(source.matches(0, "hELLO", true));
        assertFalse(source.matches(0, "hELLO", false));
        assertTrue(source.matches(1, "ELL", true));
    }
}
