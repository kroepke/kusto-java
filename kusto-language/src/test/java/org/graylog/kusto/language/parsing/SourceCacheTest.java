// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for Source.cache() and SourceCache (Combinators/Source.cs).

package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.graylog.kusto.language.utils.dotnet.Out;
import org.junit.jupiter.api.Test;

class SourceCacheTest {
    public static final class Memo {
        public final ArrayList<String> items = new ArrayList<>();

        public Memo() {
        }
    }

    @Test
    void sourceCacheIsCreatedOnceAndShared() {
        var source = new TextSource("abc");
        var cache = source.cache();
        assertNotNull(cache);
        assertSame(cache, source.cache());
        assertNotSame(cache, new TextSource("abc").cache());
    }

    @Test
    void getOrCreateByTypeInstantiatesOncePerType() {
        var cache = new SourceCache();
        var m1 = cache.getOrCreate(Memo.class);
        var m2 = cache.getOrCreate(Memo.class);
        assertSame(m1, m2);
    }

    @Test
    void getOrCreateWithCreatorCallsCreatorTwiceOnMiss() {
        // PORT-BUG mirror: upstream evaluates creator() twice and keeps the second value (Source.cs:63-64)
        var cache = new SourceCache();
        var calls = new AtomicInteger();
        var value = cache.getOrCreate(StringBuilder.class, () -> new StringBuilder("v" + calls.incrementAndGet()));
        assertEquals(2, calls.get());
        assertEquals("v2", value.toString());

        // a hit does not call the creator
        var again = cache.getOrCreate(StringBuilder.class, () -> new StringBuilder("v" + calls.incrementAndGet()));
        assertSame(value, again);
        assertEquals(2, calls.get());
    }

    @Test
    void tryGetValue() {
        var cache = new SourceCache();
        var out = new Out<Memo>();
        assertFalse(cache.tryGetValue(Memo.class, out));
        assertNull(out.value);

        var memo = cache.getOrCreate(Memo.class);
        assertTrue(cache.tryGetValue(Memo.class, out));
        assertSame(memo, out.value);
    }
}
