// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for DotNet, DotNetGuid and DotNetBoolean beyond dotnet-facts.json.

package org.graylog.kusto.language.utils.dotnet;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DotNetMiscTest {
    private static final String G = "01234567-89ab-cdef-0123-456789abcdef";

    @Test
    void str() {
        assertEquals("", DotNet.str(null));
        assertEquals("True", DotNet.str(true));
        assertEquals("1E+21", DotNet.str(1e21));
        assertEquals("1.50", DotNet.str(new BigDecimal("1.50")));
        assertEquals("01:00:00", DotNet.str(TimeSpan.fromHours(1)));
        assertEquals(G, DotNet.str(UUID.fromString(G)));
        assertEquals("x", DotNet.str('x'));
        assertEquals("-7", DotNet.str(-7L));
    }

    @Test
    void changeType() {
        assertEquals(3L, DotNet.changeType(3, Long.class));
        assertEquals(4L, DotNet.changeType(3.5, Long.class));
        assertEquals(new BigDecimal("0.333333333333333"), DotNet.changeType(1.0 / 3, BigDecimal.class));
        assertEquals(new BigDecimal("100"), DotNet.changeType(100.0, BigDecimal.class));
        assertEquals(Boolean.FALSE, DotNet.changeType("False", Boolean.class));
        assertEquals("False", DotNet.changeType(false, String.class));
        assertThrows(IllegalArgumentException.class, () -> DotNet.changeType("x", Boolean.class));
        assertThrows(ArithmeticException.class, () -> DotNet.changeType("99999999999999999999", Long.class));
        assertThrows(IllegalArgumentException.class, () -> DotNet.changeType("1.5", Long.class));
        assertThrows(ClassCastException.class, () -> DotNet.changeType(DateTime.MIN_VALUE, Long.class));
        assertThrows(ClassCastException.class, () -> DotNet.changeType(TimeSpan.ZERO, Double.class));
        TimeSpan t = TimeSpan.ZERO;
        assertSame(t, DotNet.changeType(t, TimeSpan.class));
        assertNull(DotNet.changeType(null, String.class));
    }

    @Test
    void dictionaryAdd() {
        Map<String, Integer> m = new LinkedHashMap<>();
        DotNet.dictionaryAdd(m, "k", 1);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> DotNet.dictionaryAdd(m, "k", 2));
        assertEquals("An item with the same key has already been added. Key: k", e.getMessage());
        assertEquals(1, m.get("k"));
    }

    public static final class Probe {
        public Probe() {
        }
    }

    public static final class Thrower {
        public Thrower() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void newInstance() {
        assertNotNull(DotNet.newInstance(Probe.class));
        assertEquals("boom", assertThrows(IllegalStateException.class, () -> DotNet.newInstance(Thrower.class)).getMessage());
        assertThrows(IllegalStateException.class, () -> DotNet.newInstance(Runnable.class));
    }

    @Test
    void longCast() {
        assertEquals(Long.MAX_VALUE, DotNet.longCast(Double.POSITIVE_INFINITY));
        assertEquals(0L, DotNet.longCast(Double.NaN));
        assertEquals(-1L, DotNet.longCast(-1.9));
    }

    @Test
    void guidFormats() {
        assertEquals(G, DotNetGuid.tryParse(" { 0x01234567 , 0x89ab,0xcdef,{0x1,0x23,0x45,0x67,0x89,0xab,0xcd,0xef}} ").toString());
        assertEquals("00234567-89ab-cdef-0123-456789abcdef", DotNetGuid.tryParse("0x234567-89ab-cdef-0123-456789abcdef").toString());
        assertEquals("00034567-89ab-cdef-0123-456789abcdef", DotNetGuid.tryParse("+0X34567-89ab-cdef-0123-456789abcdef").toString());
        assertNull(DotNetGuid.tryParse("0123456789abcdef0123456789abcdeg"));
        assertNull(DotNetGuid.tryParse("{0x01234567,0x89ab,0xcdef,{0x01,0x23,0x45,0x67,0x89,0xab,0xcd,0x1ef}}"));
        assertNull(DotNetGuid.tryParse("{0x01234567,0x89ab,0xcdef,{0x01,0x23,0x45,0x67,0x89,0xab,0xcd,0xef}}x"));
        assertNull(DotNetGuid.tryParse("(" + G + "}"));
        assertNull(DotNetGuid.tryParse("０123456789abcdef0123456789abcdef"));
        assertNull(DotNetGuid.tryParse(null));
        assertEquals(new UUID(0, 0), DotNetGuid.EMPTY);
    }

    @Test
    void booleanParse() {
        assertEquals(Boolean.FALSE, DotNetBoolean.tryParse("\0\0fAlSe\t"));
        assertEquals(Boolean.TRUE, DotNetBoolean.tryParse("true "));
        assertNull(DotNetBoolean.tryParse(" tru"));
        assertNull(DotNetBoolean.tryParse("\u200btrue"));
        assertEquals("False", DotNetBoolean.toString(false));
        assertNull(DotNetBoolean.tryParse(null));
    }
}
