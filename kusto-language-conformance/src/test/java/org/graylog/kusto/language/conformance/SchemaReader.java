// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: loads and caches schemas/<id>.json.

package org.graylog.kusto.language.conformance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Loads {@code schemas/<id>.json}; unknown fields are an error so typos surface. */
public final class SchemaReader {
    private static final Map<String, SchemaFile> CACHE = new ConcurrentHashMap<>();

    private SchemaReader() {
    }

    /** The schema for {@code id}, or {@code null} for a {@code null} id ({@code GlobalState.Default}). */
    public static SchemaFile get(String id) {
        return id == null ? null : CACHE.computeIfAbsent(id, k -> read(Harness.schemasDir().resolve(k + ".json"), k));
    }

    public static SchemaFile read(Path file, String expectedId) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("schema file missing: " + file);
        }
        SchemaFile s;
        try {
            s = Harness.MAPPER.readValue(file.toFile(), SchemaFile.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read schema " + file, e);
        }
        if (expectedId != null && !expectedId.equals(s.id())) {
            throw new IllegalStateException(file + ": id '" + s.id() + "' does not match file name '" + expectedId + "'");
        }
        return s;
    }
}
