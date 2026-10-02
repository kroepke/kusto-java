// Ported from: src/Kusto.Language/Parser/Combinators/Parsers.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.graylog.kusto.language.utils.Ensure;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.ObjectPool;
import org.graylog.kusto.language.utils.dotnet.DotNetChars;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.EqualityComparer;
import org.graylog.kusto.language.utils.dotnet.Func3;
import org.graylog.kusto.language.utils.dotnet.Func4;
import org.graylog.kusto.language.utils.dotnet.Func5;
import org.graylog.kusto.language.utils.dotnet.Func6;
import org.graylog.kusto.language.utils.dotnet.Func7;
import org.graylog.kusto.language.utils.dotnet.Func8;
import org.graylog.kusto.language.utils.dotnet.Func9;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// Parser combinators, APIs to combine/construt parsers out of other parsers.
/// </summary>
// PORT: §3.10 D7 static class Parsers<TInput> -> final class of static generic methods <TInput>;
// the non-generic LexicalTokenParsers facade (TInput = LexicalToken) is generated from this member list
public final class Parsers
{
    private Parsers()
    {
    }

    /// <summary>
    /// A parser that consumes all the input items successfully consumed by the specified parsers.
    /// Fails if any parser fails.
    /// </summary>
    @SafeVarargs
    public static <TInput> Parser<TInput> and(Parser<TInput>... parsers)
    {
        return new SequenceParser<TInput>(Arrays.asList(parsers)); // PORT: §3.10 params array as IReadOnlyList
    }

    /// <summary>
    /// A parser that always consumes one input item, but produces nothing.
    /// </summary>
    // PORT: §3.9 static field of a generic class: one raw instance shared by all TInput (the predicate ignores its input); any() is the typed view
    @SuppressWarnings("rawtypes")
    public static final Parser Any =
        match((Object t) -> true).withTag("<any>");

    // PORT: §3.9 typed accessor for the shared raw static Any
    @SuppressWarnings("unchecked")
    public static <TInput> Parser<TInput> any()
    {
        return (Parser<TInput>) Any;
    }

    /// <summary>
    /// A parser that yields the result of the left-hand applied to the right-hand parser.
    /// </summary>
    public static <TInput, TLeft, TOutput> Parser2<TInput, TOutput> apply(Parser2<TInput, TLeft> leftParser, Function<LeftValue<TLeft>, RightParser<TInput, TOutput>> fnRightParser)
    {
        return new ApplyParser<TInput, TLeft, TOutput>(ApplyKind.One, leftParser, fnRightParser.apply(new LeftValue<TLeft>())); // PORT: §3.2 default(LeftValue<TLeft>)
    }

    /// <summary>
    /// A parser that yields the result of the left-hand parser or the result of applying that value to the right-hand parser.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> applyOptional(Parser2<TInput, TOutput> leftParser, Function<LeftValue<TOutput>, RightParser<TInput, TOutput>> fnRightParser)
    {
        return new ApplyParser<TInput, TOutput, TOutput>(ApplyKind.ZeroOrOne, leftParser, fnRightParser.apply(new LeftValue<TOutput>())); // PORT: §3.2 default(LeftValue<TOutput>)
    }

    /// <summary>
    /// A left associative parser that yields the result of the left-hand parser, or the result of applying that value (and subsequent results) to the right-hand parser zero or more times.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> applyZeroOrMore(Parser2<TInput, TOutput> leftParser, Function<LeftValue<TOutput>, RightParser<TInput, TOutput>> fnRightParser)
    {
        return new ApplyParser<TInput, TOutput, TOutput>(ApplyKind.ZeroOrMore, leftParser, fnRightParser.apply(new LeftValue<TOutput>())); // PORT: §3.2 default(LeftValue<TOutput>)
    }

    /// <summary>
    /// A parser that yields the result of the parser that consumed the most input items.
    /// </summary>
    @SafeVarargs
    public static <TInput> Parser<TInput> best(Parser<TInput>... parsers)
    {
        return new BestParser<TInput>(Arrays.asList(parsers)); // PORT: §3.10 params array as IReadOnlyList
    }

    /// <summary>
    /// A parser that yields the result of the parser that consumed the most input items.
    /// </summary>
    @SafeVarargs
    public static <TInput, TOutput> Parser2<TInput, TOutput> best(Parser2<TInput, TOutput>... parsers)
    {
        return new BestParser2<TInput, TOutput>(Arrays.asList(parsers)); // PORT: §3.10 params array as IReadOnlyList
    }

    /// <summary>
    /// A parser that yields the result of the parser that produced the best output item.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> best(Parser2<TInput, TOutput>[] parsers, BiPredicate<TOutput, TOutput> fnBetter) // PORT: §3.8 Func<TOutput, TOutput, bool>
    {
        return new BestParser2<TInput, TOutput>(Arrays.asList(parsers), fnBetter); // PORT: §3.10 array as IReadOnlyList
    }

    /// <summary>
    /// A parser that yields the result of the parser that consumed the most input items.
    /// </summary>
    @SafeVarargs
    public static <TInput, TOutput> RightParser<TInput, TOutput> best(RightParser<TInput, TOutput>... parsers)
    {
        return new RightParser<TInput, TOutput>(new BestParser2<TInput, TOutput>(Linq.select(Arrays.asList(parsers), p -> p.parser()))); // PORT: §3.6
    }

    /// <summary>
    /// A parser that converts all the successfully scanned input items into a single output item.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> convert(Parser<TInput> pattern, SourceProducer<TInput, TOutput> producer)
    {
        return new ConvertParser<TInput, TOutput>(pattern, producer);
    }

    /// <summary>
    /// A parser that converts all the successfully scanned input items into a single output item.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> convertList(Parser<TInput> pattern, Function<List<TInput>, TOutput> producer) // PORT: §2.5 Convert(Parser<I>, Func<IReadOnlyList<I>,O>)
    {
        return ConvertParser.<TInput, TOutput>ofList(pattern, producer); // PORT: §2.5 ConvertParser IReadOnlyList constructor
    }

    /// <summary>
    /// A parser that converts all the successfully scanned input characters into a single output item.
    /// </summary>
    public static <TOutput> Parser2<Character, TOutput> convertText(Parser<Character> pattern, Function<String, TOutput> producer) // PORT: §2.5 Convert(Parser<char>, Func<string,O>)
    {
        return Parsers.<Character, TOutput>convert(pattern, (Source<Character> source, int start, int length) ->
        {
            // check for TextSource to do it the easy way
            if (source instanceof TextSource ts)
            {
                return producer.apply(ts.peekText(start, length));
            }
            else
            {
                // otherwise, do it the hard way
                var builder = new StringBuilder();

                for (int i = 0; i < length; i++)
                {
                    builder.append(charOrDefault(source.peek(start + i))); // PORT: §3.10 default(char) is '\0', not null
                }

                return producer.apply(builder.toString());
            }
        });
    }

    /// <summary>
    /// A parser that converts all the successfully scanned input items into a single output item.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> convert(Parser<TInput> pattern, Function<TInput, TOutput> producer)
    {
        return new ConvertParser<TInput, TOutput>(pattern, producer);
    }

    /// <summary>
    /// A parser that converts all the successfully scanned input items into a single output item.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> convert(Parser<TInput> pattern, TOutput value)
    {
        return Parsers.<TInput, TOutput>convert(pattern, (TInput t) -> value);
    }

    /// <summary>
    /// A parser that produces the count of the number of successfully scanned and consumed input items.
    /// </summary>
    public static <TInput> Parser2<TInput, Integer> count(Parser<TInput> scanner)
    {
        return Parsers.<TInput, Integer>convert(scanner, (source, start, length) -> length);
    }

    /// <summary>
    /// A parser that succeeds (without consuming input) if the specified parser scan fails. Does not produce output.
    /// </summary>
    public static <TInput> Parser<TInput> fails(Parser<TInput> parser)
    {
        return new FailsParser<TInput>(parser);
    }

    /// <summary>
    /// A parser that yields the result of the first parser to succeed.
    /// </summary>
    @SafeVarargs
    public static <TInput, TOutput> Parser2<TInput, TOutput> first(Parser2<TInput, TOutput>... parsers)
    {
        return new FirstParser2<TInput, TOutput>(Arrays.asList(parsers)); // PORT: §3.10 params array as IReadOnlyList
    }

    /// <summary>
    /// A parser that yields the result of the first parser to succeed.
    /// </summary>
    @SafeVarargs
    public static <TInput> Parser<TInput> first(Parser<TInput>... parsers)
    {
        return new FirstParser<TInput>(Arrays.asList(parsers)); // PORT: §3.10 params array as IReadOnlyList
    }

    /// <summary>
    /// A parser that yields the result of the first parser to succeed.
    /// </summary>
    @SafeVarargs
    public static <TInput, TOutput> RightParser<TInput, TOutput> first(RightParser<TInput, TOutput>... parsers)
    {
        return new RightParser<TInput, TOutput>(new FirstParser2<TInput, TOutput>(Linq.select(Arrays.asList(parsers), p -> p.parser()))); // PORT: §3.6
    }

