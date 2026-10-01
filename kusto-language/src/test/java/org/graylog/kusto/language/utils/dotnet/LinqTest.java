// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for Linq.

package org.graylog.kusto.language.utils.dotnet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

class LinqTest {
    private static <T> List<T> l(T... v) {
        return Arrays.asList(v);
    }

    private static final class P {
        final String name;
        final int key;

        P(String name, int key) {
            this.name = name;
            this.key = key;
        }
    }

    @Test
    void selectWhere() {
        assertEquals(l(2, 4, 6), Linq.select(l(1, 2, 3), x -> x * 2));
        assertEquals(l("a0", "b1"), Linq.selectIndexed(l("a", "b"), (s, i) -> s + i));
        assertEquals(l(2, 4), Linq.where(l(1, 2, 3, 4), x -> x % 2 == 0));
        assertThrows(NullPointerException.class, () -> Linq.select(null, x -> x));
    }

    @Test
    void anyAll() {
        assertFalse(Linq.any(l()));
        assertTrue(Linq.any(l(1)));
        assertTrue(Linq.any(l(1, 2), x -> x == 2));
        assertFalse(Linq.any(l(1, 2), x -> x == 3));
        assertTrue(Linq.all(l(), x -> false));
        assertFalse(Linq.all(l(1, 2), x -> x == 1));
    }

    @Test
    void firstLast() {
        assertEquals(1, Linq.first(l(1, 2)));
        assertEquals(2, Linq.first(l(1, 2), x -> x > 1));
        assertThrows(IllegalStateException.class, () -> Linq.first(l()));
        assertThrows(IllegalStateException.class, () -> Linq.first(l(1), x -> x > 1));
        assertNull(Linq.firstOrDefault(l()));
        assertNull(Linq.firstOrDefault(l(1), x -> x > 1));
        assertEquals(1, Linq.firstOrDefault(l(1, 2)));
        assertEquals(2, Linq.firstOrDefault(l(1, 2), x -> x > 1));
        assertEquals(2, Linq.last(l(1, 2)));
        assertEquals(3, Linq.last(l(1, 2, 3, 4), x -> x < 4));
        assertThrows(IllegalStateException.class, () -> Linq.last(l()));
        assertThrows(IllegalStateException.class, () -> Linq.last(l(1), x -> x > 1));
        assertNull(Linq.lastOrDefault(l()));
        assertNull(Linq.lastOrDefault(l(1), x -> x > 1));
        assertEquals(2, Linq.lastOrDefault(l(1, 2)));
        assertEquals(3, Linq.lastOrDefault(l(1, 2, 3, 4), x -> x < 4));
    }

    @Test
    void concatAppend() {
        assertEquals(l(1, 2, 3), Linq.concat(l(1), l(2, 3)));
        assertEquals(l(1, 2), Linq.append(l(1), 2));
    }

    @Test
    void distinctKeepsFirstAndOrder() {
        assertEquals(l(3, 1, 2), Linq.distinct(l(3, 1, 3, 2, 1)));
        List<P> r = Linq.distinctBy(l(new P("a", 1), new P("b", 2), new P("c", 1)), p -> p.key);
        assertEquals(2, r.size());
        assertEquals("a", r.get(0).name);
        assertEquals("b", r.get(1).name);
    }

    @Test
    void exceptOrder() {
        assertEquals(l(5, 3, 4), Linq.except(l(5, 1, 3, 5, 2, 4, 3), l(1, 2, 9)));
        assertEquals(l(1), Linq.except(l(1, 1), l()));
    }

    @Test
    void ofTypeCast() {
        List<Object> mixed = l(1, "a", 2, null);
        assertEquals(l(1, 2), Linq.ofType(mixed, Integer.class));
        assertEquals(l("a"), Linq.ofType(mixed, String.class));
        assertThrows(ClassCastException.class, () -> Linq.cast(mixed, Integer.class));
        assertEquals(l(1, 2), Linq.cast(l(1, 2), Integer.class));
    }

    @Test
    void toListToArray() {
        List<Integer> src = new ArrayList<>(l(1, 2));
        List<Integer> copy = Linq.toList(src);
        assertNotSame(src, copy);
        assertEquals(src, copy);
        assertArrayEquals(new String[] {"a", "b"}, Linq.toArray(l("a", "b"), String[]::new));
    }

