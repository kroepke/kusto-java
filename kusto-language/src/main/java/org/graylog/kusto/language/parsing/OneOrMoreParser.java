// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/OneOrMoreParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;

import org.graylog.kusto.language.utils.Ensure;

public final class OneOrMoreParser<TInput> extends Parser<TInput>
{
    private final Parser<TInput> parser;
    public Parser<TInput> parser() { return this.parser; }

    public OneOrMoreParser(Parser<TInput> parser)
    {
        Ensure.argumentNotNull(parser, "parser" /* nameof */);
        this.parser = parser;
    }

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
        visitor.visitOneOrMore(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitOneOrMore(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitOneOrMore(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new OneOrMoreParser<TInput>(this.parser());
    }

    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        var firstLen = this.parser().parse(source, inputStart, output, output.size());
        if (firstLen < 0)
        {
            return firstLen;
        }

        var length = firstLen;

        while (true)
        {
            var len = parser().parse(source, inputStart + length, output, output.size());
            if (len <= 0)
            {
                return length;
            }
            else
            {
                length += len;
            }
        }
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        var n = parser().scan(source, start);
        if (n < 0)
        {
            return n;
        }

        var len = n;

        while (true)
        {
            n = parser().scan(source, start + len);
            if (n <= 0)
                break;
            len += n;
        }

        return len;
    }
}
