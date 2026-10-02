// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: aggregates comparison results per corpus and layer; writes Markdown, JSON and a stdout table.

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Conformance report. Per corpus and layer: total compared records (Command records excluded
 * where golden-format.md says so), passed, known differences, failed, and the pass rate
 * {@code passed / (total - known)}. Failures are grouped by cause, then layer and first differing
 * path with array indices blanked ({@code tree[].kind}), then, for diagnostics layers, the
 * diagnostic code; each group shows the first 20 lines of a unified diff of the pretty-printed
 * expected/actual layer projections of its first record.
 *
 * <p>Files: {@code target/conformance-report.md}, {@code target/conformance-report.json}
 * ({@code {upstream, port, corpora: {name: {records, commands, golden, layers: {LAYER: {total,
 * passed, known, failed, unclassified, noPort, rate}}}}, failures: [group...], knownDifferences,
 * noGolden}}; {@code failures} lists groups with counts and up to {@value #MAX_IDS} record ids,
 * not every failing pair, to keep the file small for the sentinel corpus).
 */
public final class ConformanceReport {
    static final int MAX_IDS = 100;
    static final int DIFF_LINES = 20;

    /** Counters of one corpus × layer cell. */
    public static final class Stats {
        public int total;
        public int passed;
        public int known;
        public int failed;
        public int unclassified;
        public int noPort;
        public int excluded;

        /** {@code passed / (total - known)}, or NaN when nothing was compared. */
        public double rate() {
            int d = total - known;
            return d == 0 ? Double.NaN : (double) passed / d;
        }
    }

    /** Per-corpus counters. */
    public static final class CorpusStats {
        public int records;
        public int corpusRecords;
        public int commands;
        public final Map<Layer, Stats> layers = new EnumMap<>(Layer.class);

        CorpusStats() {
            for (Layer l : Layer.values()) {
                layers.put(l, new Stats());
            }
        }
    }

    /** One failure group. */
    static final class Group {
        final String cause;
        final Layer layer;
        final String pathShape;
        final String code;
        int count;
        final List<String> ids = new ArrayList<>();
        GoldenComparator.Failure example;
        List<String> diff = List.of();

        Group(String cause, Layer layer, String pathShape, String code) {
            this.cause = cause;
            this.layer = layer;
            this.pathShape = pathShape;
            this.code = code;
        }
    }

    private final String upstream;
    private final String port;
    private final boolean portAvailable;
    private final KnownDifferences known;
    private final Map<String, CorpusStats> corpora = new LinkedHashMap<>();
    private final Map<String, String> noGolden = new LinkedHashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<String, Integer> knownApplied = new LinkedHashMap<>();

    public ConformanceReport(String upstream, String port, boolean portAvailable, KnownDifferences known) {
        this.upstream = upstream;
        this.port = port;
        this.portAvailable = portAvailable;
        this.known = known;
    }

    public void noGolden(String corpus, String reason) {
        noGolden.put(corpus, reason);
    }

    public Map<String, String> noGolden() {
        return noGolden;
    }

    public CorpusStats corpus(String name) {
        return corpora.computeIfAbsent(name, k -> new CorpusStats());
    }

    public Map<String, CorpusStats> corpora() {
        return corpora;
    }

    public Stats stats(String corpus, Layer layer) {
        return corpus(corpus).layers.get(layer);
    }

    /** Adds one record's results. */
    public void add(String corpus, GoldenComparator.RecordResult r) {
        CorpusStats cs = corpus(corpus);
        cs.records++;
        if (r.command()) {
            cs.commands++;
        }
        for (GoldenComparator.LayerResult lr : r.layers()) {
            Stats s = cs.layers.get(lr.layer());
            switch (lr.status()) {
                case EXCLUDED -> s.excluded++;
                case PASS -> {
                    s.total++;
                    s.passed++;
                }
                case KNOWN -> {
                    s.total++;
                    s.known++;
                    knownApplied.merge(lr.failure().cause(), 1, Integer::sum);
                    group(lr);
                }
                case FAIL -> {
                    s.total++;
                    s.failed++;
                    if (GoldenComparator.NO_PORT.equals(lr.failure().cause())) {
                        s.noPort++;
                    } else {
                        s.unclassified++;
                    }
                    group(lr);
                }
                default -> throw new AssertionError(lr.status());
            }
        }
    }

    private void group(GoldenComparator.LayerResult lr) {
        GoldenComparator.Failure f = lr.failure();
        String key = f.cause() + "\u0000" + f.layer() + "\u0000" + f.pathShape() + "\u0000" + f.diagnosticCode();
        Group g = groups.get(key);
        if (g == null) {
            g = new Group(f.cause(), f.layer(), f.pathShape(), f.diagnosticCode());
            g.example = f;
            g.diff = UnifiedDiff.diff(JsonText.pretty(lr.expectedProjection()), JsonText.pretty(lr.actualProjection()), DIFF_LINES);
            groups.put(key, g);
        }
        g.count++;
        if (g.ids.size() < MAX_IDS) {
            g.ids.add(f.recordId());
        }
    }

    private List<Group> sortedGroups() {
        List<Group> list = new ArrayList<>(groups.values());
        list.sort(Comparator.<Group>comparingInt(g -> causeRank(g.cause))
                .thenComparing(g -> g.cause)
                .thenComparing(g -> g.layer)
                .thenComparing(g -> -g.count)
                .thenComparing(g -> g.pathShape)
                .thenComparing(g -> g.code == null ? "" : g.code));
        return list;
    }

    private static int causeRank(String cause) {
        if (GoldenComparator.UNCLASSIFIED.equals(cause)) {
            return 0;
        }
        return GoldenComparator.NO_PORT.equals(cause) ? 2 : 1;
    }

    static String pct(double rate) {
        return Double.isNaN(rate) ? "n/a" : String.format(Locale.ROOT, "%.1f%%", rate * 100);
    }

    /** Compact fixed-width table for stdout. */
    public String table() {
        String[] abbrev = {"TOK", "FID", "TREE", "SYN", "OPARSE", "SEM", "BIND", "RTYPE", "OANLZ", "TVAL"};
        StringBuilder b = new StringBuilder();
        b.append("Conformance (upstream ").append(upstream, 0, Math.min(12, upstream.length()))
                .append(", port ").append(port).append(portAvailable ? "" : " [unavailable]").append(")\n");
        b.append(String.format(Locale.ROOT, "%-10s %11s %5s", "corpus", "records", "cmds"));
        for (String a : abbrev) {
            b.append(String.format(Locale.ROOT, " %7s", a));
        }
        b.append('\n');
        for (Map.Entry<String, CorpusStats> e : corpora.entrySet()) {
            CorpusStats cs = e.getValue();
            b.append(String.format(Locale.ROOT, "%-10s %11s %5d", e.getKey(), cs.records + "/" + cs.corpusRecords, cs.commands));
            for (Layer l : Layer.values()) {
                b.append(String.format(Locale.ROOT, " %7s", pct(cs.layers.get(l).rate())));
            }
            b.append('\n');
        }
        for (Map.Entry<String, String> e : noGolden.entrySet()) {
            b.append(String.format(Locale.ROOT, "%-10s no golden (%s)%n", e.getKey(), e.getValue()));
        }
        b.append("layers: ");
        for (int i = 0; i < abbrev.length; i++) {
            b.append(i == 0 ? "" : ", ").append(abbrev[i]).append('=').append(Layer.values()[i]);
        }
        return b.append('\n').toString();
    }

    public String markdown() {
        StringBuilder b = new StringBuilder();
        b.append("# Conformance report\n\n");
        b.append("- Upstream: `").append(upstream).append("`\n");
        b.append("- Port adapter: `").append(port).append("` (available: ").append(portAvailable).append(")\n");
        b.append("- Goldens: `").append(Harness.goldensDir()).append("`\n");
        b.append("- Generated: ").append(Instant.now()).append("\n\n");
        b.append("## Pass rates\n\n");
        b.append("Rate = passed / (total - known). Command records are compared on TOKENS, FIDELITY and "
                + "TOKEN_VALUES only; TOKENS ignores each token's `value`, which TOKEN_VALUES covers.\n\n");
        for (Map.Entry<String, CorpusStats> e : corpora.entrySet()) {
            CorpusStats cs = e.getValue();
            b.append("### ").append(e.getKey()).append("\n\n");
            b.append("Records compared: ").append(cs.records).append(" of ").append(cs.corpusRecords)
                    .append(" in the corpus; command records excluded from TREE, "
                    + "SYNTAX_DIAGNOSTICS, OUTCOME_PARSE and the semantic layers: ").append(cs.commands).append(".\n\n");
            b.append("| Layer | Total | Passed | Known | Failed | Unclassified | No port | Rate |\n");
            b.append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
            for (Layer l : Layer.values()) {
                Stats s = cs.layers.get(l);
                b.append("| ").append(l).append(" | ").append(s.total).append(" | ").append(s.passed).append(" | ")
                        .append(s.known).append(" | ").append(s.failed).append(" | ").append(s.unclassified).append(" | ")
                        .append(s.noPort).append(" | ").append(pct(s.rate())).append(" |\n");
            }
            b.append('\n');
        }
        if (!noGolden.isEmpty()) {
            b.append("## Corpora without golden\n\n");
            for (Map.Entry<String, String> e : noGolden.entrySet()) {
                b.append("- ").append(e.getKey()).append(": no golden (`").append(e.getValue()).append("`)\n");
            }
            b.append('\n');
        }
        b.append("## Failures by cause\n\n");
        if (groups.isEmpty()) {
            b.append("None.\n\n");
        }
        String cause = null;
        for (Group g : sortedGroups()) {
            if (!g.cause.equals(cause)) {
                cause = g.cause;
                int total = 0;
                for (Group o : groups.values()) {
                    if (o.cause.equals(cause)) {
                        total += o.count;
                    }
                }
                b.append("### Cause `").append(cause).append("` (").append(total).append(" failures)\n\n");
            }
            b.append("#### ").append(g.layer).append(" at `").append(g.pathShape).append('`');
            if (g.code != null) {
                b.append(" code `").append(g.code).append('`');
            }
            b.append(" (").append(g.count).append(" records)\n\n");
            GoldenComparator.Failure f = g.example;
            b.append("First: `").append(f.recordId()).append("` at `").append(f.firstDifferingPath()).append("`. ")
                    .append("Expected `").append(f.expected().replace("`", "'")).append("`, actual `")
                    .append(f.actual().replace("`", "'")).append("`.\n\n");
            if (!g.diff.isEmpty()) {
                b.append("```diff\n");
                for (String line : g.diff) {
                    b.append(line.replace("```", "` ` `")).append('\n');
                }
                b.append("```\n\n");
            }
            b.append("Records: ").append(String.join(", ", g.ids.subList(0, Math.min(20, g.ids.size()))));
            if (g.count > 20) {
                b.append(", ... (").append(g.count - 20).append(" more)");
            }
            b.append("\n\n");
        }
        b.append("## Known differences\n\n");
        if (known.entries().isEmpty()) {
            b.append("None registered.\n");
        } else {
            b.append("| Id | Applied | Layer | dRow | Expires | Status |\n|---|---:|---|---|---|---|\n");
            for (KnownDifferences.Entry e : known.entries()) {
                b.append("| ").append(e.id()).append(" | ").append(knownApplied.getOrDefault(e.id(), 0)).append(" | ")
                        .append(e.layer()).append(" | ").append(e.dRow()).append(" | `").append(e.expires(), 0, 8)
                        .append("` | ").append(e.needsRejustification(upstream) ? "**needs re-justification**" : "ok")
                        .append(" |\n");
            }
        }
        return b.toString();
    }

    public ObjectNode json() {
        ObjectNode root = Harness.MAPPER.createObjectNode();
        root.put("upstream", upstream);
        root.put("port", port);
        root.put("portAvailable", portAvailable);
        ObjectNode cs = root.putObject("corpora");
        for (Map.Entry<String, CorpusStats> e : corpora.entrySet()) {
            ObjectNode c = cs.putObject(e.getKey());
            c.put("records", e.getValue().records);
            c.put("corpusRecords", e.getValue().corpusRecords);
            c.put("commands", e.getValue().commands);
            c.put("golden", true);
            ObjectNode ls = c.putObject("layers");
            for (Layer l : Layer.values()) {
                Stats s = e.getValue().layers.get(l);
                ObjectNode o = ls.putObject(l.name());
                o.put("total", s.total);
                o.put("passed", s.passed);
                o.put("known", s.known);
                o.put("failed", s.failed);
                o.put("unclassified", s.unclassified);
                o.put("noPort", s.noPort);
                if (Double.isNaN(s.rate())) {
                    o.putNull("rate");
                } else {
                    o.put("rate", Math.round(s.rate() * 1e6) / 1e6);
                }
            }
        }
        for (String name : noGolden.keySet()) {
            cs.putObject(name).put("golden", false);
        }
        ArrayNode fs = root.putArray("failures");
        for (Group g : sortedGroups()) {
            ObjectNode o = fs.addObject();
            o.put("cause", g.cause);
            o.put("layer", g.layer.name());
            o.put("path", g.pathShape);
            o.put("code", g.code);
            o.put("count", g.count);
            ArrayNode ids = o.putArray("records");
            g.ids.forEach(ids::add);
            ObjectNode ex = o.putObject("example");
            ex.put("recordId", g.example.recordId());
            ex.put("firstDifferingPath", g.example.firstDifferingPath());
            ex.put("expected", g.example.expected());
            ex.put("actual", g.example.actual());
        }
        ArrayNode kds = root.putArray("knownDifferences");
        for (KnownDifferences.Entry e : known.entries()) {
            ObjectNode o = kds.addObject();
            o.put("id", e.id());
            o.put("applied", knownApplied.getOrDefault(e.id(), 0));
            o.put("needsRejustification", e.needsRejustification(upstream));
        }
        ArrayNode ng = root.putArray("noGolden");
        noGolden.keySet().forEach(ng::add);
        return root;
    }

    /** Writes {@code conformance-report.md} and {@code conformance-report.json} into {@code dir}. */
    public void write(Path dir) {
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve("conformance-report.md"), markdown().getBytes(StandardCharsets.UTF_8));
            String json = JsonText.escapeLoneSurrogates(Harness.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json()));
            Files.write(dir.resolve("conformance-report.json"), json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