    @Test
    void toDictionary() {
        Map<Integer, String> m = Linq.toDictionary(l("bb", "a", "ccc"), String::length);
        assertEquals(l(2, 1, 3), new ArrayList<>(m.keySet()));
        assertEquals("a", m.get(1));
        Map<String, Integer> m2 = Linq.toDictionary(l("bb", "a"), s -> s, String::length);
        assertEquals(2, m2.get("bb"));
        assertThrows(IllegalArgumentException.class, () -> Linq.toDictionary(l("a", "b"), String::length));
    }

    @Test
    void maxMin() {
        assertEquals(3, Linq.max(l(1, 3, 2)));
        assertEquals(1, Linq.min(l(2, 1, 3)));
        assertEquals(5, Linq.max(l("a", "bbbbb"), String::length));
        assertEquals(1, Linq.min(l("a", "bbbbb"), String::length));
        assertThrows(IllegalStateException.class, () -> Linq.max(new ArrayList<Integer>()));
        assertThrows(IllegalStateException.class, () -> Linq.min(new ArrayList<Integer>()));
        assertThrows(IllegalStateException.class, () -> Linq.max(new ArrayList<String>(), String::length));
        assertThrows(IllegalStateException.class, () -> Linq.min(new ArrayList<String>(), String::length));
    }

    @Test
    void orderByStable() {
        List<P> in = l(new P("a", 2), new P("b", 1), new P("c", 2), new P("d", 1));
        assertEquals("bdac", names(Linq.orderBy(in, p -> p.key)));
        assertEquals("bdac", names(Linq.orderBy(in, p -> p.key, Comparator.<Integer>naturalOrder())));
        assertEquals("acbd", names(Linq.orderByDescending(in, p -> p.key)));
        assertEquals("acbd", names(Linq.orderByDescending(in, p -> p.key, Comparator.<Integer>naturalOrder())));
        assertEquals("abcd", names(in), "input untouched");
    }

    private static String names(List<P> ps) {
        StringBuilder sb = new StringBuilder();
        for (P p : ps) {
            sb.append(p.name);
        }
        return sb.toString();
    }

    @Test
    void sequenceEqualZip() {
        assertTrue(Linq.sequenceEqual(l(1, null), l(1, null)));
        assertFalse(Linq.sequenceEqual(l(1), l(1, 2)));
        assertFalse(Linq.sequenceEqual(l(1, 2), l(1)));
        assertFalse(Linq.sequenceEqual(l(1), l(2)));
        assertEquals(l("a1", "b2"), Linq.zip(l("a", "b", "c"), l(1, 2), (s, i) -> s + i));
    }

    @Test
    void selectManyTakeSkip() {
        assertEquals(l(1, 1, 2, 2, 2), Linq.selectMany(l(1, 2), x -> Linq.range(0, x == 1 ? 2 : 3).stream().map(i -> x).toList()));
        assertEquals(l(1, 2), Linq.take(l(1, 2, 3), 2));
        assertEquals(l(), Linq.take(l(1), 0));
        assertEquals(l(1), Linq.take(l(1), 5));
        assertEquals(l(3), Linq.skip(l(1, 2, 3), 2));
        assertEquals(l(), Linq.skip(l(1), 5));
        assertEquals(l(1, 2, 3), Linq.skip(l(1, 2, 3), -1));
    }

    @Test
    void countRangeReverseContainsSumAggregate() {
        assertEquals(3, Linq.count(l(1, 2, 3)));
        assertEquals(2, Linq.count(l(1, 2, 3), x -> x > 1));
        assertEquals(l(5, 6, 7), Linq.range(5, 3));
        assertEquals(l(), Linq.range(5, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> Linq.range(0, -1));
        assertEquals(l(3, 2, 1), Linq.reverse(l(1, 2, 3)));
        assertTrue(Linq.contains(l(1, null), null));
        assertFalse(Linq.contains(l(1), 2));
        assertEquals(6, Linq.sum(l(1, 2, 3)));
        assertEquals(0, Linq.sum(l()));
        assertEquals("abc", Linq.aggregate(l("a", "b", "c"), "", (a, s) -> a + s));
        assertEquals(6, Linq.aggregate(l(1, 2, 3), (a, b) -> a + b));
        assertThrows(IllegalStateException.class, () -> Linq.aggregate(new ArrayList<Integer>(), (a, b) -> a + b));
    }
}
