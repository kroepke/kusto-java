// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: mutates seed records and checks the parse invariants on every mutant.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Fuzzing (PLAN.md 5.3): reads {@code corpus/fuzz-seeds.txt} (one record id per line, {@code #}
 * comments), mutates each seed with {@link Mutator} and checks {@link Invariants} on every mutant.
 * A failing mutant is reduced with {@link Reducer} (keeping the first violation's category) and
 * written (once per distinct reduced text and schema) to {@code target/fuzz-failures.jsonl} as a corpus line with
 * {@code "source": "fuzz: <seedId> <mutation>"}, ready to promote into {@code traps.jsonl}.
 * The seed is {@code -Dkusto.fuzzSeed} (default {@value #DEFAULT_SEED}).
 * Aborted while no port is available or the seeds file does not exist.
 */
class FuzzTest {
    static final long DEFAULT_SEED = 20261002L;

    @Test
    void fuzz() {
        PortAdapter port = PortAdapters.get();
        if (!PortAdapters.available(port)) {
            Assumptions.abort("no port yet");
        }
        Path seedsFile = Harness.corpusDir().resolve("fuzz-seeds.txt");
        if (!Files.isRegularFile(seedsFile)) {
            Assumptions.abort("no " + seedsFile);
        }
        List<String> seedIds = readSeeds(seedsFile);
        Map<String, CorpusRecord> all = CorpusReader.readAll();
        List<String> unknown = new ArrayList<>();
        for (String id : seedIds) {
            if (!all.containsKey(id)) {
                unknown.add(id);
            }
        }
        if (!unknown.isEmpty()) {
            fail("fuzz-seeds.txt names unknown record ids: " + unknown);
        }

        String seedProp = Harness.property("kusto.fuzzSeed");
        Mutator mutator = new Mutator(seedProp == null ? DEFAULT_SEED : Long.parseLong(seedProp));
        List<String> lines = new ArrayList<>();
        List<String> summary = new ArrayList<>();
        java.util.Set<String> reducedTexts = new java.util.HashSet<>();
        Set<String> knownTexts = readKnownFailures();
        Set<String> knownHit = new TreeSet<>();
        int mutants = 0;
        int failing = 0;
        for (String seedId : seedIds) {
            CorpusRecord seed = all.get(seedId);
            for (Mutator.Mutant m : mutator.mutate(seed.text(), starts(port, seed.text()))) {
                mutants++;
                CorpusRecord rec = new CorpusRecord("fuzz/tmp", m.text(), seed.schema(), null, false);
                Invariants.Check c = Invariants.check(port, rec);
                if (c.ok()) {
                    continue;
                }
                failing++;
                String category = c.category();
                String reduced = Reducer.reduce(m.text(), starts(port, m.text()), t -> {
                    Invariants.Check rc = Invariants.check(port, new CorpusRecord("fuzz/tmp", t, seed.schema(), null, false));
                    return category.equals(rc.category());
                });
                if (knownTexts.contains(reduced)) {
                    knownHit.add(reduced);
                    continue;
                }
                if (!reducedTexts.add(seed.schema() + "\u0000" + reduced)) {
                    continue;
                }
                String id = String.format(Locale.ROOT, "fuzz/%04d", lines.size() + 1);
                lines.add("{\"id\": " + JsonText.quote(id) + ", \"text\": " + JsonText.quote(reduced)
                        + ", \"schema\": " + JsonText.quote(seed.schema())
                        + ", \"source\": " + JsonText.quote("fuzz: " + seedId + " " + m.mutation()) + "}");
                summary.add(id + " (" + seedId + " " + m.mutation() + "): " + c.violations().get(0));
            }
        }
        Path out = Harness.targetDir().resolve("fuzz-failures.jsonl");
        try {
            Files.write(out, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("fuzz: " + seedIds.size() + " seeds, " + mutants + " mutants, " + failing
                + " failing, " + lines.size() + " distinct after reduction, " + knownHit.size() + " known (D33)");
        List<String> stale = new ArrayList<>();
        for (String t : knownTexts) {
            if (!knownHit.contains(t)) {
                stale.add(t);
            }
        }
        if (!stale.isEmpty()) {
            fail("stale known failure(s) in fuzz-known-failures.json (no longer failing): " + stale);
        }
        if (!lines.isEmpty()) {
            fail(failing + " failing mutants, " + lines.size() + " distinct after reduction (in " + out + "):\n  "
                    + String.join("\n  ", summary.subList(0, Math.min(50, summary.size()))));
        }
    }

    /** Exact reduced texts exempt from the round-trip invariant ({@code fuzz-known-failures.json}). */
    static Set<String> readKnownFailures() {
        Set<String> texts = new TreeSet<>();
        try (var in = FuzzTest.class.getResourceAsStream("/fuzz-known-failures.json")) {
            if (in == null) {
                return texts;
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            var m = java.util.regex.Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
            while (m.find()) {
                texts.add(m.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return texts;
    }

    static List<String> readSeeds(Path file) {
        List<String> ids = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int hash = line.indexOf('#');
                String id = (hash < 0 ? line : line.substring(0, hash)).strip();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ids;
    }

    /** Token starts from the adapter; falls back to character-class transitions if it has none. */
    static int[] starts(PortAdapter port, String text) {
        try {
            int[] s = KqlThread.call(() -> port.tokenStarts(text), Invariants.HANG_MS);
            if (s != null) {
                return s;
            }
        } catch (KqlThread.CallFailed | java.util.concurrent.TimeoutException e) {
            // fall through: a broken lexer must not stop the fuzzer from reducing
        }
        return charClassStarts(text);
    }

    static int[] charClassStarts(String text) {
        List<Integer> out = new ArrayList<>();
        int prev = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int cls = Character.isWhitespace(ch) ? 0 : Character.isLetterOrDigit(ch) || ch == '_' ? 1 : 2 + ch;
            if (cls != 0 && (cls != prev || cls >= 2)) {
                out.add(i);
            }
            prev = cls;
        }
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = out.get(i);
        }
        return r;
    }
}
