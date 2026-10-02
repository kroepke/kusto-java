// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/ConvertParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.graylog.kusto.language.utils.Ensure;
import org.graylog.kusto.language.utils.ObjectPool;

/// <summary>
/// A parser that converts the tokens that would otherwise be consumed by another parser (Pattern) into a different output.
/// </summary>
public final class ConvertParser<TInput, TOutput> extends ResultPrimaryParser<TInput, TOutput>
{
    private final Parser<TInput> pattern;
    public Parser<TInput> pattern() { return this.pattern; }

    private final SourceProducer<TInput, TOutput> listProducer;
    public SourceProducer<TInput, TOutput> listProducer() { return this.listProducer; }

    private final Function<TInput, TOutput> singleProducer; // PORT: §3.8 Func<TInput, TOutput>
    public Function<TInput, TOutput> singleProducer() { return this.singleProducer; }

    private ConvertParser(
        Parser<TInput> pattern,
        SourceProducer<TInput, TOutput> listProducer,
        Function<TInput, TOutput> singleProducer)
    {
        Ensure.argumentNotNull(pattern, "pattern" /* nameof */);
        Ensure.isTrue(listProducer != null || singleProducer != null);

        this.pattern = pattern;
        this.listProducer = listProducer;
        this.singleProducer = singleProducer;
    }

    public ConvertParser(
        Parser<TInput> pattern,
        SourceProducer<TInput, TOutput> producer)
    {
        this(pattern, producer, null);
    }

    /// <summary>
    /// The upstream constructor <c>ConvertParser(Parser&lt;TInput&gt;, Func&lt;IReadOnlyList&lt;TInput&gt;, TOutput&gt;)</c>.
    /// </summary>
    // PORT: §2.5 erases to the same signature as ConvertParser(Parser, Func<TInput, TOutput>) below;
    // a constructor cannot be renamed, so the IReadOnlyList form is the static factory ofList (proposed §2.5 row)
    public static <TInput, TOutput> ConvertParser<TInput, TOutput> ofList(
        Parser<TInput> pattern,
        Function<List<TInput>, TOutput> producer)
    {
        return new ConvertParser<TInput, TOutput>(pattern, listToSourceProducer(producer), null);
    }

    // PORT: §3.11 the multi-statement lambda of the `: this(...)` chain at ConvertParser.cs:40 as a private static helper
    @SuppressWarnings("unchecked")
    private static <TInput, TOutput> SourceProducer<TInput, TOutput> listToSourceProducer(Function<List<TInput>, TOutput> producer)
    {
        return (Source<TInput> source, int start, int length) ->
        {
            var values = (List<TInput>) (List<?>) s_inputListPool.allocateFromPool(); // PORT: §3.9 shared static pool in a generic class
            try
            {
                for (int i = 0; i < length; i++)
                {
                    values.add(source.peek(start + i));
                }

                return producer.apply(values);
            }
            finally
            {
                s_inputListPool.returnToPool((List<Object>) (List<?>) values); // PORT: §3.9
            }
        };
    }

    public ConvertParser(
        Parser<TInput> pattern,
        Function<TInput, TOutput> producer)
    {
        this(pattern, null, producer);
    }

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
        visitor.visitConvert(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitConvert(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitConvert(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new ConvertParser<TInput, TOutput>(this.pattern(), this.listProducer(), this.singleProducer());
    }

    @Override
    public ParseResult<TOutput> parse(Source<TInput> source, int start)
    {
        int length = this.pattern().scan(source, start);
        if (length < 0)
            return new ParseResult<TOutput>(length, null); // PORT: §3.10 default(TOutput)

        var value = produce(source, start, length);
        return new ParseResult<TOutput>(length, value);
    }

    /// <summary>
    /// Common pool of input lists: don't allocate temporary lists!
    /// </summary>
    // PORT: §3.9 static field of a generic class: one pool shared by all instantiations (List<object> stands in for List<TInput>)
    private static final ObjectPool<List<Object>> s_inputListPool =
        new ObjectPool<List<Object>>(() -> new ArrayList<Object>(), list -> list.clear());

    private TOutput produce(Source<TInput> source, int start, int length)
    {
        if (this.singleProducer() != null)
        {
            return this.singleProducer().apply(source.peek(start));
        }
        else
        {
            return this.listProducer().invoke(source, start, length);
        }
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        return this.pattern().scan(source, start);
    }
}
