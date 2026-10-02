// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/OptionalParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;
import java.util.function.Supplier;

import org.graylog.kusto.language.utils.Ensure;
import org.graylog.kusto.language.utils.ListExtensions;

public class OptionalParser<TInput, TOutput> extends Parser2<TInput, TOutput>
{
    private final Parser2<TInput, TOutput> parser;
    public Parser2<TInput, TOutput> parser() { return this.parser; }

    private final Supplier<TOutput> producer; // PORT: §3.8 Func<TOutput>
    public Supplier<TOutput> producer() { return this.producer; }

    public OptionalParser(Parser2<TInput, TOutput> parser, Supplier<TOutput> producer)
    {
        Ensure.argumentNotNull(parser, "parser" /* nameof */);
        Ensure.argumentNotNull(producer, "producer" /* nameof */);
        this.parser = parser;
        this.producer = producer;
    }

    @Override
    public boolean isOptional() { return true; }

    @Override
    public int childParserCount() { return 1; }

    @Override
    public Parser<TInput> getChildParser(int index)
    {
        return index == 0 ? this.parser() : null;
    }

    @Override
    public void accept(ParserVisitor<TInput> visitor)
    {
        visitor.visitOptional(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitOptional(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitOptional(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new OptionalParser<TInput, TOutput>(this.parser(), this.producer());
    }

    @Override
    public ParseResult<TOutput> parse(Source<TInput> source, int start)
    {
        var result = this.parser().parse(source, start);
        if (result.length() < 0)
        {
            return new ParseResult<TOutput>(0, producer().get());
        }
        else
        {
            return result;
        }
    }

    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        var originalOutputCount = output.size();
        int length = parser().parse(source, inputStart, output, output.size());

        if (length < 0 || output.size() == originalOutputCount)
        {
            ListExtensions.setCount(output, originalOutputCount); // PORT: §3.5
            output.add(producer().get());
            return 0;
        }

        return length;
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        var n = parser().scan(source, start);

        if (n < 0)
        {
            return 0;
        }

        return n;
    }
}
