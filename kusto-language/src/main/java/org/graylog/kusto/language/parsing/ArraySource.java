// Ported from: src/Kusto.Language/Parser/Combinators/ArraySource.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;

/// <summary>
/// An input source based on an array or input items.
/// </summary>
public final class ArraySource<TInput> extends Source<TInput>
{
    private final List<TInput> _input; // PORT: §3.17 IReadOnlyList<TInput>
    private int _offset;
    private int _end;

    public ArraySource(List<TInput> input, int start, int length)
    {
        _input = input;
        _offset = start;

        if (length >= 0)
        {
            _end = start + Math.min(length, input.size() - start);
        }
        else
        {
            _end = input.size();
        }
    }

    public ArraySource(List<TInput> input, int start) // PORT: §3.12 optional parameter length = -1
    {
        this(input, start, -1);
    }

    public ArraySource(List<TInput> input) // PORT: §3.12 optional parameters start = 0, length = -1
    {
        this(input, 0, -1);
    }

    @Override
    public TInput peek(int n)
    {
        if (_offset + n < _end)
        {
            return _input.get(n + _offset);
        }
        else
        {
            return null; // PORT: §3.10 default(TInput) is null; for TInput = char C# yields '\0'
        }
    }

    @Override
    public boolean isEnd(int n) // PORT: §3.12 optional parameter n = 0; isEnd() is inherited from Source
    {
        return _offset + n >= _end;
    }
}
