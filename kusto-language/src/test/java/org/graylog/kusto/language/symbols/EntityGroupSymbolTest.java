// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for EntityGroupSymbol and EntityGroupElementSymbol (Symbols/EntityGroup*.cs).
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class EntityGroupSymbolTest {
    @Test
    void bodyFromNullDefinitionIsEmptyGroup() {
        assertEquals("entity_group []", EntityGroupSymbol.getBodyFromDefinition(null));
    }

    @Test
    void bodyFromBracketedList() {
        assertEquals("entity_group [a, b]", EntityGroupSymbol.getBodyFromDefinition("[a, b]"));
        assertEquals("entity_group [a, b]", EntityGroupSymbol.getBodyFromDefinition("  [ a, b ]  "));
        assertEquals("entity_group [a, b]", EntityGroupSymbol.getBodyFromDefinition("a, b"));
        assertEquals("entity_group []", EntityGroupSymbol.getBodyFromDefinition("[]"));
    }

    @Test
    void bodyKeepsExistingEntityGroupExpression() {
        assertEquals("entity_group [x]", EntityGroupSymbol.getBodyFromDefinition("entity_group [x]"));
        assertEquals("entity_group [x]", EntityGroupSymbol.getBodyFromDefinition("  entity_group [x] "));
    }

    @Test
    void definitionBasedGroup() {
        var group = new EntityGroupSymbol("EG", "[a, b]", "my description");
        assertEquals("EG", group.name());
        assertEquals("[a, b]", group.definition());
        assertEquals("my description", group.description());
        assertEquals(SymbolKind.EntityGroup, group.kind());
        assertEquals(Tabularity.None, group.tabularity());
        assertTrue(group.members().isEmpty());
        assertSame(group, group.signature().symbol());
    }

    @Test
    void descriptionDefaultsToEmpty() {
        assertEquals("", new EntityGroupSymbol("EG", "[a]").description());
        assertEquals("", new EntityGroupSymbol("EG", List.<Symbol>of()).description());
    }

    @Test
    void memberBasedGroup() {
        var t1 = new TableSymbol("T1", new ColumnSymbol("a", ScalarTypes.Int));
        var t2 = new TableSymbol("T2", new ColumnSymbol("b", ScalarTypes.Int));
        var group = new EntityGroupSymbol("EG", t1, t2);
        assertNull(group.definition());
        assertEquals(List.of(t1, t2), group.members());
        assertSame(group, group.signature().symbol());
        assertThrows(UnsupportedOperationException.class, () -> group.members().add(t1));
        assertThrows(IllegalArgumentException.class, () -> new EntityGroupSymbol("EG", Arrays.asList(t1, null)));
    }

    @Test
    void emptyGroup() {
        assertEquals("", EntityGroupSymbol.Empty.name());
        assertTrue(EntityGroupSymbol.Empty.members().isEmpty());
        assertEquals("", new EntityGroupSymbol().name());
        var unnamed = new EntityGroupSymbol(new TableSymbol("T", new ColumnSymbol("a", ScalarTypes.Int)));
        assertEquals("", unnamed.name());
        assertEquals(1, unnamed.members().size());
    }

    @Test
    void elementUsesFirstEntityWithMembers() {
        var empty = new TableSymbol("E");
        var full = new TableSymbol("F", new ColumnSymbol("a", ScalarTypes.Int));
        var group = new EntityGroupSymbol("EG", empty, full);
        var element = new EntityGroupElementSymbol("el", group);
        assertSame(group, element.entityGroup());
        assertSame(full, element.underlyingSymbol());
        assertEquals(SymbolKind.EntityGroupElement, element.kind());
        assertEquals(full.tabularity(), element.tabularity());
    }

    @Test
    void elementFallsBackToFirstEntityThenError() {
        var empty = new TableSymbol("E");
        var group = new EntityGroupSymbol("EG", empty);
        assertSame(empty, new EntityGroupElementSymbol("el", group).underlyingSymbol());
        assertSame(ErrorSymbol.Instance, new EntityGroupElementSymbol("el", new EntityGroupSymbol("EG")).underlyingSymbol());
        var bare = new EntityGroupElementSymbol("el");
        assertSame(EntityGroupSymbol.Empty, bare.entityGroup());
        assertSame(ErrorSymbol.Instance, bare.underlyingSymbol());
    }

    @Test
    void elementMembersAreTheSpecialVariables() {
        var element = new EntityGroupElementSymbol("el");
        assertSame(EntityGroupElementSymbol.SpecialMembers, element.members());
        assertEquals(2, element.members().size());
        assertEquals("$current_database", element.members().get(0).name());
        assertEquals("$current_cluster_endpoint", element.members().get(1).name());
        assertSame(ScalarTypes.String, ((VariableSymbol) element.members().get(0)).type());
    }
}
