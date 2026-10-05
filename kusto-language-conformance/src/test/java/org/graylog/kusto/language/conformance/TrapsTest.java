// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: one test per PORTING.md section 5 trap policy, against dotnet-facts.json and traps goldens.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import org.graylog.kusto.language.parsing.LexicalToken;
import org.graylog.kusto.language.parsing.TextFacts;
import org.graylog.kusto.language.syntax.SyntaxFacts;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.SyntaxToken;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Out;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * One test per behavioural-trap policy (PORTING.md section 5), checked against
 * {@code dotnet-facts.json} and the {@code traps/*} golden records that pin each policy. Records
 * are selected by their corpus {@code source} (which names the PORTING.md section) and, for the
 * literal-value policies, by the literal token kinds the golden contains; only the part of the
 * record that the policy governs is compared, so a regression names the policy.
 *
 * <p>The port is driven through {@link GoldenWriter}, the same rendering the conformance gate uses.
 */
class TrapsTest {
    /** The 24 characters of the {@code TextFacts.IsWhitespace} switch ({@code TextFacts.cs}). */
    static final String KQL_WHITESPACE = "\t \r\n\u000c  ᠎      "
            + "     ​  　﻿";

    private static final GoldenWriter WRITER = new GoldenWriter();
    private static volatile Traps traps;
    private static volatile JsonNode facts;

    /** PORTING.md 5.1: {@code TextFacts.IsWhitespace} is the literal 24-character switch (U+200B, U+FEFF included). */
    @Test
    void whitespaceSwitch() {
        assertEquals(24, KQL_WHITESPACE.length());
        assertEquals(ranges(c -> KQL_WHITESPACE.indexOf((char) c) >= 0), ranges(c -> TextFacts.isWhitespace((char) c)),
                "TextFacts.isWhitespace must be exactly the 24-character switch");
        for (char c : KQL_WHITESPACE.toCharArray()) {
            String hex = hex(c);
            LexicalToken[] t = GoldenWriter.lex("a" + c + "b" + c);
            assertEquals(3, t.length, hex);
            assertEquals(SyntaxKind.IdentifierToken, t[1].kind(), hex);
            assertEquals(String.valueOf(c), t[1].trivia(), hex + " is trivia before a token");
            assertEquals(SyntaxKind.EndOfTextToken, t[2].kind(), hex);
            assertEquals(String.valueOf(c), t[2].trivia(), hex + " is trivia before the end token");
        }
        // .NET char.IsWhiteSpace differs from the switch; the lexer must follow the switch.
        JsonNode dotnet = fact("char.IsWhiteSpace", "table").path("value");
        for (char c : new char[] {'\u000b', '\u0085'}) {
            assertTrue(inRanges(dotnet, c), "dotnet-facts: char.IsWhiteSpace(" + hex(c) + ")");
            assertFalse(TextFacts.isWhitespace(c), hex(c));
            LexicalToken[] t = GoldenWriter.lex("a" + c + "b");
            assertEquals(SyntaxKind.BadToken, t[1].kind(), hex(c) + " is a BadToken");
        }
        compareTraps("5.1 whitespace", r -> r.source().contains("5.1 TextFacts.IsWhitespace"), Layer.TOKENS, 32);
    }

    /** PORTING.md 5.1: {@code TextFacts.IsLetter/IsDigit/IsLetterOrDigit/IsHexDigit} are ASCII-only. */
    @Test
    void asciiIdentifierClasses() {
        assertEquals(List.of(List.of((int) 'A', (int) 'Z'), List.of((int) 'a', (int) 'z')), ranges(c -> TextFacts.isLetter((char) c)));
        assertEquals(List.of(List.of((int) '0', (int) '9')), ranges(c -> TextFacts.isDigit((char) c)));
        assertEquals(List.of(List.of((int) '0', (int) '9'), List.of((int) 'A', (int) 'Z'), List.of((int) 'a', (int) 'z')),
                ranges(c -> TextFacts.isLetterOrDigit((char) c)));
        assertEquals(List.of(List.of((int) '0', (int) '9'), List.of((int) 'A', (int) 'F'), List.of((int) 'a', (int) 'f')),
                ranges(c -> TextFacts.isHexDigit((char) c)));
        compareTraps("5.1 identifiers", r -> r.source().startsWith("traps: PORTING.md 5.1 ")
                && !r.source().contains("TextFacts.IsWhitespace") && !isUnicodeDigitTrap(r), Layer.TOKENS, 30);
    }

    /** PORTING.md 5.1: lexer dispatch uses {@code char.IsDigit}; U+0663 lexes as a long literal with value 0. */
    @Test
    void unicodeDigitLexing() {
        JsonNode table = fact("char.IsDigit", "table").path("value");
        int checked = 0;
        for (JsonNode range : table) {
            for (int c = range.get(0).asInt(); c <= range.get(1).asInt(); c++) {
                LexicalToken[] t = GoldenWriter.lex(String.valueOf((char) c));
                assertEquals(SyntaxKind.LongLiteralToken, t[0].kind(), "char.IsDigit " + hex((char) c) + " lexes as a long literal");
                String expected = c <= '9' ? String.valueOf((char) c) : "0";
                assertEquals(expected, DotNet.str(SyntaxToken.from(t[0]).value()), "value of " + hex((char) c));
                checked++;
            }
        }
        assertTrue(checked > 300, "char.IsDigit table has " + checked + " digits");
        LexicalToken[] t = GoldenWriter.lex("٣");
        assertEquals(SyntaxKind.LongLiteralToken, t[0].kind());
        assertEquals(0L, SyntaxToken.from(t[0]).value());
        compareTraps("5.1 Unicode digits", TrapsTest::isUnicodeDigitTrap, Layer.TOKENS, 6);
        compareTraps("5.1 Unicode digits", TrapsTest::isUnicodeDigitTrap, Layer.TOKEN_VALUES, 6);
    }

    /** PORTING.md 5.2: {@code Int32/Int64.TryParse}, failure gives 0. */
    @Test
    void literalValuesInt() {
        compareLiteralValues("5.2 int/long", Set.of(SyntaxKind.IntLiteralToken, SyntaxKind.LongLiteralToken), 25);
    }

    /** PORTING.md 5.2: {@code Double.TryParse} with Float | AllowThousands, overflow to infinity. */
    @Test
    void literalValuesReal() {
        compareLiteralValues("5.2 real", Set.of(SyntaxKind.RealLiteralToken), 25);
    }

    /** PORTING.md 5.2: {@code Decimal.TryParse} with NumberStyles.Number, 28-digit scale. */
    @Test
    void literalValuesDecimal() {
        compareLiteralValues("5.2 decimal", Set.of(SyntaxKind.DecimalLiteralToken), 10);
    }

    /** PORTING.md 5.2: {@code TimeSpan.TryParse} then unit suffixes; overflow and saturation rules. */
    @Test
    void literalValuesTimeSpan() {
        compareLiteralValues("5.2 timespan", Set.of(SyntaxKind.TimespanLiteralToken), 30);
    }

    /** PORTING.md 5.2: {@code DateTime.TryParse}, Z/offset values converted to UTC (D11). */
    @Test
    void literalValuesDateTime() {
        compareLiteralValues("5.2 datetime", Set.of(SyntaxKind.DateTimeLiteralToken), 20);
    }

    /** PORTING.md 5.2: {@code Guid.TryParse} for N, D, B, P, X; never {@code UUID.fromString}. */
    @Test
    void literalValuesGuid() {
        compareLiteralValues("5.2 guid", Set.of(SyntaxKind.GuidLiteralToken, SyntaxKind.RawGuidLiteralToken), 8);
    }

    /** PORTING.md 5.3: {@code SyntaxFacts.TryGetKind("")} is {@code (true, None)} (D13). */
    @Test
    void tryGetKindEmpty() {
        Out<SyntaxKind> kind = new Out<>();
        assertTrue(SyntaxFacts.tryGetKind("", kind), "D13: TryGetKind(\"\") is true");
        assertEquals(SyntaxKind.None, kind.value, "D13: TryGetKind(\"\") gives None");
        int n = 0;
        for (JsonNode f : facts("SyntaxFacts.TryGetKind")) {
            String input = f.get("input").asText();
            String text = input.startsWith("[") ? args(f).get(0).asText() : input;
            Out<SyntaxKind> k = new Out<>();
            boolean ok = SyntaxFacts.tryGetKind(text, k);
            assertEquals(f.get("ok").asBoolean(), ok, "TryGetKind(" + input + ")");
            assertEquals(f.get("value").asText(), k.value == null ? "None" : k.value.name(), "TryGetKind(" + input + ") kind");
            n++;
        }
        assertTrue(n >= 9, n + " TryGetKind facts");
    }

    /** PORTING.md 5.3: {@code Distinct()} keeps insertion order. */
    @Test
    void distinctInsertionOrder() {
        // traps 5.3 records are compared on bind/resultType by the conformance run (ConformanceTest).
    }

    /** PORTING.md 5.3/5.4: {@code OrdinalIgnoreCase} upper-cases per char ('_' vs 'a', K vs Kelvin sign). */
    @Test
    void ordinalIgnoreCaseCompare() {
        int n = 0;
        for (JsonNode f : facts("String.CompareOrdinalIgnoreCase")) {
            List<JsonNode> a = args(f);
            int actual = DotNetStrings.compareOrdinalIgnoreCase(a.get(0).asText(), a.get(1).asText());
            assertEquals(f.get("sign").asInt(), Integer.signum(actual), "CompareOrdinalIgnoreCase " + f.get("input").asText());
            n++;
        }
        for (JsonNode f : facts("String.EqualsOrdinalIgnoreCase")) {
            List<JsonNode> a = args(f);
            assertEquals(f.get("value").asBoolean(), DotNetStrings.equalsOrdinalIgnoreCase(a.get(0).asText(), a.get(1).asText()),
                    "EqualsOrdinalIgnoreCase " + f.get("input").asText());
            n++;
        }
        assertTrue(n >= 19, n + " OrdinalIgnoreCase facts");
        assertTrue(DotNetStrings.compareOrdinalIgnoreCase("_", "a") > 0, "'_' sorts after letters (upper-casing, not lower-casing)");
    }

    /** PORTING.md 5.4: {@code string.Compare(a, ia, b, ib, len)} clamps to the remaining length. */
    @Test
    void substringClampedCompare() {
        int n = 0;
        for (JsonNode f : facts("String.Compare")) {
            List<JsonNode> a = args(f);
            if (a.size() == 2) {
                continue;
            }
            String what = "String.Compare " + f.get("input").asText();
            if (!f.get("ok").asBoolean()) {
                assertEquals("!ArgumentOutOfRangeException", f.get("value").asText(), what);
                assertThrows(IndexOutOfBoundsException.class, () -> compare5or6(a), what);
            } else {
                assertEquals(f.get("sign").asInt(), Integer.signum(compare5or6(a)), what);
            }
            n++;
        }
        assertTrue(n >= 15, n + " five/six-argument Compare facts");
    }

    // ------------------------------------------------------------------ helpers

    private static int compare5or6(List<JsonNode> a) {
        return a.size() == 5
                ? DotNetStrings.compare(a.get(0).asText(), a.get(1).asInt(), a.get(2).asText(), a.get(3).asInt(), a.get(4).asInt())
                : DotNetStrings.compare(a.get(0).asText(), a.get(1).asInt(), a.get(2).asText(), a.get(3).asInt(), a.get(4).asInt(),
                        a.get(5).asBoolean());
    }

    static boolean isUnicodeDigitTrap(CorpusRecord r) {
        String s = r.source();
        return s.startsWith("traps: PORTING.md 5.1 ")
                && (s.contains("Arabic-Indic") || s.contains("fullwidth digits") || s.contains("Unicode digit"));
    }

    /**
     * Compares {@code layer} writer-vs-golden for every traps record matching {@code select};
     * fails naming the policy and every differing record. {@code minRecords} guards the selection.
     */
    private static void compareTraps(String policy, Predicate<CorpusRecord> select, Layer layer, int minRecords) {
        Traps t = traps();
        List<String> diffs = new ArrayList<>();
        int n = 0;
        for (CorpusRecord r : t.records.values()) {
            if (!select.test(r)) {
                continue;
            }
            n++;
            ObjectNode golden = t.golden(r.id());
            ObjectNode actual = WRITER.write(r, null);
            GoldenComparator.Difference d = GoldenComparator.firstDifference("", layer.project(golden), layer.project(actual));
            if (d != null) {
                diffs.add(r.id() + " " + d.path() + ": expected " + JsonText.snippet(d.expected(), 120)
                        + ", actual " + JsonText.snippet(d.actual(), 120) + "  [" + r.source() + "]");
            }
        }
        assertTrue(n >= minRecords, policy + ": only " + n + " traps records selected");
        if (!diffs.isEmpty()) {
            fail(policy + " (" + layer + "): " + diffs.size() + " of " + n + " traps records differ:\n  " + String.join("\n  ", diffs));
        }
    }

    /**
     * For every PORTING.md 5.1/5.2 traps record whose golden has a token of one of {@code kinds}:
     * compares {@code value} of those tokens (by index) and, when one of them throws,
     * {@code outcome.tokenValues}.
     */
    private static void compareLiteralValues(String policy, Set<SyntaxKind> kinds, int minRecords) {
        Traps t = traps();
        Set<String> kindNames = new java.util.HashSet<>();
        kinds.forEach(k -> kindNames.add(k.name()));
        List<String> diffs = new ArrayList<>();
        int records = 0;
        int tokens = 0;
        for (CorpusRecord r : t.records.values()) {
            if (!r.source().startsWith("traps: PORTING.md 5.2 ") && !r.source().startsWith("traps: PORTING.md 5.1 ")) {
                continue;
            }
            ObjectNode golden = t.golden(r.id());
            JsonNode et = golden.path("tokens");
            boolean any = false;
            for (JsonNode tok : et) {
                any |= kindNames.contains(tok.path("kind").asText());
            }
            if (!any) {
                continue;
            }
            records++;
            ObjectNode actual = WRITER.write(r, null);
            JsonNode at = actual.path("tokens");
            if (at.size() != et.size()) {
                diffs.add(r.id() + ": " + at.size() + " tokens, golden " + et.size() + "  [" + r.source() + "]");
                continue;
            }
            boolean threw = false;
            for (int i = 0; i < et.size(); i++) {
                JsonNode e = et.get(i);
                if (!kindNames.contains(e.path("kind").asText())) {
                    continue;
                }
                tokens++;
                JsonNode ev = e.get("value");
                JsonNode av = at.get(i).get("value");
                threw |= ev.isTextual() && ev.asText().startsWith("!") && ev.asText().endsWith("Exception");
                if (!ev.equals(av)) {
                    diffs.add(r.id() + " tokens[" + i + "] " + e.path("text") + ": expected " + ev + ", actual " + av
                            + "  [" + r.source() + "]");
                }
            }
            if (threw) {
                JsonNode eo = golden.path("outcome").path("tokenValues");
                JsonNode ao = actual.path("outcome").path("tokenValues");
                if (!eo.equals(ao)) {
                    diffs.add(r.id() + " outcome.tokenValues: expected " + eo + ", actual " + ao);
                }
            }
        }
        assertTrue(records >= minRecords, policy + ": only " + records + " traps records selected");
        if (!diffs.isEmpty()) {
            fail(policy + " literal values: " + diffs.size() + " differences in " + records + " records (" + tokens
                    + " tokens):\n  " + String.join("\n  ", diffs));
        }
    }

    /** Inclusive {@code [start, end]} ranges over U+0000..U+FFFF where {@code p} holds. */
    static List<List<Integer>> ranges(IntPredicate p) {
        List<List<Integer>> out = new ArrayList<>();
        int start = -1;
        for (int c = 0; c <= 0x10000; c++) {
            boolean in = c <= 0xFFFF && p.test(c);
            if (in && start < 0) {
                start = c;
            } else if (!in && start >= 0) {
                out.add(List.of(start, c - 1));
                start = -1;
            }
        }
        return out;
    }

    private static boolean inRanges(JsonNode table, char c) {
        for (JsonNode r : table) {
            if (c >= r.get(0).asInt() && c <= r.get(1).asInt()) {
                return true;
            }
        }
        return false;
    }

    private static String hex(char c) {
        return String.format("U+%04X", (int) c);
    }

    private static JsonNode facts() {
        JsonNode f = facts;
        if (f == null) {
            try {
                f = Harness.MAPPER.readTree(Files.readString(Harness.resourcesDir().resolve("dotnet-facts.json"))).path("facts");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            facts = f;
        }
        return f;
    }

    private static List<JsonNode> facts(String api) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode f : facts()) {
            if (api.equals(f.path("api").asText())) {
                out.add(f);
            }
        }
        if (out.isEmpty()) {
            fail("dotnet-facts.json has no " + api + " facts");
        }
        return out;
    }

    private static JsonNode fact(String api, String input) {
        for (JsonNode f : facts(api)) {
            if (input.equals(f.path("input").asText())) {
                return f;
            }
        }
        return fail("dotnet-facts.json has no " + api + " " + input);
    }

    private static List<JsonNode> args(JsonNode fact) {
        try {
            List<JsonNode> out = new ArrayList<>();
            Harness.MAPPER.readTree(fact.get("input").asText()).forEach(out::add);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Traps traps() {
        Traps t = traps;
        if (t == null) {
            Assumptions.assumeTrue(GoldenReader.exists("traps"), "no traps golden");
            Map<String, CorpusRecord> records = new LinkedHashMap<>();
            for (CorpusRecord r : CorpusReader.read("traps")) {
                records.put(r.id(), r);
            }
            t = new Traps(records, GoldenReader.readAll("traps").records());
            traps = t;
        }
        return t;
    }

    private record Traps(Map<String, CorpusRecord> records, Map<String, ObjectNode> goldens) {
        ObjectNode golden(String id) {
            ObjectNode g = goldens.get(id);
            if (g == null) {
                fail("traps golden has no record " + id);
            }
            return g;
        }
    }
}
