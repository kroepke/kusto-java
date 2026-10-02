// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: parse invariants checked on adapter output (never throws, round-trip, monotonic tokens, time).

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Invariants, expressed through the adapter's golden-format output so the harness has no
 * compile-time dependency on the port:
 * <ul>
 *   <li>{@code write} never throws (any {@link Throwable}) on a 16 MB thread, and
 *       {@code outcome.parse == "ok"};</li>
 *   <li>{@code fidelity.roundTrip} (root text equals input) and
 *       {@code fidelity.fullWidth == text.length()};</li>
 *   <li>{@code tokens[].start} strictly increasing;</li>
 *   <li>parse time ({@code timing.parseMs}, else wall time of {@code write}) at most
 *       {@value #MAX_MS} ms; over {@value #PATHOLOGICAL_MS} ms is reported as pathological.</li>
 * </ul>
 * Each violation string starts with a category ({@code throw}, {@code parse}, {@code roundTrip},
 * {@code fullWidth}, {@code tokenStarts}, {@code slow}, {@code timeout}) followed by {@code :}.
 */
public final class Invariants {
    public static final long MAX_MS = 2_000;
    public static final long PATHOLOGICAL_MS = 200;
    static final long HANG_MS = 30_000;

    /** Result of one check. */
    public record Check(String text, List<String> violations, double parseMs, ObjectNode output) {
        public boolean ok() {
            return violations.isEmpty();
        }

        public boolean pathological() {
            return parseMs > PATHOLOGICAL_MS;
        }

        /** Category of the first violation, or null. */
        public String category() {
            if (violations.isEmpty()) {
                return null;
            }
            String v = violations.get(0);
            int c = v.indexOf(':');
            return c < 0 ? v : v.substring(0, c);
        }
    }

    private Invariants() {
    }

    /** Runs the adapter on {@code rec} and checks every invariant. */
    public static Check check(PortAdapter port, CorpusRecord rec) {
        SchemaFile schema = SchemaReader.get(rec.schema());
        long t0 = System.nanoTime();
        ObjectNode out;
        try {
            out = KqlThread.call(() -> port.write(rec, schema), HANG_MS);
        } catch (KqlThread.CallFailed e) {
            return new Check(rec.text(), List.of("throw: " + e.getCause()), ms(t0), null);
        } catch (TimeoutException e) {
            return new Check(rec.text(), List.of("timeout: " + e.getMessage()), ms(t0), null);
        }
        double wall = ms(t0);
        if (out == null) {
            return new Check(rec.text(), List.of("throw: write returned null"), wall, null);
        }
        JsonNode parseMs = out.path("timing").path("parseMs");
        double t = parseMs.isNumber() ? parseMs.asDouble() : wall;
        return new Check(rec.text(), violations(rec.text(), out, t), t, out);
    }

    /** The invariant violations of a golden-format record {@code out} for {@code text}. */
    public static List<String> violations(String text, JsonNode out, double parseMs) {
        List<String> v = new ArrayList<>();
        JsonNode parse = out.path("outcome").path("parse");
        if (!"ok".equals(parse.asText(null))) {
            v.add("parse: outcome.parse = " + JsonText.snippet(parse, 120));
        }
        JsonNode fidelity = out.path("fidelity");
        if (!fidelity.path("roundTrip").asBoolean(false)) {
            v.add("roundTrip: fidelity.roundTrip = " + JsonText.snippet(fidelity.path("roundTrip"), 40));
        }
        JsonNode fw = fidelity.path("fullWidth");
        if (!fw.isIntegralNumber() || fw.asLong() != text.length()) {
            v.add("fullWidth: fidelity.fullWidth = " + JsonText.snippet(fw, 40) + ", text length " + text.length());
        }
        JsonNode tokens = out.path("tokens");
        long prev = Long.MIN_VALUE;
        for (int i = 0; i < tokens.size(); i++) {
            JsonNode s = tokens.get(i).path("start");
            if (!s.isIntegralNumber() || s.asLong() <= prev) {
                v.add("tokenStarts: tokens[" + i + "].start = " + JsonText.snippet(s, 40) + " after " + prev);
                break;
            }
            prev = s.asLong();
        }
        if (parseMs > MAX_MS) {
            v.add("slow: parse took " + Math.round(parseMs) + " ms (limit " + MAX_MS + ")");
        }
        return v;
    }

    private static double ms(long t0) {
        return (System.nanoTime() - t0) / 1e6;
    }
}
