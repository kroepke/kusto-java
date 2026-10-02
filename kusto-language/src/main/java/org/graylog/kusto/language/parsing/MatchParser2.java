// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/MatchParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graylog.kusto.language.utils.Ensure;

/// <summary>
/// A parser that succeeds if it successfully consumes tokens.
/// The consumed tokens are converted into a produced values.
/// </summary>
public class MatchParser2<TInput, TOutput> extends Parser2<TInput, TOutput> // PORT: §2.4 MatchParser<TInput, TOutput>
{
    private final SourceConsumer<TInput> consumer;
    public SourceConsumer<TInput> consumer() { return this.consumer; }

    private final SourceProducer<TInput, TOutput> producer;
    public SourceProducer<TInput, TOutput> producer() { return this.producer; }

    public MatchParser2(SourceConsumer<TInput> consumer, SourceProducer<TInput, TOutput> producer)
    {
        Ensure.argumentNotNull(consumer, "consumer" /* nameof */);
        Ensure.argumentNotNull(producer, "producer" /* nameof */);

        this.consumer = consumer;
        this.producer = producer;
    }

    public MatchParser2(Predicate<TInput> predicate, Function<TInput, TOutput> producer) // PORT: §3.8 Func<TInput, bool>, Func<TInput, TOutput>
    {
        this(
              (source, start) -> !source.isEnd(start) && predicate.test(source.peek(start)) ? 1 : -1,
              (source, start, length) -> producer.apply(source.peek(start)));
    }

    @Override
    public boolean isMatch() { return true; }

    @Override
    public int childParserCount() { return 0; }

    @Override
    public Parser<TInput> getChildParser(int index)
    {
        return null;
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new MatchParser2<TInput, TOutput>(this.consumer(), this.producer());
    }

    @Override
    public void accept(ParserVisitor<TInput> visitor)
    {
        visitor.visitMatch(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitMatch(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitMatch(this, arg);
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        return this.consumer().invoke(source, start); // PORT: §3.8 delegate invoke
    }

    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        var length = this.consumer().invoke(source, inputStart); // PORT: §3.8 delegate invoke

        if (length >= 0)
        {
            var value = this.producer().invoke(source, inputStart, length); // PORT: §3.8 delegate invoke
            output.add(value);
        }

        return length;
    }

    @Override
    public ParseResult<TOutput> parse(Source<TInput> source, int start)
    {
        var length = this.consumer().invoke(source, start); // PORT: §3.8 delegate invoke

        if (length > 0) // length > 0 here but length >= 0 in the list Parse above: mirrored
        {
            var value = this.producer().invoke(source, start, length); // PORT: §3.8 delegate invoke
            return new ParseResult<TOutput>(length, value);
        }

        return new ParseResult<TOutput>(length, null); // PORT: §3.10 default(TOutput)
    }

    public ParseResult<TOutput> parse(Source<TInput> source) // PORT: §3.12 optional parameter start = 0
    {
        return parse(source, 0);
    }
}
