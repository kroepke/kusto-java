// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for SafeList copy-on-write ownership transfer (Utils/SafeList.cs).
package org.graylog.kusto.language.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SafeListTest {
    private static <T> List<T> toList(SafeList<T> list) {
        List<T> result = new ArrayList<>();
        for (T item : list) {
            result.add(item);
        }
        return result;
    }

    @Test
    void newListOwnsItsBackingList() {
        var a = new SafeList<>(List.of(1, 2, 3));
        assertTrue(a.isOwner());
        assertEquals(3, a.size());
        assertEquals(List.of(1, 2, 3), toList(a));
    }

    @Test
    void addItemOnOwnerTransfersOwnership() {
        var a = new SafeList<>(List.of(1, 2, 3));
        var b = a.addItem(4);
        assertFalse(a.isOwner());
        assertTrue(b.isOwner());
        // a still sees only its first three elements although the backing list grew
        assertEquals(3, a.size());
        assertEquals(List.of(1, 2, 3), toList(a));
        assertThrows(IndexOutOfBoundsException.class, () -> a.get(3));
        assertEquals(List.of(1, 2, 3, 4), toList(b));
    }

    @Test
    void addItemOnNonOwnerCopies() {
        var a = new SafeList<>(List.of(1, 2, 3));
        var b = a.addItem(4);
        var c = a.addItem(9); // a is no longer the owner: copies its visible elements
        assertTrue(c.isOwner());
        assertTrue(b.isOwner()); // c did not take b's list
        assertEquals(List.of(1, 2, 3, 9), toList(c));
        assertEquals(List.of(1, 2, 3, 4), toList(b));
        assertEquals(List.of(1, 2, 3), toList(a));
    }

    @Test
    void addItemsFollowsTheSameOwnershipRules() {
        var a = new SafeList<>(List.of("x"));
        var b = a.addItems(List.of("y", "z"));
        var c = a.addItems(List.of("q"));
        assertFalse(a.isOwner());
        assertEquals(List.of("x", "y", "z"), toList(b));
        assertEquals(List.of("x", "q"), toList(c));
        assertEquals(List.of("x"), toList(a));
    }

    @Test
    void emptyIsSharedAndNeverOwner() {
        SafeList<Integer> empty = SafeList.empty();
        assertEquals(0, empty.size());
        assertFalse(empty.isOwner());
        var one = empty.addItem(1);
        assertEquals(List.of(1), toList(one));
        assertEquals(0, SafeList.<Integer>empty().size());
        assertSame(empty, SafeList.empty());
    }

    @Test
    void nullItemsGiveAnEmptyList() {
        var a = new SafeList<Integer>((Iterable<Integer>) null);
        assertEquals(0, a.size());
        assertTrue(a.isEmpty());
    }

    @Test
    void indexOutOfRangeThrows() {
        var a = new SafeList<>(List.of(1));
        assertThrows(IndexOutOfBoundsException.class, () -> a.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> a.get(1));
    }

    @Test
    void enumeratorCurrentIsNullOutOfRange() {
        var a = new SafeList<>(List.of(1, 2));
        var e = a.getEnumerator();
        assertNull(e.current());
        assertTrue(e.moveNext());
        assertEquals(1, e.current());
        assertTrue(e.moveNext());
        assertFalse(e.moveNext());
        assertNull(e.current());
    }

    @Test
    void identityEqualsOnTheListAndItsView() {
        var a = new SafeList<>(List.of(1, 2));
        var b = new SafeList<>(List.of(1, 2));
        assertNotEquals(a, b);
        var v1 = ListExtensions.toReadOnly(List.of(1, 2));
        var v2 = ListExtensions.toReadOnly(List.of(1, 2));
        assertEquals(v1, v1);
        assertNotEquals(v1, v2);
        assertEquals(List.of(1, 2), new ArrayList<>(v1));
        // a view is already immutable and is returned as is
        assertSame(v1, ListExtensions.toReadOnly(v1));
        assertThrows(UnsupportedOperationException.class, () -> v1.add(3));
    }

    @Test
    void toReadOnlyReturnsTheSharedEmptyInstance() {
        assertSame(EmptyReadOnlyList.Instance, ListExtensions.toReadOnly(null));
        assertSame(EmptyReadOnlyList.Instance, ListExtensions.toReadOnly(new ArrayList<Integer>()));
        assertSame(EmptyReadOnlyList.Instance, ListExtensions.toReadOnly(SafeList.empty()));
        assertSame(SafeList.empty(), ListExtensions.toSafeList(null));
    }
}
