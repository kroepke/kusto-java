// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/BestParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

import org.graylog.kusto.language.utils.Ensure;
import org.graylog.kusto.language.utils.ListExtensions;

public final class BestParser2<TInput, TOutput> extends Parser2<TInput, TOutput> // PORT: §2.4 BestParser<TInput, TOutput>
{
    private final Parser2<TInput, TOutput>[] _parsers;
    private final BiPredicate<TOutput, TOutput> _fnIsBetter; // PORT: §3.8 Func<TOutput, TOutput, bool>
    private final List<Parser2<TInput, TOutput>> _parsersView; // PORT: §3.17 read-only view of the array, created once

    public List<Parser2<TInput, TOutput>> parsers() { return _parsersView; }
    public BiPredicate<TOutput, TOutput> isBetter() { return _fnIsBetter; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public BestParser2(
        List<? extends Parser2<TInput, TOutput>> parsers, // PORT: §3.10 IReadOnlyList<T> is covariant upstream
        BiPredicate<TOutput, TOutput> fnIsBetter)
    {
        Ensure.argumentNotNull(parsers, "parsers" /* nameof */);
        Ensure.elementsNotNull(parsers, "parsers" /* nameof */);
        _parsers = parsers.toArray(new Parser2[0]); // PORT: §3.10 ToArray()
        _fnIsBetter = fnIsBetter;
        _parsersView = Collections.unmodifiableList(Arrays.asList(_parsers)); // PORT: §3.17
    }

    public BestParser2(List<? extends Parser2<TInput, TOutput>> parsers) // PORT: §3.12 optional parameter fnIsBetter = null
    {
        this(parsers, null);
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
        return new BestParser2<TInput, TOutput>(this.parsers()); // PORT-BUG: the clone drops IsBetter (fnIsBetter); mirrored
    }

    @Override
    public ParseResult<TOutput> parse(Source<TInput> source, int start)
    {
        int minLength = -1;
        int maxLength = -1;
        int bestParser = -1;
        List<Parser2<TInput, TOutput>> candidates = null;

        // figure out which parser will consume most input
        for (int i = 0; i < _parsers.length; i++)
        {
            var parser = _parsers[i];
            var length = parser.scan(source, start);

            if (length > maxLength)
            {
                maxLength = length;
                bestParser = i;

                if (candidates != null)
                {
                    candidates.clear();
                }
            }
            else if (length == maxLength && bestParser >= 0 && _fnIsBetter != null)
            {
                if (candidates == null)
                {
                    candidates = new ArrayList<Parser2<TInput, TOutput>>();
                }

                candidates.add(_parsers[i]);
            }
            else if (length < minLength)
            {
                minLength = length;
            }
        }

        if (maxLength >= 0)
        {
            var bestP = _parsers[bestParser];

            if (candidates != null && candidates.size() > 0)
            {
                var bestV = bestP.parse(source, start).value();

                for (int i = 0; i < candidates.size(); i++)
                {
                    var otherV = candidates.get(i).parse(source, start).value();
                    if (_fnIsBetter.test(otherV, bestV))
                    {
                        bestV = otherV;
                    }
                }

                return new ParseResult<TOutput>(maxLength, bestV);
            }
            else
            {
                var result = bestP.parse(source, start);
                return new ParseResult<TOutput>(maxLength, result.value());
            }
        }
        else
        {
            return new ParseResult<TOutput>(minLength, null); // PORT: §3.10 default(TOutput)
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        int minLength = -1;
        int maxLength = -1;
        int bestParser = -1;
        List<Parser2<TInput, TOutput>> candidates = null;

        // figure out which parser will consume most input
        for (int i = 0; i < _parsers.length; i++)
        {
            var parser = _parsers[i];
            var length = parser.scan(source, inputStart);

            if (length > maxLength)
            {
                maxLength = length;
                bestParser = i;

                if (candidates != null)
                {
                    candidates.clear();
                }
            }
            else if (length == maxLength && bestParser >= 0 && _fnIsBetter != null)
            {
                if (candidates == null)
                {
                    candidates = new ArrayList<Parser2<TInput, TOutput>>();
                }

                candidates.add(_parsers[i]);
            }
            else if (length < minLength)
            {
                minLength = length;
            }
        }

        if (maxLength >= 0)
        {
            var bestP = _parsers[bestParser];

            if (candidates != null && candidates.size() > 0)
            {
                bestP.parse(source, inputStart, output, outputStart);
                var bestV = (TOutput) output.get(outputStart); // PORT: §3.10 unchecked cast
                ListExtensions.setCount(output, outputStart); // PORT: §3.5

                for (int i = 0; i < candidates.size(); i++)
                {
                    candidates.get(i).parse(source, inputStart, output, outputStart);
                    var otherV = (TOutput) output.get(outputStart); // PORT: §3.10 unchecked cast
                    ListExtensions.setCount(output, outputStart); // PORT: §3.5

                    if (_fnIsBetter.test(otherV, bestV))
                    {
                        bestV = otherV;
                    }
                }

                output.add(bestV);
            }
            else
            {
                bestP.parse(source, inputStart, output, outputStart);
            }
        }

        return maxLength; // PORT-BUG: returns -1 on failure where Parse(source, start) and Scan return min (the longest failure); mirrored
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
