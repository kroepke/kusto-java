// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for TupleSymbol (Symbols/TupleSymbol.cs).
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.junit.jupiter.api.Test;

class TupleSymbolTest {
    @Test
    void empty() {
        // TupleSymbol.cs:62: new TupleSymbol(null) binds the params constructor; ToReadOnly(null) is the empty list
        assertTrue(TupleSymbol.Empty.columns().isEmpty());
        assertSame(EmptyReadOnlyList.Instance, TupleSymbol.Empty.columns());
        assertFalse(TupleSymbol.Empty.isReducibleToScalar());
        assertNull(TupleSymbol.Empty.relatedTable());
    }

    @Test
    void basics() {
        var a = new ColumnSymbol("a", ScalarTypes.Long);
        var b = new ColumnSymbol("b", ScalarTypes.String);
        var t = new TupleSymbol(a, b);
        assertEquals("tuple", t.name());
        assertEquals(SymbolKind.Tuple, t.kind());
        assertEquals(Tabularity.Scalar, t.tabularity());
        assertEquals(List.of(a, b), t.columns());
        assertEquals(List.<Symbol>of(a, b), t.members());
        assertFalse(t.isReducibleToScalar());
        assertTrue(new TupleSymbol(a).isReducibleToScalar());
        assertTrue(new TupleSymbol(List.of(a)).isReducibleToScalar());
        assertTrue(t.aliases().isEmpty());
        assertSame(b, t.getFirstMember("b"));
        assertNull(t.getFirstMember("c"));
    }

    @Test
    void nullElementsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TupleSymbol(Arrays.asList(new ColumnSymbol("a", ScalarTypes.Long), null)));
    }

    @Test
    void withColumnsAndSource() {
        var a = new ColumnSymbol("a", ScalarTypes.Long);
        var t = new TupleSymbol(a);
        var t2 = t.withColumns(List.of(a));
        assertNotSame(t, t2); // always a new instance (TupleSymbol.cs:218-221)
        assertEquals(List.copyOf(t.columns()), t2.columns()); // ListView equality is identity (D26)
        var t3 = t.withSource(null);
        assertNotSame(t, t3);
        assertSame(a, t3.columns().get(0)); // ColumnSymbol.WithSource(null) keeps the column when the source is unchanged
    }

    @Test
    void referenceEquality() {
        var a = new ColumnSymbol("a", ScalarTypes.Long);
        var t1 = new TupleSymbol(a);
        var t2 = new TupleSymbol(a);
        assertFalse(t1.equals(t2)); // Symbol equality is reference identity
    }
}
