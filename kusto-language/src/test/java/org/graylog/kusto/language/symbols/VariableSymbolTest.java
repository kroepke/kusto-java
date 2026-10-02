// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for VariableSymbol and ParameterSymbol (Symbols/VariableSymbol.cs, ParameterSymbol.cs).
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.graylog.kusto.language.syntax.ValueInfo;
import org.junit.jupiter.api.Test;

class VariableSymbolTest {
    @Test
    void defaults() {
        var v = new VariableSymbol("x", ScalarTypes.Int);
        assertEquals("x", v.name());
        assertSame(ScalarTypes.Int, v.type());
        assertFalse(v.isConstant());
        assertNull(v.constantValueInfo());
        assertNull(v.constantValue());
        assertNull(v.source());
        assertEquals(SymbolKind.Variable, v.kind());
    }

    @Test
    void constantValue() {
        var info = new ValueInfo("42", "42", Long.valueOf(42));
        var v = new VariableSymbol("x", ScalarTypes.Long, true, info);
        assertTrue(v.isConstant());
        assertSame(info, v.constantValueInfo());
        assertEquals(Long.valueOf(42), v.constantValue());
        assertTrue(new VariableSymbol("y", ScalarTypes.Long, true).isConstant());
    }

    @Test
    void tabularityComesFromTheType() {
        assertEquals(ScalarTypes.Int.tabularity(), new VariableSymbol("x", ScalarTypes.Int).tabularity());
        var table = new TableSymbol("T", new ColumnSymbol("a", ScalarTypes.Int));
        assertEquals(table.tabularity(), new VariableSymbol("t", table).tabularity());
    }

    @Test
    void nullTypeIsRejected() {
        assertThrows(NullPointerException.class, () -> new VariableSymbol("x", null));
        assertThrows(NullPointerException.class, () -> new ParameterSymbol("p", null));
    }

    @Test
    void parameterSymbol() {
        var p = new ParameterSymbol("p", ScalarTypes.String);
        assertEquals("p", p.name());
        assertSame(ScalarTypes.String, p.type());
        assertEquals("", p.description());
        assertEquals(SymbolKind.Parameter, p.kind());
        assertEquals(ScalarTypes.String.tabularity(), p.tabularity());
        assertEquals("desc", new ParameterSymbol("p", ScalarTypes.String, "desc").description());
    }
}
