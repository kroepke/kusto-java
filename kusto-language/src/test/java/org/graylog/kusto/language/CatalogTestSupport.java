// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: shared helpers for the W3 catalog tests (Aggregates, Operators, PlugIns, QueryOperatorParameters, FunctionHelpers).
package org.graylog.kusto.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.graylog.kusto.language.symbols.CustomReturnTypeContext;
import org.graylog.kusto.language.symbols.Parameter;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.LiteralExpression;
import org.graylog.kusto.language.syntax.NameDeclaration;
import org.graylog.kusto.language.syntax.SimpleNamedExpression;
import org.graylog.kusto.language.syntax.StarExpression;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.SyntaxToken;
import org.graylog.kusto.language.syntax.TokenName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class CatalogTestSupport {
    private CatalogTestSupport() {
    }

    static final Path GlobalsDump = Path.of("..", "kusto-language-conformance", "src", "test", "resources", "globals.jsonl.gz");

    /** Records of one catalog from the oracle dump (line 1 is the header), in file order. */
    static List<JsonNode> readGlobals(String catalog) throws IOException {
        var mapper = new ObjectMapper();
        var records = new ArrayList<JsonNode>();
        try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(GlobalsDump)), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty())
                    continue;
                var node = mapper.readTree(line);
                if (catalog.equals(node.path("catalog").asText()))
                    records.add(node);
            }
        }
        return records;
    }

    /** Every static field of the class (any visibility) read reflectively; asserts none is null. Returns the field count. */
    static int assertNoNullStaticFields(Class<?> type) throws IllegalAccessException {
        int count = 0;
        for (Field f : type.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.isSynthetic())
                continue;
            f.setAccessible(true);
            if (f.getName().equals("s_nameToPlugInMap"))
                continue; // lazy cache, null until GetPlugIn runs (PlugIns.cs:1162)
            assertNotNull(f.get(null), type.getSimpleName() + "." + f.getName());
            count++;
        }
        return count;
    }

    /** Public static fields of exactly the given type, in declaration order. */
    static <T> List<T> publicStaticFieldsOfType(Class<?> owner, Class<T> fieldType) throws IllegalAccessException {
        var result = new ArrayList<T>();
        for (Field f : owner.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isPublic(m) && Modifier.isStatic(m) && f.getType() == fieldType) {
                assertEquals(true, Modifier.isFinal(m), f.getName() + " is final (static readonly)");
                result.add(fieldType.cast(f.get(null)));
            }
        }
        return result;
    }

    // ---- syntax fragments that need no binder ----

    static Expression boolLit(boolean value) {
        return new LiteralExpression(SyntaxKind.BooleanLiteralExpression, SyntaxToken.literal("", value ? "true" : "false", SyntaxKind.BooleanLiteralToken));
    }

    static Expression longLit(String text) {
        return new LiteralExpression(SyntaxKind.LongLiteralExpression, SyntaxToken.literal("", text, SyntaxKind.LongLiteralToken));
    }

    static Expression realLit(String text) {
        return new LiteralExpression(SyntaxKind.RealLiteralExpression, SyntaxToken.literal("", text, SyntaxKind.RealLiteralToken));
    }

    static Expression star() {
        return new StarExpression(SyntaxToken.operator("", SyntaxKind.AsteriskToken));
    }

    static Expression named(String name, Expression value) {
        return new SimpleNamedExpression(
            new NameDeclaration(new TokenName(SyntaxToken.identifier("", name))),
            SyntaxToken.punctuation("", SyntaxKind.EqualToken),
            value);
    }

    /** A {@link CustomReturnTypeContext} assembled by hand; result names come from a map (default otherwise). */
    static final class FakeContext extends CustomReturnTypeContext {
        private final Signature signature;
        private final List<Expression> arguments;
        private final List<TypeSymbol> argumentTypes;
        private final List<Parameter> argumentParameters;
        private final TableSymbol rowScope;
        private final Map<Expression, String> resultNames = new HashMap<>();

        FakeContext(Signature signature, List<Expression> arguments, List<TypeSymbol> argumentTypes, List<Parameter> argumentParameters, TableSymbol rowScope) {
            this.signature = signature;
            this.arguments = arguments;
            this.argumentTypes = argumentTypes;
            this.argumentParameters = argumentParameters;
            this.rowScope = rowScope;
        }

        FakeContext resultName(Expression expr, String name) {
            resultNames.put(expr, name);
            return this;
        }

        @Override public Signature signature() { return signature; }
        @Override public List<Expression> arguments() { return arguments; }
        @Override public List<TypeSymbol> argumentTypes() { return argumentTypes; }
        @Override public List<Parameter> argumentParameters() { return argumentParameters; }
        @Override public TableSymbol rowScope() { return rowScope; }
        @Override public Symbol getReferencedSymbol(String name) { return null; }
        @Override public TypeSymbol getResultType(String name) { return null; }

        @Override
        public String getResultName(Expression expr, String defaultName) {
            var name = resultNames.get(expr);
            return name != null ? name : defaultName;
        }
    }

    // ---- oracle record comparison (globals.jsonl.gz, oracle/kusto-oracle/src/Globals.cs) ----

    static void compareFunction(String ctx, JsonNode rec, org.graylog.kusto.language.symbols.FunctionSymbol fn) {
        compareSymbol(ctx, rec, fn);
        assertEquals(rec.path("resultNameKind").asText(), fn.resultNameKind().name(), ctx + " resultNameKind");
        assertEquals(textOrNull(rec.path("resultNamePrefix")), fn.resultNamePrefix(), ctx + " resultNamePrefix");
        assertEquals(rec.path("isObsolete").asBoolean(), fn.isObsolete(), ctx + " isObsolete");
        assertEquals(textOrNull(rec.path("alternative")), fn.alternative(), ctx + " alternative");
        assertEquals(textOrNull(rec.path("optimizedAlternative")), fn.optimizedAlternative(), ctx + " optimizedAlternative");
        assertEquals(rec.path("isConstantFoldable").asBoolean(), fn.isConstantFoldable(), ctx + " isConstantFoldable");
        assertEquals(rec.path("minArgumentCount").asInt(), fn.minArgumentCount(), ctx + " minArgumentCount");
        assertEquals(rec.path("maxArgumentCount").asInt(), fn.maxArgumentCount(), ctx + " maxArgumentCount");
        compareSignatures(ctx, rec.path("signatures"), fn.signatures());
    }

    static void compareOperator(String ctx, JsonNode rec, org.graylog.kusto.language.symbols.OperatorSymbol op) {
        compareSymbol(ctx, rec, op);
        assertEquals(rec.path("operatorKind").asText(), op.operatorKind().name(), ctx + " operatorKind");
        compareSignatures(ctx, rec.path("signatures"), op.signatures());
    }

    private static void compareSymbol(String ctx, JsonNode rec, Symbol symbol) {
        assertEquals(rec.path("kind").asText(), symbol.kind().name(), ctx + " kind");
        assertEquals(rec.path("name").asText(), symbol.name(), ctx + " name");
        assertEquals(rec.path("alternateName").asText(""), symbol.alternateName(), ctx + " alternateName");
        assertEquals(rec.path("isHidden").asBoolean(), symbol.isHidden(), ctx + " isHidden");
    }

    private static void compareSignatures(String ctx, JsonNode sigs, List<Signature> signatures) {
        assertEquals(sigs.size(), signatures.size(), ctx + " signature count");
        for (int s = 0; s < sigs.size(); s++) {
            var sr = sigs.get(s);
            var sig = signatures.get(s);
            String sctx = ctx + " sig" + s;
            assertEquals(sr.path("minArgumentCount").asInt(), sig.minArgumentCount(), sctx + " minArgumentCount");
            assertEquals(sr.path("maxArgumentCount").asInt(), sig.maxArgumentCount(), sctx + " maxArgumentCount");
            assertEquals(sr.path("returnKind").asText(), sig.returnKind().name(), sctx + " returnKind");
            assertEquals(sr.path("hasCustomReturnType").asBoolean(), sig.customReturnType() != null, sctx + " hasCustomReturnType");
            assertEquals(sr.path("isHidden").asBoolean(), sig.isHidden(), sctx + " isHidden");
            assertEquals(textOrNull(sr.path("alternative")), sig.alternative(), sctx + " alternative");
            assertEquals(sr.path("tabularity").asText(), sig.tabularity().name(), sctx + " tabularity");
            assertEquals(sr.path("layoutType").asText(), sig.layout().getClass().getSimpleName(), sctx + " layoutType");
            var prs = sr.path("parameters");
            assertEquals(prs.size(), sig.parameters().size(), sctx + " parameter count");
            for (int p = 0; p < prs.size(); p++) {
                var pr = prs.get(p);
                var par = sig.parameters().get(p);
                String pctx = sctx + " p" + p + " " + pr.path("name").asText();
                assertEquals(pr.path("name").asText(), par.name(), pctx + " name");
                assertEquals(pr.path("typeKind").asText(), par.typeKind().name(), pctx + " typeKind");
                assertEquals(pr.path("minOccurring").asInt(), par.minOccurring(), pctx + " minOccurring");
                assertEquals(pr.path("maxOccurring").asInt(), par.maxOccurring(), pctx + " maxOccurring");
                assertEquals(pr.path("isCaseSensitive").asBoolean(), par.isCaseSensitive(), pctx + " isCaseSensitive");
                assertEquals(textOrNull(pr.path("defaultValueIndicator")), par.defaultValueIndicator(), pctx + " defaultValueIndicator");
                var argumentKind = pr.path("argumentKind").asText();
                if (argumentKind.equals("13"))
                    argumentKind = "Expression"; // (ArgumentKind)(Column | Literal) in PlugIns.cs is mirrored as Expression (PORT-BUG in PlugIns.java)
                assertEquals(argumentKind, par.argumentKind().name(), pctx + " argumentKind");
                var types = pr.path("declaredTypesDebug");
                assertEquals(types.size(), par.declaredTypes().size(), pctx + " declaredTypes count");
                for (int t = 0; t < types.size(); t++)
                    assertEquals(types.get(t).asText(), par.declaredTypes().get(t).name(), pctx + " declaredType" + t);
            }
        }
    }

    static String textOrNull(JsonNode node) {
        return node.isNull() || node.isMissingNode() ? null : node.asText();
    }
}
