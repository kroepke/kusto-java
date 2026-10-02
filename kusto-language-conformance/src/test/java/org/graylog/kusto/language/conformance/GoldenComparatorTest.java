// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: tests path reporting, numeric comparison, known differences and Command restriction.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.graylog.kusto.language.conformance.GoldenComparator.Failure;
import org.graylog.kusto.language.conformance.GoldenComparator.LayerResult;
import org.graylog.kusto.language.conformance.GoldenComparator.RecordResult;
import org.graylog.kusto.language.conformance.GoldenComparator.Status;
import org.junit.jupiter.api.Test;

class GoldenComparatorTest {
    static final String SHA = "9d95a2d5bb085d151f14e88e07b703755fd914e1";

    /** A golden-format record with 20 tree nodes and one syntax diagnostic. */
    static ObjectNode record(String id, String kind) {
        ObjectNode r = Harness.MAPPER.createObjectNode();
        r.put("id", id);
        r.put("kind", kind);
        ArrayNode tokens = r.putArray("tokens");
        ObjectNode t = tokens.addObject();
        t.put("kind", "LongLiteralToken").put("triviaStart", 0).put("start", 0).put("end", 1).put("trivia", "").put("text", "1");
        t.put("value", "1");
        t.putArray("diagnostics");
        ObjectNode e = tokens.addObject();
        e.put("kind", "EndOfTextToken").put("triviaStart", 1).put("start", 1).put("end", 1).put("trivia", "").put("text", "");
        e.putNull("value");
        e.putArray("diagnostics");
        r.putObject("fidelity").put("roundTrip", true).put("fullWidth", 1);
        ArrayNode tree = r.putArray("tree");
        for (int i = 0; i < 20; i++) {
            tree.addObject().put("i", i).put("kind", "N" + i).put("depth", i).put("parent", i - 1).put("name", "")
                    .put("start", 0).put("end", 1).put("missing", false);
        }
        r.putArray("syntaxDiagnostics").addObject().put("code", "KS001").put("severity", "Error").put("start", 0)
                .put("length", 1).put("message", "m");
        r.putArray("semanticDiagnostics");
        r.putArray("bind");
        r.putNull("resultType");
        r.putObject("outcome").put("parse", "ok").put("analyze", "ok").put("tokenValues", "ok");
        r.putObject("timing").put("parseMs", 1.5).put("analyzeMs", 2.5);
        return r;
    }

