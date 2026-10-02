// Ported from: src/Kusto.Language/Syntax/SeparatedElement.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

/// <summary>
/// An element in a list with an optional separator token.
/// </summary>
public final class SeparatedElement1<TElement extends SyntaxElement> extends SeparatedElement // PORT: §2.4 SeparatedElement<TElement>
{
    /// <summary>
    /// The element in a list
    /// </summary>
    @Override
    @SuppressWarnings("unchecked")
    public TElement element() { return (TElement)super.element(); } // PORT: §3.10 'new' hiding → covariant override

    public SeparatedElement1(TElement element, SyntaxToken separator)
    {
        super(element, separator);
    }

    public SeparatedElement1(TElement element) // PORT: §3.12
    {
        this(element, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected SyntaxElement cloneCore(boolean includeDiagnostics)
    {
        return new SeparatedElement1<TElement>((TElement)this.element().clone(includeDiagnostics), this.separator() != null ? this.separator().clone(includeDiagnostics) : null); // PORT: §3.14
    }

    public static <TElement extends SyntaxElement> SeparatedElement1<TElement> empty() { return new SeparatedElement1<TElement>(null, null); }
}
