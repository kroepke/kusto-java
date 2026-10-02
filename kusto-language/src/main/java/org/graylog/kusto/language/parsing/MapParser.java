// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/MapParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.Out;

public final class MapParser<TInput, TOutput> extends ResultPrimaryParser<TInput, TOutput>
{
    private final Node<TInput, TOutput> root;

    private MapParser(Node<TInput, TOutput> root)
    {
        this.root = root;

    }
    public MapParser(Iterable<Map.Entry<Iterable<TInput>, Supplier<TOutput>>> keyValuePairs) // PORT: §3.17 KeyValuePair -> Map.Entry; §3.8 Func<TOutput>
    {
        this(Node.from(keyValuePairs));
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
    public void accept(ParserVisitor<TInput> visitor)
    {
        visitor.visitMap(this);
    }

    @Override
    public <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor)
    {
        return visitor.visitMap(this);
    }

    @Override
    public <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg)
    {
        return visitor.visitMap(this, arg);
    }

    @Override
    protected Parser<TInput> clone()
    {
        return new MapParser<TInput, TOutput>(this.root);
    }

    @Override
    public ParseResult<TOutput> parse(Source<TInput> source, int start)
    {
        var node = root;
        var length = 0;

        var bestLength = -1;
        Supplier<TOutput> bestOutput = null;

        while (true)
        {
            if (source.isEnd(start + length))
                break;

            var input = source.peek(start + length);

            Out<Node<TInput, TOutput>> subNode = new Out<>(); // PORT: §3.3
            if (node.tryGetValueNode(input, subNode))
            {
                length++;

                if (subNode.value.hasValue())
                {
                    bestLength = length;
                    bestOutput = subNode.value.value();
                }

                node = subNode.value;
            }
            else
            {
                break;
            }
        }

        if (bestLength > 0)
        {
            var bestValue = bestOutput.get();
            return new ParseResult<TOutput>(bestLength, bestValue);
        }
        else
        {
            return new ParseResult<TOutput>(bestLength, null); // PORT: §3.10 default(TOutput)
        }
    }

    @Override
    public int scan(Source<TInput> source, int start)
    {
        var node = root;
        var length = 0;

        var bestLength = -1;

        while (true)
        {
            if (source.isEnd(start + length))
                break;

            var input = source.peek(start + length);

            Out<Node<TInput, TOutput>> subNode = new Out<>(); // PORT: §3.3
            if (node.tryGetValueNode(input, subNode))
            {
                length++;

                if (subNode.value.hasValue())
                {
                    bestLength = length;
                }

                node = subNode.value;
            }
            else
            {
                break;
            }
        }

        return bestLength;
    }

    private static final class Node<TInput, TOutput> // PORT: §3.10 nested class of a generic class takes its own type parameters
    {
        private boolean hasValue;
        public boolean hasValue() { return this.hasValue; }

        private Supplier<TOutput> value;
        public Supplier<TOutput> value() { return this.value; }

        private LinkedHashMap<TInput, Node<TInput, TOutput>> map; // PORT: §3.17 Dictionary -> LinkedHashMap

        private Node()
        {
        }

        public static <TInput, TOutput> Node<TInput, TOutput> from(Iterable<Map.Entry<Iterable<TInput>, Supplier<TOutput>>> keyValuePairs)
        {
            var node = new Node<TInput, TOutput>();

            for (var pair : keyValuePairs)
            {
                node.add(pair.getKey(), 0, pair.getValue());
            }

            return node;
        }

        private void add(Iterable<TInput> sequence, int pos, Supplier<TOutput> value)
        {
            var node = this;

            for (var item : sequence)
            {
                if (node.map == null)
                {
                    node.map = new LinkedHashMap<TInput, Node<TInput, TOutput>>();
                }

                Node<TInput, TOutput> subNode = node.map.get(Objects.requireNonNull(item, "key")); // PORT: §3.3 values never null; §3.16 null key throws
                if (subNode == null)
                {
                    subNode = new Node<TInput, TOutput>();
                    DotNet.dictionaryAdd(node.map, item, subNode); // PORT: §3.17
                }

                node = subNode;
            }

            node.hasValue = true;
            node.value = value;
        }

        public boolean tryGetValueNode(TInput key, Out<Node<TInput, TOutput>> node)
        {
            if (this.map != null)
            {
                node.value = this.map.get(Objects.requireNonNull(key, "key")); // PORT: §3.3 values never null; §3.16 null key throws
                return node.value != null;
            }

            node.value = null;
            return false;
        }
    }
}
