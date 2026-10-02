// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: small line-based unified diff for conformance report excerpts.

package org.graylog.kusto.language.conformance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * First hunk of a unified diff. Common prefix and suffix are trimmed, the differing middle is
 * aligned by LCS over at most {@value #WINDOW} lines per side (enough for a 20-line excerpt).
 */
public final class UnifiedDiff {
    static final int WINDOW = 400;
    private static final int CONTEXT = 3;

    private UnifiedDiff() {
    }

    /** Up to {@code maxLines} lines of a unified diff of {@code expected} vs {@code actual}; empty when equal. */
    public static List<String> diff(String expected, String actual, int maxLines) {
        String[] a = expected.split("\n", -1);
        String[] b = actual.split("\n", -1);
        int p = 0;
        while (p < a.length && p < b.length && a[p].equals(b[p])) {
            p++;
        }
        if (p == a.length && p == b.length) {
            return List.of();
        }
        int s = 0;
        while (s < a.length - p && s < b.length - p && a[a.length - 1 - s].equals(b[b.length - 1 - s])) {
            s++;
        }
        int aEnd = a.length - s;
        int bEnd = b.length - s;
        boolean truncated = aEnd - p > WINDOW || bEnd - p > WINDOW;
        String[] am = Arrays.copyOfRange(a, p, Math.min(aEnd, p + WINDOW));
        String[] bm = Arrays.copyOfRange(b, p, Math.min(bEnd, p + WINDOW));

        List<String> body = new ArrayList<>();
        int ctx = Math.max(0, p - CONTEXT);
        for (int i = ctx; i < p; i++) {
            body.add(" " + a[i]);
        }
        int[][] lcs = new int[am.length + 1][bm.length + 1];
        for (int i = am.length - 1; i >= 0; i--) {
            for (int j = bm.length - 1; j >= 0; j--) {
                lcs[i][j] = am[i].equals(bm[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        int i = 0;
        int j = 0;
        int aCount = p - ctx;
        int bCount = p - ctx;
        while (i < am.length || j < bm.length) {
            if (i < am.length && j < bm.length && am[i].equals(bm[j])) {
                body.add(" " + am[i++]);
                j++;
                aCount++;
                bCount++;
            } else if (i < am.length && (j == bm.length || lcs[i + 1][j] >= lcs[i][j + 1])) {
                body.add("-" + am[i++]);
                aCount++;
            } else {
                body.add("+" + bm[j++]);
                bCount++;
            }
        }
        if (!truncated) {
            for (int k = aEnd; k < Math.min(a.length, aEnd + CONTEXT); k++) {
                body.add(" " + a[k]);
                aCount++;
                bCount++;
            }
        }
        List<String> out = new ArrayList<>();
        out.add("--- expected");
        out.add("+++ actual");
        out.add("@@ -" + (ctx + 1) + "," + aCount + " +" + (ctx + 1) + "," + bCount + " @@" + (truncated ? " (truncated)" : ""));
        for (String line : body) {
            if (out.size() >= maxLines) {
                break;
            }
            out.add(line);
        }
        return out.size() > maxLines ? out.subList(0, maxLines) : out;
    }
}
