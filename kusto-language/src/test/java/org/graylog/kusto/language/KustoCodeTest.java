// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: KustoCode.parse over the readme golden records (kind, round trip, fullWidth, lexical tokens, syntax diagnostics) plus unit tests.

package org.graylog.kusto.language;

import static org.junit.jupiter.api.Assertions.*;

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
import org.graylog.kusto.language.editor.CodeKinds;
import org.graylog.kusto.language.parsing.LexicalToken;
import org.graylog.kusto.language.syntax.IncludeTrivia;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.utils.dotnet.IntRef;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class KustoCodeTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RESOURCES = Paths.get(System.getProperty("kusto.conformanceResources",
            Paths.get("..", "kusto-language-conformance", "src", "test", "resources").toString()));

    private static Boolean parseAvailable;

    /**
     * True once QueryParser (part B) and the grammar classes are spliced in. Until then parse() hits a
     * PORT-PENDING stub or a missing class, which is not a failure of this port unit.
     */
    private static synchronized boolean parseAvailable() {
        if (parseAvailable == null) {
            try {
                KustoCode.parse("T | take 1");
                parseAvailable = Boolean.TRUE;
            } catch (UnsupportedOperationException | LinkageError e) {
                parseAvailable = Boolean.FALSE;
            }
        }
        return parseAvailable;
    }

    private static void assumeParse() {
        Assumptions.assumeTrue(parseAvailable(), "KustoCode.parse not available yet (QueryParser part B / grammars not spliced)");
    }

    // ---- no parser needed ----

    @Test
    void getKindClassifiesCommandsDirectivesAndQueries() {
        assertEquals(CodeKinds.Query, KustoCode.getKind(""));
        assertEquals(CodeKinds.Query, KustoCode.getKind("T | take 1"));
        assertEquals(CodeKinds.Query, KustoCode.getKind("   "));
        assertEquals(CodeKinds.Command, KustoCode.getKind(".show tables"));
        assertEquals(CodeKinds.Command, KustoCode.getKind("  // comment\n.show tables"));
        assertEquals(CodeKinds.Command, KustoCode.getKind("#connect cluster('x')\n.show tables"));
        assertEquals(CodeKinds.Query, KustoCode.getKind("#connect cluster('x')\nT | take 1"));
    }

    @Test
    void parseRejectsNullText() {
        assertThrows(NullPointerException.class, () -> KustoCode.parse(null));
        assertThrows(NullPointerException.class, () -> KustoCode.parse(null, GlobalState.default_()));
        assertThrows(NullPointerException.class, () -> KustoCode.parseAndAnalyze(null));
    }

    @Test
    void includeFunctionKindFlags() {
        assertEquals(1, IncludeFunctionKind.BuiltInFunctions);
        assertEquals(2, IncludeFunctionKind.DatabaseFunctions);
        assertEquals(4, IncludeFunctionKind.LocalFunctions);
        assertEquals(8, IncludeFunctionKind.LocalViews);
        assertEquals(0, IncludeFunctionKind.None);
        assertEquals(15, IncludeFunctionKind.All);
    }

    @Test
    void parserKindOrderMatchesUpstream() {
        assertEquals(ParserKind.Grammar, ParserKind.values()[0]);
        assertEquals(ParserKind.Default, ParserKind.values()[1]);
    }

    // ---- parse over goldens ----

    @TestFactory
    List<DynamicTest> readmeGoldens() throws IOException {
        Map<String, String> texts = readCorpus(RESOURCES.resolve("corpus").resolve("readme.jsonl"));
        List<JsonNode> goldens = readGoldens(RESOURCES.resolve("goldens").resolve("readme.jsonl.gz"));
        List<DynamicTest> tests = new ArrayList<>(goldens.size());
        for (JsonNode golden : goldens) {
            String id = golden.get("id").asText();
            String text = texts.get(id);
            tests.add(DynamicTest.dynamicTest(id, () -> {
                assertNotNull(text, id + ": no corpus text");
                assumeParse();
                check(id, text, golden);
            }));
        }
        return tests;
    }

    private static void check(String id, String text, JsonNode golden) {
        KustoCode code = KustoCode.parse(text);

        assertEquals(golden.get("kind").asText(), code.kind(), id + " kind");
        assertEquals(text, code.text(), id + " text");
        assertFalse(code.hasSemantics(), id + " hasSemantics");
        assertSame(ParserKind.Default, code.globals().parseOptions().parserKind());

        assertEquals(text, code.syntax().toString(IncludeTrivia.All), id + " round trip");
        assertEquals(golden.get("fidelity").get("roundTrip").asBoolean(), text.equals(code.syntax().toString(IncludeTrivia.All)), id + " roundTrip flag");
        assertEquals(golden.get("fidelity").get("fullWidth").asInt(), code.syntax().fullWidth(), id + " fullWidth");

        assertEquals(golden.get("tokens").size(), code.getLexicalTokens().size(), id + " lexical token count");
        int pos = 0;
        for (LexicalToken t : code.getLexicalTokens()) {
            pos += t.length();
        }
        assertEquals(text.length(), pos, id + " lexical tokens cover the text");

        List<String> expected = new ArrayList<>();
        for (JsonNode d : golden.get("syntaxDiagnostics")) {
            expected.add(d.get("code").asText());
        }
        List<String> actual = new ArrayList<>();
        for (Diagnostic d : code.getSyntaxDiagnostics()) {
            actual.add(d.code());
        }
        assertEquals(expected, actual, id + " syntax diagnostic codes");

        // the CAS-published caches return the same list on every call
        assertSame(code.getSyntaxDiagnostics(), code.getSyntaxDiagnostics());
        assertSame(code.getDiagnostics(), code.getDiagnostics());
    }

    // ---- instance members over a parsed query ----

    @Test
    void tokenIndexAndLineLookup() {
        assumeParse();
        String text = "T\n| where a > 1\n| take 2";
        KustoCode code = KustoCode.parse(text);
        assertTrue(code.globals().parseOptions().alwaysProduceEndToken());

        List<LexicalToken> tokens = code.getLexicalTokens();
        assertEquals(SyntaxKind.EndOfTextToken, tokens.get(tokens.size() - 1).kind());

        assertEquals(0, code.getTokenIndex(0));
        assertEquals(tokens.size() - 1, code.getTokenIndex(text.length() + 10));
        int idx = code.getTokenIndex(text.indexOf("where"));
        assertEquals("where", tokens.get(idx).text());

        IntRef line = new IntRef();
        IntRef offset = new IntRef();
        assertTrue(code.tryGetLineAndOffset(text.indexOf("where"), line, offset));
        assertEquals(2, line.value);
        assertEquals(3, offset.value);
        assertFalse(code.tryGetLineAndOffset(-1, line, offset));
        assertFalse(code.tryGetLineAndOffset(text.length() + 5, line, offset));
    }

    @Test
    void withGlobalsKeepsInstanceForSameGlobalsAndNoSemantics() {
        assumeParse();
        KustoCode code = KustoCode.parse("T | take 1");
        assertSame(code, code.withGlobals(code.globals()));
        assertEquals(KustoDialect.Query, code.dialect());
        assertEquals(KustoDialect.EngineCommand, KustoCode.parse(".show tables").dialect());
    }

    @Test
    void symbolQueriesWithoutSemanticsAreEmpty() {
        assumeParse();
        KustoCode code = KustoCode.parse("T | take 1");
        assertTrue(code.getSymbolsInScope(0).isEmpty());
        assertNull(code.getSpeculativeReferencedSymbol(0, "T"));
        assertNull(code.getColumnsInScope(0));
        assertNull(code.resultType());
    }

    // ---- loaders (same shape as TokenParserGoldenTest) ----

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
