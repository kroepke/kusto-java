// Ported from: src/Kusto.Language/Symbols/VariableSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.ValueInfo;
import org.graylog.kusto.language.utils.ArgumentCheckers;

/// <summary>
/// A symbol for a variable declaration.
/// Typically from a let statement.
/// </summary>
public final class VariableSymbol extends Symbol
{
    /// <summary>
    /// The type of the variable.
    /// </summary>
    private final TypeSymbol type;
    public TypeSymbol type() { return this.type; }

    /// <summary>
    /// True if the variable should be considered a constant.
    /// </summary>
    private final boolean isConstant;
    public boolean isConstant() { return this.isConstant; }

    /// <summary>
    /// The known constant value info (or null if unknown).
    /// </summary>
    private final ValueInfo constantValueInfo;
    public ValueInfo constantValueInfo() { return this.constantValueInfo; }

    /// <summary>
    /// The known constant value (or null if unknown).
    /// </summary>
    public Object constantValue() { return constantValueInfo() != null ? constantValueInfo().value() : null; } // PORT: §3.14 ?.

    /// <summary>
    /// The expression that the variable is computed from.
    /// </summary>
    private final Expression source;
    public Expression source() { return this.source; }

    /// <summary>
    /// Creates a new instance of a <see cref="VariableSymbol"/>
    /// </summary>
    public VariableSymbol(String name, TypeSymbol type, boolean isConstant, ValueInfo constantValueInfo, Expression source)
    {
        super(name);
        this.type = ArgumentCheckers.checkArgumentNull(type, "type" /* nameof */);
        this.isConstant = isConstant;
        this.constantValueInfo = constantValueInfo;
        this.source = source;
    }

    // PORT: §3.12 optional parameters isConstant = false, constantValueInfo = null, source = null
    public VariableSymbol(String name, TypeSymbol type)
    {
        this(name, type, false, null, null);
    }

    public VariableSymbol(String name, TypeSymbol type, boolean isConstant)
    {
        this(name, type, isConstant, null, null);
    }

    public VariableSymbol(String name, TypeSymbol type, boolean isConstant, ValueInfo constantValueInfo)
    {
        this(name, type, isConstant, constantValueInfo, null);
    }

    @Override
    public SymbolKind kind() { return SymbolKind.Variable; }

    @Override
    public Tabularity tabularity() { return this.type().tabularity(); }
}