    /// <summary>
    /// A parser that forwards to a deferred parser.
    /// This parser is typically used to resolve cycles in grammar.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> forward(Supplier<Parser2<TInput, TOutput>> deferredParser) // PORT: §3.8 Func<Parser<TInput, TOutput>>
    {
        return new ForwardParser<TInput, TOutput>(deferredParser);
    }

    /// <summary>
    /// A parser that forwards to a deferred parser.
    /// This parser is typically used to resolve cycles in grammar.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> forward(Parser2<TInput, TOutput> parser)
    {
        return new ForwardParser<TInput, TOutput>(() -> parser);
    }

    /// <summary>
    /// A parser that produces the result of the specified parser only if a scan of the test parser succeeds.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> if_(Parser<TInput> test, Parser2<TInput, TOutput> parser) // PORT: §2.3 If is a Java keyword
    {
        return new IfParser2<TInput, TOutput>(test, parser);
    }

    /// <summary>
    /// A parser that produces the result of the specified parser only if a scan of the test parser succeeds.
    /// </summary>
    public static <TInput, TOutput> RightParser<TInput, TOutput> if_(Parser<TInput> test, RightParser<TInput, TOutput> parser) // PORT: §2.3 If is a Java keyword
    {
        return new RightParser<TInput, TOutput>(new IfParser2<TInput, TOutput>(test, parser.parser()));
    }

    /// <summary>
    /// A parser that produces the result of the specified parser only if a scan of the test parser succeeds.
    /// </summary>
    public static <TInput> Parser<TInput> if_(Parser<TInput> test, Parser<TInput> parser) // PORT: §2.3 If is a Java keyword
    {
        return new IfParser<TInput>(test, parser);
    }

    /// <summary>
    /// A parser that constrains the amount of input that can be accessed by another parser.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> limit(Parser<TInput> limiter, Parser2<TInput, TOutput> limited)
    {
        return new LimitParser<TInput, TOutput>(limiter, limited);
    }

    /// <summary>
    /// Creates a parser that parses a list of elements.
    /// </summary>
    /// <param name="elementParser">The parser for each element.</param>
    /// <param name="oneOrMore">If true, the generated parser expects at least one element to exist.</param>
    /// <param name="producer">A function that converts the series of elements into the produced value.</param>
    public static <TInput, TElement, TProducer> Parser2<TInput, TProducer> list(
        Parser2<TInput, TElement> elementParser,
        boolean oneOrMore,
        Function<List<TElement>, TProducer> producer)
    {
        return Parsers.<TInput, TElement, TProducer>list(
            elementParser,
            (BiFunction<Source<TInput>, Integer, TElement>) null, // PORT: §3.12 named argument fnMissingElement: null
            oneOrMore,
            producer);
    }

    /// <summary>
    /// Creates a parser that parses a list of elements.
    /// </summary>
    /// <param name="elementParser">The parser for each element.</param>
    /// <param name="fnMissingElement">An optional function that constructs a new element to be used when an expected element is missing.</param>
    /// <param name="oneOrMore">If true, the generated parser expects at least one element to exist.</param>
    /// <param name="producer">A function that converts the series of elements into the produced value.</param>
    public static <TInput, TElement, TProducer> Parser2<TInput, TProducer> list(
        Parser2<TInput, TElement> elementParser,
        BiFunction<Source<TInput>, Integer, TElement> fnMissingElement, // PORT: §3.8 Func<Source<TInput>, int, TElement>
        boolean oneOrMore,
        Function<List<TElement>, TProducer> producer)
    {
        if (oneOrMore)
        {
            if (fnMissingElement != null)
            {
                var requiredElement = required(elementParser, fnMissingElement);

                return produceList(
                    sequence(
                        requiredElement,
                        zeroOrMore(elementParser)),
                    producer);
            }
            else
            {
                return oneOrMore(elementParser, producer);
            }
        }
        else
        {
            return zeroOrMore(elementParser, producer);
        }
    }

    /// <summary>
    /// Creates a parser that parses a list of elements and separators.
    /// </summary>
    /// <param name="elementParser">The parser for each element.</param>
    /// <param name="separatorParser">The parser for each separator.</param>
    /// <param name="fnMissingElement">An optional function that constructs a new element to be used when the element is missing (between two separators).</param>
    /// <param name="fnMissingSeparator">An optional function that constructs a new separator instance to be used when the separator is missing (between two elements).</param>
    /// <param name="endOfList">An optional parser that quickly determines if there are no more elements.</param>
    /// <param name="oneOrMore">If true, the generated parser expects at least one element to exist.</param>
    /// <param name="allowTrailingSeparator">If true, it is legal for a final separator to occur without a following element.</param>
    /// <param name="producer">A function that converts the series of elements and separators into the produced value.</param>
    public static <TInput, TElement, TSeparator, TProducer> Parser2<TInput, TProducer> oList(
        Parser2<TInput, TElement> elementParser,
        Parser2<TInput, TSeparator> separatorParser,
        BiFunction<Source<TInput>, Integer, TElement> fnMissingElement,
        BiFunction<Source<TInput>, Integer, TSeparator> fnMissingSeparator,
        Parser<TInput> endOfList,
        boolean oneOrMore,
        boolean allowTrailingSeparator,
        Function<List<Object>, TProducer> producer)
    {
        return oList(
            elementParser,
            separatorParser,
            elementParser,
            fnMissingElement,
            fnMissingSeparator,
            fnMissingElement,
            endOfList,
            oneOrMore,
            allowTrailingSeparator,
            producer);
    }

    /// <summary>
    /// Creates a parser that parses a list of elements and separators.
    /// </summary>
    /// <param name="primaryElementParser">The parser for the primary element.</param>
    /// <param name="separatorParser">The parser for each separator.</param>
    /// <param name="secondaryElementParser">The parser for any element after the first separator.</param>
    /// <param name="fnMissingPrimaryElement">An optional function that constructs a new element to be used when the primary element is missing (between two separators).</param>
    /// <param name="fnMissingSecondaryElement">An optional function that constructs a new element to be used when the secondary element is missing (between two separators).</param>
    /// <param name="fnMissingSeparator">An optional function that constructs a new separator instance to be used when the separator is missing (between two elements).</param>
    /// <param name="endOfList">An optional parser that quickly determines if there are not more elements.</param>
    /// <param name="oneOrMore">If true, the generated parser expects at least one element to exist.</param>
    /// <param name="allowTrailingSeparator">If true, it is legal for a final separator to occur without a following element.</param>
    /// <param name="producer">A function that converts the series of elements and separators into the produced value.</param>
    public static <TInput, TElement, TSeparator, TProducer> Parser2<TInput, TProducer> oList(
        Parser2<TInput, TElement> primaryElementParser,
        Parser2<TInput, TSeparator> separatorParser,
        Parser2<TInput, TElement> secondaryElementParser,
        BiFunction<Source<TInput>, Integer, TElement> fnMissingPrimaryElement, // optional
        BiFunction<Source<TInput>, Integer, TSeparator> fnMissingSeparator, // optional
        BiFunction<Source<TInput>, Integer, TElement> fnMissingSecondaryElement, // optional
        Parser<TInput> endOfList, // optional
        boolean oneOrMore,
        boolean allowTrailingSeparator,
        Function<List<Object>, TProducer> producer)
    {
        Ensure.argumentNotNull(primaryElementParser, "primaryElementParser" /* nameof */);
        Ensure.argumentNotNull(separatorParser, "separatorParser" /* nameof */);

        if (secondaryElementParser == null)
            secondaryElementParser = primaryElementParser;

        var requiredPrimaryElementParser = fnMissingPrimaryElement != null ? required(primaryElementParser, fnMissingPrimaryElement) : primaryElementParser;
        var requiredSecondaryElementParser = fnMissingSecondaryElement != null ? required(secondaryElementParser, fnMissingSecondaryElement) : secondaryElementParser;
        var requiredSeparatorParser = fnMissingSeparator != null ? required(separatorParser, fnMissingSeparator) : separatorParser;
        Supplier<TProducer> emptyList = () -> producer.apply(Arrays.asList(new Object[] { })); // PORT: §3.8 Func<TProducer>; §3.17 new object[] { } as IReadOnlyList

        if (oneOrMore)
        {
            if (allowTrailingSeparator)
            {
                var secondaryParser = sequence(separatorParser, secondaryElementParser);

                if (fnMissingSeparator != null && endOfList != null)
                {
                    secondaryParser = first(
                        secondaryParser,
                        // check for missing secondardy element between two separators
                        if_(not(endOfList), sequence(requiredSeparatorParser, secondaryElementParser)).hide());
                }

                return produce(
                    sequence(
                        requiredPrimaryElementParser,
                        zeroOrMore(secondaryParser),
                        optional(separatorParser)),
                    producer);
            }
            else
            {
                var secondaryParser = sequence(separatorParser, requiredSecondaryElementParser);

                if (fnMissingSeparator != null && endOfList != null)
                {
                    secondaryParser = first(
                        secondaryParser,
                        // check for missing secondary element between two separators
                        if_(not(endOfList), sequence(requiredSeparatorParser, secondaryElementParser)).hide());
                }

                return produce(
                    sequence(
                        requiredPrimaryElementParser,
                        zeroOrMore(secondaryParser)),
                    producer);
            }
        }
        else
        {
            if (allowTrailingSeparator)
            {
                var secondaryParser = sequence(separatorParser, secondaryElementParser);

                if (fnMissingSeparator != null && endOfList != null)
                {
                    secondaryParser = first(
                        secondaryParser,
                        // check for missing secondardy element between two separators
                        if_(not(endOfList), sequence(requiredSeparatorParser, secondaryElementParser)).hide());
                }

                return optional(
                    produce(
                        sequence(
                            first(
                                if_(separatorParser, requiredPrimaryElementParser).hide(), // check for missing primary element
                                primaryElementParser),
                            zeroOrMore(secondaryParser),
                            optional(separatorParser)),
                        producer),
                    emptyList);
            }
            else
            {
                var secondaryParser = sequence(separatorParser, requiredSecondaryElementParser);

                if (fnMissingSeparator != null && endOfList != null)
                {
                    secondaryParser = first(
                        secondaryParser,
                        // check for missing secondardy element between two separators
                        if_(not(endOfList), sequence(requiredSeparatorParser, secondaryElementParser)).hide());
                }

                return optional(
                    produce(
                        sequence(
                            first(
                                if_(separatorParser, requiredPrimaryElementParser).hide(), // check for missing primary element
                                primaryElementParser),
                            zeroOrMore(secondaryParser)),
                        producer),
                    emptyList);
            }
        }
    }

