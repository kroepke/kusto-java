// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: compares TokenParser.parseTokens against the .NET oracle's golden lexical tokens.

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
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.DiagnosticLocationKind;
import org.graylog.kusto.language.ParseOptions;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Lexes every record of the {@code readme}, {@code traps}, {@code docs} and {@code sentinel} corpora with the options
 * {@code KustoCode.Parse} uses ({@code ParseOptions.Default.WithAlwaysProduceEndTokens(true)},
 * {@code KustoCode.cs:152,169}) and compares each token's kind, offsets, trivia, text and
 * diagnostics with the golden {@code tokens[]} written by {@code kusto-oracle} (token {@code value}
 * is out of scope here). Diagnostic positions follow the oracle's rendering
 * ({@code oracle/README.md}: Absolute as is, RelativeEnd at the token end, otherwise the token text span).
 */
class TokenParserGoldenTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RESOURCES = Paths.get(System.getProperty("kusto.conformanceResources",
            Paths.get("..", "kusto-language-conformance", "src", "test", "resources").toString()));
    private static final String[] CORPORA = {"readme", "traps", "docs", "sentinel"};

    @TestFactory
    List<DynamicNode> goldenTokens() throws IOException {
        List<DynamicNode> containers = new ArrayList<>();
        for (String corpus : CORPORA) {
            Map<String, String> texts = readCorpus(RESOURCES.resolve("corpus").resolve(corpus + ".jsonl"));
            List<JsonNode> goldens = readGoldens(RESOURCES.resolve("goldens").resolve(corpus + ".jsonl.gz"));
            List<DynamicTest> tests = new ArrayList<>(goldens.size());
            for (JsonNode golden : goldens) {
                String id = golden.get("id").asText();
                String text = texts.get(id);
                tests.add(DynamicTest.dynamicTest(id, () -> {
                    if (text == null) {
                        fail(id + ": no corpus text");
                    }
                    String mismatch = compare(text, golden.get("tokens"));
                    if (mismatch != null) {
                        fail(id + ": " + mismatch);
                    }
                }));
            }
            containers.add(DynamicContainer.dynamicContainer(corpus + " (" + tests.size() + ")", tests));
        }
        return containers;
    }

    /** Returns null on a match, else a description of the first mismatch. */
    static String compare(String text, JsonNode expectedTokens) {
        ParseOptions options = ParseOptions.Default.withAlwaysProduceEndTokens(true);
        LexicalToken[] tokens = TokenParser.parseTokens(text, options);
        int pos = 0;
        int n = Math.max(tokens.length, expectedTokens.size());
        for (int i = 0; i < n; i++) {
            if (i >= tokens.length) {
                return "token " + i + ": missing, expected " + expectedTokens.get(i);
            }
            LexicalToken t = tokens[i];
            int triviaStart = pos;
            int start = pos + t.trivia().length();
            int end = start + t.text().length();
            pos = end;
            if (i >= expectedTokens.size()) {
                return "token " + i + ": extra " + t.kind() + " '" + t.text() + "'";
            }
            JsonNode e = expectedTokens.get(i);
            String where = "token " + i + " ";
            if (!e.get("kind").asText().equals(t.kind().name())) {
                return where + "kind: expected " + e.get("kind").asText() + ", got " + t.kind().name() + " ('" + t.text() + "')";
            }
            if (e.get("triviaStart").asInt() != triviaStart) {
                return where + "triviaStart: expected " + e.get("triviaStart").asInt() + ", got " + triviaStart;
            }
            if (e.get("start").asInt() != start) {
                return where + "start: expected " + e.get("start").asInt() + ", got " + start;
            }
            if (e.get("end").asInt() != end) {
                return where + "end: expected " + e.get("end").asInt() + ", got " + end;
            }
            if (!e.get("trivia").asText().equals(t.trivia())) {
                return where + "trivia: expected " + JSON.valueToTree(e.get("trivia").asText()) + ", got " + JSON.valueToTree(t.trivia());
            }
            if (!e.get("text").asText().equals(t.text())) {
                return where + "text: expected " + JSON.valueToTree(e.get("text").asText()) + ", got " + JSON.valueToTree(t.text());
            }
            JsonNode ed = e.get("diagnostics");
            List<Diagnostic> dx = t.diagnostics();
            if (ed.size() != dx.size()) {
                return where + "diagnostics count: expected " + ed.size() + ", got " + dx.size();
            }
            for (int j = 0; j < dx.size(); j++) {
                Diagnostic d = dx.get(j);
                int dStart;
                int dLength;
                if (d.locationKind() == DiagnosticLocationKind.Absolute) {
                    dStart = d.start();
                    dLength = d.length();
                } else if (d.locationKind() == DiagnosticLocationKind.RelativeEnd) {
                    dStart = end;
                    dLength = 0;
                } else {
                    dStart = start;
                    dLength = end - start;
                }
                JsonNode x = ed.get(j);
                if (!Objects.equals(x.get("code").asText(), d.code())
                        || !Objects.equals(x.get("severity").asText(), d.severity())
                        || x.get("start").asInt() != dStart
                        || x.get("length").asInt() != dLength
                        || !Objects.equals(x.get("message").asText(), d.message())) {
                    return where + "diagnostic " + j + ": expected " + x + ", got {code=" + d.code() + ", severity="
                            + d.severity() + ", start=" + dStart + ", length=" + dLength + ", message=" + d.message() + "}";
                }
            }
        }
        if (pos != text.length()) {
            return "tokens cover " + pos + " of " + text.length() + " chars";
        }
        return null;
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
