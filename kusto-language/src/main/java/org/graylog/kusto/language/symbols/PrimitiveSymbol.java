// Ported from: src/Kusto.Language/Symbols/ScalarSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.utils.ArgumentCheckers;
import org.graylog.kusto.language.utils.ListExtensions;

/// <summary>
/// A symbol for scalar types: long, real, string, bool, etc.
/// </summary>
public final class PrimitiveSymbol extends ScalarSymbol
{
    private final List<String> _aliases;
    private final int _flags; // PORT: §3.17 ScalarFlags is an int holder (D23)
    private final List<ScalarSymbol> _widerThan;

    public PrimitiveSymbol(String name, String[] aliases, int flags, ScalarSymbol[] widerThan) // PORT: §3.17 ScalarFlags is an int holder (D23)
    {
        super(name);
        _aliases = ArgumentCheckers.checkArgumentNullOrElementNull(ListExtensions.toReadOnly(aliases != null ? Arrays.asList(aliases) : null), "aliases" /* nameof */); // PORT: §3.5, §3.17 array as IEnumerable
        _flags = flags;
        _widerThan = ArgumentCheckers.checkArgumentNullOrElementNull(ListExtensions.toReadOnly(widerThan != null ? Arrays.asList(widerThan) : null), "widerThan" /* nameof */); // PORT: §3.5, §3.17 array as IEnumerable
    }

    public PrimitiveSymbol(String name, String[] aliases, int flags) // PORT: §3.12 optional parameter widerThan = null
    {
        this(name, aliases, flags, null);
    }

    public PrimitiveSymbol(String name, String[] aliases) // PORT: §3.12 optional parameter flags = ScalarFlags.None
    {
        this(name, aliases, ScalarFlags.None);
    }

    public PrimitiveSymbol(String name) // PORT: §3.12 optional parameter aliases = null
    {
        this(name, null);
    }

    @Override
    public SymbolKind kind() { return SymbolKind.Primitive; }
    @Override
    public List<String> aliases() { return _aliases; }
    @Override
    public boolean isInteger() { return (_flags & ScalarFlags.Integer) != 0; }
    @Override
    public boolean isNumeric() { return (_flags & ScalarFlags.Numeric) != 0; }
    @Override
    public boolean isInterval() { return (_flags & ScalarFlags.Interval) != 0; }
    @Override
    public boolean isSummable() { return (_flags & ScalarFlags.Summable) != 0; }
    @Override
    public boolean isOrderable() { return (_flags & ScalarFlags.Orderable) != 0; }

    @Override
    public boolean isWiderThan(ScalarSymbol scalar)
    {
        for (int i = 0; i < _widerThan.size(); i++)
        {
            if (_widerThan.get(i) == scalar)
                return true;
        }

        return false;
    }
}
