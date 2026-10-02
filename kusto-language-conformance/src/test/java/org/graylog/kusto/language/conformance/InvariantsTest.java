// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: checks the parse invariants on every corpus record through the port adapter.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * {@link Invariants} over every record of every corpus (goldens not needed). Writes
 * {@code target/invariants-report.md} with violations and pathological (&gt; 200 ms) inputs.
 * Aborted while no port is available.
 */
class InvariantsTest {
    @Test
    void invariants() {
        PortAdapter port = PortAdapters.get();
        if (!PortAdapters.available(port)) {
            Assumptions.abort("no port yet");
        }
        List<String> violations = new ArrayList<>();
        List<String> pathological = new ArrayList<>();
        int records = 0;
        for (String corpus : Harness.corpusNames()) {
            for (CorpusRecord rec : CorpusReader.read(corpus)) {
                records++;
                Invariants.Check c = Invariants.check(port, rec);
                if (c.pathological()) {
                    pathological.add(String.format(Locale.ROOT, "%s: %.1f ms (%d chars)", rec.id(), c.parseMs(), rec.text().length()));
                }
                for (String v : c.violations()) {
                    violations.add(rec.id() + ": " + v);
                }
            }
        }
        StringBuilder md = new StringBuilder("# Invariants report\n\n");
        md.append("Records: ").append(records).append("; violations: ").append(violations.size())
                .append("; pathological (> ").append(Invariants.PATHOLOGICAL_MS).append(" ms): ").append(pathological.size()).append("\n\n");
        md.append("## Violations\n\n");
        violations.forEach(v -> md.append("- ").append(v).append('\n'));
        md.append("\n## Pathological inputs\n\n");
        pathological.forEach(p -> md.append("- ").append(p).append('\n'));
        try {
            Files.write(Harness.targetDir().resolve("invariants-report.md"),
                    JsonText.escapeLoneSurrogates(md.toString()).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("invariants: " + records + " records, " + violations.size() + " violations, "
                + pathological.size() + " pathological");
        pathological.forEach(p -> System.out.println("  pathological " + p));
        if (!violations.isEmpty()) {
            fail(violations.size() + " invariant violations (target/invariants-report.md):\n  "
                    + String.join("\n  ", violations.subList(0, Math.min(50, violations.size()))));
        }
    }
}
