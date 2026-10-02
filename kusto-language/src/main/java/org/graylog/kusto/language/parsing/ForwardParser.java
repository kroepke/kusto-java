// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/ForwardParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;
import java.util.function.Supplier;

import org.graylog.kusto.language.utils.Ensure;

public final class ForwardParser<TInput, TOutput> extends ListPrimaryParser<TInput, TOutput>
{
    private final Supplier<Parser2<TInput, TOutput>> deferredParser; // PORT: §3.8 Func<Parser<TInput, TOutput>>
    public Supplier<Parser2<TInput, TOutput>> deferredParser() { return this.deferredParser; }

    public ForwardParser(Supplier<Parser2<TInput, TOutput>> deferredParser)
    {
        Ensure.argumentNotNull(deferredParser, "deferredParser" /* nameof */);
        this.deferredParser = deferredParser;
    }

    @Override
    public boolean isForward() { return true; }

    @Override
    public int childParserCount() { return 1; }

    @Override
    public Parser<TInput> getChildParser(int index)
    {
        return index == 0 ? deferredParser().get() : null;
    }

    @Override
    public void accept(ParserVisitor<TInput> visitor)
    {
        visitor.visitForward(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitForward(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitForward(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new ForwardParser<TInput, TOutput>(this.deferredParser());
    }

    // Sanitiy check recursive call depth to catch run-away parsing/scanning on deeply nested function calls, etc
    // [ThreadStatic]
    // PORT: D9 one ThreadLocal counter shared by all instantiations (per closed generic type upstream); §3.9
    private static final ThreadLocal<int[]> s_callDepth = ThreadLocal.withInitial(() -> new int[1]);
    private static final int MaxCallDepth = 30;

    @Override
    public ParseResult<TOutput> parse(Source<TInput> source, int start)
    {
        var callDepth = s_callDepth.get(); // PORT: D9
        try
        {
            callDepth[0]++;

            if (callDepth[0] > MaxCallDepth)
            {
                return super.parse(source, start);
            }

            return this.deferredParser().get().parse(source, start);
        }
        finally
        {
            callDepth[0]--;
        }
    }

    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        var callDepth = s_callDepth.get(); // PORT: D9
        try
        {
            callDepth[0]++;

            if (callDepth[0] > MaxCallDepth)
            {
                return SafeParser.parseSafe(this.deferredParser().get(), source, inputStart, output, outputStart); // PORT: §3.5
            }

            return this.deferredParser().get().parse(source, inputStart, output, outputStart);
        }
        finally
        {
            callDepth[0]--;
        }
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        var callDepth = s_callDepth.get(); // PORT: D9
        try
        {
            callDepth[0]++;

            if (callDepth[0] > MaxCallDepth)
            {
                return SafeScanner.scanSafe(this.deferredParser().get(), source, start); // PORT: §3.5
            }

            return this.deferredParser().get().scan(source, start);
        }
        finally
        {
            callDepth[0]--;
        }
    }
}