    /// <summary>
    /// Creates a parser that parses a list of elements and separators.
    /// </summary>
    /// <param name="elementParser">The parser for the primary element.</param>
    /// <param name="separatorParser">The parser for each separator.</param>
    /// <param name="secondaryElementParser">The parser for any elements after the first separator.</param>
    /// <param name="fnMissingElement">An optional function that constructs a new element to be used when the element is missing (between two separators).</param>
    /// <param name="fnMissingSeparator">An optional function that constructs a new separator instance to be used when the separator is missing (between two elements).</param>
    /// <param name="fnMissingSecondaryElement">An optional function that constructs a new element to be used when a second element is missing (between two separators).</param>
    /// <param name="endOfList">An optional parser that quickly determines if there are not more elements.</param>
    /// <param name="oneOrMore">If true, the generated parser expects at least one element to exist.</param>
    /// <param name="allowTrailingSeparator">If true, it is legal for a final separator to occur without a following element.</param>
    /// <param name="producer">A function that converts the series of elements and separators into the produced value.</param>
    public static <TInput, TElement, TSeparator, TProducer> Parser2<TInput, TProducer> list(
        Parser2<TInput, TElement> elementParser,
        Parser2<TInput, TSeparator> separatorParser,
        Parser2<TInput, TElement> secondaryElementParser,
        BiFunction<Source<TInput>, Integer, TElement> fnMissingElement, // optional
        BiFunction<Source<TInput>, Integer, TSeparator> fnMissingSeparator, // optional
        BiFunction<Source<TInput>, Integer, TElement> fnMissingSecondaryElement, // optional
        Parser<TInput> endOfList, // optional
        boolean oneOrMore,
        boolean allowTrailingSeparator,
        Function<List<ElementAndSeparator<TElement, TSeparator>>, TProducer> producer)
    {
        return oList(
            elementParser,
            separatorParser,
            secondaryElementParser,
            fnMissingElement,
            fnMissingSeparator,
            fnMissingSecondaryElement,
            endOfList,
            oneOrMore,
            allowTrailingSeparator,
            list -> ElementAndSeparatorProducer.<TElement, TSeparator, TProducer>produce(list, producer)
            );
    }

    /// <summary>
    /// Creates a parser that parses a list of elements and separators.
    /// </summary>
    /// <param name="elementParser">The parser for the primary element.</param>
    /// <param name="separatorParser">The parser for the separator.</param>
    /// <param name="fnMissingElement">An optional function that constructs a new element to be used when the element is missing (between two separators).</param>
    /// <param name="fnMissingSeparator">An optional function that constructs a new separator instance to be used when the separator is missing (between two elements).</param>
    /// <param name="endOfList">An optional parser that quickly determines if there are not more elements.</param>
    /// <param name="oneOrMore">If true, the generated parser expects at least one element to exist.</param>
    /// <param name="allowTrailingSeparator">If true, it is legal for a final separator to occur without a following element.</param>
    /// <param name="producer">A function that converts the series of elements and separators into the produced value.</param>
    public static <TInput, TElement, TSeparator, TProducer> Parser2<TInput, TProducer> list(
        Parser2<TInput, TElement> elementParser,
        Parser2<TInput, TSeparator> separatorParser,
        BiFunction<Source<TInput>, Integer, TElement> fnMissingElement, // optional
        BiFunction<Source<TInput>, Integer, TSeparator> fnMissingSeparator, // optional
        Parser<TInput> endOfList, // optional
        boolean oneOrMore,
        boolean allowTrailingSeparator,
        Function<List<ElementAndSeparator<TElement, TSeparator>>, TProducer> producer)
    {
        return list(
            elementParser,
            separatorParser,
            elementParser,
            fnMissingElement,
            fnMissingSeparator,
            fnMissingElement,
            endOfList,
            oneOrMore,
            allowTrailingSeparator,
            producer);
    }

    /// <summary>
    /// Creates a parser that parses a list of elements and separators.
    /// </summary>
    /// <param name="elementParser">The parser for the primary element.</param>
    /// <param name="separatorParser">The parser for the separator.</param>
    /// <param name="secondaryElementParser">The parser for any elements after the first separator.</param>
    /// <param name="oneOrMore">If true, the generated parser expects at least one element to exist.</param>
    /// <param name="producer">A function that converts the series of elements and separators into the produced value.</param>
    public static <TInput, TElement, TSeparator, TProducer> Parser2<TInput, TProducer> list(
        Parser2<TInput, TElement> elementParser,
        Parser2<TInput, TSeparator> separatorParser,
        Parser2<TInput, TElement> secondaryElementParser,
        boolean oneOrMore,
        Function<List<ElementAndSeparator<TElement, TSeparator>>, TProducer> producer)
    {
        return list(
            elementParser,
            separatorParser,
            secondaryElementParser,
            (BiFunction<Source<TInput>, Integer, TElement>) null, // PORT: §3.12 named argument fnMissingElement: null
            (BiFunction<Source<TInput>, Integer, TSeparator>) null, // PORT: §3.12 named argument fnMissingSeparator: null
            (BiFunction<Source<TInput>, Integer, TElement>) null, // PORT: §3.12 named argument fnMissingSecondaryElement: null
            (Parser<TInput>) null, // PORT: §3.12 named argument endOfList: null
            oneOrMore,
            false, // allowTrailingSeparator
            producer);
    }

    /// <summary>
    /// Creates a parser that parses a list of elements and separators.
    /// </summary>
    /// <param name="elementParser">The parser for the primary element.</param>
    /// <param name="separatorParser">The parser for the separator.</param>
    /// <param name="oneOrMore">If true, the generated parser expects at least one element to exist.</param>
    /// <param name="producer">A function that converts the series of elements and separators into the produced value.</param>
    public static <TInput, TElement, TSeparator, TProducer> Parser2<TInput, TProducer> list(
        Parser2<TInput, TElement> elementParser,
        Parser2<TInput, TSeparator> separatorParser,
        boolean oneOrMore,
        Function<List<ElementAndSeparator<TElement, TSeparator>>, TProducer> producer)
    {
        return list(
            elementParser,
            separatorParser,
            elementParser,
            (BiFunction<Source<TInput>, Integer, TElement>) null, // PORT: §3.12 named argument fnMissingElement: null
            (BiFunction<Source<TInput>, Integer, TSeparator>) null, // PORT: §3.12 named argument fnMissingSeparator: null
            (BiFunction<Source<TInput>, Integer, TElement>) null, // PORT: §3.12 named argument fnMissingSecondaryElement: null
            (Parser<TInput>) null, // PORT: §3.12 named argument endOfList: null
            oneOrMore,
            false, // allowTrailingSeparator
            producer);
    }

    // PORT: §3.10 nested class of a generic class takes its own type parameters; static pool shared by all instantiations (§3.9)
    private static final class ElementAndSeparatorProducer
    {
        private static final ObjectPool<List<ElementAndSeparator<Object, Object>>> listPool =
            new ObjectPool<List<ElementAndSeparator<Object, Object>>>(
                () -> new ArrayList<ElementAndSeparator<Object, Object>>(), list -> list.clear());

