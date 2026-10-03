// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for GraphSymbol, GraphModelSymbol, OperatorSymbol, OptionSymbol and CommandSymbol.
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.graylog.kusto.language.utils.dotnet.Out;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class OtherSymbolsTest {
    @Test
    void graphDefaultsEdgeShapeToEmptyTable() {
        var graph = new GraphSymbol("g", (TableSymbol) null);
        assertSame(TableSymbol.Empty, graph.edgeShape());
        assertNull(graph.nodeShape());
        assertEquals(SymbolKind.Graph, graph.kind());
        assertEquals(Tabularity.Other, graph.tabularity());
        assertSame(TableSymbol.Empty, GraphSymbol.Empty.edgeShape());
        assertSame(TableSymbol.Empty, GraphSymbol.Empty.nodeShape());
    }

    @Test
    void graphFromSchemas() {
        var graph = GraphSymbol.from("(src:string, dst:string)", "(id:string)");
        assertEquals("", graph.name());
        assertEquals(2, graph.edgeShape().columns().size());
        assertEquals(1, graph.nodeShape().columns().size());
        assertNull(GraphSymbol.from("(a:int)").nodeShape());
    }

    @Test
    void graphWithNameAndEdgeShape() {
        var graph = new GraphSymbol("g", new TableSymbol("E", new ColumnSymbol("a", ScalarTypes.Int)));
        assertSame(graph, graph.withName(null));
        assertEquals("h", graph.withName("h").name());
        assertSame(graph, graph.withEdgeShape(graph.edgeShape()));
        var other = new TableSymbol("E2", new ColumnSymbol("b", ScalarTypes.Int));
        assertSame(other, graph.withEdgeShape(other).edgeShape());
    }

    // PORT-BUG mirror: WithNodeShape tests this.NodeShape, not the argument (GraphSymbol.cs:96).
    @Test
    void graphWithNodeShapeMirrorsUpstreamBug() {
        var nodes = new TableSymbol("N", new ColumnSymbol("id", ScalarTypes.Int));
        var noNodes = new GraphSymbol("g", new TableSymbol("E", new ColumnSymbol("a", ScalarTypes.Int)));
        assertSame(noNodes, noNodes.withNodeShape(nodes));
        var withNodes = new GraphSymbol("g", noNodes.edgeShape(), nodes);
        var other = new TableSymbol("N2", new ColumnSymbol("id2", ScalarTypes.Int));
        assertSame(other, withNodes.withNodeShape(other).nodeShape());
        assertNull(withNodes.withNodeShape(null).nodeShape());
    }

    @Test
    void graphMergeHandlesNulls() {
        var a = new GraphSymbol(new TableSymbol(new ColumnSymbol("x", ScalarTypes.Int)));
        assertSame(a, GraphSymbol.merge(a, null));
        assertSame(a, GraphSymbol.merge(null, a));
        assertNull(GraphSymbol.merge(null, null));
    }

    @Test
    void graphMergeCombinesShapes() {
        var a = new GraphSymbol(new TableSymbol(new ColumnSymbol("x", ScalarTypes.Int)));
        var b = new GraphSymbol(new TableSymbol(new ColumnSymbol("y", ScalarTypes.Int)),
            new TableSymbol(new ColumnSymbol("id", ScalarTypes.Int)));
        var merged = GraphSymbol.merge(a, b);
        assertEquals(2, merged.edgeShape().columns().size());
        assertEquals(1, merged.nodeShape().columns().size());
    }

    @Test
    void graphModelFromStrings() {
        var model = new GraphModelSymbol("m", "edgeQuery", "nodeQuery", List.of("s1", "s2"));
        assertEquals(SymbolKind.GraphModel, model.kind());
        assertEquals(Tabularity.None, model.tabularity());
        assertEquals(1, model.edges().size());
        assertEquals(1, model.nodes().size());
        assertSame(model, model.edges().get(0).symbol());
        assertSame(model, model.nodes().get(0).symbol());
        assertEquals(2, model.snapshots().size());
        assertSame(model, model.snapshots().get(0).model());
        assertSame(model.snapshots().get(0), model.members().get(0));
        assertEquals(SymbolKind.GraphSnapshot, model.snapshots().get(0).kind());
    }

    @Test
    void graphModelWithoutPartsHasEmptyLists() {
        var model = new GraphModelSymbol("m");
        assertTrue(model.edges().isEmpty());
        assertTrue(model.nodes().isEmpty());
        assertTrue(model.snapshots().isEmpty());
        assertTrue(model.members().isEmpty());
    }

    @Test
    void graphModelSnapshotLookupLastWins() {
        var model = new GraphModelSymbol("m", List.<String>of(), List.<String>of(), List.of("s1", "s2", "s1"));
        var found = new Out<GraphSnapshotSymbol>();
        assertTrue(model.tryGetSnapshot("s1", found));
        assertSame(model.snapshots().get(2), found.value);
        assertTrue(model.tryGetSnapshot("s2", found));
        assertSame(model.snapshots().get(1), found.value);
        assertTrue(!model.tryGetSnapshot("S1", found)); // case sensitive
        assertNull(found.value);
        assertThrows(NullPointerException.class, () -> model.tryGetSnapshot(null, new Out<>()));
    }

    @Test
    void operatorSymbolAttachesItselfToItsSignatures() {
        var sig = new Signature(ScalarTypes.Bool);
        var op = new OperatorSymbol(OperatorKind.Add, sig);
        assertEquals(OperatorKind.Add.toString(), op.name());
        assertEquals(OperatorKind.Add, op.operatorKind());
        assertEquals(SymbolKind.Operator, op.kind());
        assertEquals(Tabularity.Scalar, op.tabularity());
        assertEquals(List.of(sig), op.signatures());
        assertSame(op, sig.symbol());
        assertNull(op.result());
    }

    @Test
    void operatorSymbolFromResultType() {
        var op = new OperatorSymbol(OperatorKind.Add, ScalarTypes.Int);
        assertEquals(1, op.signatures().size());
        assertSame(op, op.signatures().get(0).symbol());
        assertThrows(NullPointerException.class, () -> new OperatorSymbol(OperatorKind.Add, (TypeSymbol) null));
    }

    @Test
    void optionSymbolDefaults() {
        var o = new OptionSymbol("opt");
        assertEquals("", o.description());
        assertTrue(o.types().isEmpty());
        assertTrue(o.examples().isEmpty());
        var typed = new OptionSymbol("opt", "d", ScalarTypes.Bool, List.of("'a'"));
        assertEquals(List.of(ScalarTypes.Bool), typed.types());
        assertEquals(List.of("'a'"), typed.examples());
        assertThrows(NullPointerException.class, () -> new OptionSymbol("opt", "d", (ScalarSymbol) null));
    }

    @Test
    void commandSymbolConvertsLazily() {
        var cmd = new CommandSymbol("c", "(a:int, b:string)");
        assertEquals("", cmd.construction());
        assertEquals(SymbolKind.Command, cmd.kind());
        assertEquals("(a:int, b:string)", cmd.resultSchema());
        assertEquals(2, cmd.resultType().columns().size());
        assertSame(cmd.resultType(), cmd.resultType());
        assertTrue(new CommandSymbol("c", "()").resultType().isOpen());
        assertTrue(new CommandSymbol("c", "(*)").resultType().isOpen());
        var fromType = new CommandSymbol("c", new TableSymbol("R", new ColumnSymbol("a", ScalarTypes.Int)), "ctor");
        assertEquals("ctor", fromType.construction());
        assertEquals("(a: int)", fromType.resultSchema()); // SchemaDisplay.GetText spacing, as in the oracle goldens
        assertNull(new CommandSymbol("c", (String) null).resultType());
    }

    @Test
    void commandSymbolFromResultType() {
        var fromType = new CommandSymbol("c", new TableSymbol("R", new ColumnSymbol("a", ScalarTypes.Int)), "ctor");
        assertEquals("ctor", fromType.construction());
        assertEquals(SymbolKind.Command, fromType.kind());
        assertEquals(1, fromType.resultType().columns().size());
    }
}
