// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for ClusterSymbol (Symbols/ClusterSymbol.cs).
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

class ClusterSymbolTest {
    private final DatabaseSymbol db1 = new DatabaseSymbol("db1", "alt1");
    private final DatabaseSymbol db2 = new DatabaseSymbol("db2");

    @Test
    void kindAndTabularity() {
        var cluster = new ClusterSymbol("c", db1, db2);
        assertEquals(SymbolKind.Cluster, cluster.kind());
        assertEquals(Tabularity.Tabular, cluster.tabularity());
        assertEquals("c", cluster.name());
        assertFalse(cluster.isOpen());
    }

    @Test
    void databasesKeepOrder() {
        var cluster = new ClusterSymbol("c", db2, db1);
        assertEquals(List.of(db2, db1), cluster.databases());
        assertEquals(List.of(db2, db1), cluster.members());
    }

    @Test
    void getDatabaseByNameAndAlternateName() {
        var cluster = new ClusterSymbol("c", db1, db2);
        assertSame(db1, cluster.getDatabase("db1"));
        assertSame(db1, cluster.getDatabase("alt1"));
        assertSame(db2, cluster.getDatabase("db2"));
        assertNull(cluster.getDatabase("DB1")); // case sensitive
        assertNull(cluster.getDatabase("missing"));
        assertNull(cluster.getDatabase(null));
        assertNull(cluster.getDatabase(""));
    }

    @Test
    void lastDatabaseWinsForDuplicateNames() {
        var a = new DatabaseSymbol("same");
        var b = new DatabaseSymbol("same");
        assertSame(b, new ClusterSymbol("c", a, b).getDatabase("same"));
    }

    @Test
    void addOrUpdateDatabaseAddsNewDatabase() {
        var cluster = new ClusterSymbol("c", db1);
        var updated = cluster.addOrUpdateDatabase(db2);
        assertNotSame(cluster, updated);
        assertEquals(List.of(db1), cluster.databases());
        assertEquals(List.of(db1, db2), updated.databases());
    }

    @Test
    void addOrUpdateDatabaseReplacesDatabaseWithSameNameInPlace() {
        var cluster = new ClusterSymbol("c", db1, db2);
        var replacement = new DatabaseSymbol("db1", new TableSymbol("T", new ColumnSymbol("a", ScalarTypes.Int)));
        var updated = cluster.addOrUpdateDatabase(replacement);
        assertEquals(List.of(replacement, db2), updated.databases());
        assertEquals(List.of(db1, db2), cluster.databases());
        assertSame(replacement, updated.getDatabase("db1"));
        assertSame(db1, cluster.getDatabase("db1"));
    }

    @Test
    void updateDatabaseReplacesByReference() {
        var cluster = new ClusterSymbol("c", db1, db2);
        var other = new DatabaseSymbol("other");
        assertEquals(List.of(other, db2), cluster.updateDatabase(db1, other).databases());
        assertEquals(List.of(db1, db2), cluster.updateDatabase(new DatabaseSymbol("db1"), other).databases());
    }

    @Test
    void addDatabaseAndWithDatabases() {
        var cluster = new ClusterSymbol("c", db1);
        assertEquals(List.of(db1, db2), cluster.addDatabase(db2).databases());
        assertEquals(List.of(db1), cluster.databases());
        assertEquals(List.of(db2), cluster.withDatabases(List.of(db2)).databases());
    }

    @Test
    void addDatabaseTwiceFromTheSameClusterDoesNotShareState() {
        var cluster = new ClusterSymbol("c", db1);
        var withB = cluster.addDatabase(db2);
        var withC = cluster.addDatabase(new DatabaseSymbol("db3"));
        assertEquals(List.of(db1, db2), withB.databases());
        assertEquals("db3", withC.databases().get(1).name());
        assertEquals(List.of(db1), cluster.databases());
    }

    @Test
    void removeDatabaseAndRemoveDatabases() {
        var db3 = new DatabaseSymbol("db3");
        var cluster = new ClusterSymbol("c", db1, db2, db3);
        assertEquals(List.of(db1, db3), cluster.removeDatabase(db2).databases());
        assertEquals(List.of(db2), cluster.removeDatabases(List.of(db1, db3)).databases());
        assertEquals(3, cluster.databases().size());
    }

    @Test
    void addMembersKeepsNonDatabaseMembers() {
        var cluster = new ClusterSymbol("c", db1);
        var table = new TableSymbol("T", new ColumnSymbol("a", ScalarTypes.Int));
        var added = cluster.addMembers(table);
        assertEquals(List.of(db1, table), added.members());
        assertEquals(List.of(db1), added.databases());
        assertEquals(List.of(db1, table, db2), added.addMembers(List.of(db2)).members());
    }

    @Test
    void openClusterIsCarriedThroughCopies() {
        var cluster = new ClusterSymbol("c", List.of(db1), true);
        assertTrue(cluster.isOpen());
        assertTrue(cluster.addDatabase(db2).isOpen());
        assertTrue(cluster.withDatabases(List.of()).isOpen());
        assertTrue(cluster.removeDatabase(db1).isOpen());
        assertTrue(cluster.addOrUpdateDatabase(db2).isOpen());
    }

    @Test
    void unknownIsAnOpenEmptyCluster() {
        assertEquals("", ClusterSymbol.Unknown.name());
        assertTrue(ClusterSymbol.Unknown.isOpen());
        assertTrue(ClusterSymbol.Unknown.members().isEmpty());
        assertTrue(ClusterSymbol.Unknown.databases().isEmpty());
        assertNull(ClusterSymbol.Unknown.getDatabase("x"));
    }

    @Test
    void publicConstructorAcceptsNullDatabases() {
        var cluster = new ClusterSymbol("c", (List<DatabaseSymbol>) null, true);
        assertTrue(cluster.isOpen());
        assertTrue(cluster.members().isEmpty());
    }

    @Test
    void membersAreImmutableAndNullFree() {
        var cluster = new ClusterSymbol("c", db1);
        assertThrows(UnsupportedOperationException.class, () -> cluster.members().add(db2));
        assertThrows(IllegalArgumentException.class, () -> new ClusterSymbol("c", Arrays.asList(db1, null)));
    }
}
