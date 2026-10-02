// Ported from: src/Kusto.Language/Syntax/SeparatedElement.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

import org.graylog.kusto.language.editor.CompletionHint;

public abstract class SeparatedElement extends SyntaxNode
{
    /// <summary>
    /// The element in a list
    /// </summary>
    private final SyntaxElement element;
    public SyntaxElement element() { return this.element; }

    /// <summary>
    /// An optional separator token.
    /// </summary>
    private final SyntaxToken separator;
    public SyntaxToken separator() { return this.separator; }

    protected SeparatedElement(SyntaxElement element, SyntaxToken separator)
    {
        super(null);
        this.element = attach(element);
        this.separator = attach(separator, true); // PORT: §3.12
        this.init();
    }

    protected SeparatedElement(SyntaxElement element) // PORT: §3.12
    {
        this(element, null);
    }

    @Override
    public SyntaxKind kind() { return SyntaxKind.SeparatedElement; }

    @Override
    public int childCount() { return 2; }

    @Override
    public SyntaxElement getChild(int index)
    {
        switch (index)
        {
            case 0: return this.element();
            case 1: return this.separator();
            default: throw new IndexOutOfBoundsException(); // PORT: §3.16
        }
    }

    @Override
    public int getCompletionHint(int index) // PORT: §3.17 CompletionHint is an int holder (D23)
    {
        switch (index)
        {
            case 0: return CompletionHint.Inherit;
            case 1: return CompletionHint.Syntax; // seperator is punctuation, no symbols
            default: throw new IndexOutOfBoundsException(); // PORT: §3.16
        }
    }

    @Override
    public void accept(SyntaxVisitor visitor)
    {
        visitor.visitSeparatedElement(this);
    }

    @Override
    public <TResult> TResult accept(SyntaxVisitor1<TResult> visitor)
    {
        return visitor.visitSeparatedElement(this);
    }
}
