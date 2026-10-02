// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for the TableState [Flags] holder and TableStateExtensions (Symbols/TableState.cs).
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TableStateExtensionsTest {
    @Test
    void bits() {
        assertEquals(0, TableState.None);
        assertEquals(1, TableState.Serialized);
        assertEquals(2, TableState.Sorted);
        assertEquals(4, TableState.Open);
    }

    @Test
    void extensions() {
        int s = TableState.Serialized | TableState.Sorted;
        assertTrue(TableStateExtensions.has(s, TableState.Sorted));
        assertTrue(TableStateExtensions.has(s, TableState.Sorted | TableState.Open)); // any bit
        assertFalse(TableStateExtensions.has(s, TableState.Open));
        assertFalse(TableStateExtensions.has(s, TableState.None));
        assertEquals(s | TableState.Open, TableStateExtensions.with(s, TableState.Open));
        assertEquals(TableState.Serialized, TableStateExtensions.without(s, TableState.Sorted));
        assertEquals(TableState.Serialized, TableStateExtensions.new_(TableState.None));
        assertEquals(TableState.Serialized | TableState.Open, TableStateExtensions.new_(TableState.Open));
        assertEquals(TableState.Open, TableStateExtensions.open(TableState.None));
        assertEquals(TableState.Serialized, TableStateExtensions.unsorted(s));
    }
}
