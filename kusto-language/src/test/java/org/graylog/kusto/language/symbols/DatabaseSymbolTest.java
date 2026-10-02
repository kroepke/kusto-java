// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for DatabaseSymbol (Symbols/DatabaseSymbol.cs).
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

import org.junit.jupiter.api.Test;

class DatabaseSymbolTest {
    private final TableSymbol table = new TableSymbol("T", new ColumnSymbol("a", ScalarTypes.Int));
    private final ExternalTableSymbol external = new ExternalTableSymbol("Ext", new ColumnSymbol("b", ScalarTypes.String));
    private final MaterializedViewSymbol view = new MaterializedViewSymbol("MV", List.of(new ColumnSymbol("c", ScalarTypes.Long)), "T | summarize count()");
    private final FunctionSymbol function = new FunctionSymbol("F", ScalarTypes.Int);
    private final EntityGroupSymbol group = new EntityGroupSymbol("EG", "[cluster('a').database('b')]");

    private DatabaseSymbol newDatabase() {
        return new DatabaseSymbol("db", table, external, view, function, group);
    }

    @Test
    void membersAreGroupedByKind() {
        var db = newDatabase();
        assertEquals(SymbolKind.Database, db.kind());
        assertEquals(Tabularity.Other, db.tabularity());
        assertEquals(5, db.members().size());
        assertEquals(List.of(table), db.tables()); // external tables and materialized views are not tables
        assertEquals(List.of(external), db.externalTables());
        assertEquals(List.of(view), db.materializedViews());
        assertEquals(List.of(function), db.functions());
        assertEquals(List.of(group), db.entityGroups());
        assertTrue(db.storedQueryResults().isEmpty());
        assertTrue(db.graphModels().isEmpty());
    }

    @Test
    void membersKeepInsertionOrder() {
        var db = new DatabaseSymbol("db", function, table, group);
        assertSame(function, db.members().get(0));
        assertSame(table, db.members().get(1));
        assertSame(group, db.members().get(2));
    }

    @Test
    void lookupsAreOrdinalAndCaseSensitive() {
        var db = newDatabase();
        assertSame(table, db.getTable("T"));
        assertNull(db.getTable("t"));
        assertNull(db.getTable("Ext"));
        assertSame(function, db.getFunction("F"));
        assertNull(db.getFunction("f"));
        assertSame(external, db.getExternalTable("Ext"));
        assertNull(db.getExternalTable("ext"));
        assertSame(view, db.getMaterializedView("MV"));
        assertSame(group, db.getEntityGroup("EG"));
        assertNull(db.getEntityGroup("eg"));
        assertSame(table, db.getMember("T"));
        assertNull(db.getMember("missing"));
        assertNull(db.getMember(null));
    }

    @Test
    void getAnyTableFindsTablesExternalTablesAndViews() {
        var db = newDatabase();
        assertSame(table, db.getAnyTable("T"));
        assertSame(external, db.getAnyTable("Ext"));
        assertSame(view, db.getAnyTable("MV"));
        assertNull(db.getAnyTable("F"));
    }

    @Test
    void firstMemberWithAMatchingNameWins() {
        var first = new TableSymbol("Dup", new ColumnSymbol("a", ScalarTypes.Int));
        var second = new TableSymbol("Dup", new ColumnSymbol("b", ScalarTypes.Int));
        var db = new DatabaseSymbol("db", first, second);
        assertSame(first, db.getTable("Dup"));
    }

    @Test
    void alternateNameDefaultsToEmpty() {
        assertEquals("", new DatabaseSymbol("db", table).alternateName());
        assertEquals("alt", new DatabaseSymbol("db", "alt", table).alternateName());
        assertEquals("", new DatabaseSymbol("db", (String) null, List.of(table)).alternateName());
    }

    @Test
    void withAlternateNameReturnsSameInstanceWhenUnchanged() {
        var db = new DatabaseSymbol("db", "alt", table);
        assertSame(db, db.withAlternateName("alt"));
        var renamed = db.withAlternateName("other");
        assertNotSame(db, renamed);
        assertEquals("other", renamed.alternateName());
        assertEquals("alt", db.alternateName());
        assertEquals(db.members(), renamed.members());
    }

    @Test
    void withMembersDoesNotModifyTheOriginal() {
        var db = new DatabaseSymbol("db", table);
        var other = new TableSymbol("U", new ColumnSymbol("x", ScalarTypes.Int));
        var replaced = db.withMembers(List.of(other));
        assertNotSame(db, replaced);
        assertEquals(List.of(table), db.members());
        assertEquals(List.of(other), replaced.members());
        assertEquals("db", replaced.name());
    }

    @Test
    void addMembersAppendsAndDoesNotModifyTheOriginal() {
        var db = new DatabaseSymbol("db", table);
        var other = new TableSymbol("U", new ColumnSymbol("x", ScalarTypes.Int));
        var added = db.addMembers(other);
        assertEquals(List.of(table), db.members());
        assertEquals(List.of(table, other), added.members());
        var addedList = added.addMembers(List.of(function));
        assertEquals(3, addedList.members().size());
        assertEquals(2, added.members().size());
        assertSame(function, addedList.getFunction("F"));
    }

    @Test
    void openFlagIsCarriedThroughCopies() {
        var db = new DatabaseSymbol("db", List.of(table), true);
        assertTrue(db.isOpen());
        assertTrue(db.withMembers(List.of()).isOpen());
        assertTrue(db.addMembers(function).isOpen());
        assertTrue(db.withAlternateName("a").isOpen());
        assertFalse(new DatabaseSymbol("db", table).isOpen());
    }

    @Test
    void membersCannotBeModifiedOrContainNulls() {
        var db = newDatabase();
        assertThrows(UnsupportedOperationException.class, () -> db.members().add(table));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseSymbol("db", Arrays.asList(table, null)));
    }

    @Test
    void sourceCollectionChangesDoNotLeakIntoTheSymbol() {
        var source = new java.util.ArrayList<Symbol>(List.of(table));
        var db = new DatabaseSymbol("db", source);
        source.add(function);
        assertEquals(1, db.members().size());
    }

    @Test
    void unknownIsAnOpenEmptyDatabase() {
        assertEquals("", DatabaseSymbol.Unknown.name());
        assertTrue(DatabaseSymbol.Unknown.isOpen());
        assertTrue(DatabaseSymbol.Unknown.members().isEmpty());
    }

    // PORT-BUG mirror: upstream fills the lookup set with the argument, not the member (DatabaseSymbol.cs:307).
    @Test
    void containsMirrorsUpstreamLookupSetBug() {
        var db = newDatabase();
        var stranger = new TableSymbol("Stranger", new ColumnSymbol("z", ScalarTypes.Int));
        assertTrue(db.contains(stranger)); // the first argument fills the set once per member
        assertFalse(db.contains(table)); // a real member is not found afterwards

        var db2 = newDatabase();
        assertTrue(db2.contains(table));
        assertFalse(db2.contains(function));

        assertFalse(new DatabaseSymbol("empty", List.<Symbol>of()).contains(table));
    }
}
