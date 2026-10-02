// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: .NET Int32/Int64/Double/Single TryParse and ToString (PORTING.md 5.2).

package org.graylog.kusto.language.utils.dotnet;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.function.Predicate;

/**
 * Invariant-culture number parsing and formatting with .NET semantics.
 *
 * <p>The scanner mirrors {@code Number.TryParseNumber}: white space is ASCII only (U+0009-000D,
 * U+0020), digits are ASCII only, the group separator is {@code ,} and the decimal separator
 * {@code .}; trailing NUL chars are accepted. Java parsing is only ever applied to a normalised
 * {@code [-]digits[.digits]e[-]digits} string, so Java-only forms ({@code 0x1p3}, {@code 1.5d})
 * never reach it.
 */
public final class DotNetNumber {
    /** .NET {@code double.NaN} bit pattern (0xFFF8000000000000). */
    public static final double NAN = Double.longBitsToDouble(0xFFF8000000000000L);
    /** .NET {@code float.NaN} bit pattern (0xFFC00000). */
    public static final float NAN_SINGLE = Float.intBitsToFloat(0xFFC00000);

    static final int LEADING_WHITE = 1;
    static final int TRAILING_WHITE = 2;
    static final int LEADING_SIGN = 4;
    static final int TRAILING_SIGN = 8;
    static final int DECIMAL_POINT = 0x20;
    static final int THOUSANDS = 0x40;
    static final int EXPONENT = 0x80;

    /** {@code NumberStyles.Integer}. */
    static final int INTEGER = LEADING_WHITE | TRAILING_WHITE | LEADING_SIGN;
    /** {@code NumberStyles.Float | NumberStyles.AllowThousands}. */
    static final int FLOAT_THOUSANDS = INTEGER | DECIMAL_POINT | EXPONENT | THOUSANDS;
    /** {@code NumberStyles.Number}. */
    static final int NUMBER = INTEGER | TRAILING_SIGN | DECIMAL_POINT | THOUSANDS;

    private static final int EXPONENT_LIMIT = 100_000_000;

    private DotNetNumber() {
    }

    /** Result of scanning a .NET number: sign, digits as typed (group separators removed), exponent. */
    static final class Scan {
        boolean negative;
        final StringBuilder intDigits = new StringBuilder();
        final StringBuilder fracDigits = new StringBuilder();
        boolean hasDecimalPoint;
        long exponent;
    }

