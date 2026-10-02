// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for TypeFacts (Symbols/TypeFacts.cs): widest/common types, promotion, union/intersect.
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class TypeFactsTest {
    private static final ScalarSymbol I = ScalarTypes.Int;
    private static final ScalarSymbol L = ScalarTypes.Long;
    private static final ScalarSymbol R = ScalarTypes.Real;
    private static final ScalarSymbol D = ScalarTypes.Decimal;
    private static final ScalarSymbol S = ScalarTypes.String;

    @Test
    void widestScalarType() {
        // TypeFacts.cs:41-62
        assertSame(R, TypeFacts.getWidestScalarType(I, L, R));
        assertSame(R, TypeFacts.getWidestScalarType(R, L, I));
        assertSame(L, TypeFacts.getWidestScalarType(L, I));
        assertSame(I, TypeFacts.getWidestScalarType(I));
        assertSame(D, TypeFacts.getWidestScalarType(R, D));
        assertSame(D, TypeFacts.getWidestScalarType(D, R));
        assertSame(L, TypeFacts.getWidestScalarType(S, L, ScalarTypes.Bool)); // non-numeric ignored
        assertNull(TypeFacts.getWidestScalarType(S, ScalarTypes.Bool));
        assertNull(TypeFacts.getWidestScalarType());
        assertNull(TypeFacts.getWidestScalarType((List<TypeSymbol>) null));
        assertNull(TypeFacts.getWidestScalarType((TypeSymbol[]) null));
        assertNull(TypeFacts.getWidestScalarType(ScalarTypes.DynamicLong)); // DynamicPrimitiveSymbol is not numeric
        // Unknown has ScalarFlags.All and nothing is wider than it
        assertSame(ScalarTypes.Unknown, TypeFacts.getWidestScalarType(ScalarTypes.Unknown, L));
        assertSame(L, TypeFacts.getWidestScalarType(Arrays.asList(null, L, null)));
    }

    @Test
    void promoteToLong() {
        // TypeFacts.cs:364-365
        assertSame(L, TypeFacts.promoteToLong(I));
        assertSame(L, TypeFacts.promoteToLong(L));
        assertSame(S, TypeFacts.promoteToLong(S));
        assertSame(ScalarTypes.DynamicLong, TypeFacts.promoteToLong(ScalarTypes.DynamicLong));
        assertNull(TypeFacts.promoteToLong(null)); // null receiver tolerated (extension method)
    }

    @Test
    void isPromotableTo() {
        // TypeFacts.cs:370-377
        assertTrue(TypeFacts.isPromotableTo(I, L));
        assertTrue(TypeFacts.isPromotableTo(I, R));
        assertFalse(TypeFacts.isPromotableTo(L, I));
        assertFalse(TypeFacts.isPromotableTo(L, L));
        assertTrue(TypeFacts.isPromotableTo(ScalarTypes.Dynamic, S)); // string is declared wider than dynamic
        assertFalse(TypeFacts.isPromotableTo(S, ScalarTypes.Dynamic));
        assertTrue(TypeFacts.isPromotableTo(ScalarTypes.DynamicArrayOfLong, ScalarTypes.Dynamic));
        assertTrue(TypeFacts.isPromotableTo(ScalarTypes.GeoShape, ScalarTypes.Dynamic));
        assertFalse(TypeFacts.isPromotableTo(ScalarTypes.DynamicLong, ScalarTypes.Dynamic));
        assertFalse(TypeFacts.isPromotableTo(L, null));
        // null receiver dereferences Kind upstream (NullReferenceException)
        assertThrows(NullPointerException.class, () -> TypeFacts.isPromotableTo(null, L));
    }

    @Test
    void commonScalarType() {
        // TypeFacts.cs:93-106 and the TryGetCommonType chain (193-308)
        assertSame(L, TypeFacts.getCommonScalarType(I, L));
        assertSame(L, TypeFacts.getCommonScalarType(L, I));
        assertSame(R, TypeFacts.getCommonScalarType(I, L, R));
        assertSame(L, TypeFacts.getCommonScalarType(ScalarTypes.Null, L)); // 219
        assertSame(L, TypeFacts.getCommonScalarType(L, ScalarTypes.Null)); // 224
        assertSame(ScalarTypes.Unknown, TypeFacts.getCommonScalarType(ScalarTypes.Unknown, L)); // 229
        assertSame(ScalarTypes.Dynamic, TypeFacts.getCommonScalarType(L, ScalarTypes.Dynamic)); // 235
        assertNull(TypeFacts.getCommonScalarType(I, S)); // no common type -> defaultType null
        assertSame(ScalarTypes.Dynamic, TypeFacts.getCommonScalarType(List.of(I, S), Conversion.Dynamic)); // 297
        assertNull(TypeFacts.getCommonScalarType(List.of(I, L), Conversion.None)); // promotion not allowed
        assertNull(TypeFacts.getCommonScalarType());
        assertNull(TypeFacts.getCommonScalarType((List<TypeSymbol>) null));
        // non-scalar types are filtered out by fnInclude
        assertSame(L, TypeFacts.getCommonScalarType(L, VoidSymbol.Instance));
    }

    @Test
    void commonTypeOfDynamics() {
        // 265-271: both dynamic primitives
        assertSame(ScalarTypes.DynamicReal, TypeFacts.getCommonScalarType(ScalarTypes.DynamicLong, ScalarTypes.DynamicReal));
        // 272-278: dynamic primitive and scalar
        assertSame(ScalarTypes.DynamicReal, TypeFacts.getCommonScalarType(ScalarTypes.DynamicLong, R));
        // 279-285: scalar and dynamic primitive
        assertSame(ScalarTypes.DynamicLong, TypeFacts.getCommonScalarType(I, ScalarTypes.DynamicLong));
        // 286-291: other dynamic pairs
        assertSame(ScalarTypes.Dynamic, TypeFacts.getCommonScalarType(ScalarTypes.DynamicLong, ScalarTypes.DynamicArrayOfLong));
        assertSame(ScalarTypes.Dynamic, TypeFacts.getCommonScalarType(ScalarTypes.DynamicLong, ScalarTypes.DynamicString));
        // 253-259: arrays
        assertSame(ScalarTypes.DynamicArray, TypeFacts.getCommonScalarType(ScalarTypes.DynamicArrayOfLong, ScalarTypes.DynamicArrayOfString));
        // PORT-BUG pin (TypeFacts.cs:255-257): equal element types yield the element type itself
        var a1 = new DynamicArraySymbol(ScalarTypes.DynamicBag);
        var a2 = new DynamicArraySymbol(ScalarTypes.DynamicBag);
        assertSame(ScalarTypes.DynamicBag, TypeFacts.getCommonScalarType(a1, a2));
        // 260-264: bags intersect their properties
        var bagA = new DynamicBagSymbol(new ColumnSymbol("a", I), new ColumnSymbol("b", S));
        var bagB = new DynamicBagSymbol(new ColumnSymbol("a", L));
        var common = (DynamicBagSymbol) TypeFacts.getCommonScalarType(bagA, bagB);
        assertEquals(1, common.properties().size());
        assertEquals("a", common.properties().get(0).name());
        assertSame(L, common.properties().get(0).type());
        assertSame(ScalarTypes.DynamicBag, TypeFacts.getCommonScalarType(bagA, ScalarTypes.GeoShape)); // no shared names
    }

    @Test
    void commonTypeOptions() {
        // TypeFacts.cs:67-88
        assertSame(S, TypeFacts.getCommonType(List.of(I, S), null, Conversion.Promotable, S)); // failure -> defaultType
        assertSame(S, TypeFacts.getCommonType(List.of(), null, Conversion.Promotable, S)); // empty -> defaultType
        assertSame(L, TypeFacts.getCommonType(Arrays.asList(I, null, L))); // null entries skipped
        assertSame(I, TypeFacts.getCommonType(List.of(I, S), t -> t != S));
        assertSame(S, TypeFacts.getCommonType(null, null, Conversion.Promotable, S));
    }

    @Test
    void commonColumnType() {
        // TypeFacts.cs:171-188
        assertSame(L, TypeFacts.getCommonColumnType(List.of(new ColumnSymbol("a", I), new ColumnSymbol("b", L))));
        assertNull(TypeFacts.getCommonColumnType(List.of(new ColumnSymbol("a", I), new ColumnSymbol("b", S))));
        assertSame(ScalarTypes.Dynamic, TypeFacts.getCommonColumnType(List.of(new ColumnSymbol("a", I), new ColumnSymbol("b", S)), Conversion.Dynamic));
        assertSame(R, TypeFacts.getCommonColumnType(List.of(), Conversion.Promotable, R));
    }

    @Test
    void conversionOrder() {
        // TypeFacts.cs:313-316: enum <= in declaration order
        assertTrue(TypeFacts.isConversionAllowed(Conversion.None, Conversion.None));
        assertTrue(TypeFacts.isConversionAllowed(Conversion.Promotable, Conversion.Dynamic));
        assertFalse(TypeFacts.isConversionAllowed(Conversion.Dynamic, Conversion.Promotable));
        assertTrue(TypeFacts.isConversionAllowed(Conversion.Compatible, Conversion.Any));
        assertFalse(TypeFacts.isConversionAllowed(Conversion.Any, Conversion.Compatible));
    }

    @Test
    void elementType() {
        // TypeFacts.cs:14-28
        assertSame(ScalarTypes.DynamicLong, TypeFacts.getElementType(ScalarTypes.DynamicArrayOfLong));
        assertSame(ScalarTypes.Dynamic, TypeFacts.getElementType(ScalarTypes.DynamicArray));
        assertSame(ScalarTypes.Dynamic, TypeFacts.getElementType(ScalarTypes.Dynamic));
        assertNull(TypeFacts.getElementType(L));
        assertNull(TypeFacts.getElementType(null));
    }

    @Test
    void predicates() {
        assertTrue(TypeFacts.isInteger(I));
        assertFalse(TypeFacts.isInteger(R));
        assertFalse(TypeFacts.isInteger(null));
        assertTrue(TypeFacts.isNumeric(D));
        assertTrue(TypeFacts.isInterval(ScalarTypes.TimeSpan));
        assertTrue(TypeFacts.isSummable(ScalarTypes.DateTime));
        assertTrue(TypeFacts.isOrderable(S));
        assertFalse(TypeFacts.isOrderable(new ColumnSymbol("a", S)));
        assertTrue(TypeFacts.isAnyScalarExceptDynamic(L));
        assertFalse(TypeFacts.isAnyScalarExceptDynamic(ScalarTypes.DynamicLong));
        assertFalse(TypeFacts.isAnyScalarExceptBool(ScalarTypes.DynamicBool));
        assertTrue(TypeFacts.isAnyScalarExceptBool(ScalarTypes.DynamicLong));
        assertFalse(TypeFacts.isAnyScalarExceptReadOrBool(ScalarTypes.DynamicReal));
        assertTrue(TypeFacts.isAnyScalarExceptReadOrBool(D));
        assertTrue(TypeFacts.isNumericOrBool(ScalarTypes.Bool));
        assertTrue(TypeFacts.isRealOrDecimal(D));
        assertFalse(TypeFacts.isRealOrDecimal(L));
        assertTrue(TypeFacts.isStringOrDynamic(ScalarTypes.DynamicArrayOfLong));
        assertTrue(TypeFacts.isStringOrArray(ScalarTypes.DynamicArrayOfString));
        assertFalse(TypeFacts.isStringOrArray(ScalarTypes.DynamicArrayOfLong));
        assertTrue(TypeFacts.isIntegerOrArray(ScalarTypes.DynamicArrayOfLong));
        assertTrue(TypeFacts.isIntegerOrDynamic(ScalarTypes.DynamicLong));
        assertFalse(TypeFacts.isIntegerOrDynamic(ScalarTypes.DynamicArray));
        assertTrue(TypeFacts.isDynamicArray(ScalarTypes.Dynamic));
        assertFalse(TypeFacts.isDynamicArray(ScalarTypes.DynamicBag));
        assertTrue(TypeFacts.isDynamicBag(ScalarTypes.GeoShape));
        assertTrue(TypeFacts.isDynamicArrayOrBag(ScalarTypes.DynamicArrayOfArray));
        assertFalse(TypeFacts.isDynamicArrayOrBag(ScalarTypes.DynamicLong));
    }

    @Test
    void union() {
        // TypeFacts.cs:519-586
        var a = new ColumnSymbol("a", I);
        var b = new ColumnSymbol("b", S);
        var a2 = new ColumnSymbol("a", L);
        var c = new ColumnSymbol("c", ScalarTypes.Bool);
        var result = TypeFacts.union(List.of(a, b), List.of(a2, c));
        // PORT-BUG pin (TypeFacts.cs:534-537): a found common type (long) is not stored, so "a" keeps int
        assertEquals(List.of(a, b, c), result);

        // no common type at all (void is not scalar): the map gets dynamic and "a" is rebuilt
        var aVoid = new ColumnSymbol("a", VoidSymbol.Instance);
        var result2 = TypeFacts.union(List.of(a), List.of(aVoid));
        assertEquals(1, result2.size());
        assertNotSame(a, result2.get(0));
        assertEquals("a", result2.get(0).name());
        assertSame(ScalarTypes.Dynamic, result2.get(0).type());

        // pooled scratch lists are cleared after return; earlier results are unaffected (ToReadOnly copies)
        assertEquals(List.of(a, b, c), result);
    }

    @Test
    void intersect() {
        // TypeFacts.cs:592-632
        var a = new ColumnSymbol("a", I);
        var b = new ColumnSymbol("b", S);
        var a2 = new ColumnSymbol("a", L);
        var c = new ColumnSymbol("c", ScalarTypes.Bool);
        var result = TypeFacts.intersect(List.of(a, b), List.of(a2, c));
        assertEquals(1, result.size());
        assertEquals("a", result.get(0).name());
        assertSame(L, result.get(0).type());

        var same = TypeFacts.intersect(List.of(a2), List.of(a));
        assertSame(a2, same.get(0)); // common type equals the column type: column kept

        var aVoid = new ColumnSymbol("a", VoidSymbol.Instance);
        var dyn = TypeFacts.intersect(List.of(a), List.of(aVoid));
        assertSame(ScalarTypes.Dynamic, dyn.get(0).type());

        assertTrue(TypeFacts.intersect(List.of(a), List.of(b)).isEmpty());
    }
}
