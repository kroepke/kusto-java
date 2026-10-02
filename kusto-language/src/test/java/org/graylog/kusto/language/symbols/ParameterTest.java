// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for Parameter (Symbols/Parameter.cs) and the P named-argument builder.
package org.graylog.kusto.language.symbols;

import static org.graylog.kusto.language.symbols.W1bTestSupport.assertPending;
import static org.graylog.kusto.language.symbols.W1bTestSupport.assumeKustoFacts;
import static org.graylog.kusto.language.symbols.W1bTestSupport.expr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.ListExtensions;
import org.junit.jupiter.api.Test;

class ParameterTest {
    @Test
    void typeConstructorDefaults() {
        // Parameter.cs:213 / :157 defaults
        var p = new Parameter("x", ScalarTypes.Long);
        assertEquals("x", p.name());
        assertEquals(ParameterTypeKind.Declared, p.typeKind());
        assertEquals(List.of(ScalarTypes.Long), p.declaredTypes());
        assertEquals(ArgumentKind.Expression, p.argumentKind());
        assertEquals(1, p.minOccurring());
        assertEquals(1, p.maxOccurring());
        assertFalse(p.isOptional());
        assertFalse(p.isRepeatable());
        assertSame(EmptyReadOnlyList.Instance, p.values());
        assertSame(EmptyReadOnlyList.Instance, p.examples());
        assertFalse(p.isCaseSensitive());
        assertNull(p.defaultValueIndicator());
        assertNull(p.defaultValue());
        assertEquals("", p.description());
        assertEquals(Tabularity.Scalar, p.tabularity());
        assertTrue(p.isScalar());
        assertFalse(p.typeDependsOnArguments());
    }

    @Test
    void nullTypeBecomesUnknown() {
        // `type ?? ScalarTypes.Unknown` (Parameter.cs:172, :214)
        assertSame(ScalarTypes.Unknown, new Parameter("x", (TypeSymbol) null).declaredTypes().get(0));
        assertSame(ScalarTypes.Unknown, new Parameter("x", (TypeSymbol) null, ArgumentKind.Constant).declaredTypes().get(0));
    }

    @Test
    void typeKindConstructorHasNoDeclaredTypes() {
        var p = new Parameter("x", ParameterTypeKind.Tabular, ArgumentKind.Expression);
        assertSame(EmptyReadOnlyList.Instance, p.declaredTypes());
        assertEquals(Tabularity.Tabular, p.tabularity());
        assertTrue(p.isTabular());
        assertTrue(p.typeDependsOnArguments());
        assertEquals(Tabularity.Tabular, new Parameter("d", ParameterTypeKind.Database).tabularity());
        assertEquals(Tabularity.Tabular, new Parameter("c", ParameterTypeKind.Cluster).tabularity());
        assertEquals(Tabularity.Scalar, new Parameter("s", ParameterTypeKind.Scalar).tabularity());
    }

    @Test
    void typesArrayConstructorChecksItsArgument() {
        var p = new Parameter("x", new TypeSymbol[] { ScalarTypes.Long, ScalarTypes.Real });
        assertEquals(List.of(ScalarTypes.Long, ScalarTypes.Real), p.declaredTypes());
        // CheckArgumentNullOrEmptyOrElementNull(nameof(types))
        assertThrows(NullPointerException.class, () -> new Parameter("x", (TypeSymbol[]) null));
        assertThrows(IllegalArgumentException.class, () -> new Parameter("x", new TypeSymbol[0]));
        assertThrows(IllegalArgumentException.class, () -> new Parameter("x", new TypeSymbol[] { ScalarTypes.Long, null }));
    }

    @Test
    void declaredTableTypeIsTabular() {
        var p = new Parameter("T", TableSymbol.Empty);
        assertEquals(Tabularity.Tabular, p.tabularity());
        assertTrue(p.typeDependsOnArguments());
    }

    @Test
    void fullPositionalConstructor() {
        var values = List.<Object>of("a", "b");
        var examples = List.of("e1");
        var p = new Parameter("x", ScalarTypes.String, ArgumentKind.Literal, values, examples, true, "default", 0, 3, null, "desc");
        assertEquals(ArgumentKind.Literal, p.argumentKind());
        assertEquals(values, p.values());
        assertEquals(examples, p.examples());
        assertTrue(p.isCaseSensitive());
        assertEquals("default", p.defaultValueIndicator());
        assertEquals(0, p.minOccurring());
        assertEquals(3, p.maxOccurring());
        assertTrue(p.isOptional());
        assertTrue(p.isRepeatable());
        assertEquals("desc", p.description());
    }

    @Test
    void telescopedOverloadsUseUpstreamDefaults() {
        var p = new Parameter("x", ParameterTypeKind.Scalar, ArgumentKind.Constant, null, null, true);
        assertEquals(ArgumentKind.Constant, p.argumentKind());
        assertTrue(p.isCaseSensitive());
        assertEquals(1, p.minOccurring());
        assertEquals(1, p.maxOccurring());
        assertEquals("", p.description());
    }

    @Test
    void defaultValueForcesOptionalSingle() {
        // Parameter.cs:122-123: MinOccurring/MaxOccurring forced to 0/1 when a default value is present
        var dv = expr();
        var p = new Parameter("x", ScalarTypes.Long, ArgumentKind.Expression, null, null, false, null, 2, 5, dv, null);
        assertSame(dv, p.defaultValue());
        assertEquals(0, p.minOccurring());
        assertEquals(1, p.maxOccurring());
        var q = new Parameter("x", ScalarTypes.Long, P.maxOccurring(5), P.defaultValue(dv), P.minOccurring(3));
        assertEquals(0, q.minOccurring());
        assertEquals(1, q.maxOccurring());
    }

