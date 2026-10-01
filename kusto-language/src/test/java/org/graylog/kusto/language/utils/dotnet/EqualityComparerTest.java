// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for EqualityComparer.

package org.graylog.kusto.language.utils.dotnet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;

class EqualityComparerTest {
    @Test
    void ignoreCase() {
        EqualityComparer<String> c = EqualityComparer.ORDINAL_IGNORE_CASE;
        assertTrue(c.equals("a", "A"));
        assertEquals(c.hashCode("abc"), c.hashCode("ABC"));
        assertFalse(c.equals("a", "ab"));
        assertTrue(c.equals(null, null));
        assertFalse(c.equals(null, "a"));
        // Character.toUpperCase('\u212A') (Kelvin sign) is itself, and 'k' upper-cases to 'K' (U+004B).
        // So Kelvin sign is NOT equal to "K" or "k" here; no lower-casing is involved.
        assertFalse(c.equals("\u212A", "K"));
        assertFalse(c.equals("\u212A", "k"));
        // Per-char upper-casing never expands: sharp s stays one char.
        assertFalse(c.equals("\u00DF", "SS"));
    }

    @Test
    void ordinal() {
        assertTrue(EqualityComparer.ORDINAL.equals("a", "a"));
        assertFalse(EqualityComparer.ORDINAL.equals("a", "A"));
        assertEquals("a".hashCode(), EqualityComparer.ORDINAL.hashCode("a"));
    }

    @Test
    void defaultComparer() {
        EqualityComparer<Integer> c = EqualityComparer.defaultComparer();
        assertTrue(c.equals(1, 1));
        assertFalse(c.equals(1, null));
        assertEquals(0, c.hashCode(null));
    }

    @Test
    void keyInMap() {
        Map<EqualityComparer.Key<String>, Integer> m = new LinkedHashMap<>();
        EqualityComparer<String> c = EqualityComparer.ORDINAL_IGNORE_CASE;
        m.put(EqualityComparer.of("Foo", c), 1);
        m.put(EqualityComparer.of("bar", c), 2);
        m.put(EqualityComparer.of("FOO", c), 3);
        assertEquals(2, m.size());
        assertEquals(3, m.get(EqualityComparer.of("foo", c)));
        assertEquals("Foo", m.keySet().iterator().next().value());
    }
}
