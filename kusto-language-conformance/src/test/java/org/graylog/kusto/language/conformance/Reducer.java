// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: delta-reduces a failing input by removing token-aligned chunks.

package org.graylog.kusto.language.conformance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * ddmin over token segments (same segmentation as {@link Mutator}): repeatedly removes chunks of
 * segments while {@code fails} still holds, refining the chunk size down to single segments, so
 * the result is 1-minimal (removing any single remaining segment makes it pass) unless the
 * budget of {@value #MAX_TESTS} predicate calls runs out. Finally the empty text is tried.
 */
public final class Reducer {
    public static final int MAX_TESTS = 2_000;

    private Reducer() {
    }

    public static String reduce(String text, int[] tokenStarts, Predicate<String> fails) {
        if (!fails.test(text)) {
            return text;
        }
        int[] b = Mutator.boundaries(text, tokenStarts);
        List<String> segs = new ArrayList<>();
        for (int i = 0; i + 1 < b.length; i++) {
            segs.add(text.substring(b[i], b[i + 1]));
        }
        int tests = 0;
        int n = 2;
        while (segs.size() >= 2 && tests < MAX_TESTS) {
            int chunk = (segs.size() + n - 1) / n;
            boolean reduced = false;
            for (int start = 0; start < segs.size() && tests < MAX_TESTS; start += chunk) {
                List<String> complement = new ArrayList<>(segs.subList(0, start));
                complement.addAll(segs.subList(Math.min(segs.size(), start + chunk), segs.size()));
                tests++;
                if (fails.test(String.join("", complement))) {
                    segs = complement;
                    n = Math.max(n - 1, 2);
                    reduced = true;
                    break;
                }
            }
            if (!reduced) {
                if (n >= segs.size()) {
                    break;
                }
                n = Math.min(n * 2, segs.size());
            }
        }
        String result = String.join("", segs);
        if (!result.isEmpty() && fails.test("")) {
            return "";
        }
        return result;
    }
}
