// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/RuleParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

import org.graylog.kusto.language.utils.ListExtensions;

public final class RuleParser<TInput, TProducer> extends ListPrimaryParser<TInput, TProducer>
{
    private final Parser<TInput>[] _parsers;
    private final List<Parser<TInput>> _parsersView; // PORT: §3.17 read-only view of the array, created once
    public List<Parser<TInput>> parsers() { return _parsersView; }

    private final BiFunction<List<Object>, Integer, TProducer> listProducer; // PORT: §3.8 Func<List<object>, int, TProducer>
    public BiFunction<List<Object>, Integer, TProducer> listProducer() { return this.listProducer; }

    private final BiFunction<Source<TInput>, Integer, ParseResult<TProducer>> resultProducer; // PORT: §3.8 Func<Source<TInput>, int, ParseResult<TProducer>>
    public BiFunction<Source<TInput>, Integer, ParseResult<TProducer>> resultProducer() { return this.resultProducer; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public RuleParser(
        List<? extends Parser<TInput>> parsers, // PORT: §3.10 IReadOnlyList<T> is covariant upstream
        BiFunction<List<Object>, Integer, TProducer> listProducer,
        BiFunction<Source<TInput>, Integer, ParseResult<TProducer>> resultProducer)
    {
        _parsers = parsers.toArray(new Parser[0]); // PORT: §3.10 ToArray()
        _parsersView = Collections.unmodifiableList(Arrays.asList(_parsers)); // PORT: §3.17
        this.listProducer = listProducer;
        this.resultProducer = resultProducer;
    }

    public RuleParser(
        List<? extends Parser<TInput>> parsers,
        BiFunction<List<Object>, Integer, TProducer> listProducer) // PORT: §3.12 optional parameter resultProducer = null
    {
        this(parsers, listProducer, null);
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
        visitor.visitRule(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitRule(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitRule(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new RuleParser<TInput, TProducer>(this.parsers(), this.listProducer(), this.resultProducer());
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

    @Override
    public ParseResult<TProducer> parse(Source<TInput> source, int start)
    {
        if (this.resultProducer() != null)
        {
            return this.resultProducer().apply(source, start);
        }
        else
        {
            return super.parse(source, start);
        }
    }

    @Override
    public int parse(Source<TInput> input, int inputStart, List<Object> output, int outputStart)
    {
        int length = 0;
        int originalOutputCount = output.size();

        // invoke each parser in sequence.. if one fails then the whole is in error
        for (int i = 0; i < _parsers.length; i++)
        {
            var parser = _parsers[i];
            int n = parser.parse(input, inputStart + length, output, output.size());
            if (n < 0)
            {
                ListExtensions.setCount(output, originalOutputCount); // PORT: §3.5
                return n - length;
            }

            length += n;
        }

        var value = this.listProducer().apply(output, outputStart);
        ListExtensions.setCount(output, outputStart); // PORT: §3.5
        output.add(value);

        return length;
    }
}
