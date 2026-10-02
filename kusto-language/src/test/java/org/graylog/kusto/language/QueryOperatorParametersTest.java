// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for QueryOperatorParameters.cs (QueryOperatorParameters, QueryOperatorParameter, QueryOperatorParameterValueKind) and its oracle comparison.
package org.graylog.kusto.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.graylog.kusto.language.symbols.SymbolKind;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.fasterxml.jackson.databind.JsonNode;

class QueryOperatorParametersTest {
    // sed -n '/AllParameters = /,/};/p' upstream/kusto-query-language/src/Kusto.Language/QueryOperatorParameters.cs | grep -c '^ *[A-Za-z]*\.Hide(),'  -> 29
    //   (`//Flags.Hide(),` is commented out upstream)
    private static final int UpstreamAllParametersCount = 29;

    @Test
    void classInitSucceedsWithNoNullStaticFields() throws Exception {
        int fields = CatalogTestSupport.assertNoNullStaticFields(QueryOperatorParameters.class);
        assertTrue(fields > 0);
    }

    @Test
    void allParametersAreHiddenCopies() {
        assertEquals(UpstreamAllParametersCount, QueryOperatorParameters.AllParameters.size());
        for (var p : QueryOperatorParameters.AllParameters) {
            assertTrue(p.isHidden(), p.name());
            assertEquals(SymbolKind.QueryOperatorParameter, p.kind());
        }
        assertEquals("bagexpansion", QueryOperatorParameters.AllParameters.get(0).name());
        assertEquals("force_remote", QueryOperatorParameters.AllParameters.get(28).name());
        // Hide() returns a new instance unless already hidden
        assertNotSame(QueryOperatorParameters.Kind, QueryOperatorParameters.AllParameters.get(19));
        assertEquals("kind", QueryOperatorParameters.AllParameters.get(19).name());
        assertSame(QueryOperatorParameters.BagExpansion, QueryOperatorParameters.BagExpansion.hide());
    }

    @Test
    void kind() {
        var kind = QueryOperatorParameters.Kind;
        assertEquals("kind", kind.name());
        assertEquals(QueryOperatorParameterValueKind.Word, kind.valueKind());
        assertTrue(kind.isCaseSensitive());
        assertFalse(kind.isRepeatable());
        assertFalse(kind.isHidden());
        assertEquals(List.of(), kind.values());
        assertEquals(List.of(), kind.aliases());

        var joinKind = QueryOperatorParameters.JoinParameters.get(0);
        assertNotSame(kind, joinKind);
        assertEquals("kind", joinKind.name());
        assertEquals(KustoFacts.JoinKinds, joinKind.values());
        assertSame(joinKind, joinKind.withValues(joinKind.values())); // reference-equal values: no copy
        assertNotSame(joinKind, joinKind.withValues(KustoFacts.JoinKinds)); // a different list instance: copy
    }

    @Test
    void hintStrategy() {
        var hint = QueryOperatorParameters.HintDotStrategy;
        assertEquals("hint.strategy", hint.name());
        assertFalse(hint.isCaseSensitive());
        assertEquals(KustoFacts.HintStrategies, hint.values());
        var summarizeHint = QueryOperatorParameters.SummarizeParameters.get(1);
        assertEquals("hint.strategy", summarizeHint.name());
        assertFalse(summarizeHint.isCaseSensitive());
        assertEquals(KustoFacts.SummarizeHintStrategies, summarizeHint.values());
        assertTrue(QueryOperatorParameters.HintDotShuffleKey.isRepeatable());
        assertFalse(QueryOperatorParameters.HintDotNumPartitions.isRepeatable());
        assertEquals(QueryOperatorParameterValueKind.IntegerLiteral, QueryOperatorParameters.HintDotNumPartitions.valueKind());
    }

