// Ported from: src/Kusto.Language/QueryOperatorParameters.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language;

import java.util.List;

import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.symbols.SymbolKind;
import org.graylog.kusto.language.utils.ListExtensions;

/// <summary>
/// Describes a parameter that a query operator may have.
/// </summary>
public class QueryOperatorParameter extends Symbol
{
    /// <summary>
    /// The kind that the value can take.
    /// </summary>
    private final QueryOperatorParameterValueKind valueKind;
    public QueryOperatorParameterValueKind valueKind() { return this.valueKind; }

    /// <summary>
    /// True if token/keyword value matches are case sensitive.
    /// </summary>
    private final boolean isCaseSensitive;
    public boolean isCaseSensitive() { return this.isCaseSensitive; }

    /// <summary>
    /// The set of known parameter values.
    /// </summary>
    private final List<String> values;
    public List<String> values() { return this.values; }

    /// <summary>
    /// True if the parameter can be specified more than once.
    /// </summary>
    private final boolean isRepeatable;
    public boolean isRepeatable() { return this.isRepeatable; }

    /// <summary>
    /// True if the parameter is typed with no equals token between the name and value
    /// </summary>
    private final boolean hasNoEquals;
    public boolean hasNoEquals() { return this.hasNoEquals; }

    /// <summary>
    /// Any additional names that the parameter can be referenced by.
    /// </summary>
    private final List<String> aliases;
    public List<String> aliases() { return this.aliases; }

    private final boolean _isHidden;
    @Override
    public boolean isHidden() { return _isHidden; }

    @Override
    public SymbolKind kind() { return SymbolKind.QueryOperatorParameter; }

    private QueryOperatorParameter(
        String name,
        QueryOperatorParameterValueKind kind,
        boolean isCaseSensitive,
        Iterable<String> values,
        boolean isRepeatable,
        boolean isHidden,
        boolean hasNoEquals,
        List<String> aliases)
    {
        super(name);
        this.valueKind = kind;
        this.values = ListExtensions.toReadOnly(values); // PORT: §3.5
        this.isCaseSensitive = isCaseSensitive;
        this.isRepeatable = isRepeatable;
        _isHidden = isHidden;
        this.hasNoEquals = hasNoEquals;
        this.aliases = ListExtensions.toReadOnly(aliases); // PORT: §3.5
    }

    public QueryOperatorParameter(
        String name,
        QueryOperatorParameterValueKind kind,
        boolean isCaseSensitive,
        Iterable<String> values,
        boolean isRepeatable,
        List<String> aliases)
    {
        this(name, kind, isCaseSensitive, values, isRepeatable, false, false, aliases);
    }

    public QueryOperatorParameter(String name, QueryOperatorParameterValueKind kind, boolean isCaseSensitive, Iterable<String> values, boolean isRepeatable) // PORT: §3.12 aliases = null
    {
        this(name, kind, isCaseSensitive, values, isRepeatable, (List<String>)null);
    }

    public QueryOperatorParameter(String name, QueryOperatorParameterValueKind kind, boolean isCaseSensitive, Iterable<String> values) // PORT: §3.12 isRepeatable = false, aliases = null
    {
        this(name, kind, isCaseSensitive, values, false, (List<String>)null);
    }

    public QueryOperatorParameter(String name, QueryOperatorParameterValueKind kind, boolean isCaseSensitive) // PORT: §3.12 values = null, isRepeatable = false, aliases = null
    {
        this(name, kind, isCaseSensitive, null, false, (List<String>)null);
    }

    public QueryOperatorParameter(String name, QueryOperatorParameterValueKind kind) // PORT: §3.12 isCaseSensitive = true, values = null, isRepeatable = false, aliases = null
    {
        this(name, kind, true, null, false, (List<String>)null);
    }

    public QueryOperatorParameter withIsHidden(boolean isHidden)
    {
        if (this.isHidden() != isHidden)
        {
            return new QueryOperatorParameter(this.name(), this.valueKind(), this.isCaseSensitive(), this.values(), this.isRepeatable(), isHidden, this.hasNoEquals(), this.aliases());
        }
        else
        {
            return this;
        }
    }

    public QueryOperatorParameter withValues(List<String> values)
    {
        if (this.values() != values)
        {
            return new QueryOperatorParameter(this.name(), this.valueKind(), this.isCaseSensitive(), values, this.isRepeatable(), this.isHidden(), this.hasNoEquals(), this.aliases());
        }
        else
        {
            return this;
        }
    }

    public QueryOperatorParameter withIsCaseSensitive(boolean isCaseSensitive)
    {
        if (this.isCaseSensitive() != isCaseSensitive)
        {
            return new QueryOperatorParameter(this.name(), this.valueKind(), isCaseSensitive, this.values(), this.isRepeatable(), this.isHidden(), this.hasNoEquals(), this.aliases());
        }
        else
        {
            return this;
        }
    }

    public QueryOperatorParameter withHasNoEquals(boolean hasNoEquals)
    {
        if (this.hasNoEquals() != hasNoEquals)
        {
            return new QueryOperatorParameter(this.name(), this.valueKind(), this.isCaseSensitive(), this.values(), this.isRepeatable(), this.isHidden(), hasNoEquals, this.aliases());
        }
        else
        {
            return this;
        }
    }

    public QueryOperatorParameter hide() { return withIsHidden(true); }
}
