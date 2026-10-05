// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for Signature (Symbols/Signature.cs): argument counts, layouts, tabularity, With* copies.
package org.graylog.kusto.language.symbols;

import static org.graylog.kusto.language.symbols.SymbolTestSupport.expr;
import static org.graylog.kusto.language.symbols.SymbolTestSupport.named;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graylog.kusto.language.Functions;
import org.graylog.kusto.language.syntax.Expression;
import org.junit.jupiter.api.Test;

class SignatureTest {
    private static final Parameter A = new Parameter("a", ScalarTypes.Long);
    private static final Parameter B = new Parameter("b", ScalarTypes.Long, P.minOccurring(0));
    private static final Parameter C = new Parameter("c", ScalarTypes.Long, P.minOccurring(1), P.maxOccurring(5));

    private static List<Parameter> layout(Signature sig, int nArgs) {
        var types = new ArrayList<TypeSymbol>(Collections.nCopies(nArgs, ScalarTypes.Long));
        var result = new ArrayList<Parameter>();
        sig.getArgumentParametersForTypes(types, result);
        return result;
    }

    @Test
    void argumentCountsAndFlags() {
        // Signature.cs:133-158
        var sig = new Signature(ScalarTypes.Long, A, B, C);
        assertEquals(2, sig.minArgumentCount());
        assertEquals(7, sig.maxArgumentCount());
        assertTrue(sig.hasRepeatableParameters());
        assertTrue(sig.hasOptionalParameters());
        assertFalse(sig.hasAggregateParameters());
        assertSame(ParameterLayouts.Repeating, sig.layout());

        var fixed = new Signature(ScalarTypes.Long, List.of(A, B));
        assertEquals(1, fixed.minArgumentCount());
        assertEquals(2, fixed.maxArgumentCount());
        assertFalse(fixed.hasRepeatableParameters());
        assertSame(ParameterLayouts.Fixed, fixed.layout());
        assertTrue(fixed.isValidArgumentCount(1));
        assertTrue(fixed.isValidArgumentCount(2));
        assertFalse(fixed.isValidArgumentCount(0));
        assertFalse(fixed.isValidArgumentCount(3));

        var agg = new Signature(ReturnTypeKind.Common, new Parameter("x", ParameterTypeKind.CommonScalar, ArgumentKind.Aggregate));
        assertTrue(agg.hasAggregateParameters());

        var none = new Signature(ScalarTypes.Long);
        assertEquals(0, none.minArgumentCount());
        assertEquals(0, none.maxArgumentCount());
        assertTrue(none.parameters().isEmpty());
        assertTrue(new Signature(ScalarTypes.Long, (Parameter[]) null).parameters().isEmpty());
    }

    @Test
    void constructorValidation() {
        assertThrows(NullPointerException.class, () -> new Signature((TypeSymbol) null, A));
        assertThrows(NullPointerException.class, () -> new Signature((TypeSymbol) null, List.of(A)));
        assertThrows(NullPointerException.class, () -> new Signature((String) null, Tabularity.Scalar));
        assertThrows(NullPointerException.class, () -> new Signature((CustomReturnType) null, Tabularity.Scalar));
        assertThrows(NullPointerException.class, () -> new Signature(ReturnTypeKind.Declared, A));
        assertThrows(IllegalArgumentException.class, () -> new Signature(ScalarTypes.Long, A, null));
    }

    @Test
    void parameterLookupLastWins() {
        // Signature.cs:317 map[p.Name] = p
        var a2 = new Parameter("a", ScalarTypes.Real);
        var sig = new Signature(ScalarTypes.Long, A, a2);
        assertSame(a2, sig.getParameter("a"));
        assertNull(sig.getParameter("zz"));
    }

    @Test
    void unknownParameter() {
        assertEquals("", Signature.UnknownParameter.name());
        assertSame(ScalarTypes.Unknown, Signature.UnknownParameter.declaredTypes().get(0));
    }

