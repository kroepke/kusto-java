// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: tests golden header validation and record loading.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenReaderTest {
    static final String SHA = "9d95a2d5bb085d151f14e88e07b703755fd914e1";

    static Path write(Path dir, String name, List<String> lines) throws IOException {
        Path p = dir.resolve(name);
        try (OutputStream o = new GZIPOutputStream(Files.newOutputStream(p))) {
            o.write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return p;
    }

    static String header(int version, String sha) {
        return "{\"golden\":" + version + ",\"upstream\":\"" + sha + "\",\"oracle\":{\"runtime\":\"net10.0\"},\"generatedAt\":\"2026-10-02T00:00:00Z\"}";
    }

    @Test
    void reads_records_in_order(@TempDir Path dir) throws IOException {
        Path p = write(dir, "x.jsonl.gz", List.of(header(1, SHA),
                "{\"id\":\"x/0002\",\"kind\":\"Query\",\"text\":\"\\uD800\"}", "{\"id\":\"x/0001\",\"kind\":\"Command\"}"));
        GoldenReader.Golden g = GoldenReader.readAll(p, SHA);
        assertEquals(SHA, g.header().upstream());
        assertEquals("net10.0", g.header().oracle().path("runtime").asText());
        assertEquals(List.of("x/0002", "x/0001"), List.copyOf(g.records().keySet()));
        assertEquals("\uD800", g.records().get("x/0002").get("text").textValue());
    }

    @Test
    void upstream_mismatch_fails_with_clear_message(@TempDir Path dir) throws IOException {
        Path p = write(dir, "x.jsonl.gz", List.of(header(1, "1".repeat(40))));
        GoldenReader.GoldenMismatchException e = assertThrows(GoldenReader.GoldenMismatchException.class,
                () -> GoldenReader.readAll(p, SHA));
        assertTrue(e.getMessage().contains("1".repeat(40)) && e.getMessage().contains(SHA)
                && e.getMessage().contains("upstreamCommit"), e.getMessage());
    }

    @Test
    void version_mismatch_fails(@TempDir Path dir) throws IOException {
        Path p = write(dir, "x.jsonl.gz", List.of(header(2, SHA)));
        assertThrows(GoldenReader.GoldenMismatchException.class, () -> GoldenReader.readAll(p, SHA));
        Path q = write(dir, "y.jsonl.gz", List.of("{\"id\":\"x/1\"}"));
        assertThrows(GoldenReader.GoldenMismatchException.class, () -> GoldenReader.readAll(q, SHA));
    }

    @Test
    void duplicate_ids_fail(@TempDir Path dir) throws IOException {
        Path p = write(dir, "x.jsonl.gz", List.of(header(1, SHA), "{\"id\":\"x/1\"}", "{\"id\":\"x/1\"}"));
        assertThrows(IllegalStateException.class, () -> GoldenReader.readAll(p, SHA));
    }

    @Test
    void goldens_dir_defaults_to_resources() {
        if (Harness.property("kusto.goldens") == null) {
            assertEquals(Harness.resourcesDir().resolve("goldens"), Harness.goldensDir());
        }
    }
}