    static boolean isWhite(char c) {
        return c == ' ' || (c >= 0x0009 && c <= 0x000D);
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** Mirrors {@code Number.TryParseNumber} plus the trailing-NUL rule; null on failure. */
    static Scan scan(String s, int styles) {
        if (s == null) {
            return null;
        }
        Scan r = new Scan();
        int n = s.length();
        int p = 0;
        boolean signSeen = false;
        while (p < n) {
            char ch = s.charAt(p);
            if (isWhite(ch) && (styles & LEADING_WHITE) != 0 && !signSeen) {
                p++;
            } else if ((styles & LEADING_SIGN) != 0 && !signSeen && (ch == '+' || ch == '-')) {
                signSeen = true;
                r.negative = ch == '-';
                p++;
            } else {
                break;
            }
        }
        boolean digits = false;
        while (p < n) {
            char ch = s.charAt(p);
            if (isAsciiDigit(ch)) {
                digits = true;
                (r.hasDecimalPoint ? r.fracDigits : r.intDigits).append(ch);
            } else if ((styles & DECIMAL_POINT) != 0 && !r.hasDecimalPoint && ch == '.') {
                r.hasDecimalPoint = true;
            } else if ((styles & THOUSANDS) != 0 && digits && !r.hasDecimalPoint && ch == ',') {
                // group separator: skipped anywhere after the first digit of the integer part
            } else {
                break;
            }
            p++;
        }
        if (!digits) {
            return null;
        }
        if ((styles & EXPONENT) != 0 && p < n && (s.charAt(p) == 'e' || s.charAt(p) == 'E')) {
            int q = p + 1;
            boolean negExp = false;
            if (q < n && (s.charAt(q) == '+' || s.charAt(q) == '-')) {
                negExp = s.charAt(q) == '-';
                q++;
            }
            if (q < n && isAsciiDigit(s.charAt(q))) {
                long exp = 0;
                while (q < n && isAsciiDigit(s.charAt(q))) {
                    if (exp < EXPONENT_LIMIT) {
                        exp = exp * 10 + (s.charAt(q) - '0');
                    }
                    q++;
                }
                r.exponent = negExp ? -exp : exp;
                p = q;
            }
        }
        while (p < n) {
            char ch = s.charAt(p);
            if (isWhite(ch) && (styles & TRAILING_WHITE) != 0) {
                p++;
            } else if ((styles & TRAILING_SIGN) != 0 && !signSeen && (ch == '+' || ch == '-')) {
                signSeen = true;
                r.negative = ch == '-';
                p++;
            } else {
                break;
            }
        }
        while (p < n) {
            if (s.charAt(p) != '\0') {
                return null;
            }
            p++;
        }
        return r;
    }

    /** .NET {@code Int32.TryParse(string)}; null on failure (including overflow). */
    public static Integer tryParseInt(String s) {
        Long v = parseInteger(s, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return v == null ? null : Integer.valueOf(v.intValue());
    }

    /** .NET {@code Int64.TryParse(string)}; null on failure (including overflow). */
    public static Long tryParseLong(String s) {
        return parseInteger(s, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    /** Upstream {@code Int32.TryParse(text, out result); return result;}: 0 on failure. */
    public static int parseIntOrZero(String s) {
        Integer v = tryParseInt(s);
        return v == null ? 0 : v;
    }

    /** Upstream {@code Int64.TryParse(text, out result); return result;}: 0 on failure. */
    public static long parseLongOrZero(String s) {
        Long v = tryParseLong(s);
        return v == null ? 0L : v;
    }

    /** True when {@code s} has valid {@code NumberStyles.Integer} syntax but is outside the long range. */
    static boolean isIntegerOverflow(String s) {
        Scan r = scan(s, INTEGER);
        return r != null && parseInteger(s, Long.MIN_VALUE, Long.MAX_VALUE) == null;
    }

    private static Long parseInteger(String s, long min, long max) {
        Scan r = scan(s, INTEGER);
        if (r == null) {
            return null;
        }
        StringBuilder d = r.intDigits;
        int i = 0;
        while (i < d.length() - 1 && d.charAt(i) == '0') {
            i++;
        }
        if (d.length() - i > 19) {
            return null;
        }
        // accumulate negatively so that long.MinValue fits
        long acc = 0;
        for (; i < d.length(); i++) {
            int digit = d.charAt(i) - '0';
            if (acc < (Long.MIN_VALUE + digit) / 10) {
                return null;
            }
            acc = acc * 10 - digit;
        }
        if (r.negative) {
            return acc < min ? null : acc;
        }
        if (acc == Long.MIN_VALUE || -acc > max) {
            return null;
        }
        return -acc;
    }

    /**
     * .NET {@code Double.TryParse(string)} ({@code NumberStyles.Float | AllowThousands}, invariant);
     * null on failure. Overflow gives infinity, underflow zero; {@code -0} keeps its sign.
     */
    public static Double tryParseDouble(String s) {
        Scan r = scan(s, FLOAT_THOUSANDS);
        if (r == null) {
            Integer sym = parseSymbol(s);
            if (sym == null) {
                return null;
            }
            return sym == 0 ? NAN : sym > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        return Double.parseDouble(normalise(r));
    }

    /** Upstream {@code Double.TryParse(text, out result); return result;}: 0 on failure. */
    public static double parseDoubleOrZero(String s) {
        Double v = tryParseDouble(s);
        return v == null ? 0.0 : v;
    }

    /** .NET {@code Single.TryParse(string)} with the same rules as {@link #tryParseDouble}; null on failure. */
    public static Float tryParseFloat(String s) {
        Scan r = scan(s, FLOAT_THOUSANDS);
        if (r == null) {
            Integer sym = parseSymbol(s);
            if (sym == null) {
                return null;
            }
            return sym == 0 ? NAN_SINGLE : sym > 0 ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        }
        return Float.parseFloat(normalise(r));
    }

    /** {@code [-]digits[.digits]e[-]digits}: the only text Java's parser ever sees. */
    private static String normalise(Scan r) {
        StringBuilder sb = new StringBuilder(r.intDigits.length() + r.fracDigits.length() + 16);
        if (r.negative) {
            sb.append('-');
        }
        sb.append(r.intDigits.length() == 0 ? "0" : r.intDigits);
        if (r.fracDigits.length() > 0) {
            sb.append('.').append(r.fracDigits);
        }
        return sb.append('e').append(r.exponent).toString();
    }

    /**
     * Mirrors the .NET fallback for {@code Infinity}, {@code -Infinity}, {@code NaN} (also with a
     * leading {@code +}, and {@code -NaN}), compared ordinal-ignore-case after trimming.
     * Returns 1, -1, 0 for +inf, -inf, NaN; null when not a symbol.
     */
    private static Integer parseSymbol(String s) {
        if (s == null) {
            return null;
        }
        String v = DotNetStrings.trim(s);
        if (DotNetStrings.equalsOrdinalIgnoreCase(v, "Infinity")
                || DotNetStrings.equalsOrdinalIgnoreCase(v, "+Infinity")) {
            return 1;
        }
        if (DotNetStrings.equalsOrdinalIgnoreCase(v, "-Infinity")) {
            return -1;
        }
        if (DotNetStrings.equalsOrdinalIgnoreCase(v, "NaN")
                || DotNetStrings.equalsOrdinalIgnoreCase(v, "+NaN")
                || DotNetStrings.equalsOrdinalIgnoreCase(v, "-NaN")) {
            return 0;
        }
        return null;
    }

    /**
     * .NET Core 3.0+ {@code double.ToString()} (invariant): shortest round-trip digits, scientific
     * notation ({@code E+XX}, at least two exponent digits) when the decimal exponent exceeds 17 or is
     * below -3 (i.e. {@code x >= 1e17} or {@code x < 1e-4}); {@code -0}, {@code NaN}, {@code Infinity}.
     */
    public static String toString(double x) {
        if (Double.isNaN(x)) {
            return "NaN";
        }
        if (Double.isInfinite(x)) {
            return x > 0 ? "Infinity" : "-Infinity";
        }
        if (x == 0) {
            return (Double.doubleToRawLongBits(x) < 0) ? "-0" : "0";
        }
        double a = Math.abs(x);
        BigDecimal shortest = new BigDecimal(Double.toString(a)).stripTrailingZeros();
        if (shortest.precision() == 2) {
            BigDecimal one = oneDigit(new BigDecimal(a), c -> Double.parseDouble(c.toString()) == a);
            if (one != null) {
                shortest = one;
            }
        }
        return format(x < 0, shortest, 17);
    }

    /** .NET Core 3.0+ {@code float.ToString()} (invariant); as {@link #toString(double)} with a switch at 9 digits. */
    public static String toString(float x) {
        if (Float.isNaN(x)) {
            return "NaN";
        }
        if (Float.isInfinite(x)) {
            return x > 0 ? "Infinity" : "-Infinity";
        }
        if (x == 0) {
            return (Float.floatToRawIntBits(x) < 0) ? "-0" : "0";
        }
        float a = Math.abs(x);
        BigDecimal shortest = new BigDecimal(Float.toString(a)).stripTrailingZeros();
        if (shortest.precision() == 2) {
            BigDecimal one = oneDigit(new BigDecimal(a), c -> Float.parseFloat(c.toString()) == a);
            if (one != null) {
                shortest = one;
            }
        }
        return format(x < 0, shortest, 9);
    }

    /**
     * Java's {@code toString} never prints a single significant digit (it uses two when one would
     * do, e.g. {@code 4.9E-324}); .NET does. Returns the closest one-digit decimal that round-trips.
     */
    private static BigDecimal oneDigit(BigDecimal exact, Predicate<BigDecimal> roundTrips) {
        BigDecimal best = null;
        for (RoundingMode mode : new RoundingMode[] {RoundingMode.HALF_EVEN, RoundingMode.FLOOR, RoundingMode.CEILING}) {
            BigDecimal c = exact.round(new MathContext(1, mode));
            if (c.signum() != 0 && roundTrips.test(c)) {
                if (best == null || c.subtract(exact).abs().compareTo(best.subtract(exact).abs()) < 0) {
                    best = c;
                }
            }
        }
        return best == null ? null : best.stripTrailingZeros();
    }

    /** .NET {@code FormatGeneral} for the default ('R'-equivalent) precision. */
    private static String format(boolean negative, BigDecimal value, int maxRoundTripDigits) {
        String digits = value.unscaledValue().toString();
        int scale = digits.length() - value.scale(); // value = 0.digits * 10^scale
        StringBuilder sb = new StringBuilder(32);
        if (negative) {
            sb.append('-');
        }
        if (scale > Math.max(digits.length(), maxRoundTripDigits) || scale < -3) {
            sb.append(digits.charAt(0));
            if (digits.length() > 1) {
                sb.append('.').append(digits, 1, digits.length());
            }
            int exp = scale - 1;
            sb.append('E').append(exp < 0 ? '-' : '+');
            int abs = Math.abs(exp);
            if (abs < 10) {
                sb.append('0');
            }
            sb.append(abs);
        } else if (scale <= 0) {
            sb.append("0.");
            for (int i = 0; i < -scale; i++) {
                sb.append('0');
            }
            sb.append(digits);
        } else if (scale >= digits.length()) {
            sb.append(digits);
            for (int i = digits.length(); i < scale; i++) {
                sb.append('0');
            }
        } else {
            sb.append(digits, 0, scale).append('.').append(digits, scale, digits.length());
        }
        return sb.toString();
    }
}
