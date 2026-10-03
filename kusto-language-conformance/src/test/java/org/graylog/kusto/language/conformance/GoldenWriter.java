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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.DiagnosticLocationKind;
import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.KustoCode;
import org.graylog.kusto.language.KustoFacts;
import org.graylog.kusto.language.symbols.Parameter;
import org.graylog.kusto.language.symbols.ReturnTypeKind;
import org.graylog.kusto.language.symbols.SchemaDisplay;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.symbols.ClusterSymbol;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.DatabaseSymbol;
import org.graylog.kusto.language.symbols.EntityGroupSymbol;
import org.graylog.kusto.language.symbols.ExternalTableSymbol;
import org.graylog.kusto.language.symbols.FunctionSymbol;
import org.graylog.kusto.language.symbols.MaterializedViewSymbol;
import org.graylog.kusto.language.symbols.ScalarSymbol;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.graylog.kusto.language.syntax.IncludeTrivia;
import org.graylog.kusto.language.syntax.SyntaxElement;
import org.graylog.kusto.language.syntax.SyntaxNode;
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
 * <p><b>Layers available at W4 (parser).</b> {@code KustoCode.parse} (parse only) fills {@code kind},
 * {@code tokens}, {@code fidelity} (from the syntax root), {@code tree}, {@code syntaxDiagnostics} and
 * {@code outcome.parse}. The binder (W6) does not exist yet, so {@code semanticDiagnostics} and {@code bind} are
 * empty arrays, {@code resultType} is {@code null} and {@code outcome.analyze} is {@value #ANALYZE_UNAVAILABLE};
 * the comparator reports those as ordinary differing records on ungated layers. If {@code parse} throws, the
 * record has empty {@code tokens}/{@code tree}, {@code roundTrip=false}, {@code fullWidth=0} and
 * {@code outcome.parse = throw:<name>}, as the oracle writes it.
 *
 * <p><b>Schemas.</b> Built per schema id like {@code Schemas.cs}. Functions, materialized views and entity
 * groups are skipped while their constructors still hit (no longer applies: the schema is complete).
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
        GlobalState globals = globals(schema);
        long t0 = System.nanoTime();
        KustoCode code = null;
        String parseOutcome = "ok";
        try {
            code = KustoCode.parse(text, globals);
        } catch (Throwable ex) {
            ExceptionNames names = exceptionNames();
            parseOutcome = "throw:" + names.mapToJava(names.dotnetName(ex));
        }
        double parseMs = (System.nanoTime() - t0) / 1e6;

        t0 = System.nanoTime();
        KustoCode analyzed = null;
        String analyzeOutcome = "ok";
        try {
            analyzed = KustoCode.parseAndAnalyze(text, globals);
            if (!analyzed.hasSemantics()) {
                analyzeOutcome = "skipped:depth";
            }
        } catch (Throwable ex) {
            ExceptionNames names = exceptionNames();
            analyzeOutcome = "throw:" + names.mapToJava(names.dotnetName(ex));
        }
        double analyzeMs = (System.nanoTime() - t0) / 1e6;
        // Golden.cs: Code => AnalyzedCode ?? ParseCode
        return render(rec.id(), text, analyzed != null ? analyzed : code, analyzed, parseOutcome, analyzeOutcome, parseMs,
                analyzeMs);
    }

    /** Parse-only rendering: the semantic layers stay empty and {@code outcome.analyze} is {@value #ANALYZE_UNAVAILABLE}. */
    static ObjectNode render(String id, String text, KustoCode code, String parseOutcome, double parseMs) {
        return render(id, text, code, null, parseOutcome, ANALYZE_UNAVAILABLE, parseMs, 0.0);
    }

    /**
     * Renders one golden record from {@code code} (the analysed code if available, else the parse-only code; null
     * when both threw). {@code analyzed} is null when analysis threw.
     */
    static ObjectNode render(String id, String text, KustoCode code, KustoCode analyzed, String parseOutcome,
            String analyzeOutcome, double parseMs, double analyzeMs) {
        SyntaxNode root = code == null ? null : code.syntax();

        ObjectNode r = Harness.MAPPER.createObjectNode();
        r.put("id", id);
        String kind;
        try {
            kind = code != null ? code.kind() : KustoCode.getKind(text);
        } catch (RuntimeException ex) {
            kind = "Unknown";
        }
        r.put("kind", kind);

        String tokenValuesOutcome = "ok";
        ArrayNode tokenArray = r.putArray("tokens");
        if (code != null) {
            int pos = 0;
            for (LexicalToken lt : code.getLexicalTokens()) {
                int triviaStart = pos;
                int start = pos + lt.trivia().length();
                int end = start + lt.text().length();
                pos = end;

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
        }

        ObjectNode fidelity = r.putObject("fidelity");
        fidelity.put("roundTrip", root != null && root.toString(IncludeTrivia.All).equals(text));
        fidelity.put("fullWidth", root == null ? 0 : root.fullWidth());

        ArrayNode tree = r.putArray("tree");
        writeTree(tree, root);

        ArrayNode sdx = r.putArray("syntaxDiagnostics");
        List<Diagnostic> syntaxDx = code != null ? code.getSyntaxDiagnostics() : List.of();
        for (Diagnostic d : syntaxDx) {
            writeDiagnostic(sdx, d, d.start(), d.length());
        }

        ArrayNode semDx = r.putArray("semanticDiagnostics");
        if (analyzed != null) {
            for (Diagnostic d : analyzed.getDiagnostics()) {
                boolean isSyntax = false;
                for (Diagnostic s : syntaxDx) {
                    if (s == d || s.equals(d)) {
                        isSyntax = true;
                        break;
                    }
                }
                if (!isSyntax) {
                    writeDiagnostic(semDx, d, d.start(), d.length());
                }
            }
        }

        ArrayNode bind = r.putArray("bind");
        if (analyzed != null && analyzed.hasSemantics()) {
            writeBind(bind, analyzed, preOrder(root));
        }

        TypeSymbol rt = analyzed != null && analyzed.hasSemantics() ? analyzed.resultType() : null;
        r.put("resultType", rt != null ? SchemaDisplay.getText(rt) : null);

        ObjectNode outcome = r.putObject("outcome");
        outcome.put("parse", parseOutcome);
        outcome.put("analyze", analyzeOutcome);
        outcome.put("tokenValues", tokenValuesOutcome);

        ObjectNode timing = r.putObject("timing");
        timing.put("parseMs", Math.round(parseMs * 1000.0) / 1000.0);
        timing.put("analyzeMs", Math.round(analyzeMs * 1000.0) / 1000.0);
        return r;
    }

    private static void writeBind(ArrayNode into, KustoCode analyzed, List<TreeEntry> tree) {
        GlobalState globals = analyzed.globals();
        for (int i = 0; i < tree.size(); i++) {
            if (!(tree.get(i).element() instanceof SyntaxNode node)) {
                continue;
            }
            Symbol sym = node.referencedSymbol();
            Expression expr = node instanceof Expression e ? e : null;
            TypeSymbol type = expr != null ? expr.resultType() : null;
            if (sym == null && type == null) {
                continue;
            }
            ObjectNode o = into.addObject();
            o.put("i", i);
            o.put("symbolKind", sym != null ? sym.kind().name() : null);
            o.put("symbol", sym != null ? sym.name() : null);
            o.put("symbolOwner", ownerOf(sym, globals));
            o.put("type", type != null ? SchemaDisplay.getText(type) : null);
            o.put("signature", renderSignature(node.referencedSignature()));
            boolean isConstant = expr != null && expr.isConstant();
            o.put("isConstant", isConstant);
            String constantValue = null;
            if (isConstant) {
                try {
                    Object v = expr.constantValue();
                    constantValue = v == null ? null : DotNet.str(v);
                } catch (RuntimeException | StackOverflowError ex) {
                    constantValue = "!" + exceptionNames().dotnetName(ex);
                }
            }
            o.put("constantValue", constantValue);
            SyntaxNode body = node.getCalledFunctionBody();
            o.put("calledBody", body != null ? sha16(body.toString()) : null);
            List<SyntaxNode> alts = node.alternates();
            o.put("alternates", alts == null ? 0 : alts.size());
        }
    }

    /** Column -> GetTable(column); Database -> GetCluster(database); any other symbol -> GetDatabase(symbol). */
    static String ownerOf(Symbol sym, GlobalState globals) {
        if (sym == null) {
            return null;
        }
        Symbol owner;
        if (sym instanceof ColumnSymbol c) {
            owner = globals.getTable(c);
        } else if (sym instanceof DatabaseSymbol d) {
            owner = globals.getCluster(d);
        } else {
            owner = globals.getDatabase(sym);
        }
        return owner != null ? owner.name() : null;
    }

    static String renderSignature(Signature sig) {
        if (sig == null) {
            return null;
        }
        StringBuilder ps = new StringBuilder();
        for (Parameter p : sig.parameters()) {
            if (ps.length() > 0) {
                ps.append(", ");
            }
            ps.append(KustoFacts.bracketNameIfNecessary(p.name())).append(": ").append(SchemaDisplay.getParameterTypeText(p));
        }
        String ret = sig.returnKind() == ReturnTypeKind.Declared && sig.declaredReturnType() != null
                ? SchemaDisplay.getText(sig.declaredReturnType())
                : sig.returnKind().name();
        return "(" + ps + ") -> " + ret;
    }

    static String sha16(String text) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One element of the pre-order walk. */
    record TreeEntry(SyntaxElement element, int parent, String name, int depth) {
    }

    /** Pre-order over every element reachable through {@code getChild} (null children skipped), like Golden.cs PreOrder. */
    static List<TreeEntry> preOrder(SyntaxNode root) {
        List<TreeEntry> result = new ArrayList<>();
        if (root == null) {
            return result;
        }
        ArrayDeque<TreeEntry> stack = new ArrayDeque<>();
        stack.push(new TreeEntry(root, -1, "", 0));
        while (!stack.isEmpty()) {
            TreeEntry e = stack.pop();
            int index = result.size();
            result.add(e);
            SyntaxElement el = e.element();
            for (int c = el.childCount() - 1; c >= 0; c--) {
                SyntaxElement child = el.getChild(c);
                if (child != null) {
                    String n = el.getName(c);
                    stack.push(new TreeEntry(child, index, n == null ? "" : n, e.depth() + 1));
                }
            }
        }
        return result;
    }

    private static void writeTree(ArrayNode into, SyntaxNode root) {
        List<TreeEntry> tree = preOrder(root);
        for (int i = 0; i < tree.size(); i++) {
            TreeEntry e = tree.get(i);
            ObjectNode o = into.addObject();
            o.put("i", i);
            o.put("kind", e.element().kind().name());
            o.put("depth", e.depth());
            o.put("parent", e.parent());
            o.put("name", e.name());
            o.put("start", e.element().textStart());
            o.put("end", e.element().end());
            o.put("missing", e.element().isMissing());
        }
    }

    private static final Map<String, GlobalState> GLOBALS = new ConcurrentHashMap<>();

    /** The {@code GlobalState} of a schema, cached per schema id ({@code null} means the default). */
    static GlobalState globals(SchemaFile schema) {
        if (schema == null) {
            return GlobalState.default_();
        }
        return GLOBALS.computeIfAbsent(schema.id(), k -> build(schema));
    }

    private static GlobalState build(SchemaFile s) {
        List<Symbol> members = new ArrayList<>();
        for (SchemaFile.Table t : s.tables()) {
            members.add(new TableSymbol(t.name(), columns(t.columns()), t.docstring()));
        }
        for (SchemaFile.Function f : s.functions()) {
            String parameters = f.parameters() == null || f.parameters().isEmpty() ? "()" : f.parameters();
            members.add(new FunctionSymbol(f.name(), parameters, f.body(), f.docstring()));
        }
        for (SchemaFile.Table t : s.externalTables()) {
            members.add(new ExternalTableSymbol(t.name(), columns(t.columns()), t.docstring()));
        }
        for (SchemaFile.MaterializedView m : s.materializedViews()) {
            members.add(new MaterializedViewSymbol(m.name(), columns(m.columns()), m.query(), m.docstring()));
        }
        for (SchemaFile.EntityGroup g : s.entityGroups()) {
            members.add(new EntityGroupSymbol(g.name(), g.definition(), g.docstring()));
        }
        return GlobalState.default_()
                .withCluster(new ClusterSymbol(s.cluster(), new DatabaseSymbol(s.database(), members)))
                .withDatabase(s.database());
    }

    private static List<ColumnSymbol> columns(List<SchemaFile.Column> cols) {
        List<ColumnSymbol> out = new ArrayList<>();
        for (SchemaFile.Column c : cols) {
            ScalarSymbol type = ScalarTypes.getSymbol(c.type());
            if (type == null) {
                throw new IllegalStateException("unknown scalar type '" + c.type() + "' for column '" + c.name() + "'");
            }
            out.add(new ColumnSymbol(c.name(), type));
        }
        return out;
    }

    @Override
    public int[] tokenStarts(String text) {
        List<LexicalToken> tokens = KustoCode.parse(text, GlobalState.default_()).getLexicalTokens();
        List<Integer> starts = new ArrayList<>(tokens.size());
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
