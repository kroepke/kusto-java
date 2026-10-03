// Ported from: src/Kusto.Language/Parser/ScannerExtensions.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import org.graylog.kusto.language.utils.ObjectPool;

public final class ScannerExtensions
{
    private ScannerExtensions() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// Determines if the scanner matches the specified text.
    /// </summary>
    public static boolean matches(Parser<Character> scanner, String text) // PORT: §3.5 extension method
    {
        return matches(scanner, text, 0, text.length());
    }

    /// <summary>
    /// Determines if the scanner matches the specified text.
    /// </summary>
    public static boolean matches(Parser<Character> scanner, String text, int offset, int length) // PORT: §3.5 extension method
    {
        var source = s_sourcePool.allocateFromPool();
        try
        {
            source.init(text);
            int len = scanner.scan(source, offset);
            return len == length; // must scan all characters
        }
        finally
        {
            s_sourcePool.returnToPool(source);
        }
    }

    private static ObjectPool<ReuseableTextSource> s_sourcePool =
        new ObjectPool<ReuseableTextSource>(() -> new ReuseableTextSource(), source -> source.clear());

    /// <summary>
    /// A source of text for syntax parsing
    /// </summary>
    private static final class ReuseableTextSource extends Source<Character> // PORT: §3.10 Source<char> -> Source<Character>
    {
        private String _source = "";
        private int _offset;
        private int _end;

        public ReuseableTextSource()
        {
        }

        public void init(String source, int offset, int length)
        {
            _source = source;
            _offset = offset;
            _end = offset + length;
        }

        public void init(String source)
        {
            init(source, 0, source.length());
        }

        public void clear()
        {
            _source = "";
            _offset = 0;
            _end = 0;
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
    }
}
