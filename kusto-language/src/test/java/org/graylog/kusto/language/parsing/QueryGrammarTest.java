// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: parses the readme and traps corpora with QueryGrammar.QueryBlock and compares the tree with the oracle goldens.

package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.ParserKind;
import org.graylog.kusto.language.syntax.QueryBlock;
import org.graylog.kusto.language.syntax.SyntaxElement;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Parses every {@code Query} record of the {@code readme} and {@code traps} corpora with the
 * {@code QueryBlock} rule of {@link QueryGrammar} (the {@code ParserKind.Grammar} path of
 * {@code KustoCode.cs:225}) and compares the pre-order tree (kind, name, start, end, missing)
 * with the golden {@code tree[]}. The oracle showed {@code ParserKind.Grammar} equals the default
 * parser on the corpus, so any mismatch is a port defect. {@code Command} records are skipped
 * (they use {@code CommandGrammar}).
 */
class QueryGrammarTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RESOURCES = Paths.get(System.getProperty("kusto.conformanceResources",
            Paths.get("..", "kusto-language-conformance", "src", "test", "resources").toString()));
    private static final String[] CORPORA = {"readme", "traps"};

    /**
     * Records on which upstream's own {@code ParserKind.Grammar} path differs from the default parser that produced the
     * goldens. Each was verified record by record against the .NET oracle run with {@code ParserKind.Grammar}: the Java
     * grammar tree equals the .NET grammar tree (all 6,898 records of readme, traps, docs and sentinel, Command records
     * excluded), so these are upstream behaviours mirrored, not port defects. The test asserts that each listed record
     * still diverges from the golden, so the list cannot rot.
     */
    private static final Map<String, String> UPSTREAM_GRAMMAR_DIVERGENCES = Map.of(
            "traps/0355", "has_any: grammar builds HasAnyExpression with kind HasAnyKeyword (QueryGrammar.cs:1571), QueryParser uses HasAnyExpression",
            "traps/0361", "has_all: grammar builds HasAllExpression with kind HasAllKeyword (QueryGrammar.cs:1574), QueryParser uses HasAllExpression",
            "traps/0444", "'in [1,2]': the grammar's InOperatorExpressionList requires parentheses (QueryGrammar.cs:1515), QueryParser also accepts a bracketed list",
            "traps/0531", "'let' alone: grammar parses a LetStatement with missing parts, QueryParser an ExpressionStatement over the keyword",
            "traps/0551", "';': grammar yields a missing-expression statement spanning 0 chars, QueryParser a zero-width list",
            "traps/0552", "';;': as traps/0551, the second ';' becomes SkippedTokens",
            "traps/0631", "300 nested unary minus: the default parser's depth handling shifts the inner end offsets, the grammar has no such limit");

    @TestFactory
    List<DynamicNode> goldenTrees() throws IOException {
        QueryGrammar grammar = QueryGrammar.from(GlobalState.default_());
        List<DynamicNode> containers = new ArrayList<>();
        for (String corpus : CORPORA) {
            Map<String, String> texts = readCorpus(RESOURCES.resolve("corpus").resolve(corpus + ".jsonl"));
            List<JsonNode> goldens = readGoldens(RESOURCES.resolve("goldens").resolve(corpus + ".jsonl.gz"));
            List<DynamicTest> tests = new ArrayList<>(goldens.size());
            for (JsonNode golden : goldens) {
                String id = golden.get("id").asText();
                if ("Command".equals(golden.get("kind").asText())) {
                    continue;
                }
                String text = texts.get(id);
                tests.add(DynamicTest.dynamicTest(id, () -> {
                    if (text == null) {
                        fail(id + ": no corpus text");
                    }
                    String mismatch = compare(grammar, text, golden.get("tree"));
                    String divergence = UPSTREAM_GRAMMAR_DIVERGENCES.get(id);
                    if (divergence != null) {
                        if (mismatch == null) {
                            fail(id + ": listed as an upstream Grammar/Default divergence but now matches the golden; remove it: " + divergence);
                        }
                        return;
                    }
                    if (mismatch != null) {
                        fail(id + ": " + mismatch);
                    }
                }));
            }
            containers.add(DynamicContainer.dynamicContainer(corpus + " (" + tests.size() + ")", tests));
        }
        return containers;
    }

    @Test
    void grammarCacheIgnoresParserKind() {
        ParseOptions byDefault = ParseOptions.Default.withParserKind(ParserKind.Default);
        ParseOptions byGrammar = ParseOptions.Default.withParserKind(ParserKind.Grammar);
        QueryGrammar first = QueryGrammar.from(GlobalState.default_().withParseOptions(byDefault));
        QueryGrammar second = QueryGrammar.from(GlobalState.default_().withParseOptions(byGrammar));
        assertNotNull(first.queryBlock());
        assertSame(first, second);
    }

    @Test
    void parsesSimpleQuery() {
        QueryGrammar grammar = QueryGrammar.from(GlobalState.default_());
        QueryBlock block = parse(grammar, "T | where a > 1");
        assertNotNull(block);
        assertEquals(15, block.fullWidth());
    }

    private static QueryBlock parse(QueryGrammar grammar, String text) {
        ParseOptions options = ParseOptions.Default.withAlwaysProduceEndTokens(true);
        LexicalToken[] tokens = TokenParser.parseTokens(text, options);
        return SyntaxParsers.parseFirst(grammar.queryBlock(), Arrays.asList(tokens));
    }

    /** Returns null on a match, else a description of the first mismatch. */
    static String compare(QueryGrammar grammar, String text, JsonNode expected) {
        QueryBlock root = parse(grammar, text);
        if (root == null) {
            return "grammar produced no QueryBlock";
        }
        // pre-order walk: nodes and tokens, zero-width tokens included
        List<SyntaxElement> elements = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Integer> parents = new ArrayList<>();
        ArrayDeque<Object[]> stack = new ArrayDeque<>();
        stack.push(new Object[] {root, "", -1});
        while (!stack.isEmpty()) {
            Object[] top = stack.pop();
            SyntaxElement e = (SyntaxElement) top[0];
            int index = elements.size();
            elements.add(e);
            names.add((String) top[1]);
            parents.add((Integer) top[2]);
            for (int i = e.childCount() - 1; i >= 0; i--) {
                SyntaxElement child = e.getChild(i);
                if (child != null) {
                    stack.push(new Object[] {child, e.getName(i), index});
                }
            }
        }
        int n = Math.max(elements.size(), expected.size());
        for (int i = 0; i < n; i++) {
            if (i >= elements.size()) {
                return "tree[" + i + "]: missing, expected " + expected.get(i);
            }
            SyntaxElement e = elements.get(i);
            if (i >= expected.size()) {
                return where(elements, names, parents, i) + ": extra " + e.kind().name() + " [" + e.textStart() + "," + e.end() + ")";
            }
            JsonNode x = expected.get(i);
            if (!x.get("kind").asText().equals(e.kind().name())) {
                return where(elements, names, parents, i) + "kind: expected " + x.get("kind").asText() + ", got " + e.kind().name();
            }
            if (!x.get("name").asText().equals(names.get(i))) {
                return where(elements, names, parents, i) + "name: expected '" + x.get("name").asText() + "', got '" + names.get(i) + "'";
            }
            if (x.get("parent").asInt() != parents.get(i)) {
                return where(elements, names, parents, i) + "parent: expected " + x.get("parent").asInt() + ", got " + parents.get(i);
            }
            if (x.get("start").asInt() != e.textStart()) {
                return where(elements, names, parents, i) + "start: expected " + x.get("start").asInt() + ", got " + e.textStart();
            }
            if (x.get("end").asInt() != e.end()) {
                return where(elements, names, parents, i) + "end: expected " + x.get("end").asInt() + ", got " + e.end();
            }
            if (x.get("missing").asBoolean() != e.isMissing()) {
                return where(elements, names, parents, i) + "missing: expected " + x.get("missing").asBoolean() + ", got " + e.isMissing();
            }
        }
        return null;
    }

    private static String where(List<SyntaxElement> elements, List<String> names, List<Integer> parents, int i) {
        return "tree[" + i + "] " + path(elements, names, parents, i) + " ";
    }

    private static String path(List<SyntaxElement> elements, List<String> names, List<Integer> parents, int index) {
        StringBuilder sb = new StringBuilder();
        for (int i = index; i >= 0; i = parents.get(i)) {
            String segment = elements.get(i).kind().name() + (names.get(i).isEmpty() ? "" : ":" + names.get(i));
            sb.insert(0, sb.length() == 0 ? segment : segment + "/");
        }
        return sb.toString();
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
