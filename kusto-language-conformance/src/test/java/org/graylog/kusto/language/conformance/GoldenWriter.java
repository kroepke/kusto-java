// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: Java side of golden-format.md; renders the port's output like oracle/kusto-oracle/src/Golden.cs.

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.DiagnosticLocationKind;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.editor.CodeKinds;
import org.graylog.kusto.language.parsing.LexicalToken;
import org.graylog.kusto.language.parsing.TextFacts;
import org.graylog.kusto.language.parsing.TokenParser;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.SyntaxToken;
import org.graylog.kusto.language.utils.dotnet.DotNet;

/**
 * Renders the port's output as golden records (the default {@code kusto.port}).
 * Normative: golden-format.md plus oracle/README.md "Rendering decisions"; the token section
 * mirrors {@code oracle/kusto-oracle/src/Golden.cs} {@code WriteRecord} line for line.
 *
 * <p><b>Layers available at W2 (lexer).</b> Only {@code id}, {@code kind}, {@code tokens},
 * {@code fidelity} and {@code outcome.tokenValues} come from the port. The parser (W4) and the
 * binder (W6) do not exist yet, so:
 * <ul>
 *   <li>{@code tree}, {@code syntaxDiagnostics}, {@code semanticDiagnostics}, {@code bind} are
 *       empty arrays and {@code resultType} is {@code null};</li>
 *   <li>{@code outcome.parse} is {@code "ok"} (lexing never throws; it is what the invariants
 *       check) and {@code outcome.analyze} is {@value #ANALYZE_UNAVAILABLE}.</li>
 * </ul>
 * These placeholders are well-formed output, so the comparator reports the unavailable layers
 * as ordinary differing records (cause {@code unclassified} on non-gated layers), never as an
 * adapter crash; they do not fail the build because those layers are not gated.
 *
 * <p><b>W2 approximation of {@code fidelity}.</b> {@code roundTrip} is
 * {@code concat(trivia + text over all lexical tokens) == text} and {@code fullWidth} is the
 * sum of the token lengths. golden-format.md defines both on the syntax root
 * ({@code root.ToString(IncludeTrivia.All)}, {@code root.FullWidth}); from W4 on they must be
 * computed from the root of {@code KustoCode.parseAndAnalyze} instead.
 *
 * <p><b>Exceptions.</b> A token value that throws renders as {@code "!<.NET name>"} and sets
 * {@code outcome.tokenValues} to {@code "throw:<mapped name>"}, exactly as the oracle: the Java
 * exception class (or its nearest superclass named in the {@code java} table of
 * {@code porting/exception-map.json}) is mapped to the first .NET candidate, and that .NET name
 * is mapped back through the {@code dotnet} table for the outcome (unmapped names unchanged).
 */
public final class GoldenWriter implements PortAdapter {
    /** {@code outcome.analyze} while the binder is not ported. */
    public static final String ANALYZE_UNAVAILABLE = "skipped:no-port";

    private static volatile ExceptionNames exceptionNames;

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public ObjectNode write(CorpusRecord rec, SchemaFile schema) {
        String text = rec.text();
        long t0 = System.nanoTime();
        LexicalToken[] tokens = lex(text);
        double parseMs = (System.nanoTime() - t0) / 1e6;

        ObjectNode r = Harness.MAPPER.createObjectNode();
        r.put("id", rec.id());
        r.put("kind", getKind(text));

        String tokenValuesOutcome = "ok";
        ArrayNode tokenArray = r.putArray("tokens");
        StringBuilder roundTrip = new StringBuilder(text.length());
        int pos = 0;
        for (LexicalToken lt : tokens) {
            int triviaStart = pos;
            int start = pos + lt.trivia().length();
            int end = start + lt.text().length();
            pos = end;
            roundTrip.append(lt.trivia()).append(lt.text());

            ObjectNode t = tokenArray.addObject();
            t.put("kind", lt.kind().name());
            t.put("triviaStart", triviaStart);
            t.put("start", start);
            t.put("end", end);
            t.put("trivia", lt.trivia());
            t.put("text", lt.text());
            String value = null;
            try {
                SyntaxToken st = SyntaxToken.from(lt);
                if (st.isLiteral()) {
                    Object v = st.value();
                    value = v == null ? null : DotNet.str(v);
                }
            } catch (RuntimeException | StackOverflowError ex) {
                ExceptionNames names = exceptionNames();
                String dotnet = names.dotnetName(ex);
                value = "!" + dotnet;
                if (tokenValuesOutcome.equals("ok")) {
                    tokenValuesOutcome = "throw:" + names.mapToJava(dotnet);
                }
            }
            t.put("value", value);
            ArrayNode dx = t.putArray("diagnostics");
            for (Diagnostic d : lt.diagnostics()) {
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
                writeDiagnostic(dx, d, dStart, dLength);
            }
        }

        ObjectNode fidelity = r.putObject("fidelity");
        fidelity.put("roundTrip", roundTrip.toString().equals(text));
        fidelity.put("fullWidth", pos);

        r.putArray("tree");
        r.putArray("syntaxDiagnostics");
        r.putArray("semanticDiagnostics");
        r.putArray("bind");
        r.putNull("resultType");

        ObjectNode outcome = r.putObject("outcome");
        outcome.put("parse", "ok");
        outcome.put("analyze", ANALYZE_UNAVAILABLE);
        outcome.put("tokenValues", tokenValuesOutcome);

        ObjectNode timing = r.putObject("timing");
        timing.put("parseMs", Math.round(parseMs * 1000.0) / 1000.0);
        timing.put("analyzeMs", 0.0);
        return r;
    }

