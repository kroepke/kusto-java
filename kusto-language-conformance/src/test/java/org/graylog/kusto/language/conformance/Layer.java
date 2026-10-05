// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: comparison layers and the golden paths each one covers.

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Comparison layers, in gate order. Each layer compares a projection of the golden record that
 * keeps the record's own paths, so failure paths read like {@code tree[14].kind}.
 *
 * <p><b>Split of {@code tokens}.</b> {@link #TOKENS} compares {@code tokens[]} <em>without</em> the
 * {@code value} field of each token; {@link #TOKEN_VALUES} compares only those {@code value}
 * fields plus {@code outcome.tokenValues}. This lets the token-kind, offset, trivia, text and
 * token-diagnostic comparison be gated separately from literal-value parsing. The split
 * is a harness decision; golden-format.md describes the record, not the layers.
 *
 * <p>The top-level {@code id}, {@code kind} and {@code timing} fields belong to no layer:
 * {@code kind} only selects the Command restriction, {@code timing} is ignored.
 */
public enum Layer {
    /** {@code tokens[]} minus each token's {@code value}. */
    TOKENS("tokens (without value)", true, false),
    /** {@code fidelity}. */
    FIDELITY("fidelity", true, false),
    /** {@code tree}. */
    TREE("tree", false, false),
    /** {@code syntaxDiagnostics}. */
    SYNTAX_DIAGNOSTICS("syntaxDiagnostics", false, true),
    /** {@code outcome.parse}. */
    OUTCOME_PARSE("outcome.parse", false, false),
    /** {@code semanticDiagnostics}. */
    SEMANTIC_DIAGNOSTICS("semanticDiagnostics", false, true),
    /** {@code bind}. */
    BIND("bind", false, false),
    /** {@code resultType}. */
    RESULT_TYPE("resultType", false, false),
    /** {@code outcome.analyze}. */
    OUTCOME_ANALYZE("outcome.analyze", false, false),
    /** {@code tokens[].value} and {@code outcome.tokenValues}. */
    TOKEN_VALUES("tokens[].value, outcome.tokenValues", true, false);

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private final String paths;
    private final boolean comparedOnCommands;
    private final boolean diagnostics;

    Layer(String paths, boolean comparedOnCommands, boolean diagnostics) {
        this.paths = paths;
        this.comparedOnCommands = comparedOnCommands;
        this.diagnostics = diagnostics;
    }

    /** Human description of the covered golden paths. */
    public String paths() {
        return paths;
    }

    /** Whether records of kind {@code Command} are compared on this layer (golden-format.md "Command records"). */
    public boolean comparedOnCommands() {
        return comparedOnCommands;
    }

    /** Whether failures are further grouped by diagnostic code. */
    public boolean diagnostics() {
        return diagnostics;
    }

    /** The part of {@code record} this layer compares, as an object rooted like the record. */
    public ObjectNode project(ObjectNode record) {
        ObjectNode p = F.objectNode();
        switch (this) {
            case TOKENS -> {
                JsonNode tokens = record.get("tokens");
                if (tokens instanceof ArrayNode arr) {
                    ArrayNode out = p.putArray("tokens");
                    for (JsonNode t : arr) {
                        if (t instanceof ObjectNode o) {
                            ObjectNode c = o.deepCopy();
                            c.remove("value");
                            out.add(c);
                        } else {
                            out.add(t);
                        }
                    }
                } else if (tokens != null) {
                    p.set("tokens", tokens);
                }
            }
            case TOKEN_VALUES -> {
                JsonNode tokens = record.get("tokens");
                if (tokens instanceof ArrayNode arr) {
                    ArrayNode out = p.putArray("tokens");
                    for (JsonNode t : arr) {
                        ObjectNode v = out.addObject();
                        JsonNode value = t.get("value");
                        if (value != null) {
                            v.set("value", value);
                        }
                    }
                } else if (tokens != null) {
                    p.set("tokens", tokens);
                }
                copyOutcome(record, p, "tokenValues");
            }
            case FIDELITY -> copy(record, p, "fidelity");
            case TREE -> copy(record, p, "tree");
            case SYNTAX_DIAGNOSTICS -> copy(record, p, "syntaxDiagnostics");
            case SEMANTIC_DIAGNOSTICS -> copy(record, p, "semanticDiagnostics");
            case BIND -> copy(record, p, "bind");
            case RESULT_TYPE -> copy(record, p, "resultType");
            case OUTCOME_PARSE -> copyOutcome(record, p, "parse");
            case OUTCOME_ANALYZE -> copyOutcome(record, p, "analyze");
            default -> throw new AssertionError(this);
        }
        return p;
    }

    /** The golden key this layer's failures start under (for whole-record absence). */
    public String rootPath() {
        return switch (this) {
            case TOKENS, TOKEN_VALUES -> "tokens";
            case FIDELITY -> "fidelity";
            case TREE -> "tree";
            case SYNTAX_DIAGNOSTICS -> "syntaxDiagnostics";
            case SEMANTIC_DIAGNOSTICS -> "semanticDiagnostics";
            case BIND -> "bind";
            case RESULT_TYPE -> "resultType";
            case OUTCOME_PARSE -> "outcome.parse";
            case OUTCOME_ANALYZE -> "outcome.analyze";
        };
    }

    private static void copy(ObjectNode from, ObjectNode to, String key) {
        JsonNode v = from.get(key);
        if (v != null) {
            to.set(key, v);
        }
    }

    private static void copyOutcome(ObjectNode from, ObjectNode to, String key) {
        JsonNode outcome = from.get("outcome");
        if (outcome == null) {
            return;
        }
        if (!outcome.isObject()) {
            to.set("outcome", outcome);
            return;
        }
        ObjectNode o = to.putObject("outcome");
        JsonNode v = outcome.get(key);
        if (v != null) {
            o.set(key, v);
        }
    }
}
