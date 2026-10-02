// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for Symbol base members (Symbols/Symbol.cs), GroupSymbol, ErrorSymbol, VoidSymbol and EmptyScope.
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SymbolTest {
    @Test
    void baseMembers() {
        var c = new ColumnSymbol("__hidden", ScalarTypes.Long);
        assertTrue(c.isHidden()); // Symbol.cs:41 ordinal StartsWith("__")
        assertFalse(new ColumnSymbol("_x", ScalarTypes.Long).isHidden());
        assertEquals("", c.alternateName());
        assertFalse(c.isError());
        assertTrue(ErrorSymbol.Instance.isError());
        assertTrue(ScalarTypes.Long.members().isEmpty());
    }

    @Test
    void scalarAndTabularFromTabularity() {
        // Symbol.cs:56-87
        assertTrue(ScalarTypes.Long.isScalar());
        assertFalse(ScalarTypes.Long.isTabular());
        assertFalse(ErrorSymbol.Instance.isScalar());
        assertFalse(ErrorSymbol.Instance.isTabular());
        assertFalse(VoidSymbol.Instance.isScalar());
        assertEquals(Tabularity.None, VoidSymbol.Instance.tabularity());
        assertEquals("error", ErrorSymbol.Instance.name());
        assertEquals("void", VoidSymbol.Instance.name());
        assertEquals(SymbolKind.Error, ErrorSymbol.Instance.kind());
        assertEquals(SymbolKind.Void, VoidSymbol.Instance.kind());
    }

    @Test
    void groupSymbol() {
        // GroupSymbol.cs
        var g = new GroupSymbol(ScalarTypes.Long, ScalarTypes.String);
        assertEquals("group", g.name());
        assertEquals(SymbolKind.Group, g.kind());
        assertEquals(List.of(ScalarTypes.Long, ScalarTypes.String), g.members());
        assertEquals(Tabularity.Scalar, g.tabularity()); // Members[0].Tabularity
        assertEquals(Tabularity.None, new GroupSymbol(VoidSymbol.Instance, ScalarTypes.Long).tabularity());
        assertThrows(IndexOutOfBoundsException.class, () -> new GroupSymbol().tabularity());
        assertThrows(IllegalArgumentException.class, () -> new GroupSymbol(ScalarTypes.Long, null));
    }

    @Test
    void getResultType() {
        // Symbol.cs:136-183 (VariableSymbol/EntityGroupElementSymbol/ParameterSymbol cases belong to the other W1 partition)
        assertNull(Symbol.getResultType(null));
        assertSame(ScalarTypes.Long, Symbol.getResultType(ScalarTypes.Long));
        assertSame(ScalarTypes.Real, Symbol.getResultType(new ColumnSymbol("a", ScalarTypes.Real)));
        assertSame(ScalarTypes.Real, Symbol.getResultType(new GroupSymbol(new ColumnSymbol("a", ScalarTypes.Real))));
        var g = Symbol.getResultType(new GroupSymbol(new ColumnSymbol("a", ScalarTypes.Real), ScalarTypes.String));
        var group = assertInstanceOf(GroupSymbol.class, g);
        assertEquals(List.of(ScalarTypes.Real, ScalarTypes.String), group.members());
        assertNull(Symbol.getResultType(new GroupSymbol()));
    }

    @Test
    void getMembersAndFirstMember() {
        // Symbol.cs:98-131
        var a = new ColumnSymbol("a", ScalarTypes.Long);
        var b = new ColumnSymbol("b", ScalarTypes.Long);
        var a2 = new ColumnSymbol("A", ScalarTypes.Long);
        var t = new TupleSymbol(a, b, a2);
        var list = new ArrayList<Symbol>();
        t.getMembers("a", SymbolMatch.Column, list);
        assertEquals(List.of(a), list);
        list.clear();
        t.getMembers("a", SymbolMatch.Column, list, true);
        assertEquals(List.of(a, a2), list);
        list.clear();
        t.getMembers(SymbolMatch.Column, list);
        assertEquals(List.of(a, b, a2), list);
        list.clear();
        t.getMembers(SymbolMatch.Table, list);
        assertTrue(list.isEmpty());
        assertSame(a2, t.getFirstMember("A"));
        assertSame(a, t.getFirstMember("A", SymbolMatch.Any, true));
        assertNull(t.getFirstMember("a", SymbolMatch.Table));
        assertSame(a, t.getFirstMember(null));
    }

    @Test
    void emptyScope() {
        var list = new ArrayList<Symbol>();
        EmptyScope.Instance.getSymbols("a", SymbolMatch.Any, list);
        EmptyScope.Instance.getSymbols(SymbolMatch.Any, list);
        assertTrue(list.isEmpty());
    }

    @Test
    void enumAliasesAndOrder() {
        assertSame(ResultNameKind.None, ResultNameKind.Default);
        assertEquals(0, SymbolKind.None.ordinal());
        assertEquals("HasAll", OperatorKind.HasAll.toString()); // OperatorSymbol uses kind.ToString()
        assertTrue(Conversion.Promotable.compareTo(Conversion.Dynamic) < 0);
        assertEquals(List.of(Conversion.None, Conversion.Promotable, Conversion.Dynamic, Conversion.Compatible, Conversion.Any),
            List.of(Conversion.values()));
    }
}
