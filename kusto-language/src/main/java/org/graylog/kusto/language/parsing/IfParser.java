// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/IfParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;

import org.graylog.kusto.language.utils.Ensure;

/// <summary>
/// A parser that succeeds if both the Test parser scans and Parser parsers succeed.
/// </summary>
public final class IfParser<TInput> extends Parser<TInput>
{
    private final Parser<TInput> test;
    public Parser<TInput> test() { return this.test; }

    private final Parser<TInput> parser;
    public Parser<TInput> parser() { return this.parser; }

    public IfParser(Parser<TInput> test, Parser<TInput> parser)
    {
        Ensure.argumentNotNull(test, "test" /* nameof */);
        Ensure.argumentNotNull(parser, "parser" /* nameof */);

        this.test = test;
        this.parser = parser;
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
            case 0: return this.test();
            case 1: return this.parser();
            default: return null;
        }
    }

    @Override
    public void accept(ParserVisitor<TInput> visitor)
    {
        visitor.visitIf(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitIf(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitIf(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new IfParser<TInput>(this.test(), this.parser());
    }

    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        var length = this.test().scan(source, inputStart);
        if (length < 0)
            return length;

        return this.parser().parse(source, inputStart, output, outputStart);
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        var length = this.test().scan(source, start);
        if (length < 0)
            return length;

        return this.parser().scan(source, start);
    }
}
