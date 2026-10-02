// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: runs the port over every corpus with a golden, reports and gates per layer.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The conformance gate. For each corpus with a golden: run the adapter per record on a 16 MB
 * thread, compare per layer, then write {@code target/conformance-report.{md,json}}.
 *
 * <p>A golden may cover a subset of its corpus (e.g. a sampled sentinel golden); the report shows
 * the coverage. Fails when: a golden header does not match the manifest; a golden holds a record
 * id that is not in its corpus, or holds one twice (stale golden); {@code -Dkusto.requireGoldens=true} and some corpus has no golden;
 * a gated layer ({@code conformance-baseline.json}) passes fewer records than its baseline,
 * has an unclassified failure, or is gated while no port is available. Non-gated layers never
 * fail the build.
 */
@Tag("conformance")
class ConformanceTest {
    static final long RECORD_TIMEOUT_MS = 60_000;

    @Test
    void conformance() {
        String upstream = Harness.upstreamCommit();
        PortAdapter port = PortAdapters.get();
        boolean available = PortAdapters.available(port);
        KnownDifferences known = KnownDifferences.load();
        ConformanceBaseline baseline = ConformanceBaseline.load();
        GoldenComparator comparator = new GoldenComparator(known);
        ConformanceReport report = new ConformanceReport(upstream, port.getClass().getName(), available, known);

        List<String> problems = new ArrayList<>();
        List<String> withGolden = new ArrayList<>();
        for (String corpus : Harness.corpusNames()) {
            if (!GoldenReader.exists(corpus)) {
                report.noGolden(corpus, GoldenReader.path(corpus).toString());
                continue;
            }
            withGolden.add(corpus);
            runCorpus(corpus, upstream, port, available, comparator, report);
        }

        report.write(Harness.targetDir());
        System.out.print(report.table());

        if (Harness.requireGoldens() && !report.noGolden().isEmpty()) {
            problems.add("kusto.requireGoldens=true but these corpora have no golden in " + Harness.goldensDir()
                    + ": " + report.noGolden().keySet());
        }
        problems.addAll(gate(baseline, report, available, withGolden));
        if (!problems.isEmpty()) {
            fail("conformance gate failed (see target/conformance-report.md):\n  " + String.join("\n  ", problems));
        }
    }

    /** Gate violations: per gated layer and corpus, regression below baseline or unclassified failures. */
    static List<String> gate(ConformanceBaseline baseline, ConformanceReport report, boolean available, List<String> corpora) {
        List<String> problems = new ArrayList<>();
        for (Layer layer : baseline.gatedLayers()) {
            if (!available) {
                problems.add(layer + " is gated but no port is available (kusto.port)");
                continue;
            }
            for (String corpus : corpora) {
                ConformanceReport.Stats s = report.stats(corpus, layer);
                int min = baseline.expected(corpus, layer);
                if (s.passed < min) {
                    problems.add(corpus + " " + layer + ": " + s.passed + " passed, baseline " + min + " (regression)");
                }
                if (s.unclassified > 0) {
                    problems.add(corpus + " " + layer + ": " + s.unclassified + " unclassified failures");
                }
            }
        }
        return problems;
    }

    private static void runCorpus(String corpus, String upstream, PortAdapter port, boolean available,
            GoldenComparator comparator, ConformanceReport report) {
        Map<String, CorpusRecord> records = new LinkedHashMap<>();
        for (CorpusRecord r : CorpusReader.read(corpus)) {
            records.put(r.id(), r);
        }
        report.corpus(corpus);
        Set<String> seen = new HashSet<>();
        GoldenReader.stream(GoldenReader.path(corpus), upstream, golden -> {
            String id = golden.get("id").textValue();
            CorpusRecord rec = records.get(id);
            if (rec == null || !seen.add(id)) {
                throw new IllegalStateException("golden " + GoldenReader.path(corpus) + " has record " + id
                        + (rec == null ? " that is not in corpus/" + corpus + ".jsonl" : " twice")
                        + "; the golden is stale, regenerate it");
            }
            ObjectNode actual = null;
            String error = null;
            if (available) {
                try {
                    actual = KqlThread.call(() -> port.write(rec, SchemaReader.get(rec.schema())), RECORD_TIMEOUT_MS);
                    if (actual == null) {
                        error = "write returned null";
                    }
                } catch (KqlThread.CallFailed e) {
                    error = e.getCause().toString();
                } catch (TimeoutException e) {
                    error = "timeout: " + e.getMessage();
                }
            }
            report.add(corpus, comparator.compare(golden, actual, error));
        });
        report.corpus(corpus).corpusRecords = records.size();
    }
}
