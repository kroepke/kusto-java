// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for TableSymbol and its subclasses (Symbols/TableSymbol.cs).
package org.graylog.kusto.language.symbols;

import static org.graylog.kusto.language.symbols.W1bTestSupport.assertPending;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.junit.jupiter.api.Test;

class TableSymbolTest {
    private static final ColumnSymbol A = new ColumnSymbol("a", ScalarTypes.Long);
    private static final ColumnSymbol B = new ColumnSymbol("b", ScalarTypes.String);

    @Test
    void empty() {
        // TableSymbol.cs:426 new TableSymbol() through the params ColumnSymbol[] constructor
        var e = TableSymbol.Empty;
        assertEquals("", e.name());
        assertSame(EmptyReadOnlyList.Instance, e.columns());
        assertEquals("", e.description());
        assertEquals(SymbolKind.Table, e.kind());
        assertEquals(Tabularity.Tabular, e.tabularity());
        assertFalse(e.isOpen());
        assertFalse(e.isSerialized());
        assertFalse(e.isSorted());
        assertSame(StoredQueryResultSymbol.Empty, TableSymbol.Empty); // the inherited static, as in C#
    }

    @Test
    void withIsOpen() {
        var open = TableSymbol.Empty.withIsOpen(true);
        assertNotSame(TableSymbol.Empty, open);
        assertTrue(open.isOpen());
        assertFalse(TableSymbol.Empty.isOpen());
        assertSame(TableSymbol.Empty, TableSymbol.Empty.withIsOpen(false));
        assertSame(open, open.withIsOpen(true));
        assertFalse(open.withIsOpen(false).isOpen());
        assertEquals(TableSymbol.class, open.getClass());
    }

    @Test
    void stateFlagsAndInheritance() {
        var t = new TableSymbol("t", A, B);
        var s = t.withIsSerialized(true).withIsSorted(true);
        assertTrue(s.isSerialized());
        assertTrue(s.isSorted());
        assertFalse(s.isOpen());
        var inherited = new TableSymbol("u", A).withIsOpen(true).withInheritableProperties(s);
        assertTrue(inherited.isSerialized());
        assertTrue(inherited.isSorted());
        assertFalse(inherited.isOpen());
        assertThrows(NullPointerException.class, () -> t.withInheritableProperties(null));
    }

    @Test
    void withColumnsComparesByReference() {
        // TableSymbol.cs:164 `useColumns != this.Columns` is a reference comparison
        var t = new TableSymbol("t", A, B);
        assertSame(t, t.withColumns(t.columns()));
        var copy = t.withColumns(new ArrayList<>(t.columns()));
        assertNotSame(t, copy);
        assertEquals(List.copyOf(t.columns()), copy.columns()); // SafeList views compare by identity (D26)
        assertSame(t, t.withName("t"));
        assertNotSame(t, t.withName("u"));
        assertEquals("u", t.withName("u").name());
        assertSame(t, t.withDescripton(null)); // "" == ""
        assertEquals("d", t.withDescripton("d").description());
        assertEquals(List.of(A, B, A), t.addColumns(A).columns());
        assertEquals(List.of(B), t.withColumns(B).columns());
        assertThrows(NullPointerException.class, () -> t.addColumns((Iterable<ColumnSymbol>) null));
    }

    @Test
    void getColumnFirstWins() {
        // TableSymbol.cs:337 ColumnMap keeps the first column of a duplicated name
        var a2 = new ColumnSymbol("a", ScalarTypes.Real);
        var t = new TableSymbol("t", A, B, a2);
        assertSame(A, t.getColumn("a"));
        assertSame(B, t.getColumn("b"));
        var ex = assertThrows(IllegalStateException.class, () -> t.getColumn("x"));
        assertEquals("The column 'x' does not exist.", ex.getMessage());

        var found = new ArrayList<ColumnSymbol>();
        t.getMatchingColumns("a", found);
        assertEquals(List.of(A), found);
        found.clear();
        t.getMatchingColumns("A", found);
        assertEquals(List.of(), found);
    }

    @Test
    void getMembersIgnoresIgnoreCaseForNamedLookup() {
        // TableSymbol.cs:396-411: the ColumnMap lookup is case-sensitive even with ignoreCase
        var t = new TableSymbol("t", A, B);
        var symbols = new ArrayList<Symbol>();
        t.getMembers("A", SymbolMatch.Any, symbols, true);
        assertEquals(List.of(), symbols);
        t.getMembers("a", SymbolMatch.Column, symbols, false);
        assertEquals(List.of(A), symbols);
        symbols.clear();
        t.getMembers("a", SymbolMatch.Table, symbols, false);
        assertEquals(List.of(), symbols);
        t.getMembers(null, SymbolMatch.Column, symbols, true);
        assertEquals(List.of(A, B), symbols);
        symbols.clear();
        TableSymbol.Empty.getMembers("a", SymbolMatch.Any, symbols, false);
        assertEquals(List.of(), symbols);
        assertEquals(List.of(A, B), t.members());
    }

