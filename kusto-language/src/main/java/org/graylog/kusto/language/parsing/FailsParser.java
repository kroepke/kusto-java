// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/FailsParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;

/// <summary>
/// A parser that succeeds if the specified Pattern parser fails.
/// </summary>
public final class FailsParser<TInput> extends Parser<TInput>
{
    private final Parser<TInput> pattern;
    public Parser<TInput> pattern() { return this.pattern; }

    public FailsParser(Parser<TInput> pattern)
    {
        this.pattern = pattern;
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new FailsParser<TInput>(this.pattern());
    }

    @Override
    public boolean isNegation() { return true; }

    @Override
    public int childParserCount() { return 1; }

    @Override
    public Parser<TInput> getChildParser(int index)
    {
        return index == 0 ? this.pattern() : null;
    }

    @Override
    public void accept(ParserVisitor<TInput> visitor)
    {
        visitor.visitFails(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitFails(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitFails(this, arg);
    }

    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        return this.scan(source, inputStart);
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        // At end is a succeess here
        if (source.isEnd(start))
            return 0;

        if (this.pattern().scan(source, start) >= 0)
            return -1; // if pattern scan succeeds then this scan fails

        return 0;
    }
}