    static KnownDifferences kd(String json) {
        try {
            return KnownDifferences.parse(Harness.MAPPER.readTree(json), "test");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static List<Layer> failing(RecordResult r) {
        return r.layers().stream().filter(l -> l.status() == Status.FAIL || l.status() == Status.KNOWN).map(LayerResult::layer).toList();
    }

    private final GoldenComparator cmp = new GoldenComparator(KnownDifferences.empty());

    @Test
    void identical_records_pass_and_timing_is_ignored() {
        ObjectNode act = record("readme/0001", "Query");
        ((ObjectNode) act.get("timing")).put("parseMs", 999);
        RecordResult r = cmp.compare(record("readme/0001", "Query"), act);
        assertEquals(List.of(), failing(r));
        assertTrue(r.layers().stream().allMatch(l -> l.status() == Status.PASS));
    }

    @Test
    void reports_first_differing_path() {
        ObjectNode act = record("readme/0001", "Query");
        ((ObjectNode) act.get("tree").get(14)).put("kind", "Other");
        ((ObjectNode) act.get("tree").get(16)).put("kind", "Other");
        RecordResult r = cmp.compare(record("readme/0001", "Query"), act);
        assertEquals(List.of(Layer.TREE), failing(r));
        Failure f = r.get(Layer.TREE).failure();
        assertEquals("tree[14].kind", f.firstDifferingPath());
        assertEquals("\"N14\"", f.expected());
        assertEquals("\"Other\"", f.actual());
        assertEquals(GoldenComparator.UNCLASSIFIED, f.cause());
        assertEquals("tree[].kind", f.pathShape());
    }

    @Test
    void numbers_compare_by_value_and_key_order_is_irrelevant() {
        ObjectNode act = record("readme/0001", "Query");
        act.putObject("fidelity").put("fullWidth", 1.0).put("roundTrip", true);
        assertEquals(List.of(), failing(cmp.compare(record("readme/0001", "Query"), act)));
        act.putObject("fidelity").put("fullWidth", 2).put("roundTrip", true);
        Failure f = cmp.compare(record("readme/0001", "Query"), act).get(Layer.FIDELITY).failure();
        assertEquals("fidelity.fullWidth", f.firstDifferingPath());
        assertEquals("1", f.expected());
        assertEquals("2", f.actual());
        assertNull(GoldenComparator.firstDifference("", Harness.MAPPER.getNodeFactory().numberNode(new java.math.BigDecimal("1.50")),
                Harness.MAPPER.getNodeFactory().numberNode(1.5)));
    }

    @Test
    void array_length_and_null_vs_absent() {
        ObjectNode act = record("readme/0001", "Query");
        ((ArrayNode) act.get("syntaxDiagnostics")).removeAll();
        Failure f = cmp.compare(record("readme/0001", "Query"), act).get(Layer.SYNTAX_DIAGNOSTICS).failure();
        assertEquals("syntaxDiagnostics[0]", f.firstDifferingPath());
        assertEquals("<absent>", f.actual());
        assertEquals("KS001", f.diagnosticCode());

        ObjectNode act2 = record("readme/0001", "Query");
        act2.remove("resultType");
        Failure g = cmp.compare(record("readme/0001", "Query"), act2).get(Layer.RESULT_TYPE).failure();
        assertEquals("resultType", g.firstDifferingPath());
        assertEquals("null", g.expected());
    }

    @Test
    void tokens_layer_ignores_values_which_token_values_covers() {
        ObjectNode act = record("readme/0001", "Query");
        ((ObjectNode) act.get("tokens").get(0)).put("value", "2");
        RecordResult r = cmp.compare(record("readme/0001", "Query"), act);
        assertEquals(List.of(Layer.TOKEN_VALUES), failing(r));
        assertEquals("tokens[0].value", r.get(Layer.TOKEN_VALUES).failure().firstDifferingPath());

        ObjectNode act2 = record("readme/0001", "Query");
        ((ObjectNode) act2.get("outcome")).put("tokenValues", "throw:ArithmeticException");
        RecordResult r2 = cmp.compare(record("readme/0001", "Query"), act2);
        assertEquals(List.of(Layer.TOKEN_VALUES), failing(r2));
        assertEquals("outcome.tokenValues", r2.get(Layer.TOKEN_VALUES).failure().firstDifferingPath());

        ObjectNode act3 = record("readme/0001", "Query");
        ((ObjectNode) act3.get("outcome")).put("parse", "throw:IllegalStateException");
        assertEquals(List.of(Layer.OUTCOME_PARSE), failing(cmp.compare(record("readme/0001", "Query"), act3)));
    }

    @Test
    void known_differences_classify_failures() {
        KnownDifferences known = kd("{\"differences\":[{\"id\":\"KD-001\",\"record\":\"readme/*\",\"path\":\"tree\\\\[\\\\d+\\\\]\\\\.kind\","
                + "\"layer\":\"TREE\",\"dRow\":\"D12\",\"reason\":\"test\",\"expires\":\"" + SHA + "\"},"
                + "{\"id\":\"KD-002\",\"record\":\"^docs/00(1|2)\\\\d$\",\"layer\":\"*\",\"dRow\":\"D11\",\"reason\":\"r\",\"expires\":\"" + "a".repeat(40) + "\"}]}");
        GoldenComparator c = new GoldenComparator(known);
        ObjectNode act = record("readme/0001", "Query");
        ((ObjectNode) act.get("tree").get(14)).put("kind", "Other");
        ((ObjectNode) act.get("fidelity")).put("fullWidth", 3);
        RecordResult r = c.compare(record("readme/0001", "Query"), act);
        assertEquals(Status.KNOWN, r.get(Layer.TREE).status());
        assertEquals("KD-001", r.get(Layer.TREE).failure().cause());
        assertEquals(Status.FAIL, r.get(Layer.FIDELITY).status(), "KD-001 is TREE-only");
        assertEquals(GoldenComparator.UNCLASSIFIED, r.get(Layer.FIDELITY).failure().cause());

        RecordResult other = c.compare(record("sentinel/0001", "Query"), act);
        assertEquals(Status.FAIL, other.get(Layer.TREE).status(), "glob is on record id");

        RecordResult regex = c.compare(record("docs/0015", "Query"), act);
        assertEquals("KD-002", regex.get(Layer.FIDELITY).failure().cause());
        assertEquals(Status.FAIL, c.compare(record("docs/0035", "Query"), act).get(Layer.FIDELITY).status());

        assertEquals(List.of("KD-001"), known.needingRejustification(SHA).stream().map(KnownDifferences.Entry::id).toList());
    }

    @Test
    void command_records_compare_tokens_fidelity_and_token_values_only() {
        ObjectNode act = record("docs/0001", "Command");
        ((ObjectNode) act.get("tree").get(3)).put("kind", "X");
        ((ArrayNode) act.get("syntaxDiagnostics")).removeAll();
        ((ObjectNode) act.get("outcome")).put("analyze", "throw:X");
        RecordResult r = cmp.compare(record("docs/0001", "Command"), act);
        assertTrue(r.command());
        assertEquals(List.of(), failing(r));
        for (Layer l : Layer.values()) {
            assertEquals(l.comparedOnCommands() ? Status.PASS : Status.EXCLUDED, r.get(l).status(), l.name());
        }
        assertEquals(List.of(Layer.TOKENS, Layer.FIDELITY, Layer.TOKEN_VALUES),
                List.of(Layer.values()).stream().filter(Layer::comparedOnCommands).toList());

        ((ObjectNode) act.get("tokens").get(0)).put("kind", "X");
        assertEquals(List.of(Layer.TOKENS), failing(cmp.compare(record("docs/0001", "Command"), act)));
    }

    @Test
    void absent_actual_is_no_port_and_adapter_error_is_unclassified() {
        RecordResult r = cmp.compare(record("readme/0001", "Query"), null);
        assertEquals(List.of(Layer.values()), failing(r));
        for (LayerResult l : r.layers()) {
            assertEquals(GoldenComparator.NO_PORT, l.failure().cause());
            assertEquals(l.layer().rootPath(), l.failure().firstDifferingPath());
        }
        RecordResult cmd = cmp.compare(record("docs/0001", "Command"), null);
        assertEquals(List.of(Layer.TOKENS, Layer.FIDELITY, Layer.TOKEN_VALUES), failing(cmd));

        RecordResult err = cmp.compare(record("readme/0001", "Query"), null, "java.lang.StackOverflowError");
        assertTrue(err.get(Layer.TREE).failure().unclassified());
        assertTrue(err.get(Layer.TREE).failure().actual().contains("StackOverflowError"));
    }

    @Test
    void gate_checks_only_gated_layers() {
        GoldenComparator c = new GoldenComparator(KnownDifferences.empty());
        ConformanceReport rep = new ConformanceReport(SHA, "test", true, KnownDifferences.empty());
        ObjectNode bad = record("readme/0002", "Query");
        ((ObjectNode) bad.get("tree").get(1)).put("kind", "Z");
        rep.add("readme", c.compare(record("readme/0001", "Query"), record("readme/0001", "Query")));
        rep.add("readme", c.compare(record("readme/0002", "Query"), bad));
        java.util.Map<Layer, Integer> counts = new java.util.EnumMap<>(Layer.class);
        counts.put(Layer.TOKENS, 2);
        counts.put(Layer.TREE, 1);
        ConformanceBaseline tokensOnly = new ConformanceBaseline(java.util.EnumSet.of(Layer.TOKENS), java.util.Map.of("readme", counts));
        assertEquals(List.of(), ConformanceTest.gate(tokensOnly, rep, true, List.of("readme")), "TREE failure is not gated");
        ConformanceBaseline tree = new ConformanceBaseline(java.util.EnumSet.of(Layer.TREE), java.util.Map.of("readme", counts));
        assertEquals(List.of("readme TREE: 1 unclassified failures"), ConformanceTest.gate(tree, rep, true, List.of("readme")));
        counts.put(Layer.TOKENS, 3);
        assertEquals(List.of("readme TOKENS: 2 passed, baseline 3 (regression)"), ConformanceTest.gate(tokensOnly, rep, true, List.of("readme")));
        assertEquals(1, ConformanceTest.gate(tokensOnly, rep, false, List.of("readme")).size(), "gated without port");
        assertEquals(List.of(), ConformanceTest.gate(new ConformanceBaseline(java.util.EnumSet.noneOf(Layer.class), java.util.Map.of()),
                new ConformanceReport(SHA, "empty", false, KnownDifferences.empty()), false, List.of()));
    }

    @Test
    void report_counts_and_rates() {
        KnownDifferences known = kd("{\"differences\":[{\"id\":\"KD-001\",\"record\":\"readme/0002\",\"layer\":\"TREE\","
                + "\"dRow\":\"D12\",\"reason\":\"t\",\"expires\":\"" + SHA + "\"}]}");
        GoldenComparator c = new GoldenComparator(known);
        ConformanceReport rep = new ConformanceReport(SHA, "test", true, known);
        ObjectNode bad = record("x", "Query");
        ((ObjectNode) bad.get("tree").get(1)).put("kind", "Z");
        rep.add("readme", c.compare(record("readme/0001", "Query"), record("readme/0001", "Query")));
        rep.add("readme", c.compare(record("readme/0002", "Query"), bad.put("id", "readme/0002")));
        rep.add("readme", c.compare(record("readme/0003", "Query"), bad.put("id", "readme/0003")));
        rep.add("readme", c.compare(record("readme/0004", "Command"), null));
        ConformanceReport.Stats tree = rep.stats("readme", Layer.TREE);
        assertEquals(3, tree.total);
        assertEquals(1, tree.passed);
        assertEquals(1, tree.known);
        assertEquals(1, tree.failed);
        assertEquals(1, tree.unclassified);
        assertEquals(0.5, tree.rate());
        ConformanceReport.Stats tokens = rep.stats("readme", Layer.TOKENS);
        assertEquals(4, tokens.total);
        assertEquals(3, tokens.passed);
        assertEquals(1, tokens.noPort);
        assertEquals(1, rep.corpus("readme").commands);
        String md = rep.markdown();
        assertTrue(md.contains("### Cause `unclassified` (1 failures)"), md);
        assertTrue(md.contains("#### TREE at `tree[].kind`"), md);
        assertTrue(md.contains("**needs re-justification**"), md);
        assertTrue(md.contains("```diff\n--- expected\n+++ actual\n@@"), md);
        assertNotNull(rep.json().get("failures").get(0).get("example"));
        assertTrue(rep.table().contains("readme"));
    }
}
