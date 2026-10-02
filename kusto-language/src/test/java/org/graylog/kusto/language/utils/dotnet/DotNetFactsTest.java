// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: pins the utils.dotnet shims to the oracle's dotnet-facts.json (PORTING.md 5.2, 7).

package org.graylog.kusto.language.utils.dotnet;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * One dynamic test per fact in {@code dotnet-facts.json}. Char category and casing tables are
 * compared through a drift allowlist ({@code dotnet-char-drift.json}, D21); run with
 * {@code -Dkusto.regenDrift=true} to rewrite it.
 */
class DotNetFactsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path FACTS = Paths.get(System.getProperty("kusto.facts",
            Paths.get("..", "kusto-language-conformance", "src", "test", "resources", "dotnet-facts.json").toString()));
    private static final Path EXCEPTION_MAP = Paths.get("..", "porting", "exception-map.json");
    private static final Path DRIFT = Paths.get("src", "test", "resources", "dotnet-char-drift.json");

    private Map<String, String> exceptionMap;
    private JsonNode drift;

    @TestFactory
    List<DynamicTest> facts() throws IOException {
        JsonNode root = JSON.readTree(FACTS.toFile());
        exceptionMap = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = JSON.readTree(EXCEPTION_MAP.toFile()).get("dotnet").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            exceptionMap.put(e.getKey(), e.getValue().asText());
        }
        if (Boolean.getBoolean("kusto.regenDrift")) {
            regenerateDrift(root.get("facts"));
        }
        drift = JSON.readTree(DRIFT.toFile());
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode fact : root.get("facts")) {
            String api = fact.get("api").asText();
            String input = fact.get("input").asText();
            tests.add(DynamicTest.dynamicTest(api + " " + input, () -> check(fact)));
        }
        return tests;
    }

    private static List<JsonNode> args(JsonNode fact) throws IOException {
        String input = fact.get("input").asText();
        List<JsonNode> out = new ArrayList<>();
        if (input.startsWith("[")) {
            for (JsonNode n : JSON.readTree(input)) {
                out.add(n);
            }
        } else {
            out.add(JSON.getNodeFactory().textNode(input));
        }
        return out;
    }

    private static String arg0(JsonNode fact) throws IOException {
        return args(fact).get(0).asText();
    }

    private void check(JsonNode fact) throws IOException {
        String api = fact.get("api").asText();
        String input = fact.get("input").asText();
        boolean ok = fact.get("ok").asBoolean();
        JsonNode value = fact.get("value");
        switch (api) {
            case "char.IsWhiteSpace" -> assertEquals(value, ranges(c -> DotNetChars.isWhiteSpace((char) c)));
            case "char.IsDigit" -> checkDrift(api, value, c -> DotNetChars.isDigit((char) c));
            case "char.IsLetter" -> checkDrift(api, value, c -> DotNetChars.isLetter((char) c));
            case "char.IsLetterOrDigit" -> checkDrift(api, value, c -> DotNetChars.isLetterOrDigit((char) c));
            case "char.IsUpper" -> checkDrift(api, value, c -> DotNetChars.isUpper((char) c));
            case "char.IsLower" -> checkDrift(api, value, c -> DotNetChars.isLower((char) c));
            case "char.ToUpperInvariant" -> checkCaseDrift(api, value, c -> DotNetChars.toUpperInvariant((char) c));
            case "char.ToLowerInvariant" -> checkCaseDrift(api, value, c -> DotNetChars.toLowerInvariant((char) c));
            case "Int32.TryParse" -> checkParse(fact, DotNetNumber.tryParseInt(arg0(fact)), "0");
            case "Int64.TryParse" -> checkParse(fact, DotNetNumber.tryParseLong(arg0(fact)), "0");
            case "Double.TryParse" -> checkParse(fact, DotNetNumber.tryParseDouble(arg0(fact)), "0");
            case "Decimal.TryParse" -> checkParse(fact, DotNetDecimal.tryParse(arg0(fact)), "0");
            case "TimeSpan.TryParse" -> checkParse(fact, TimeSpan.tryParse(arg0(fact)), "00:00:00");
            case "DateTime.TryParse" -> {
                Assumptions.assumeFalse("12:00".equals(input), "time-only input defaults to the oracle's run date");
                checkParse(fact, DateTime.tryParse(arg0(fact)), "01/01/0001 00:00:00");
            }
            case "Guid.TryParse" -> checkParse(fact, DotNetGuid.tryParse(arg0(fact)), DotNetGuid.EMPTY.toString());
            case "Boolean.TryParse" -> checkParse(fact, DotNetBoolean.tryParse(arg0(fact)), "False");
            case "Double.ToString" -> checkParse(fact, DotNetNumber.tryParseDouble(arg0(fact)), null);
            case "Single.ToString" -> checkParse(fact, DotNetNumber.tryParseFloat(arg0(fact)), null);
            case "Decimal.ToString" -> checkParse(fact, DotNetDecimal.tryParse(arg0(fact), true), null);
            case "TimeSpan.ToString" -> checkParse(fact, isInteger(input)
                    ? TimeSpan.fromTicks(Long.parseLong(input)) : TimeSpan.tryParse(input), null);
            case "DateTime.ToString" -> checkParse(fact, isInteger(input)
                    ? DateTime.ofTicks(Long.parseLong(input), DateTime.Kind.Unspecified) : DateTime.tryParse(input), null);
            case "Guid.ToString" -> checkParse(fact, DotNetGuid.tryParse(input), null);
            case "TimeSpan.FromSeconds" -> checkCall(fact, () -> TimeSpan.fromSeconds(dbl(input)));
            case "TimeSpan.FromMinutes" -> checkCall(fact, () -> TimeSpan.fromMinutes(dbl(input)));
            case "TimeSpan.FromHours" -> checkCall(fact, () -> TimeSpan.fromHours(dbl(input)));
            case "TimeSpan.FromDays" -> checkCall(fact, () -> TimeSpan.fromDays(dbl(input)));
            case "TimeSpan.FromMilliseconds" -> checkCall(fact, () -> TimeSpan.fromMilliseconds(dbl(input)));
            case "TimeSpan.FromTicks" -> checkCall(fact, () -> TimeSpan.fromTicks(Long.parseLong(input)));
            case "Convert.ToInt64Cast" -> checkCall(fact, () -> DotNet.longCast(dbl(input)));
            case "Convert.ChangeType" -> checkChangeType(fact);
            case "String.Compare" -> {
                List<JsonNode> a = args(fact);
                checkCall(fact, () -> switch (a.size()) {
                    case 2 -> DotNetStrings.compare(a.get(0).asText(), a.get(1).asText());
                    case 5 -> DotNetStrings.compare(a.get(0).asText(), a.get(1).asInt(), a.get(2).asText(),
                            a.get(3).asInt(), a.get(4).asInt());
                    case 6 -> DotNetStrings.compare(a.get(0).asText(), a.get(1).asInt(), a.get(2).asText(),
                            a.get(3).asInt(), a.get(4).asInt(), a.get(5).asBoolean());
                    default -> throw new AssertionError("bad arity " + a.size());
                });
            }
            case "String.CompareOrdinalIgnoreCase" -> {
                List<JsonNode> a = args(fact);
                checkCall(fact, () -> DotNetStrings.compareOrdinalIgnoreCase(a.get(0).asText(), a.get(1).asText()));
            }
            case "String.EqualsOrdinalIgnoreCase" -> {
                List<JsonNode> a = args(fact);
                assertEquals(value.asBoolean(), DotNetStrings.equalsOrdinalIgnoreCase(a.get(0).asText(), a.get(1).asText()));
            }
            case "String.ToUpperInvariant" -> assertEquals(value.asText(), DotNetStrings.toUpperInvariant(arg0(fact)));
            case "String.ToLowerInvariant" -> assertEquals(value.asText(), DotNetStrings.toLowerInvariant(arg0(fact)));
            case "String.Trim" -> assertEquals(value.asText(), DotNetStrings.trim(arg0(fact)));
            case "String.IsNullOrWhiteSpace" -> assertEquals(value.asBoolean(), DotNetStrings.isNullOrWhiteSpace(arg0(fact)));
            case "String.Split" -> {
                List<JsonNode> a = args(fact);
                List<String> expected = new ArrayList<>();
                value.forEach(n -> expected.add(n.asText()));
                assertEquals(expected, DotNetStrings.split(a.get(0).asText(), a.get(1).asText().charAt(0)));
            }
            case "SyntaxFacts.TryGetKind" -> Assumptions.abort("SyntaxFacts not ported yet");
            default -> fail("unhandled api " + api + " (ok=" + ok + ")");
        }
    }

    private static boolean isInteger(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= '0' && c <= '9') && !(i == 0 && c == '-' && s.length() > 1)) {
                return false;
            }
        }
        return true;
    }

    private static double dbl(String s) {
        Double d = DotNetNumber.tryParseDouble(s);
        assertNotNull(d, "unparseable double " + s);
        return d;
    }

    /** TryParse-style check: {@code ok}, rendered value (default when not ok), {@code ticks}, {@code kind}, {@code bits}. */
    private static void checkParse(JsonNode fact, Object result, String defaultRendering) {
        boolean ok = fact.get("ok").asBoolean();
        assertEquals(ok, result != null, "ok");
        if (result == null && defaultRendering == null) {
            return;
        }
        assertEquals(fact.get("value").asText(), result == null ? defaultRendering : DotNet.str(result), "value");
        checkExtras(fact, result);
    }

    private static void checkExtras(JsonNode fact, Object result) {
        if (fact.has("ticks")) {
            long ticks = result instanceof TimeSpan t ? t.ticks() : result instanceof DateTime d ? d.ticks() : 0L;
            assertEquals(fact.get("ticks").asLong(), ticks, "ticks");
        }
        if (fact.has("kind")) {
            String expected = fact.get("kind").asText();
            if ("Local".equals(expected)) {
                expected = "Utc"; // D11: offsets convert to UTC; the oracle runs with TZ=UTC
            }
            String actual = result instanceof DateTime d ? d.kind().name() : "Unspecified";
            assertEquals(expected, actual, "kind");
        }
        if (fact.has("bits")) {
            String bits = result instanceof Double d ? Long.toString(Double.doubleToRawLongBits(d))
                    : result instanceof Float f ? Integer.toString(Float.floatToRawIntBits(f)) : "0";
            assertEquals(fact.get("bits").asText(), bits, "bits");
        }
    }

    @FunctionalInterface
    private interface Call {
        Object run() throws Exception;
    }

    /** Value-or-exception check; {@code "!X"} values compare the mapped Java exception name. */
    private void checkCall(JsonNode fact, Call call) {
        String expected = fact.get("value").asText();
        Object result;
        try {
            result = call.run();
        } catch (Exception e) {
            assertTrue(expected.startsWith("!"), "unexpected " + e);
            String mapped = exceptionMap.getOrDefault(expected.substring(1), expected.substring(1));
            assertEquals(mapped, e.getClass().getSimpleName(), "exception type");
            assertFalse(fact.get("ok").asBoolean(), "ok");
            return;
        }
        assertFalse(expected.startsWith("!"), "expected " + expected + " but got " + result);
        assertTrue(fact.get("ok").asBoolean(), "ok");
        if (fact.has("sign")) {
            assertEquals(fact.get("sign").asInt(), Integer.signum((Integer) result), "sign");
        } else {
            assertEquals(expected, DotNet.str(result), "value");
        }
        checkExtras(fact, result);
    }

    private void checkChangeType(JsonNode fact) throws IOException {
        List<JsonNode> a = args(fact);
        String text = a.get(0).asText();
        String target = a.get(1).asText();
        String source = a.size() > 2 ? a.get(2).asText() : "String";
        Object value = switch (source) {
            case "String" -> text;
            case "Double" -> DotNetNumber.tryParseDouble(text);
            case "Int64" -> DotNetNumber.tryParseLong(text);
            case "Decimal" -> DotNetDecimal.tryParse(text);
            case "Boolean" -> DotNetBoolean.tryParse(text);
            case "TimeSpan" -> TimeSpan.tryParse(text);
            case "Guid" -> DotNetGuid.tryParse(text);
            case "DateTime" -> DateTime.tryParse(text);
            default -> throw new AssertionError("source " + source);
        };
        assertNotNull(value, "source value " + text);
        Class<?> cls = switch (target) {
            case "String" -> String.class;
            case "Int64" -> Long.class;
            case "Double" -> Double.class;
            case "Decimal" -> BigDecimal.class;
            case "Boolean" -> Boolean.class;
            default -> throw new AssertionError("target " + target);
        };
        checkCall(fact, () -> DotNet.changeType(value, cls));
        if (fact.has("type")) {
            Object r = DotNet.changeType(value, cls);
            String type = r instanceof Long ? "Int64" : r instanceof BigDecimal ? "Decimal" : r instanceof UUID ? "Guid"
                    : r.getClass().getSimpleName();
            assertEquals(fact.get("type").asText(), type, "type");
        }
    }

    // ---- char tables ----

    private static ArrayNode ranges(IntPredicate p) {
        ArrayNode out = JSON.createArrayNode();
        int c = 0;
        while (c <= 0xFFFF) {
            if (!p.test(c)) {
                c++;
                continue;
            }
            int start = c;
            while (c + 1 <= 0xFFFF && p.test(c + 1)) {
                c++;
            }
            out.add(JSON.createArrayNode().add(start).add(c));
            c++;
        }
        return out;
    }

    private static boolean[] fromRanges(JsonNode ranges) {
        boolean[] set = new boolean[0x10000];
        for (JsonNode r : ranges) {
            for (int c = r.get(0).asInt(); c <= r.get(1).asInt(); c++) {
                set[c] = true;
            }
        }
        return set;
    }

    private static ArrayNode categoryDrift(JsonNode table, IntPredicate java) {
        boolean[] net = fromRanges(table);
        return ranges(c -> net[c] != java.test(c));
    }

    private static ArrayNode caseDrift(JsonNode table, IntUnaryOperator java) {
        int[] net = new int[0x10000];
        for (int c = 0; c < net.length; c++) {
            net[c] = c;
        }
        for (JsonNode pair : table) {
            net[pair.get(0).asInt()] = pair.get(1).asInt();
        }
        return ranges(c -> net[c] != java.applyAsInt(c));
    }

    private void checkDrift(String api, JsonNode table, IntPredicate java) {
        ArrayNode actual = categoryDrift(table, java);
        System.out.println(api + " drift: " + count(actual) + " code units");
        assertEquals(drift.get(api), actual, api + " drift differs from dotnet-char-drift.json");
    }

    private void checkCaseDrift(String api, JsonNode table, IntUnaryOperator java) {
        ArrayNode actual = caseDrift(table, java);
        System.out.println(api + " drift: " + count(actual) + " code units");
        assertEquals(drift.get(api), actual, api + " drift differs from dotnet-char-drift.json");
    }

    private static int count(JsonNode ranges) {
        int n = 0;
        for (JsonNode r : ranges) {
            n += r.get(1).asInt() - r.get(0).asInt() + 1;
        }
        return n;
    }

    private static void regenerateDrift(JsonNode facts) throws IOException {
        ObjectNode out = JSON.createObjectNode();
        for (JsonNode fact : facts) {
            String api = fact.get("api").asText();
            JsonNode table = fact.get("value");
            switch (api) {
                case "char.IsDigit" -> out.set(api, categoryDrift(table, c -> DotNetChars.isDigit((char) c)));
                case "char.IsLetter" -> out.set(api, categoryDrift(table, c -> DotNetChars.isLetter((char) c)));
                case "char.IsLetterOrDigit" -> out.set(api, categoryDrift(table, c -> DotNetChars.isLetterOrDigit((char) c)));
                case "char.IsUpper" -> out.set(api, categoryDrift(table, c -> DotNetChars.isUpper((char) c)));
                case "char.IsLower" -> out.set(api, categoryDrift(table, c -> DotNetChars.isLower((char) c)));
                case "char.ToUpperInvariant" -> out.set(api, caseDrift(table, c -> DotNetChars.toUpperInvariant((char) c)));
                case "char.ToLowerInvariant" -> out.set(api, caseDrift(table, c -> DotNetChars.toLowerInvariant((char) c)));
                default -> {
                }
            }
        }
        StringBuilder sb = new StringBuilder("{\n");
        Iterator<Map.Entry<String, JsonNode>> it = out.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            sb.append("  \"").append(e.getKey()).append("\": ").append(JSON.writeValueAsString(e.getValue()));
            sb.append(it.hasNext() ? ",\n" : "\n");
        }
        Files.writeString(DRIFT, sb.append("}\n").toString());
    }
}