        @SuppressWarnings({"unchecked", "rawtypes"})
        public static <TElement, TSeparator, TProducer> TProducer produce(List<Object> output, Function<List<ElementAndSeparator<TElement, TSeparator>>, TProducer> producer)
        {
            var list = (List<ElementAndSeparator<TElement, TSeparator>>) (List) listPool.allocateFromPool(); // PORT: §3.9
            try
            {
                for (int i = 0; i < output.size(); i += 2)
                {
                    var element = (TElement) output.get(i); // PORT: §3.10 unchecked cast
                    var separator = (i < output.size() - 1) ? (TSeparator) output.get(i + 1) : (TSeparator) null; // PORT: §3.10 default(TSeparator)
                    list.add(new ElementAndSeparator<TElement, TSeparator>(element, separator));
                }

                return producer.apply(list);
            }
            finally
            {
                listPool.returnToPool((List<ElementAndSeparator<Object, Object>>) (List) list); // PORT: §3.9
            }
        }
    }

    /// <summary>
    /// A parser that maps sequences of input values to output values.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> map(Iterable<Map.Entry<Iterable<TInput>, TOutput>> keyValuePairs) // PORT: §3.17 KeyValuePair -> Map.Entry
    {
        return new MapParser<TInput, TOutput>(Linq.<Map.Entry<Iterable<TInput>, TOutput>, Iterable<TInput>, Supplier<TOutput>>toDictionary(keyValuePairs, kvp -> kvp.getKey(), kvp -> (Supplier<TOutput>) (() -> kvp.getValue())).entrySet()); // PORT: §3.6
    }

    /// <summary>
    /// A parser that maps sequences of input values to output values.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> mapList(Iterable<Map.Entry<Iterable<TInput>, Supplier<TOutput>>> keyValuePairs) // PORT: §2.5 Map(IEnumerable<KeyValuePair<IEnumerable<TInput>, Func<TOutput>>>)
    {
        return new MapParser<TInput, TOutput>(keyValuePairs);
    }

    /// <summary>
    /// A parser that consumes one input item if it matches the predicate. Does not produce output.
    /// </summary>
    public static <TInput> Parser<TInput> match(Predicate<TInput> predicate) // PORT: §3.8 Func<TInput, bool>
    {
        return new MatchParser<TInput>(predicate);
    }

    /// <summary>
    /// A parser that consumes one or more input items.
    /// </summary>
    public static <TInput> Parser<TInput> match(SourceConsumer<TInput> consumer)
    {
        return new MatchParser<TInput>(consumer);
    }

    /// <summary>
    /// A parser that consumes one matching input item.
    /// </summary>
    public static <TInput> Parser<TInput> match(TInput item)
    {
        return match(item, EqualityComparer.<TInput>defaultComparer()); // PORT: §3.10 EqualityComparer<TInput>.Default
    }

    /// <summary>
    /// A parser that consumes one matching input item.
    /// </summary>
    public static <TInput> Parser<TInput> match(TInput item, EqualityComparer<TInput> comparer)
    {
        return Parsers.<TInput>match((Predicate<TInput>) i -> comparer.equals(i, item));
    }

    /// <summary>
    /// A parser that consumes one input item that matches one of the specified items
    /// </summary>
    public static <TInput> Parser<TInput> matchAny(List<TInput> items)
    {
        return matchAny(items, EqualityComparer.<TInput>defaultComparer()); // PORT: §3.10 EqualityComparer<TInput>.Default
    }

    /// <summary>
    /// A parser that consumes one input item that matches one of the specified items
    /// </summary>
    @SafeVarargs
    public static <TInput> Parser<TInput> matchAny(TInput... items)
    {
        return matchAny(Arrays.asList(items), EqualityComparer.<TInput>defaultComparer()); // PORT: §3.10 params array as IReadOnlyList; EqualityComparer<TInput>.Default
    }

    /// <summary>
    /// A parser that consumes one input item that matches one of the specified items
    /// </summary>
    public static <TInput> Parser<TInput> matchAny(List<TInput> items, EqualityComparer<TInput> comparer)
    {
        // PORT: §3.17 HashSet<TInput>(items, comparer) via EqualityComparer.Key (a null comparer means the default comparer)
        var hashset = new LinkedHashSet<EqualityComparer.Key<TInput>>();
        for (var item : items)
        {
            hashset.add(EqualityComparer.of(item, comparer != null ? comparer : EqualityComparer.<TInput>defaultComparer()));
        }
        return Parsers.<TInput>match((TInput item) -> items.contains(item)); // PORT-BUG: hashset (and comparer) unused; items.Contains uses default equality; mirrored
    }

    /// <summary>
    /// A parser that consumes one or more sequential matching input items.
    /// </summary>
    public static <TInput> Parser<TInput> matchSequence(List<TInput> items)
    {
        return matchSequence(items, EqualityComparer.<TInput>defaultComparer()); // PORT: §3.10 EqualityComparer<TInput>.Default
    }

    /// <summary>
    /// A parser that consumes one or more sequential matching input items.
    /// </summary>
    public static <TInput> Parser<TInput> matchSequence(List<TInput> items, EqualityComparer<TInput> comparer)
    {
        return new MatchParser<TInput>(
            (SourceConsumer<TInput>) (source, start) ->
            {
                for (int i = 0; i < items.size(); i++)
                {
                    if (source.isEnd(start + i))
                        return ~i;

                    if (!comparer.equals(items.get(i), source.peek(start + i)))
                        return ~i;
                }

                return items.size();
            });
    }

    /// <summary>
    /// A parser that consumes a matching character.
    /// </summary>
    public static Parser<Character> match(char ch, boolean ignoreCase)
    {
        if (ignoreCase)
        {
            var chUpper = DotNetChars.toUpperInvariant(ch); // PORT: §5.1 char.ToUpper under the invariant-culture policy
            var chLower = DotNetChars.toLowerInvariant(ch); // PORT: §5.1 char.ToLower under the invariant-culture policy
            return Parsers.<Character>match((Predicate<Character>) c -> c == ch || c == chUpper || c == chLower);
        }
        else
        {
            return Parsers.<Character>match((Predicate<Character>) c -> c == ch);
        }
    }

    public static Parser<Character> match(char ch) // PORT: §3.12 optional parameter ignoreCase = false
    {
        return match(ch, false);
    }

    /// <summary>
    /// A parser that consumes one or more sequential matching characters.
    /// </summary>
    public static Parser<Character> match(String text, boolean ignoreCase)
    {
        if (text.length() == 1)
        {
            return match(text.charAt(0), ignoreCase);
        }

        if (ignoreCase)
        {
            var lower = DotNetStrings.toLowerInvariant(text); // PORT: §5.1 per-char invariant mapping, length unchanged
            var upper = DotNetStrings.toUpperInvariant(text); // PORT: §5.1 per-char invariant mapping, length unchanged

            return Parsers.<Character>match((SourceConsumer<Character>) (source, start) ->
            {
                // check for quick string comparison
                if (source instanceof TextSource ts)
                {
                    return ts.matches(start, text, true) ? text.length() : -1; // ignoreCase: true
                }
                else
                {
                    // otherwise do it the hard way
                    for (int i = 0; i < text.length(); i++)
                    {
                        var ch = charOrDefault(source.peek(start + i)); // PORT: §3.10 default(char) is '\0', not null
                        if (ch != lower.charAt(i) && ch != upper.charAt(i))
                            return ~i;
                    }

                    return text.length();
                }
            });
        }
        else
        {
            return Parsers.<Character>match((SourceConsumer<Character>) (source, start) ->
            {
                // check for quick string comparison
                if (source instanceof TextSource ts)
                {
                    return ts.matches(start, text) ? text.length() : -1;
                }
                else
                {
                    // otherwise do it the hard way
                    for (int i = 0; i < text.length(); i++)
                    {
                        if (charOrDefault(source.peek(start + i)) != text.charAt(i)) // PORT: §3.10 default(char) is '\0', not null
                            return ~i;
                    }

                    return text.length();
                }
            });
        }
    }

    public static Parser<Character> match(String text) // PORT: §3.12 optional parameter ignoreCase = false
    {
        return match(text, false);
    }

    // PORT: §3.10 default(char) is '\0'; a Source<Character> past its end may yield null instead
    private static char charOrDefault(Character ch)
    {
        return ch != null ? ch : '\0';
    }

    /// <summary>
    /// A parser that producers a single output value from a single input value if it matches the predicate.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> match(Predicate<TInput> predicate, Function<TInput, TOutput> producer) // PORT: §3.8
    {
        return new MatchParser2<TInput, TOutput>(predicate, producer);
    }

    /// <summary>
    /// A parser that producers a single output value from one or more input values.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> match(SourceConsumer<TInput> consumer, SourceProducer<TInput, TOutput> producer)
    {
        return new MatchParser2<TInput, TOutput>(consumer, producer);
    }

    /// <summary>
    /// A parser that consumes one input item if the specified parser scan fails. Does not produce output.
    /// </summary>
    public static <TInput> Parser<TInput> not(Parser<TInput> parser)
    {
        return new NotParser<TInput>(parser);
    }

