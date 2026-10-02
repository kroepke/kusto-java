// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for the Dynamic* symbols (Symbols/DynamicSymbol.cs); upstream defines no structural equality.
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.dotnet.Out;
import org.junit.jupiter.api.Test;

class DynamicSymbolTest {
    @Test
    void namesAndKinds() {
        // every dynamic symbol is named "dynamic" (DynamicSymbol.cs:30,48,64,83)
        assertEquals("dynamic", DynamicAnySymbol.Instance.name());
        assertEquals("dynamic", ScalarTypes.DynamicLong.name());
        assertEquals("dynamic", ScalarTypes.DynamicArray.name());
        assertEquals("dynamic", ScalarTypes.DynamicBag.name());
        assertEquals(SymbolKind.Primitive, DynamicAnySymbol.Instance.kind());
        assertEquals(SymbolKind.Primitive, ScalarTypes.DynamicLong.kind());
        assertEquals(SymbolKind.Array, ScalarTypes.DynamicArray.kind());
        assertEquals(SymbolKind.Bag, ScalarTypes.DynamicBag.kind());
        assertEquals(Tabularity.Scalar, ScalarTypes.DynamicBag.tabularity());
        assertEquals(Tabularity.Scalar, ScalarTypes.DynamicArray.tabularity());
    }

    @Test
    void noStructuralEquality() {
        // no Equals/GetHashCode overrides upstream: equal shapes are distinct symbols
        var c = new ColumnSymbol("a", ScalarTypes.Long);
        assertFalse(new DynamicBagSymbol(c).equals(new DynamicBagSymbol(c)));
        assertFalse(new DynamicArraySymbol(ScalarTypes.Long).equals(new DynamicArraySymbol(ScalarTypes.Long)));
        assertFalse(new DynamicPrimitiveSymbol(ScalarTypes.Long).equals(ScalarTypes.DynamicLong));
    }

    @Test
    void emptyBag() {
        assertTrue(DynamicBagSymbol.Empty.properties().isEmpty());
        assertSame(EmptyReadOnlyList.Instance, DynamicBagSymbol.Empty.properties());
        assertTrue(new DynamicBagSymbol((ColumnSymbol[]) null).properties().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new DynamicBagSymbol(new ColumnSymbol("a", ScalarTypes.Long), null));
    }

    @Test
    void tryGetProperty() {
        // DynamicSymbol.cs:102-117: last writer wins on duplicate names
        var a1 = new ColumnSymbol("a", ScalarTypes.Long);
        var a2 = new ColumnSymbol("a", ScalarTypes.String);
        var b = new ColumnSymbol("b", ScalarTypes.Real);
        var bag = new DynamicBagSymbol(a1, b, a2);
        var p = new Out<ColumnSymbol>();
        assertTrue(bag.tryGetProperty("a", p));
        assertSame(a2, p.value);
        assertTrue(bag.tryGetProperty("b", p));
        assertSame(b, p.value);
        assertFalse(bag.tryGetProperty("c", p));
        assertNull(p.value);
        assertFalse(bag.tryGetProperty("A", p)); // ordinal
        assertThrows(NullPointerException.class, () -> bag.tryGetProperty(null, new Out<>()));
        assertEquals(List.<Symbol>of(a1, b, a2), bag.members());
    }

    @Test
    void addOrUpdatePropertyMirrorsUpstreamBug() {
        // PORT-BUG pin (DynamicSymbol.cs:146): the Select maps every other property to existingProperty
        var a = new ColumnSymbol("a", ScalarTypes.Long);
        var b = new ColumnSymbol("b", ScalarTypes.Long);
        var c = new ColumnSymbol("c", ScalarTypes.Long);
        var b2 = new ColumnSymbol("b", ScalarTypes.String);
        var bag = new DynamicBagSymbol(a, b, c);
        assertEquals(List.of(b, b2, b), bag.addOrUpdateProperty(b2).properties());

        var d = new ColumnSymbol("d", ScalarTypes.Long);
        assertEquals(List.of(a, b, c, d), bag.addOrUpdateProperty(d).properties());
        assertSame(bag, bag.addOrUpdateProperty(null));
    }

    @Test
    void withPropertiesAndSource() {
        var a = new ColumnSymbol("a", ScalarTypes.Long);
        var bag = new DynamicBagSymbol(a);
        assertSame(bag, bag.withProperties(bag.properties())); // reference comparison
        var bag2 = bag.withProperties(List.of(a));
        assertNotSame(bag, bag2);
        assertEquals(List.of(a), bag2.properties());
        var bag3 = bag.withSource(null);
        assertNotSame(bag, bag3);
        assertSame(a, bag3.properties().get(0));
    }

    @Test
    void underlyingAndElementTypes() {
        assertSame(ScalarTypes.Bool, ((DynamicPrimitiveSymbol) ScalarTypes.DynamicBool).underlyingType());
        assertSame(ScalarTypes.String, ScalarTypes.DynamicArrayOfString.elementType());
        assertNull(new DynamicArraySymbol(null).elementType());
    }
}
