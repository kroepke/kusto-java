// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/BestParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graylog.kusto.language.utils.Ensure;

public final class BestParser<TInput> extends Parser<TInput>
{
    private final Parser<TInput>[] _parsers;
    private final List<Parser<TInput>> _parsersView; // PORT: §3.17 read-only view of the array, created once
    public List<Parser<TInput>> parsers() { return _parsersView; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public BestParser(List<? extends Parser<TInput>> parsers) // PORT: §3.10 IReadOnlyList<T> is covariant upstream
    {
        Ensure.argumentNotNull(parsers, "parsers" /* nameof */);
        Ensure.elementsNotNull(parsers, "parsers" /* nameof */);
        _parsers = parsers.toArray(new Parser[0]); // PORT: §3.10 ToArray()
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
        visitor.visitBest(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitBest(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitBest(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new BestParser<TInput>(this.parsers());
    }

    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        // must be true: until this is rewritten for use as RightParser
        Ensure.areEqual(output.size(), outputStart);

        int minLength = -1;
        int maxLength = -1;
        int bestParser = -1;

        // figure out which parser will consume most input
        for (int i = 0; i < _parsers.length; i++)
        {
            var parser = _parsers[i];
            var length = parser.scan(source, inputStart);

            if (length > maxLength)
            {
                maxLength = length;
                bestParser = i;
            }
            else if (length < minLength)
            {
                minLength = length;
            }
        }

        if (maxLength >= 0)
        {
            _parsers[bestParser].parse(source, inputStart, output, outputStart);
        }

        return maxLength; // PORT-BUG: returns -1 on failure where Scan returns min (the longest failure); mirrored
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        int max = -1;
        int min = -1;

        for (int i = 0; i < _parsers.length; i++)
        {
            var parser = _parsers[i];
            var n = parser.scan(source, start);
            if (n > max)
                max = n;
            else if (n < min)
                min = n;
        }

        return max >= 0 ? max : min;
    }
}