    /// <summary>
    /// A parser that combines one or more parsed values into a single value.
    /// </summary>
    public static <TInput, TParser, TProducer> Parser2<TInput, TProducer> oneOrMore(
        Parser2<TInput, TParser> parser,
        Function<List<TParser>, TProducer> producer)
    {
        return produceList(oneOrMore(parser), producer);
    }

    /// <summary>
    /// A parser that combines one or more parsed values into a single value.
    /// </summary>
    public static <TInput, TParser> Parser2<TInput, List<TParser>> oneOrMoreList(
        Parser2<TInput, TParser> parser)
    {
        return Parsers.<TInput, TParser, List<TParser>>oneOrMore(parser, list -> ListExtensions.toReadOnly(list)); // PORT: §3.5
    }

    /// <summary>
    /// A parser that parsers one or more values from the specified parser.
    /// </summary>
    public static <TInput> Parser<TInput> oneOrMore(Parser<TInput> parser)
    {
        return new OneOrMoreParser<TInput>(parser);
    }

    /// <summary>
    /// A parser that produces the specified parser's result or nothing if the specified parser fails.
    /// </summary>
    public static <TInput> Parser<TInput> optional(Parser<TInput> parser)
    {
        return new ZeroOrMoreParser<TInput>(parser, true); // zeroOrOne: true
    }

    /// <summary>
    /// A parser that produces the specified parser's result or the default value if the specified parser fails.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> optional(Parser2<TInput, TOutput> parser)
    {
        return new OptionalParser<TInput, TOutput>(parser, () -> null); // PORT: §3.10 default(TOutput)
    }

    /// <summary>
    /// A parser that produces the specified parser's result or the value from the producer function if the specified parser fails.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> optional(Parser2<TInput, TOutput> parser, Supplier<TOutput> producer) // PORT: §3.8 Func<TOutput>
    {
        return new OptionalParser<TInput, TOutput>(parser, producer);
    }

    /// <summary>
    /// A parser that succeeds if any of the specified parsers succeed, consuming the greatest number of input items consumed.
    /// This parser is a synonym for <see cref="Best(Parser{TInput}[])"/>.
    /// </summary>
    @SafeVarargs
    public static <TInput> Parser<TInput> or(Parser<TInput>... parsers)
    {
        return new BestParser<TInput>(Arrays.asList(parsers)); // PORT: §3.10 params array as IReadOnlyList
    }

    /// <summary>
    /// A parser that combines one or more values produced by a single parser into a new value.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> produce(Parser<TInput> parser, Function<List<Object>, TOutput> producer)
    {
        return new ProduceParser<TInput, TOutput>(parser, produce(producer));
    }

    /// <summary>
    /// A parser that combines one or more values produced by a single parser into a new value.
    /// </summary>
    public static <TInput, TElement, TOutput> Parser2<TInput, TOutput> produceList(Parser<TInput> parser, Function<List<TElement>, TOutput> producer) // PORT: §2.5 Produce<TElement, TOutput>
    {
        return new ProduceParser<TInput, TOutput>(parser, produce(producer));
    }

    private static <TElement, TOutput> BiFunction<List<Object>, Integer, TOutput> produce(Function<List<TElement>, TOutput> producer) // PORT: §3.8 Func<List<object>, int, TOutput>
    {
        return (list, start) -> ElementProducer.<TElement, TOutput>produce(list, start, producer);
    }

    // PORT: §3.10 nested class of a generic class takes its own type parameters; static pool shared by all instantiations (§3.9)
    private static final class ElementProducer
    {
        private static final ObjectPool<List<Object>> listPool =
            new ObjectPool<List<Object>>(() -> new ArrayList<Object>(), list -> list.clear());

        @SuppressWarnings("unchecked")
        public static <TElement, TProducer> TProducer produce(List<Object> output, int outputStart, Function<List<TElement>, TProducer> producer)
        {
            var list = (List<TElement>) (List<?>) listPool.allocateFromPool(); // PORT: §3.9
            try
            {
                for (int i = outputStart; i < output.size(); i++)
                {
                    list.add((TElement) output.get(i)); // PORT: §3.10 unchecked cast
                }

                return producer.apply(list);
            }
            finally
            {
                listPool.returnToPool((List<Object>) (List<?>) list); // PORT: §3.9
            }
        }
    }

    /// <summary>
    /// A parser that produces the specified parsers result or the value of the producer.
    /// This parser behaves like optional, except that a producer must be specified and has UI behavior differences.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> required(Parser2<TInput, TOutput> parser, Supplier<TOutput> producer) // PORT: §3.8 Func<TOutput>
    {
        return new RequiredParser<TInput, TOutput>(parser, (source, start) -> producer.get());
    }

    /// <summary>
    /// A parser that produces the specified parsers result or the value of the producer.
    /// This parser behaves like optional, except that a producer must be specified and has UI behavior differences.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> required(Parser2<TInput, TOutput> parser, BiFunction<Source<TInput>, Integer, TOutput> producer) // PORT: §3.8 Func<Source<TInput>, int, TOutput>
    {
        return new RequiredParser<TInput, TOutput>(parser, producer);
    }

