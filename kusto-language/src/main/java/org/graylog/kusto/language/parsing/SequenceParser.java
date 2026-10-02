// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/SequenceParser.cs
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

public final class SequenceParser<TInput> extends Parser<TInput>
{
    private final Parser<TInput>[] _parsers;
    private final List<Parser<TInput>> _parsersView; // PORT: §3.17 read-only view of the array, created once
    public List<Parser<TInput>> parsers() { return _parsersView; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public SequenceParser(List<? extends Parser<TInput>> parsers) // PORT: §3.10 IReadOnlyList<T> is covariant upstream
    {
        Ensure.argumentNotNull(parsers, "parsers" /* nameof */);
        Ensure.elementsNotNull(parsers, "parsers" /* nameof */);
        _parsers = parsers.toArray(new Parser[0]); // PORT: §3.10 ToArray()
        _parsersView = Collections.unmodifiableList(Arrays.asList(_parsers)); // PORT: §3.17
    }

    @Override
    public boolean isSequence() { return true; }

    @Override
    public int childParserCount() { return _parsers.length; }

    @Override
    public Parser<TInput> getChildParser(int index)
    {
        return index >= 0 && index < _parsers.length ? _parsers[index] : null;
    }

    @Override
    public void accept(ParserVisitor<TInput> visitor)
    {
        visitor.visitSequence(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitSequence(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitSequence(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new SequenceParser<TInput>(this.parsers());
    }

    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        int length = 0;
        var originalOutputCount = output.size();

        for (int i = 0; i < _parsers.length; i++)
        {
            var parser = _parsers[i];
            var len = parser.parse(source, inputStart + length, output, output.size());

            if (len < 0)
            {
                ListExtensions.setCount(output, originalOutputCount); // PORT: §3.5
                return -length + len;
            }

            length += len;
        }

        return length;
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        var len = 0;

        for (int i = 0; i < _parsers.length; i++)
        {
            var parser = _parsers[i];
            var n = parser.scan(source, start + len);

            if (n < 0)
            {
                return n - len;
            }

            len += n;
        }

        return len;
    }
}
