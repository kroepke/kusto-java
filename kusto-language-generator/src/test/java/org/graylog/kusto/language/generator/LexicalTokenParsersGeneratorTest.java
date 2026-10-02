// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: tests the Parsers.java declaration parser and the LexicalTokenParsers facade emission (PORTING.md 3.10, D7).
package org.graylog.kusto.language.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class LexicalTokenParsersGeneratorTest {
    private static final String SAMPLE = """
            package org.graylog.kusto.language.parsing;

            import java.util.List;
            import java.util.function.Function;
            import java.util.function.Predicate;

            public final class Parsers
            {
                @SafeVarargs
                public static <TInput> Parser<TInput> and(Parser<TInput>... parsers)
                {
                    return null;
                }

                @SuppressWarnings("rawtypes")
                public static final Parser Any =
                    match((Object t) -> true);

                public static <TInput, TLeft, TOutput> Parser2<TInput, TOutput> apply(Parser2<TInput, TLeft> leftParser, Function<LeftValue<TLeft>, RightParser<TInput, TOutput>> fnRightParser)
                {
                    return null;
                }

                public static <TOutput> Parser2<Character, TOutput> convertText(Parser<Character> pattern, Function<String, TOutput> producer) // PORT: §2.5 comment (with parens)
                {
                    return null;
                }

                public static <TInput, TElement, TProducer> Parser2<TInput, TProducer> list(
                    Parser2<TInput, TElement> elementParser, // PORT: §3.8 Func<A, B>
                    boolean oneOrMore /* named */,
                    Function<List<TElement>, TProducer> producer)
                {
                    return null;
                }

                @SafeVarargs
                public static <TInput> Parser<TInput> matchAny(TInput... items)
                {
                    return null;
                }

                public static Parser<Character> match(char ch)
                {
                    return null;
                }

                public static final class LeftValue<TLeft>
                {
                    public static <T> void inner(T t)
                    {
                    }
                }
            }
            """;

    @Test
    void parses_members_and_skips_non_input_ones() {
        var parsed = LexicalTokenParsersGenerator.parse(SAMPLE);
        assertEquals(List.of("and", "apply", "list", "matchAny"),
                parsed.facadeMethods().stream().map(LexicalTokenParsersGenerator.Method::name).toList());
        assertEquals(List.of("Any (field)",
                "convertText(Parser<Character>, Function<String, TOutput>) (TInput is not a type parameter)",
                "match(char) (not generic)", "LeftValue (nested type)"), parsed.skipped());
        var list = parsed.facadeMethods().get(2);
        assertEquals(List.of("TInput", "TElement", "TProducer"), list.typeParameters());
        assertEquals(List.of("elementParser", "oneOrMore", "producer"),
                list.parameters().stream().map(LexicalTokenParsersGenerator.Parameter::name).toList());
        assertEquals("boolean", list.parameters().get(1).type());
    }

    @Test
    void emits_bound_delegating_methods() {
        String out = LexicalTokenParsersGenerator.generate(LexicalTokenParsersGenerator.parse(SAMPLE));
        assertTrue(out.contains("""
                    @SafeVarargs
                    @SuppressWarnings("varargs")
                    public static Parser<LexicalToken> and(Parser<LexicalToken>... parsers)
                    {
                        return Parsers.<LexicalToken>and(parsers);
                    }
                """), out);
        assertTrue(out.contains("public static <TLeft, TOutput> Parser2<LexicalToken, TOutput> apply("
                + "Parser2<LexicalToken, TLeft> leftParser, Function<Parsers.LeftValue<TLeft>, "
                + "RightParser<LexicalToken, TOutput>> fnRightParser)"), out);
        assertTrue(out.contains("return Parsers.<LexicalToken, TElement, TProducer>list(elementParser, oneOrMore, producer);"), out);
        // reifiable varargs: no @SafeVarargs
        assertTrue(out.contains("    }\n\n    public static Parser<LexicalToken> matchAny(LexicalToken... items)\n"), out);
        assertTrue(out.contains("public static final Parser<LexicalToken> Any = Parsers.any();"), out);
        assertTrue(out.contains("import java.util.List;\nimport java.util.function.Function;\n"), out);
        assertFalse(out.contains("Predicate"), out);
        assertFalse(out.contains("convertText"), out);
        assertTrue(out.startsWith("// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0\n"
                + "// Copyright (c) 2026 Graylog, Inc. Purpose: non-generic facade over Parsers<LexicalToken>, "
                + "generated by kusto-language-generator (D7)\n// GENERATED"), out);
    }

    @Test
    void real_parsers_source_yields_the_expected_facade() throws IOException {
        Path file = LexicalTokenParsersGenerator.parsingDirectory(RepoPaths.kustoLanguageSourceRoot())
                .resolve(LexicalTokenParsersGenerator.SOURCE_FILE);
        Assumptions.assumeTrue(Files.isRegularFile(file), "no " + file);
        var parsed = LexicalTokenParsersGenerator.parse(Files.readString(file, StandardCharsets.UTF_8));
        var facade = parsed.facadeMethods();
        // 76 upstream Parsers<TInput> methods whose TInput stays generic, plus the typed any() accessor
        assertEquals(77, facade.size());
        assertEquals(35, facade.stream().map(LexicalTokenParsersGenerator.Method::name).distinct().count());
        assertEquals(List.of("Any (field)",
                "convertText(Parser<Character>, Function<String, TOutput>) (TInput is not a type parameter)",
                "match(char, boolean) (not generic)", "match(char) (not generic)",
                "match(String, boolean) (not generic)", "match(String) (not generic)",
                "LeftValue (nested type)",
                "text(Parser<Character>) (not generic)", "text(String) (not generic)",
                "textAndOffset(Parser<Character>) (not generic)"), parsed.skipped());
    }
}
