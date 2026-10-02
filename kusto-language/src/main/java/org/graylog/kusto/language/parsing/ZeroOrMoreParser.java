// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/ZeroOrMoreParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;

import org.graylog.kusto.language.utils.Ensure;

public final class ZeroOrMoreParser<TInput> extends Parser<TInput>
{
    private final Parser<TInput> parser;
    public Parser<TInput> parser() { return this.parser; }

    private final boolean zeroOrOne;
    public boolean zeroOrOne() { return this.zeroOrOne; }

    public ZeroOrMoreParser(Parser<TInput> parser, boolean zeroOrOne)
    {
        Ensure.argumentNotNull(parser, "parser" /* nameof */);
        this.parser = parser;
        this.zeroOrOne = zeroOrOne;
    }

    public ZeroOrMoreParser(Parser<TInput> parser) // PORT: §3.12 optional parameter zeroOrOne = false
    {
        this(parser, false);
    }

    @Override
    public boolean isOptional() { return true; }
    @Override
    public boolean isRepetition() { return true; }

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
        visitor.visitZeroOrMore(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitZeroOrMore(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitZeroOrMore(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new ZeroOrMoreParser<TInput>(this.parser(), this.zeroOrOne());
    }

    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        var length = 0;

        while (true)
        {
            var len = parser().parse(source, inputStart + length, output, output.size());
            if (len > 0)
            {
                length += len;
            }

            if (len <= 0 || this.zeroOrOne())
                return length;
        }
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        var len = 0;

        while (true)
        {
            var n = parser().scan(source, start + len);
            if (n > 0)
            {
                len += n;
            }

            if (n <= 0 || this.zeroOrOne())
                break;
        }

        return len;
    }
}
