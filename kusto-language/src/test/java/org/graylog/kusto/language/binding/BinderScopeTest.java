// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: W6 spot checks that the ported Binder resolves tables, columns and scope end to end.

package org.graylog.kusto.language.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.graylog.kusto.language.DiagnosticFacts;
import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.KustoCode;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.DatabaseSymbol;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.junit.jupiter.api.Test;

class BinderScopeTest {
    private static GlobalState globals() {
        var t = new TableSymbol("T", List.of(
            new ColumnSymbol("a", ScalarTypes.Long),
            new ColumnSymbol("b", ScalarTypes.String)));
        return GlobalState.default_().withDatabase(new DatabaseSymbol("db", t));
    }

    private static boolean hasSymbol(List<Symbol> symbols, String name, Class<? extends Symbol> type) {
        return symbols.stream().anyMatch(s -> name.equals(s.name()) && type.isInstance(s));
    }

    @Test
    void projectResultTypeIsTableWithOneColumn() {
        var code = KustoCode.parseAndAnalyze("T | project a", globals());
        assertTrue(code.resultType() instanceof TableSymbol);
        var cols = ((TableSymbol) code.resultType()).columns();
        assertEquals(1, cols.size());
        assertEquals("a", cols.get(0).name());
        assertEquals(ScalarTypes.Long, cols.get(0).type());
        assertEquals(List.of(), code.getDiagnostics());
    }

    @Test
    void symbolsInScopeInsideProjectContainColumns() {
        var text = "T | project a";
        var code = KustoCode.parseAndAnalyze(text, globals());
        var inProject = code.getSymbolsInScope(text.indexOf("project a") + "project ".length());
        assertTrue(hasSymbol(inProject, "a", ColumnSymbol.class), "column a in scope");
        assertTrue(hasSymbol(inProject, "b", ColumnSymbol.class), "column b in scope");

        var atStart = code.getSymbolsInScope(0);
        assertTrue(hasSymbol(atStart, "T", TableSymbol.class), "table T at statement start");
    }

    @Test
    void unknownColumnIsReported() {
        var code = KustoCode.parseAndAnalyze("T | where x > 1", globals());
        var diagnostics = code.getDiagnostics();
        assertEquals(1, diagnostics.size());
        var expected = DiagnosticFacts.getNameDoesNotReferToAnyKnownItem("x");
        assertEquals(expected.code(), diagnostics.get(0).code());
        assertEquals(expected.message(), diagnostics.get(0).message());
        assertEquals("T | where ".length(), diagnostics.get(0).start());
    }
}
