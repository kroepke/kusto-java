// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for TimeSpan beyond dotnet-facts.json.

package org.graylog.kusto.language.utils.dotnet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TimeSpanTest {
    @Test
    void checkedArithmetic() {
        assertEquals(3L, TimeSpan.fromTicks(1).add(TimeSpan.fromTicks(2)).ticks());
        assertEquals(-1L, TimeSpan.fromTicks(1).subtract(TimeSpan.fromTicks(2)).ticks());
        assertThrows(ArithmeticException.class, () -> TimeSpan.MAX_VALUE.add(TimeSpan.fromTicks(1)));
        assertThrows(ArithmeticException.class, () -> TimeSpan.MIN_VALUE.add(TimeSpan.fromTicks(-1)));
        assertThrows(ArithmeticException.class, () -> TimeSpan.MIN_VALUE.subtract(TimeSpan.fromTicks(1)));
        assertThrows(ArithmeticException.class, () -> TimeSpan.MAX_VALUE.subtract(TimeSpan.fromTicks(-1)));
        assertThrows(ArithmeticException.class, TimeSpan.MIN_VALUE::negate);
        assertEquals(-5L, TimeSpan.fromTicks(5).negate().ticks());
    }

    @Test
    void constantFormat() {
        assertEquals("-1.02:03:04.0050000", TimeSpan.fromTicks(-(TimeSpan.TICKS_PER_DAY + 2 * TimeSpan.TICKS_PER_HOUR
                + 3 * TimeSpan.TICKS_PER_MINUTE + 4 * TimeSpan.TICKS_PER_SECOND + 50_000)).toString());
        assertEquals("-00:00:00.5000000", TimeSpan.fromTicks(-5_000_000).toString());
        assertEquals("12.00:00:00", TimeSpan.fromDays(12).toString());
        assertEquals("00:00:00", TimeSpan.ZERO.toString());
    }

    @Test
    void comparisonAndEquality() {
        assertTrue(TimeSpan.fromTicks(1).compareTo(TimeSpan.fromTicks(2)) < 0);
        assertTrue(TimeSpan.MAX_VALUE.compareTo(TimeSpan.MIN_VALUE) > 0);
        assertEquals(TimeSpan.fromSeconds(1), TimeSpan.fromTicks(TimeSpan.TICKS_PER_SECOND));
        assertEquals(TimeSpan.fromSeconds(1).hashCode(), TimeSpan.fromTicks(TimeSpan.TICKS_PER_SECOND).hashCode());
        assertNotEquals(TimeSpan.fromSeconds(1), TimeSpan.fromSeconds(2));
    }

    @Test
    void fromDoubleTruncatesTowardZero() {
        assertEquals(0L, TimeSpan.fromSeconds(-1e-8).ticks());
        assertEquals(1L, TimeSpan.fromSeconds(1.9e-7).ticks());
        assertEquals(-1L, TimeSpan.fromSeconds(-1.9e-7).ticks());
        assertEquals(TimeSpan.MAX_VALUE, TimeSpan.fromTicks(Long.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> TimeSpan.fromDays(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> TimeSpan.fromDays(Double.NaN));
    }

    @Test
    void parseForms() {
        assertEquals("01:02:00", TimeSpan.tryParse("1:2").toString());
        assertEquals("1.02:03:04.5000000", TimeSpan.tryParse("1:02:03:04.5").toString());
        assertEquals("1.02:03:00", TimeSpan.tryParse("\t1.02:03\n").toString());
        assertEquals("-3.00:00:00", TimeSpan.tryParse("-3").toString());
        assertEquals(1L, TimeSpan.tryParse("0:00:00.00000005").ticks());
        assertEquals(0L, TimeSpan.tryParse("0:00:00.00000004").ticks());
        assertEquals(0L, TimeSpan.tryParse("0:00:00.0000000000").ticks());
        assertEquals(10L, TimeSpan.tryParse("0:00:00.000001").ticks());
        assertNull(TimeSpan.tryParse("- 1:00"));
        assertNull(TimeSpan.tryParse("1 :00"));
        assertNull(TimeSpan.tryParse("10675200"));
        assertNull(TimeSpan.tryParse("1:00:00:00:00:00"));
        assertNull(TimeSpan.tryParse("999999999999"));
        assertNull(TimeSpan.tryParse(null));
    }

    @Test
    void components() {
        TimeSpan t = TimeSpan.tryParse("1.02:03:04.5");
        assertEquals(1, t.days());
        assertEquals(2, t.hours());
        assertEquals(3, t.minutes());
        assertEquals(4, t.seconds());
        assertEquals(500, t.milliseconds());
        assertEquals(93784.5, t.totalSeconds());
        assertEquals(1.5, TimeSpan.fromHours(36).totalDays());
    }
}
