// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: table of Symbol.IsAssignableTo cases derived from the ordered AreAssignable chain (Symbols/Symbol.cs:218-305).
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolAssignabilityTest {
    private static ColumnSymbol col(String name, TypeSymbol type) {
        return new ColumnSymbol(name, type);
    }

    // Upstream table/tuple/bag helpers. Table cases (Symbol.cs:276-277, 346-360) are not covered here.
    static Stream<Arguments> cases() {
        var L = ScalarTypes.Long;
        var I = ScalarTypes.Int;
        var S = ScalarTypes.String;
        var colA = col("a", L);
        var arrInt = new DynamicArraySymbol(I);
        var arrLong = new DynamicArraySymbol(L);
        return Stream.of(
            // Symbol.cs:220 same reference
            Arguments.of("same ref", L, L, Conversion.None, true),
            Arguments.of("same ref column", colA, colA, Conversion.None, true),
            // Symbol.cs:223 null target
            Arguments.of("null target", null, L, Conversion.Any, false),
            // Symbol.cs:226 unknown source to scalar target
            Arguments.of("unknown -> long", L, ScalarTypes.Unknown, Conversion.None, true),
            Arguments.of("unknown -> column", colA, ScalarTypes.Unknown, Conversion.None, true),
            Arguments.of("unknown -> error (not scalar)", ErrorSymbol.Instance, ScalarTypes.Unknown, Conversion.Any, false),
            // Symbol.cs:229 scalar source to unknown target
            Arguments.of("long -> unknown", ScalarTypes.Unknown, L, Conversion.None, true),
            Arguments.of("void -> unknown (void not scalar)", ScalarTypes.Unknown, VoidSymbol.Instance, Conversion.Any, false),
            // Symbol.cs:233-237 single column tuple reduces; the recursive call drops allowedConversion (default None)
            Arguments.of("tuple(long) -> long", L, new TupleSymbol(col("x", L)), Conversion.None, true),
            Arguments.of("tuple(int) -> long, promotable dropped", L, new TupleSymbol(col("x", I)), Conversion.Promotable, false),
            Arguments.of("tuple(int) -> long, any dropped", L, new TupleSymbol(col("x", I)), Conversion.Any, false),
            // Symbol.cs:239-247 dynamic target
            Arguments.of("dynamic(long) -> dynamic", ScalarTypes.Dynamic, ScalarTypes.DynamicLong, Conversion.None, true),
            Arguments.of("array -> dynamic", ScalarTypes.Dynamic, ScalarTypes.DynamicArrayOfLong, Conversion.None, true),
            Arguments.of("long -> dynamic, none", ScalarTypes.Dynamic, L, Conversion.None, false),
            Arguments.of("long -> dynamic, promotable", ScalarTypes.Dynamic, L, Conversion.Promotable, false),
            Arguments.of("long -> dynamic, dynamic", ScalarTypes.Dynamic, L, Conversion.Dynamic, true),
            Arguments.of("long -> dynamic, compatible", ScalarTypes.Dynamic, L, Conversion.Compatible, true),
            // Symbol.cs:249-252 DynamicArray target
            Arguments.of("array(long) -> array, promotable", ScalarTypes.DynamicArray, ScalarTypes.DynamicArrayOfLong, Conversion.Promotable, true),
            // with None it falls to the DynamicArraySymbol case: AreAssignable(Long, Dynamic) -> primitive, None -> false
            Arguments.of("array(long) -> array, none", ScalarTypes.DynamicArray, ScalarTypes.DynamicArrayOfLong, Conversion.None, false),
            // Symbol.cs:295-297 PORT-BUG: element types are passed (source, target)
            Arguments.of("array(int) -> array(long), promotable (swapped)", arrLong, arrInt, Conversion.Promotable, false),
            Arguments.of("array(long) -> array(int), promotable (swapped)", arrInt, arrLong, Conversion.Promotable, true),
            Arguments.of("array(long) -> array(long) distinct instances", arrLong, new DynamicArraySymbol(L), Conversion.None, true),
            // Symbol.cs:254-257 DynamicBag target
            Arguments.of("bag -> DynamicBag, promotable", ScalarTypes.DynamicBag, ScalarTypes.GeoShape, Conversion.Promotable, true),
            // with None: DynamicBagSymbol case, empty target bag has no required properties (Symbol.cs:327-341)
            Arguments.of("bag -> DynamicBag, none", ScalarTypes.DynamicBag, ScalarTypes.GeoShape, Conversion.None, true),
            Arguments.of("bag{a:int,b} -> bag{a:long}", new DynamicBagSymbol(col("a", L)), new DynamicBagSymbol(col("a", I), col("b", S)), Conversion.None, true),
            Arguments.of("bag{b} -> bag{a:long}", new DynamicBagSymbol(col("a", L)), new DynamicBagSymbol(col("b", S)), Conversion.None, false),
            Arguments.of("DynamicBag -> GeoShape", ScalarTypes.GeoShape, ScalarTypes.DynamicBag, Conversion.Any, false),
            // Symbol.cs:259-261 dynamic primitives compare underlying types with the same conversion
            Arguments.of("dynamic(int) -> dynamic(long), promotable", ScalarTypes.DynamicLong, new DynamicPrimitiveSymbol(I), Conversion.Promotable, true),
            Arguments.of("dynamic(int) -> dynamic(long), none", ScalarTypes.DynamicLong, new DynamicPrimitiveSymbol(I), Conversion.None, false),
            Arguments.of("dynamic(long) -> dynamic(long) distinct", ScalarTypes.DynamicLong, new DynamicPrimitiveSymbol(L), Conversion.None, true),
            // Symbol.cs:263-264 kinds differ
            Arguments.of("long -> column", colA, L, Conversion.Any, false),
            Arguments.of("void -> error", ErrorSymbol.Instance, VoidSymbol.Instance, Conversion.Any, false),
            Arguments.of("array -> long", L, ScalarTypes.DynamicArrayOfLong, Conversion.Any, false),
            // Symbol.cs:268-270 columns: ordinal name match and type assignability
            Arguments.of("col a:long -> col a:long", colA, col("a", L), Conversion.None, true),
            Arguments.of("col b:long -> col a:long", colA, col("b", L), Conversion.Any, false),
            Arguments.of("col A:long -> col a:long", colA, col("A", L), Conversion.Any, false),
            Arguments.of("col a:int -> col a:long, promotable", colA, col("a", I), Conversion.Promotable, true),
            Arguments.of("col a:int -> col a:long, none", colA, col("a", I), Conversion.None, false),
            // Symbol.cs:272-274 tuples and groups: member-wise with default (None) conversion
            Arguments.of("tuple2 -> tuple2", new TupleSymbol(col("a", L), col("b", S)), new TupleSymbol(col("a", L), col("b", S)), Conversion.None, true),
            Arguments.of("tuple2 (int) -> tuple2, any dropped", new TupleSymbol(col("a", L), col("b", S)), new TupleSymbol(col("a", I), col("b", S)), Conversion.Any, false),
            Arguments.of("tuple1 -> tuple2", new TupleSymbol(col("a", L), col("b", S)), new TupleSymbol(col("a", L)), Conversion.None, false),
            // a one-column source tuple is reduced first (Symbol.cs:233), so tuple1 -> tuple1 compares tuple vs long
            Arguments.of("tuple1 -> tuple1 distinct", new TupleSymbol(col("a", L)), new TupleSymbol(col("a", L)), Conversion.None, false),
            Arguments.of("group -> group", new GroupSymbol(L, S), new GroupSymbol(L, S), Conversion.None, true),
            Arguments.of("group -> group, order", new GroupSymbol(L, S), new GroupSymbol(S, L), Conversion.None, false),
            // Symbol.cs:279-293 primitives by conversion
            Arguments.of("int -> long, none", L, I, Conversion.None, false),
            Arguments.of("int -> long, promotable", L, I, Conversion.Promotable, true),
            Arguments.of("long -> int, promotable", I, L, Conversion.Promotable, false),
            Arguments.of("long -> int, dynamic", I, L, Conversion.Dynamic, false),
            Arguments.of("long -> int, compatible", I, L, Conversion.Compatible, true),
            Arguments.of("string -> long, compatible", L, S, Conversion.Compatible, false),
            Arguments.of("string -> long, any", L, S, Conversion.Any, true),
            Arguments.of("dynamic -> long, any (primitive kind)", L, ScalarTypes.Dynamic, Conversion.Any, true),
            Arguments.of("dynamic -> long, compatible", L, ScalarTypes.Dynamic, Conversion.Compatible, false),
            Arguments.of("dynamic -> string, promotable (string widerThan dynamic)", S, ScalarTypes.Dynamic, Conversion.Promotable, true),
            Arguments.of("null -> long, promotable", L, ScalarTypes.Null, Conversion.Promotable, false),
            // non-primitive target of the same kind as a primitive: DynamicAnySymbol is not a PrimitiveSymbol
            Arguments.of("long -> dynamic, any", ScalarTypes.Dynamic, L, Conversion.Any, true),
            Arguments.of("dynamic(long) -> long, any", L, ScalarTypes.DynamicLong, Conversion.Any, true),
            Arguments.of("error -> error", ErrorSymbol.Instance, ErrorSymbol.Instance, Conversion.None, true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void isAssignableTo(String label, Symbol target, Symbol source, Conversion conversion, boolean expected) {
        assertEquals(expected, source.isAssignableTo(target, conversion), label);
    }

    @Test
    void defaultConversionIsNone() {
        assertFalse(ScalarTypes.Int.isAssignableTo(ScalarTypes.Long));
        assertTrue(ScalarTypes.Int.isAssignableTo(ScalarTypes.Int));
    }

    @Test
    void isAssignableToAny() {
        // Symbol.cs:196-213
        assertTrue(ScalarTypes.Int.isAssignableToAny(List.of(ScalarTypes.String, ScalarTypes.Long), Conversion.Promotable));
        assertFalse(ScalarTypes.Long.isAssignableToAny(List.of(ScalarTypes.String, ScalarTypes.Int), Conversion.Promotable));
        assertFalse(ScalarTypes.Int.isAssignableToAny(List.of(ScalarTypes.String, ScalarTypes.Long)));
        assertFalse(ScalarTypes.Int.isAssignableToAny(List.<ScalarSymbol>of()));
    }
}
