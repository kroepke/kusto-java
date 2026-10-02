// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: line 1 of a golden file (golden-format.md "Header line").

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Golden header.
 *
 * @param golden      format version, must be 1
 * @param upstream    upstream commit the oracle was built from
 * @param oracle      oracle build facts ({@code sourcesSha256}, {@code runtime}, ...)
 * @param generatedAt ISO-8601 timestamp
 */
public record GoldenHeader(int golden, String upstream, JsonNode oracle, String generatedAt) {
    static GoldenHeader of(JsonNode n) {
        return new GoldenHeader(n.path("golden").asInt(-1), n.path("upstream").asText(null),
                n.path("oracle"), n.path("generatedAt").asText(null));
    }
}
