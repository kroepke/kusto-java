// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: reads corpus/<name>.jsonl files, keeping lone surrogates intact.

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads corpus files. Lone surrogates arrive as {@code \\uD800} escapes; Jackson decodes each
 * escape to one UTF-16 unit without pairing checks, so they survive ({@code CorpusReaderTest}).
 */
public final class CorpusReader {
    private CorpusReader() {
    }

    public static Path path(String corpus) {
        return Harness.corpusDir().resolve(corpus + ".jsonl");
    }

    public static boolean exists(String corpus) {
        return Files.isRegularFile(path(corpus));
    }

    /** Reads {@code corpus/<name>.jsonl} from the module's test resources. */
    public static List<CorpusRecord> read(String corpus) {
        return read(path(corpus));
    }

    public static List<CorpusRecord> read(Path file) {
        List<CorpusRecord> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null) {
                n++;
                if (line.isEmpty()) {
                    continue;
                }
                CorpusRecord rec;
                try {
                    rec = parseLine(line);
                } catch (RuntimeException e) {
                    throw new IllegalStateException(file + ":" + n + ": " + e.getMessage(), e);
                }
                if (!ids.add(rec.id())) {
                    throw new IllegalStateException(file + ":" + n + ": duplicate id " + rec.id());
                }
                out.add(rec);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
        return out;
    }

    /** All records of every corpus in {@code corpus/}, keyed by id, corpus order preserved. */
    public static Map<String, CorpusRecord> readAll() {
        Map<String, CorpusRecord> all = new LinkedHashMap<>();
        for (String name : Harness.corpusNames()) {
            for (CorpusRecord rec : read(name)) {
                all.put(rec.id(), rec);
            }
        }
        return all;
    }

    public static CorpusRecord parseLine(String line) {
        JsonNode n;
        try {
            n = Harness.MAPPER.readTree(line);
        } catch (IOException e) {
            throw new IllegalStateException("invalid JSON: " + e.getMessage(), e);
        }
        if (n == null || !n.isObject()) {
            throw new IllegalStateException("not a JSON object");
        }
        JsonNode id = n.get("id");
        JsonNode text = n.get("text");
        if (id == null || !id.isTextual() || text == null || !text.isTextual()) {
            throw new IllegalStateException("missing string 'id' or 'text'");
        }
        return new CorpusRecord(id.textValue(), text.textValue(), textOrNull(n.get("schema")),
                textOrNull(n.get("source")), n.path("crlf").asBoolean(false));
    }

    private static String textOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : n.asText();
    }
}
