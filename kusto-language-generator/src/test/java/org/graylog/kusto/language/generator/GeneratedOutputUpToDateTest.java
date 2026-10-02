// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: drift test: the checked-in generated syntax nodes equal a fresh generation (PORTING.md 4.1).
package org.graylog.kusto.language.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Runs {@code GenerateSyntaxNodes --check} against {@code ../kusto-language/src/main/java}. Skipped while
 * that directory holds no generated files yet.
 */
class GeneratedOutputUpToDateTest {
    @Test
    void checked_in_output_is_up_to_date() {
        Path dir = GenerateSyntaxNodes.syntaxDirectory(RepoPaths.kustoLanguageSourceRoot());
        Assumptions.assumeTrue(Files.isRegularFile(dir.resolve("BinaryExpression.java")),
                "no generated files in " + dir);
        var buffer = new ByteArrayOutputStream();
        int status = GenerateSyntaxNodes.run(dir, true, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        assertEquals(0, status, buffer.toString(StandardCharsets.UTF_8));
    }
}