    @Test
    void aliases() {
        assertEquals(List.of("__isFuzzy"), QueryOperatorParameters.IsFuzzy.aliases());
        assertEquals(List.of("with_source"), QueryOperatorParameters.WithSource.aliases());
        // IsHidden is overridden: a "__" prefix does not hide a query operator parameter (Symbol.IsHidden would)
        assertFalse(QueryOperatorParameters.Id.isHidden());
    }

    @Test
    void renderParameters() {
        assertTrue(QueryOperatorParameters.RenderWithDeprecated.hasNoEquals());
        assertTrue(QueryOperatorParameters.RenderByDeprecated.hasNoEquals());
        assertEquals(QueryOperatorParameterValueKind.ColumnList, QueryOperatorParameters.RenderByDeprecated.valueKind());
        assertEquals(17, QueryOperatorParameters.RenderWithProperties.size());
        assertSame(QueryOperatorParameters.RenderKind, QueryOperatorParameters.RenderWithProperties.get(0));
        assertEquals(KustoFacts.ChartKinds, QueryOperatorParameters.RenderKind.values());
        assertEquals(List.of("csl"), QueryOperatorParameters.GetSchemaKind.values());
    }

    @Test
    void emptyParameterSets() {
        assertEquals(0, QueryOperatorParameters.DataTableParameters.size());
        assertEquals(0, QueryOperatorParameters.TakeParameters.size());
        assertEquals(6, QueryOperatorParameters.ParseKvWithProperties.size());
        assertTrue(QueryOperatorParameters.ParseKvWithProperties.get(3).isRepeatable());
    }

    @Test
    void withMethodsCopyOnlyOnChange() {
        var p = QueryOperatorParameters.BinLegacy;
        assertSame(p, p.withIsCaseSensitive(true));
        var insensitive = p.withIsCaseSensitive(false);
        assertNotSame(p, insensitive);
        assertFalse(insensitive.isCaseSensitive());
        assertEquals(p.name(), insensitive.name());
        assertSame(p, p.withHasNoEquals(false));
        assertTrue(p.withHasNoEquals(true).hasNoEquals());
        assertSame(p, p.withIsHidden(false));
    }

