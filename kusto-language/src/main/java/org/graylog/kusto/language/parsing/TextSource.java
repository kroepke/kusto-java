// Ported from: src/Kusto.Language/Parser/Combinators/TextSource.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import org.graylog.kusto.language.utils.StringTable;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;

/// <summary>
/// A source of text for syntax parsing
/// </summary>
public final class TextSource extends Source<Character> // PORT: §3.10 Source<char> -> Source<Character>
{
    private final String _source;
    private int _offset;
    private int _end;
    private StringTable _strings;

    public TextSource(String source, int offset, int length)
    {
        _source = source;
        _offset = offset;
        _end = offset + length;
    }

    public TextSource(String source)
    {
        this(source, 0, source.length());
    }

    @Override
    public Character peek(int n)
    {
        return _offset + n < _end ? _source.charAt(_offset + n) : '\0';
    }

    @Override
    public boolean isEnd(int n)
    {
        return _offset + n >= _end;
    }

    public String peekText(int length)
    {
        return peekText(0, length);
    }

    public String peekText(int start, int length)
    {
        if (_strings == null)
        {
             _strings = new StringTable();
        }

        return _strings.add(_source, _offset + start, length);
    }

    public boolean matches(int start, String text)
    {
        // compare first character before calling string.Compare (perf)
        var offs = _offset + start;
        return offs < _source.length() 
            && text.length() > 0 
            && _source.charAt(offs) == text.charAt(0)
            && DotNetStrings.compare(_source, offs, text, 0, text.length()) == 0; // PORT: §5.4 D12 ordinal, clamped region
    }

    public boolean matches(int start, String text, boolean ignoreCase)
    {
        return DotNetStrings.compare(_source, _offset + start, text, 0, text.length(), ignoreCase) == 0; // PORT: §5.4 D12 ordinal / OrdinalIgnoreCase, clamped region
    }

    /// <summary>
    ///  The current position within the source text.
    /// </summary>
    public int position()
    {
        return this._offset;
    }
}
