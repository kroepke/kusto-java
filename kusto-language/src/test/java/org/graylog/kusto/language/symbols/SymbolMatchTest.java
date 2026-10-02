// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for the SymbolMatch [Flags] holder and SymbolMatchExtensions.Matches (Symbols/SymbolMatch.cs).
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SymbolMatchTest {
    @Test
    void bitValues() {
        // SymbolMatch.cs:11-107: Column = 1, then each member shifts the previous one left by one
        int[] chain = { SymbolMatch.Column, SymbolMatch.Table, SymbolMatch.ExternalTable, SymbolMatch.Function,
            SymbolMatch.View, SymbolMatch.Local, SymbolMatch.Database, SymbolMatch.Cluster, SymbolMatch.EntityGroup,
            SymbolMatch.EntityGroupElement, SymbolMatch.Scalar, SymbolMatch.Tabular, SymbolMatch.NonScalar,
            SymbolMatch.MaterializedView, SymbolMatch.Option, SymbolMatch.Graph, SymbolMatch.StoredQueryResult,
            SymbolMatch.GraphModel, SymbolMatch.GraphSnapshot };
        assertEquals(0, SymbolMatch.None);
        for (int i = 0; i < chain.length; i++) {
            assertEquals(1 << i, chain[i], "bit " + i);
        }
        assertEquals(1 << 18, SymbolMatch.GraphSnapshot);
    }

    @Test
    void compositeMembers() {
        // SymbolMatch.cs:112 Any
        assertEquals(SymbolMatch.Column | SymbolMatch.Table | SymbolMatch.Function | SymbolMatch.View | SymbolMatch.Local
            | SymbolMatch.Database | SymbolMatch.Cluster | SymbolMatch.MaterializedView | SymbolMatch.EntityGroup
            | SymbolMatch.EntityGroupElement | SymbolMatch.Graph | SymbolMatch.GraphModel | SymbolMatch.GraphSnapshot, SymbolMatch.Any);
        // SymbolMatch.cs:117 Default
        assertEquals(SymbolMatch.Column | SymbolMatch.Table | SymbolMatch.Function | SymbolMatch.View | SymbolMatch.Local
            | SymbolMatch.MaterializedView | SymbolMatch.EntityGroup | SymbolMatch.EntityGroupElement | SymbolMatch.Graph
            | SymbolMatch.ExternalTable, SymbolMatch.Default);
        for (int excluded : new int[] { SymbolMatch.Scalar, SymbolMatch.Tabular, SymbolMatch.NonScalar, SymbolMatch.Option,
                SymbolMatch.StoredQueryResult, SymbolMatch.ExternalTable }) {
            assertEquals(0, SymbolMatch.Any & excluded);
        }
        for (int excluded : new int[] { SymbolMatch.Database, SymbolMatch.Cluster, SymbolMatch.GraphModel, SymbolMatch.GraphSnapshot,
                SymbolMatch.Scalar, SymbolMatch.Tabular }) {
            assertEquals(0, SymbolMatch.Default & excluded);
        }
        assertNotEquals(SymbolMatch.Any, SymbolMatch.Default);
    }

    @Test
    void columnMatchesByName() {
        // SymbolMatch.cs:122-144
        var col = new ColumnSymbol("abc", ScalarTypes.Long);
        assertTrue(SymbolMatchExtensions.matches(col, "abc", SymbolMatch.Column));
        assertTrue(SymbolMatchExtensions.matches(col, null, SymbolMatch.Column));
        assertTrue(SymbolMatchExtensions.matches(col, SymbolMatch.Default));
        assertTrue(SymbolMatchExtensions.matches(col, SymbolMatch.Any));
        assertFalse(SymbolMatchExtensions.matches(col, "abd", SymbolMatch.Column));
        assertFalse(SymbolMatchExtensions.matches(col, "ABC", SymbolMatch.Column));
        assertFalse(SymbolMatchExtensions.matches(col, "", SymbolMatch.Column)); // empty name fast-path
        assertFalse(SymbolMatchExtensions.matches(col, "abc", SymbolMatch.Table));
        assertFalse(SymbolMatchExtensions.matches(col, "abc", SymbolMatch.None));
        assertFalse(SymbolMatchExtensions.matches(new ColumnSymbol("", ScalarTypes.Long), "x", SymbolMatch.Column));
        assertFalse(SymbolMatchExtensions.matches(new ColumnSymbol("", ScalarTypes.Long), "", SymbolMatch.Column)); // both empty: still false
    }

    @Test
    void ignoreCaseIsOrdinalIgnoreCase() {
        var col = new ColumnSymbol("abc", ScalarTypes.Long);
        assertTrue(SymbolMatchExtensions.matches(col, "ABC", SymbolMatch.Column, true));
        assertTrue(SymbolMatchExtensions.matches(col, "aBc", SymbolMatch.Column, true));
        assertFalse(SymbolMatchExtensions.matches(col, "abcd", SymbolMatch.Column, true));
        // with ignoreCase the empty-name fast path is not taken: "" equals ""
        assertTrue(SymbolMatchExtensions.matches(new ColumnSymbol("", ScalarTypes.Long), "", SymbolMatch.Column, true));
    }

    @Test
    void tabularityFiltersApplyOnlyAfterKindMatches() {
        var col = new ColumnSymbol("a", ScalarTypes.Long);
        // the Column test returns before the tabularity filters (SymbolMatch.cs:143 vs 182-199)
        assertTrue(SymbolMatchExtensions.matches(col, SymbolMatch.Column | SymbolMatch.Tabular));
        // no kind flag that applies: filters run, then nothing matches
        assertFalse(SymbolMatchExtensions.matches(col, SymbolMatch.Scalar));
        // types are never matched
        assertFalse(SymbolMatchExtensions.matches(ScalarTypes.Long, SymbolMatch.Any));
        assertFalse(SymbolMatchExtensions.matches(new TupleSymbol(col), SymbolMatch.Any | SymbolMatch.Scalar));
    }
}
