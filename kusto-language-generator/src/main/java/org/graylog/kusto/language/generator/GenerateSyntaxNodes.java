// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: entry point of the SyntaxNode generator (write or --check the generated Java files).
package org.graylog.kusto.language.generator;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Generates the syntax node classes and visitors of {@code kusto-language} (PORTING.md 4.1).
 *
 * <pre>
 * GenerateSyntaxNodes [--check] &lt;source root&gt;     e.g. kusto-language/src/main/java
 * </pre>
 * Writes one file per class into {@code <source root>/org/graylog/kusto/language/syntax/}, keeping the
 * {@code <hand-written>} regions of existing files. Then writes the {@code LexicalTokenParsers} facade into
 * {@code <source root>/org/graylog/kusto/language/parsing/}, derived from {@code Parsers.java} there
 * ({@link LexicalTokenParsersGenerator}, PORTING.md 3.10, D7). With {@code --check} nothing is written: the
 * output is generated in memory and every file that differs from disk is listed (exit status 1).
 * Exit status: 0 success, 1 drift or region error, 2 usage.
 */
public final class GenerateSyntaxNodes {
    private GenerateSyntaxNodes() {
    }

    static final String REGENERATE_HINT = "regenerate with (from the repository root): "
            + "mvn -B -q -pl kusto-language-generator compile && java -cp kusto-language-generator/target/classes "
            + "org.graylog.kusto.language.generator.GenerateSyntaxNodes kusto-language/src/main/java";

    public static void main(String[] args) {
        boolean check = false;
        String root = null;
        for (String arg : args) {
            if (arg.equals("--check")) {
                check = true;
            } else if (root == null && !arg.startsWith("-")) {
                root = arg;
            } else {
                root = null;
                break;
            }
        }
        if (root == null) {
            System.err.println("usage: GenerateSyntaxNodes [--check] <source root, e.g. kusto-language/src/main/java>");
            System.exit(2);
            return;
        }
        int status;
        try {
            status = run(syntaxDirectory(Path.of(root)), check, System.out);
            status = Math.max(status, LexicalTokenParsersGenerator.run(Path.of(root), check, System.out));
        } catch (HandWrittenRegions.RegionException | UncheckedIOException | IllegalStateException e) {
            System.err.println("GenerateSyntaxNodes: " + e.getMessage());
            status = 1;
        }
        System.exit(status);
    }

    /** The package directory of the generated files below a source root. */
    public static Path syntaxDirectory(Path sourceRoot) {
        return sourceRoot.resolve(SyntaxNodeGenerator.PACKAGE.replace('.', '/'));
    }

    /**
     * Generates into (or, with {@code check}, compares against) {@code dir}.
     *
     * @return the exit status: 0 when written or up to date, 1 when {@code check} found differences
     * @throws HandWrittenRegions.RegionException when an existing file's regions are not the expected ones
     */
    public static int run(Path dir, boolean check, PrintStream out) {
        Map<String, String> files = SyntaxNodeGenerator.generate(
                SyntaxNodeInfos.All, SyntaxNodeInfos.KnownTypes, name -> read(dir.resolve(name)));

        int nodes = 0;
        int abstractNodes = 0;
        for (SyntaxNodeInfo c : SyntaxNodeInfos.All) {
            nodes++;
            if (c.Abstract) {
                abstractNodes++;
            }
        }
        int visitors = SyntaxNodeGenerator.VISITORS.size();

        var differing = new ArrayList<String>();
        int unchanged = 0;
        for (var entry : files.entrySet()) {
            String current = read(dir.resolve(entry.getKey()));
            if (entry.getValue().equals(current)) {
                unchanged++;
            } else {
                differing.add((current == null ? "missing: " : "differs: ") + entry.getKey());
            }
        }
        List<String> stale = staleFiles(dir, files.keySet());

        if (check) {
            for (String d : differing) {
                out.println(d);
            }
            for (String s : stale) {
                out.println("stale:   " + s + " (generated header, but no such node)");
            }
            out.printf("checked %d files (%d nodes, %d abstract; %d visitors): %d up to date, %d out of date, %d stale%n",
                    files.size(), nodes, abstractNodes, visitors, unchanged, differing.size(), stale.size());
            if (!differing.isEmpty() || !stale.isEmpty()) {
                out.println(REGENERATE_HINT);
                return 1;
            }
            return 0;
        }

        try {
            Files.createDirectories(dir);
            for (var entry : files.entrySet()) {
                Path file = dir.resolve(entry.getKey());
                if (!entry.getValue().equals(read(file))) {
                    Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (String s : stale) {
            out.println("warning: stale generated file " + s + " (no such node; not deleted)");
        }
        out.printf("generated %d files (%d nodes, %d abstract; %d visitors) into %s: %d written, %d unchanged%n",
                files.size(), nodes, abstractNodes, visitors, dir, differing.size(), unchanged);
        return 0;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Files in {@code dir} that carry the generated-file header but are not produced any more. */
    private static List<String> staleFiles(Path dir, java.util.Set<String> generated) {
        var stale = new ArrayList<String>();
        if (!Files.isDirectory(dir)) {
            return stale;
        }
        String marker = "// Ported from: " + SyntaxNodeGenerator.TEMPLATE;
        try (Stream<Path> list = Files.list(dir)) {
            for (Path p : list.sorted().toList()) {
                String name = p.getFileName().toString();
                if (name.endsWith(".java") && !generated.contains(name)) {
                    String text = read(p);
                    if (text != null && text.startsWith(marker)) {
                        stale.add(name);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return stale;
    }
}
