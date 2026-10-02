// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for FunctionHelpers.cs (name and result-type helpers).
package org.graylog.kusto.language;

import static org.graylog.kusto.language.CatalogTestSupport.boolLit;
import static org.graylog.kusto.language.CatalogTestSupport.longLit;
import static org.graylog.kusto.language.CatalogTestSupport.realLit;
import static org.graylog.kusto.language.CatalogTestSupport.star;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.CatalogTestSupport.FakeContext;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.FunctionSymbol;
import org.graylog.kusto.language.symbols.Parameter;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.TupleSymbol;
import org.graylog.kusto.language.syntax.Expression;
import org.junit.jupiter.api.Test;

class FunctionHelpersTest {

    @Test
    void maxRepeatIsShortMaxValue() {
        assertEquals(32767, FunctionHelpers.MaxRepeat);
    }

    @Test
    void makeColumnName() {
        assertEquals("a", FunctionHelpers.makeColumnName("a"));
        assertNull(FunctionHelpers.makeColumnName((String) null)); // single part is returned as is, even null
        assertEquals("a_b", FunctionHelpers.makeColumnName("a", "b"));
        assertEquals("a_c", FunctionHelpers.makeColumnName("a", null, "c")); // nulls are skipped
        assertEquals("", FunctionHelpers.makeColumnName());
        assertEquals("_x", FunctionHelpers.makeColumnName("", "x")); // empty parts are kept
    }

    @Test
    void makeValidNameFragment() {
        var text = "abc_123"; // '_' is not a letter or digit
        assertEquals("abc_123", FunctionHelpers.makeValidNameFragment(text));
        var ok = "abc123";
        assertSame(ok, FunctionHelpers.makeValidNameFragment(ok)); // unchanged text is returned as is
        assertEquals("0_5", FunctionHelpers.makeValidNameFragment("0.5"));
        assertEquals("_1", FunctionHelpers.makeValidNameFragment("-1"));
        assertEquals("caf_", FunctionHelpers.makeValidNameFragment("café")); // TextFacts letters are ASCII only
        assertEquals("__", FunctionHelpers.makeValidNameFragment("😀")); // UTF-16 code units, not code points
        assertEquals("", FunctionHelpers.makeValidNameFragment(""));
    }

    @Test
    void getConstantValueUsesDotNetFormatting() {
        assertEquals("True", FunctionHelpers.getConstantValue(boolLit(true)));
        assertEquals("False", FunctionHelpers.getConstantValue(boolLit(false)));
        assertEquals("42", FunctionHelpers.getConstantValue(longLit("42")));
        assertEquals("0.5", FunctionHelpers.getConstantValue(realLit("0.5")));
        assertEquals("50", FunctionHelpers.getConstantValue(realLit("50")));
        assertEquals("", FunctionHelpers.getConstantValue(star())); // not constant
    }

    @Test
    void isBoolean() {
        assertTrue(FunctionHelpers.isBoolean(boolLit(false)));
        assertFalse(FunctionHelpers.isBoolean(longLit("1")));
        assertFalse(FunctionHelpers.isBoolean(star()));
    }

    @Test
    void getSummarizeByColumnsWithoutSummarizeIsEmpty() {
        assertEquals(0, FunctionHelpers.getSummarizeByColumns(List.of()).size());
        assertEquals(0, FunctionHelpers.getSummarizeByColumns(List.of(star())).size());
    }

    @Test
    void addReferencedColumnsSkipsUnboundArguments() {
        var sig = PlugIns.SessionCount.signatures().get(0);
        var dims = sig.getParameter("Dimension");
        var args = List.<Expression>of(star(), star());
        var ctx = new FakeContext(sig, args, List.of(ScalarTypes.Unknown, ScalarTypes.Unknown), List.of(dims, dims), null);
        var columns = new ArrayList<ColumnSymbol>();
        FunctionHelpers.addReferencedColumns(columns, ctx, "Dimension");
        FunctionHelpers.addReferencedColumn(columns, ctx, "Dimension");
        FunctionHelpers.addReferencedColumn(columns, ctx, "NoSuchParameter");
        assertEquals(0, columns.size());
    }

    @Test
    void makePrefixedTupleUsesResultNamePrefixThenFunctionName() {
        var a = new ColumnSymbol("a", ScalarTypes.Long);
        var b = new ColumnSymbol("b", ScalarTypes.Real);
        var baseTuple = new TupleSymbol(a, b);

        var sumSig = Aggregates.Sum.signatures().get(0); // ResultNamePrefix "sum"
        var arg = star();
        var ctx = new FakeContext(sumSig, List.of(arg), List.of(ScalarTypes.Long), List.of(sumSig.parameters().get(0)), null).resultName(arg, "x");
        var tuple = FunctionHelpers.makePrefixedTuple(ctx, "expr", baseTuple);
        assertEquals("sum_x_a", tuple.columns().get(0).name());
        assertEquals("sum_x_b", tuple.columns().get(1).name());
        assertEquals(List.of(a), new ArrayList<>(tuple.columns().get(0).originalColumns()));
        assertSame(ScalarTypes.Real, tuple.columns().get(1).type());

        var fn = new FunctionSymbol("myfn", ScalarTypes.Long, new Parameter("p", ScalarTypes.Long)); // no prefix
        var fnSig = fn.signatures().get(0);
        var fnCtx = new FakeContext(fnSig, List.of(arg), List.of(ScalarTypes.Long), List.of(fnSig.parameters().get(0)), null); // result name "" (default)
        assertEquals("myfn__a", FunctionHelpers.makePrefixedTuple(fnCtx, "p", baseTuple).columns().get(0).name());
    }
}
