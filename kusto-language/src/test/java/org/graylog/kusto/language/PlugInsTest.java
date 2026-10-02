// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for the PlugIns catalog (PlugIns.cs) and its oracle comparison.
package org.graylog.kusto.language;

import static org.graylog.kusto.language.CatalogTestSupport.boolLit;
import static org.graylog.kusto.language.CatalogTestSupport.longLit;
import static org.graylog.kusto.language.CatalogTestSupport.named;
import static org.graylog.kusto.language.CatalogTestSupport.star;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.stream.Stream;

import org.graylog.kusto.language.CatalogTestSupport.FakeContext;
import org.graylog.kusto.language.symbols.ArgumentKind;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.FunctionSymbol;
import org.graylog.kusto.language.symbols.Parameter;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.syntax.Expression;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class PlugInsTest {
    // grep -c "new FunctionSymbol(" upstream/kusto-query-language/src/Kusto.Language/PlugIns.cs -> 50
    //   minus 1 inside `#if false` (funnel_analysis) = 49 fields (grep -c "public static readonly FunctionSymbol" -> 49);
    //   All omits SchemaMerge -> 48 entries
    private static final int UpstreamFunctionSymbolFields = 49;
    private static final int UpstreamAllCount = 48;

    private static final ColumnSymbol A = new ColumnSymbol("a", ScalarTypes.Long);
    private static final ColumnSymbol B = new ColumnSymbol("b", ScalarTypes.String);

    private static List<TypeSymbol> unknowns(int n) {
        var list = new ArrayList<TypeSymbol>();
        for (int i = 0; i < n; i++)
            list.add(ScalarTypes.Unknown);
        return list;
    }

    private static List<Parameter> layout(Signature sig, List<Expression> args) {
        var list = new ArrayList<Parameter>();
        sig.getArgumentParameters(args, list);
        return list;
    }

    private static List<String> names(List<Parameter> parameters) {
        var result = new ArrayList<String>();
        for (var p : parameters)
            result.add(p.name());
        return result;
    }

    private static List<String> columnNames(TableSymbol table) {
        var result = new ArrayList<String>();
        for (var c : table.columns())
            result.add(c.name());
        return result;
    }

    @Test
    void classInitSucceedsWithNoNullStaticFields() throws Exception {
        CatalogTestSupport.assertNoNullStaticFields(PlugIns.class);
        assertEquals(UpstreamFunctionSymbolFields, CatalogTestSupport.publicStaticFieldsOfType(PlugIns.class, FunctionSymbol.class).size());
    }

    @Test
    void allHoldsEveryFieldButSchemaMerge() throws Exception {
        assertEquals(UpstreamAllCount, PlugIns.All.size());
        var seen = Collections.newSetFromMap(new IdentityHashMap<FunctionSymbol, Boolean>());
        seen.addAll(PlugIns.All);
        for (var f : CatalogTestSupport.publicStaticFieldsOfType(PlugIns.class, FunctionSymbol.class))
            assertEquals(f != PlugIns.SchemaMerge, seen.contains(f), f.name());
        assertSame(PlugIns.ActiveUseCounts, PlugIns.All.get(0));
        assertSame(PlugIns.AzureDigitalTwinsQueryRequest, PlugIns.All.get(4));
        assertSame(PlugIns.AIEmbeddings, PlugIns.All.get(47));
    }

    @Test
    void getPlugIn() {
        assertSame(PlugIns.BagUnpack, PlugIns.getPlugIn("bag_unpack"));
        assertSame(PlugIns.Pivot, PlugIns.getPlugIn("pivot"));
        assertNull(PlugIns.getPlugIn("schema_merge")); // not in All
        assertNull(PlugIns.getPlugIn("BAG_UNPACK")); // ordinal keys
        assertThrows(NullPointerException.class, () -> PlugIns.getPlugIn(null)); // ArgumentNullException upstream
    }

    @Test
    void hiddenPlugIns() {
        assertTrue(PlugIns.PostgreSqlRequest.isHidden());
        assertTrue(PlugIns.DaxRequest.isHidden());
        assertTrue(PlugIns.GqlRequest.isHidden());
        assertFalse(PlugIns.SqlRequest.isHidden());
        assertEquals("ai_embeddings", PlugIns.AIEmbedText_Deprecated.alternative());
    }

    @Test
    void bagUnpackReturnsOpenRowScopeMinusTheUnpackedColumn() {
        var sig = PlugIns.BagUnpack.signatures().get(0);
        var row = new TableSymbol(A, B).withIsSerialized(true);
        var ctx = new FakeContext(sig, List.of(), List.of(), List.of(), row);
        var result = (TableSymbol) sig.customReturnType().invoke(ctx);
        assertEquals(List.of("a", "b"), columnNames(result));
        assertTrue(result.isOpen());
        assertTrue(result.isSerialized()); // inheritable property
        var p = sig.parameters().get(1);
        assertEquals("column_prefix", p.name());
        assertEquals(ArgumentKind.LiteralNotEmpty, p.argumentKind());
        assertEquals(0, p.minOccurring());
    }

    @Test
    void pivotWithoutArgumentsKeepsAllColumnsAndIsOpen() {
        var sig = PlugIns.Pivot.signatures().get(0);
        var ctx = new FakeContext(sig, List.of(), List.of(), List.of(), new TableSymbol(A, B));
        var result = (TableSymbol) sig.customReturnType().invoke(ctx);
        assertEquals(List.of("a", "b"), columnNames(result));
        assertTrue(result.isOpen());
        assertEquals(ArgumentKind.Aggregate, sig.parameters().get(1).argumentKind());
        assertEquals(FunctionHelpers.MaxRepeat, sig.parameters().get(2).maxOccurring());
    }

    @Test
    void autoclusterPrependsSegmentColumns() {
        var sig = PlugIns.AutoCluster.signatures().get(0);
        var ctx = new FakeContext(sig, List.of(), List.of(), List.of(), new TableSymbol(A));
        var result = (TableSymbol) sig.customReturnType().invoke(ctx);
        assertEquals(List.of("SegmentId", "Count", "Percent", "a"), columnNames(result));
        assertEquals("~", sig.parameters().get(0).defaultValueIndicator());
    }

    @Test
    void dcountIntersectAppendsOneColumnPerArgument() {
        var sig = PlugIns.DCountIntersect.signatures().get(0);
        var args = List.of(star(), star(), star());
        var ctx = new FakeContext(sig, args, unknowns(3), List.of(sig.parameters().get(0), sig.parameters().get(0), sig.parameters().get(0)), new TableSymbol(A));
        var result = (TableSymbol) sig.customReturnType().invoke(ctx);
        assertEquals(List.of("a", "s0", "s1", "s2"), columnNames(result));
    }

    @Test
    void aiEmbeddingsAddsErrorColumnWhenRequested() {
        var sig = PlugIns.AIEmbeddings.signatures().get(0);
        var text = star();
        var conn = star();
        var include = boolLit(true);
        var ps = sig.parameters();
        var ctx = new FakeContext(sig, List.of(text, conn, include), unknowns(3), List.of(ps.get(0), ps.get(1), ps.get(3)), new TableSymbol(A))
            .resultName(text, "t");
        var result = (TableSymbol) sig.customReturnType().invoke(ctx);
        assertEquals(List.of("a", "t_embeddings", "t_embeddings_error"), columnNames(result));

        var ctxFalse = new FakeContext(sig, List.of(text, conn, boolLit(false)), unknowns(3), List.of(ps.get(0), ps.get(1), ps.get(3)), new TableSymbol(A))
            .resultName(text, "t");
        assertEquals(List.of("a", "t_embeddings"), columnNames((TableSymbol) sig.customReturnType().invoke(ctxFalse)));

        // PORT-BUG mirror: ArgumentKind.Column | ArgumentKind.Literal is (ArgumentKind)13 upstream; Java uses Expression
        assertEquals(ArgumentKind.Expression, ps.get(0).argumentKind());
    }

    @Test
    void newActivityMetricsLayoutDetectsCohortAndDimensions() {
        var sig = PlugIns.NewActivityMetrics.signatures().get(0);
        List<Expression> args = List.of(star(), star(), star(), star(), star(), longLit("1"), star());
        assertEquals(List.of("IdColumn", "TimelineColumn", "Start", "End", "Window", "Cohort", "Dimension"), names(layout(sig, args)));
    }

    @Test
    void sequenceDetectLayoutTakesBooleanArgumentsAsExpressions() {
        var sig = PlugIns.SequenceDetect.signatures().get(0);
        List<Expression> args = List.of(star(), star(), star(), star(), boolLit(true), boolLit(false), star(), star());
        assertEquals(List.of("TimelineColumn", "MaxSeqeunceStepWindow", "MaxSequenceSpan", "Expr", "Expr", "Expr", "Dimension", "Dimension"), names(layout(sig, args)));
    }

    @Test
    void geoPolygonLookupLayoutMatchesNamedArgumentsCaseInsensitively() {
        var sig = PlugIns.Geo_Polygon_Lookup.signatures().get(0);
        List<Expression> args = List.of(star(), star(), star(), star(), named("Return_Unmatched", boolLit(true)), named("RADIUS", longLit("1")));
        var params = layout(sig, args);
        assertEquals(List.of("LookupTable", "LookupPolygonKey", "SourceLongitude", "SourceLatitude", "return_unmatched", "radius"), names(params));
        List<Expression> positional = List.of(star(), star(), star(), star(), longLit("1"), boolLit(true), longLit("2"), boolLit(true));
        assertEquals(List.of("LookupTable", "LookupPolygonKey", "SourceLongitude", "SourceLatitude", "radius", "return_unmatched", "lookup_area_radius", "return_lookup_key"),
            names(layout(sig, positional)));
    }

    @Test
    void ipv4LookupLayoutTreatsTrailingBooleanAsReturnUnmatched() {
        var sig = PlugIns.Ipv4_Lookup.signatures().get(0);
        List<Expression> args = List.of(star(), star(), star(), star(), star(), boolLit(true));
        assertEquals(List.of("LookupTable", "SourceIPv4Key", "IPv4LookupKey", "ExtraKey", "ExtraKey", "return_unmatched"), names(layout(sig, args)));
    }

    @Test
    void geoLookupWithUnknownTableReturnsRowScope() {
        var sig = PlugIns.Geo_Line_Lookup.signatures().get(0);
        var row = new TableSymbol(A);
        var ctx = new FakeContext(sig, List.of(star()), unknowns(1), List.of(sig.parameters().get(0)), row);
        assertSame(row, sig.customReturnType().invoke(ctx));
    }

    @Test
    void outputSchemaFallsBackToOpenTable() {
        var sig = PlugIns.Python.signatures().get(0);
        var ctx = new FakeContext(sig, List.of(star()), unknowns(1), List.of(sig.parameters().get(0)), new TableSymbol(A));
        var result = (TableSymbol) sig.customReturnType().invoke(ctx);
        assertTrue(result.isOpen());
        assertEquals(0, result.columns().size());
        assertNotNull(PlugIns.CSharp.signatures().get(0).customReturnType());
        assertNotNull(PlugIns.R.signatures().get(0).customReturnType());
    }

    @Test
    void rollingPercentileNamesColumnFromLiterals() {
        var sig = PlugIns.RollingPercentile.signatures().get(0);
        var ps = sig.parameters();
        var args = List.<Expression>of(star(), longLit("90"), star(), star(), longLit("5"));
        var ctx = new FakeContext(sig, args, unknowns(5), List.of(ps.get(0), ps.get(1), ps.get(2), ps.get(3), ps.get(4)), new TableSymbol(A));
        var result = (TableSymbol) sig.customReturnType().invoke(ctx);
        assertEquals(List.of("rolling_5_percentile_value_90"), columnNames(result));
        assertSame(ScalarTypes.Long, result.columns().get(0).type());
    }

    @Test
    void staticReturnTypes() {
        assertNotNull(PlugIns.Narrow.signatures().get(0).declaredReturnType());
        assertEquals(List.of("Row", "Column", "Value"), columnNames((TableSymbol) PlugIns.Narrow.signatures().get(0).declaredReturnType()));
        assertEquals(4, ((TableSymbol) PlugIns.SchemaMerge.signatures().get(0).declaredReturnType()).columns().size());
    }

    // ---- oracle comparison ----

    @TestFactory
    Stream<DynamicTest> matchesOracleGlobalsDump() throws IOException {
        Assumptions.assumeTrue(Files.exists(CatalogTestSupport.GlobalsDump), "oracle dump absent: " + CatalogTestSupport.GlobalsDump);
        var records = CatalogTestSupport.readGlobals("plugins");
        assertEquals(PlugIns.All.size(), records.size(), "record count");
        var tests = new ArrayList<DynamicTest>();
        for (int i = 0; i < records.size(); i++) {
            final int index = i;
            final var rec = records.get(i);
            final String ctx = i + " " + rec.path("name").asText();
            tests.add(DynamicTest.dynamicTest(ctx, () -> {
                assertEquals(index, rec.path("index").asInt(), ctx + " index");
                CatalogTestSupport.compareFunction(ctx, rec, PlugIns.All.get(index));
            }));
        }
        return tests.stream();
    }
}
