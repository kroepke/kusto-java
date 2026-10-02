// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: .NET TimeSpan value (100 ns ticks) with parse, format and From* (PORTING.md 3.7, 5.2).

package org.graylog.kusto.language.utils.dotnet;

import java.util.ArrayList;
import java.util.List;

/** Immutable mirror of .NET {@code System.TimeSpan}: a signed count of 100 ns ticks. */
public final class TimeSpan implements Comparable<TimeSpan> {
    public static final long TICKS_PER_MILLISECOND = 10_000L;
    public static final long TICKS_PER_SECOND = 10_000_000L;
    public static final long TICKS_PER_MINUTE = 600_000_000L;
    public static final long TICKS_PER_HOUR = 36_000_000_000L;
    public static final long TICKS_PER_DAY = 864_000_000_000L;

    public static final TimeSpan ZERO = new TimeSpan(0L);
    public static final TimeSpan MIN_VALUE = new TimeSpan(Long.MIN_VALUE);
    public static final TimeSpan MAX_VALUE = new TimeSpan(Long.MAX_VALUE);

    private final long ticks;

    private TimeSpan(long ticks) {
        this.ticks = ticks;
    }

    /** .NET {@code TimeSpan.FromTicks(long)} / {@code new TimeSpan(long)}. */
    public static TimeSpan fromTicks(long ticks) {
        return ticks == 0 ? ZERO : new TimeSpan(ticks);
    }

    /** .NET {@code TimeSpan.FromMilliseconds(double)}; see {@link #fromSeconds(double)} for the rules. */
    public static TimeSpan fromMilliseconds(double value) {
        return interval(value, TICKS_PER_MILLISECOND);
    }

    /**
     * .NET {@code TimeSpan.FromSeconds(double)} (.NET Core 3.0+ {@code Interval}): ticks =
     * {@code (long)(value * TicksPerSecond)}, truncated toward zero with no millisecond rounding.
     *
     * @throws IllegalArgumentException (.NET {@code ArgumentException}) for NaN
     * @throws ArithmeticException (.NET {@code OverflowException}) when the ticks exceed the long range
     */
    public static TimeSpan fromSeconds(double value) {
        return interval(value, TICKS_PER_SECOND);
    }

    /** .NET {@code TimeSpan.FromMinutes(double)}; see {@link #fromSeconds(double)}. */
    public static TimeSpan fromMinutes(double value) {
        return interval(value, TICKS_PER_MINUTE);
    }

    /** .NET {@code TimeSpan.FromHours(double)}; see {@link #fromSeconds(double)}. */
    public static TimeSpan fromHours(double value) {
        return interval(value, TICKS_PER_HOUR);
    }

    /** .NET {@code TimeSpan.FromDays(double)}; see {@link #fromSeconds(double)}. */
    public static TimeSpan fromDays(double value) {
        return interval(value, TICKS_PER_DAY);
    }

