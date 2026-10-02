// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: deterministic token-level mutants of a KQL text for fuzzing.

package org.graylog.kusto.language.conformance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Token-level mutants. The text is cut at token boundaries (the token starts plus 0 and the text
 * length) into segments; a segment is a token's text plus the trivia before the next token.
 * Mutations: delete one segment, duplicate one segment, swap two adjacent segments, truncate at
 * each boundary, insert one of {@link #INSERT_TOKENS} (followed by a space) at a boundary.
 *
 * <p>At most {@value #CAP} mutants per text. When there are more candidates, the selection is a
 * partial Fisher-Yates shuffle driven by {@code new Random(seed ^ text.hashCode())}, then put
 * back in enumeration order, so a (seed, text) pair always yields the same mutants. Mutants equal
 * to the input or to an earlier mutant are dropped.
 */
public final class Mutator {
    public static final int CAP = 400;

    /** Tokens inserted at boundaries ({@code |} twice, as specified for T0b). */
    public static final List<String> INSERT_TOKENS = List.of(
            "|", "(", ")", ",", "=", "==", "\"", "'", "where", "|",
            "[", "{", ".", "by", "let", "print", "\\", "//", "1e", "datetime(");

    /** One mutant; {@code mutation} names it, e.g. {@code delete#3} or {@code insert@17:where}. */
    public record Mutant(String mutation, String text) {
    }

    private final long seed;

    public Mutator(long seed) {
        this.seed = seed;
    }

    /** Sorted, distinct boundaries in {@code [0, text.length()]}, always including both ends. */
    static int[] boundaries(String text, int[] tokenStarts) {
        int n = text.length();
        int[] all = Arrays.copyOf(tokenStarts == null ? new int[0] : tokenStarts, (tokenStarts == null ? 0 : tokenStarts.length) + 2);
        all[all.length - 2] = 0;
        all[all.length - 1] = n;
        Arrays.sort(all);
        int[] out = new int[all.length];
        int k = 0;
        for (int b : all) {
            if (b >= 0 && b <= n && (k == 0 || out[k - 1] != b)) {
                out[k++] = b;
            }
        }
        return Arrays.copyOf(out, k);
    }

    public List<Mutant> mutate(String text, int[] tokenStarts) {
        int[] b = boundaries(text, tokenStarts);
        int segs = b.length - 1;
        int truncs = b.length - 1;
        int inserts = INSERT_TOKENS.size() * b.length;
        int total = segs + segs + Math.max(0, segs - 1) + truncs + inserts;
        int[] chosen = select(total, text);
        Set<String> seen = new LinkedHashSet<>();
        seen.add(text);
        List<Mutant> out = new ArrayList<>();
        for (int idx : chosen) {
            Mutant m = materialize(text, b, idx);
            if (seen.add(m.text())) {
                out.add(m);
            }
        }
        return out;
    }

    private int[] select(int total, String text) {
        int[] idx = new int[total];
        for (int i = 0; i < total; i++) {
            idx[i] = i;
        }
        if (total <= CAP) {
            return idx;
        }
        Random r = new Random(seed ^ text.hashCode());
        for (int i = 0; i < CAP; i++) {
            int j = i + r.nextInt(total - i);
            int t = idx[i];
            idx[i] = idx[j];
            idx[j] = t;
        }
        int[] chosen = Arrays.copyOf(idx, CAP);
        Arrays.sort(chosen);
        return chosen;
    }

    private static Mutant materialize(String text, int[] b, int idx) {
        int segs = b.length - 1;
        if (idx < segs) {
            return new Mutant("delete#" + idx, text.substring(0, b[idx]) + text.substring(b[idx + 1]));
        }
        idx -= segs;
        if (idx < segs) {
            String seg = text.substring(b[idx], b[idx + 1]);
            return new Mutant("duplicate#" + idx, text.substring(0, b[idx + 1]) + seg + text.substring(b[idx + 1]));
        }
        idx -= segs;
        int swaps = Math.max(0, segs - 1);
        if (idx < swaps) {
            String s1 = text.substring(b[idx], b[idx + 1]);
            String s2 = text.substring(b[idx + 1], b[idx + 2]);
            return new Mutant("swap#" + idx, text.substring(0, b[idx]) + s2 + s1 + text.substring(b[idx + 2]));
        }
        idx -= swaps;
        if (idx < b.length - 1) {
            return new Mutant("truncate@" + b[idx], text.substring(0, b[idx]));
        }
        idx -= b.length - 1;
        int at = b[idx / INSERT_TOKENS.size()];
        String tok = INSERT_TOKENS.get(idx % INSERT_TOKENS.size());
        return new Mutant("insert@" + at + ":" + tok, text.substring(0, at) + tok + " " + text.substring(at));
    }
}