    @Test
    void tabularity() {
        var table = new TableSymbol(new ColumnSymbol("x", ScalarTypes.Long));
        assertEquals(Tabularity.Scalar, new Signature(ScalarTypes.Long).tabularity());
        assertEquals(Tabularity.Tabular, new Signature(table).tabularity());
        assertEquals(Tabularity.Unknown, new Signature("T", Tabularity.Unspecified).tabularity());
        assertEquals(Tabularity.Tabular, new Signature("T", Tabularity.Tabular).tabularity());
        assertEquals(Tabularity.Tabular, new Signature(ReturnTypeKind.Parameter0, new Parameter("t", table)).tabularity());
        assertEquals(Tabularity.Unknown, new Signature(ReturnTypeKind.Parameter1, A).tabularity());
        assertEquals(Tabularity.Tabular, new Signature(ReturnTypeKind.ParameterN, A, new Parameter("t", ParameterTypeKind.Tabular)).tabularity());
        assertEquals(Tabularity.Other, new Signature(ReturnTypeKind.Parameter0Graph, A).tabularity());
        assertEquals(Tabularity.Tabular, new Signature(ReturnTypeKind.Parameter0Database, A).tabularity());
        assertEquals(Tabularity.Scalar, new Signature(ReturnTypeKind.Common, A).tabularity());
        assertEquals(Tabularity.Unknown, new Signature((CustomReturnType) ctx -> ScalarTypes.Long, Tabularity.Unspecified).tabularity());

        var unknown = new Signature("T", Tabularity.Unspecified);
        assertTrue(unknown.isScalar());
        assertTrue(unknown.isTabular());
        assertFalse(new Signature(table).isScalar());
        assertFalse(new Signature(ScalarTypes.Long).isTabular());
    }

    @Test
    void bodyAndDeclaredReturnType() {
        var sig = new Signature("{ T | take 1 }", Tabularity.Tabular);
        assertEquals(ReturnTypeKind.Computed, sig.returnKind());
        assertEquals("{ T | take 1 }", sig.body());
        assertNull(sig.declaredReturnType());
        assertNull(sig.declaration());
        assertSame(ScalarTypes.Long, new Signature(ScalarTypes.Long).declaredReturnType());
    }

    @Test
    void withCopies() {
        var sig = new Signature(ScalarTypes.Long, A, B);
        var hidden = sig.hide();
        assertNotSame(sig, hidden);
        assertTrue(hidden.isHidden());
        assertFalse(sig.isHidden());
        assertSame(sig.parameters(), hidden.parameters());
        assertSame(sig.layout(), hidden.layout());

        var obsolete = sig.obsolete("other");
        assertTrue(obsolete.isObsolete());
        assertEquals("other", obsolete.alternative());
        assertFalse(sig.isObsolete());

        var custom = sig.withLayout(ParameterLayouts.BlockRepeating);
        assertSame(ParameterLayouts.BlockRepeating, custom.layout());
        assertInstanceOf(CustomParameterLayout.class, sig.withLayout((s, args, ps) -> { }).layout());
    }

    @Test
    void fixedLayout() {
        var sig = new Signature(ScalarTypes.Long, A, B);
        assertEquals(List.of(A), layout(sig, 1));
        assertEquals(List.of(A, B), layout(sig, 2));
        assertEquals(List.of(A, B, Signature.UnknownParameter), layout(sig, 3));
    }

    @Test
    void fixedLayoutNamedArguments() {
        // NonRepeatingParameterLayout.LayoutParameters: AllowsNamedArguments is true when the symbol is not a FunctionSymbol
        var sig = new Signature(ScalarTypes.Long, A, B);
        List<Expression> args = List.of(named("b"), expr(), named("zz"));
        assertEquals(List.of(B, B, Signature.UnknownParameter), sig.getArgumentParameters(args));
    }

    @Test
    void namedArgumentsAreAllowedOnlyForUserFunctions() {
        // Signature.cs: AllowsNamedArguments => !(Symbol is FunctionSymbol fn && GlobalState.Default.IsBuiltInFunction(fn))
        var fn = new FunctionSymbol("f", ScalarTypes.Long, A, B);
        var sig = fn.signatures().get(0);
        assertSame(fn, sig.symbol());
        assertTrue(sig.allowsNamedArguments());
        var builtIn = Functions.Strlen.signatures().get(0);
        assertFalse(builtIn.allowsNamedArguments());
        assertEquals(2, layout(sig, 2).size());
    }

    @Test
    void repeatingLayoutWithoutBinderDecisions() {
        // each argument is decided by MinOccurring/MaxOccurring alone, so Binder.GetParameterMatchKind is not consulted
        var rep = new Parameter("r", ScalarTypes.Long, P.minOccurring(1), P.maxOccurring(3));
        var sig = new Signature(ScalarTypes.Long, A, rep);
        assertSame(ParameterLayouts.Repeating, sig.layout());
        assertEquals(List.of(A, rep, rep, rep), layout(sig, 4));
        assertEquals(List.of(A, rep, rep, rep, Signature.UnknownParameter), layout(sig, 5)); // iArg >= MaxArgumentCount

        // optional parameters cannot be skipped by Repeating: count == 0 && MinOccurring == 0 takes it
        var opt = new Parameter("o", ScalarTypes.Long, P.minOccurring(0), P.maxOccurring(2));
        var sig2 = new Signature(ScalarTypes.Long, List.of(A, opt));
        assertEquals(List.of(A, opt, opt), layout(sig2, 3));
    }

