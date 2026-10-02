// Ported from: src/Kusto.Language/Symbols/CommandSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

/// <summary>
/// A <see cref="Symbol"/> corresponding to a control command.
/// </summary>
public class CommandSymbol extends Symbol
{
    // PORT: §3.13 upstream's racy lazy caches are plain fields; volatile here so a published value is visible
    private volatile String _resultSchema;
    private volatile TableSymbol _resultType;

    /// <summary>
    /// The language declaration of the result schema: (col:type, ...)
    /// </summary>
    public String resultSchema()
    {
        if (_resultSchema == null && _resultType != null)
        {
            _resultSchema = SchemaDisplay.getText(_resultType);
        }

        return _resultSchema;
    }

    /// <summary>
    /// The <see cref="TableSymbol"/> describing the result schema.
    /// </summary>
    public TableSymbol resultType()
    {
        if (_resultType == null && _resultSchema != null)
        {
            if ("()".equals(_resultSchema) // PORT: §3.14 string ==
                || "(*)".equals(_resultSchema))
            {
                _resultType = new TableSymbol().withIsOpen(true);
            }
            else
            {
                _resultType = TableSymbol.from(_resultSchema);
            }
        }

        return _resultType;
    }

    private final String construction;
    public String construction() { return this.construction; }

    @Override
    public SymbolKind kind() { return SymbolKind.Command; }

    public CommandSymbol(String name, String resultSchema, String construction)
    {
        super(name);
        _resultSchema = resultSchema;
        this.construction = construction != null ? construction : ""; // PORT: §3.14 ??
    }

    public CommandSymbol(String name, String resultSchema) // PORT: §3.12 optional parameter construction = null
    {
        this(name, resultSchema, (String) null);
    }

    public CommandSymbol(String name, TableSymbol resultType, String construction)
    {
        super(name);
        _resultType = resultType;
        this.construction = construction != null ? construction : ""; // PORT: §3.14 ??
    }

    public CommandSymbol(String name, TableSymbol resultType) // PORT: §3.12 optional parameter construction = null
    {
        this(name, resultType, (String) null);
    }
}
