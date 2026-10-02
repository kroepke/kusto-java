// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests pinning SubstringMap semantics (Utils/SubstringMap.cs).
package org.graylog.kusto.language.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SubstringMapTest {
    private static Map.Entry<String, Integer> e(String key, int value) {
        return new AbstractMap.SimpleEntry<>(key, value);
    }

    @SafeVarargs
    private static SubstringMap<Integer> mapOf(Map.Entry<String, Integer>... pairs) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(List.of(pairs));
        return new SubstringMap<>(list);
    }

    @Test
    void longestMatchWins() {
        var m = mapOf(e("a", 1), e("ab", 2), e("abc", 3));
        assertEquals("abc", m.getLongestMatch("abcd", 0).getKey());
        assertEquals(3, m.getLongestMatch("abcd", 0).getValue());
    }

    @Test
    void prefixOfLongerKeyFallsBackToLastKeyWithValue() {
        var m = mapOf(e("a", 1), e("abc", 3));
        // "ab" walks a -> b but b has no value, so the best node stays "a"
        assertEquals("a", m.getLongestMatch("abx", 0).getKey());
        assertEquals(1, m.getLongestMatch("abx", 0).getValue());
        assertEquals("a", m.getLongestMatch("ab", 0).getKey());
    }

    @Test
    void matchStartsAtStartOffset() {
        var m = mapOf(e("a", 1), e("abc", 3), e("bcd", 5));
        assertEquals("bcd", m.getLongestMatch("abcd", 1).getKey());
        assertEquals(5, m.getLongestMatch("abcd", 1).getValue());
        // the match is anchored at the start offset: nothing starts with 'c'
        var none = m.getLongestMatch("abcd", 2);
        assertEquals("", none.getKey());
        assertNull(none.getValue());
    }

    @Test
    void noMatchReturnsEmptyKeyAndNullValue() {
        var m = mapOf(e("abc", 3));
        var none = m.getLongestMatch("xyz", 0);
        assertEquals("", none.getKey());
        assertNull(none.getValue());
        // start at end of text
        var atEnd = m.getLongestMatch("abc", 3);
        assertEquals("", atEnd.getKey());
    }

    @Test
    void lastDuplicateKeyWins() {
        var m = mapOf(e("a", 1), e("a", 9));
        assertEquals(9, m.getLongestMatch("a", 0).getValue());
    }

    @Test
    void emptyKeyIsNeverReturned() {
        // the empty key sets the value on the root node, which getLongestMatch never inspects
        var m = mapOf(e("", 7), e("a", 1));
        assertEquals("", m.getLongestMatch("zzz", 0).getKey());
        assertNull(m.getLongestMatch("zzz", 0).getValue());
        assertEquals(1, m.getLongestMatch("a", 0).getValue());
    }

    @Test
    void manyChildrenConvertSingleToArrayToDictionaryMap() {
        // one child: SingleCharMap, two: ArrayCharMap, third and later: DictionaryMap
        var m = mapOf(e("a", 1), e("b", 2), e("c", 3), e("d", 4), e("e", 5), e("ea", 6));
        assertEquals(1, m.getLongestMatch("a", 0).getValue());
        assertEquals(2, m.getLongestMatch("b", 0).getValue());
        assertEquals(3, m.getLongestMatch("c", 0).getValue());
        assertEquals(4, m.getLongestMatch("d", 0).getValue());
        assertEquals(5, m.getLongestMatch("e", 0).getValue());
        assertEquals(6, m.getLongestMatch("ea", 0).getValue());
        assertNull(m.getLongestMatch("f", 0).getValue());
    }

    @Test
    void nulCharacterLookupFindsNothingAfterConversion() {
        // PORT-BUG pin: the DictionaryMap built from the 3-slot ArrayCharMap holds '\0' -> null
        var m = mapOf(e("a", 1), e("b", 2), e("c", 3));
        var none = m.getLongestMatch("\0", 0);
        assertEquals("", none.getKey());
        assertNull(none.getValue());
    }

    @Test
    void nulKeyAsThirdChildThrowsLikeDictionaryAdd() {
        // PORT-BUG pin: the unused default slot ('\0', null) collides with a real '\0' key (Dictionary.Add throws)
        assertThrows(IllegalArgumentException.class, () -> mapOf(e("a", 1), e("b", 2), e("\0", 3)));
    }
}
