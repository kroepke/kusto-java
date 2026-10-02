// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: .NET Guid.TryParse (N, D, B, P, X) and ToString over UUID (PORTING.md 5.2).

package org.graylog.kusto.language.utils.dotnet;

import java.util.UUID;

/**
 * .NET {@code System.Guid} parse and format on {@link UUID}. Never uses {@code UUID.fromString},
 * which accepts malformed input. Mirrors {@code Guid.TryParse} including its legacy quirks: in the
 * N and D formats a component may start with {@code +} and/or {@code 0x} as long as the component
 * keeps its fixed width, and the X format ignores all white space.
 */
public final class DotNetGuid {
    /** .NET {@code Guid.Empty}. */
    public static final UUID EMPTY = new UUID(0L, 0L);

    private DotNetGuid() {
    }

    /** .NET {@code Guid.TryParse(string)}; null on failure. */
    public static UUID tryParse(String input) {
        if (input == null) {
            return null;
        }
        String s = DotNetStrings.trim(input);
        if (s.isEmpty()) {
            return null;
        }
        switch (s.charAt(0)) {
            case '(':
                return parseP(s);
            case '{':
                return s.indexOf('-') >= 0 ? parseB(s) : parseX(s);
            default:
                return s.indexOf('-') >= 0 ? parseD(s) : parseN(s);
        }
    }

    /** .NET {@code Guid.ToString()} ("D", lower case); identical to {@link UUID#toString()}. */
    public static String toString(UUID g) {
        return g.toString();
    }

    private static UUID parseB(String s) {
        if (s.length() != 38 || s.charAt(0) != '{' || s.charAt(37) != '}') {
            return null;
        }
        return parseD(s.substring(1, 37));
    }

    private static UUID parseP(String s) {
        if (s.length() != 38 || s.charAt(0) != '(' || s.charAt(37) != ')') {
            return null;
        }
        return parseD(s.substring(1, 37));
    }

    private static UUID parseD(String s) {
        if (s.length() != 36 || s.charAt(8) != '-' || s.charAt(13) != '-' || s.charAt(18) != '-' || s.charAt(23) != '-') {
            return null;
        }
        long a = hex(s, 0, 8);
        long b = hex(s, 9, 13);
        long c = hex(s, 14, 18);
        long de = hex(s, 19, 23);
        long fg = hex(s, 24, 28);
        long hk = hex(s, 28, 36);
        if (a < 0 || b < 0 || c < 0 || de < 0 || fg < 0 || hk < 0) {
            return null;
        }
        return new UUID(a << 32 | b << 16 | c, de << 48 | fg << 32 | hk);
    }

    private static UUID parseN(String s) {
        if (s.length() != 32) {
            return null;
        }
        long a = hex(s, 0, 8);
        long bc = hex(s, 8, 16);
        long dg = hex(s, 16, 24);
        long hk = hex(s, 24, 32);
        if (a < 0 || bc < 0 || dg < 0 || hk < 0) {
            return null;
        }
        return new UUID(a << 32 | bc, dg << 32 | hk);
    }

    /** {@code {0xdddddddd,0xdddd,0xdddd,{0xdd,0xdd,0xdd,0xdd,0xdd,0xdd,0xdd,0xdd}}}, white space ignored. */
    private static UUID parseX(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (!DotNetChars.isWhiteSpace(ch)) {
                sb.append(ch);
            }
        }
        String s = sb.toString();
        if (s.isEmpty() || s.charAt(0) != '{' || !isHexPrefix(s, 1)) {
            return null;
        }
        int numStart = 3;
        int numLen = indexFrom(s, ',', numStart);
        if (numLen <= 0) {
            return null;
        }
        long a = hex(s, numStart, numStart + numLen);
        if (a < 0 || a > 0xFFFFFFFFL) {
            return null;
        }
        long[] bc = new long[2];
        for (int k = 0; k < 2; k++) {
            if (!isHexPrefix(s, numStart + numLen + 1)) {
                return null;
            }
            numStart = numStart + numLen + 3;
            numLen = indexFrom(s, ',', numStart);
            if (numLen <= 0) {
                return null;
            }
            bc[k] = hex(s, numStart, numStart + numLen);
            if (bc[k] < 0 || bc[k] > 0xFFFF) {
                return null;
            }
        }
        if (s.length() <= numStart + numLen + 1 || s.charAt(numStart + numLen + 1) != '{') {
            return null;
        }
        numLen++;
        long low = 0;
        for (int i = 0; i < 8; i++) {
            if (!isHexPrefix(s, numStart + numLen + 1)) {
                return null;
            }
            numStart = numStart + numLen + 3;
            numLen = indexFrom(s, i < 7 ? ',' : '}', numStart);
            if (numLen <= 0) {
                return null;
            }
            long v = hex(s, numStart, numStart + numLen);
            if (v < 0 || v > 0xFF) {
                return null;
            }
            low = low << 8 | v;
        }
        if (numStart + numLen + 1 >= s.length() || s.charAt(numStart + numLen + 1) != '}') {
            return null;
        }
        if (numStart + numLen + 1 != s.length() - 1) {
            return null;
        }
        return new UUID(a << 32 | bc[0] << 16 | bc[1], low);
    }

    private static boolean isHexPrefix(String s, int i) {
        return i + 1 < s.length() && s.charAt(i) == '0' && (s.charAt(i + 1) | 0x20) == 'x';
    }

    private static int indexFrom(String s, char c, int from) {
        int i = s.indexOf(c, from);
        return i < 0 ? -1 : i - from;
    }

    /**
     * Mirrors .NET {@code Guid.TryParseHex}: optional {@code +}, optional {@code 0x}/{@code 0X}, leading
     * zeros skipped, at most 8 significant hex digits. Returns -1 on failure or overflow.
     */
    private static long hex(String s, int start, int end) {
        int i = start;
        if (i < end && s.charAt(i) == '+') {
            i++;
        }
        if (end - i > 1 && s.charAt(i) == '0' && (s.charAt(i + 1) | 0x20) == 'x') {
            i += 2;
        }
        while (i < end && s.charAt(i) == '0') {
            i++;
        }
        if (end - i > 8) {
            return -1;
        }
        long v = 0;
        for (; i < end; i++) {
            int d = Character.digit(s.charAt(i), 16);
            if (d < 0 || s.charAt(i) > 'f') {
                return -1;
            }
            v = v << 4 | d;
        }
        return v;
    }
}
