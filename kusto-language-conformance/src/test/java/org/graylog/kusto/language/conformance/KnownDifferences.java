// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: loads known-differences.json and classifies comparison failures.

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Accepted Java/.NET differences ({@code src/test/resources/known-differences.json}):
 * <pre>{"differences": [{"id": "KD-001", "record": "docs/00*", "path": "tree\\[\\d+\\]\\.kind",
 *   "layer": "TREE", "dRow": "D12", "reason": "...", "expires": "&lt;upstream sha&gt;"}]}</pre>
 *
 * <ul>
 *   <li>{@code record}: a regex when it starts with {@code ^}, else a glob ({@code *} any run,
 *       {@code ?} one character); matched against the whole record id.</li>
 *   <li>{@code path}: a regex matched against the whole first differing path; absent or
 *       {@code *} matches any path.</li>
 *   <li>{@code layer}: a {@link Layer} name or {@code *}.</li>
 *   <li>{@code expires}: an upstream commit. Commits cannot be ordered here, so an entry is
 *       flagged "needs re-justification" when {@code expires} equals the current
 *       {@code upstreamCommit}; it is still applied.</li>
 * </ul>
 * The first matching entry in file order wins.
 */
public final class KnownDifferences {
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern DROW = Pattern.compile("D\\d+");

    /** One entry. */
    public record Entry(String id, String record, String path, String layer, String dRow, String reason,
            String expires, Pattern recordPattern, Pattern pathPattern) {

        public boolean matches(String recordId, Layer l, String failurePath) {
            return ("*".equals(layer) || l.name().equals(layer))
                    && recordPattern.matcher(recordId).matches()
                    && (pathPattern == null || pathPattern.matcher(failurePath).matches());
        }

        public boolean needsRejustification(String upstream) {
            return expires.equals(upstream);
        }
    }

    private final List<Entry> entries;

    private KnownDifferences(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static KnownDifferences empty() {
        return new KnownDifferences(List.of());
    }

    public static Path defaultPath() {
        return Harness.resourcesDir().resolve("known-differences.json");
    }

    public static KnownDifferences load() {
        return load(defaultPath());
    }

    public static KnownDifferences load(Path file) {
        try {
            return parse(Harness.MAPPER.readTree(Files.readString(file)), file.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    /** Parses and validates; any malformed entry is an {@link IllegalStateException}. */
    public static KnownDifferences parse(JsonNode root, String origin) {
        JsonNode list = root == null ? null : root.get("differences");
        if (list == null || !list.isArray()) {
            throw new IllegalStateException(origin + ": expected {\"differences\": [...]}");
        }
        List<Entry> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode n : list) {
            String id = req(n, "id", origin);
            String where = origin + " entry " + id;
            if (!ids.add(id)) {
                throw new IllegalStateException(where + ": duplicate id");
            }
            String record = req(n, "record", where);
            String path = n.path("path").asText(null);
            String layer = n.path("layer").asText("*");
            String dRow = req(n, "dRow", where);
            String reason = req(n, "reason", where);
            String expires = req(n, "expires", where);
            if (!"*".equals(layer)) {
                try {
                    Layer.valueOf(layer);
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException(where + ": unknown layer " + layer);
                }
            }
            if (!DROW.matcher(dRow).matches()) {
                throw new IllegalStateException(where + ": dRow must look like D12, got " + dRow);
            }
            if (!SHA.matcher(expires).matches()) {
                throw new IllegalStateException(where + ": expires must be a 40-hex upstream commit, got " + expires);
            }
            Pattern rp;
            Pattern pp;
            try {
                rp = Pattern.compile(record.startsWith("^") ? record : globToRegex(record));
                pp = path == null || path.isEmpty() || path.equals("*") ? null : Pattern.compile(path);
            } catch (PatternSyntaxException e) {
                throw new IllegalStateException(where + ": bad pattern: " + e.getMessage(), e);
            }
            out.add(new Entry(id, record, path, layer, dRow, reason, expires, rp, pp));
        }
        return new KnownDifferences(out);
    }

    private static String req(JsonNode n, String field, String where) {
        JsonNode v = n.get(field);
        if (v == null || !v.isTextual() || v.textValue().isEmpty()) {
            throw new IllegalStateException(where + ": missing string field '" + field + "'");
        }
        return v.textValue();
    }

    static String globToRegex(String glob) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                b.append(".*");
            } else if (c == '?') {
                b.append('.');
            } else {
                b.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return b.toString();
    }

    public List<Entry> entries() {
        return entries;
    }

    public Optional<Entry> match(String recordId, Layer layer, String path) {
        for (Entry e : entries) {
            if (e.matches(recordId, layer, path)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    /** Entries whose {@code expires} equals {@code upstream}. */
    public List<Entry> needingRejustification(String upstream) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.needsRejustification(upstream)) {
                out.add(e);
            }
        }
        return out;
    }
}
