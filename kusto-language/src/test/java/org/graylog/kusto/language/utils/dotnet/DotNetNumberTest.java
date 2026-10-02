// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for DotNetNumber and DotNetDecimal beyond dotnet-facts.json.

package org.graylog.kusto.language.utils.dotnet;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DotNetNumberTest {
    @Test
    void integerSyntax() {
        assertEquals(5, DotNetNumber.tryParseInt("5\0\0"));
        assertNull(DotNetNumber.tryParseInt("5\0x"));
        assertNull(DotNetNumber.tryParseInt("- 5"));
        assertNull(DotNetNumber.tryParseInt(" 5"), "number white space is ASCII only");
        assertNull(DotNetNumber.tryParseInt("5-"));
        assertEquals(Long.MIN_VALUE, DotNetNumber.tryParseLong("-0009223372036854775808"));
        assertNull(DotNetNumber.tryParseLong("99999999999999999999"));
        assertNull(DotNetNumber.tryParseInt(null));
        assertEquals(0, DotNetNumber.parseIntOrZero("0x1F"));
        assertEquals(0L, DotNetNumber.parseLongOrZero("abc"));
        assertEquals(-12L, DotNetNumber.parseLongOrZero("  -12  "));
    }

    @Test
    void doubleSyntax() {
        assertEquals(100.0, DotNetNumber.tryParseDouble("1,0,0"));
        assertEquals(1.0, DotNetNumber.tryParseDouble("1,"));
        assertNull(DotNetNumber.tryParseDouble(",1"));
        assertNull(DotNetNumber.tryParseDouble("1.5,0"));
        assertNull(DotNetNumber.tryParseDouble("."));
        assertNull(DotNetNumber.tryParseDouble("1e"));
        assertNull(DotNetNumber.tryParseDouble("1e+"));
        assertNull(DotNetNumber.tryParseDouble("1f"));
        assertNull(DotNetNumber.tryParseDouble("0x10"));
        assertEquals(Double.POSITIVE_INFINITY, DotNetNumber.tryParseDouble(" infinity "));
        assertEquals(Double.POSITIVE_INFINITY, DotNetNumber.tryParseDouble("+Infinity"));
        assertTrue(Double.isNaN(DotNetNumber.tryParseDouble("-nan")));
        assertEquals(Double.POSITIVE_INFINITY, DotNetNumber.tryParseDouble("1e999999999999"));
        assertEquals(0.0, DotNetNumber.tryParseDouble("1e-999999999999"));
        assertEquals(0.0, DotNetNumber.parseDoubleOrZero("x"));
    }

    @Test
    void doubleFormat() {
        assertEquals("1E+17", DotNetNumber.toString(1e17));
        assertEquals("99999999999999980", DotNetNumber.toString(99999999999999980.0));
        assertEquals("0.0001", DotNetNumber.toString(1e-4));
        assertEquals("0.00012345", DotNetNumber.toString(1.2345e-4));
        assertEquals("1.2345E-05", DotNetNumber.toString(1.2345e-5));
        assertEquals("-123.456", DotNetNumber.toString(-123.456));
        assertEquals("4.9E-322", DotNetNumber.toString(4.9e-322));
        assertEquals("1E+100", DotNetNumber.toString(1e100));
        assertEquals("1E-45", DotNetNumber.toString(Float.MIN_VALUE));
        assertEquals("123456790", DotNetNumber.toString(123456789f));
        assertEquals("1.234568E+09", DotNetNumber.toString(1234567890f));
        assertEquals("-0", DotNetNumber.toString(-0.0f));
    }

    @Test
    void decimalRounding() {
        assertEquals("0.0000000000000000000000000000", DotNetDecimal.tryParse("0.00000000000000000000000000005").toPlainString());
        assertEquals("1.0000000000000000000000000000", DotNetDecimal.tryParse("1.00000000000000000000000000005").toPlainString());
        assertEquals("1.0000000000000000000000000002", DotNetDecimal.tryParse("1.00000000000000000000000000015").toPlainString());
        assertEquals("1.0000000000000000000000000001", DotNetDecimal.tryParse("1.000000000000000000000000000050001").toPlainString());
        assertEquals("79228162514264337593543950334", DotNetDecimal.tryParse("79228162514264337593543950334.5").toPlainString());
        assertNull(DotNetDecimal.tryParse("79228162514264337593543950335.5"));
        assertEquals("0.00", DotNetDecimal.tryParse("0.00").toPlainString());
        assertEquals("1.50", DotNetDecimal.toString(DotNetDecimal.tryParse("1.50")));
    }

    @Test
    void decimalSyntax() {
        assertEquals(new BigDecimal("-5"), DotNetDecimal.tryParse("5-"));
        assertEquals(new BigDecimal("-5"), DotNetDecimal.tryParse(" 5 - "));
        assertNull(DotNetDecimal.tryParse("-5-"));
        assertNull(DotNetDecimal.tryParse("(5)"));
        assertNull(DotNetDecimal.tryParse("1e3"));
        assertEquals("1000", DotNetDecimal.tryParse("1e3", true).toPlainString());
        assertEquals(BigDecimal.ZERO, DotNetDecimal.parseOrZero("x"));
        assertTrue(DotNetDecimal.isInRange(DotNetDecimal.MAX_VALUE));
        assertFalse(DotNetDecimal.isInRange(DotNetDecimal.MAX_VALUE.add(BigDecimal.ONE)));
        assertEquals(DotNetDecimal.MAX_VALUE.negate(), DotNetDecimal.MIN_VALUE);
    }
}
