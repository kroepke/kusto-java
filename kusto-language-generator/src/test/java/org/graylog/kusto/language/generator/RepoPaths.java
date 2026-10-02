// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: locates repository files from generator tests (module basedir is the working directory).
package org.graylog.kusto.language.generator;

import java.nio.file.Path;

final class RepoPaths {
    private RepoPaths() {
    }

    /** The module directory: surefire sets {@code basedir} and runs tests in it. */
    static Path moduleDir() {
        return Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    }

    static Path repoRoot() {
        return moduleDir().getParent();
    }

    static Path reference() {
        return repoRoot().resolve("porting/reference/GeneratedSyntaxNodes.cs");
    }

    static Path upstreamSource(String relative) {
        return repoRoot().resolve("upstream/kusto-query-language").resolve(relative);
    }

    static Path kustoLanguageSourceRoot() {
        return repoRoot().resolve("kusto-language/src/main/java");
    }
}
