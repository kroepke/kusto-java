// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/RequiredParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;
import java.util.function.BiFunction;

import org.graylog.kusto.language.utils.Ensure;
import org.graylog.kusto.language.utils.ListExtensions;

public final class RequiredParser<TInput, TOutput> extends Parser2<TInput, TOutput>
{
    private final Parser2<TInput, TOutput> parser;
    public Parser2<TInput, TOutput> parser() { return this.parser; }

    private final BiFunction<Source<TInput>, Integer, TOutput> producer; // PORT: §3.8 Func<Source<TInput>, int, TOutput>
    public BiFunction<Source<TInput>, Integer, TOutput> producer() { return this.producer; }

    public RequiredParser(Parser2<TInput, TOutput> parser, BiFunction<Source<TInput>, Integer, TOutput> producer)
    {
        Ensure.argumentNotNull(parser, "parser" /* nameof */);
        Ensure.argumentNotNull(producer, "producer" /* nameof */);
        this.parser = parser;
        this.producer = producer;
    }

    @Override
    public boolean isRequired() { return true; }

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
        visitor.visitRequired(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitRequired(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitRequired(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new RequiredParser<TInput, TOutput>(this.parser(), this.producer());
    }

    @Override
    public ParseResult<TOutput> parse(Source<TInput> source, int start)
    {
        var result = parser().parse(source, start);
        if (result.length() < 0)
        {
            return new ParseResult<TOutput>(0, this.producer().apply(source, start));
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

        var length = this.parser().parse(source, inputStart, output, output.size());
        if (length < 0 || output.size() == originalOutputCount)
        {
            ListExtensions.setCount(output, originalOutputCount); // PORT: §3.5
            output.add(this.producer().apply(source, inputStart));
            return 0;
        }

        return length;
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        var len = this.parser().scan(source, start);
        return (len < 0) ? 0 : len;
    }
}
