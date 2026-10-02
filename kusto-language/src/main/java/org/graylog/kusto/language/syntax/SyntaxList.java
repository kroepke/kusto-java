// Ported from: src/Kusto.Language/Syntax/SyntaxList.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.graylog.kusto.language.Diagnostic;

/// <summary>
/// A list of <see cref="SyntaxElement"/>'s.
/// </summary>
public abstract class SyntaxList extends SyntaxNode //, IReadOnlyList<SyntaxElement>
{
    private final SyntaxElement[] elements;

    protected SyntaxList(SyntaxElement[] elements, List<Diagnostic> diagnostics)
    {
        super(diagnostics);
        this.elements = elements;

        for (int i = 0; i < elements.length; i++)
        {
            this.elements[i] = attach(elements[i]);
        }

        this.init();
    }

    @Override
    public SyntaxKind kind() { return SyntaxKind.List; }

    public Class<?> elementType() { return null; } // PORT: §3.10 System.Type → Class<?>

    /// <summary>
    /// Gets the element at the index.
    /// </summary>
    public SyntaxElement get(int index) { return elements[index]; } // PORT: §3.1 indexer

    /// <summary>
    /// The number of elements in the list.
    /// </summary>
    public int size() { return elements.length; } // PORT: §3.10 Count → size()

    public Iterator<? extends SyntaxElement> iterator() // PORT: §3.10 GetEnumerator; the base returns Iterator<? extends SyntaxElement>
    {
        return Arrays.asList(this.elements).iterator();
    }

    /// <summary>
    /// The number of child elements this element has.
    /// </summary>
    @Override
    public int childCount() { return this.size(); }

    /// <summary>
    /// Get the child of this element at the specified index.
    /// </summary>
    @Override
    public SyntaxElement getChild(int index) { return this.elements[index]; }

    protected List<SyntaxElement> getElements()
    {
        return Arrays.asList(this.elements); // PORT: §3.17 array as IReadOnlyList
    }

    @Override
    public void accept(SyntaxVisitor visitor)
    {
        visitor.visitList(this);
    }

    @Override
    public <TResult> TResult accept(SyntaxVisitor1<TResult> visitor)
    {
        return visitor.visitList(this);
    }
}
