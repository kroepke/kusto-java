// Ported from: src/Kusto.Language/Binder/SemanticInfo.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W6

package org.graylog.kusto.language.binding;

import java.util.List;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// The semantic information associated with a <see cref="SyntaxNode"/>.
/// </summary>
@Internal
public final class SemanticInfo
{
    /// <summary>
    /// The symbol referenced by the <see cref="SyntaxNode"/>,
    /// a column, function, operator, etc.
    /// May be null.
    /// </summary>
    public Symbol referencedSymbol() // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    /// <summary>
    /// The matching signature of the function or operator symbol referenced by the <see cref="SyntaxNode"/>.
    /// May be null.
    /// </summary>
    public Signature referencedSignature() // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    /// <summary>
    /// The result type of the expression.
    /// May be null if the node is not an expression.
    /// </summary>
    public TypeSymbol resultType() // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    /// <summary>
    /// If true then the expression is considered constant.
    /// </summary>
    public boolean isConstant() // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    /// <summary>
    /// Diagnostics discovered during binding.
    /// </summary>
    public List<Diagnostic> diagnostics() // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    /// <summary>
    /// The expansion of the function called
    /// </summary>
    public FunctionCallInfo calledFunctionInfo() // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    /// <summary>
    /// A list of alternate versions of the associated node with differing semantics (or null).
    /// </summary>
    public List<SyntaxNode> alternates() // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    public SemanticInfo(TypeSymbol result) // PORT-PENDING: W6 (telescoped from SemanticInfo(TypeSymbol, IEnumerable<Diagnostic> = null, bool = false, FunctionCallInfo = null))
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }
}
