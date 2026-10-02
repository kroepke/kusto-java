// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: .NET decimal parse, range and ToString over BigDecimal (PORTING.md 3.7, 5.2).

package org.graylog.kusto.language.utils.dotnet;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * .NET {@code System.Decimal} semantics on {@link BigDecimal}: 96-bit unsigned mantissa, scale 0-28.
 *
 * <p>Parsing mirrors {@code Number.TryNumberToDecimal}: digits are taken while the mantissa fits in
 * 96 bits and the scale stays at most 28; the next digit rounds half to even (a 5 followed only by
 * zeros rounds to even, anything else after a 5 rounds up). Typed trailing zeros are kept
 * ({@code 1.50} has scale 2), as .NET keeps them.
 */
public final class DotNetDecimal {
    private static final BigInteger MAX_MANTISSA = BigInteger.ONE.shiftLeft(96).subtract(BigInteger.ONE);
    /** {@code 2^96 / 10} rounded, the .NET re-scale value used when rounding overflows 96 bits. */
    private static final BigInteger ROUND_OVERFLOW_MANTISSA = new BigInteger("7922816251426433759354395034");
    private static final int MAX_SCALE = 28;
    private static final int PRECISION = 29;

    /** .NET {@code decimal.MaxValue}: 79228162514264337593543950335. */
    public static final BigDecimal MAX_VALUE = new BigDecimal(MAX_MANTISSA);
    /** .NET {@code decimal.MinValue}: -79228162514264337593543950335. */
    public static final BigDecimal MIN_VALUE = MAX_VALUE.negate();

    private DotNetDecimal() {
    }

    /** .NET {@code Decimal.TryParse(string)} ({@code NumberStyles.Number}, invariant; no exponent); null on failure. */
    public static BigDecimal tryParse(String s) {
        return tryParse(s, false);
    }

    /** As {@link #tryParse(String)}; {@code allowExponent} adds {@code NumberStyles.AllowExponent}. */
    public static BigDecimal tryParse(String s, boolean allowExponent) {
        DotNetNumber.Scan r = DotNetNumber.scan(s, allowExponent
                ? DotNetNumber.NUMBER | DotNetNumber.EXPONENT
                : DotNetNumber.NUMBER);
        if (r == null) {
            return null;
        }
        // NumberBuffer: significant digits (leading zeros dropped, typed trailing zeros kept) and Scale.
        StringBuilder digits = new StringBuilder();
        long scale = 0;
        boolean nonZero = false;
        for (int i = 0; i < r.intDigits.length(); i++) {
            char c = r.intDigits.charAt(i);
            if (c != '0' || nonZero) {
                nonZero = true;
                digits.append(c);
                scale++;
            }
        }
        for (int i = 0; i < r.fracDigits.length(); i++) {
            char c = r.fracDigits.charAt(i);
            if (c != '0' || nonZero) {
                nonZero = true;
                digits.append(c);
            } else {
                scale--;
            }
        }
        scale += r.exponent;
        if (!nonZero) {
            long zeroScale = Math.max(0, Math.min(MAX_SCALE, -scale));
            return BigDecimal.valueOf(0, (int) zeroScale);
        }
        return toDecimal(r.negative, digits, scale);
    }

    /** Mirrors {@code Number.TryNumberToDecimal}. */
    private static BigDecimal toDecimal(boolean negative, CharSequence digits, long scale) {
        if (scale > PRECISION) {
            return null;
        }
        long e = scale;
        BigInteger m = BigInteger.ZERO;
        int p = 0;
        while (e > 0 || (p < digits.length() && e > -MAX_SCALE)) {
            int c = p < digits.length() ? digits.charAt(p) - '0' : 0;
            BigInteger next = m.multiply(BigInteger.TEN).add(BigInteger.valueOf(c));
            if (next.compareTo(MAX_MANTISSA) > 0) {
                break;
            }
            m = next;
            p++;
            e--;
        }
        if (p < digits.length() && digits.charAt(p) >= '5') {
            boolean round = true;
            if (digits.charAt(p) == '5' && !m.testBit(0)) {
                round = false;
                for (int q = p + 1; q < digits.length(); q++) {
                    if (digits.charAt(q) != '0') {
                        round = true;
                        break;
                    }
                }
            }
            if (round) {
                m = m.add(BigInteger.ONE);
                if (m.compareTo(MAX_MANTISSA) > 0) {
                    m = ROUND_OVERFLOW_MANTISSA;
                    e++;
                }
            }
        }
        if (e > 0) {
            return null;
        }
        if (e <= -PRECISION) {
            return BigDecimal.valueOf(0, MAX_SCALE);
        }
        BigDecimal v = new BigDecimal(m, (int) -e);
        return negative ? v.negate() : v;
    }

    /** Upstream {@code Decimal.TryParse(text, out result); return result;}: 0 on failure. */
    public static BigDecimal parseOrZero(String s) {
        BigDecimal v = tryParse(s);
        return v == null ? BigDecimal.ZERO : v;
    }

    /** True when {@code v} is a valid .NET decimal: |mantissa| below 2^96 and scale 0-28. */
    public static boolean isInRange(BigDecimal v) {
        return v.scale() >= 0 && v.scale() <= MAX_SCALE && v.unscaledValue().abs().compareTo(MAX_MANTISSA) <= 0;
    }

    /** .NET {@code decimal.ToString()} (invariant): plain notation keeping the scale, never an exponent. */
    public static String toString(BigDecimal v) {
        return v.toPlainString();
    }
}
