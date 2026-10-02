// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: .NET DateTime value (100 ns ticks since 0001-01-01) with parse and format (PORTING.md 3.7, 5.2).

package org.graylog.kusto.language.utils.dotnet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Immutable mirror of .NET {@code System.DateTime}: ticks since 0001-01-01T00:00 in the proleptic
 * Gregorian calendar plus a {@link Kind}. Equality and ordering use the ticks only, as in .NET.
 */
public final class DateTime implements Comparable<DateTime> {
    /** .NET {@code DateTimeKind}. */
    public enum Kind {
        Unspecified,
        Utc,
        Local
    }

    private static final long TICKS_PER_DAY = TimeSpan.TICKS_PER_DAY;
    private static final long MAX_TICKS = 3_155_378_975_999_999_999L;
    /** {@code LocalDate.of(1, 1, 1).toEpochDay()}. */
    private static final long EPOCH_DAY_0001 = -719_162L;

    public static final DateTime MIN_VALUE = new DateTime(0L, Kind.Unspecified);
    public static final DateTime MAX_VALUE = new DateTime(MAX_TICKS, Kind.Unspecified);

    private final long ticks;
    private final Kind kind;

    private DateTime(long ticks, Kind kind) {
        this.ticks = ticks;
        this.kind = kind;
    }

    /**
     * .NET {@code new DateTime(ticks, kind)}.
     *
     * @throws IndexOutOfBoundsException (.NET {@code ArgumentOutOfRangeException}) outside 0..MaxValue
     */
    public static DateTime ofTicks(long ticks, Kind kind) {
        if (ticks < 0 || ticks > MAX_TICKS) {
            throw new IndexOutOfBoundsException("Ticks must be between DateTime.MinValue.Ticks and DateTime.MaxValue.Ticks.");
        }
        return new DateTime(ticks, kind);
    }

    public long ticks() {
        return ticks;
    }

    public Kind kind() {
        return kind;
    }

    private LocalDateTime local() {
        long day = ticks / TICKS_PER_DAY;
        long nanoOfDay = ticks % TICKS_PER_DAY * 100;
        return LocalDate.ofEpochDay(day + EPOCH_DAY_0001).atStartOfDay().plusNanos(nanoOfDay);
    }

    public int year() {
        return local().getYear();
    }

    public int month() {
        return local().getMonthValue();
    }

    public int day() {
        return local().getDayOfMonth();
    }

    public int hour() {
        return (int) (ticks / TimeSpan.TICKS_PER_HOUR % 24);
    }

    public int minute() {
        return (int) (ticks / TimeSpan.TICKS_PER_MINUTE % 60);
    }

    public int second() {
        return (int) (ticks / TimeSpan.TICKS_PER_SECOND % 60);
    }

    public int millisecond() {
        return (int) (ticks / TimeSpan.TICKS_PER_MILLISECOND % 1000);
    }

    /** .NET {@code DayOfWeek}: 0 = Sunday .. 6 = Saturday. */
    public int dayOfWeek() {
        return (int) ((ticks / TICKS_PER_DAY + 1) % 7);
    }

    @Override
    public int compareTo(DateTime o) {
        return Long.compare(ticks, o.ticks);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DateTime d && d.ticks == ticks;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(ticks);
    }

    /** .NET {@code DateTime.ToString()} under the invariant culture: {@code MM/dd/yyyy HH:mm:ss}. */
    @Override
    public String toString() {
        LocalDateTime t = local();
        StringBuilder sb = new StringBuilder(19);
        pad(sb, t.getMonthValue(), 2).append('/');
        pad(sb, t.getDayOfMonth(), 2).append('/');
        pad(sb, t.getYear(), 4).append(' ');
        pad(sb, t.getHour(), 2).append(':');
        pad(sb, t.getMinute(), 2).append(':');
        pad(sb, t.getSecond(), 2);
        return sb.toString();
    }

    private static StringBuilder pad(StringBuilder sb, int v, int width) {
        String s = Integer.toString(v);
        for (int i = s.length(); i < width; i++) {
            sb.append('0');
        }
        return sb.append(s);
    }

    // ---- parsing ----

