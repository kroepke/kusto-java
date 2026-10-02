// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: harness self-test adapter that replays oracle goldens as if it were the port.

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;

/**
 * Self-test of the harness, not a port: {@code -Dkusto.port=org.graylog.kusto.language.conformance.OracleEchoPort}
 * returns each record's golden (by id), so every layer must report 100%. Records without a golden
 * (fuzz mutants) get a minimal record that satisfies the invariants, with character-class token
 * starts. {@code -Dkusto.echo.failOn=<substring>} makes {@code write} throw for texts containing
 * it, to exercise the fuzz reducer.
 */
public final class OracleEchoPort implements PortAdapter {
    private final Map<String, ObjectNode> byId = new HashMap<>();
    private final Map<String, ObjectNode> byText = new HashMap<>();

    public OracleEchoPort() {
        String upstream = Harness.upstreamCommit();
        for (String corpus : Harness.corpusNames()) {
            if (!GoldenReader.exists(corpus)) {
                continue;
            }
            Map<String, String> texts = new HashMap<>();
            for (CorpusRecord r : CorpusReader.read(corpus)) {
                texts.put(r.id(), r.text());
            }
            GoldenReader.stream(GoldenReader.path(corpus), upstream, g -> {
                String text = texts.get(g.get("id").textValue());
                if (text != null) {
                    byId.put(g.get("id").textValue(), g);
                    byText.putIfAbsent(text, g);
                }
            });
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public ObjectNode write(CorpusRecord rec, SchemaFile schema) {
        String failOn = Harness.property("kusto.echo.failOn");
        if (failOn != null && rec.text().contains(failOn)) {
            throw new IllegalStateException("echo failOn " + failOn);
        }
        ObjectNode g = byId.get(rec.id());
        if (g != null) {
            return g.deepCopy();
        }
        ObjectNode r = Harness.MAPPER.createObjectNode();
        r.put("id", rec.id());
        r.put("kind", "Query");
        ArrayNode tokens = r.putArray("tokens");
        for (int s : FuzzTest.charClassStarts(rec.text())) {
            tokens.addObject().put("start", s);
        }
        r.putObject("fidelity").put("roundTrip", true).put("fullWidth", rec.text().length());
        r.putObject("outcome").put("parse", "ok").put("analyze", "ok").put("tokenValues", "ok");
        return r;
    }

    @Override
    public int[] tokenStarts(String text) {
        ObjectNode g = byText.get(text);
        if (g == null) {
            return null;
        }
        JsonNode tokens = g.path("tokens");
        int[] s = new int[tokens.size()];
        for (int i = 0; i < s.length; i++) {
            s[i] = tokens.get(i).path("start").asInt();
        }
        return s;
    }
}
