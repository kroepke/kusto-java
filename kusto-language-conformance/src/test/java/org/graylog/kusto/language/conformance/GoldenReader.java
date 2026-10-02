// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: reads and validates goldens/<corpus>.jsonl.gz.

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Reads golden files: gzip JSON lines, line 1 the header, then one record per corpus line.
 *
 * <p>The header must have {@code golden == 1} and {@code upstream} equal to
 * {@code porting/manifest.json} {@code upstreamCommit}; otherwise {@link GoldenMismatchException}.
 *
 * <p>{@link #readAll} keeps every record as an {@link ObjectNode}; that is fine for readme, docs
 * and traps but the sentinel golden is ~37 MB compressed, so the conformance run uses
 * {@link #stream} and holds one record at a time.
 */
public final class GoldenReader {
    public static final int FORMAT_VERSION = 1;

    /** Header problems; fatal for the suite. */
    public static final class GoldenMismatchException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public GoldenMismatchException(String message) {
            super(message);
        }
    }

    /** A fully loaded golden. */
    public record Golden(GoldenHeader header, Map<String, ObjectNode> records) {
    }

    private GoldenReader() {
    }

    /** {@code <goldensDir>/<corpus>.jsonl.gz}. */
    public static Path path(String corpus) {
        return Harness.goldensDir().resolve(corpus + ".jsonl.gz");
    }

    public static boolean exists(String corpus) {
        return Files.isRegularFile(path(corpus));
    }

    /** Checks the header against {@code upstream}; returns it. */
    public static GoldenHeader validate(GoldenHeader h, String upstream, Path file) {
        if (h.golden() != FORMAT_VERSION) {
            throw new GoldenMismatchException(file + ": golden format version " + h.golden()
                    + ", harness expects " + FORMAT_VERSION + ". Regenerate with 'bash oracle/run.sh regenerate'.");
        }
        if (!upstream.equals(h.upstream())) {
            throw new GoldenMismatchException(file + ": golden was generated from upstream " + h.upstream()
                    + " but porting/manifest.json upstreamCommit is " + upstream
                    + ". Regenerate goldens ('bash oracle/run.sh regenerate') after the upstream bump.");
        }
        return h;
    }

    /** Streams records to {@code sink} in file order; returns the validated header. */
    public static GoldenHeader stream(Path file, String upstream, Consumer<ObjectNode> sink) {
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16), 1 << 16);
                MappingIterator<JsonNode> it = Harness.MAPPER.readerFor(JsonNode.class).readValues(in)) {
            if (!it.hasNextValue()) {
                throw new GoldenMismatchException(file + ": empty golden file (no header line)");
            }
            JsonNode headerNode = it.nextValue();
            if (!headerNode.isObject() || !headerNode.has("golden")) {
                throw new GoldenMismatchException(file + ": line 1 is not a golden header");
            }
            GoldenHeader header = validate(GoldenHeader.of(headerNode), upstream, file);
            int line = 1;
            while (it.hasNextValue()) {
                line++;
                JsonNode rec = it.nextValue();
                if (!(rec instanceof ObjectNode o) || !rec.path("id").isTextual()) {
                    throw new IllegalStateException(file + ": record " + line + " has no string 'id'");
                }
                sink.accept(o);
            }
            return header;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read golden " + file, e);
        }
    }

    /** Loads a whole golden; records keyed by id in file order. */
    public static Golden readAll(Path file, String upstream) {
        Map<String, ObjectNode> records = new LinkedHashMap<>();
        GoldenHeader h = stream(file, upstream, r -> {
            String id = r.get("id").textValue();
            if (records.put(id, r) != null) {
                throw new IllegalStateException(file + ": duplicate record id " + id);
            }
        });
        return new Golden(h, records);
    }

    public static Golden readAll(String corpus) {
        return readAll(path(corpus), Harness.upstreamCommit());
    }
}
