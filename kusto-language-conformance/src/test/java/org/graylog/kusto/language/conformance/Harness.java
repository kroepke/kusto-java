// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: shared paths, system properties and JSON mapper of the conformance harness.

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shared configuration of the harness.
 *
 * <p>System properties: {@code kusto.basedir} (module directory; falls back to {@code basedir},
 * then {@code user.dir}), {@code kusto.goldens} (absolute goldens directory), {@code kusto.port}
 * (adapter class), {@code kusto.requireGoldens} ({@code true} makes a missing golden set fatal).
 * Blank values count as unset, because surefire passes undefined pom properties as empty strings.
 */
public final class Harness {
    /** Corpora in report order; other {@code corpus/*.jsonl} files follow alphabetically. */
    public static final List<String> KNOWN_CORPORA = List.of("readme", "docs", "sentinel", "traps");

    /** The one mapper; default Jackson settings keep lone surrogates from {@code \\uD800} escapes. */
    public static final ObjectMapper MAPPER = new ObjectMapper();

    /** Stack size of every thread that runs the port (PORTING.md section 6). */
    public static final long KQL_STACK = 16L << 20;

    private static volatile String upstreamCommit;

    private Harness() {
    }

    static String property(String name) {
        String v = System.getProperty(name);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** The {@code kusto-language-conformance} module directory. */
    public static Path moduleDir() {
        String v = property("kusto.basedir");
        if (v == null) {
            v = property("basedir");
        }
        Path p = Path.of(v != null ? v : System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (!Files.isDirectory(p.resolve("src/test/resources")) && Files.isDirectory(p.resolve("kusto-language-conformance"))) {
            p = p.resolve("kusto-language-conformance");
        }
        return p;
    }

    /** Repository root (parent of the module directory). */
    public static Path repoRoot() {
        return moduleDir().getParent();
    }

    public static Path resourcesDir() {
        return moduleDir().resolve("src/test/resources");
    }

    public static Path corpusDir() {
        return resourcesDir().resolve("corpus");
    }

    public static Path schemasDir() {
        return resourcesDir().resolve("schemas");
    }

    /** {@code kusto.goldens} if set, else {@code src/test/resources/goldens}. */
    public static Path goldensDir() {
        String v = property("kusto.goldens");
        return v != null ? Path.of(v).toAbsolutePath() : resourcesDir().resolve("goldens");
    }

    public static Path targetDir() {
        Path t = moduleDir().resolve("target");
        try {
            Files.createDirectories(t);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return t;
    }

    public static boolean requireGoldens() {
        return Boolean.parseBoolean(property("kusto.requireGoldens"));
    }

    /** {@code upstreamCommit} of {@code porting/manifest.json}. */
    public static String upstreamCommit() {
        String c = upstreamCommit;
        if (c == null) {
            c = readUpstreamCommit(repoRoot().resolve("porting/manifest.json"));
            upstreamCommit = c;
        }
        return c;
    }

    static String readUpstreamCommit(Path manifest) {
        try (JsonParser p = MAPPER.getFactory().createParser(manifest.toFile())) {
            if (p.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalStateException(manifest + ": not a JSON object");
            }
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                String name = p.currentName();
                p.nextToken();
                if (name.equals("upstreamCommit")) {
                    return p.getValueAsString();
                }
                p.skipChildren();
            }
            throw new IllegalStateException(manifest + ": no upstreamCommit");
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + manifest, e);
        }
    }

    /** Corpus names found in {@code corpus/}: known ones first, in {@link #KNOWN_CORPORA} order. */
    public static List<String> corpusNames() {
        List<String> found = new ArrayList<>();
        try (Stream<Path> s = Files.list(corpusDir())) {
            s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".jsonl"))
                    .map(n -> n.substring(0, n.length() - ".jsonl".length()))
                    .sorted()
                    .forEach(found::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> out = new ArrayList<>();
        for (String k : KNOWN_CORPORA) {
            if (found.remove(k)) {
                out.add(k);
            }
        }
        out.addAll(found);
        return out;
    }
}
