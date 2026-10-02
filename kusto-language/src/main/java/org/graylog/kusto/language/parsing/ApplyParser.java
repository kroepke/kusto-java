// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/ApplyParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;

import org.graylog.kusto.language.utils.Ensure;
import org.graylog.kusto.language.utils.ListExtensions;

public final class ApplyParser<TInput, TLeft, TOutput> extends ListPrimaryParser<TInput, TOutput>
{
    private final Parser2<TInput, TLeft> leftParser;
    public Parser2<TInput, TLeft> leftParser() { return this.leftParser; }

    private final Parser2<TInput, TOutput> rightParser;
    public Parser2<TInput, TOutput> rightParser() { return this.rightParser; }

    private final ApplyKind applyKind;
    public ApplyKind applyKind() { return this.applyKind; }

    private ApplyParser(ApplyKind kind, Parser2<TInput, TLeft> leftParser, Parser2<TInput, TOutput> rightParser)
    {
        Ensure.argumentNotNull(leftParser, "leftParser" /* nameof */);
        Ensure.argumentNotNull(rightParser, "rightParser" /* nameof */);

        this.leftParser = leftParser;
        this.rightParser = rightParser;
        this.applyKind = kind;
    }

    public ApplyParser(ApplyKind kind, Parser2<TInput, TLeft> leftParser, RightParser<TInput, TOutput> rightParser)
    {
        this(kind, leftParser, rightParser.parser());
    }

    @Override
    public int childParserCount() { return 2; }

    @Override
    public Parser<TInput> getChildParser(int index)
    {
        switch (index)
        {
            case 0:
                return this.leftParser();
            case 1:
                return this.rightParser();
            default:
                return null;
        }
    }

    @Override
    public void accept(ParserVisitor<TInput> visitor)
    {
        visitor.visitApply(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitApply(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitApply(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new ApplyParser<TInput, TLeft, TOutput>(this.applyKind(), this.leftParser(), this.rightParser());
    }

    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        var leftOriginalOutputCount = output.size();

        var leftLength = leftParser().parse(source, inputStart, output, outputStart);
        if (leftLength < 0)
        {
            return leftLength;
        }

        var rightOriginalOutputCount = output.size();

        while (true)
        {
            var rightLength = rightParser().parse(source, inputStart + leftLength, output, outputStart);
            if (rightLength < 0)
            {
                if (this.applyKind() == ApplyKind.One)
                {
                    ListExtensions.setCount(output, leftOriginalOutputCount); // PORT: §3.5
                    return -leftLength + rightLength;
                }
                else
                {
                    ListExtensions.setCount(output, rightOriginalOutputCount); // PORT: §3.5
                    return leftLength;
                }
            }
            else
            {
                leftLength += rightLength;

                if (this.applyKind() != ApplyKind.ZeroOrMore)
                {
                    return leftLength;
                }
            }
        }
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        var leftLen = this.leftParser().scan(source, start);
        if (leftLen < 0)
        {
            return leftLen;
        }

        while (true)
        {
            var rightLen = this.rightParser().scan(source, start + leftLen);
            if (rightLen < 0)
            {
                if (this.applyKind() == ApplyKind.One)
                {
                    // failed to be applied once
                    return -leftLen + rightLen;
                }
                else
                {
                    return leftLen;
                }
            }
            else
            {
                leftLen += rightLen;

                if (this.applyKind() != ApplyKind.ZeroOrMore)
                {
                    return leftLen;
                }
            }
        }
    }
}
