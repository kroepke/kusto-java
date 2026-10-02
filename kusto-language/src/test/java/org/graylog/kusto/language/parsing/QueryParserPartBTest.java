// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: compares QueryParser.parseQuery (query operators, statements, query block) against the full syntax trees in the .NET oracle's readme and traps goldens.

package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.syntax.SyntaxElement;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs {@code QueryParser.parseQuery} (with the options {@code KustoCode.Parse} uses,
 * {@code ParseOptions.Default.WithAlwaysProduceEndTokens(true)}) over every query record of the
 * {@code readme} and {@code traps} corpora and compares the complete pre-order tree (kind, child
 * name via {@code parent.getName(i)}, start, end, missing flag) with the golden {@code tree[]}
 * written by {@code kusto-oracle}. Only the first mismatch of a record is reported.
 */
class QueryParserPartBTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RESOURCES = Paths.get(System.getProperty("kusto.conformanceResources",
            Paths.get("..", "kusto-language-conformance", "src", "test", "resources").toString()));
    private static final String[] CORPORA = {"readme", "traps"};
    private static final ParseOptions OPTIONS = ParseOptions.Default.withAlwaysProduceEndTokens(true);

    @TestFactory
    List<DynamicNode> goldenTrees() throws IOException {
        List<DynamicNode> containers = new ArrayList<>();
        for (String corpus : CORPORA) {
            Map<String, String> texts = readCorpus(RESOURCES.resolve("corpus").resolve(corpus + ".jsonl"));
            List<DynamicTest> tests = new ArrayList<>();
            for (JsonNode golden : readGoldens(RESOURCES.resolve("goldens").resolve(corpus + ".jsonl.gz"))) {
                if (!"Query".equals(golden.get("kind").asText())) {
                    continue;
                }
                String id = golden.get("id").asText();
                String text = texts.get(id);
                tests.add(DynamicTest.dynamicTest(id, () -> {
                    if (text == null) {
                        fail(id + ": no corpus text");
                    }
                    String mismatch = compare(text, golden.get("tree"));
                    if (mismatch != null) {
                        fail(id + ": " + mismatch);
                    }
                }));
            }
            containers.add(DynamicContainer.dynamicContainer(corpus + " (" + tests.size() + ")", tests));
        }
        return containers;
    }

    /** Returns null on a match, else a description of the first mismatching node. */
    static String compare(String text, JsonNode expectedTree) {
        List<String> got = new ArrayList<>();
        try {
            SyntaxElement root = QueryParser.parseQuery(text, OPTIONS);
            if (root == null) {
                return "parseQuery returned null";
            }
            flatten(root, "", got);
        } catch (RuntimeException | StackOverflowError e) {
            return "parseQuery threw " + e;
        }
        int n = Math.max(expectedTree.size(), got.size());
        for (int i = 0; i < n; i++) {
            String e = i < expectedTree.size() ? describe(expectedTree.get(i)) : "<none>";
            String g = i < got.size() ? got.get(i) : "<none>";
            if (!e.equals(g)) {
                return "node " + i + ": expected " + e + " but was " + g;
            }
        }
        return null;
    }

    private static String describe(JsonNode n) {
        return describe(n.get("kind").asText(), n.get("name").asText(), n.get("start").asInt(), n.get("end").asInt(),
                n.get("missing").asBoolean());
    }

    private static String describe(String kind, String name, int start, int end, boolean missing) {
        return kind + "|" + name + "|" + start + "|" + end + "|" + missing;
    }

    /** Pre-order over nodes and tokens, as the oracle writes {@code tree[]} (absent optional children are skipped). */
    private static void flatten(SyntaxElement element, String name, List<String> out) {
        out.add(describe(element.kind().name(), name, element.textStart(), element.end(), element.isMissing()));
        for (int i = 0; i < element.childCount(); i++) {
            SyntaxElement child = element.getChild(i);
            if (child != null) {
                flatten(child, element.getName(i), out);
            }
        }
    }

    private static Map<String, String> readCorpus(Path path) throws IOException {
        Map<String, String> texts = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isEmpty()) {
                continue;
            }
            JsonNode rec = JSON.readTree(line);
            texts.put(rec.get("id").asText(), rec.get("text").asText());
        }
        return texts;
    }

    private static List<JsonNode> readGoldens(Path path) throws IOException {
        List<JsonNode> records = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(path)), StandardCharsets.UTF_8))) {
            String line = r.readLine(); // header
            if (line == null) {
                return records;
            }
            while ((line = r.readLine()) != null) {
                if (!line.isEmpty()) {
                    records.add(JSON.readTree(line));
                }
            }
        }
        return records;
    }
}
