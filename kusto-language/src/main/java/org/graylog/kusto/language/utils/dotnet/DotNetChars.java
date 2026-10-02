// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: .NET char classification and invariant casing (PORTING.md 5.1).

package org.graylog.kusto.language.utils.dotnet;

/**
 * Mirrors {@code System.Char} helpers used upstream.
 *
 * <p>{@link #isWhiteSpace} is the exact .NET set. Category tests delegate to {@link Character};
 * table drift against .NET 10 is accepted (PORTING.md D21) and pinned by
 * {@code dotnet-char-drift.json}. Casing is per UTF-16 code unit, never the String forms.
 */
public final class DotNetChars {
    private DotNetChars() {
    }

    /** .NET {@code char.IsWhiteSpace}: U+0009-000D, 0020, 0085, 00A0, 1680, 2000-200A, 2028, 2029, 202F, 205F, 3000. */
    public static boolean isWhiteSpace(char c) {
        if (c <= 0x00FF) {
            return c == ' ' || (c >= 0x0009 && c <= 0x000D) || c == 0x0085 || c == 0x00A0;
        }
        return c == 0x1680
                || (c >= 0x2000 && c <= 0x200A)
                || c == 0x2028
                || c == 0x2029
                || c == 0x202F
                || c == 0x205F
                || c == 0x3000;
    }

    /** .NET {@code char.IsDigit} (Nd); delegates to {@link Character#isDigit(char)}. */
    public static boolean isDigit(char c) {
        return Character.isDigit(c);
    }

    /** .NET {@code char.IsLetter}; delegates to {@link Character#isLetter(char)}. */
    public static boolean isLetter(char c) {
        return Character.isLetter(c);
    }

    /** .NET {@code char.IsLetterOrDigit}; delegates to {@link Character#isLetterOrDigit(char)}. */
    public static boolean isLetterOrDigit(char c) {
        return Character.isLetterOrDigit(c);
    }

    /** .NET {@code char.IsUpper}; delegates to {@link Character#isUpperCase(char)} (drift: Other_Uppercase). */
    public static boolean isUpper(char c) {
        return Character.isUpperCase(c);
    }

    /** .NET {@code char.IsLower}; delegates to {@link Character#isLowerCase(char)} (drift: Other_Lowercase). */
    public static boolean isLower(char c) {
        return Character.isLowerCase(c);
    }

    /**
     * .NET {@code char.ToUpperInvariant}: simple case mapping, except that .NET never maps
     * U+0131 (dotless i) or U+017F (long s) to ASCII.
     */
    public static char toUpperInvariant(char c) {
        if (c < 0x80) {
            return c >= 'a' && c <= 'z' ? (char) (c - 0x20) : c;
        }
        if (c == 0x0131 || c == 0x017F) {
            return c;
        }
        return Character.toUpperCase(c);
    }

    /** .NET {@code char.ToLowerInvariant}: simple case mapping, except that .NET never maps U+0130 to ASCII. */
    public static char toLowerInvariant(char c) {
        if (c < 0x80) {
            return c >= 'A' && c <= 'Z' ? (char) (c + 0x20) : c;
        }
        if (c == 0x0130) {
            return c;
        }
        return Character.toLowerCase(c);
    }

    /** Invariant upper-case mapping of a code point; supplementary results stay supplementary. */
    static int toUpperInvariant(int codePoint) {
        if (codePoint < 0x10000) {
            return toUpperInvariant((char) codePoint);
        }
        int u = Character.toUpperCase(codePoint);
        return u >= 0x10000 ? u : codePoint;
    }

    /** Invariant lower-case mapping of a code point; supplementary results stay supplementary. */
    static int toLowerInvariant(int codePoint) {
        if (codePoint < 0x10000) {
            return toLowerInvariant((char) codePoint);
        }
        int l = Character.toLowerCase(codePoint);
        return l >= 0x10000 ? l : codePoint;
    }

    /**
     * .NET {@code char.ConvertFromUtf32}: one or two UTF-16 code units.
     *
     * @throws IndexOutOfBoundsException (.NET {@code ArgumentOutOfRangeException}, PORTING.md 3.16) for
     *     negative values, values above U+10FFFF and the surrogate range U+D800-DFFF
     */
    public static String convertFromUtf32(int utf32) {
        if (utf32 < 0 || utf32 > 0x10FFFF || (utf32 >= 0xD800 && utf32 <= 0xDFFF)) {
            throw new IndexOutOfBoundsException("A valid UTF32 value is between 0x000000 and 0x10ffff, inclusive, "
                    + "and should not include surrogate codepoint values (0x00d800 ~ 0x00dfff). (Parameter 'utf32')");
        }
        return new String(Character.toChars(utf32));
    }
}
