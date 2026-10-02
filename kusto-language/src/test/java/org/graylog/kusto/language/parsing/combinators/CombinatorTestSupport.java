// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: shared helpers for the parser-combinator unit tests (Source over chars, scan/parse agreement).

package org.graylog.kusto.language.parsing.combinators;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.parsing.ArraySource;
import org.graylog.kusto.language.parsing.ParseResult;
import org.graylog.kusto.language.parsing.Parser;
import org.graylog.kusto.language.parsing.Parser2;
import org.graylog.kusto.language.parsing.Source;

final class CombinatorTestSupport {
    private CombinatorTestSupport() {
    }

    /** An ArraySource over the UTF-16 units of {@code text} (not a TextSource: exercises the generic paths). */
    static Source<Character> src(String text) {
        List<Character> chars = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            chars.add(text.charAt(i));
        }
        return new ArraySource<>(chars);
    }

    static int scan(Parser<Character> p, String text) {
        return p.scan(src(text), 0);
    }

    /** List-based parse from a fresh output list; returns the length, output in {@code out}. */
    static int parseList(Parser<Character> p, String text, List<Object> out) {
        return p.parse(src(text), 0, out, 0);
    }

    static <T> ParseResult<T> parse(Parser2<Character, T> p, String text) {
        return p.parse(src(text), 0);
    }

    /** Scan length == list-parse length (== result-parse length for Parser2). Returns the length. */
    static int assertAgree(Parser<Character> p, String text) {
        int scan = p.scan(src(text), 0);
        List<Object> out = new ArrayList<>();
        int listLen = p.parse(src(text), 0, out, 0);
        assertEquals(scan, listLen, "scan vs list parse on '" + text + "'");
        if (scan < 0) {
            assertEquals(0, out.size(), "failed list parse leaves no output on '" + text + "'");
        }
        if (p instanceof Parser2<Character, ?> p2) {
            int resultLen = p2.parse(src(text), 0).length();
            assertEquals(scan, resultLen, "scan vs result parse on '" + text + "'");
        }
        return scan;
    }
}