    /// <summary>
    /// A parser that converts the output of one parser into a new value.
    /// If the specified parser fails, then this parser fails too.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TInput, TParser1, TOutput> Parser2<TInput, TOutput> rule(
        Parser2<TInput, TParser1> parser1,
        Function<TParser1, TOutput> producer) // PORT: §3.8
    {
        return new RuleParser<TInput, TOutput>(
            Arrays.<Parser<TInput>>asList(parser1), // PORT: §3.10 new Parser<TInput>[] { ... }

            (list, start) ->
            {
                Ensure.areEqual(list.size() - start, 1);
                return producer.apply((TParser1) list.get(start)); // PORT: §3.10 unchecked cast
            },

            (source, start) ->
            {
                var result1 = parser1.parse(source, start);

                if (result1.length() < 0)
                    return new ParseResult<TOutput>(result1.length(), null); // PORT: §3.10 default(TOutput)

                var produced = producer.apply(result1.value());
                return new ParseResult<TOutput>(result1.length(), produced);
            });
    }

    /// <summary>
    /// A parser that combines the output of multiple parsers into a new value.
    /// If either parser fails, then this parser fails.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TInput, TParser1, TParser2, TOutput> Parser2<TInput, TOutput> rule(
        Parser2<TInput, TParser1> parser1,
        Parser2<TInput, TParser2> parser2,
        BiFunction<TParser1, TParser2, TOutput> producer) // PORT: §3.8
    {
        return new RuleParser<TInput, TOutput>(
            Arrays.<Parser<TInput>>asList(parser1, parser2), // PORT: §3.10 new Parser<TInput>[] { ... }

            (list, start) ->
            {
                Ensure.areEqual(list.size() - start, 2);
                return producer.apply(
                    (TParser1) list.get(start),
                    (TParser2) list.get(start + 1)); // PORT: §3.10 unchecked casts
            },

            (source, start) ->
            {
                var result1 = parser1.parse(source, start);
                if (result1.length() < 0)
                    return new ParseResult<TOutput>(result1.length(), null); // PORT: §3.10 default(TOutput)
                var len = result1.length();

                var result2 = parser2.parse(source, start + len);
                if (result2.length() < 0)
                    return new ParseResult<TOutput>(-len + result2.length(), null); // PORT: §3.10 default(TOutput)
                len += result2.length();

                var produced = producer.apply(result1.value(), result2.value());
                return new ParseResult<TOutput>(len, produced);
            });
    }

    /// <summary>
    /// A parser that combines the output of multiple parsers into a new value.
    /// If any parser in the sequence fails, then this parser fails.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TInput, TParser1, TParser2, TParser3, TOutput> Parser2<TInput, TOutput> rule(
        Parser2<TInput, TParser1> parser1,
        Parser2<TInput, TParser2> parser2,
        Parser2<TInput, TParser3> parser3,
        Func3<TParser1, TParser2, TParser3, TOutput> producer) // PORT: §3.8 Func<TParser1, TParser2, TParser3, TOutput> -> Func3
    {
        return new RuleParser<TInput, TOutput>(
            Arrays.<Parser<TInput>>asList(parser1, parser2, parser3), // PORT: §3.10 new Parser<TInput>[] { ... }

            (list, start) ->
            {
                Ensure.areEqual(list.size() - start, 3);
                return producer.apply(
                    (TParser1) list.get(start),
                    (TParser2) list.get(start + 1),
                    (TParser3) list.get(start + 2)); // PORT: §3.10 unchecked casts
            },

            (source, start) ->
            {
                var result1 = parser1.parse(source, start);
                if (result1.length() < 0)
                    return new ParseResult<TOutput>(result1.length(), null); // PORT: §3.10 default(TOutput)
                var len = result1.length();

                var result2 = parser2.parse(source, start + len);
                if (result2.length() < 0)
                    return new ParseResult<TOutput>(-len + result2.length(), null); // PORT: §3.10 default(TOutput)
                len += result2.length();

                var result3 = parser3.parse(source, start + len);
                if (result3.length() < 0)
                    return new ParseResult<TOutput>(-len + result3.length(), null); // PORT: §3.10 default(TOutput)
                len += result3.length();

                var produced = producer.apply(result1.value(), result2.value(), result3.value());
                return new ParseResult<TOutput>(len, produced);
            });
    }

    /// <summary>
    /// A parser that combines the output of multiple parsers into a new value.
    /// If any parser in the sequence fails, then this parser fails.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TInput, TParser1, TParser2, TParser3, TParser4, TOutput> Parser2<TInput, TOutput> rule(
        Parser2<TInput, TParser1> parser1,
        Parser2<TInput, TParser2> parser2,
        Parser2<TInput, TParser3> parser3,
        Parser2<TInput, TParser4> parser4,
        Func4<TParser1, TParser2, TParser3, TParser4, TOutput> producer) // PORT: §3.8 Func<TParser1, TParser2, TParser3, TParser4, TOutput> -> Func4
    {
        return new RuleParser<TInput, TOutput>(
            Arrays.<Parser<TInput>>asList(parser1, parser2, parser3, parser4), // PORT: §3.10 new Parser<TInput>[] { ... }

            (list, start) ->
            {
                Ensure.areEqual(list.size() - start, 4);
                return producer.apply(
                    (TParser1) list.get(start),
                    (TParser2) list.get(start + 1),
                    (TParser3) list.get(start + 2),
                    (TParser4) list.get(start + 3)); // PORT: §3.10 unchecked casts
            },

            (source, start) ->
            {
                var result1 = parser1.parse(source, start);
                if (result1.length() < 0)
                    return new ParseResult<TOutput>(result1.length(), null); // PORT: §3.10 default(TOutput)
                var len = result1.length();

                var result2 = parser2.parse(source, start + len);
                if (result2.length() < 0)
                    return new ParseResult<TOutput>(-len + result2.length(), null); // PORT: §3.10 default(TOutput)
                len += result2.length();

                var result3 = parser3.parse(source, start + len);
                if (result3.length() < 0)
                    return new ParseResult<TOutput>(-len + result3.length(), null); // PORT: §3.10 default(TOutput)
                len += result3.length();

                var result4 = parser4.parse(source, start + len);
                if (result4.length() < 0)
                    return new ParseResult<TOutput>(-len + result4.length(), null); // PORT: §3.10 default(TOutput)
                len += result4.length();

                var produced = producer.apply(result1.value(), result2.value(), result3.value(), result4.value());
                return new ParseResult<TOutput>(len, produced);
            });
    }

    /// <summary>
    /// A parser that combines the output of multiple parsers into a new value.
    /// If any parser in the sequence fails, then this parser fails.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TInput, TParser1, TParser2, TParser3, TParser4, TParser5, TOutput> Parser2<TInput, TOutput> rule(
        Parser2<TInput, TParser1> parser1,
        Parser2<TInput, TParser2> parser2,
        Parser2<TInput, TParser3> parser3,
        Parser2<TInput, TParser4> parser4,
        Parser2<TInput, TParser5> parser5,
        Func5<TParser1, TParser2, TParser3, TParser4, TParser5, TOutput> producer) // PORT: §3.8 Func<TParser1, TParser2, TParser3, TParser4, TParser5, TOutput> -> Func5
    {
        return new RuleParser<TInput, TOutput>(
            Arrays.<Parser<TInput>>asList(parser1, parser2, parser3, parser4, parser5), // PORT: §3.10 new Parser<TInput>[] { ... }

            (list, start) ->
            {
                Ensure.areEqual(list.size() - start, 5);
                return producer.apply(
                    (TParser1) list.get(start),
                    (TParser2) list.get(start + 1),
                    (TParser3) list.get(start + 2),
                    (TParser4) list.get(start + 3),
                    (TParser5) list.get(start + 4)); // PORT: §3.10 unchecked casts
            },

            (source, start) ->
            {
                var result1 = parser1.parse(source, start);
                if (result1.length() < 0)
                    return new ParseResult<TOutput>(result1.length(), null); // PORT: §3.10 default(TOutput)
                var len = result1.length();

                var result2 = parser2.parse(source, start + len);
                if (result2.length() < 0)
                    return new ParseResult<TOutput>(-len + result2.length(), null); // PORT: §3.10 default(TOutput)
                len += result2.length();

                var result3 = parser3.parse(source, start + len);
                if (result3.length() < 0)
                    return new ParseResult<TOutput>(-len + result3.length(), null); // PORT: §3.10 default(TOutput)
                len += result3.length();

                var result4 = parser4.parse(source, start + len);
                if (result4.length() < 0)
                    return new ParseResult<TOutput>(-len + result4.length(), null); // PORT: §3.10 default(TOutput)
                len += result4.length();

                var result5 = parser5.parse(source, start + len);
                if (result5.length() < 0)
                    return new ParseResult<TOutput>(-len + result5.length(), null); // PORT: §3.10 default(TOutput)
                len += result5.length();

                var produced = producer.apply(result1.value(), result2.value(), result3.value(), result4.value(), result5.value());
                return new ParseResult<TOutput>(len, produced);
            });
    }

    /// <summary>
    /// A parser that combines the output of multiple parsers into a new value.
    /// If any parser in the sequence fails, then this parser fails.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TInput, TParser1, TParser2, TParser3, TParser4, TParser5, TParser6, TOutput> Parser2<TInput, TOutput> rule(
        Parser2<TInput, TParser1> parser1,
        Parser2<TInput, TParser2> parser2,
        Parser2<TInput, TParser3> parser3,
        Parser2<TInput, TParser4> parser4,
        Parser2<TInput, TParser5> parser5,
        Parser2<TInput, TParser6> parser6,
        Func6<TParser1, TParser2, TParser3, TParser4, TParser5, TParser6, TOutput> producer) // PORT: §3.8 Func<TParser1, TParser2, TParser3, TParser4, TParser5, TParser6, TOutput> -> Func6
    {
        return new RuleParser<TInput, TOutput>(
            Arrays.<Parser<TInput>>asList(parser1, parser2, parser3, parser4, parser5, parser6), // PORT: §3.10 new Parser<TInput>[] { ... }

            (list, start) ->
            {
                Ensure.areEqual(list.size() - start, 6);
                return producer.apply(
                    (TParser1) list.get(start),
                    (TParser2) list.get(start + 1),
                    (TParser3) list.get(start + 2),
                    (TParser4) list.get(start + 3),
                    (TParser5) list.get(start + 4),
                    (TParser6) list.get(start + 5)); // PORT: §3.10 unchecked casts
            },

            (source, start) ->
            {
                var result1 = parser1.parse(source, start);
                if (result1.length() < 0)
                    return new ParseResult<TOutput>(result1.length(), null); // PORT: §3.10 default(TOutput)
                var len = result1.length();

                var result2 = parser2.parse(source, start + len);
                if (result2.length() < 0)
                    return new ParseResult<TOutput>(-len + result2.length(), null); // PORT: §3.10 default(TOutput)
                len += result2.length();

                var result3 = parser3.parse(source, start + len);
                if (result3.length() < 0)
                    return new ParseResult<TOutput>(-len + result3.length(), null); // PORT: §3.10 default(TOutput)
                len += result3.length();

                var result4 = parser4.parse(source, start + len);
                if (result4.length() < 0)
                    return new ParseResult<TOutput>(-len + result4.length(), null); // PORT: §3.10 default(TOutput)
                len += result4.length();

                var result5 = parser5.parse(source, start + len);
                if (result5.length() < 0)
                    return new ParseResult<TOutput>(-len + result5.length(), null); // PORT: §3.10 default(TOutput)
                len += result5.length();

                var result6 = parser6.parse(source, start + len);
                if (result6.length() < 0)
                    return new ParseResult<TOutput>(-len + result6.length(), null); // PORT: §3.10 default(TOutput)
                len += result6.length();

                var produced = producer.apply(result1.value(), result2.value(), result3.value(), result4.value(), result5.value(), result6.value());
                return new ParseResult<TOutput>(len, produced);
            });
    }

    /// <summary>
    /// A parser that combines the output of multiple parsers into a new value.
    /// If any parser in the sequence fails, then this parser fails.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TInput, TParser1, TParser2, TParser3, TParser4, TParser5, TParser6, TParser7, TOutput> Parser2<TInput, TOutput> rule(
        Parser2<TInput, TParser1> parser1,
        Parser2<TInput, TParser2> parser2,
        Parser2<TInput, TParser3> parser3,
        Parser2<TInput, TParser4> parser4,
        Parser2<TInput, TParser5> parser5,
        Parser2<TInput, TParser6> parser6,
        Parser2<TInput, TParser7> parser7,
        Func7<TParser1, TParser2, TParser3, TParser4, TParser5, TParser6, TParser7, TOutput> producer) // PORT: §3.8 Func<TParser1, TParser2, TParser3, TParser4, TParser5, TParser6, TParser7, TOutput> -> Func7
    {
        return new RuleParser<TInput, TOutput>(
            Arrays.<Parser<TInput>>asList(parser1, parser2, parser3, parser4, parser5, parser6, parser7), // PORT: §3.10 new Parser<TInput>[] { ... }

            (list, start) ->
            {
                Ensure.areEqual(list.size() - start, 7);
                return producer.apply(
                    (TParser1) list.get(start),
                    (TParser2) list.get(start + 1),
                    (TParser3) list.get(start + 2),
                    (TParser4) list.get(start + 3),
                    (TParser5) list.get(start + 4),
                    (TParser6) list.get(start + 5),
                    (TParser7) list.get(start + 6)); // PORT: §3.10 unchecked casts
            },

            (source, start) ->
            {
                var result1 = parser1.parse(source, start);
                if (result1.length() < 0)
                    return new ParseResult<TOutput>(result1.length(), null); // PORT: §3.10 default(TOutput)
                var len = result1.length();

                var result2 = parser2.parse(source, start + len);
                if (result2.length() < 0)
                    return new ParseResult<TOutput>(-len + result2.length(), null); // PORT: §3.10 default(TOutput)
                len += result2.length();

                var result3 = parser3.parse(source, start + len);
                if (result3.length() < 0)
                    return new ParseResult<TOutput>(-len + result3.length(), null); // PORT: §3.10 default(TOutput)
                len += result3.length();

                var result4 = parser4.parse(source, start + len);
                if (result4.length() < 0)
                    return new ParseResult<TOutput>(-len + result4.length(), null); // PORT: §3.10 default(TOutput)
                len += result4.length();

                var result5 = parser5.parse(source, start + len);
                if (result5.length() < 0)
                    return new ParseResult<TOutput>(-len + result5.length(), null); // PORT: §3.10 default(TOutput)
                len += result5.length();

                var result6 = parser6.parse(source, start + len);
                if (result6.length() < 0)
                    return new ParseResult<TOutput>(-len + result6.length(), null); // PORT: §3.10 default(TOutput)
                len += result6.length();

                var result7 = parser7.parse(source, start + len);
                if (result7.length() < 0)
                    return new ParseResult<TOutput>(-len + result7.length(), null); // PORT: §3.10 default(TOutput)
                len += result7.length();

                var produced = producer.apply(result1.value(), result2.value(), result3.value(), result4.value(), result5.value(), result6.value(), result7.value());
                return new ParseResult<TOutput>(len, produced);
            });
    }

    /// <summary>
    /// A parser that combines the output of multiple parsers into a new value.
    /// If any parser in the sequence fails, then this parser fails.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TInput, TParser1, TParser2, TParser3, TParser4, TParser5, TParser6, TParser7, TParser8, TOutput> Parser2<TInput, TOutput> rule(
        Parser2<TInput, TParser1> parser1,
        Parser2<TInput, TParser2> parser2,
        Parser2<TInput, TParser3> parser3,
        Parser2<TInput, TParser4> parser4,
        Parser2<TInput, TParser5> parser5,
        Parser2<TInput, TParser6> parser6,
        Parser2<TInput, TParser7> parser7,
        Parser2<TInput, TParser8> parser8,
        Func8<TParser1, TParser2, TParser3, TParser4, TParser5, TParser6, TParser7, TParser8, TOutput> producer) // PORT: §3.8 Func<TParser1, TParser2, TParser3, TParser4, TParser5, TParser6, TParser7, TParser8, TOutput> -> Func8
    {
        return new RuleParser<TInput, TOutput>(
            Arrays.<Parser<TInput>>asList(parser1, parser2, parser3, parser4, parser5, parser6, parser7, parser8), // PORT: §3.10 new Parser<TInput>[] { ... }

            (list, start) ->
            {
                Ensure.areEqual(list.size() - start, 8);
                return producer.apply(
                    (TParser1) list.get(start),
                    (TParser2) list.get(start + 1),
                    (TParser3) list.get(start + 2),
                    (TParser4) list.get(start + 3),
                    (TParser5) list.get(start + 4),
                    (TParser6) list.get(start + 5),
                    (TParser7) list.get(start + 6),
                    (TParser8) list.get(start + 7)); // PORT: §3.10 unchecked casts
            },

            (source, start) ->
            {
                var result1 = parser1.parse(source, start);
                if (result1.length() < 0)
                    return new ParseResult<TOutput>(result1.length(), null); // PORT: §3.10 default(TOutput)
                var len = result1.length();

                var result2 = parser2.parse(source, start + len);
                if (result2.length() < 0)
                    return new ParseResult<TOutput>(-len + result2.length(), null); // PORT: §3.10 default(TOutput)
                len += result2.length();

                var result3 = parser3.parse(source, start + len);
                if (result3.length() < 0)
                    return new ParseResult<TOutput>(-len + result3.length(), null); // PORT: §3.10 default(TOutput)
                len += result3.length();

                var result4 = parser4.parse(source, start + len);
                if (result4.length() < 0)
                    return new ParseResult<TOutput>(-len + result4.length(), null); // PORT: §3.10 default(TOutput)
                len += result4.length();

                var result5 = parser5.parse(source, start + len);
                if (result5.length() < 0)
                    return new ParseResult<TOutput>(-len + result5.length(), null); // PORT: §3.10 default(TOutput)
                len += result5.length();

                var result6 = parser6.parse(source, start + len);
                if (result6.length() < 0)
                    return new ParseResult<TOutput>(-len + result6.length(), null); // PORT: §3.10 default(TOutput)
                len += result6.length();

                var result7 = parser7.parse(source, start + len);
                if (result7.length() < 0)
                    return new ParseResult<TOutput>(-len + result7.length(), null); // PORT: §3.10 default(TOutput)
                len += result7.length();

                var result8 = parser8.parse(source, start + len);
                if (result8.length() < 0)
                    return new ParseResult<TOutput>(-len + result8.length(), null); // PORT: §3.10 default(TOutput)
                len += result8.length();

                var produced = producer.apply(result1.value(), result2.value(), result3.value(), result4.value(), result5.value(), result6.value(), result7.value(), result8.value());
                return new ParseResult<TOutput>(len, produced);
            });
    }

    /// <summary>
    /// A parser that combines the output of multiple parsers into a new value.
    /// If any parser in the sequence fails, then this parser fails.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TInput, TParser1, TParser2, TParser3, TParser4, TParser5, TParser6, TParser7, TParser8, TParser9, TOutput> Parser2<TInput, TOutput> rule(
        Parser2<TInput, TParser1> parser1,
        Parser2<TInput, TParser2> parser2,
        Parser2<TInput, TParser3> parser3,
        Parser2<TInput, TParser4> parser4,
        Parser2<TInput, TParser5> parser5,
        Parser2<TInput, TParser6> parser6,
        Parser2<TInput, TParser7> parser7,
        Parser2<TInput, TParser8> parser8,
        Parser2<TInput, TParser9> parser9,
        Func9<TParser1, TParser2, TParser3, TParser4, TParser5, TParser6, TParser7, TParser8, TParser9, TOutput> producer) // PORT: §3.8 Func<TParser1, TParser2, TParser3, TParser4, TParser5, TParser6, TParser7, TParser8, TParser9, TOutput> -> Func9
    {
        return new RuleParser<TInput, TOutput>(
            Arrays.<Parser<TInput>>asList(parser1, parser2, parser3, parser4, parser5, parser6, parser7, parser8, parser9), // PORT: §3.10 new Parser<TInput>[] { ... }

            (list, start) ->
            {
                Ensure.areEqual(list.size() - start, 9);
                return producer.apply(
                    (TParser1) list.get(start),
                    (TParser2) list.get(start + 1),
                    (TParser3) list.get(start + 2),
                    (TParser4) list.get(start + 3),
                    (TParser5) list.get(start + 4),
                    (TParser6) list.get(start + 5),
                    (TParser7) list.get(start + 6),
                    (TParser8) list.get(start + 7),
                    (TParser9) list.get(start + 8)); // PORT: §3.10 unchecked casts
            },

            (source, start) ->
            {
                var result1 = parser1.parse(source, start);
                if (result1.length() < 0)
                    return new ParseResult<TOutput>(result1.length(), null); // PORT: §3.10 default(TOutput)
                var len = result1.length();

                var result2 = parser2.parse(source, start + len);
                if (result2.length() < 0)
                    return new ParseResult<TOutput>(-len + result2.length(), null); // PORT: §3.10 default(TOutput)
                len += result2.length();

                var result3 = parser3.parse(source, start + len);
                if (result3.length() < 0)
                    return new ParseResult<TOutput>(-len + result3.length(), null); // PORT: §3.10 default(TOutput)
                len += result3.length();

                var result4 = parser4.parse(source, start + len);
                if (result4.length() < 0)
                    return new ParseResult<TOutput>(-len + result4.length(), null); // PORT: §3.10 default(TOutput)
                len += result4.length();

                var result5 = parser5.parse(source, start + len);
                if (result5.length() < 0)
                    return new ParseResult<TOutput>(-len + result5.length(), null); // PORT: §3.10 default(TOutput)
                len += result5.length();

                var result6 = parser6.parse(source, start + len);
                if (result6.length() < 0)
                    return new ParseResult<TOutput>(-len + result6.length(), null); // PORT: §3.10 default(TOutput)
                len += result6.length();

                var result7 = parser7.parse(source, start + len);
                if (result7.length() < 0)
                    return new ParseResult<TOutput>(-len + result7.length(), null); // PORT: §3.10 default(TOutput)
                len += result7.length();

                var result8 = parser8.parse(source, start + len);
                if (result8.length() < 0)
                    return new ParseResult<TOutput>(-len + result8.length(), null); // PORT: §3.10 default(TOutput)
                len += result8.length();

                var result9 = parser9.parse(source, start + len);
                if (result9.length() < 0)
                    return new ParseResult<TOutput>(-len + result9.length(), null); // PORT: §3.10 default(TOutput)
                len += result9.length();

                var produced = producer.apply(result1.value(), result2.value(), result3.value(), result4.value(), result5.value(), result6.value(), result7.value(), result8.value(), result9.value());
                return new ParseResult<TOutput>(len, produced);
            });
    }


    // PORT: §3.2 empty struct LeftValue<TLeft> -> empty final class (inference marker)
    public static final class LeftValue<TLeft>
    {
    }

    /// <summary>
    /// A parser that takes only the 'left' side parser's value and converts it into a new value.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TInput, TLeft, TOutput> RightParser<TInput, TOutput> rule(
        LeftValue<TLeft> left,
        Function<TLeft, TOutput> producer) // PORT: §3.8
    {
        return new RightParser<TInput, TOutput>(
            new RuleParser<TInput, TOutput>(
                Arrays.<Parser<TInput>>asList(), // PORT: §3.10 new Parser<TInput>[] { }
                (list, start) ->
                {
                    Ensure.areEqual(list.size() - start, 1);
                    return producer.apply(
                        (TLeft) list.get(start)); // PORT: §3.10 unchecked cast
                }));
    }

    /// <summary>
    /// A parser that combines the 'left' side parser's value with the values from a sequence of parsers into a new value.
    /// This parser fails if any of the parsers in the sequence fail.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TInput, TLeft, TParser1, TOutput> RightParser<TInput, TOutput> rule(
        LeftValue<TLeft> left,
        Parser2<TInput, TParser1> parser1,
        BiFunction<TLeft, TParser1, TOutput> producer) // PORT: §3.8
    {
        return new RightParser<TInput, TOutput>(
            new RuleParser<TInput, TOutput>(
                Arrays.<Parser<TInput>>asList(parser1), // PORT: §3.10 new Parser<TInput>[] { ... }
                (list, start) ->
                {
                    Ensure.areEqual(list.size() - start, 2);
                    return producer.apply(
                        (TLeft) list.get(start),
                        (TParser1) list.get(start + 1)); // PORT: §3.10 unchecked casts
                }));
    }

    /// <summary>
    /// A parser that combines the 'left' side parser's value with the values from a sequence of parsers into a new value.
    /// This parser fails if any of the parsers in the sequence fail.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TInput, TLeft, TParser1, TParser2, TOutput> RightParser<TInput, TOutput> rule(
        LeftValue<TLeft> left,
        Parser2<TInput, TParser1> parser1,
        Parser2<TInput, TParser2> parser2,
        Func3<TLeft, TParser1, TParser2, TOutput> producer) // PORT: §3.8 Func<TLeft, TParser1, TParser2, TOutput> -> Func3
    {
        return new RightParser<TInput, TOutput>(
            new RuleParser<TInput, TOutput>(
                Arrays.<Parser<TInput>>asList(parser1, parser2), // PORT: §3.10 new Parser<TInput>[] { ... }
                (list, start) ->
                {
                    Ensure.areEqual(list.size() - start, 3);
                    return producer.apply(
                        (TLeft) list.get(start),
                        (TParser1) list.get(start + 1),
                        (TParser2) list.get(start + 2)); // PORT: §3.10 unchecked casts
                }));
    }

    /// <summary>
    /// A parser that combines the 'left' side parser's value with the values from a sequence of parsers into a new value.
    /// This parser fails if any of the parsers in the sequence fail.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TInput, TLeft, TParser1, TParser2, TParser3, TOutput> RightParser<TInput, TOutput> rule(
        LeftValue<TLeft> left,
        Parser2<TInput, TParser1> parser1,
        Parser2<TInput, TParser2> parser2,
        Parser2<TInput, TParser2> parser3, // PORT-BUG: parser3 is typed TParser2 (not TParser3) upstream; mirrored
        Func4<TLeft, TParser1, TParser2, TParser3, TOutput> producer) // PORT: §3.8 Func<TLeft, TParser1, TParser2, TParser3, TOutput> -> Func4
    {
        return new RightParser<TInput, TOutput>(
            new RuleParser<TInput, TOutput>(
                Arrays.<Parser<TInput>>asList(parser1, parser2, parser3), // PORT: §3.10 new Parser<TInput>[] { ... }
                (list, start) ->
                {
                    Ensure.areEqual(list.size() - start, 4);
                    return producer.apply(
                        (TLeft) list.get(start),
                        (TParser1) list.get(start + 1),
                        (TParser2) list.get(start + 2),
                        (TParser3) list.get(start + 3)); // PORT: §3.10 unchecked casts
                }));
    }

    /// <summary>
    /// A parser that parsers a sequence of values into the output.
    /// If any parser in the sequence fails, then this parser fails.
    /// </summary>
    @SafeVarargs
    public static <TInput> Parser<TInput> sequence(Parser<TInput>... parsers)
    {
        return new SequenceParser<TInput>(Arrays.asList(parsers)); // PORT: §3.10 params array as IReadOnlyList
    }

    /// <summary>
    /// A parser that parsers a sequence of values into the output.
    /// If any parser in the sequence fails, then this parser fails.
    /// </summary>
    public static <TInput> Parser<TInput> sequence(List<? extends Parser<TInput>> parsers) // PORT: §3.10 IReadOnlyList<T> is covariant upstream
    {
        return new SequenceParser<TInput>(parsers);
    }

    /// <summary>
    /// A parser that converts all the successfully scanned input characters into a single output string.
    /// </summary>
    public static Parser2<Character, String> text(Parser<Character> pattern)
    {
        return Parsers.<Character, String>convert(pattern, (Source<Character> source, int start, int length) -> ((TextSource) source).peekText(start, length));
    }

    /// <summary>
    /// A parser that consumes the matching text characters and produces the same text string.
    /// </summary>
    public static Parser2<Character, String> text(String text)
    {
        return Parsers.<Character, String>convert(match(text), text);
    }

    /// <summary>
    /// A parser that converts all the successfully scanned input characters into a single output string and its starting offset.
    /// </summary>
    public static Parser2<Character, OffsetValue<String>> textAndOffset(Parser<Character> pattern)
    {
        return Parsers.<Character, OffsetValue<String>>convert(pattern, (Source<Character> source, int start, int length) -> new OffsetValue<String>(start, ((TextSource) source).peekText(start, length)));
    }

    /// <summary>
    /// A parser that consumes no input but always generates the specified output.
    /// </summary>
    public static <TInput, TOutput> Parser2<TInput, TOutput> value(Supplier<TOutput> fnValue) // PORT: §3.8 Func<TOutput>
    {
        return Parsers.<TInput, TOutput>match((SourceConsumer<TInput>) (source, start) -> 0, (SourceProducer<TInput, TOutput>) (source, start, length) -> fnValue.get());
    }

    /// <summary>
    /// A parser that combines zero or more parsed values into a single value.
    /// </summary>
    public static <TInput, TParser, TProducer> Parser2<TInput, TProducer> zeroOrMore(
        Parser2<TInput, TParser> parser,
        Function<List<TParser>, TProducer> producer)
    {
        return produceList(zeroOrMore(parser), producer);
    }

    /// <summary>
    /// A parser that parses zero or more values from the specified parser.
    /// </summary>
    public static <TInput> Parser<TInput> zeroOrMore(Parser<TInput> parser)
    {
        return new ZeroOrMoreParser<TInput>(parser);
    }

    /// <summary>
    /// A parser that parses zero or more values from the specified parser
    /// and produces a list of those values.
    /// </summary>
    public static <TInput, TParser> Parser2<TInput, List<TParser>> zeroOrMoreList(
        Parser2<TInput, TParser> parser)
    {
        return Parsers.<TInput, TParser, List<TParser>>zeroOrMore(parser, list -> ListExtensions.toReadOnly(list)); // PORT: §3.5
    }

    /// <summary>
    /// A parser that parses zero or one value from the specified parser.
    /// </summary>
    public static <TInput> Parser<TInput> zeroOrOne(Parser<TInput> parser)
    {
        return new ZeroOrMoreParser<TInput>(parser, true); // zeroOrOne: true
    }



}
