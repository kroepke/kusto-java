// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for FunctionBodyFacts.cs.
package org.graylog.kusto.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.symbols.Parameter;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.junit.jupiter.api.Test;

class FunctionBodyFactsTest {

    @Test
    void functionBodyFactsDefault() {
        var d = FunctionBodyFacts.Default;
        assertFalse(d.isInteresting());
        assertFalse(d.hasVariableReturnType());
        assertNull(d.nonVariableReturnType());
        assertEquals(0, d.dependentParameters().size());
        assertEquals(0, d.unqualifiedTableNames().size());
        assertSame(d, d.withHasClusterCall(false));
        assertSame(d, d.withIsInteresting(true)); // PORT-BUG mirror: Flags.None never changes the flags
    }

    @Test
    void functionBodyFactsFlags() {
        var f = FunctionBodyFacts.Default.withHasClusterCall(true).withHasGraphCall(true);
        assertTrue(f.hasClusterCall());
        assertTrue(f.hasGraphCall());
        assertFalse(f.hasDatabaseCall());
        assertTrue(f.isInteresting());
        assertFalse(f.hasVariableReturnType());
        var g = f.withHasClusterCall(false);
        assertFalse(g.hasClusterCall());
        assertTrue(g.hasGraphCall());
        assertSame(g, g.withHasGraphCall(true));
        var t = FunctionBodyFacts.Default.withHasUnqualifiedTableCall(true);
        assertTrue(t.hasVariableReturnType());
    }

    @Test
    void functionBodyFactsLists() {
        var p = new Parameter("p", ScalarTypes.Long);
        var q = new Parameter("q", ScalarTypes.Long);
        var f = FunctionBodyFacts.Default.addDependentParameter(p);
        assertEquals(List.of(p), new ArrayList<>(f.dependentParameters()));
        assertTrue(f.hasVariableReturnType());
        assertSame(f, f.addDependentParameter(p));
        assertSame(f, f.addDependentParameters(List.of(p)));
        var g = f.addDependentParameters(List.of(p, q));
        assertEquals(List.of(p, q), new ArrayList<>(g.dependentParameters()));
        assertEquals(List.of(q, p), new ArrayList<>(g.withDependentParameters(List.of(q, p, q)).dependentParameters())); // Distinct keeps first

        var n = FunctionBodyFacts.Default.addUnqualifiedTableName("T").addUnqualifiedTableName("U").addUnqualifiedTableName("T");
        assertEquals(List.of("T", "U"), new ArrayList<>(n.unqualifiedTableNames()));
        var other = FunctionBodyFacts.Default.withUnqualifiedTableNames(List.of("U", "V")).withHasDatabaseCall(true);
        var combined = n.combineCalledFunction(other);
        assertEquals(List.of("T", "U", "V"), new ArrayList<>(combined.unqualifiedTableNames()));
        assertTrue(combined.hasDatabaseCall());
        assertSame(n.unqualifiedTableNames(), n.combineCalledFunction(FunctionBodyFacts.Default).unqualifiedTableNames());
        assertSame(FunctionBodyFacts.Default, FunctionBodyFacts.Default.combineCalledFunction(FunctionBodyFacts.Default));

        var typed = FunctionBodyFacts.Default.withNonVariableReturnType(ScalarTypes.Long).withHasSyntaxErrors(true);
        assertSame(ScalarTypes.Long, typed.nonVariableReturnType());
        assertTrue(typed.hasSyntaxErrors());
        assertSame(typed, typed.withNonVariableReturnType(ScalarTypes.Long));
    }
}
