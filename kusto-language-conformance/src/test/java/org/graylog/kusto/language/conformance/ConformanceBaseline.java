// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: loads conformance-baseline.json (gated layers and expected pass counts).

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@code {"gatedLayers": ["TOKENS"], "expected": {"readme": {"TOKENS": 7}}}}. A gated layer may
 * not regress below its expected pass count and may not have unclassified failures; other layers
 * are informational. Missing counts are 0.
 */
public record ConformanceBaseline(Set<Layer> gatedLayers, Map<String, Map<Layer, Integer>> expected) {

    public static Path defaultPath() {
        return Harness.resourcesDir().resolve("conformance-baseline.json");
    }

    public static ConformanceBaseline load() {
        return load(defaultPath());
    }

    public static ConformanceBaseline load(Path file) {
        JsonNode root;
        try {
            root = Harness.MAPPER.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
        Set<Layer> gated = EnumSet.noneOf(Layer.class);
        for (JsonNode n : root.path("gatedLayers")) {
            gated.add(layer(n.asText(), file));
        }
        Map<String, Map<Layer, Integer>> exp = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = root.path("expected").fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> c = it.next();
            Map<Layer, Integer> m = new LinkedHashMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> lt = c.getValue().fields(); lt.hasNext();) {
                Map.Entry<String, JsonNode> l = lt.next();
                m.put(layer(l.getKey(), file), l.getValue().asInt());
            }
            exp.put(c.getKey(), m);
        }
        return new ConformanceBaseline(gated, exp);
    }

    private static Layer layer(String name, Path file) {
        try {
            return Layer.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(file + ": unknown layer " + name, e);
        }
    }

    public int expected(String corpus, Layer layer) {
        return expected.getOrDefault(corpus, Map.of()).getOrDefault(layer, 0);
    }
}
