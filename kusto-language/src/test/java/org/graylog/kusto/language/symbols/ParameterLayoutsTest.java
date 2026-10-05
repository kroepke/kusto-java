// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for ParameterLayouts (Symbols/ParameterLayouts.cs) and the ParameterMatchKind order.
package org.graylog.kusto.language.symbols;

import static org.graylog.kusto.language.symbols.W1bTestSupport.expr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.binding.ParameterMatchKind;
import org.graylog.kusto.language.syntax.Expression;
import org.junit.jupiter.api.Test;

class ParameterLayoutsTest {
    private static final Parameter A = new Parameter("a", ScalarTypes.Long);
    private static final Parameter B = new Parameter("b", ScalarTypes.Long);

    @Test
    void knownLayouts() {
        assertInstanceOf(NonRepeatingParameterLayout.class, ParameterLayouts.Fixed);
        assertInstanceOf(RepeatingParameterLayout.class, ParameterLayouts.Repeating);
        assertInstanceOf(RepeatingParameterLayout.class, ParameterLayouts.RepeatingSkipping);
        assertNotSame(ParameterLayouts.Repeating, ParameterLayouts.RepeatingSkipping);
        assertInstanceOf(BlockRepeatingParameterLayout.class, ParameterLayouts.BlockRepeating);
        ParameterLayoutBuilder builder = (s, args, ps) -> { };
        var custom = ParameterLayouts.custom(builder);
        assertInstanceOf(CustomParameterLayout.class, custom);
        assertNotSame(custom, ParameterLayouts.custom(builder));
    }

    @Test
    void customLayoutReplacesNullsAndFillsMissing() {
        // CustomParameterLayout.GetArgumentParameters (ParameterLayouts.cs:477-493)
        var sig = new Signature(ScalarTypes.Long, A, B).withLayout((s, args, ps) -> {
            ps.add(B);
            ps.add(null);
        });
        List<Expression> args = List.of(expr(), expr(), expr(), expr());
        assertEquals(List.of(B, Signature.UnknownParameter, Signature.UnknownParameter, Signature.UnknownParameter), sig.getArgumentParameters(args));
    }

    @Test
    void customLayoutBuilderSeesSignatureAndArguments() {
        var seen = new ArrayList<Object>();
        var sig = new Signature(ScalarTypes.Long, A).withLayout((s, args, ps) -> {
            seen.add(s);
            seen.add(args.size());
        });
        sig.getArgumentParameters(List.of(expr()));
        assertSame(sig, seen.get(0));
        assertEquals(1, seen.get(1));
    }

    @Test
    void baseTypeLayoutBuildsFakeExpressions() {
        // ParameterLayout.GetArgumentParameters(signature, argumentTypes, …) makes FakeExpressions (via the Binder)
        var sig = new Signature(ScalarTypes.Long, A).withLayout((s, args, ps) -> { });
        var out = new ArrayList<Parameter>();
        sig.getArgumentParametersForTypes(List.of(ScalarTypes.Long), out);
        assertEquals(1, out.size());
    }

    @Test
    void baseIsValidArgumentCount() {
        var sig = new Signature(ScalarTypes.Long, A, new Parameter("r", ScalarTypes.Long, P.minOccurring(0), P.maxOccurring(2)));
        var layout = ParameterLayouts.Fixed;
        assertTrue(layout.isValidArgumentCount(sig, 1));
        assertTrue(layout.isValidArgumentCount(sig, 3));
        assertFalse(layout.isValidArgumentCount(sig, 0));
        assertFalse(layout.isValidArgumentCount(sig, 4));
    }

    @Test
    void blockRepeatingNextPossibleParameters() {
        var x = new Parameter("x", ScalarTypes.Long);
        var k = new Parameter("k", ScalarTypes.String, P.minOccurring(1), P.maxOccurring(2));
        var v = new Parameter("v", ScalarTypes.Real, P.minOccurring(1), P.maxOccurring(2));
        var z = new Parameter("z", ScalarTypes.Bool);
        var sig = new Signature(ScalarTypes.Long, x, k, v, z).withLayout(ParameterLayouts.BlockRepeating);

        assertEquals(List.of(x), next(sig, 0));
        assertEquals(List.of(k), next(sig, 1));       // first repeat group, minimum not yet satisfied
        assertEquals(List.of(v), next(sig, 2));
        assertEquals(List.of(k, z), next(sig, 3));    // second group may start, or the fixed tail
        assertEquals(List.of(v), next(sig, 4));       // iEndParam = last + offset + 1 is past the end
        assertEquals(List.of(z), next(sig, 5));       // maximum groups reached

        var plain = new Signature(ScalarTypes.Long, x, z).withLayout(ParameterLayouts.BlockRepeating);
        assertEquals(List.of(z), next(plain, 1));
        assertEquals(List.of(), next(plain, 2));
    }

    private static List<Parameter> next(Signature sig, int nArgs) {
        var args = new ArrayList<Expression>();
        for (int i = 0; i < nArgs; i++)
            args.add(expr());
        var possible = new ArrayList<Parameter>();
        sig.getNextPossibleParameters(args, possible);
        return possible;
    }

    @Test
    void parameterMatchKindDeclarationOrder() {
        // RepeatingParameterLayout compares with >= (ParameterLayouts.cs:185): declaration order is the ranking
        var expected = List.of("None", "Unknown", "Dynamic", "Compatible", "Promoted", "NotType", "OneOfMany", "Scalar",
            "Summable", "Orderable", "Number", "Integer", "Tabular", "Table", "Database", "Cluster", "Exact");
        var actual = new ArrayList<String>();
        for (var k : ParameterMatchKind.values())
            actual.add(k.name());
        assertEquals(expected, actual);
        assertTrue(ParameterMatchKind.Exact.compareTo(ParameterMatchKind.Promoted) >= 0);
        assertTrue(ParameterMatchKind.None.compareTo(ParameterMatchKind.Unknown) < 0);
    }
}
