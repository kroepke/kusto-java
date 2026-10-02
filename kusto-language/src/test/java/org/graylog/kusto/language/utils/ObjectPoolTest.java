// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for ObjectPool (Utils/ObjectPool.cs).
package org.graylog.kusto.language.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ObjectPoolTest {
    @Test
    void allocatesNewWhenEmptyAndReusesReturnedItem() {
        var created = new AtomicInteger();
        var pool = new ObjectPool<StringBuilder>(() -> {
            created.incrementAndGet();
            return new StringBuilder();
        }, sb -> sb.setLength(0));

        var a = pool.allocateFromPool();
        assertEquals(1, created.get());
        a.append("dirty");
        pool.returnToPool(a);
        assertEquals(0, a.length()); // resetter ran

        var b = pool.allocateFromPool();
        assertSame(a, b);
        assertEquals(1, created.get());

        var c = pool.allocateFromPool(); // pool is empty again
        assertNotSame(b, c);
        assertEquals(2, created.get());
    }

    @Test
    void resetterRunsEvenWhenThePoolIsFull() {
        var resets = new AtomicInteger();
        var pool = new ObjectPool<Object>(Object::new, o -> resets.incrementAndGet(), 1);
        var a = new Object();
        var b = new Object();
        pool.returnToPool(a);
        pool.returnToPool(b); // no room: dropped, but still reset (ObjectPool.cs:44)
        assertEquals(2, resets.get());
        assertSame(a, pool.allocateFromPool());
        assertNotSame(b, pool.allocateFromPool());
    }

    @Test
    void poolHoldsAtMostItsSize() {
        var pool = new ObjectPool<Object>(Object::new, o -> { }, 3);
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            items.add(new Object());
        }
        for (Object o : items) {
            pool.returnToPool(o);
        }
        Set<Object> got = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < 3; i++) {
            got.add(pool.allocateFromPool());
        }
        assertEquals(3, got.size());
        assertTrue(items.containsAll(got));
        assertTrue(!items.contains(pool.allocateFromPool())); // the fourth is newly created
    }

    @Test
    void concurrentUseNeverHandsOutTheSameItemTwice() throws Exception {
        var pool = new ObjectPool<AtomicInteger>(AtomicInteger::new, a -> a.set(0), 4);
        var failures = new AtomicInteger();
        Runnable work = () -> {
            for (int i = 0; i < 20_000; i++) {
                var item = pool.allocateFromPool();
                if (item.incrementAndGet() != 1) {
                    failures.incrementAndGet(); // someone else holds it
                }
                pool.returnToPool(item);
            }
        };
        var threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(work);
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        assertEquals(0, failures.get());
    }
}
