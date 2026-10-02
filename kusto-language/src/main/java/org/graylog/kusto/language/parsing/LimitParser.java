// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/LimitParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;

/// <summary>
/// A parser that succeeds if the Limited parser succeeds with only the tokens that the Limiter parser successfully scans.
/// </summary>
public final class LimitParser<TInput, TOutput> extends Parser2<TInput, TOutput>
{
    public final Parser<TInput> Limiter;
    public final Parser2<TInput, TOutput> Limited;

    public LimitParser(Parser<TInput> limiter, Parser2<TInput, TOutput> limited)
    {
        this.Limiter = limiter;
        this.Limited = limited;
    }

    @Override
    public boolean isConditional() { return true; }

    @Override
    public int childParserCount() { return 2; }

    @Override
    public Parser<TInput> getChildParser(int index)
    {
        switch (index)
        {
            case 0: return this.Limiter;
            case 1: return this.Limited;
            default: return null;
        }
    }

    @Override
    public void accept(ParserVisitor<TInput> visitor)
    {
        visitor.visitLimit(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitLimit(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitLimit(this, arg);
    }

    @Override
    public ParseResult<TOutput> parse(Source<TInput> input, int inputStart)
    {
        var len = this.Limiter.scan(input, inputStart);
        if (len >= 0)
        {
            var limitSource = new LimitSource<TInput>(input, inputStart + len);
            return this.Limited.parse(limitSource, inputStart);
        }
        return new ParseResult<TOutput>(-1, null); // PORT: §3.10 default(TOutput)
    }

    @Override
    public int parse(Source<TInput> input, int inputStart, List<Object> output, int outputStart)
    {
        var len = this.Limiter.scan(input, inputStart);
        if (len >= 0)
        {
            var limitSource = new LimitSource<TInput>(input, inputStart + len);
            return this.Limited.parse(limitSource, inputStart, output, outputStart);
        }
        return -1;
    }

    @Override
    public int scan(Source<TInput> input, int inputStart)
    {
        var len = this.Limiter.scan(input, inputStart);
        if (len >= 0)
        {
            var limitSource = new LimitSource<TInput>(input, inputStart + len);
            return this.Limited.scan(limitSource, inputStart);
        }
        return -1;
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new LimitParser<TInput, TOutput>(this.Limiter, this.Limited);
    }
}