    @Override
    public int[] tokenStarts(String text) {
        LexicalToken[] tokens = lex(text);
        List<Integer> starts = new ArrayList<>(tokens.length);
        int pos = 0;
        for (LexicalToken lt : tokens) {
            int start = pos + lt.trivia().length();
            pos = start + lt.text().length();
            if (lt.kind() != SyntaxKind.EndOfTextToken) {
                starts.add(start);
            }
        }
        int[] out = new int[starts.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = starts.get(i);
        }
        return out;
    }

    /** The lexical tokens {@code KustoCode.Parse} produces ({@code KustoCode.cs:152}: end tokens always on). */
    static LexicalToken[] lex(String text) {
        return TokenParser.parseTokens(text, ParseOptions.Default.withAlwaysProduceEndTokens(true));
    }

    /**
     * {@code KustoCode.GetKind(text)} ({@code KustoCode.cs:331-359}): {@code Command} when the first
     * token after any directive lines is a {@code .}, else {@code Query}. Never {@code Directive}.
     */
    static String getKind(String text) {
        int position = 0;
        while (position < text.length()) {
            LexicalToken token = TokenParser.parseToken(text, position);
            if (token != null) {
                if (token.kind() == SyntaxKind.DotToken) {
                    return CodeKinds.Command;
                }
                if (token.kind() == SyntaxKind.DirectiveToken) {
                    // skip directive line and continue looking
                    int nextStart = TextFacts.getNextLineStart(text, position + token.length());
                    if (nextStart > position) {
                        position = nextStart;
                        continue;
                    }
                }
            }
            break;
        }
        return CodeKinds.Query;
    }

    private static void writeDiagnostic(ArrayNode into, Diagnostic d, int start, int length) {
        ObjectNode o = into.addObject();
        o.put("code", d.code());
        o.put("severity", d.severity());
        o.put("start", start);
        o.put("length", length);
        o.put("message", d.message());
    }

    static ExceptionNames exceptionNames() {
        ExceptionNames n = exceptionNames;
        if (n == null) {
            n = ExceptionNames.load(Harness.repoRoot().resolve("porting/exception-map.json"));
            exceptionNames = n;
        }
        return n;
    }

    /** The two tables of {@code porting/exception-map.json}. */
    record ExceptionNames(Map<String, String> dotnetToJava, Map<String, String> javaToDotnet) {
        static ExceptionNames load(Path file) {
            JsonNode root;
            try {
                root = Harness.MAPPER.readTree(Files.readString(file));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + file, e);
            }
            Map<String, String> d2j = new HashMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = root.path("dotnet").fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> e = it.next();
                d2j.put(e.getKey(), e.getValue().asText());
            }
            Map<String, String> j2d = new HashMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = root.path("java").fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonNode candidates = e.getValue();
                if (candidates.isArray() && !candidates.isEmpty()) {
                    j2d.put(e.getKey(), candidates.get(0).asText());
                }
            }
            return new ExceptionNames(d2j, j2d);
        }

        /** The .NET type name for a Java throwable: first candidate of the nearest mapped class, else the simple name. */
        String dotnetName(Throwable t) {
            for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass()) {
                String mapped = javaToDotnet.get(c.getSimpleName());
                if (mapped != null) {
                    return mapped;
                }
            }
            return t.getClass().getSimpleName();
        }

        /** The {@code dotnet} table mapping (what the oracle prints after {@code throw:}); unmapped names unchanged. */
        String mapToJava(String dotnetName) {
            return dotnetToJava.getOrDefault(dotnetName, dotnetName);
        }
    }
}