    @Test
    void namedArgumentBuilder() {
        // new Parameter("name", ScalarTypes.String, minOccurring: 0)
        var p = new Parameter("name", ScalarTypes.String, P.minOccurring(0));
        assertEquals(0, p.minOccurring());
        assertEquals(1, p.maxOccurring());
        assertEquals(ArgumentKind.Expression, p.argumentKind());

        // new Parameter("occurrence", ParameterTypeKind.Integer, ArgumentKind.Constant, minOccurring: 0)
        var q = new Parameter("occurrence", ParameterTypeKind.Integer, ArgumentKind.Constant, P.minOccurring(0));
        assertEquals(ArgumentKind.Constant, q.argumentKind());
        assertEquals(0, q.minOccurring());

        // new Parameter("kind", ScalarTypes.String, ArgumentKind.Literal, new object[] { "normal", "regex" }, isCaseSensitive: true, minOccurring: 0)
        var r = new Parameter("kind", ScalarTypes.String, ArgumentKind.Literal, List.of("normal", "regex"), P.isCaseSensitive(true), P.minOccurring(0));
        assertEquals(List.of("normal", "regex"), r.values());
        assertTrue(r.isCaseSensitive());
        assertEquals(0, r.minOccurring());

        // every slot, any order, on the TypeSymbol[] variant
        var s = new Parameter("s", new TypeSymbol[] { ScalarTypes.Long }, P.description("d"), P.examples(List.of("1")),
            P.defaultValueIndicator("*"), P.maxOccurring(4), P.values(List.of(1L)), P.argumentKind(ArgumentKind.Constant));
        assertEquals("d", s.description());
        assertEquals(List.of("1"), s.examples());
        assertEquals("*", s.defaultValueIndicator());
        assertEquals(4, s.maxOccurring());
        assertEquals(List.of(1L), s.values());
        assertEquals(ArgumentKind.Constant, s.argumentKind());
    }

    @Test
    void namedArgumentBuilderRejectsRepeatedSlots() {
        // C# CS1740 (named twice) and CS1744 (named after positional) are compile errors; P throws
        assertThrows(IllegalArgumentException.class, () -> new Parameter("x", ScalarTypes.Long, P.minOccurring(0), P.minOccurring(1)));
        assertThrows(IllegalArgumentException.class, () -> new Parameter("x", ScalarTypes.Long, ArgumentKind.Constant, P.argumentKind(ArgumentKind.Literal)));
        assertThrows(IllegalArgumentException.class, () -> new Parameter("x", ScalarTypes.Long, ArgumentKind.Constant, List.of(), P.values(List.of())));
    }

    @Test
    void heterogeneousValuesKeepTheirBoxedTypes() {
        // IReadOnlyList<object>: a C# boxed int never Equals a boxed long; Java Integer/Long behave the same way
        var p = new Parameter("x", ScalarTypes.Long, P.values(List.of(1, 1L, "1", true)));
        assertEquals(Integer.class, p.values().get(0).getClass());
        assertEquals(Long.class, p.values().get(1).getClass());
        assertNotEquals(p.values().get(0), p.values().get(1));
    }

    @Test
    void referenceIdentity() {
        var a = new Parameter("x", ScalarTypes.Long);
        var b = new Parameter("x", ScalarTypes.Long);
        assertNotEquals(a, b);
        var list = new ArrayList<Parameter>(List.of(b, a));
        assertEquals(1, ListExtensions.indexOf(list, a));
    }

    @Test
    void fromParameterSymbol() {
        var ps = new ParameterSymbol("p", ScalarTypes.Real, "about p");
        var p = Parameter.from(ps);
        assertEquals("p", p.name());
        assertSame(ScalarTypes.Real, p.declaredTypes().get(0));
        assertEquals(1, p.minOccurring());
        assertEquals("about p", p.description());
        assertEquals(0, Parameter.from(ps, true).minOccurring());
        var dv = expr();
        var q = Parameter.from(ps, false, dv);
        assertSame(dv, q.defaultValue());
        assertEquals(0, q.minOccurring());
    }

    @Test
    void parseListNeedsTheBinder() {
        // parser works (W4); stops at the W6 binder edge. W6 must turn this into a positive test.
        assertPending("W6", () -> Parameter.parseList("(x: long)"));
    }

    @Test
    void declarationText() {
        assumeKustoFacts();
        assertEquals("x: long", Parameter.getDeclaration(new Parameter("x", ScalarTypes.Long)));
        assertEquals("T: (*)", Parameter.getDeclaration(new Parameter("T", ParameterTypeKind.Tabular)));
        assertEquals("T: (*)", Parameter.getDeclaration(new Parameter("T", TableSymbol.Empty)));
        assertEquals("t: (a: long, ['b c']: string)", Parameter.getDeclaration(new Parameter("t",
            new TableSymbol(new ColumnSymbol("a", ScalarTypes.Long), new ColumnSymbol("b c", ScalarTypes.String)))));
        assertEquals("s: dynamic", Parameter.getDeclaration(new Parameter("s", ParameterTypeKind.Scalar)));
        assertEquals("u: dynamic", Parameter.getDeclaration(new Parameter("u", new TypeSymbol[] { ScalarTypes.Long, ScalarTypes.Real })));
        assertEquals("(x: long, T: (*))", Parameter.getParameterListDeclaration(List.of(
            new Parameter("x", ScalarTypes.Long), new Parameter("T", ParameterTypeKind.Tabular))));
        assertEquals("()", Parameter.getParameterListDeclaration(List.of()));
    }
}
