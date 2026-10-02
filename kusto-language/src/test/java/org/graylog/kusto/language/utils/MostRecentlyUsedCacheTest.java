// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for MostRecentlyUsedCache (Utils/MostRecentlyUsedCache.cs).
package org.graylog.kusto.language.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.graylog.kusto.language.utils.dotnet.Out;
import org.junit.jupiter.api.Test;

class MostRecentlyUsedCacheTest {
    private static Integer get(MostRecentlyUsedCache<String, Integer> cache, String key) {
        var out = new Out<Integer>();
        return cache.tryGetValue(key, out) ? out.value : null;
    }

    @Test
    void addAndGet() {
        var cache = new MostRecentlyUsedCache<String, Integer>();
        cache.addOrUpdate("a", 1);
        assertEquals(1, cache.count());
        assertEquals(1, get(cache, "a"));
        var out = new Out<Integer>();
        assertFalse(cache.tryGetValue("missing", out));
        assertNull(out.value);
    }

    @Test
    void evictsLeastRecentlyUsedBeyondMaxSize() {
        var cache = new MostRecentlyUsedCache<String, Integer>(3);
        cache.addOrUpdate("a", 1);
        cache.addOrUpdate("b", 2);
        cache.addOrUpdate("c", 3);
        cache.addOrUpdate("d", 4); // evicts a, the oldest
        assertEquals(3, cache.count());
        assertNull(get(cache, "a"));
        assertEquals(2, get(cache, "b"));
        assertEquals(3, get(cache, "c"));
        assertEquals(4, get(cache, "d"));
    }

    @Test
    void getMovesTheEntryToTheFront() {
        var cache = new MostRecentlyUsedCache<String, Integer>(3);
        cache.addOrUpdate("a", 1);
        cache.addOrUpdate("b", 2);
        cache.addOrUpdate("c", 3);
        assertEquals(1, get(cache, "a")); // order is now a, c, b
        cache.addOrUpdate("d", 4); // evicts b
        assertNull(get(cache, "b"));
        assertEquals(1, get(cache, "a"));
        assertEquals(3, get(cache, "c"));
        assertEquals(4, get(cache, "d"));
    }

    @Test
    void portBugUpdateKeepsTheOldValue() {
        // PORT-BUG pin, MostRecentlyUsedCache.cs:66: 'value = pair.Value' overwrites the new value with the old one (D18)
        var cache = new MostRecentlyUsedCache<String, Integer>();
        cache.addOrUpdate("a", 1);
        cache.addOrUpdate("a", 99);
        assertEquals(1, get(cache, "a"));
        assertEquals(1, cache.count());
    }

    @Test
    void updateMovesTheEntryToTheFrontAndDoesNotEvict() {
        var cache = new MostRecentlyUsedCache<String, Integer>(2);
        cache.addOrUpdate("a", 1);
        cache.addOrUpdate("b", 2); // order b, a
        cache.addOrUpdate("a", 5); // order a, b; no eviction
        assertEquals(2, cache.count());
        cache.addOrUpdate("c", 3); // evicts b
        assertNull(get(cache, "b"));
        assertTrue(get(cache, "a") != null);
        assertEquals(3, get(cache, "c"));
    }
}
