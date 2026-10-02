// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/FirstParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graylog.kusto.language.utils.Ensure;
import org.graylog.kusto.language.utils.ListExtensions;

public class FirstParser2<TInput, TOutput> extends Parser2<TInput, TOutput> // PORT: §2.4 FirstParser<TInput, TOutput>
{
    private final Parser2<TInput, TOutput>[] _parsers;
    private final List<Parser2<TInput, TOutput>> _parsersView; // PORT: §3.17 read-only view of the array, created once
    public List<Parser2<TInput, TOutput>> parsers() { return _parsersView; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public FirstParser2(List<? extends Parser2<TInput, TOutput>> parsers) // PORT: §3.10 IReadOnlyList<T> is covariant upstream
    {
        Ensure.argumentNotNull(parsers, "parsers" /* nameof */);
        Ensure.elementsNotNull(parsers, "parsers" /* nameof */);
        _parsers = parsers.toArray(new Parser2[0]); // PORT: §3.10 ToArray()
        _parsersView = Collections.unmodifiableList(Arrays.asList(_parsers)); // PORT: §3.17
    }

    @Override
    public boolean isAlternation() { return true; }

    @Override
    public int childParserCount() { return _parsers.length; }

    @Override
    public Parser<TInput> getChildParser(int index)
    {
        if (index >= 0 && index < _parsers.length)
            return _parsers[index];
        return null;
    }

    @Override
    public void accept(ParserVisitor<TInput> visitor)
    {
        visitor.visitFirst(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitFirst(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitFirst(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new FirstParser2<TInput, TOutput>(this.parsers());
    }

    @Override
    public ParseResult<TOutput> parse(Source<TInput> source, int start)
    {
        int minLength = -1;

        for (int i = 0; i < _parsers.length; i++)
        {
            var parser = _parsers[i];
            var result = parser.parse(source, start);

            if (result.length() >= 0)
            {
                Ensure.isTrue(result.length() > 0 || i == this.parsers().size() - 1, "zero consuming parsers should only occur at end");
                return result;
            }

            if (result.length() < minLength)
            {
                minLength = result.length();
            }
        }

        return new ParseResult<TOutput>(minLength, null); // PORT: §3.10 default(TOutput)
    }

    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        int minLength = -1;
        var originalOutputCount = output.size();

        for (int i = 0; i < _parsers.length; i++)
        {
            var parser = _parsers[i];
            ListExtensions.setCount(output, originalOutputCount); // PORT: §3.5

            var length = parser.parse(source, inputStart, output, outputStart);
            if (length >= 0)
            {
                return length;
            }

            if (length < minLength)
            {
                minLength = length;
            }
        }

        return minLength;
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        int minLength = -1;

        for (int i = 0; i < _parsers.length; i++)
        {
            var parser = _parsers[i];
            var n = parser.scan(source, start);
            if (n >= 0)
                return n;

            if (n < minLength)
            {
                minLength = n;
            }
        }

        return minLength;
    }
}
