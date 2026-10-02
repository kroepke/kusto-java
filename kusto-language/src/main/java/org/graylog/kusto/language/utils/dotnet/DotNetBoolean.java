// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: .NET bool.TryParse and bool.ToString (PORTING.md 5.2).

package org.graylog.kusto.language.utils.dotnet;

/** .NET {@code System.Boolean} parse and format. */
public final class DotNetBoolean {
    private DotNetBoolean() {
    }

    /**
     * .NET {@code bool.TryParse}: {@code true}/{@code false} in any ASCII case; when that fails and the
     * input has at least 5 chars, retries after trimming .NET white space and NUL from both ends.
     * Returns null on failure ({@code 1}, {@code yes}, {@code t} fail).
     */
    public static Boolean tryParse(String s) {
        if (s == null) {
            return null;
        }
        Boolean v = match(s);
        if (v != null || s.length() < 5) {
            return v;
        }
        int start = 0;
        int end = s.length();
        while (start < end && isWhiteSpaceOrNull(s.charAt(start))) {
            start++;
        }
        while (end > start && isWhiteSpaceOrNull(s.charAt(end - 1))) {
            end--;
        }
        return end - start == s.length() ? null : match(s.substring(start, end));
    }

    /** .NET {@code bool.ToString()}: {@code True} / {@code False}. */
    public static String toString(boolean b) {
        return b ? "True" : "False";
    }

    private static boolean isWhiteSpaceOrNull(char c) {
        return c == '\0' || DotNetChars.isWhiteSpace(c);
    }

    private static Boolean match(String s) {
        if (s.length() == 4
                && (s.charAt(0) | 0x20) == 't'
                && (s.charAt(1) | 0x20) == 'r'
                && (s.charAt(2) | 0x20) == 'u'
                && (s.charAt(3) | 0x20) == 'e') {
            return Boolean.TRUE;
        }
        if (s.length() == 5
                && (s.charAt(0) | 0x20) == 'f'
                && (s.charAt(1) | 0x20) == 'a'
                && (s.charAt(2) | 0x20) == 'l'
                && (s.charAt(3) | 0x20) == 's'
                && (s.charAt(4) | 0x20) == 'e') {
            return Boolean.FALSE;
        }
        return null;
    }
}
