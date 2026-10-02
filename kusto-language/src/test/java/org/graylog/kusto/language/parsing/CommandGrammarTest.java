// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: parses command text with CommandGrammar (stubbed command parsers, PORTING 8, D10) and compares with the traps goldens.

package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.syntax.CommandBlock;
import org.graylog.kusto.language.syntax.ExpressionStatement;
import org.graylog.kusto.language.syntax.PipeExpression;
import org.graylog.kusto.language.syntax.SyntaxElement;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.UnknownCommand;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Parses command text with {@link CommandGrammar#commandBlock()}. The goldens come from an oracle built with upstream's
 * generated command grammar; Java has stub command grammars (no command parsers, PORTING.md section 8), so a golden
 * {@code CustomCommand} is an {@code UnknownCommand} of the same span in Java and only the rest of the tree is compared.
 * Records whose golden has no {@code CustomCommand} ({@code .foo}, {@code .}) are compared in full.
 */
class CommandGrammarTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RESOURCES = Paths.get(System.getProperty("kusto.conformanceResources",
            Paths.get("..", "kusto-language-conformance", "src", "test", "resources").toString()));

    /** Skips the test while QueryGrammar or GlobalState are not yet ported. */
    private static CommandGrammar grammar() {
        try {
            Class.forName("org.graylog.kusto.language.parsing.QueryGrammar");
            return CommandGrammar.from(GlobalState.default_());
        } catch (ClassNotFoundException | UnsupportedOperationException | LinkageError e) {
            Assumptions.abort("QueryGrammar/GlobalState not available: " + e);
            return null;
        }
    }

    @TestFactory
    List<DynamicNode> goldenCommandRecords() throws IOException {
        CommandGrammar grammar = grammar();
        Map<String, String> texts = readCorpus(RESOURCES.resolve("corpus").resolve("traps.jsonl"));
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode golden : readGoldens(RESOURCES.resolve("goldens").resolve("traps.jsonl.gz"))) {
            if (!"Command".equals(golden.get("kind").asText())) {
                continue;
            }
            String id = golden.get("id").asText();
            String text = texts.get(id);
            tests.add(DynamicTest.dynamicTest(id + " " + text, () -> {
                assertNotNull(text, id + ": no corpus text");
                String mismatch = compare(grammar, text, golden);
                if (mismatch != null) {
                    fail(id + ": " + mismatch);
                }
            }));
        }
        assertEquals(8, tests.size(), "command records in traps");
        return List.of(DynamicContainer.dynamicContainer("traps commands (" + tests.size() + ")", tests));
    }

    @Test
    void noCommandParsersDoesNotThrow() { // D10
        CommandGrammar grammar = grammar();
        assertEquals(0, grammar.createCommandParsers(grammar.predefinedRules()).length);

        CommandBlock block = parse(grammar, ".foo");
        assertEquals(4, block.fullWidth());
        assertFalse(block.hasSyntaxDiagnostics());
        assertFalse(block.containsSyntaxDiagnostics());
        assertTrue(firstExpression(block) instanceof UnknownCommand, "UnknownCommand expected");
    }

    @Test
    void showTablesIsUnknownCommand() {
        CommandBlock block = parse(grammar(), ".show tables");
        assertTrue(firstExpression(block) instanceof UnknownCommand);
        assertEquals(12, block.fullWidth());
    }

    @Test
    void showTablesPipesIntoQueryOperators() {
        CommandBlock block = parse(grammar(), ".show tables | where x == 1");
        PipeExpression pipe = (PipeExpression) firstExpression(block);
        assertTrue(pipe.expression() instanceof UnknownCommand);
        assertEquals(SyntaxKind.FilterOperator, pipe.operator().kind());
        assertEquals(27, block.fullWidth());
    }

    @Test
    void queryEmbeddedInCommandIsSwallowedIntoSkippedTokens() { // PORTING.md section 8 item 3
        String text = ".set-or-append T <| T | take 1";
        CommandBlock block = parse(grammar(), text);
        assertTrue(firstExpression(block) instanceof UnknownCommand);
        assertNotNull(block.skippedTokens());
        assertFalse(block.containsSyntaxDiagnostics());
        assertEquals(text.length(), block.fullWidth());
        assertEquals(text.indexOf("<|"), block.skippedTokens().textStart());
    }

    @Test
    void lonelyDotIsBadCommand() {
        CommandBlock block = parse(grammar(), ".");
        assertEquals(SyntaxKind.BadCommand, firstExpression(block).kind());
        List<Diagnostic> diagnostics = block.getContainedSyntaxDiagnostics();
        assertEquals(1, diagnostics.size());
        assertEquals("KS300", diagnostics.get(0).code());
    }

    @Test
    void fromReturnsTheCachedDefaultGrammar() {
        CommandGrammar first = grammar();
        assertSame(first, CommandGrammar.from(GlobalState.default_()));
        assertNull(first.globals()); // PORT-BUG mirrored: upstream never assigns Globals
    }

    private static org.graylog.kusto.language.syntax.Expression firstExpression(CommandBlock block) {
        ExpressionStatement statement = (ExpressionStatement) block.statements().get(0).element();
        return statement.expression();
    }

    private static CommandBlock parse(CommandGrammar grammar, String text) {
        ParseOptions options = ParseOptions.Default.withAlwaysProduceEndTokens(true);
        LexicalToken[] tokens = TokenParser.parseTokens(text, options);
        return SyntaxParsers.parseFirst(grammar.commandBlock(), Arrays.asList(tokens));
    }

    /** One pre-order entry. */
    private record Node(String kind, String name, int depth, int start, int end, boolean missing) {
        String describe() {
            return kind + (name.isEmpty() ? "" : ":" + name) + " [" + start + "," + end + ")" + (missing ? " missing" : "");
        }
    }

    private static List<Node> walk(SyntaxElement root) {
        List<Node> nodes = new ArrayList<>();
        ArrayDeque<Object[]> stack = new ArrayDeque<>();
        stack.push(new Object[] {root, "", 0});
        while (!stack.isEmpty()) {
            Object[] top = stack.pop();
            SyntaxElement e = (SyntaxElement) top[0];
            int depth = (Integer) top[2];
            nodes.add(new Node(e.kind().name(), (String) top[1], depth, e.textStart(), e.end(), e.isMissing()));
            for (int i = e.childCount() - 1; i >= 0; i--) {
                SyntaxElement child = e.getChild(i);
                if (child != null) {
                    stack.push(new Object[] {child, e.getName(i), depth + 1});
                }
            }
        }
        return nodes;
    }

    private static List<Node> goldenNodes(JsonNode tree) {
        List<Node> nodes = new ArrayList<>();
        for (JsonNode x : tree) {
            nodes.add(new Node(x.get("kind").asText(), x.get("name").asText(), x.get("depth").asInt(),
                    x.get("start").asInt(), x.get("end").asInt(), x.get("missing").asBoolean()));
        }
        return nodes;
    }

    /** Index of the first node of the given kind, or -1. */
    private static int indexOf(List<Node> nodes, String kind) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).kind().equals(kind)) {
                return i;
            }
        }
        return -1;
    }

    /** The nodes without the subtree rooted at {@code index}. */
    private static List<Node> withoutSubtree(List<Node> nodes, int index) {
        List<Node> result = new ArrayList<>(nodes.subList(0, index));
        int depth = nodes.get(index).depth();
        int i = index + 1;
        while (i < nodes.size() && nodes.get(i).depth() > depth) {
            i++;
        }
        result.addAll(nodes.subList(i, nodes.size()));
        return result;
    }

    /** Returns null on a match, else a description of the first mismatch. */
    private static String compare(CommandGrammar grammar, String text, JsonNode golden) {
        CommandBlock root = parse(grammar, text);
        if (root == null) {
            return "grammar produced no CommandBlock";
        }
        List<Node> actual = walk(root);
        List<Node> expected = goldenNodes(golden.get("tree"));

        int custom = indexOf(expected, "CustomCommand");
        if (custom >= 0) {
            // stub grammars: the command is an UnknownCommand over the same span
            int unknown = indexOf(actual, "UnknownCommand");
            if (unknown < 0) {
                return "no UnknownCommand in " + describeAll(actual);
            }
            Node e = expected.get(custom);
            Node a = actual.get(unknown);
            if (!e.name().equals(a.name()) || e.start() != a.start() || e.end() != a.end() || e.depth() != a.depth()) {
                return "command node: expected " + e.describe() + " at depth " + e.depth() + ", got " + a.describe() + " at depth " + a.depth();
            }
            expected = withoutSubtree(expected, custom);
            actual = withoutSubtree(actual, unknown);
        }

        int n = Math.max(expected.size(), actual.size());
        for (int i = 0; i < n; i++) {
            if (i >= actual.size()) {
                return "node[" + i + "] missing, expected " + expected.get(i).describe();
            }
            if (i >= expected.size()) {
                return "node[" + i + "] extra " + actual.get(i).describe();
            }
            if (!expected.get(i).equals(actual.get(i))) {
                return "node[" + i + "]: expected " + expected.get(i) + ", got " + actual.get(i);
            }
        }

        List<String> expectedCodes = new ArrayList<>();
        for (JsonNode d : golden.get("syntaxDiagnostics")) {
            expectedCodes.add(d.get("code").asText() + "@" + d.get("start").asInt() + "+" + d.get("length").asInt());
        }
        List<String> actualCodes = new ArrayList<>();
        for (Diagnostic d : root.getContainedSyntaxDiagnostics()) {
            actualCodes.add(d.code() + "@" + d.start() + "+" + d.length());
        }
        if (!expectedCodes.equals(actualCodes)) {
            return "syntax diagnostics: expected " + expectedCodes + ", got " + actualCodes;
        }
        return null;
    }

    private static String describeAll(List<Node> nodes) {
        StringBuilder sb = new StringBuilder();
        for (Node n : nodes) {
            sb.append(n.describe()).append("; ");
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
