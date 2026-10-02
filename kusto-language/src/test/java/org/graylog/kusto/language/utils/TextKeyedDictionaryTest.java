// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for TextKeyedDictionary slice lookups (Utils/TextKeyedDictionary.cs).
package org.graylog.kusto.language.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.utils.dotnet.Out;
import org.junit.jupiter.api.Test;

class TextKeyedDictionaryTest {
    @Test
    void sliceLookupEqualsFullStringLookup() {
        var d = new TextKeyedDictionary<Integer>();
        d.getOrAddValue("hello", 1);
        d.getOrAddValue("world", 2);

        var full = new Out<Integer>();
        var slice = new Out<Integer>();
        assertTrue(d.tryGetValue("hello", 0, 5, full));
        assertTrue(d.tryGetValue("say hello there", 4, 5, slice));
        assertEquals(full.value, slice.value);
        assertEquals(1, slice.value);

        assertTrue(d.tryGetValue("xxworldxx", 2, 5, slice));
        assertEquals(2, slice.value);

        assertFalse(d.tryGetValue("say hello there", 3, 5, slice));
        assertNull(slice.value);
    }

    @Test
    void containsKeyForSliceAndFullString() {
        var d = new TextKeyedDictionary<String>();
        d.getOrAddValue("abc", "v");
        assertTrue(d.containsKey("abc"));
        assertTrue(d.containsKey("xabcx", 1, 3));
        assertFalse(d.containsKey("xabcx", 0, 3));
        assertFalse(d.containsKey("abd"));
    }

    @Test
    void containsKeyRejectsEmptyAndOutOfRangeSlices() {
        var d = new TextKeyedDictionary<Integer>();
        d.getOrAddValue("", 7);
        // ContainsKey returns false for length < 1 even when the empty string was added (TextKeyedDictionary.cs:529)
        assertFalse(d.containsKey(""));
        // TryGetValue does not apply that check
        var v = new Out<Integer>();
        assertTrue(d.tryGetValue("", 0, 0, v));
        assertEquals(7, v.value);

        d.getOrAddValue("abc", 1);
        assertFalse(d.containsKey("abc", -1, 2));
        assertFalse(d.containsKey("abc", 2, 5));
    }

    @Test
    void firstValueWins() {
        var d = new TextKeyedDictionary<Integer>();
        assertEquals(1, d.getOrAddValue("a", 1));
        assertEquals(1, d.getOrAddValue("a", 2));
        assertEquals(1, d.getOrAddValue("xax", 1, 1, 3));
    }

    @Test
    void evaluatorRunsOnlyWhenAbsent() {
        var d = new TextKeyedDictionary<String>();
        int[] calls = {0};
        var a = d.getOrAddValue("say hello there", 4, 5, (t, s, l) -> {
            calls[0]++;
            return t.substring(s, s + l);
        });
        var b = d.getOrAddValue("hello", (t, s, l) -> {
            calls[0]++;
            return "other";
        });
        assertEquals("hello", a);
        assertEquals("hello", b);
        assertEquals(1, calls[0]);
    }

    @Test
    void enumeratesValuesInInsertionOrder() {
        var d = new TextKeyedDictionary<Integer>();
        d.getOrAddValue("c", 3);
        d.getOrAddValue("a", 1);
        d.getOrAddValue("b", 2);
        List<Integer> seen = new ArrayList<>();
        for (int v : d) {
            seen.add(v);
        }
        assertEquals(List.of(3, 1, 2), seen);
    }

    @Test
    void toTextKeyedDictionaryKeepsFirstDuplicate() {
        var d = TextKeyedDictionaryExtensions.toTextKeyedDictionary(
            List.of("a1", "b1", "a2"), s -> s.substring(0, 1), s -> s);
        var v = new Out<String>();
        assertTrue(d.tryGetValue("a", 0, 1, v));
        assertEquals("a1", v.value);
    }

    @Test
    void stringTableInternsSlices() {
        var t = new StringTable();
        String first = t.add("say hello there", 4, 5);
        String second = t.add("hello");
        assertEquals("hello", first);
        assertTrue(first == second);
        assertTrue(t.contains("hello"));
        assertTrue(t.containsKey("say hello there", 4, 5));
        // the whole string is stored as is, not copied
        String whole = new String("whole");
        assertTrue(t.add(whole) == whole);
    }
}
