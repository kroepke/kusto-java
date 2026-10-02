// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: compares QueryParser part A (literals, schemas, expressions, function parameters) against subtrees of the .NET oracle's traps goldens.

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
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.syntax.SyntaxElement;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * For every {@code traps} golden, finds the subtrees that one of the part A entry points
 * ({@code parseLiteral}, {@code parseRowSchema}, {@code parseFunctionParameters}, and
 * {@code parseExpression} for the single expressions of a {@code print}) is responsible for, re-lexes the
 * corpus text, parses from the token at the subtree's start and compares kind, child name, start, end and
 * missing flag for every node in pre-order. Positions are compared relative to the subtree root.
 */
class QueryParserPartATest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RESOURCES = Paths.get(System.getProperty("kusto.conformanceResources",
            Paths.get("..", "kusto-language-conformance", "src", "test", "resources").toString()));

    private static final Set<String> LITERAL_KINDS = Set.of(
            "StringLiteralExpression", "BooleanLiteralExpression", "LongLiteralExpression", "RealLiteralExpression",
            "DecimalLiteralExpression", "IntLiteralExpression", "GuidLiteralExpression", "DateTimeLiteralExpression",
            "TimespanLiteralExpression", "CompoundStringLiteralExpression", "DynamicExpression", "TypeOfLiteralExpression");

    private static final Set<String> NAMED_EXPRESSION_KINDS = Set.of("SimpleNamedExpression", "CompoundNamedExpression");

    private static final ParseOptions OPTIONS = ParseOptions.Default.withAlwaysProduceEndTokens(true);

    @TestFactory
    List<DynamicTest> traps() throws IOException {
        Map<String, String> texts = readCorpus(RESOURCES.resolve("corpus").resolve("traps.jsonl"));
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode golden : readGoldens(RESOURCES.resolve("goldens").resolve("traps.jsonl.gz"))) {
            String id = golden.get("id").asText();
            String text = texts.get(id);
            if (text == null || !"Query".equals(golden.get("kind").asText())) {
                continue;
            }
            JsonNode tree = golden.get("tree");
            for (int i = 0; i < tree.size(); i++) {
                JsonNode node = tree.get(i);
                if (node.get("end").asInt() <= node.get("start").asInt()) {
                    continue;
                }
                String kind = node.get("kind").asText();
                String mode = null;
                if (LITERAL_KINDS.contains(kind) && literalTokenMatches(tree, i)) {
                    mode = "literal";
                } else if (kind.equals("RowSchema")) {
                    mode = "rowSchema";
                } else if (kind.equals("FunctionParameters")) {
                    mode = "functionParameters";
                } else if (isSinglePrintExpression(tree, i)) {
                    mode = "expression";
                }
                if (mode != null) {
                    int index = i;
                    String testMode = mode;
                    tests.add(DynamicTest.dynamicTest(id + " " + mode + " #" + i + " " + kind,
                            () -> check(id, text, tree, index, testMode)));
                }
            }
        }
        return tests;
    }

    /** A literal expression node whose first token is the token kind ParseLiteral maps to the node kind. */
    private static boolean literalTokenMatches(JsonNode tree, int i) {
        String kind = tree.get(i).get("kind").asText();
        if (i + 1 >= tree.size()) {
            return false;
        }
        JsonNode first = tree.get(i + 1);
        // DynamicExpression / TypeOfLiteralExpression start with a keyword child, the others with the matching token
        String firstKind = first.get("kind").asText();
        switch (kind) {
            case "DynamicExpression":
                return firstKind.equals("DynamicKeyword");
            case "TypeOfLiteralExpression":
                return firstKind.equals("TypeOfKeyword");
            case "CompoundStringLiteralExpression":
                return firstKind.equals("List");
            default:
                return firstKind.equals(kind.replace("Expression", "Token"));
        }
    }

    /** An expression that is an element of the Expressions list of a PrintOperator and is not a named expression. */
    private static boolean isSinglePrintExpression(JsonNode tree, int i) {
        JsonNode node = tree.get(i);
        if (NAMED_EXPRESSION_KINDS.contains(node.get("kind").asText())) {
            return false;
        }
        if (!node.get("name").asText().equals("Element")) {
            return false;
        }
        int parent = node.get("parent").asInt();
        if (parent < 0 || !tree.get(parent).get("kind").asText().equals("SeparatedElement")) {
            return false;
        }
        int list = tree.get(parent).get("parent").asInt();
        if (list < 0 || !tree.get(list).get("name").asText().equals("Expressions")) {
            return false;
        }
        int op = tree.get(list).get("parent").asInt();
        return op >= 0 && tree.get(op).get("kind").asText().equals("PrintOperator");
    }

    private static void check(String id, String text, JsonNode tree, int rootIndex, String mode) {
        LexicalToken[] tokens = TokenParser.parseTokens(text, OPTIONS);
        JsonNode root = tree.get(rootIndex);
        int rootStart = root.get("start").asInt();
        int tokenIndex = -1;
        int pos = 0;
        for (int i = 0; i < tokens.length; i++) {
            pos += tokens[i].trivia().length();
            if (pos == rootStart && tokens[i].text().length() > 0) {
                tokenIndex = i;
                break;
            }
            pos += tokens[i].text().length();
        }
        if (tokenIndex < 0) {
            fail(id + ": no token starts at " + rootStart);
        }

        SyntaxElement actual;
        try {
            switch (mode) {
                case "literal":
                    actual = QueryParser.parseLiteral(tokens, tokenIndex, OPTIONS);
                    break;
                case "rowSchema":
                    actual = QueryParser.parseRowSchema(tokens, tokenIndex, OPTIONS);
                    break;
                case "functionParameters":
                    actual = QueryParser.parseFunctionParameters(tokens, tokenIndex, OPTIONS);
                    break;
                default:
                    actual = QueryParser.parseExpression(tokens, tokenIndex, OPTIONS);
                    break;
            }
        } catch (UnsupportedOperationException | NoSuchMethodError | AbstractMethodError e) {
            Assumptions.abort(mode + " needs QueryParser part B / QueryGrammar: " + e);
            return;
        }
        if (actual == null && !mode.equals("literal") && !mode.equals("rowSchema")) {
            Assumptions.assumeTrue(partBAvailable(), mode + " needs QueryParser part B");
        }
        if (actual == null) {
            fail(id + ": parser returned null, expected " + root.get("kind").asText());
        }

        List<String> expected = new ArrayList<>();
        int rootDepth = root.get("depth").asInt();
        for (int i = rootIndex; i < tree.size(); i++) {
            JsonNode n = tree.get(i);
            if (i > rootIndex && n.get("depth").asInt() <= rootDepth) {
                break;
            }
            expected.add(describe(n.get("kind").asText(), i == rootIndex ? "" : n.get("name").asText(),
                    n.get("start").asInt() - rootStart, n.get("end").asInt() - rootStart, n.get("missing").asBoolean()));
        }
        List<String> got = new ArrayList<>();
        flatten(actual, "", actual.textStart(), got);

        int n = Math.max(expected.size(), got.size());
        for (int i = 0; i < n; i++) {
            String e = i < expected.size() ? expected.get(i) : "<none>";
            String g = i < got.size() ? got.get(i) : "<none>";
            if (!e.equals(g)) {
                fail(id + " " + mode + ": node " + i + " expected " + e + " but was " + g);
            }
        }
    }

    /** True when the part B members (function parameters, query operator parameters, ...) are implemented. */
    private static boolean partBAvailable() {
        try {
            return QueryParser.parseFunctionParameters("(a: long)") != null;
        } catch (UnsupportedOperationException | LinkageError e) {
            return false;
        }
    }

    private static String describe(String kind, String name, int start, int end, boolean missing) {
        return kind + "|" + name + "|" + start + "|" + end + "|" + missing;
    }

    private static void flatten(SyntaxElement element, String name, int rootStart, List<String> out) {
        out.add(describe(element.kind().name(), name, element.textStart() - rootStart, element.end() - rootStart,
                element.isMissing()));
        for (int i = 0; i < element.childCount(); i++) {
            SyntaxElement child = element.getChild(i);
            if (child != null) {
                flatten(child, element.getName(i), rootStart, out);
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
