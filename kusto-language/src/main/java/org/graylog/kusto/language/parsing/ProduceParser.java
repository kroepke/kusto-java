// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/ProduceParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;
import java.util.function.BiFunction;

import org.graylog.kusto.language.utils.Ensure;
import org.graylog.kusto.language.utils.ListExtensions;

public class ProduceParser<TInput, TProducer> extends ListPrimaryParser<TInput, TProducer>
{
    private final Parser<TInput> parser;
    public Parser<TInput> parser() { return this.parser; }

    private final BiFunction<List<Object>, Integer, TProducer> producer; // PORT: §3.8 Func<List<object>, int, TProducer>
    public BiFunction<List<Object>, Integer, TProducer> producer() { return this.producer; }

    public ProduceParser(Parser<TInput> parser, BiFunction<List<Object>, Integer, TProducer> producer)
    {
        Ensure.argumentNotNull(parser, "parser" /* nameof */);
        Ensure.argumentNotNull(producer, "producer" /* nameof */);

        this.parser = parser;
        this.producer = producer;
    }

    @Override
    public int childParserCount() { return 1; }

    @Override
    public Parser<TInput> getChildParser(int index)
    {
        return index == 0 ? this.parser() : null;
    }

    @Override
    public void accept(ParserVisitor<TInput> visitor)
    {
        visitor.visitProduce(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitProduce(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitProduce(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new ProduceParser<TInput, TProducer>(this.parser(), this.producer());
    }

    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        int originalOutputCount = output.size();
        var length = this.parser().parse(source, inputStart, output, outputStart);
        return produce(output, outputStart, originalOutputCount, length);
    }

    private int produce(List<Object> output, int outputStart, int originalOutputCount, int inputLength)
    {
        if (inputLength >= 0)
        {
            var value = this.producer().apply(output, outputStart);
            ListExtensions.setCount(output, outputStart); // PORT: §3.5
            output.add(value);
        }
        else
        {
            ListExtensions.setCount(output, originalOutputCount); // PORT: §3.5
        }

        return inputLength;
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        return parser().scan(source, start);
    }
}
