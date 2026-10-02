// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for ColumnSymbol (Symbols/ColumnSymbol.cs): constructor coercions, original columns, With* identity.
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.junit.jupiter.api.Test;

class ColumnSymbolTest {
    @Test
    void constructorCoercions() {
        // ColumnSymbol.cs:45-69
        var c = new ColumnSymbol(null, null);
        assertEquals("", c.name());
        assertSame(ScalarTypes.Unknown, c.type());
        assertEquals("", c.description());
        assertSame(EmptyReadOnlyList.Instance, c.originalColumns());
        assertSame(EmptyReadOnlyList.Instance, c.examples());
        assertNull(c.source());
        assertSame(ScalarTypes.Unknown, new ColumnSymbol("e", ErrorSymbol.Instance).type());
        assertSame(VoidSymbol.Instance, new ColumnSymbol("v", VoidSymbol.Instance).type());
        assertEquals(SymbolKind.Column, c.kind());
        assertEquals(Tabularity.Scalar, c.tabularity());
        assertTrue(c.isScalar());
        assertEquals(List.of("x", "y"), new ColumnSymbol("a", ScalarTypes.String, "d", null, null, List.of("x", "y")).examples());
    }

    @Test
    void trulyOriginalColumns() {
        // ColumnSymbol.cs:74-93
        var a = new ColumnSymbol("a", ScalarTypes.Long);
        var b = new ColumnSymbol("b", ScalarTypes.Long);
        var e = new ColumnSymbol("e", ScalarTypes.Long);
        var c = new ColumnSymbol("c", ScalarTypes.Long, null, List.of(a, b));
        assertEquals(List.of(a, b), c.originalColumns());
        var d = new ColumnSymbol("d", ScalarTypes.Long, null, List.of(c, e));
        assertEquals(List.of(a, b, e), d.originalColumns());
        assertSame(EmptyReadOnlyList.Instance, new ColumnSymbol("x", ScalarTypes.Long, null, List.of()).originalColumns());
    }

    @Test
    void withReturnsSameInstanceWhenUnchanged() {
        // ColumnSymbol.cs:101-127
        var c = new ColumnSymbol("a", ScalarTypes.Long, "desc");
        assertSame(c, c.withName("a"));
        assertSame(c, c.withType(ScalarTypes.Long));
        assertSame(c, c.withDescription("desc"));
        assertSame(c, c.withSource(null));
        assertSame(c, c.withOriginalColumns(c.originalColumns()));
        assertSame(c, c.withExamples(c.examples()));
    }

    @Test
    void withCreatesNewInstanceWhenChanged() {
        var c = new ColumnSymbol("a", ScalarTypes.Long);
        var renamed = c.withName("b");
        assertNotSame(c, renamed);
        assertEquals("b", renamed.name());
        assertSame(ScalarTypes.Long, renamed.type());
        assertSame(ScalarTypes.String, c.withType(ScalarTypes.String).type());
        // C# string != compares values, but null != "" is still true: a new column with description "" results
        var d = c.withDescription(null);
        assertNotSame(c, d);
        assertEquals("", d.description());
        // lists are compared by reference: an empty array differs from EmptyReadOnlyList.Instance
        var oc = c.withOriginalColumns();
        assertNotSame(c, oc);
        assertSame(EmptyReadOnlyList.Instance, oc.originalColumns());
        var ex = c.withExamples(null);
        assertNotSame(c, ex);
        assertSame(EmptyReadOnlyList.Instance, ex.examples());
        // a type that is not the same reference produces a new column even if equivalent
        assertNotSame(c, c.withType(new PrimitiveSymbol("long")));
    }

    @Test
    void referenceEquality() {
        var c1 = new ColumnSymbol("a", ScalarTypes.Long);
        var c2 = new ColumnSymbol("a", ScalarTypes.Long);
        assertTrue(!c1.equals(c2) && c1.equals(c1));
    }
}
