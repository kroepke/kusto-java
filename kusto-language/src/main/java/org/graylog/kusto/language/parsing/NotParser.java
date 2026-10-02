// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/NotParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;

public final class NotParser<TInput> extends Parser<TInput>
{
    private final Parser<TInput> pattern;
    public Parser<TInput> pattern() { return this.pattern; }

    public NotParser(Parser<TInput> parser)
    {
        this.pattern = parser;
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
    protected Parser<TInput> clone()
    {
        return new NotParser<TInput>(this.pattern());
    }

    @Override
    public void accept(ParserVisitor<TInput> visitor)
    {
        visitor.visitNot(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitNot(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitNot(this, arg);
    }

    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        return scan(source, inputStart);
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        // EOF never scans successfully
        if (source.isEnd(start))
            return -1;

        // if scanning succeeds then fail
        if (this.pattern().scan(source, start) >= 0)
            return -1;

        return 1;
    }
}
