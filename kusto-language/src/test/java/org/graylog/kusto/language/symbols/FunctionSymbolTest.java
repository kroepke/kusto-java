// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for FunctionSymbol and PatternSymbol (Symbols/FunctionSymbol.cs, PatternSymbol.cs).
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FunctionSymbolTest {
    private static final Parameter A = new Parameter("a", ScalarTypes.Long);
    private static final Parameter B = new Parameter("b", ScalarTypes.Long, P.minOccurring(0));

    @Test
    void constructorDefaults() {
        var fn = new FunctionSymbol("f", ScalarTypes.Long, A);
        assertEquals(SymbolKind.Function, fn.kind());
        assertEquals("", fn.description());
        assertEquals(ResultNameKind.Default, fn.resultNameKind());
        assertNull(fn.resultNamePrefix());
        assertNull(fn.alternative());
        assertFalse(fn.isObsolete());
        assertFalse(fn.isHidden());
        assertFalse(fn.isConstantFoldable());
        assertFalse(fn.isView());
        assertNull(fn.customAvailability());
        assertEquals(Tabularity.Scalar, fn.tabularity());
        assertTrue(new FunctionSymbol("__internal", ScalarTypes.Long).isHidden()); // base IsHidden: "__" prefix
        assertThrows(IllegalArgumentException.class, () -> new FunctionSymbol("f", List.<Signature>of()));
    }

    @Test
    void withCopiesRepointSharedSignatures() {
        // FunctionSymbol.cs:105-108: every constructed copy re-points the shared Signature objects at itself
        var fn = new FunctionSymbol("f", ScalarTypes.Long, A);
        var sig = fn.signatures().get(0);
        assertSame(fn, sig.symbol());
        var hidden = fn.hide();
        assertNotSame(fn, hidden);
        assertTrue(hidden.isHidden());
        assertSame(sig, hidden.signatures().get(0));
        assertSame(hidden, sig.symbol());
        assertSame(hidden, hidden.withIsHidden(true));
        assertFalse(hidden.withIsHidden(false).isHidden());
    }

    @Test
    void withReturnsThisWhenUnchanged() {
        var fn = new FunctionSymbol("f", List.of(new Signature(ScalarTypes.Long, A)), "d");
        assertSame(fn, fn.withDescription("d"));
        assertSame(fn, fn.withResultNameKind(ResultNameKind.Default));
        assertSame(fn, fn.withIsObsolete(false));
        assertSame(fn, fn.withCustomAvailability(null));
        assertNotSame(fn, fn.withDescription("e"));
        assertTrue(fn.constantFoldable().isConstantFoldable());
        assertTrue(fn.withIsView(true).isView());
        assertEquals("p_", fn.withResultNamePrefix("p_").resultNamePrefix());
        assertEquals("g", fn.withOptimizedAlternative("g").optimizedAlternative());
        CustomAvailability ca = ctx -> true;
        assertSame(ca, fn.withCustomAvailability(ca).customAvailability());
    }

    @Test
    void obsolete() {
        var fn = new FunctionSymbol("f", ScalarTypes.Long, A);
        var obs = fn.obsolete("g");
        assertTrue(obs.isObsolete());
        assertEquals("g", obs.alternative());
        assertTrue(obs.isHidden());
        assertEquals("", fn.withIsObsolete(true).alternative()); // alternative ?? ""
        assertNull(obs.withIsObsolete(false).alternative());
    }

    @Test
    void argumentCountsAcrossSignatures() {
        var fn = new FunctionSymbol("f",
            new Signature(ScalarTypes.Long, A, B),
            new Signature(ScalarTypes.Long),
            new Signature(ScalarTypes.Long, A, A, A));
        assertEquals(0, fn.minArgumentCount());
        assertEquals(3, fn.maxArgumentCount());
    }

    @Test
    void constructorOverloads() {
        assertEquals(ReturnTypeKind.Parameter0, new FunctionSymbol("f", ReturnTypeKind.Parameter0, A).signatures().get(0).returnKind());
        assertEquals(ReturnTypeKind.Custom, new FunctionSymbol("f", (CustomReturnType) ctx -> ScalarTypes.Long, Tabularity.Scalar, List.of(A)).signatures().get(0).returnKind());
        var body = new FunctionSymbol("f", "T | take 1", Tabularity.Tabular, A);
        assertEquals("T | take 1", body.signatures().get(0).body());
        assertEquals(Tabularity.Tabular, body.tabularity());
        assertEquals("doc", new FunctionSymbol("f", "x", List.of(A), "doc").description());
        // parameter list text is parsed and bound into parameters
        var f = new FunctionSymbol("f", "(x: long)", "{ x }");
        var sig = f.signatures().get(0);
        assertEquals(1, sig.parameters().size());
        assertEquals("x", sig.parameters().get(0).name());
        assertSame(ScalarTypes.Long, sig.parameters().get(0).declaredTypes().get(0));
        assertEquals("{ x }", sig.body());
        var g = new FunctionSymbol("f", "(x: long)", "{ x }", Tabularity.Scalar);
        assertEquals("{ x }", g.signatures().get(0).body());
        assertEquals(Tabularity.Scalar, g.tabularity());
    }

    @Test
    void patternSignatureBuildsSignatureLazily() {
        var path = new Parameter("path", ScalarTypes.String);
        var ps = new PatternSignature(List.of("a"), "p", "T | take 1");
        var pat = new PatternSymbol("pat", List.of(A), path, List.of(ps));
        assertEquals(SymbolKind.Pattern, pat.kind());
        assertEquals(Tabularity.Tabular, pat.tabularity());
        assertSame(pat, ps.symbol());
        var sig = ps.signature();
        assertSame(sig, ps.signature());
        assertSame(pat, sig.symbol());
        assertEquals(List.of(A, path), sig.parameters());
        assertEquals("T | take 1", sig.body());

        var orphan = new PatternSignature(List.of(), null, "x");
        assertNull(orphan.signature()); // no Symbol yet

        var empty = new PatternSymbol("e");
        assertTrue(empty.parameters().isEmpty());
        assertTrue(empty.signatures().isEmpty());
        assertNull(empty.pathParameter());
    }
}