    private static TimeSpan interval(double value, double scale) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("TimeSpan does not accept floating point Not-a-Number values.");
        }
        double ticks = value * scale;
        if (ticks > Long.MAX_VALUE || ticks < Long.MIN_VALUE || Double.isNaN(ticks)) {
            throw new ArithmeticException("TimeSpan overflowed because the duration is too long.");
        }
        if (ticks == Long.MAX_VALUE) {
            return MAX_VALUE;
        }
        return fromTicks((long) ticks);
    }

    public long ticks() {
        return ticks;
    }

    public int days() {
        return (int) (ticks / TICKS_PER_DAY);
    }

    public int hours() {
        return (int) (ticks / TICKS_PER_HOUR % 24);
    }

    public int minutes() {
        return (int) (ticks / TICKS_PER_MINUTE % 60);
    }

    public int seconds() {
        return (int) (ticks / TICKS_PER_SECOND % 60);
    }

    public int milliseconds() {
        return (int) (ticks / TICKS_PER_MILLISECOND % 1000);
    }

    public double totalDays() {
        return (double) ticks / TICKS_PER_DAY;
    }

    public double totalHours() {
        return (double) ticks / TICKS_PER_HOUR;
    }

    public double totalMinutes() {
        return (double) ticks / TICKS_PER_MINUTE;
    }

    public double totalSeconds() {
        return (double) ticks / TICKS_PER_SECOND;
    }

    public double totalMilliseconds() {
        return (double) ticks / TICKS_PER_MILLISECOND;
    }

    /** .NET {@code TimeSpan.Add}; throws {@link ArithmeticException} on overflow. */
    public TimeSpan add(TimeSpan other) {
        return fromTicks(checkedAdd(ticks, other.ticks));
    }

    /** .NET {@code TimeSpan.Subtract}; throws {@link ArithmeticException} on overflow. */
    public TimeSpan subtract(TimeSpan other) {
        long r = ticks - other.ticks;
        if (((ticks ^ other.ticks) & (ticks ^ r)) < 0) {
            throw overflow();
        }
        return fromTicks(r);
    }

    /** .NET {@code TimeSpan.Negate}; throws {@link ArithmeticException} for {@link #MIN_VALUE}. */
    public TimeSpan negate() {
        if (ticks == Long.MIN_VALUE) {
            throw new ArithmeticException("Negating the minimum value of a twos complement number is invalid.");
        }
        return fromTicks(-ticks);
    }

    private static long checkedAdd(long a, long b) {
        long r = a + b;
        if (((a ^ r) & (b ^ r)) < 0) {
            throw overflow();
        }
        return r;
    }

    private static ArithmeticException overflow() {
        return new ArithmeticException("TimeSpan overflowed because the duration is too long.");
    }

    @Override
    public int compareTo(TimeSpan o) {
        return Long.compare(ticks, o.ticks);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TimeSpan t && t.ticks == ticks;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(ticks);
    }

    /** .NET {@code TimeSpan.ToString()} ("c"): {@code [-][d.]hh:mm:ss[.fffffff]}, fraction only when non-zero. */
    @Override
    public String toString() {
        long day = ticks / TICKS_PER_DAY;
        long time = ticks % TICKS_PER_DAY;
        StringBuilder sb = new StringBuilder(26);
        if (ticks < 0) {
            sb.append('-');
            day = -day;
            time = -time;
        }
        if (day != 0) {
            sb.append(day).append('.');
        }
        pad(sb, time / TICKS_PER_HOUR % 24, 2).append(':');
        pad(sb, time / TICKS_PER_MINUTE % 60, 2).append(':');
        pad(sb, time / TICKS_PER_SECOND % 60, 2);
        long fraction = time % TICKS_PER_SECOND;
        if (fraction != 0) {
            pad(sb.append('.'), fraction, 7);
        }
        return sb.toString();
    }

    private static StringBuilder pad(StringBuilder sb, long v, int width) {
        String s = Long.toString(v);
        for (int i = s.length(); i < width; i++) {
            sb.append('0');
        }
        return sb.append(s);
    }

    // ---- parsing: mirrors System.Globalization.TimeSpanParse (TryParse, invariant culture) ----

    private static final int MAX_DAYS = 10_675_199;
    private static final long MAX_MILLISECONDS = Long.MAX_VALUE / TICKS_PER_MILLISECOND;
    private static final long MIN_MILLISECONDS = Long.MIN_VALUE / TICKS_PER_MILLISECOND;

    /** Literal pattern: start, day-hour, hour-minute, minute-second, second-fraction, end. */
    private record Pattern(String start, String dayHour, String hourMinute, String minuteSecond, String secondFraction,
            String end) {
        String appCompat() {
            return minuteSecond + secondFraction;
        }
    }

    /** "c" format literals. */
    private static final Pattern POSITIVE_INVARIANT = new Pattern("", ".", ":", ":", ".", "");
    private static final Pattern NEGATIVE_INVARIANT = new Pattern("-", ".", ":", ":", ".", "");
    /** Invariant {@code FullTimeSpanPositivePattern} {@code d':'h':'mm':'ss'.'FFFFFFF}. */
    private static final Pattern POSITIVE_LOCALIZED = new Pattern("", ":", ":", ":", ".", "");
    private static final Pattern NEGATIVE_LOCALIZED = new Pattern("-", ":", ":", ":", ".", "");
    private static final Pattern[] PATTERNS = {POSITIVE_INVARIANT, NEGATIVE_INVARIANT, POSITIVE_LOCALIZED, NEGATIVE_LOCALIZED};

    /** Number token: value and count of leading zeros (significant for fractions). */
    private record Num(int value, int zeroes) {
    }

    private static final Num ZERO_NUM = new Num(0, 0);

    /**
     * .NET {@code TimeSpan.TryParse(string)} under the invariant culture; null on failure.
     *
     * <p>Accepts {@code [ws][-]{ d | [d.]hh:mm[:ss[.fffffff]] | d:hh:mm:ss[.fffffff] }[ws]}: a bare
     * integer is days, hours 0-23, minutes and seconds 0-59, fractions of up to 7 digits (or longer
     * with leading zeros, rounded), overflow fails, {@code +} is rejected.
     */
    public static TimeSpan tryParse(String input) {
        if (input == null) {
            return null;
        }
        String s = DotNetStrings.trim(input);
        if (s.isEmpty()) {
            return null;
        }
        List<Num> nums = new ArrayList<>(5);
        List<String> seps = new ArrayList<>(6);
        int pos = 0;
        boolean lastWasNum = false;
        while (pos < s.length()) {
            char ch = s.charAt(pos);
            if (ch >= '0' && ch <= '9') {
                int zeroes = 0;
                while (pos < s.length() && s.charAt(pos) == '0') {
                    zeroes++;
                    pos++;
                }
                int num = 0;
                boolean any = false;
                while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') {
                    num = num * 10 + (s.charAt(pos) - '0');
                    any = true;
                    pos++;
                    if ((num & 0xF0000000) != 0) {
                        return null;
                    }
                }
                if (!any) {
                    // all zeros: the tokenizer counts them all as leading zeros of 0
                    num = 0;
                }
                if (seps.isEmpty()) {
                    seps.add("");
                }
                if (nums.size() >= 5 || nums.size() + seps.size() >= 11) {
                    return null;
                }
                nums.add(new Num(num, zeroes));
                lastWasNum = true;
            } else {
                int start = pos;
                while (pos < s.length() && (s.charAt(pos) < '0' || s.charAt(pos) > '9')) {
                    pos++;
                }
                if (seps.size() >= 6 || nums.size() + seps.size() >= 11) {
                    return null;
                }
                seps.add(s.substring(start, pos));
                lastWasNum = false;
            }
        }
        if (lastWasNum) {
            if (seps.size() >= 6) {
                return null;
            }
            seps.add("");
        }
        if (seps.size() != nums.size() + 1) {
            return null;
        }
        Long ticks = switch (nums.size()) {
            case 1 -> terminalD(nums, seps);
            case 2 -> terminalHM(nums, seps);
            case 3 -> terminalHMSD(nums, seps);
            case 4 -> terminalHMSFD(nums, seps);
            case 5 -> terminalDHMSF(nums, seps);
            default -> null;
        };
        return ticks == null ? null : fromTicks(ticks);
    }

    private static boolean match(List<String> seps, String... literals) {
        if (seps.size() != literals.length) {
            return false;
        }
        for (int i = 0; i < literals.length; i++) {
            if (!seps.get(i).equals(literals[i])) {
                return false;
            }
        }
        return true;
    }

    private static Long terminalD(List<Num> n, List<String> l) {
        for (Pattern p : PATTERNS) {
            if (match(l, p.start, p.end)) {
                return toTicks(p, n.get(0), ZERO_NUM, ZERO_NUM, ZERO_NUM, ZERO_NUM);
            }
        }
        return null;
    }

    private static Long terminalHM(List<Num> n, List<String> l) {
        for (Pattern p : PATTERNS) {
            if (match(l, p.start, p.hourMinute, p.end)) {
                Long t = toTicks(p, ZERO_NUM, n.get(0), n.get(1), ZERO_NUM, ZERO_NUM);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    private static Long terminalHMSD(List<Num> n, List<String> l) {
        for (Pattern p : PATTERNS) {
            Long t = null;
            if (match(l, p.start, p.hourMinute, p.minuteSecond, p.end)) {
                t = toTicks(p, ZERO_NUM, n.get(0), n.get(1), n.get(2), ZERO_NUM);
            }
            if (t == null && match(l, p.start, p.dayHour, p.hourMinute, p.end)) {
                t = toTicks(p, n.get(0), n.get(1), n.get(2), ZERO_NUM, ZERO_NUM);
            }
            if (t == null && match(l, p.start, p.hourMinute, p.appCompat(), p.end)) {
                t = toTicks(p, ZERO_NUM, n.get(0), n.get(1), ZERO_NUM, n.get(2));
            }
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static Long terminalHMSFD(List<Num> n, List<String> l) {
        for (Pattern p : PATTERNS) {
            Long t = null;
            if (match(l, p.start, p.hourMinute, p.minuteSecond, p.secondFraction, p.end)) {
                t = toTicks(p, ZERO_NUM, n.get(0), n.get(1), n.get(2), n.get(3));
            }
            if (t == null && match(l, p.start, p.dayHour, p.hourMinute, p.minuteSecond, p.end)) {
                t = toTicks(p, n.get(0), n.get(1), n.get(2), n.get(3), ZERO_NUM);
            }
            if (t == null && match(l, p.start, p.dayHour, p.hourMinute, p.appCompat(), p.end)) {
                t = toTicks(p, n.get(0), n.get(1), n.get(2), ZERO_NUM, n.get(3));
            }
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static Long terminalDHMSF(List<Num> n, List<String> l) {
        for (Pattern p : PATTERNS) {
            if (match(l, p.start, p.dayHour, p.hourMinute, p.minuteSecond, p.secondFraction, p.end)) {
                Long t = toTicks(p, n.get(0), n.get(1), n.get(2), n.get(3), n.get(4));
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    /** Mirrors {@code TimeSpanParse.TryTimeToTicks}. */
    private static Long toTicks(Pattern p, Num days, Num hours, Num minutes, Num seconds, Num fraction) {
        Long frac = normalizeFraction(fraction);
        if (days.value > MAX_DAYS || hours.value > 23 || minutes.value > 59 || seconds.value > 59 || frac == null) {
            return null;
        }
        long ms = ((long) days.value * 3600 * 24 + (long) hours.value * 3600 + (long) minutes.value * 60 + seconds.value)
                * 1000;
        if (ms > MAX_MILLISECONDS || ms < MIN_MILLISECONDS) {
            return null;
        }
        long t = ms * TICKS_PER_MILLISECOND;
        if (p.start.isEmpty()) {
            t += frac;
            if (t < 0) {
                return null;
            }
        } else {
            t = -t - frac;
            if (t > 0) {
                return null;
            }
        }
        return t;
    }

    /** Mirrors {@code TimeSpanToken.NormalizeAndValidateFraction}: scale to 7 digits; null when invalid. */
    private static Long normalizeFraction(Num f) {
        if (f.value == 0) {
            return 0L;
        }
        if (f.zeroes == 0 && f.value > 9_999_999) {
            return null;
        }
        int total = Integer.toString(f.value).length() + f.zeroes;
        if (total == 7) {
            return (long) f.value;
        }
        if (total < 7) {
            long v = f.value;
            for (int i = total; i < 7; i++) {
                v *= 10;
            }
            return v;
        }
        double q = f.value / Math.pow(10, total - 7);
        // Math.Round(q, MidpointRounding.AwayFromZero) for a non-negative q
        long whole = (long) q;
        return q - whole >= 0.5 ? whole + 1 : whole;
    }
}