    @Test
    void repeatingLayoutAmbiguity() {
        var rep = new Parameter("r", ScalarTypes.Long, P.minOccurring(1), P.maxOccurring(3));
        var sig = new Signature(ScalarTypes.Long, rep, A);
        // both parameters match a long argument equally; Binder.GetParameterMatchKind ties go to the current (repeating) parameter
        assertEquals(List.of(rep, rep), layout(sig, 2));
    }

    @Test
    void repeatingSkippingLayout() {
        var opt = new Parameter("o", ScalarTypes.Long, P.minOccurring(0), P.maxOccurring(3));
        var sig = new Signature(ScalarTypes.Long, opt).withLayout(ParameterLayouts.RepeatingSkipping);
        assertEquals(List.of(opt, opt), layout(sig, 2));
    }

    @Test
    void blockRepeatingLayout() {
        var x = new Parameter("x", ScalarTypes.Long);
        var k = new Parameter("k", ScalarTypes.String, P.minOccurring(1), P.maxOccurring(3));
        var v = new Parameter("v", ScalarTypes.Real, P.minOccurring(1), P.maxOccurring(3));
        var z = new Parameter("z", ScalarTypes.Bool);
        var sig = new Signature(ScalarTypes.Long, x, k, v, z).withLayout(ParameterLayouts.BlockRepeating);
        assertEquals(4, sig.minArgumentCount());
        assertEquals(8, sig.maxArgumentCount());
        assertEquals(List.of(x, k, v, k, v, z), layout(sig, 6));
        // 5 arguments: two repeat groups are expected, so the last argument lands on the second v (upstream arithmetic)
        assertEquals(List.of(x, k, v, k, v), layout(sig, 5));
        assertEquals(List.of(x, k, v, z), layout(sig, 4));
        assertEquals(List.of(x, k, v, k, v, k, v, z, Signature.UnknownParameter), layout(sig, 9));

        assertTrue(sig.isValidArgumentCount(4));
        assertFalse(sig.isValidArgumentCount(5));
        assertTrue(sig.isValidArgumentCount(6));
        assertTrue(sig.isValidArgumentCount(8));
        assertFalse(sig.isValidArgumentCount(10));

        // no repeatable parameters: falls back to the non-repeating layout
        var plain = new Signature(ScalarTypes.Long, x, z).withLayout(ParameterLayouts.BlockRepeating);
        assertEquals(List.of(x, z, Signature.UnknownParameter), layout(plain, 3));
    }

    @Test
    void nextPossibleParameters() {
        var sig = new Signature(ScalarTypes.Long, A, B);
        var possible = new ArrayList<Parameter>();
        sig.getNextPossibleParameters(List.of(expr()), possible);
        assertEquals(List.of(B), possible);

        possible.clear();
        sig.getNextPossibleParameters(List.of(expr(), expr()), possible);
        assertEquals(List.of(), possible);

        var rep = new Parameter("r", ScalarTypes.Long, P.minOccurring(1), P.maxOccurring(3));
        possible.clear();
        new Signature(ScalarTypes.Long, A, rep).getNextPossibleParameters(List.of(), possible);
        assertEquals(List.of(A), possible);
    }

    @Test
    void argumentNullChecks() {
        var sig = new Signature(ScalarTypes.Long, A);
        assertThrows(NullPointerException.class, () -> sig.getArgumentParameters(null));
        assertThrows(NullPointerException.class, () -> sig.getArgumentParameters(List.of(), null));
        assertThrows(NullPointerException.class, () -> sig.getArgumentParametersForTypes(null, new ArrayList<>()));
        assertThrows(NullPointerException.class, () -> sig.getNextPossibleParameters(null, new ArrayList<>()));
        assertThrows(NullPointerException.class, () -> sig.getReturnType(null));
        assertThrows(NullPointerException.class, () -> sig.getReturnType(null, List.of()));
        assertThrows(NullPointerException.class, () -> sig.getReturnType(null, List.of(), List.of()));
    }

    @Test
    void debugText() {
        var sig = new Signature(ScalarTypes.Long, A, B, C);
        assertEquals("(a, [b], c, ...)", DebugDisplay.getText(sig));
        assertEquals("(a: long, [b: long], c: long, ...)", DebugDisplay.getText(sig, false, true));
        var fn = new FunctionSymbol("f", ScalarTypes.Long, A);
        assertEquals("f(a)", DebugDisplay.getText(fn.signatures().get(0), true));
        assertEquals("a: long", DebugDisplay.getText(A));
        assertEquals("t: <Tabular>", DebugDisplay.getText(new Parameter("t", ParameterTypeKind.Tabular)));
    }
}
