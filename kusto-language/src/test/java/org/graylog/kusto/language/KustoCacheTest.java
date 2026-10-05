// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: pins KustoCache.getOrCreate (both factories), instance reuse, tryGetValue and withGlobals (PORTING.md 3.10).

package org.graylog.kusto.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.graylog.kusto.language.utils.dotnet.Out;
import org.junit.jupiter.api.Test;

class KustoCacheTest {
    @Test
    void getOrCreateByClassInstantiatesOnceAndReuses() {
        KustoCache cache = new KustoCache(null);
        StringBuilder first = cache.getOrCreate(StringBuilder.class);
        assertSame(first, cache.getOrCreate(StringBuilder.class));
    }

    @Test
    void getOrCreateKeysByClass() {
        KustoCache cache = new KustoCache(null);
        StringBuilder builder = cache.getOrCreate(StringBuilder.class);
        ArrayList<?> list = cache.getOrCreate(ArrayList.class);
        assertNotSame(builder, list);
        assertSame(list, cache.getOrCreate(ArrayList.class));
    }

    @Test
    void getOrCreateWithSupplierCachesTheFirstResult() {
        KustoCache cache = new KustoCache(null);
        AtomicInteger calls = new AtomicInteger();
        ArrayList<String> value = cache.getOrCreate(ArrayList.class, () -> {
            calls.incrementAndGet();
            return new ArrayList<String>();
        });
        // PORT-BUG mirrored: upstream (KustoCache.cs:54-56) calls creator() twice on a miss
        assertEquals(2, calls.get());
        assertSame(value, cache.getOrCreate(ArrayList.class, () -> {
            calls.incrementAndGet();
            return new ArrayList<String>();
        }));
        assertEquals(2, calls.get());
        assertSame(value, cache.getOrCreate(ArrayList.class));
    }

    @Test
    void getOrCreateByClassAfterSupplierReusesInstance() {
        KustoCache cache = new KustoCache(null);
        StringBuilder supplied = new StringBuilder("x");
        assertSame(supplied, cache.getOrCreate(StringBuilder.class, () -> supplied));
        assertSame(supplied, cache.getOrCreate(StringBuilder.class));
    }

    @Test
    void tryGetValue() {
        KustoCache cache = new KustoCache(null);
        Out<StringBuilder> out = new Out<>();
        assertFalse(cache.tryGetValue(StringBuilder.class, out));
        assertNull(out.value);
        StringBuilder created = cache.getOrCreate(StringBuilder.class);
        assertTrue(cache.tryGetValue(StringBuilder.class, out));
        assertSame(created, out.value);
    }

    @Test
    void withGlobalsReusesWhenGlobalsAreSame() {
        KustoCache cache = new KustoCache(null);
        assertSame(cache, cache.withGlobals(null));
        GlobalState globals = GlobalState.default_();
        KustoCache other = cache.withGlobals(globals);
        assertNotSame(cache, other);
        assertSame(globals, other.globals());
        assertSame(other, other.withGlobals(globals));
    }
}
