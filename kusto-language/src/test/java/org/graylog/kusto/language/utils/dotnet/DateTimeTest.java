// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for DateTime beyond dotnet-facts.json.

package org.graylog.kusto.language.utils.dotnet;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DateTimeTest {
    @Test
    void rangeAndFormat() {
        assertEquals("01/01/0001 00:00:00", DateTime.MIN_VALUE.toString());
        assertEquals("12/31/9999 23:59:59", DateTime.MAX_VALUE.toString());
        assertThrows(IndexOutOfBoundsException.class, () -> DateTime.ofTicks(-1, DateTime.Kind.Utc));
        assertThrows(IndexOutOfBoundsException.class,
                () -> DateTime.ofTicks(DateTime.MAX_VALUE.ticks() + 1, DateTime.Kind.Utc));
    }

    @Test
    void equalityIgnoresKind() {
        DateTime a = DateTime.ofTicks(42, DateTime.Kind.Utc);
        DateTime b = DateTime.ofTicks(42, DateTime.Kind.Local);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(DateTime.MIN_VALUE.compareTo(a) < 0);
        assertEquals(0, a.compareTo(b));
    }

    @Test
    void components() {
        DateTime d = DateTime.tryParse("2020-02-29T13:14:15.678");
        assertEquals(2020, d.year());
        assertEquals(2, d.month());
        assertEquals(29, d.day());
        assertEquals(13, d.hour());
        assertEquals(14, d.minute());
        assertEquals(15, d.second());
        assertEquals(678, d.millisecond());
        assertEquals(6, d.dayOfWeek()); // Saturday
        assertEquals(1, DateTime.MIN_VALUE.dayOfWeek()); // Monday
    }

    @Test
    void moreForms() {
        assertEquals("01/02/2020 15:04:00", DateTime.tryParse("1/2/2020 3:04 PM").toString());
        assertEquals("01/02/2020 00:30:00", DateTime.tryParse("1/2/2020 12:30 am").toString());
        assertEquals("01/02/2020 00:00:00", DateTime.tryParse("January 2, 2020").toString());
        assertEquals("01/02/2020 00:00:00", DateTime.tryParse("Thursday, 2 January 2020").toString());
        assertEquals("01/02/2020 00:00:00", DateTime.tryParse("2020/1/2").toString());
        assertEquals("01/02/2020 05:04:05", DateTime.tryParse("2020-01-02T03:04:05-0200").toString());
        assertEquals(DateTime.Kind.Utc, DateTime.tryParse("2020-01-02T03:04:05z").kind());
        assertNull(DateTime.tryParse("Fri, 02 Jan 2020"));
        // .NET AdjustHour: PM accepts hours up to 23 and leaves 12..23 unchanged.
        assertEquals("01/02/2020 13:00:00", DateTime.tryParse("1/2/2020 13:00 PM").toString());
        assertNull(DateTime.tryParse("1/2/2020 13:00 AM"));
        assertNull(DateTime.tryParse("2020-01-02T03:04:05 +01:00 x"));
        assertNull(DateTime.tryParse("2020-01-02T03:04:05+15:00"));
        assertNull(DateTime.tryParse("2020-01-02X03:04"));
        assertNull(DateTime.tryParse(null));
    }

    @Test
    void goldenLiteralsWithCommasAndSpacedSeparators() {
        // docs/0281, docs/1283: datetime(2025, 6, 14); ',' is ignorable, so this is "y M d".
        assertEquals("06/14/2025 00:00:00", DateTime.tryParse("2025, 6, 14").toString());
        assertEquals("06/17/2025 00:00:00", DateTime.tryParse("2025, 6, 17").toString());
        // docs/1272: datetime(2017 - 08 - 01); white space may surround a date separator.
        assertEquals("08/01/2017 00:00:00", DateTime.tryParse("2017 - 08 - 01").toString());
        assertEquals("08/07/2017 00:00:00", DateTime.tryParse("2017 - 08 - 07").toString());
        assertEquals(DateTime.Kind.Unspecified, DateTime.tryParse("2025, 6, 14").kind());
    }

    @Test
    void lenientTokenForms() {
        assertEquals("06/01/2014 00:00:00", DateTime.tryParse("14 June").toString()); // d MMMM reads a year
        assertEquals("06/14/2025 15:04:00", DateTime.tryParse("15:04 2025-6-14").toString());
        assertEquals("01/02/2049 00:00:00", DateTime.tryParse("1/2/49").toString());
        assertEquals("01/02/2020 20:04:05", DateTime.tryParse("2020/1/2 3:04:05.123 PM -05").toString());
        assertNull(DateTime.tryParse("14/6/2025")); // MDY only
        assertNull(DateTime.tryParse("2025 1 2 3"));
        assertNull(DateTime.tryParse("2020-01-02 03:04:05 -08:00 GMT")); // two zones
    }

    @Test
    void fractionRoundsHalfEvenAndCarries() {
        assertEquals("01/02/2020 03:04:06", DateTime.tryParse("2020-01-02T03:04:05.99999999").toString());
        assertNull(DateTime.tryParse("9999-12-31T23:59:59.99999999"));
    }

    @Test
    void timeOnlyIsTodayUtc() {
        DateTime d = DateTime.tryParse("12:00");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertEquals(today.getYear(), d.year());
        assertEquals(today.getDayOfYear(), LocalDate.of(d.year(), d.month(), d.day()).getDayOfYear());
        assertEquals(12, d.hour());
    }
}
