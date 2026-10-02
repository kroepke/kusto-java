// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/MatchParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;
import java.util.function.Predicate;

/// <summary>
/// A parser that succeeds if it successfully consumes tokens.
/// </summary>
public class MatchParser<TInput> extends Parser<TInput>
{
    private final SourceConsumer<TInput> consumer;
    public SourceConsumer<TInput> consumer() { return this.consumer; }

    public MatchParser(SourceConsumer<TInput> consumer)
    {
        this.consumer = consumer;
    }

    public MatchParser(Predicate<TInput> predicate) // PORT: §3.8 Func<TInput, bool>
    {
        this((source, start) -> !source.isEnd(start) && predicate.test(source.peek(start)) ? 1 : -1);
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
        return new MatchParser<TInput>(this.consumer());
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
        return scan(source, inputStart);
    }
}
