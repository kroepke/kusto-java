// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for the Aggregates catalog (Aggregates.cs) and its oracle comparison.
package org.graylog.kusto.language;

import static org.graylog.kusto.language.CatalogTestSupport.realLit;
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
import org.graylog.kusto.language.symbols.ParameterTypeKind;
import org.graylog.kusto.language.symbols.ResultNameKind;
import org.graylog.kusto.language.symbols.ReturnTypeKind;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.graylog.kusto.language.symbols.TupleSymbol;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.syntax.Expression;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class AggregatesTest {
    // grep -c "new FunctionSymbol(" upstream/kusto-query-language/src/Kusto.Language/Aggregates.cs                  -> 61
    // grep -c "public static readonly FunctionSymbol" upstream/kusto-query-language/src/Kusto.Language/Aggregates.cs -> 61
    private static final int UpstreamFunctionSymbolCount = 61;

    private static FunctionSymbol byName(String name) {
        FunctionSymbol found = null;
        for (var fn : Aggregates.All) {
            if (fn.name().equals(name)) {
                assertNull(found, "duplicate " + name);
                found = fn;
            }
        }
        assertNotNull(found, name);
        return found;
    }

    @Test
    void classInitSucceedsWithNoNullStaticFields() throws Exception {
        CatalogTestSupport.assertNoNullStaticFields(Aggregates.class);
        var fields = CatalogTestSupport.publicStaticFieldsOfType(Aggregates.class, FunctionSymbol.class);
        assertEquals(UpstreamFunctionSymbolCount, fields.size());
    }

    @Test
    void allHoldsEveryFieldOnceInUpstreamOrder() throws Exception {
        assertEquals(UpstreamFunctionSymbolCount, Aggregates.All.size());
        var seen = Collections.newSetFromMap(new IdentityHashMap<FunctionSymbol, Boolean>());
        seen.addAll(Aggregates.All);
        for (var f : CatalogTestSupport.publicStaticFieldsOfType(Aggregates.class, FunctionSymbol.class))
            assertTrue(seen.contains(f), f.name());
        // first, a reordered middle part (MakeDictionary before Passthrough upstream) and last entries
        assertSame(Aggregates.Sum, Aggregates.All.get(0));
        assertSame(Aggregates.MakeDictionary, Aggregates.All.get(26));
        assertSame(Aggregates.Passthrough, Aggregates.All.get(30));
        assertSame(Aggregates.CountDistinctIf, Aggregates.All.get(60));
    }

    @Test
    void count() {
        var count = byName("count");
        assertSame(Aggregates.Count, count);
        assertEquals(2, count.signatures().size());
        assertEquals(0, count.signatures().get(0).minArgumentCount());
        assertEquals(0, count.signatures().get(0).maxArgumentCount());
        var legacy = count.signatures().get(1);
        assertTrue(legacy.isHidden());
        assertEquals("countif", legacy.alternative());
        assertEquals(ScalarTypes.Bool, legacy.parameters().get(0).declaredTypes().get(0));
        assertEquals(ResultNameKind.PrefixAndFirstArgument, count.resultNameKind());
        assertEquals("count", count.resultNamePrefix());
        assertFalse(count.isHidden());
    }

    @Test
    void cntIsHiddenAndObsolete() {
        assertTrue(Aggregates.Cnt.isHidden());
        assertEquals("count", Aggregates.Cnt.alternative());
        assertEquals("cnt", Aggregates.Cnt.resultNamePrefix());
    }

    @Test
    void sum() {
        var sum = Aggregates.Sum;
        assertEquals(ReturnTypeKind.Parameter0Promoted, sum.signatures().get(0).returnKind());
        assertEquals(ParameterTypeKind.Summable, sum.signatures().get(0).parameters().get(0).typeKind());
        assertEquals("sum", sum.resultNamePrefix());
    }

    @Test
    void argMaxHasRepeatingStarAllowedReturnedParameter() {
        var sig = Aggregates.ArgMax.signatures().get(0);
        assertEquals(ReturnTypeKind.Custom, sig.returnKind());
        assertNotNull(sig.customReturnType());
        var returned = sig.parameters().get(1);
        assertEquals("returned", returned.name());
        assertEquals(ArgumentKind.StarAllowed, returned.argumentKind());
        assertEquals(0, returned.minOccurring());
        assertEquals(FunctionHelpers.MaxRepeat, returned.maxOccurring());
        assertEquals(1, sig.minArgumentCount());
        assertEquals(1 + FunctionHelpers.MaxRepeat, sig.maxArgumentCount());
    }

    @Test
    void anyIsObsoleteAndTakeAnyRepeats() {
        assertEquals("take_any", Aggregates.Any.alternative());
        assertEquals(3, Aggregates.Any.signatures().size());
        assertEquals(ArgumentKind.StarOnly, Aggregates.Any.signatures().get(2).parameters().get(0).argumentKind());
        var p = Aggregates.TakeAny.signatures().get(0).parameters().get(0);
        assertEquals(ArgumentKind.StarAllowed, p.argumentKind());
        assertEquals(1, p.minOccurring());
        assertEquals(Short.MAX_VALUE, p.maxOccurring());
    }

    @Test
    void deprecatedArgMinIsHiddenWithPrefix() {
        assertTrue(Aggregates.ArgMin_Deprecated.isHidden());
        assertEquals("arg_min", Aggregates.ArgMin_Deprecated.alternative());
        assertEquals("min", Aggregates.ArgMin_Deprecated.resultNamePrefix());
    }

    @Test
    void countDistinctOptimizedAlternative() {
        assertEquals("dcount", Aggregates.CountDistinct.optimizedAlternative());
        assertEquals("dcountif", Aggregates.CountDistinctIf.optimizedAlternative());
    }

    @Test
    void percentilesReturnNamesColumnsFromConstantPercentiles() {
        var sig = Aggregates.Percentiles.signatures().get(0);
        Expression value = star();
        Expression p50 = realLit("50");
        Expression p995 = realLit("99.5");
        var ctx = new FakeContext(sig, List.of(value, p50, p995), List.of(ScalarTypes.Long, ScalarTypes.Real, ScalarTypes.Real),
            List.of(sig.parameters().get(0), sig.parameters().get(1), sig.parameters().get(1)), null)
            .resultName(value, "x");
        var result = (TupleSymbol) sig.customReturnType().invoke(ctx);
        assertEquals(2, result.columns().size());
        assertEquals("percentile_x_50", result.columns().get(0).name());
        assertEquals("percentile_x_99_5", result.columns().get(1).name());
        assertSame(value, result.columns().get(0).source());
    }

    @Test
    void percentilesArrayReturn() {
        var sig = Aggregates.PercentilesArray.signatures().get(1);
        Expression value = star();
        var ctx = new FakeContext(sig, List.of(value, star()), List.of(ScalarTypes.Long, ScalarTypes.DynamicArray),
            List.of(sig.parameters().get(0), sig.parameters().get(1)), null)
            .resultName(value, "x");
        var result = (TupleSymbol) sig.customReturnType().invoke(ctx);
        assertEquals(1, result.columns().size());
        assertEquals("percentiles_x", result.columns().get(0).name());
        assertSame(ScalarTypes.DynamicArray, result.columns().get(0).type());
    }

    @Test
    void takeAnyStarReturnsRowScopeColumns() {
        var a = new ColumnSymbol("a", ScalarTypes.Long);
        var b = new ColumnSymbol("b", ScalarTypes.String);
        var row = new TableSymbol(a, b);
        var sig = Aggregates.TakeAny.signatures().get(0);
        var ctx = new FakeContext(sig, List.of(star()), List.of(ScalarTypes.Unknown), List.of(sig.parameters().get(0)), row);
        var result = (TupleSymbol) sig.customReturnType().invoke(ctx);
        assertEquals(List.of(a, b), new ArrayList<>(result.columns()));
        assertSame(a, result.columns().get(0));
    }

    @Test
    void getAnyResultWithNoArgumentsIsEmpty() {
        var sig = Aggregates.TakeAny.signatures().get(0);
        var ctx = new FakeContext(sig, List.of(), List.of(), List.of(), new TableSymbol());
        TypeSymbol result = Aggregates.getAnyResult(ctx, null);
        assertEquals(0, ((TupleSymbol) result).columns().size());
    }

    @Test
    void argMaxReturnWithStarArgument() {
        var sig = Aggregates.ArgMax.signatures().get(0);
        var ctx = new FakeContext(sig, List.of(star()), List.of(ScalarTypes.Unknown), List.of(sig.parameters().get(0)),
            new TableSymbol(new ColumnSymbol("a", ScalarTypes.Long), new ColumnSymbol("b", ScalarTypes.String)));
        TypeSymbol result = sig.customReturnType().invoke(ctx);
        assertNotNull(result);
        // Aggregates.ArgMax custom return: a `*` argument expands to the row-scope columns in a TupleSymbol
        var names = ((TupleSymbol) result).columns().stream().map(c -> c.name() + ":" + c.type().name()).toList();
        assertEquals(List.of("a:long", "b:string"), names);
    }

    // ---- oracle comparison ----

    @TestFactory
    Stream<DynamicTest> matchesOracleGlobalsDump() throws IOException {
        Assumptions.assumeTrue(Files.exists(CatalogTestSupport.GlobalsDump), "oracle dump absent: " + CatalogTestSupport.GlobalsDump);
        var records = CatalogTestSupport.readGlobals("aggregates");
        assertEquals(Aggregates.All.size(), records.size(), "record count");
        var tests = new ArrayList<DynamicTest>();
        for (int i = 0; i < records.size(); i++) {
            final int index = i;
            final var rec = records.get(i);
            final String ctx = i + " " + rec.path("name").asText();
            tests.add(DynamicTest.dynamicTest(ctx, () -> {
                assertEquals(index, rec.path("index").asInt(), ctx + " index");
                CatalogTestSupport.compareFunction(ctx, rec, Aggregates.All.get(index));
            }));
        }
        return tests.stream();
    }
}
