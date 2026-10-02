// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: tests mutant determinism and cap, delta reduction and the diff excerpt.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MutatorTest {
    static final String TEXT = "T | where a > 1";
    static final int[] STARTS = {0, 2, 4, 10, 12, 14, 15};

    @Test
    void boundaries_include_both_ends() {
        assertArrayEquals(new int[] {0, 2, 4, 10, 12, 14, 15}, Mutator.boundaries(TEXT, STARTS));
        assertArrayEquals(new int[] {0, 3}, Mutator.boundaries("abc", null));
        assertArrayEquals(new int[] {0, 1, 3}, Mutator.boundaries("abc", new int[] {1, 1, 3, 7}));
    }

    @Test
    void small_text_yields_every_mutation_kind() {
        List<Mutator.Mutant> ms = new Mutator(1).mutate(TEXT, STARTS);
        List<String> texts = ms.stream().map(Mutator.Mutant::text).toList();
        assertTrue(texts.contains("T | a > 1"), "delete 'where '");
        assertTrue(texts.contains("T | where where a > 1"), "duplicate");
        assertTrue(texts.contains("T | a where > 1"), "swap");
        assertTrue(texts.contains("T | wh".substring(0, 4)), "truncate at boundary 4");
        assertTrue(texts.contains("T | datetime( where a > 1"), "insert");
        assertTrue(texts.contains("T | where a > 1\\ "), "insert at end");
        assertTrue(ms.stream().anyMatch(m -> m.mutation().equals("insert@4:datetime(")));
        assertEquals(texts.size(), texts.stream().distinct().count(), "no duplicates");
        assertTrue(!texts.contains(TEXT), "identity dropped");
        assertEquals(20, Mutator.INSERT_TOKENS.size());
    }

    @Test
    void large_text_is_capped_and_deterministic() {
        StringBuilder b = new StringBuilder();
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            starts.add(b.length());
            b.append("tok").append(i).append(' ');
        }
        String text = b.toString();
        int[] s = starts.stream().mapToInt(Integer::intValue).toArray();
        List<Mutator.Mutant> a = new Mutator(42).mutate(text, s);
        List<Mutator.Mutant> again = new Mutator(42).mutate(text, s);
        List<Mutator.Mutant> other = new Mutator(43).mutate(text, s);
        assertTrue(a.size() <= Mutator.CAP && a.size() > 350, "size " + a.size());
        assertEquals(a, again);
        assertNotEquals(a, other);
    }

    @Test
    void reducer_finds_a_one_minimal_token_subset() {
        String text = "let x = 1; T | where bad == 2 | project y, z";
        int[] starts = {0, 4, 6, 8, 9, 11, 13, 15, 21, 25, 28, 30, 32, 40, 41, 43};
        AtomicInteger calls = new AtomicInteger();
        String r = Reducer.reduce(text, starts, t -> {
            calls.incrementAndGet();
            return t.contains("bad") && t.contains("|");
        });
        assertTrue(r.contains("bad") && r.contains("|"));
        assertTrue(r.length() <= "| where bad ".length(), r);
        assertTrue(calls.get() < Reducer.MAX_TESTS);
        assertEquals("", Reducer.reduce(text, starts, t -> true));
        assertEquals("ok", Reducer.reduce("ok", new int[] {0}, t -> false));
    }

    @Test
    void unified_diff_excerpt() {
        List<String> d = UnifiedDiff.diff("a\nb\nc\nd\ne\nf\ng\nh", "a\nb\nc\nd\nX\nf\ng\nh", 20);
        assertEquals(List.of("--- expected", "+++ actual", "@@ -2,7 +2,7 @@", " b", " c", " d", "-e", "+X", " f", " g", " h"), d);
        assertEquals(List.of(), UnifiedDiff.diff("same", "same", 20));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            big.append("line").append(i).append('\n');
        }
        assertEquals(20, UnifiedDiff.diff(big.toString(), "<absent>", 20).size());
    }

    @Test
    void char_class_fallback_starts() {
        assertArrayEquals(new int[] {0, 2, 4, 10, 12, 14}, FuzzTest.charClassStarts("T | where a > 1"));
        assertArrayEquals(new int[] {0, 1, 2}, FuzzTest.charClassStarts("(()"));
    }
}
