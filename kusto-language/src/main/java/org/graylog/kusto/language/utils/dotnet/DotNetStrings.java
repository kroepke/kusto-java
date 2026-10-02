// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: .NET string helpers under invariant globalization (PORTING.md 5.1, 5.4).

package org.graylog.kusto.language.utils.dotnet;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors {@code System.String} members whose Java counterparts differ.
 *
 * <p>Comparisons are ordinal: upstream's culture-sensitive overloads are ordinal under invariant
 * globalization (D12). {@code StartsWith}, {@code EndsWith}, {@code IndexOf} and {@code Contains}
 * map to the plain Java methods and need no helper here.
 */
public final class DotNetStrings {
    private DotNetStrings() {
    }

    /** .NET {@code string.Trim()}: strips {@link DotNetChars#isWhiteSpace} from both ends. */
    public static String trim(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && DotNetChars.isWhiteSpace(s.charAt(start))) {
            start++;
        }
        while (end > start && DotNetChars.isWhiteSpace(s.charAt(end - 1))) {
            end--;
        }
        return start == 0 && end == s.length() ? s : s.substring(start, end);
    }

    /** .NET {@code string.IsNullOrEmpty}. */
    public static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /** .NET {@code string.IsNullOrWhiteSpace} over the .NET white-space set. */
    public static boolean isNullOrWhiteSpace(String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!DotNetChars.isWhiteSpace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** .NET {@code string.Compare(a, b)} (ordinal under invariant globalization); null sorts first. */
    public static int compare(String a, String b) {
        return compare(a, b, false);
    }

    /** .NET {@code string.Compare(a, b, ignoreCase)}; ignore case is {@code OrdinalIgnoreCase}. */
    public static int compare(String a, String b, boolean ignoreCase) {
        if (a == null || b == null) {
            return nullOrder(a, b);
        }
        return ignoreCase
                ? compareIgnoreCase(a, 0, a.length(), b, 0, b.length())
                : compareOrdinal(a, 0, a.length(), b, 0, b.length());
    }

    /**
     * .NET {@code string.Compare(a, indexA, b, indexB, length)}: compares
     * {@code min(length, a.Length - indexA)} chars of {@code a} with {@code min(length, b.Length - indexB)}
     * chars of {@code b}, ordinally; a shorter span sorts first.
     *
     * @throws IndexOutOfBoundsException (.NET {@code ArgumentOutOfRangeException}) for a negative
     *     length or index, or an index past the end
     */
    public static int compare(String a, int indexA, String b, int indexB, int length) {
        return compare(a, indexA, b, indexB, length, false);
    }

    /** As {@link #compare(String, int, String, int, int)}, optionally {@code OrdinalIgnoreCase}. */
    public static int compare(String a, int indexA, String b, int indexB, int length, boolean ignoreCase) {
        int lengthA = length;
        int lengthB = length;
        if (a != null) {
            lengthA = Math.min(lengthA, a.length() - indexA);
        }
        if (b != null) {
            lengthB = Math.min(lengthB, b.length() - indexB);
        }
        if (a == null || b == null) {
            return nullOrder(a, b);
        }
        if (length < 0 || indexA < 0 || indexB < 0 || lengthA < 0 || lengthB < 0) {
            throw new IndexOutOfBoundsException("Specified argument was out of the range of valid values.");
        }
        return ignoreCase
                ? compareIgnoreCase(a, indexA, lengthA, b, indexB, lengthB)
                : compareOrdinal(a, indexA, lengthA, b, indexB, lengthB);
    }

    /** .NET {@code string.Compare(a, b, StringComparison.OrdinalIgnoreCase)}. */
    public static int compareOrdinalIgnoreCase(String a, String b) {
        return compare(a, b, true);
    }

    /**
     * .NET {@code string.Equals(a, b, StringComparison.OrdinalIgnoreCase)}: equal after invariant
     * upper-casing per char (per code point for surrogate pairs). Never lower-cases.
     */
    public static boolean equalsOrdinalIgnoreCase(String a, String b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        return compareIgnoreCase(a, 0, a.length(), b, 0, b.length()) == 0;
    }

    /** .NET {@code string.ToUpperInvariant()}: per char (per code point for pairs); length never changes. */
    public static String toUpperInvariant(String s) {
        return mapCase(s, true);
    }

    /** .NET {@code string.ToLowerInvariant()}: per char (per code point for pairs); length never changes. */
    public static String toLowerInvariant(String s) {
        return mapCase(s, false);
    }

    /** .NET {@code string.Join(separator, values)}: null separator and null elements render as empty. */
    public static String join(String separator, Iterable<?> values) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object v : values) {
            if (!first && separator != null) {
                sb.append(separator);
            }
            first = false;
            sb.append(DotNet.str(v));
        }
        return sb.toString();
    }

    /** .NET {@code string.Join(separator, params object[])}. */
    public static String join(String separator, Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0 && separator != null) {
                sb.append(separator);
            }
            sb.append(DotNet.str(values[i]));
        }
        return sb.toString();
    }

    /** .NET {@code string.Concat(params object[])}: null renders as empty. */
    public static String concat(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (Object v : values) {
            sb.append(DotNet.str(v));
        }
        return sb.toString();
    }

    /** .NET {@code s.Split(c)}: keeps empty entries; {@code ""} yields one empty entry. */
    public static List<String> split(String s, char separator) {
        return split(s, separator, false);
    }

    /** .NET {@code s.Split(new[] { c }, removeEmptyEntries ? RemoveEmptyEntries : None)}. */
    public static List<String> split(String s, char separator, boolean removeEmptyEntries) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == separator) {
                if (!removeEmptyEntries || i > start) {
                    parts.add(s.substring(start, i));
                }
                start = i + 1;
            }
        }
        return parts;
    }

    private static int nullOrder(String a, String b) {
        if (a == b) {
            return 0;
        }
        return a == null ? -1 : 1;
    }

    private static int compareOrdinal(String a, int ia, int lenA, String b, int ib, int lenB) {
        int n = Math.min(lenA, lenB);
        for (int k = 0; k < n; k++) {
            char x = a.charAt(ia + k);
            char y = b.charAt(ib + k);
            if (x != y) {
                return x - y;
            }
        }
        return lenA - lenB;
    }

    /** Mirrors .NET {@code Ordinal.CompareStringIgnoreCase}: upper-case per char, per code point for valid pairs. */
    private static int compareIgnoreCase(String a, int ia, int lenA, String b, int ib, int lenB) {
        int n = Math.min(lenA, lenB);
        int k = 0;
        while (k < n) {
            char x = a.charAt(ia + k);
            char y = b.charAt(ib + k);
            if (k + 1 < n
                    && Character.isHighSurrogate(x)
                    && Character.isHighSurrogate(y)
                    && Character.isLowSurrogate(a.charAt(ia + k + 1))
                    && Character.isLowSurrogate(b.charAt(ib + k + 1))) {
                int cx = DotNetChars.toUpperInvariant(Character.toCodePoint(x, a.charAt(ia + k + 1)));
                int cy = DotNetChars.toUpperInvariant(Character.toCodePoint(y, b.charAt(ib + k + 1)));
                if (cx != cy) {
                    return cx - cy;
                }
                k += 2;
                continue;
            }
            if (x != y) {
                char ux = DotNetChars.toUpperInvariant(x);
                char uy = DotNetChars.toUpperInvariant(y);
                if (ux != uy) {
                    return ux - uy;
                }
            }
            k++;
        }
        return lenA - lenB;
    }

    private static String mapCase(String s, boolean upper) {
        StringBuilder sb = null;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                int cp = Character.toCodePoint(c, s.charAt(i + 1));
                int m = upper ? DotNetChars.toUpperInvariant(cp) : DotNetChars.toLowerInvariant(cp);
                if (m != cp && sb == null) {
                    sb = new StringBuilder(s.length()).append(s, 0, i);
                }
                if (sb != null) {
                    sb.appendCodePoint(m);
                }
                i += 2;
                continue;
            }
            char m = upper ? DotNetChars.toUpperInvariant(c) : DotNetChars.toLowerInvariant(c);
            if (m != c && sb == null) {
                sb = new StringBuilder(s.length()).append(s, 0, i);
            }
            if (sb != null) {
                sb.append(m);
            }
            i++;
        }
        return sb == null ? s : sb.toString();
    }
}
