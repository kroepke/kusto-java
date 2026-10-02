// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: field-wise comparison of golden and port records per layer.

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares an expected (oracle) record with an actual (port) record, layer by layer.
 *
 * <p>Deep comparison: numbers by value ({@code 1 == 1.0}), strings exactly, booleans and nulls by
 * value, arrays positionally (the first differing index is reported, a length mismatch reports
 * the first index present on one side only), objects by key regardless of key order (expected
 * keys in expected order first, then keys only the actual side has). A key absent on one side
 * differs from an explicit {@code null}.
 *
 * <p>Records whose expected {@code kind} is {@code Command} are compared only on the layers with
 * {@link Layer#comparedOnCommands()}; the others are {@link Status#EXCLUDED}. When the actual
 * record is absent (no port) every compared layer fails with cause {@value #NO_PORT}.
 */
public final class GoldenComparator {
    public static final String NO_PORT = "no-port";
    public static final String UNCLASSIFIED = "unclassified";
    public static final int SNIPPET = 200;

    private static final Pattern INDEX = Pattern.compile("\\[(\\d+)]");

    /** Outcome of one (record, layer) pair. */
    public enum Status { PASS, KNOWN, FAIL, EXCLUDED }

    /** First difference between two JSON values; {@code path} is relative to the record root. */
    public record Difference(String path, JsonNode expected, JsonNode actual) {
    }

    /**
     * A failing (record, layer) pair.
     *
     * @param cause          known-difference id, {@value #UNCLASSIFIED} or {@value #NO_PORT}
     * @param diagnosticCode for diagnostics layers, the {@code code} of the differing diagnostic
     */
    public record Failure(String recordId, Layer layer, String firstDifferingPath, String expected, String actual,
            String cause, String diagnosticCode) {

        public boolean unclassified() {
            return UNCLASSIFIED.equals(cause);
        }

        /** The path with array indices blanked ({@code tree[].kind}), used for grouping. */
        public String pathShape() {
            return INDEX.matcher(firstDifferingPath).replaceAll("[]");
        }
    }

    /** One layer's result; the projections are kept only for report diffs and then dropped. */
    public record LayerResult(Layer layer, Status status, Failure failure, JsonNode expectedProjection,
            JsonNode actualProjection) {
    }

    /** All layers of one record. */
    public record RecordResult(String recordId, boolean command, List<LayerResult> layers) {
        public LayerResult get(Layer l) {
            return layers.get(l.ordinal());
        }
    }

    private final KnownDifferences known;

    public GoldenComparator(KnownDifferences known) {
        this.known = known;
    }

    /** Compares; {@code actual == null} means no port output (cause {@value #NO_PORT}). */
    public RecordResult compare(ObjectNode expected, ObjectNode actual) {
        return compare(expected, actual, null);
    }

    /**
     * Compares. When {@code actual} is null and {@code adapterError} is non-null, the adapter
     * threw: every layer fails as {@value #UNCLASSIFIED} with the error as the actual snippet.
     */
    public RecordResult compare(ObjectNode expected, ObjectNode actual, String adapterError) {
        String id = expected.path("id").asText();
        boolean command = "Command".equals(expected.path("kind").asText(null));
        List<LayerResult> out = new ArrayList<>(Layer.values().length);
        for (Layer l : Layer.values()) {
            if (command && !l.comparedOnCommands()) {
                out.add(new LayerResult(l, Status.EXCLUDED, null, null, null));
                continue;
            }
            ObjectNode exp = l.project(expected);
            if (actual == null) {
                boolean err = adapterError != null;
                Failure f = new Failure(id, l, l.rootPath(), JsonText.snippet(exp, SNIPPET),
                        err ? "<adapter error: " + adapterError + ">" : "<absent>",
                        err ? UNCLASSIFIED : NO_PORT, null);
                out.add(new LayerResult(l, Status.FAIL, f, exp, null));
                continue;
            }
            ObjectNode act = l.project(actual);
            Difference d = firstDifference("", exp, act);
            if (d == null) {
                out.add(new LayerResult(l, Status.PASS, null, exp, act));
                continue;
            }
            Optional<KnownDifferences.Entry> kd = known.match(id, l, d.path());
            String code = l.diagnostics() ? diagnosticCode(d.path(), exp, act) : null;
            Failure f = new Failure(id, l, d.path(), JsonText.snippet(d.expected(), SNIPPET),
                    JsonText.snippet(d.actual(), SNIPPET), kd.map(KnownDifferences.Entry::id).orElse(UNCLASSIFIED), code);
            out.add(new LayerResult(l, kd.isPresent() ? Status.KNOWN : Status.FAIL, f, exp, act));
        }
        return new RecordResult(id, command, out);
    }

    /** {@code code} of the diagnostic at the path's first index, expected side first. */
    static String diagnosticCode(String path, JsonNode exp, JsonNode act) {
        int bracket = path.indexOf('[');
        if (bracket < 0) {
            return null;
        }
        Matcher m = INDEX.matcher(path);
        if (!m.find(bracket)) {
            return null;
        }
        String key = path.substring(0, bracket);
        int i = Integer.parseInt(m.group(1));
        for (JsonNode side : new JsonNode[] {exp, act}) {
            JsonNode c = side == null ? null : side.path(key).path(i).get("code");
            if (c != null && c.isTextual()) {
                return c.textValue();
            }
        }
        return null;
    }

    /** First difference between {@code e} and {@code a}, or {@code null} when equal. */
    public static Difference firstDifference(String path, JsonNode e, JsonNode a) {
        boolean eAbsent = e == null || e.isMissingNode();
        boolean aAbsent = a == null || a.isMissingNode();
        if (eAbsent || aAbsent) {
            return eAbsent && aAbsent ? null : new Difference(path, orMissing(e), orMissing(a));
        }
        if (e.isNumber() && a.isNumber()) {
            return numbersEqual(e, a) ? null : new Difference(path, e, a);
        }
        if (e.getNodeType() != a.getNodeType()) {
            return new Difference(path, e, a);
        }
        switch (e.getNodeType()) {
            case OBJECT -> {
                for (Iterator<String> it = e.fieldNames(); it.hasNext();) {
                    String k = it.next();
                    Difference d = firstDifference(child(path, k), e.get(k), a.get(k));
                    if (d != null) {
                        return d;
                    }
                }
                for (Iterator<String> it = a.fieldNames(); it.hasNext();) {
                    String k = it.next();
                    if (!e.has(k)) {
                        return new Difference(child(path, k), MissingNode.getInstance(), a.get(k));
                    }
                }
                return null;
            }
            case ARRAY -> {
                int n = Math.min(e.size(), a.size());
                for (int i = 0; i < n; i++) {
                    Difference d = firstDifference(path + "[" + i + "]", e.get(i), a.get(i));
                    if (d != null) {
                        return d;
                    }
                }
                if (e.size() != a.size()) {
                    return new Difference(path + "[" + n + "]", orMissing(e.get(n)), orMissing(a.get(n)));
                }
                return null;
            }
            case STRING -> {
                return e.textValue().equals(a.textValue()) ? null : new Difference(path, e, a);
            }
            case BOOLEAN -> {
                return e.booleanValue() == a.booleanValue() ? null : new Difference(path, e, a);
            }
            case NULL -> {
                return null;
            }
            default -> {
                return e.equals(a) ? null : new Difference(path, e, a);
            }
        }
    }

    static boolean numbersEqual(JsonNode e, JsonNode a) {
        if (e.isIntegralNumber() && a.isIntegralNumber()) {
            return e.bigIntegerValue().equals(a.bigIntegerValue());
        }
        return e.decimalValue().compareTo(a.decimalValue()) == 0;
    }

    private static JsonNode orMissing(JsonNode n) {
        return n == null ? MissingNode.getInstance() : n;
    }

    private static String child(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }
}
