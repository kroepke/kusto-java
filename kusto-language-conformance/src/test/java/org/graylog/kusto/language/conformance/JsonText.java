// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: oracle-compatible JSON string escaping and short snippets.

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON text helpers. {@link #quote} follows the oracle's escaping (oracle/README.md "Rendering
 * decisions"): {@code "} and {@code \\} escaped, every unit below U+0020 and every lone surrogate
 * as upper-case {@code \\uXXXX}, everything else raw.
 */
public final class JsonText {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private JsonText() {
    }

    public static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                b.append('\\').append(c);
            } else if (c < 0x20) {
                unicode(b, c);
            } else if (Character.isHighSurrogate(c)) {
                if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                    b.append(c).append(s.charAt(++i));
                } else {
                    unicode(b, c);
                }
            } else if (Character.isLowSurrogate(c)) {
                unicode(b, c);
            } else {
                b.append(c);
            }
        }
        return b.append('"').toString();
    }

    private static void unicode(StringBuilder b, char c) {
        b.append("\\u").append(HEX[(c >> 12) & 0xF]).append(HEX[(c >> 8) & 0xF]).append(HEX[(c >> 4) & 0xF]).append(HEX[c & 0xF]);
    }

    /** Replaces lone surrogates by {@code \\uXXXX} text so the string survives UTF-8 encoding. */
    public static String escapeLoneSurrogates(String s) {
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean lone = Character.isHighSurrogate(c)
                    ? !(i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1)))
                    : Character.isLowSurrogate(c) && !(i > 0 && Character.isHighSurrogate(s.charAt(i - 1)));
            if (lone && b == null) {
                b = new StringBuilder(s.length() + 8).append(s, 0, i);
            }
            if (b != null) {
                if (lone) {
                    unicode(b, c);
                } else {
                    b.append(c);
                }
            }
        }
        return b == null ? s : b.toString();
    }

    /** Compact JSON of {@code n} ({@code <absent>} for null/missing), cut to {@code max} chars. */
    public static String snippet(JsonNode n, int max) {
        String s;
        if (n == null || n.isMissingNode()) {
            s = "<absent>";
        } else {
            try {
                s = escapeLoneSurrogates(Harness.MAPPER.writeValueAsString(n));
            } catch (JsonProcessingException e) {
                s = "<unprintable: " + e.getOriginalMessage() + ">";
            }
        }
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    /** Pretty-printed JSON, lone surrogates escaped. */
    public static String pretty(JsonNode n) {
        if (n == null || n.isMissingNode()) {
            return "<absent>";
        }
        try {
            return escapeLoneSurrogates(Harness.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(n));
        } catch (JsonProcessingException e) {
            return "<unprintable: " + e.getOriginalMessage() + ">";
        }
    }
}
