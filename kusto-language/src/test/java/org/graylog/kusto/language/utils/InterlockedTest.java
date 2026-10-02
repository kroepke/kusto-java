// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for Interlocked over VarHandles (Utils/Interlocked.cs, PORTING.md 3.13).
package org.graylog.kusto.language.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.junit.jupiter.api.Test;

class InterlockedTest {
    private static final class Holder {
        volatile Object ref;
        volatile int count;
        static volatile Object sref;
        static volatile int scount;
    }

    private static final VarHandle REF = Interlocked.handle(Holder.class, "ref", Object.class);
    private static final VarHandle COUNT = Interlocked.handle(Holder.class, "count", int.class);
    private static final VarHandle SREF = Interlocked.staticHandle(Holder.class, "sref", Object.class);
    private static final VarHandle SCOUNT = Interlocked.staticHandle(Holder.class, "scount", int.class);

    @Test
    void instanceReferenceCompareExchangeSucceedsOnReferenceMatch() {
        var h = new Holder();
        var value = new Object();
        assertNull(Interlocked.compareExchange(REF, h, value, null)); // returns the original value
        assertSame(value, h.ref);
    }

    @Test
    void instanceReferenceCompareExchangeFailsOnMismatchAndReturnsCurrent() {
        var h = new Holder();
        var first = new Object();
        h.ref = first;
        var second = new Object();
        assertSame(first, Interlocked.compareExchange(REF, h, second, null));
        assertSame(first, h.ref); // unchanged
        assertSame(first, Interlocked.compareExchange(REF, h, second, first));
        assertSame(second, h.ref);
    }

    @Test
    void referenceComparisonIsByIdentityNotEquals() {
        var h = new Holder();
        String a = new String("same");
        String b = new String("same");
        h.ref = a;
        assertSame(a, Interlocked.compareExchange(REF, h, "new", b)); // b is equal but not identical: no swap
        assertSame(a, h.ref);
    }

    @Test
    void instanceIntCompareExchange() {
        var h = new Holder();
        h.count = 1;
        assertEquals(1, Interlocked.compareExchange(COUNT, h, 0, 1)); // SafeList._isOwner pattern
        assertEquals(0, h.count);
        assertEquals(0, Interlocked.compareExchange(COUNT, h, 0, 1)); // second attempt loses
        assertEquals(0, h.count);
    }

    @Test
    void staticReferenceAndIntCompareExchange() {
        Holder.sref = null;
        var v = new Object();
        assertNull(Interlocked.compareExchange(SREF, v, null));
        assertSame(v, Holder.sref);
        assertSame(v, Interlocked.compareExchange(SREF, new Object(), null));
        assertSame(v, Holder.sref);

        Holder.scount = 5;
        assertEquals(5, Interlocked.compareExchange(SCOUNT, 6, 5));
        assertEquals(6, Holder.scount);
        assertEquals(6, Interlocked.compareExchange(SCOUNT, 7, 5));
        assertEquals(6, Holder.scount);
    }

    @Test
    void exchangeReturnsPreviousValue() {
        var h = new Holder();
        var a = new Object();
        var b = new Object();
        assertNull(Interlocked.exchange(REF, h, a));
        assertSame(a, Interlocked.exchange(REF, h, b));
        assertSame(b, h.ref);

        Holder.sref = a;
        assertSame(a, Interlocked.exchange(SREF, b));
        assertSame(b, Holder.sref);
    }

    @Test
    void atomicReferenceArrayExchangeAndCompareExchange() {
        var array = new AtomicReferenceArray<String>(2);
        assertNull(Interlocked.exchange(array, 0, "x"));
        assertEquals("x", Interlocked.exchange(array, 0, null));
        assertNull(array.get(0));

        assertNull(Interlocked.compareExchange(array, 1, "y", null)); // ObjectPool.ReturnToPool pattern
        assertEquals("y", array.get(1));
        assertEquals("y", Interlocked.compareExchange(array, 1, "z", null));
        assertEquals("y", array.get(1));
    }

    @Test
    void concurrentIntCompareExchangeGrantsOwnershipToExactlyOneThread() throws Exception {
        for (int round = 0; round < 200; round++) {
            var h = new Holder();
            h.count = 1;
            var winners = new AtomicInteger();
            var threads = new Thread[4];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    if (Interlocked.compareExchange(COUNT, h, 0, 1) == 1) {
                        winners.incrementAndGet();
                    }
                });
            }
            for (Thread t : threads) {
                t.start();
            }
            for (Thread t : threads) {
                t.join();
            }
            assertEquals(1, winners.get());
        }
    }

    @Test
    void handleForUnknownFieldFails() {
        assertThrows(ExceptionInInitializerError.class, () -> Interlocked.handle(Holder.class, "missing", int.class));
    }
}
