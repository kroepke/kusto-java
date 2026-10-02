// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit and golden tests for the KustoFacts port (string literal values, bracketing, wildcard matching, tables).

package org.graylog.kusto.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.graylog.kusto.language.parsing.LexicalToken;
import org.graylog.kusto.language.parsing.TokenParser;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.Tabularity;
import org.graylog.kusto.language.syntax.Statement;
import org.graylog.kusto.language.syntax.SyntaxFacts;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.SyntaxToken;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.IntRef;
import org.graylog.kusto.language.utils.dotnet.Out;
import org.junit.jupiter.api.Test;

class KustoFactsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RESOURCES = Paths.get(System.getProperty("kusto.conformanceResources",
            Paths.get("..", "kusto-language-conformance", "src", "test", "resources").toString()));
    private static final Path EXCEPTION_MAP = Paths.get("..", "porting", "exception-map.json");

    // ------------------------------------------------------------------ goldens

    /**
     * Every {@code StringLiteralToken} of the {@code traps} and {@code readme} goldens: the value of
     * {@code SyntaxToken.from(lexicalToken).value()} (lexed by the real {@code TokenParser}) rendered with
     * {@code DotNet.str} equals the oracle's {@code tokens[].value}. A golden {@code "!<Name>"} means the
     * oracle threw {@code <Name>}; Java must throw the type {@code porting/exception-map.json} maps it to.
     */
    @Test
    void goldenStringLiteralValuesTraps() throws IOException {
        int[] counts = checkGoldenStringValues("traps");
        // python3: count tokens[] with kind StringLiteralToken / value starting with '!' in goldens/traps.jsonl.gz
        assertEquals(162, counts[0], "string literal tokens checked");
        assertEquals(2, counts[1], "throwing string literal tokens checked (traps/0312, traps/0313)");
    }

    @Test
    void goldenStringLiteralValuesReadme() throws IOException {
        int[] counts = checkGoldenStringValues("readme");
        assertEquals(0, counts[1]);
    }

    @Test
    void goldenStringLiteralValuesDocsAndSentinel() throws IOException {
        int[] docs = checkGoldenStringValues("docs");
        int[] sentinel = checkGoldenStringValues("sentinel");
        assertTrue(docs[0] > 0 && sentinel[0] > 0, "corpora contain string literals");
    }

    /** Returns {checked, throwing}. */
    private static int[] checkGoldenStringValues(String corpus) throws IOException {
        Map<String, String> dotnetToJava = exceptionMap();
        Map<String, String> texts = readCorpus(RESOURCES.resolve("corpus").resolve(corpus + ".jsonl"));
        List<JsonNode> goldens = readGoldens(RESOURCES.resolve("goldens").resolve(corpus + ".jsonl.gz"));
        ParseOptions options = ParseOptions.Default.withAlwaysProduceEndTokens(true);
        List<String> failures = new ArrayList<>();
        int checked = 0;
        int throwing = 0;
        for (JsonNode golden : goldens) {
            String id = golden.get("id").asText();
            String text = texts.get(id);
            assertNotNull(text, id + ": no corpus text");
            JsonNode expected = golden.get("tokens");
            LexicalToken[] tokens = TokenParser.parseTokens(text, options);
            for (int i = 0; i < expected.size(); i++) {
                JsonNode e = expected.get(i);
                if (!"StringLiteralToken".equals(e.get("kind").asText())) {
                    continue;
                }
                checked++;
                if (i >= tokens.length || tokens[i].kind() != SyntaxKind.StringLiteralToken
                        || !tokens[i].text().equals(e.get("text").asText())) {
                    failures.add(id + " token " + i + ": lexer mismatch");
                    continue;
                }
                String want = e.get("value").asText();
                SyntaxToken token = SyntaxToken.from(tokens[i]);
                if (want.startsWith("!")) {
                    throwing++;
                    String javaName = dotnetToJava.get(want.substring(1));
                    try {
                        Object v = token.value();
                        failures.add(id + " token " + i + ": expected " + want + ", got " + JSON.valueToTree(DotNet.str(v)));
                    } catch (RuntimeException ex) {
                        if (!ex.getClass().getSimpleName().equals(javaName)) {
                            failures.add(id + " token " + i + ": expected " + want + " (" + javaName + "), got " + ex);
                        }
                    }
                    continue;
                }
                String got;
                try {
                    got = DotNet.str(token.value());
                } catch (RuntimeException ex) {
                    failures.add(id + " token " + i + ": expected " + JSON.valueToTree(want) + ", threw " + ex);
                    continue;
                }
                if (!want.equals(got)) {
                    failures.add(id + " token " + i + " " + JSON.valueToTree(tokens[i].text()) + ": expected "
                            + JSON.valueToTree(want) + ", got " + JSON.valueToTree(got));
                }
                // the direct call agrees with the token path
                assertEquals(got, KustoFacts.getStringLiteralValue(tokens[i].text()), id);
            }
        }
        if (!failures.isEmpty()) {
            fail(corpus + ": " + failures.size() + " mismatches, first: " + failures.subList(0, Math.min(20, failures.size())));
        }
        return new int[] {checked, throwing};
    }

    private static Map<String, String> exceptionMap() throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        JsonNode dotnet = JSON.readTree(EXCEPTION_MAP.toFile()).get("dotnet");
        dotnet.fields().forEachRemaining(f -> map.put(f.getKey(), f.getValue().asText()));
        return map;
    }

    private static Map<String, String> readCorpus(Path path) throws IOException {
        Map<String, String> texts = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (!line.isEmpty()) {
                JsonNode rec = JSON.readTree(line);
                texts.put(rec.get("id").asText(), rec.get("text").asText());
            }
        }
        return texts;
    }

    private static List<JsonNode> readGoldens(Path path) throws IOException {
        List<JsonNode> records = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(path)), StandardCharsets.UTF_8))) {
            String line = r.readLine(); // header
            while (line != null && (line = r.readLine()) != null) {
                if (!line.isEmpty()) {
                    records.add(JSON.readTree(line));
                }
            }
        }
        return records;
    }

    // ------------------------------------------------------------------ getStringLiteralValue

    private static void value(String literal, String expected) {
        assertEquals(expected, KustoFacts.getStringLiteralValue(literal), literal);
    }

    @Test
    void regularEscapes() {
        value("\"a\\tb\"", "a\tb");
        value("'a\\nb'", "a\nb");
        value("\"a\\\\b\"", "a\\b");
        value("\"a\\\"b\"", "a\"b");
        value("'a\\'b'", "a'b");
        value("\"\\a\\b\\f\\n\\r\\t\\v\"", "\u0007\b\f\n\r\t\u000B");
        value("\"\\u00e9\"", "\u00e9");
        value("'\\uD83D\\uDE00'", "\uD83D\uDE00");
        value("'\\uD800'", "\uD800"); // lone surrogate via \\u is allowed (plain char cast)
        value("\"\\U0001F600\"", "\uD83D\uDE00");
        value("\"\\U00000041\"", "A");
        value("\"\\x41\"", "A");
        value("\"\\101\"", "A");
        value("\"\\0\"", "\u0000");
        value("\"\\400\"", "\u0100");
    }

    @Test
    void invalidAndShortEscapes() {
        value("\"\\q\"", "q"); // unknown escape: just the character
        value("\"\\u12\"", "\u0012"); // hex stops at the closing quote
        value("\"\\x4g\"", "\u0004g");
        value("\"\\xzz\"", "\u0000zz"); // no hex digits: value 0, nothing consumed
        value("\"\\19\"", "\u0011"); // char.IsDigit accepts 8/9: (1 << 3) + 9
        value("\"\\1\"", "\u0001");
        value("\"\\1234\"", "S4"); // at most three octal digits: 0123 = 83 = 'S'
        value("\"\\\u0663\"", String.valueOf((char) (0x0663 - '0'))); // Unicode digit: garbage value, mirrored
        value("\"abc\\\"", "abc\\"); // trailing backslash before the end quote (unterminated)
        assertThrows(IndexOutOfBoundsException.class, () -> KustoFacts.getStringLiteralValue("\"\\U0000D800\""));
        assertThrows(IndexOutOfBoundsException.class, () -> KustoFacts.getStringLiteralValue("\"\\U00110000\""));
        assertThrows(IndexOutOfBoundsException.class, () -> KustoFacts.getStringLiteralValue("\"\\UFFFFFFFF\"")); // negative int
    }

    @Test
    void verbatimAndHidden() {
        value("@\"c:\\path\"", "c:\\path");
        value("@'x''y'", "x'y");
        value("@\"a\"\"b\"", "a\"b");
        value("@\"\"", "");
        value("@'abc'", "abc");
        value("h\"hidden\"", "hidden");
        value("H'x'", "x");
        value("h@'c:\\x'", "c:\\x");
        value("H@\"a\"\"b\"", "a\"b");
        value("h'a\\tb'", "a\tb");
    }

    @Test
    void multiLine() {
        value("```a\nb```", "a\nb");
        value("~~~a~~~", "a");
        value("~~~a\r\nb~~~", "a\r\nb");
        value("``````", "");
        value("```", "`"); // too short to be multi-line: a back-quoted single char
        value("```abc", "abc"); // unterminated
        value("~~~abc", "abc");
        value("h```x```", "x");
        value("```a\\tb```", "a\\tb"); // no escapes in multi-line strings
    }

    @Test
    void unterminatedAndEmpty() {
        value("", "");
        value("h", "");
        value("@", "");
        value("'", "");
        value("\"", "");
        value("'abc", "abc");
        value("\"abc", "abc");
        value("''", "");
        value("\"\"", "");
        value("'a\u0000b'", "a\u0000b");
        value("\"a\uD800b\"", "a\uD800b");
    }

    @Test
    void bracketed() {
        value("[abc]", "abc");
        value("[a\\tb]", "a\\tb"); // no escape decoding in brackets
        value("[abc", "abc");
    }

    // ------------------------------------------------------------------ bracketing and quoting

    @Test
    void bracketNameIfNecessary() {
        assertEquals("abc", KustoFacts.bracketNameIfNecessary("abc"));
        assertEquals("_x1", KustoFacts.bracketNameIfNecessary("_x1"));
        assertEquals("['where']", KustoFacts.bracketNameIfNecessary("where"));
        assertEquals("['a b']", KustoFacts.bracketNameIfNecessary("a b"));
        assertEquals("[\"it's\"]", KustoFacts.bracketNameIfNecessary("it's"));
        assertEquals("['1abc']", KustoFacts.bracketNameIfNecessary("1abc"));
        assertEquals("['true']", KustoFacts.bracketNameIfNecessary("true"));
        assertEquals("['']", KustoFacts.bracketNameIfNecessary(""));
        assertEquals("['a-b']", KustoFacts.bracketNameIfNecessary("a-b"));
        // a keyword that can be an identifier stays bare in queries
        String kw = SyntaxFacts.getText(KustoFacts.KeywordsAsIdentifiers.get(0));
        assertEquals(kw, KustoFacts.bracketNameIfNecessary(kw));
        // dialects
        assertEquals("['accumulate']", KustoFacts.bracketNameIfNecessary("accumulate", KustoDialect.EngineCommand));
        assertEquals("foo", KustoFacts.bracketNameIfNecessary("foo", KustoDialect.EngineCommand));
        assertEquals("['foo']", KustoFacts.bracketNameIfNecessary("foo", KustoDialect.ClusterManagerCommand));
        assertEquals("['foo']", KustoFacts.bracketNameIfNecessary("foo", KustoDialect.DataManagerCommand));
        assertEquals("where", KustoFacts.bracketNameIfNecessary("where", KustoDialect.Unknown));
        assertEquals("['1a']", KustoFacts.bracketNameIfNecessary("1a", KustoDialect.Unknown));
    }

    @Test
    void canBeIdentifier() {
        assertFalse(KustoFacts.canBeIdentifier(null));
        assertFalse(KustoFacts.canBeIdentifier(""));
        assertTrue(KustoFacts.canBeIdentifier("abc"));
        assertFalse(KustoFacts.canBeIdentifier("where"));
        assertFalse(KustoFacts.canBeIdentifier("false"));
        assertFalse(KustoFacts.canBeIdentifier("9a"));
        assertFalse(KustoFacts.canBeIdentifier("\u00e9t\u00e9")); // identifiers are ASCII (TextFacts)
    }

    @Test
    void getBracketedNameAndLiterals() {
        assertEquals("['a']", KustoFacts.getBracketedName("a"));
        assertEquals("['where']", KustoFacts.getBracketedName("where"));
        assertEquals("['a\\nb']", KustoFacts.getBracketedName("a\nb"));
        assertEquals("[\"a'b\\\"c\"]", KustoFacts.getBracketedName("a'b\"c"));
        assertEquals("'a\\tb\\\\c\\a\\b\\f\\r\"'", KustoFacts.getSingleQuotedStringLiteral("a\tb\\c\u0007\b\f\r\""));
        assertEquals("'x\\'y'", KustoFacts.getSingleQuotedStringLiteral("x'y"));
        assertEquals("\"x'y\\\"z\\n\"", KustoFacts.getDoubleQuotedStringLiteral("x'y\"z\n"));
        assertEquals("'\u000B'", KustoFacts.getSingleQuotedStringLiteral("\u000B")); // \v is not escaped
        assertEquals("```x```", KustoFacts.getMultiLineStringLiteral("x"));
        assertEquals("``````", KustoFacts.getMultiLineStringLiteral(null)); // .NET concat renders null as ""
        for (String s : new String[] {"", "a", "a'b", "a\"b", "a'b\"c", "\\", "\t\n\r\b\f\u0007", "\u00e9\uD83D\uDE00"}) {
            assertEquals(s, KustoFacts.getStringLiteralValue(KustoFacts.getStringLiteral(s)), "round trip " + s);
        }
    }

    // ------------------------------------------------------------------ matches

    @Test
    void matchesWildcards() {
        assertFalse(KustoFacts.matches("", "abc"));
        assertFalse(KustoFacts.matches("", ""));
        assertTrue(KustoFacts.matches("*", ""));
        assertTrue(KustoFacts.matches("*", "abc"));
        assertTrue(KustoFacts.matches("**", "abc"));
        assertTrue(KustoFacts.matches("abc", "abc"));
        assertFalse(KustoFacts.matches("abc", "abcd"));
        assertFalse(KustoFacts.matches("abc", "ABC"));
        assertTrue(KustoFacts.matches("abc", "ABC", true));
        assertTrue(KustoFacts.matches("ab*", "abxyz"));
        assertTrue(KustoFacts.matches("ab*", "ab"));
        assertFalse(KustoFacts.matches("ab*", "a"));
        assertTrue(KustoFacts.matches("*bc", "xxbc"));
        assertFalse(KustoFacts.matches("*bc", "bcx"));
        assertTrue(KustoFacts.matches("*b*", "abc"));
        assertFalse(KustoFacts.matches("*b*", "ac"));
        assertTrue(KustoFacts.matches("a*c", "abbc"));
        assertTrue(KustoFacts.matches("a**c", "ac"));
        assertTrue(KustoFacts.matches("*a*c*", "xaxcx"));
        assertTrue(KustoFacts.matches("a*b*c", "aXbYc"));
        assertFalse(KustoFacts.matches("a*b*c", "acb"));
        assertFalse(KustoFacts.matches("ab*b", "ab")); // ends-with segment may not overlap the starts-with segment
        assertFalse(KustoFacts.matches("a*a", "a"));
        assertTrue(KustoFacts.matches("a*a", "aa"));
        assertTrue(KustoFacts.matches("*aab", "aaab")); // contains/ends-with retries after a failed first char
        assertTrue(KustoFacts.matches("*ab*", "aab"));
        assertTrue(KustoFacts.matches("AB*cd", "abXCD", true));
        assertFalse(KustoFacts.matches("AB*cd", "abXCD", false));
        // OrdinalIgnoreCase upper-cases only: KELVIN SIGN never equals 'k'
        assertFalse(KustoFacts.matches("k", "\u212A", true));
        assertTrue(KustoFacts.matches("\u00e9", "\u00c9", true));
        assertThrows(NullPointerException.class, () -> KustoFacts.matches(null, "a"));
        assertThrows(NullPointerException.class, () -> KustoFacts.matches("a", null));
    }

    // ------------------------------------------------------------------ tables

    @Test
    void stringTablesMatchUpstreamCounts() {
        // Counts taken from KustoFacts.cs by script: split each initializer body on ',' after stripping // comments.
        assertEquals(17, KustoFacts.ParamTypes.size());
        assertEquals(27, KustoFacts.ExtendedParamTypes.size()); // "decimal" twice upstream
        assertSame(KustoFacts.ExtendedParamTypes, KustoFacts.StorageTypes);
        assertEquals(21, KustoFacts.ChartTypes.size());
        assertEquals(3, KustoFacts.HiddenChartTypes.size());
        assertEquals(18, KustoFacts.VisibleChartTypes.size());
        assertFalse(KustoFacts.VisibleChartTypes.contains("3Dchart"));
        assertEquals("table", KustoFacts.VisibleChartTypes.get(0));
        assertEquals(40, KustoFacts.KnownInternalFunctionNames.size());
        assertEquals("__get_scalar", KustoFacts.KnownInternalFunctionNames.get(39));
        assertEquals(16, KustoFacts.Directives.size());
        assertEquals(12, KustoFacts.DateTimeParts.size());
        assertEquals(11, KustoFacts.DateDiffParts.size());
        assertEquals(12, KustoFacts.JoinKinds.size());
        assertSame(KustoFacts.HintStrategies, KustoFacts.DistinctHintStrategies);
        assertSame(KustoFacts.HintStrategies, KustoFacts.JoinHintStrategies);
        assertSame(KustoFacts.HintDistributions, KustoFacts.EvaluateHintDistributions);
        assertSame(KustoFacts.HintRemotes, KustoFacts.EvaluateHintRemotes);
        assertSame(KustoFacts.HintConcurrencies, KustoFacts.PartitionHintConcurrencies);
        assertSame(KustoFacts.HintSpreads, KustoFacts.PartitionHintSpreads);
        assertSame(KustoFacts.HintConcurrencies, KustoFacts.UnionHintConcurrencies);
        assertSame(KustoFacts.HintSpreads, KustoFacts.UnionHintSpreads);
        assertEquals("isfuzzy", KustoFacts.unionIsFuzzyProperty());
        assertEquals(List.of("splitBlock", "multipleBlocks"), KustoFacts.SortHintStrategies);
        assertEquals("```", KustoFacts.MultiLineStringQuote);
        assertEquals("~~~", KustoFacts.AlternateMultiLineStringQuote);
        assertEquals(".kusto.windows.net", KustoFacts.KustoWindowsNet);
    }

    @Test
    void keywordTables() throws Exception {
        // KeywordsAsIdentifiers = SyntaxFacts.GetKindsWithFixedText().Where(k => k.IsKeyword() && k.CanBeIdentifier())
        List<SyntaxKind> expected = new ArrayList<>();
        for (SyntaxKind k : SyntaxFacts.getKindsWithFixedText()) {
            if (SyntaxFacts.isKeyword(k) && SyntaxFacts.canBeIdentifier(k)) {
                expected.add(k);
            }
        }
        assertEquals(expected, KustoFacts.KeywordsAsIdentifiers);
        assertFalse(expected.isEmpty());
        // KustoFacts.cs:434-484: 47 extra kinds (python3: count 'SyntaxKind\.\w+' in that initializer)
        assertEquals(KustoFacts.KeywordsAsIdentifiers.size() + 47, KustoFacts.ExtendedKeywordsAsIdentifiers.size());
        assertEquals(KustoFacts.KeywordsAsIdentifiers, KustoFacts.ExtendedKeywordsAsIdentifiers.subList(0, KustoFacts.KeywordsAsIdentifiers.size()));
        assertEquals(SyntaxKind.AccumulateKeyword, KustoFacts.ExtendedKeywordsAsIdentifiers.get(KustoFacts.KeywordsAsIdentifiers.size()));
        assertEquals(SyntaxKind.WhereKeyword, KustoFacts.ExtendedKeywordsAsIdentifiers.get(KustoFacts.ExtendedKeywordsAsIdentifiers.size() - 1));
        assertEquals(List.of(SyntaxKind.KindKeyword, SyntaxKind.WithSourceKeyword, SyntaxKind.With_SourceKeyword), KustoFacts.SpecialKeywordsAsIdentifiers);
        assertEquals(26, KustoFacts.ForkOperatorKinds.size());
        assertEquals(46, KustoFacts.PostPipeOperatorKinds.size());
        assertEquals(SyntaxKind.UnionOperator, KustoFacts.PostPipeOperatorKinds.get(45));

        // KustoFacts_Keywords.cs: 169 entries, all distinct (python3: re.findall(r'^\s*"([^"]*)",?\s*$', src, re.M))
        Field f = KustoFacts.class.getDeclaredField("s_engineCommandKeywordsThatNeedBrackets");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> engine = (Set<String>) f.get(null);
        assertEquals(169, engine.size());
        assertEquals("__unique", engine.iterator().next());
        assertTrue(engine.contains("graph_shards"));
        for (String s : engine) {
            assertFalse(KustoFacts.canBeIdentifier(s, KustoDialect.EngineCommand), s);
        }
    }

    // ------------------------------------------------------------------ hex / octal

    private static int decode(String method, String text, int length, IntRef index) throws Exception {
        Method m = KustoFacts.class.getDeclaredMethod(method, String.class, int.class, IntRef.class);
        m.setAccessible(true);
        return (int) m.invoke(null, text, length, index);
    }

    @Test
    void decodeHexAndOctal() throws Exception {
        IntRef i = new IntRef(0);
        assertEquals(0xff, decode("decodeHex", "ff", 2, i));
        assertEquals(2, i.value);
        i = new IntRef(0);
        assertEquals(0xAB, decode("decodeHex", "aBz", 4, i));
        assertEquals(2, i.value); // stops at the first non-hex char
        i = new IntRef(0);
        assertEquals(0, decode("decodeHex", "zz", 2, i));
        assertEquals(0, i.value);
        i = new IntRef(0);
        assertEquals(-1, decode("decodeHex", "FFFFFFFF", 8, i)); // unchecked int overflow, as in C#
        i = new IntRef(1);
        assertEquals(0x12, decode("decodeHex", "x12345", 2, i));
        assertEquals(3, i.value);
        i = new IntRef(0);
        assertEquals(0x1, decode("decodeHex", "1", 4, i)); // bounded by the text length
        assertEquals(1, i.value);

        i = new IntRef(0);
        assertEquals(0777, decode("decodeOctal", "777", 3, i));
        assertEquals(3, i.value);
        i = new IntRef(0);
        assertEquals(0123, decode("decodeOctal", "1234", 3, i)); // at most three digits; length is ignored upstream
        assertEquals(3, i.value);
        i = new IntRef(0);
        assertEquals(0123, decode("decodeOctal", "1234", 1, i));
        i = new IntRef(0);
        assertEquals(9, decode("decodeOctal", "9", 3, i)); // 8 and 9 accepted (char.IsDigit)
        i = new IntRef(0);
        assertEquals(0, decode("decodeOctal", "a", 3, i));
        assertEquals(0, i.value);
    }

    // ------------------------------------------------------------------ lazy KnownQueryOperatorParameterNames

    @Test
    void knownQueryOperatorParameterNamesIsLazy() throws Exception {
        // class init does not touch QueryOperatorParameters (W3): loading and initialising KustoFacts succeeds
        Class<?> c = Class.forName("org.graylog.kusto.language.KustoFacts", true, KustoFactsTest.class.getClassLoader());
        Field f = c.getDeclaredField("_knownQueryOperatorParameterNames");
        f.setAccessible(true);
        assertNull(f.get(null));
        // the accessor is where the W3 dependency lives: every name and alias of AllParameters, sorted ordinally
        List<String> names = KustoFacts.knownQueryOperatorParameterNames();
        assertNotNull(f.get(null));
        assertTrue(names.contains("kind"));
        assertTrue(names.contains("hint.strategy"));
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(null);
        assertEquals(sorted, names);
        int expected = 0;
        for (QueryOperatorParameter q : QueryOperatorParameters.AllParameters) {
            expected += 1 + q.aliases().size();
        }
        assertEquals(expected, names.size());
    }

    // ------------------------------------------------------------------ host names

    @Test
    void hostNames() {
        assertEquals("help.kusto.windows.net", KustoFacts.getHostName("https://help.kusto.windows.net:443/Samples?x=1"));
        assertEquals("help", KustoFacts.getHostName("help"));
        assertEquals("a", KustoFacts.getHostName("a b"));
        assertEquals("", KustoFacts.getHostName("://x")); // prefix at 0 is not a scheme
        Out<String> host = new Out<>();
        Out<String> path = new Out<>();
        KustoFacts.getHostAndPath("https://help.kusto.windows.net/Samples?x", host, path);
        assertEquals("help.kusto.windows.net", host.value);
        assertEquals("Samples", path.value);
        KustoFacts.getHostAndPath("help", host, path);
        assertEquals("help", host.value);
        assertNull(path.value);

        assertTrue(KustoFacts.isHostName("Help.Kusto.Windows.Net", "help.kusto.windows.net"));
        assertFalse(KustoFacts.isHostName("help", "help2"));

        String suffix = ".kusto.windows.net";
        assertEquals("help.kusto.windows.net", KustoFacts.getFullHostName("help", suffix));
        assertEquals("help.westus.kusto.windows.net", KustoFacts.getFullHostName("help.westus", suffix));
        assertEquals("a.b.c", KustoFacts.getFullHostName("a.b.c", suffix));
        assertEquals("help", KustoFacts.getFullHostName("help", "kusto.windows.net"));
        assertEquals("help.", KustoFacts.getFullHostName("help.", suffix));
        assertTrue(KustoFacts.hasShortHostName("help.kusto.windows.net", suffix));
        assertTrue(KustoFacts.hasShortHostName("HELP.KUSTO.WINDOWS.NET", suffix));
        assertFalse(KustoFacts.hasShortHostName("a.b.c.kusto.windows.net", suffix));
        assertFalse(KustoFacts.hasShortHostName(".net", suffix)); // shorter than the suffix: no throw
        assertFalse(KustoFacts.hasShortHostName(suffix, suffix));
        assertFalse(KustoFacts.hasShortHostName("help.kusto.windows.net", ""));
        assertEquals("help", KustoFacts.getShortHostName("help.kusto.windows.net", suffix));
        assertNull(KustoFacts.getShortHostName("help.example.com", suffix));
        assertNull(KustoFacts.getShortHostName(null, suffix));
        assertTrue(KustoFacts.isShortHostName("HELP", "help.kusto.windows.net", suffix));
        assertFalse(KustoFacts.isShortHostName("hel", "help.kusto.windows.net", suffix));
        assertFalse(KustoFacts.isShortHostName("xelp", "help.kusto.windows.net", suffix));
        assertTrue(KustoFacts.isPossibleShortHostName("a.b"));
        assertFalse(KustoFacts.isPossibleShortHostName("a.b.c"));
        assertFalse(KustoFacts.isPossibleShortHostName(""));
    }

    // ------------------------------------------------------------------ literal types and tabularity

    @Test
    void literalTypes() {
        assertSame(ScalarTypes.Bool, KustoFacts.getLiteralType(SyntaxKind.BooleanLiteralToken));
        assertSame(ScalarTypes.String, KustoFacts.getLiteralType(SyntaxKind.CompoundStringLiteralExpression));
        assertSame(ScalarTypes.Long, KustoFacts.getLiteralType(SyntaxKind.LongLiteralExpression));
        assertSame(ScalarTypes.Guid, KustoFacts.getLiteralType(SyntaxKind.RawGuidLiteralToken));
        assertSame(ScalarTypes.Type, KustoFacts.getLiteralType(SyntaxKind.TypeOfLiteralExpression));
        assertSame(ScalarTypes.Dynamic, KustoFacts.getLiteralType(SyntaxKind.DynamicExpression));
        assertSame(ScalarTypes.Unknown, KustoFacts.getLiteralType(SyntaxKind.IdentifierToken));
    }

    @Test
    void syntaxTabularityWithoutStatements() {
        assertEquals(Tabularity.None, KustoFacts.getSyntaxTabularity((Statement) null));
        assertEquals(Tabularity.None, KustoFacts.getSyntaxTabularity((Statement) null, null));
        assertEquals(Tabularity.None, KustoFacts.getSyntaxTabularity(
                (org.graylog.kusto.language.syntax.SyntaxList1<org.graylog.kusto.language.syntax.SeparatedElement1<Statement>>) null));
    }
}