    @Test
    void externalAndMaterializedConversions() {
        var t = new TableSymbol("t", A, B).withIsOpen(true);
        var ext = t.withIsExternal(true);
        assertTrue(ext instanceof ExternalTableSymbol);
        assertTrue(ext.isExternal());
        assertEquals("t", ext.name());
        assertSame(t.columns(), ext.columns());
        assertTrue(ext.isOpen());
        assertSame(ext, ext.withIsExternal(true));
        var back = ext.withIsExternal(false);
        assertEquals(TableSymbol.class, back.getClass());
        assertFalse(back.isExternal());
        assertSame(t, t.withIsExternal(false));
        // Create() keeps the subclass
        assertEquals(ExternalTableSymbol.class, ext.withName("e2").getClass());

        var mv = t.withIsMaterializedView(true);
        assertTrue(mv instanceof MaterializedViewSymbol);
        assertTrue(mv.isMaterializedView());
        assertEquals(SymbolKind.MaterializedView, mv.kind());
        assertSame(mv, mv.withIsMaterializedView(true));
        assertEquals(TableSymbol.class, mv.withIsMaterializedView(false).getClass());

        var view = new MaterializedViewSymbol("mv", List.of(A), "T | summarize count()");
        assertEquals("T | summarize count()", view.materializedViewQuery());
        assertEquals(MaterializedViewKind.Unknown, view.materializedViewKind());
        var view2 = view.withIsSorted(true);
        assertEquals(MaterializedViewSymbol.class, view2.getClass());
        assertEquals("T | summarize count()", ((MaterializedViewSymbol) view2).materializedViewQuery());
        assertTrue(view2.isSorted());

        var ext2 = new ExternalTableSymbol("x", A);
        assertEquals(SymbolKind.Table, ext2.kind());
        assertTrue(ext2.isExternal());
    }

    @Test
    void storedQueryResult() {
        var sq = new StoredQueryResultSymbol("sq");
        assertEquals(SymbolKind.StoredQueryResult, sq.kind());
        assertTrue(sq.isStoredQueryResult());
        assertTrue(sq.columns().isEmpty());
        assertEquals(List.of(A), new StoredQueryResultSymbol("sq", List.of(A)).columns());
    }

    @Test
    void equivalence() {
        var t1 = new TableSymbol("t", A, B);
        var t2 = new TableSymbol("t", List.of(A, B));
        assertTrue(TableSymbol.areEquivalent(t1, t2));
        assertFalse(TableSymbol.areEquivalent(t1, t2.withName("u")));
        assertTrue(TableSymbol.areResultEquivalent(t1, t2.withName("u")));
        assertFalse(TableSymbol.areResultEquivalent(t1, t2.withIsOpen(true)));
        assertFalse(TableSymbol.areEquivalent(t1, t2.withIsExternal(true))); // GetType() differs
        assertTrue(TableSymbol.areColumnsEquivalent(t1, new TableSymbol(new ColumnSymbol("a", ScalarTypes.Long), B)));
        assertFalse(TableSymbol.areColumnsEquivalent(t1, new TableSymbol(new ColumnSymbol("a", ScalarTypes.Int), B)));
        assertFalse(TableSymbol.areColumnsEquivalent(t1, new TableSymbol(A)));
    }

    private static void assertCols(TableSymbol t, Object... nameTypePairs) {
        assertEquals(nameTypePairs.length / 2, t.columns().size());
        for (int i = 0; i < t.columns().size(); i++) {
            assertEquals(nameTypePairs[2 * i], t.columns().get(i).name());
            assertSame(nameTypePairs[2 * i + 1], t.columns().get(i).type());
        }
    }

    @Test
    void schemaText() {
        // TableSymbol.From: QueryParser.ParseRowSchema then Binder.CreateColumnsFromRowSchema
        assertCols(TableSymbol.from("a: long, b: string"), "a", ScalarTypes.Long, "b", ScalarTypes.String);
        var t = new TableSymbol("t", "(a: long)");
        assertEquals("t", t.name());
        assertCols(t, "a", ScalarTypes.Long);
        var ext = new ExternalTableSymbol("t", "(a: long)");
        assertCols(ext, "a", ScalarTypes.Long);
        assertEquals(SymbolKind.Table, ext.kind());
        assertTrue(ext.isExternal());
        var mv = new MaterializedViewSymbol("t", "(a: long)", "T");
        assertCols(mv, "a", ScalarTypes.Long);
        assertTrue(mv.isMaterializedView());
        assertEquals("T", mv.materializedViewQuery());
        assertThrows(NullPointerException.class, () -> TableSymbol.from(null));
    }

    @Test
    void combineUnifySameName() {
        var a2 = new ColumnSymbol("a", ScalarTypes.String);
        // same-named columns of different types collapse into one column of the common type, else dynamic (Binder.UnifyColumnsWithSameName)
        var r = TableSymbol.combine(CombineKind.UnifySameName, new TableSymbol(A), new TableSymbol(B));
        assertCols(r, "a", ScalarTypes.Long, "b", ScalarTypes.String);
        var u = TableSymbol.combine(CombineKind.UnifySameName, new TableSymbol(A), new TableSymbol(a2));
        assertCols(u, "a", ScalarTypes.Dynamic);
        assertCols(TableSymbol.combine(CombineKind.UnifySameName, new TableSymbol(A), new TableSymbol(A)), "a", ScalarTypes.Long);
    }
}
