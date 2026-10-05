// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: ParserKind.Grammar and ParserKind.Default must produce the same tree on every golden Query record, bar the seven known upstream divergences.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeoutException;
import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.KustoCode;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.ParserKind;
import org.graylog.kusto.language.editor.CodeKinds;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * For every non-Command record of every golden set, parses with {@code ParserKind.Grammar} and with the default
 * parser and compares {@code tree} and {@code syntaxDiagnostics}. The seven records of
 * {@code QueryGrammarTest.UPSTREAM_GRAMMAR_DIVERGENCES} are verified upstream behaviour (the .NET Grammar parser
 * differs the same way) and must still differ, so the list cannot rot; all others must be identical.
 */
class ParserKindEquivalenceTest {
    static final Set<String> EXPECTED_DIVERGENCES = Set.of(
            "traps/0355", "traps/0361", "traps/0444", "traps/0531", "traps/0551", "traps/0552", "traps/0631",
            "docs/0649");

    /**
     * Upstream builds {@code has_any}/{@code has_all} in the grammar path with kind {@code HasAnyKeyword}/{@code
     * HasAllKeyword} (QueryGrammar.cs:1571,1574) where QueryParser uses {@code HasAnyExpression}/{@code
     * HasAllExpression}. Everything else in the tree is identical, so such records count as equal once the grammar's
     * kind names are normalised; traps/0355 and 0361 are the minimal examples.
     */
    static final String HAS_NORMALISE = "has";

    @Test
    void grammarEqualsDefaultParser() throws Exception {
        PortAdapter port = PortAdapters.get();
        if (!PortAdapters.available(port) || !(port instanceof GoldenWriter)) {
            Assumptions.abort("needs the GoldenWriter port (kusto.port selects another adapter)");
        }
        List<String> unexpected = new ArrayList<>();
        Set<String> diverged = new TreeSet<>();
        int[] compared = new int[1];
        int[] commands = new int[1];
        int[] hasKinds = new int[1];
        for (String corpus : Harness.corpusNames()) {
            if (!GoldenReader.exists(corpus)) {
                continue;
            }
            Map<String, CorpusRecord> records = new java.util.HashMap<>();
            CorpusReader.read(corpus).forEach(r -> records.put(r.id(), r));
            List<CorpusRecord> golden = new ArrayList<>();
            GoldenReader.stream(GoldenReader.path(corpus), Harness.upstreamCommit(), g -> {
                CorpusRecord r = records.get(g.get("id").textValue());
                if ("Command".equals(g.get("kind").textValue())) {
                    commands[0]++;
                } else if (r != null) {
                    golden.add(r);
                }
            });
            KqlThread.call(() -> {
                for (CorpusRecord rec : golden) {
                    GlobalState globals = GoldenWriter.globals(SchemaReader.get(rec.schema()));
                    JsonNode a = project(rec, globals.withParseOptions(globals.parseOptions().withParserKind(ParserKind.Default)));
                    JsonNode b = project(rec, globals.withParseOptions(globals.parseOptions().withParserKind(ParserKind.Grammar)));
                    compared[0]++;
                    if (!a.equals(b)) {
                        diverged.add(rec.id());
                        if (a.equals(normalise(b))) {
                            hasKinds[0]++;
                        } else if (!EXPECTED_DIVERGENCES.contains(rec.id())) {
                            unexpected.add(rec.id() + " " + firstDiff(a, b));
                        }
                    }
                }
                return null;
            }, 600_000);
        }
        System.out.println("parser-kind equivalence: " + compared[0] + " Query records compared, " + commands[0]
                + " Command records skipped, " + diverged.size() + " diverge raw (" + hasKinds[0]
                + " only by has_any/has_all kind names)");
        assertTrue(unexpected.isEmpty(), "Grammar and Default trees differ on " + unexpected);
        Set<String> stale = new TreeSet<>(EXPECTED_DIVERGENCES);
        stale.removeAll(diverged);
        assertTrue(stale.isEmpty(), "known divergences that no longer diverge: " + stale);
    }

    /** Renames the grammar's {@code HasAnyKeyword}/{@code HasAllKeyword} tree entries to the query parser's kinds. */
    static JsonNode normalise(JsonNode projection) {
        com.fasterxml.jackson.databind.node.ObjectNode copy = projection.deepCopy();
        JsonNode tree = copy.get("tree");
        java.util.BitSet hasChildren = new java.util.BitSet();
        for (JsonNode e : tree) {
            if (e.get("parent").intValue() >= 0) {
                hasChildren.set(e.get("parent").intValue());
            }
        }
        for (JsonNode e : tree) {
            String kind = e.get("kind").textValue();
            // the keyword token of the same name has no children; only the node is renamed
            if ((kind.equals("HasAnyKeyword") || kind.equals("HasAllKeyword")) && hasChildren.get(e.get("i").intValue())) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) e).put("kind", kind.replace("Keyword", "Expression"));
            }
        }
        return copy;
    }

    static String firstDiff(JsonNode a, JsonNode b) {
        for (String part : new String[] {"tree", "syntaxDiagnostics"}) {
            JsonNode x = a.get(part);
            JsonNode y = b.get(part);
            int n = Math.min(x.size(), y.size());
            for (int i = 0; i < n; i++) {
                if (!x.get(i).equals(y.get(i))) {
                    return part + "[" + i + "] default=" + x.get(i) + " grammar=" + y.get(i);
                }
            }
            if (x.size() != y.size()) {
                return part + " size default=" + x.size() + " grammar=" + y.size();
            }
        }
        return "?";
    }

    private static JsonNode project(CorpusRecord rec, GlobalState globals) {
        KustoCode code = KustoCode.parse(rec.text(), globals);
        assertTrue(!CodeKinds.Command.equals(code.kind()));
        var r = GoldenWriter.render(rec.id(), rec.text(), code, "ok", 0);
        var o = Harness.MAPPER.createObjectNode();
        o.set("tree", r.get("tree"));
        o.set("syntaxDiagnostics", r.get("syntaxDiagnostics"));
        return o;
    }
}