    private static final String[] MONTHS = {
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    };
    private static final String[] DAYS = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    /**
     * .NET {@code DateTime.TryParse(string)} under the invariant culture, for the forms upstream
     * literals use; null on failure. Supported (case-insensitive names, .NET white space trimmed):
     * <ul>
     *   <li>{@code yyyy-M-d} / {@code yyyy/M/d}, {@code M/d/yyyy} / {@code M-d-yyyy},
     *       {@code MMM d[,] yyyy}, {@code d MMM yyyy}, each with an optional leading day name
     *       ({@code Thu, }) that must match the date;</li>
     *   <li>an optional time after {@code T} (ISO form only) or white space:
     *       {@code H:mm[:ss[.f...]]} with optional {@code AM}/{@code PM}; a time alone means today (UTC);</li>
     *   <li>an optional zone after the time: {@code Z}, {@code GMT}, {@code ±HH[:mm]}, {@code ±HHmm}.</li>
     * </ul>
     * Fractions of any length are summed as a double digit by digit and rounded half to even to
     * ticks, as .NET's {@code ParseFraction} does ({@code .12345678} gives 1234568 ticks).
     * A zone converts the value to UTC and the result has {@link Kind#Utc} (D11; .NET returns
     * {@code Local}, which equals UTC on the oracle). As in .NET's {@code AdjustTimeZoneToUniversal},
     * a negative UTC result gets one day added ({@code 0001-01-01T00:00:00+01:00} gives
     * 0001-01-01 23:00), and a result past {@code MaxValue} fails.
     */
    public static DateTime tryParse(String input) {
        if (input == null) {
            return null;
        }
        String s = DotNetStrings.trim(input);
        if (s.isEmpty()) {
            return null;
        }
        return new Parser(s).parse();
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        private boolean atEnd() {
            return pos >= s.length();
        }

        private char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        private boolean isDigit(int i) {
            return i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9';
        }

        private boolean isAsciiLetter(int i) {
            if (i >= s.length()) {
                return false;
            }
            char c = s.charAt(i);
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }

        private boolean skipWhite() {
            int start = pos;
            while (!atEnd() && DotNetChars.isWhiteSpace(peek())) {
                pos++;
            }
            return pos > start;
        }

        /** Reads 1..maxDigits ASCII digits; returns -1 (and leaves pos) when none or too many. */
        private int number(int minDigits, int maxDigits) {
            int start = pos;
            int end = pos;
            while (isDigit(end)) {
                end++;
            }
            int len = end - start;
            if (len < minDigits || len > maxDigits) {
                return -1;
            }
            pos = end;
            return Integer.parseInt(s, start, end, 10);
        }

        private String word() {
            int start = pos;
            int end = pos;
            while (isAsciiLetter(end)) {
                end++;
            }
            return s.substring(start, end);
        }

        private static int nameIndex(String w, String[] names) {
            if (w.length() < 3) {
                return -1;
            }
            for (int i = 0; i < names.length; i++) {
                if (DotNetStrings.equalsOrdinalIgnoreCase(w, names[i])
                        || DotNetStrings.equalsOrdinalIgnoreCase(w, names[i].substring(0, 3))) {
                    return i;
                }
            }
            return -1;
        }

        DateTime parse() {
            int dayOfWeek = -1;
            String w = word();
            int d = nameIndex(w, DAYS);
            if (d >= 0) {
                dayOfWeek = d;
                pos += w.length();
                if (peek() == ',') {
                    pos++;
                }
                if (!skipWhite()) {
                    return null;
                }
            }
            int year;
            int month;
            int day;
            boolean iso = false;
            boolean timeOnly = false;
            int save = pos;
            int n1Start = pos;
            int n1 = number(1, 9);
            if (n1 >= 0 && peek() == ':') {
                pos = save;
                timeOnly = true;
                LocalDate today = LocalDate.now(ZoneOffset.UTC);
                year = today.getYear();
                month = today.getMonthValue();
                day = today.getDayOfMonth();
            } else if (n1 >= 0) {
                int digits = pos - n1Start;
                char sep = peek();
                if (digits == 4 && (sep == '-' || sep == '/')) {
                    pos++;
                    month = number(1, 2);
                    if (month < 0 || peek() != sep) {
                        return null;
                    }
                    pos++;
                    day = number(1, 2);
                    year = n1;
                    iso = sep == '-';
                } else if (digits <= 2 && (sep == '-' || sep == '/')) {
                    pos++;
                    day = number(1, 2);
                    if (day < 0 || peek() != sep) {
                        return null;
                    }
                    pos++;
                    year = number(4, 4);
                    month = n1;
                } else if (digits <= 2 && skipWhite()) {
                    String m = word();
                    month = nameIndex(m, MONTHS) + 1;
                    if (month == 0) {
                        return null;
                    }
                    pos += m.length();
                    if (!skipWhite()) {
                        return null;
                    }
                    year = number(4, 4);
                    day = n1;
                } else {
                    return null;
                }
            } else {
                String m = word();
                month = nameIndex(m, MONTHS) + 1;
                if (month == 0) {
                    return null;
                }
                pos += m.length();
                if (!skipWhite()) {
                    return null;
                }
                day = number(1, 2);
                if (peek() == ',') {
                    pos++;
                }
                if (!skipWhite()) {
                    return null;
                }
                year = number(4, 4);
            }
            if (year < 1 || year > 9999 || month < 1 || month > 12 || day < 1
                    || day > LocalDate.of(year, month, 1).lengthOfMonth()) {
                return null;
            }
            long ticks = (LocalDate.of(year, month, day).toEpochDay() - EPOCH_DAY_0001) * TICKS_PER_DAY;
            if (dayOfWeek >= 0 && (ticks / TICKS_PER_DAY + 1) % 7 != dayOfWeek) {
                return null;
            }
            if (atEnd()) {
                return timeOnly ? null : new DateTime(ticks, Kind.Unspecified);
            }
            if (!timeOnly) {
                if (iso && (peek() == 'T' || peek() == 't')) {
                    pos++;
                } else if (!skipWhite()) {
                    return null;
                }
            }
            long time = time();
            if (time < 0) {
                return null;
            }
            ticks += time;
            if (ticks > MAX_TICKS) {
                return null;
            }
            boolean hadWhite = skipWhite();
            if (atEnd()) {
                return new DateTime(ticks, Kind.Unspecified);
            }
            long offset;
            char c = peek();
            if ((c == 'Z' || c == 'z') && pos + 1 == s.length()) {
                pos++;
                offset = 0;
            } else if (hadWhite && s.length() - pos == 3 && DotNetStrings.equalsOrdinalIgnoreCase(s.substring(pos), "GMT")) {
                pos += 3;
                offset = 0;
            } else if (c == '+' || c == '-') {
                pos++;
                offset = offset();
                if (offset < 0) {
                    return null;
                }
                if (c == '-') {
                    offset = -offset;
                }
            } else {
                return null;
            }
            if (!atEnd()) {
                return null;
            }
            long utc = ticks - offset;
            if (utc < 0) {
                utc += TICKS_PER_DAY;
            }
            if (utc < 0 || utc > MAX_TICKS) {
                return null;
            }
            return new DateTime(utc, Kind.Utc);
        }

        /** {@code H:mm[:ss[.f...]][ws AM|PM]} as ticks into the day; -1 on failure. */
        private long time() {
            int h = number(1, 2);
            if (h < 0 || peek() != ':') {
                return -1;
            }
            pos++;
            int m = number(1, 2);
            if (m < 0 || m > 59) {
                return -1;
            }
            int sec = 0;
            long fractionTicks = 0;
            if (peek() == ':') {
                pos++;
                sec = number(1, 2);
                if (sec < 0 || sec > 59) {
                    return -1;
                }
                if (peek() == '.' && isDigit(pos + 1)) {
                    pos++;
                    double fraction = 0;
                    double base = 0.1;
                    while (isDigit(pos)) {
                        fraction += (s.charAt(pos) - '0') * base;
                        base *= 0.1;
                        pos++;
                    }
                    fractionTicks = (long) Math.rint(fraction * TimeSpan.TICKS_PER_SECOND);
                }
            }
            int save = pos;
            skipWhite();
            String w = word();
            if (w.length() == 2 && (DotNetStrings.equalsOrdinalIgnoreCase(w, "AM") || DotNetStrings.equalsOrdinalIgnoreCase(w, "PM"))) {
                if (h < 1 || h > 12) {
                    return -1;
                }
                pos += 2;
                h = h % 12 + (DotNetStrings.equalsOrdinalIgnoreCase(w, "PM") ? 12 : 0);
            } else {
                pos = save;
            }
            if (h > 23) {
                return -1;
            }
            return h * TimeSpan.TICKS_PER_HOUR + m * TimeSpan.TICKS_PER_MINUTE + sec * TimeSpan.TICKS_PER_SECOND
                    + fractionTicks;
        }

        /** {@code HH[:mm]} or {@code HHmm} after the sign, as ticks; -1 on failure. */
        private long offset() {
            int start = pos;
            int hh = number(1, 4);
            if (hh < 0) {
                return -1;
            }
            int mm = 0;
            int len = pos - start;
            if (len == 4 || len == 3) {
                mm = hh % 100;
                hh = hh / 100;
            } else if (peek() == ':') {
                pos++;
                mm = number(2, 2);
                if (mm < 0) {
                    return -1;
                }
            }
            if (hh > 14 || mm > 59) {
                return -1;
            }
            return hh * TimeSpan.TICKS_PER_HOUR + mm * TimeSpan.TICKS_PER_MINUTE;
        }
    }
}
