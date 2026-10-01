// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for IntList.

package org.graylog.kusto.language.utils.dotnet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IntListTest {
    private static IntList of(int... v) {
        IntList l = new IntList();
        for (int x : v) {
            l.add(x);
        }
        return l;
    }

    @Test
    void growth() {
        IntList l = new IntList();
        for (int i = 0; i < 1000; i++) {
            l.add(i * 2);
        }
        assertEquals(1000, l.size());
        assertEquals(1998, l.get(999));
        assertEquals(1998, l.lastValue());
        assertFalse(l.isEmpty());
        assertEquals(500, l.indexOf(1000));
        assertEquals(-1, l.indexOf(1));
        l.clear();
        assertTrue(l.isEmpty());
    }

    @Test
    void removeRangeUsesStartAndCount() {
        IntList l = of(0, 1, 2, 3, 4, 5);
        l.removeRange(1, 2);
        assertArrayEquals(new int[] {0, 3, 4, 5}, l.toArray());
        l.removeRange(2, 2);
        assertArrayEquals(new int[] {0, 3}, l.toArray());
        l.removeRange(2, 0);
        l.removeRange(0, 2);
        assertTrue(l.isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> of(1, 2).removeRange(1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> of(1, 2).removeRange(-1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> of(1, 2).removeRange(0, -1));
    }

    @Test
    void binarySearchEncoding() {
        IntList l = of(10, 20, 30, 40);
        for (int i = 0; i < 4; i++) {
            assertEquals(i, l.binarySearch((i + 1) * 10));
        }
        assertEquals(~0, l.binarySearch(5));
        assertEquals(~1, l.binarySearch(15));
        assertEquals(~2, l.binarySearch(25));
        assertEquals(~3, l.binarySearch(35));
        assertEquals(~4, l.binarySearch(45));
        assertEquals(~0, new IntList().binarySearch(1));
    }

    @Test
    void insertAndAddRange() {
        IntList l = of(1, 3);
        l.insert(1, 2);
        l.insert(3, 4);
        l.insert(0, 0);
        assertArrayEquals(new int[] {0, 1, 2, 3, 4}, l.toArray());
        l.addRange(of(5, 6));
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5, 6}, l.toArray());
        assertThrows(IndexOutOfBoundsException.class, () -> l.insert(8, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> l.insert(-1, 1));
    }

    @Test
    void bounds() {
        IntList l = of(1);
        assertThrows(IndexOutOfBoundsException.class, () -> l.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> l.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> l.set(1, 0));
        l.set(0, 9);
        assertEquals(9, l.get(0));
        assertEquals(9, l.removeLast());
        assertThrows(IndexOutOfBoundsException.class, l::removeLast);
        assertThrows(IndexOutOfBoundsException.class, l::lastValue);
    }
}
