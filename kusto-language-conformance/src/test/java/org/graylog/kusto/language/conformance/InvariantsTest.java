// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: checks the parse invariants on every corpus record through the port adapter.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.KustoCode;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.ParserKind;
import org.graylog.kusto.language.syntax.IncludeTrivia;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    /** Stack of the "small" depth threads: the 4 MB that PORTING.md section 6 documents for library users. */
    static final long SMALL_STACK = 4L << 20;

    /** {@code print (((1)))} with {@code n} parentheses. */
    static String nested(int n) {
        return "print " + "(".repeat(n) + "1" + ")".repeat(n);
    }

    /**
     * Parses {@code text} on a fresh thread with the given stack and returns the problem, or null when
     * {@code KustoCode.parse} did not throw and the tree round-trips with the full width.
     */
    static String parseProblem(String text, long stack, ParserKind kind) {
        Throwable[] error = new Throwable[1];
        String[] problem = new String[1];
        Thread t = new Thread(null, () -> {
            try {
                GlobalState g = GlobalState.default_().withParseOptions(ParseOptions.Default.withParserKind(kind));
                KustoCode code = KustoCode.parse(text, g);
                if (!code.syntax().toString(IncludeTrivia.All).equals(text)) {
                    problem[0] = "round trip differs";
                } else if (code.syntax().fullWidth() != text.length()) {
                    problem[0] = "fullWidth " + code.syntax().fullWidth() + " != " + text.length();
                }
            } catch (Throwable e) {
                error[0] = e;
            }
        }, "kql-depth", stack);
        t.start();
        try {
            t.join(60_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
        if (t.isAlive()) {
            return "still running after 60 s";
        }
        return error[0] != null ? "threw " + error[0] : problem[0];
    }

    /** PORTING.md section 6: {@code QueryParser.MaxDepth = 300}. */
    @ParameterizedTest
    @ValueSource(ints = {299, 300, 301})
    void parserDepthBoundary(int n) {
        assertNull(parseProblem(nested(n), Harness.KQL_STACK, ParserKind.Default), "16 MB, n=" + n);
        assertNull(parseProblem(nested(n), SMALL_STACK, ParserKind.Default), "4 MB, n=" + n);
    }

    /**
     * {@code ForwardParser.MaxCallDepth = 30} is only on the grammar path ({@code ParserKind.Grammar});
     * the default query parser never uses it. Nesting around the limit must degrade, not throw.
     */
    @ParameterizedTest
    @ValueSource(ints = {10, 29, 30, 31, 32, 299, 300, 301})
    void forwardParserDepthBoundary(int n) {
        assertNull(parseProblem(nested(n), Harness.KQL_STACK, ParserKind.Grammar), "16 MB, grammar, n=" + n);
        assertNull(parseProblem(nested(n), SMALL_STACK, ParserKind.Grammar), "4 MB, grammar, n=" + n);
    }

    /** {@code Properties.MaxAnalysisDepth = 500}: parsing must succeed; analysis is W6. */
    @ParameterizedTest
    @ValueSource(ints = {499, 500, 501, 1000})
    void analysisDepthBoundaryParses(int n) {
        assertNull(parseProblem(nested(n), Harness.KQL_STACK, ParserKind.Default), "16 MB, n=" + n);
        assertNull(parseProblem(nested(n), SMALL_STACK, ParserKind.Default), "4 MB, n=" + n);
    }
}