    /** Initialises the named classes in order in a fresh class loader and checks every static field of each is non-null (PORTING.md 3.9). */
    private static void initInFreshLoader(String... classNames) throws Exception {
        var entries = System.getProperty("java.class.path").split(java.io.File.pathSeparator);
        var urls = new java.net.URL[entries.length];
        for (int i = 0; i < entries.length; i++)
            urls[i] = java.nio.file.Path.of(entries[i]).toUri().toURL();
        try (var loader = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            for (var name : classNames) {
                var type = Class.forName("org.graylog.kusto.language." + name, true, loader);
                assertNotSame(Class.forName("org.graylog.kusto.language." + name), type, "isolated loader");
                for (Field f : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) || f.isSynthetic() || f.getName().startsWith("_") || f.getName().startsWith("s_"))
                        continue; // lazy caches (KustoFacts._knownQueryOperatorParameterNames, PlugIns.s_nameToPlugInMap)
                    f.setAccessible(true);
                    assertTrue(f.get(null) != null, name + "." + f.getName());
                }
            }
        }
    }

    @Test
    void staticInitInEitherOrderLeavesNoNulls() throws Exception {
        initInFreshLoader("QueryOperatorParameters", "KustoFacts");
        initInFreshLoader("KustoFacts", "QueryOperatorParameters");
        initInFreshLoader("PlugIns", "Aggregates", "Operators", "FunctionHelpers", "QueryOperatorParameters");
        initInFreshLoader("FunctionHelpers", "Operators", "Aggregates", "PlugIns");
    }

    // ---- oracle comparison ----

    private static void compare(String ctx, JsonNode rec, QueryOperatorParameter p) {
        assertEquals(rec.path("kind").asText(), p.kind().name(), ctx + " kind");
        assertEquals(rec.path("name").asText(), p.name(), ctx + " name");
        assertEquals(strings(rec.path("aliases")), p.aliases(), ctx + " aliases");
        assertEquals(rec.path("valueKind").asText(), p.valueKind().name(), ctx + " valueKind");
        assertEquals(rec.path("isRepeatable").asBoolean(), p.isRepeatable(), ctx + " isRepeatable");
        assertEquals(rec.path("isCaseSensitive").asBoolean(), p.isCaseSensitive(), ctx + " isCaseSensitive");
        assertEquals(strings(rec.path("values")), p.values(), ctx + " values");
        assertEquals(rec.path("isHidden").asBoolean(), p.isHidden(), ctx + " isHidden");
        assertEquals(rec.path("hasNoEquals").asBoolean(), p.hasNoEquals(), ctx + " hasNoEquals");
    }

    private static List<String> strings(JsonNode array) {
        var result = new ArrayList<String>();
        for (var n : array)
            result.add(n.asText());
        return result;
    }

    @TestFactory
    Stream<DynamicTest> allParametersMatchOracleGlobalsDump() throws IOException {
        Assumptions.assumeTrue(Files.exists(CatalogTestSupport.GlobalsDump), "oracle dump absent: " + CatalogTestSupport.GlobalsDump);
        var records = CatalogTestSupport.readGlobals("queryOperatorParameters");
        assertEquals(QueryOperatorParameters.AllParameters.size(), records.size(), "record count");
        var tests = new ArrayList<DynamicTest>();
        for (int i = 0; i < records.size(); i++) {
            final int index = i;
            final var rec = records.get(i);
            final String ctx = i + " " + rec.path("name").asText();
            tests.add(DynamicTest.dynamicTest(ctx, () -> {
                assertEquals(index, rec.path("index").asInt(), ctx + " index");
                compare(ctx, rec, QueryOperatorParameters.AllParameters.get(index));
            }));
        }
        return tests.stream();
    }

    /** Every other public static field (single parameter or parameter list), in declaration order. */
    @TestFactory
    Stream<DynamicTest> fieldsMatchOracleGlobalsDump() throws Exception {
        Assumptions.assumeTrue(Files.exists(CatalogTestSupport.GlobalsDump), "oracle dump absent: " + CatalogTestSupport.GlobalsDump);
        var records = CatalogTestSupport.readGlobals("queryOperatorParameterFields");

        // flatten the Java fields the same way Globals.cs does
        record Item(String field, boolean isList, int index, QueryOperatorParameter value) { }
        var items = new ArrayList<Item>();
        for (Field f : QueryOperatorParameters.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (!Modifier.isPublic(m) || !Modifier.isStatic(m) || f.getName().equals("AllParameters"))
                continue;
            var value = f.get(null);
            if (value instanceof QueryOperatorParameter single) {
                items.add(new Item(f.getName(), false, 0, single));
            } else if (value instanceof List<?> list) {
                if (list.isEmpty())
                    items.add(new Item(f.getName(), true, -1, null));
                for (int i = 0; i < list.size(); i++)
                    items.add(new Item(f.getName(), true, i, (QueryOperatorParameter) list.get(i)));
            }
        }

        assertEquals(records.size(), items.size(), "record count");
        var tests = new ArrayList<DynamicTest>();
        for (int i = 0; i < records.size(); i++) {
            final var rec = records.get(i);
            final var item = items.get(i);
            final String ctx = i + " " + rec.path("field").asText() + "[" + rec.path("index").asInt() + "]";
            tests.add(DynamicTest.dynamicTest(ctx, () -> {
                assertEquals(rec.path("field").asText(), item.field(), ctx + " field");
                assertEquals(rec.path("isList").asBoolean(), item.isList(), ctx + " isList");
                assertEquals(rec.path("index").asInt(), item.index(), ctx + " index");
                if (item.value() != null)
                    compare(ctx, rec, item.value());
            }));
        }
        return tests.stream();
    }
}
