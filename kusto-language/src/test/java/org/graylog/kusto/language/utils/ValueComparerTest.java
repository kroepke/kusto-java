// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for ValueComparer.AreEquivalent (Utils/ValueComparer.cs).
package org.graylog.kusto.language.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.graylog.kusto.language.utils.dotnet.TimeSpan;
import org.junit.jupiter.api.Test;

class ValueComparerTest {
    @Test
    void nullsAreEquivalentOnlyToNull() {
        assertTrue(ValueComparer.areEquivalent(null, null));
        assertFalse(ValueComparer.areEquivalent(null, 1L));
        assertFalse(ValueComparer.areEquivalent("a", null));
    }

    @Test
    void positiveAndNegativeZeroAreEquivalent() {
        // .NET 0.0.Equals(-0.0) is true (PORTING.md 5.2); Java Double.equals would say false
        assertTrue(ValueComparer.areEquivalent(0.0, -0.0));
        assertTrue(ValueComparer.areEquivalent(-0.0, 0.0));
    }

    @Test
    void nanEqualsNanLikeDotNetDoubleEquals() {
        assertTrue(ValueComparer.areEquivalent(Double.NaN, Double.NaN));
        assertFalse(ValueComparer.areEquivalent(Double.NaN, 1.0));
    }

    @Test
    void longVersusDoubleComparesAsDouble() {
        assertTrue(ValueComparer.areEquivalent(1L, 1.0));
        assertTrue(ValueComparer.areEquivalent(1.0, 1L));
        assertFalse(ValueComparer.areEquivalent(1L, 1.5));
        // the long is converted to double, so precision loss makes these equal
        assertTrue(ValueComparer.areEquivalent(Long.MAX_VALUE, (double) Long.MAX_VALUE));
    }

    @Test
    void integerVersusLongComparesAsLong() {
        assertTrue(ValueComparer.areEquivalent(1, 1L));
        assertFalse(ValueComparer.areEquivalent(1, 2L));
        assertTrue(ValueComparer.areEquivalent(7, 7));
    }

    @Test
    void floatVersusDoubleComparesAsDouble() {
        assertTrue(ValueComparer.areEquivalent(1.5f, 1.5));
        assertTrue(ValueComparer.areEquivalent(1.5f, 1.5f));
    }

    @Test
    void decimalComparesByValueNotScale() {
        assertTrue(ValueComparer.areEquivalent(new BigDecimal("1.0"), new BigDecimal("1.00")));
        assertTrue(ValueComparer.areEquivalent(new BigDecimal("2"), 2L));
        assertFalse(ValueComparer.areEquivalent(new BigDecimal("1.5"), 1L));
    }

    @Test
    void stringVersusNumberComparesAsString() {
        assertTrue(ValueComparer.areEquivalent("1", 1L));
        assertTrue(ValueComparer.areEquivalent(1L, "1"));
        assertTrue(ValueComparer.areEquivalent("1", 1.0)); // 1.0 formats as "1"
        assertFalse(ValueComparer.areEquivalent("1.0", 1.0));
        assertFalse(ValueComparer.areEquivalent("2", 1L));
    }

    @Test
    void stringComparisonIsCaseInsensitiveUnlessRequested() {
        assertTrue(ValueComparer.areEquivalent("ABC", "abc"));
        assertTrue(ValueComparer.areEquivalent("ABC", "abc", false));
        assertFalse(ValueComparer.areEquivalent("ABC", "abc", true));
        assertTrue(ValueComparer.areEquivalent("abc", "abc", true));
    }

    @Test
    void booleanComparesAsStringAgainstString() {
        assertTrue(ValueComparer.areEquivalent(true, "True"));
        assertTrue(ValueComparer.areEquivalent(false, "false"));
        assertFalse(ValueComparer.areEquivalent(true, 1L)); // Boolean is not numeric and the types differ
    }

    @Test
    void unrelatedTypesAreNotEquivalent() {
        assertFalse(ValueComparer.areEquivalent(1L, true));
        assertFalse(ValueComparer.areEquivalent(TimeSpan.ZERO, 1L));
    }

    @Test
    void sameTypeUsesEquals() {
        assertTrue(ValueComparer.areEquivalent(true, true));
        assertFalse(ValueComparer.areEquivalent(true, false));
        assertTrue(ValueComparer.areEquivalent(TimeSpan.ZERO, TimeSpan.ZERO));
    }

    @Test
    void timeSpanToStringConversionThrowsLikeConvertChangeType() {
        // Convert.ChangeType(TimeSpan, typeof(string)) throws InvalidCastException (PORTING.md 5.2)
        assertThrows(ClassCastException.class, () -> ValueComparer.areEquivalent(TimeSpan.ZERO, "00:00:00"));
    }
}
