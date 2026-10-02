// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for the Functions catalog (Functions.cs, Functions.Convert.cs) and its oracle comparison.
package org.graylog.kusto.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.graylog.kusto.language.symbols.FunctionSymbol;
import org.graylog.kusto.language.symbols.Parameter;
import org.graylog.kusto.language.symbols.ResultNameKind;
import org.graylog.kusto.language.symbols.ReturnTypeKind;
import org.graylog.kusto.language.symbols.Signature;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class FunctionsTest {
    // Count of `new FunctionSymbol(` in upstream (Functions.Convert.cs 8 + Functions.cs 421):
    //   grep -c "new FunctionSymbol(" upstream/kusto-query-language/src/Kusto.Language/Functions.cs   -> 421
    //   grep -c "new FunctionSymbol(" upstream/kusto-query-language/src/Kusto.Language/Functions.Convert.cs -> 8
    private static final int UpstreamFunctionSymbolCount = 429;

    private static final Path GlobalsDump = Path.of("..", "kusto-language-conformance", "src", "test", "resources", "globals.jsonl.gz");

    private static List<FunctionSymbol> publicFunctionFields() throws IllegalAccessException {
        var result = new ArrayList<FunctionSymbol>();
        for (Field f : Functions.class.getDeclaredFields()) {
            if (Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers()) && f.getType() == FunctionSymbol.class) {
                assertTrue(Modifier.isFinal(f.getModifiers()), f.getName());
                result.add((FunctionSymbol) f.get(null));
                assertNotNull(result.get(result.size() - 1), "null field " + f.getName());
            }
        }
        return result;
    }

    private static FunctionSymbol byName(String name) {
        FunctionSymbol found = null;
        for (var fn : Functions.All) {
            if (fn.name().equals(name)) {
                assertTrue(found == null, "duplicate name " + name);
                found = fn;
            }
        }
        assertNotNull(found, name);
        return found;
    }

    @Test
    void classInitSucceedsWithNoNullFields() throws Exception {
        var fields = publicFunctionFields();
        assertEquals(UpstreamFunctionSymbolCount, fields.size());
        assertNotNull(Functions.All);
    }

    @Test
    void allCountEqualsUpstreamCount() {
        assertEquals(UpstreamFunctionSymbolCount, Functions.All.size());
    }

    @Test
    void allHasNoNullsOrDuplicatesByReference() {
        Set<FunctionSymbol> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var fn : Functions.All) {
            assertNotNull(fn);
            assertTrue(seen.add(fn), "duplicate entry " + fn.name());
        }
    }

    @Test
    void allCoversEveryPublicFunctionField() throws Exception {
        Set<FunctionSymbol> all = Collections.newSetFromMap(new IdentityHashMap<>());
        all.addAll(Functions.All);
        for (var fn : publicFunctionFields())
            assertTrue(all.contains(fn), fn.name());
    }

    @Test
    void allStartsAndEndsLikeUpstream() {
        assertSame(Functions.Cluster, Functions.All.get(0));
        assertSame(Functions.ColumnNamesOf, Functions.All.get(Functions.All.size() - 1));
        assertSame(Functions.Strcat, Functions.All.get(8));
    }

    @Test
    void convertPartFieldsAreInAll() {
        var convert = List.of(Functions.ConvertAngle, Functions.ConvertEnergy, Functions.ConvertForce, Functions.ConvertLength,
            Functions.ConvertMass, Functions.ConvertSpeed, Functions.ConvertTemperature, Functions.ConvertVolume);
        int at = Functions.All.indexOf(Functions.ConvertAngle);
        assertTrue(at >= 0);
        for (int i = 0; i < convert.size(); i++)
            assertSame(convert.get(i), Functions.All.get(at + i));
    }

    private static void assertShape(String name, int min, int max, ReturnTypeKind kind, int signatureCount) {
        var fn = byName(name);
        assertEquals(signatureCount, fn.signatures().size(), name);
        assertEquals(min, fn.minArgumentCount(), name + " min");
        assertEquals(max, fn.maxArgumentCount(), name + " max");
        assertEquals(kind, fn.signatures().get(0).returnKind(), name + " kind");
    }

    @Test
    void strcatIsRepeating() {
        assertShape("strcat", 1, 64, ReturnTypeKind.Declared, 1);
        Parameter arg = Functions.Strcat.signatures().get(0).parameters().get(0);
        assertEquals("arg", arg.name());
        assertEquals(64, arg.maxOccurring());
        assertTrue(arg.isRepeatable());
        assertEquals(ResultNameKind.None, Functions.Strcat.resultNameKind());
        assertTrue(Functions.Strcat.isConstantFoldable());
    }

    @Test
    void strcatDelim() {
        assertShape("strcat_delim", 3, 65, ReturnTypeKind.Declared, 1);
    }

    @Test
    void iffUsesCommonNonDynamic() {
        assertShape("iff", 3, 3, ReturnTypeKind.CommonNonDynamic, 1);
    }

    @Test
    void binHasWidestFirstSignature() {
        assertShape("bin", 2, 2, ReturnTypeKind.Widest, 4);
        assertEquals(ResultNameKind.FirstArgument, Functions.Bin.resultNameKind());
    }

    @Test
    void binAutoHasCustomReturnTypeAndIsHidden() {
        var fn = Functions.BinAuto;
        assertEquals("bin_auto", fn.name());
        assertEquals(1, fn.signatures().size());
        Signature sig = fn.signatures().get(0);
        assertEquals(ReturnTypeKind.Custom, sig.returnKind());
        assertNotNull(sig.customReturnType());
        assertTrue(fn.isHidden());
    }

    @Test
    void conversions() {
        assertShape("toint", 1, 1, ReturnTypeKind.Declared, 1);
        assertShape("tolong", 1, 1, ReturnTypeKind.Declared, 1);
        assertEquals(ResultNameKind.FirstArgument, Functions.ToLong.resultNameKind());
    }

    @Test
    void convertLengthFromConvertPart() {
        assertShape("convert_length", 3, 3, ReturnTypeKind.Declared, 1);
        var from = Functions.ConvertLength.signatures().get(0).parameters().get(1);
        assertEquals("from", from.name());
        assertTrue(from.values().contains("Meter"));
        assertEquals(36, from.values().size());
        assertTrue(Functions.ConvertLength.isConstantFoldable());
        assertEquals(ResultNameKind.None, Functions.ConvertLength.resultNameKind());
    }

    @Test
    void optionalParameters() {
        assertShape("database", 0, 1, ReturnTypeKind.Parameter0Database, 1);
        assertShape("cluster", 1, 1, ReturnTypeKind.Parameter0Cluster, 1);
    }

    @Test
    void hiddenFunction() {
        var fn = byName("__lz4_compress_dynamic_array_to_base64_string");
        assertTrue(fn.isHidden());
        assertFalse(Functions.Strcat.isHidden());
    }

    @Test
    void obsoleteFunction() {
        var fn = byName("parsejson");
        assertTrue(fn.isObsolete());
        assertEquals("parse_json", fn.alternative());
        assertFalse(Functions.ParseJson.isObsolete());
    }

    @Test
    void customReturnTypeWithBlockRepeatingLayout() {
        assertShape("bag_pack", 2, 65534, ReturnTypeKind.Custom, 1);
        assertShape("array_sort_asc", 1, 65, ReturnTypeKind.Custom, 1);
    }

    @Test
    void graphFunctionsHaveCustomAvailability() {
        assertNotNull(Functions.InnerNodes.customAvailability());
        assertNotNull(Functions.Any.customAvailability());
        assertNotNull(Functions._All.customAvailability());
    }

    // ---- oracle comparison ----

    @TestFactory
    Stream<DynamicTest> matchesOracleGlobalsDump() throws IOException {
        Assumptions.assumeTrue(Files.exists(GlobalsDump), "oracle dump absent: " + GlobalsDump);
        var mapper = new ObjectMapper();
        var records = new ArrayList<JsonNode>();
        try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(GlobalsDump)), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank())
                    continue;
                var node = mapper.readTree(line);
                if ("functions".equals(node.path("catalog").asText()))
                    records.add(node);
            }
        }
        assertEquals(Functions.All.size(), records.size(), "record count");
        var tests = new ArrayList<DynamicTest>();
        for (int i = 0; i < records.size(); i++) {
            final int index = i;
            final var rec = records.get(i);
            tests.add(DynamicTest.dynamicTest(i + " " + rec.path("name").asText(), () -> compare(index, rec, Functions.All.get(index))));
        }
        return tests.stream();
    }

    private static void compare(int index, JsonNode rec, FunctionSymbol fn) {
        String ctx = index + " " + rec.path("name").asText();
        assertEquals(index, rec.path("index").asInt(), ctx + " index");
        assertEquals(rec.path("name").asText(), fn.name(), ctx + " name");
        assertEquals(rec.path("alternateName").asText(""), fn.alternateName(), ctx + " alternateName");
        assertEquals(rec.path("isHidden").asBoolean(), fn.isHidden(), ctx + " isHidden");
        assertEquals(rec.path("resultNameKind").asText(), fn.resultNameKind().name(), ctx + " resultNameKind");
        var sigs = rec.path("signatures");
        assertEquals(sigs.size(), fn.signatures().size(), ctx + " signature count");
        for (int s = 0; s < sigs.size(); s++) {
            var sr = sigs.get(s);
            var sig = fn.signatures().get(s);
            String sctx = ctx + " sig" + s;
            assertEquals(sr.path("minArgumentCount").asInt(), sig.minArgumentCount(), sctx + " minArgumentCount");
            assertEquals(sr.path("maxArgumentCount").asInt(), sig.maxArgumentCount(), sctx + " maxArgumentCount");
            assertEquals(sr.path("returnKind").asText(), sig.returnKind().name(), sctx + " returnKind");
            var prs = sr.path("parameters");
            assertEquals(prs.size(), sig.parameters().size(), sctx + " parameter count");
            for (int p = 0; p < prs.size(); p++) {
                var pr = prs.get(p);
                var par = sig.parameters().get(p);
                String pctx = sctx + " p" + p;
                assertEquals(pr.path("name").asText(), par.name(), pctx + " name");
                assertEquals(pr.path("typeKind").asText(), par.typeKind().name(), pctx + " typeKind");
                assertEquals(pr.path("minOccurring").asInt(), par.minOccurring(), pctx + " minOccurring");
                assertEquals(pr.path("maxOccurring").asInt(), par.maxOccurring(), pctx + " maxOccurring");
            }
        }
    }
}
