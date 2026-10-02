// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for ScalarTypes (Symbols/ScalarTypes.cs) static init, lookup and dynamic mappings.
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ScalarTypesTest {
    /** Every name and alias the static ctor (ScalarTypes.cs:365-376) registers, in registration order. */
    private static Map<String, ScalarSymbol> expectedTypeMap() {
        var m = new LinkedHashMap<String, ScalarSymbol>();
        m.put("bool", ScalarTypes.Bool);
        m.put("boolean", ScalarTypes.Bool);
        m.put("int", ScalarTypes.Int);
        for (var a : List.of("int32", "uint", "uint32", "int8", "uint8", "int16", "uint16")) {
            m.put(a, ScalarTypes.Int);
        }
        m.put("long", ScalarTypes.Long);
        for (var a : List.of("int64", "ulong", "uint64")) {
            m.put(a, ScalarTypes.Long);
        }
        m.put("real", ScalarTypes.Real);
        m.put("double", ScalarTypes.Real);
        m.put("float", ScalarTypes.Real);
        m.put("decimal", ScalarTypes.Decimal);
        m.put("string", ScalarTypes.String);
        m.put("datetime", ScalarTypes.DateTime);
        m.put("date", ScalarTypes.DateTime);
        m.put("timespan", ScalarTypes.TimeSpan);
        m.put("time", ScalarTypes.TimeSpan);
        m.put("guid", ScalarTypes.Guid);
        m.put("uuid", ScalarTypes.Guid);
        m.put("uniqueid", ScalarTypes.Guid);
        m.put("type", ScalarTypes.Type);
        m.put("dynamic", ScalarTypes.Dynamic);
        m.put("null", ScalarTypes.Null);
        return m;
    }

    @Test
    void getSymbolResolvesEveryNameAndAlias() {
        var expected = expectedTypeMap();
        assertEquals(29, expected.size());
        for (var e : expected.entrySet()) {
            assertSame(e.getValue(), ScalarTypes.getSymbol(e.getKey()), e.getKey());
            assertSame(e.getValue(), ScalarSymbol.from(e.getKey()), e.getKey());
        }
    }

    @Test
    void getSymbolUnknownNamesReturnNull() {
        // ScalarTypes.cs:381-386: TryGetValue miss leaves the out value default (null)
        assertNull(ScalarTypes.getSymbol("unknown")); // Unknown is not in All
        assertNull(ScalarTypes.getSymbol("Bool")); // ordinal, case-sensitive Dictionary
        assertNull(ScalarTypes.getSymbol(""));
        assertNull(ScalarTypes.getSymbol("tuple"));
    }

    @Test
    void getSymbolNullThrowsLikeDictionaryTryGetValue() {
        assertThrows(NullPointerException.class, () -> ScalarTypes.getSymbol(null));
    }

    @Test
    void allOrder() {
        // ScalarTypes.cs:346-360
        assertEquals(List.of(ScalarTypes.Bool, ScalarTypes.Int, ScalarTypes.Long, ScalarTypes.Real, ScalarTypes.Decimal,
                ScalarTypes.String, ScalarTypes.DateTime, ScalarTypes.TimeSpan, ScalarTypes.Guid, ScalarTypes.Type,
                ScalarTypes.Dynamic, ScalarTypes.Null), ScalarTypes.All);
    }

    @Test
    void namesAndAliasesAreDistinctSoTheStaticCtorCannotThrow() {
        // The static ctor uses Dictionary.Add (DotNet.dictionaryAdd), which throws on a duplicate key. The throw is
        // unreachable with the upstream data: class init succeeds and every name/alias is distinct. This test pins that.
        var seen = new HashSet<String>();
        var all = new ArrayList<String>();
        for (var type : ScalarTypes.All) {
            all.add(type.name());
            all.addAll(type.aliases());
        }
        for (var key : all) {
            assertTrue(seen.add(key), "duplicate key " + key);
        }
        assertEquals(new ArrayList<>(expectedTypeMap().keySet()), all);
    }

    @Test
    void singletonsAreReferenceIdentical() {
        assertSame(DynamicAnySymbol.Instance, ScalarTypes.Dynamic);
        assertSame(DynamicBagSymbol.Empty, ScalarTypes.DynamicBag);
        assertSame(ScalarTypes.Dynamic, ScalarTypes.DynamicArray.elementType());
        assertSame(ScalarTypes.DynamicArray, ScalarTypes.DynamicArrayOfArray.elementType());
        assertSame(ScalarTypes.DynamicBag, ScalarTypes.DynamicArrayOfBag.elementType());
        assertSame(ScalarTypes.DynamicArrayOfReal, ScalarTypes.DynamicArrayOfArrayOfReal.elementType());
        assertSame(ScalarTypes.DynamicArrayOfString, ScalarTypes.DynamicArrayOfArrayOfString.elementType());
        assertSame(ScalarTypes.Long, ((DynamicPrimitiveSymbol) ScalarTypes.DynamicLong).underlyingType());
    }

    @Test
    void staticInitHasNoNullFields() {
        for (var f : ScalarTypes.class.getFields()) {
            try {
                assertTrue(f.get(null) != null, f.getName());
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
    }

    @Test
    void geoShape() {
        // ScalarTypes.cs:209-212
        var props = ScalarTypes.GeoShape.properties();
        assertEquals(2, props.size());
        assertEquals("type", props.get(0).name());
        assertSame(ScalarTypes.String, props.get(0).type());
        assertEquals("coordinates", props.get(1).name());
        assertSame(ScalarTypes.DynamicArray, props.get(1).type());
    }

    @Test
    void getDynamicMappings() {
        // ScalarTypes.cs:217-244
        assertSame(ScalarTypes.Dynamic, ScalarTypes.getDynamic(null));
        assertSame(ScalarTypes.Dynamic, ScalarTypes.getDynamic(ScalarTypes.Dynamic));
        assertSame(ScalarTypes.Dynamic, ScalarTypes.getDynamic(ScalarTypes.Null));
        assertSame(ScalarTypes.Dynamic, ScalarTypes.getDynamic(ScalarTypes.Unknown));
        assertSame(ScalarTypes.DynamicLong, ScalarTypes.getDynamic(ScalarTypes.DynamicLong));
        assertSame(ScalarTypes.DynamicArrayOfLong, ScalarTypes.getDynamic(ScalarTypes.DynamicArrayOfLong));
        assertSame(ScalarTypes.DynamicBool, ScalarTypes.getDynamic(ScalarTypes.Bool));
        assertSame(ScalarTypes.DynamicLong, ScalarTypes.getDynamic(ScalarTypes.Int)); // Int -> DynamicLong
        assertSame(ScalarTypes.DynamicLong, ScalarTypes.getDynamic(ScalarTypes.Long));
        assertSame(ScalarTypes.DynamicReal, ScalarTypes.getDynamic(ScalarTypes.Real));
        assertSame(ScalarTypes.DynamicDateTime, ScalarTypes.getDynamic(ScalarTypes.DateTime));
        assertSame(ScalarTypes.DynamicTimeSpan, ScalarTypes.getDynamic(ScalarTypes.TimeSpan));
        assertSame(ScalarTypes.DynamicGuid, ScalarTypes.getDynamic(ScalarTypes.Guid));
        assertSame(ScalarTypes.DynamicString, ScalarTypes.getDynamic(ScalarTypes.String));
        assertSame(ScalarTypes.Dynamic, ScalarTypes.getDynamic(ScalarTypes.Decimal));
        assertSame(ScalarTypes.Dynamic, ScalarTypes.getDynamic(ScalarTypes.Type));
        assertSame(ScalarTypes.Dynamic, ScalarTypes.getDynamic(ErrorSymbol.Instance));
    }

    @Test
    void getDynamicArrayMappings() {
        // ScalarTypes.cs:249-292
        assertSame(ScalarTypes.DynamicArray, ScalarTypes.getDynamicArray(null));
        assertSame(ScalarTypes.DynamicArrayOfBool, ScalarTypes.getDynamicArray(ScalarTypes.Bool));
        assertSame(ScalarTypes.DynamicArrayOfLong, ScalarTypes.getDynamicArray(ScalarTypes.Int)); // Int -> array of long
        assertSame(ScalarTypes.DynamicArrayOfLong, ScalarTypes.getDynamicArray(ScalarTypes.Long));
        assertSame(ScalarTypes.DynamicArrayOfLong, ScalarTypes.getDynamicArray(ScalarTypes.DynamicLong)); // unwrapped
        assertSame(ScalarTypes.DynamicArrayOfReal, ScalarTypes.getDynamicArray(ScalarTypes.Real));
        assertSame(ScalarTypes.DynamicArrayOfDateTime, ScalarTypes.getDynamicArray(ScalarTypes.DateTime));
        assertSame(ScalarTypes.DynamicArrayOfTimeSpan, ScalarTypes.getDynamicArray(ScalarTypes.TimeSpan));
        assertSame(ScalarTypes.DynamicArrayOfGuid, ScalarTypes.getDynamicArray(ScalarTypes.Guid));
        assertSame(ScalarTypes.DynamicArray, ScalarTypes.getDynamicArray(ScalarTypes.Dynamic));
        assertSame(ScalarTypes.DynamicArrayOfString, ScalarTypes.getDynamicArray(ScalarTypes.String));
        assertSame(ScalarTypes.DynamicArrayOfString, ScalarTypes.getDynamicArray(ScalarTypes.DynamicString));
        assertSame(ScalarTypes.DynamicArrayOfArray, ScalarTypes.getDynamicArray(ScalarTypes.DynamicArray));
        assertSame(ScalarTypes.DynamicArrayOfBag, ScalarTypes.getDynamicArray(ScalarTypes.DynamicBag));
        assertSame(ScalarTypes.DynamicArray, ScalarTypes.getDynamicArray(ScalarTypes.Null));
        assertSame(ScalarTypes.DynamicArray, ScalarTypes.getDynamicArray(ScalarTypes.Unknown));
        assertSame(ScalarTypes.DynamicArrayOfArrayOfReal, ScalarTypes.getDynamicArray(ScalarTypes.DynamicArrayOfReal));
        assertSame(ScalarTypes.DynamicArrayOfArrayOfString, ScalarTypes.getDynamicArray(ScalarTypes.DynamicArrayOfString));
        assertSame(ScalarTypes.DynamicArray, ScalarTypes.getDynamicArray(ScalarTypes.Decimal));

        // other arrays and bags get a fresh array symbol each call (ScalarTypes.cs:287-289)
        var a1 = ScalarTypes.getDynamicArray(ScalarTypes.DynamicArrayOfLong);
        var a2 = ScalarTypes.getDynamicArray(ScalarTypes.DynamicArrayOfLong);
        assertNotSame(a1, a2);
        assertSame(ScalarTypes.DynamicArrayOfLong, a1.elementType());
        assertSame(ScalarTypes.GeoShape, ScalarTypes.getDynamicArray(ScalarTypes.GeoShape).elementType());
    }

    @Test
    void getDynamicBagAndTuple() {
        assertSame(ScalarTypes.DynamicBag, ScalarTypes.getDynamicBag((List<ColumnSymbol>) null));
        assertSame(ScalarTypes.DynamicBag, ScalarTypes.getDynamicBag(List.of()));
        assertSame(ScalarTypes.DynamicBag, ScalarTypes.getDynamicBag());
        assertSame(ScalarTypes.DynamicBag, ScalarTypes.getDynamicBag((ColumnSymbol[]) null));
        var c = new ColumnSymbol("a", ScalarTypes.Long);
        var bag = ScalarTypes.getDynamicBag(c);
        assertNotSame(ScalarTypes.DynamicBag, bag);
        assertEquals(List.of(c), bag.properties());

        var t = ScalarTypes.getTuple(c);
        assertEquals(List.of(c), t.columns());
        assertEquals(List.of(c), ScalarTypes.getTuple(List.of(c)).columns());
    }

    @Test
    void primitiveFlagsAndWidening() {
        // ScalarTypes.cs:17-84
        assertTrue(ScalarTypes.Int.isInteger() && ScalarTypes.Int.isNumeric() && ScalarTypes.Int.isInterval()
                && ScalarTypes.Int.isSummable() && ScalarTypes.Int.isOrderable());
        assertFalse(ScalarTypes.Real.isInteger());
        assertTrue(ScalarTypes.Real.isNumeric());
        assertTrue(ScalarTypes.Bool.isOrderable());
        assertFalse(ScalarTypes.Bool.isNumeric());
        assertFalse(ScalarTypes.Guid.isOrderable());
        assertTrue(ScalarTypes.Unknown.isInteger() && ScalarTypes.Unknown.isNumeric()); // ScalarFlags.All
        assertFalse(ScalarTypes.Null.isOrderable());
        assertFalse(ScalarTypes.Dynamic.isNumeric()); // DynamicAnySymbol keeps the ScalarSymbol defaults
        assertFalse(ScalarTypes.Int.isMultiValue());

        assertTrue(ScalarTypes.Long.isWiderThan(ScalarTypes.Int));
        assertFalse(ScalarTypes.Int.isWiderThan(ScalarTypes.Long));
        assertTrue(ScalarTypes.Real.isWiderThan(ScalarTypes.Long));
        assertTrue(ScalarTypes.Decimal.isWiderThan(ScalarTypes.Real));
        assertFalse(ScalarTypes.Long.isWiderThan(ScalarTypes.Long));
        assertTrue(ScalarTypes.String.isWiderThan(ScalarTypes.Dynamic)); // ScalarTypes.cs:72 widerThan: { Dynamic }

        assertEquals(List.of("int32", "uint", "uint32", "int8", "uint8", "int16", "uint16"), ScalarTypes.Int.aliases());
        assertTrue(ScalarTypes.Decimal.aliases().isEmpty());
        assertTrue(ScalarTypes.Dynamic.aliases().isEmpty());
        assertEquals(SymbolKind.Primitive, ScalarTypes.Long.kind());
        assertEquals(Tabularity.Scalar, ScalarTypes.Long.tabularity());
    }

    @Test
    void scalarFlagsBits() {
        assertEquals(0, ScalarFlags.None);
        assertEquals(0b1_1111, ScalarFlags.All);
        assertEquals(ScalarFlags.Integer | ScalarFlags.Numeric | ScalarFlags.Interval | ScalarFlags.Summable | ScalarFlags.Orderable, ScalarFlags.All);
    }

    @Test
    void primitiveSymbolRejectsNullElements() {
        assertThrows(IllegalArgumentException.class, () -> new PrimitiveSymbol("x", new String[] { "a", null }));
        assertThrows(IllegalArgumentException.class, () -> new PrimitiveSymbol("x", null, 0, new ScalarSymbol[] { null }));
        assertTrue(new PrimitiveSymbol("x").aliases().isEmpty());
    }
}
