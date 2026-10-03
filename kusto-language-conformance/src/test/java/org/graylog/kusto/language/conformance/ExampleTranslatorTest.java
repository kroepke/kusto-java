// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: API proof (PLAN.md 5.5): ExampleTranslator round-trips and annotates KQL.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.KustoCode;
import org.graylog.kusto.language.symbols.ClusterSymbol;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.DatabaseSymbol;
import org.graylog.kusto.language.symbols.FunctionSymbol;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.SchemaDisplay;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.graylog.kusto.language.parsing.LexicalToken;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.junit.jupiter.api.Test;

class ExampleTranslatorTest {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    private static GlobalState schema() {
        TableSymbol t = new TableSymbol("T",
                new ColumnSymbol("a", ScalarTypes.Long),
                new ColumnSymbol("b", ScalarTypes.String),
                new ColumnSymbol("ts", ScalarTypes.DateTime));
        FunctionSymbol f = new FunctionSymbol("f", "(x: long)", "{ x + 1 }");
        return GlobalState.default_()
                .withCluster(new ClusterSymbol("c", new DatabaseSymbol("db", t, f)))
                .withDatabase("db");
    }

    private static <T> T onKqlThread(java.util.concurrent.Callable<T> c) throws Exception {
        return KqlThread.call(c, TIMEOUT);
    }

    private static List<String> tokenTexts(String text) throws Exception {
        List<LexicalToken> tokens = onKqlThread(() -> KustoCode.parse(text, GlobalState.default_()).getLexicalTokens());
        List<String> out = new ArrayList<>();
        for (LexicalToken lt : tokens) {
            if (lt.kind() != SyntaxKind.EndOfTextToken) {
                out.add(lt.text());
            }
        }
        return out;
    }

    private static String annotate(String input, GlobalState globals) throws Exception {
        return onKqlThread(() -> ExampleTranslator.translate(
                KustoCode.parseAndAnalyze(input, globals), ExampleTranslator.Mode.ANNOTATED));
    }

    private static void assertAnnotatedRoundTrips(String input) throws Exception {
        GlobalState g = schema();
        String out = annotate(input, g);
        System.out.println("INPUT:     " + input + "\nANNOTATED:\n" + out + "---");
        KustoCode in = onKqlThread(() -> KustoCode.parseAndAnalyze(input, g));
        KustoCode re = onKqlThread(() -> KustoCode.parseAndAnalyze(out, g));
        assertEquals(List.of(), re.getDiagnostics(), "re-parse diagnostics for: " + out);
        assertEquals(SchemaDisplay.getText(in.resultType()), SchemaDisplay.getText(re.resultType()));
        assertTrue(out.contains("// result: "), out);
    }

    private static void assertWhitespaceMode(String input, GlobalState globals) throws Exception {
        String out = onKqlThread(() -> ExampleTranslator.translate(
                KustoCode.parseAndAnalyze(input, globals), ExampleTranslator.Mode.WHITESPACE_ONLY));
        assertEquals(tokenTexts(input), tokenTexts(out), "token sequence differs for: " + input);
        // Trivia is a single space or nothing: no newline or tab survives outside tokens.
        assertEquals(out.strip(), out);
    }

    @Test
    void whitespaceModeOnFixedInputs() throws Exception {
        GlobalState g = schema();
        for (String q : List.of("T | where a > 1 | project a, b", "T | summarize count() by b",
                "T | extend c = f(a) | project c", "T   |\n where   a>1 // note\n | take 3")) {
            assertWhitespaceMode(q, g);
        }
    }

    /** The readme corpus has only 7 records; top up to 20 from other schema-less corpora. */
    @Test
    void whitespaceModeOnCorpus() throws Exception {
        List<CorpusRecord> records = new ArrayList<>(CorpusReader.read("readme"));
        for (String name : Harness.corpusNames()) {
            if (!name.equals("readme")) {
                for (CorpusRecord r : CorpusReader.read(name)) {
                    if (r.schema() == null && records.size() < 20) {
                        records.add(r);
                    }
                }
            }
        }
        assertEquals(20, records.size());
        for (CorpusRecord r : records) {
            assertWhitespaceMode(r.text(), GlobalState.default_());
        }
    }

    @Test
    void annotatedWhereProject() throws Exception {
        assertAnnotatedRoundTrips("T | where a > 1 | project a, b");
    }

    @Test
    void annotatedSummarize() throws Exception {
        assertAnnotatedRoundTrips("T | summarize count() by b");
    }

    @Test
    void annotatedFunctionCall() throws Exception {
        assertAnnotatedRoundTrips("T | extend c = f(a) | project c");
        String out = annotate("T | extend c = f(a) | project c", schema());
        assertTrue(out.contains("// call f => "), out);
    }
}
