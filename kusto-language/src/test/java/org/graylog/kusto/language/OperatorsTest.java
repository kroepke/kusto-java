// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for the Operators catalog (Operators.cs) and its oracle comparison.
package org.graylog.kusto.language;

import static org.graylog.kusto.language.CatalogTestSupport.longLit;
import static org.graylog.kusto.language.CatalogTestSupport.star;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import org.graylog.kusto.language.symbols.OperatorKind;
import org.graylog.kusto.language.symbols.OperatorSymbol;
import org.graylog.kusto.language.symbols.ParameterTypeKind;
import org.graylog.kusto.language.symbols.ReturnTypeKind;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class OperatorsTest {
    // grep -c "public static readonly OperatorSymbol" upstream/kusto-query-language/src/Kusto.Language/Operators.cs -> 55
    //   (= 23 direct `new OperatorSymbol(` + 32 `StringBinary(...)` fields; grep -c "new OperatorSymbol(" -> 25 counts the 2 inside StringBinary)
    private static final int UpstreamOperatorSymbolCount = 55;

    @Test
    void classInitSucceedsWithNoNullStaticFields() throws Exception {
        CatalogTestSupport.assertNoNullStaticFields(Operators.class);
        assertEquals(UpstreamOperatorSymbolCount, CatalogTestSupport.publicStaticFieldsOfType(Operators.class, OperatorSymbol.class).size());
    }

    @Test
    void allHoldsEveryFieldOnceInUpstreamOrder() throws Exception {
        assertEquals(UpstreamOperatorSymbolCount, Operators.All.size());
        var seen = Collections.newSetFromMap(new IdentityHashMap<OperatorSymbol, Boolean>());
        seen.addAll(Operators.All);
        assertEquals(UpstreamOperatorSymbolCount, seen.size());
        for (var f : CatalogTestSupport.publicStaticFieldsOfType(Operators.class, OperatorSymbol.class))
            assertTrue(seen.contains(f));
        assertSame(Operators.UnaryMinus, Operators.All.get(0));
        assertSame(Operators.In, Operators.All.get(47));
        assertSame(Operators.InCs, Operators.All.get(48));
        assertSame(Operators.HasAll, Operators.All.get(54));
    }

    @Test
    void equal() {
        var eq = Operators.Equal;
        assertEquals(OperatorKind.Equal, eq.operatorKind());
        assertEquals(3, eq.signatures().size());
        assertTrue(eq.signatures().get(0).isHidden());
        assertEquals(ScalarTypes.Bool, eq.signatures().get(0).parameters().get(0).declaredTypes().get(0));
        assertEquals(ParameterTypeKind.NotBool, eq.signatures().get(1).parameters().get(0).typeKind());
        assertEquals(ArgumentKind.StarOnly, eq.signatures().get(2).parameters().get(0).argumentKind());
        assertEquals(ParameterTypeKind.Scalar, eq.signatures().get(2).parameters().get(1).typeKind());
        assertEquals(2, Operators.NotEqual.signatures().size());
    }

    @Test
    void containsHasDynamicRightHandSideButHasDoesNot() {
        var contains = Operators.Contains;
        assertEquals(OperatorKind.Contains, contains.operatorKind());
        assertEquals(2, contains.signatures().size());
        assertEquals(ParameterTypeKind.StringOrDynamic, contains.signatures().get(0).parameters().get(1).typeKind());
        assertEquals(ArgumentKind.StarOnly, contains.signatures().get(1).parameters().get(0).argumentKind());

        var has = Operators.Has;
        assertEquals(OperatorKind.Has, has.operatorKind());
        assertEquals(ParameterTypeKind.Declared, has.signatures().get(0).parameters().get(1).typeKind());
        assertEquals(List.of(ScalarTypes.String), has.signatures().get(0).parameters().get(1).declaredTypes());
        // each StringBinary call builds a fresh symbol
        assertFalse(Operators.Has == Operators.HasCs);
    }

    @Test
    void inHasTabularAndRepeatingOverloads() {
        var in = Operators.In;
        assertEquals(OperatorKind.In, in.operatorKind());
        assertEquals(4, in.signatures().size());
        assertTrue(in.signatures().get(0).isHidden());
        assertFalse(in.signatures().get(1).isHidden());
        assertTrue(in.signatures().get(2).isHidden());
        assertEquals(ParameterTypeKind.Tabular, in.signatures().get(1).parameters().get(1).typeKind());
        var values = in.signatures().get(3).parameters().get(1);
        assertEquals("value", values.name());
        assertEquals(ParameterTypeKind.Scalar, values.typeKind());
        assertEquals(1, values.minOccurring());
        assertEquals(Short.MAX_VALUE, values.maxOccurring());
        assertEquals(2, Operators.InCs.signatures().size());
    }

    @Test
    void addOverloads() {
        var add = Operators.Add;
        assertEquals(8, add.signatures().size());
        assertEquals(ReturnTypeKind.Widest, add.signatures().get(0).returnKind());
        assertEquals(ReturnTypeKind.Parameter1, add.signatures().get(6).returnKind());
        assertEquals(6, add.signatures().get(6).parameters().get(1).declaredTypes().size()); // DynamicAddable
        assertEquals(2, add.signatures().get(2).parameters().get(0).declaredTypes().size()); // DateAndTimespan
    }

    @Test
    void boundLiteralIsConstant() {
        var code = KustoCode.parseAndAnalyze("print 1");
        var lit = code.syntax().getFirstDescendant(org.graylog.kusto.language.syntax.LiteralExpression.class);
        assertTrue(lit.isConstant());
    }

    @Test
    void unaryMinusPromotesNonConstantInt() {
        var sig = Operators.UnaryMinus.signatures().get(0);
        assertEquals(ReturnTypeKind.Custom, sig.returnKind());
        // IsConstant comes from semantic info that the binder sets; a literal built by hand was never bound, so it is not constant
        var unboundLiteral = longLit("1");
        var literalCtx = new FakeContext(sig, List.of(unboundLiteral), List.of(ScalarTypes.Int), List.of(sig.parameters().get(0)), null);
        assertSame(ScalarTypes.Long, sig.customReturnType().invoke(literalCtx));
        var nonConstant = star();
        var nonConstantCtx = new FakeContext(sig, List.of(nonConstant), List.of(ScalarTypes.Int), List.of(sig.parameters().get(0)), null);
        assertSame(ScalarTypes.Long, sig.customReturnType().invoke(nonConstantCtx));
        var realCtx = new FakeContext(sig, List.of(nonConstant), List.of(ScalarTypes.Real), List.of(sig.parameters().get(0)), null);
        assertSame(ScalarTypes.Real, sig.customReturnType().invoke(realCtx));
    }

    @Test
    void betweenUsesParameterRelativeTypes() {
        var sig = Operators.Between.signatures().get(1);
        assertEquals(ParameterTypeKind.Parameter0, sig.parameters().get(1).typeKind());
        assertEquals(ParameterTypeKind.Parameter1, Operators.Between.signatures().get(3).parameters().get(2).typeKind());
    }

    // ---- oracle comparison ----

    @TestFactory
    Stream<DynamicTest> matchesOracleGlobalsDump() throws IOException {
        Assumptions.assumeTrue(Files.exists(CatalogTestSupport.GlobalsDump), "oracle dump absent: " + CatalogTestSupport.GlobalsDump);
        var records = CatalogTestSupport.readGlobals("operators");
        assertEquals(Operators.All.size(), records.size(), "record count");
        var tests = new ArrayList<DynamicTest>();
        for (int i = 0; i < records.size(); i++) {
            final int index = i;
            final var rec = records.get(i);
            final String ctx = i + " " + rec.path("name").asText();
            tests.add(DynamicTest.dynamicTest(ctx, () -> {
                assertEquals(index, rec.path("index").asInt(), ctx + " index");
                CatalogTestSupport.compareOperator(ctx, rec, Operators.All.get(index));
            }));
        }
        return tests.stream();
    }
}
