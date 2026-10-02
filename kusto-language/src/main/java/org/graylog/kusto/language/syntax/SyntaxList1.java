// Ported from: src/Kusto.Language/Syntax/SyntaxList.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.utils.dotnet.ReadOnlyList;

/// <summary>
/// A list of <see cref="SyntaxElement"/>'s.
/// </summary>
public final class SyntaxList1<TElement extends SyntaxElement> extends SyntaxList implements ReadOnlyList<TElement> // PORT: §2.4 SyntaxList<TElement>; §3.10 IReadOnlyList<T> → ReadOnlyList<T> (D26)
{
    public SyntaxList1(Iterable<? extends TElement> elements, List<Diagnostic> diagnostics)
    {
        super(toArray(elements), diagnostics); // PORT: §3.11 computed base argument via private static helper
    }

    public SyntaxList1(Iterable<? extends TElement> elements) // PORT: §3.12
    {
        this(elements, null);
    }

    @SafeVarargs
    public SyntaxList1(TElement... elements)
    {
        super(elements, null);
    }

    private static SyntaxElement[] toArray(Iterable<? extends SyntaxElement> elements) // PORT: §3.11 elements.ToArray()
    {
        var list = new ArrayList<SyntaxElement>();
        for (var e : elements)
        {
            list.add(e);
        }
        return list.toArray(new SyntaxElement[0]);
    }

    @Override
    public SyntaxKind kind() { return SyntaxKind.List; }

    @Override
    public Class<?> elementType() { return SyntaxElement.class; } // PORT: §3.10 typeof(TElement) is erased; the bound is the closest runtime answer

    @SuppressWarnings("unused")
    private static <TElement extends SyntaxElement> SyntaxElement[] copy(List<TElement> list) // PORT: §3.10 new TElement[] → SyntaxElement[]
    {
        var newArray = new SyntaxElement[list.size()];

        for (int i = 0; i < newArray.length; i++)
        {
            newArray[i] = list.get(i);
        }

        return newArray;
    }

    /// <summary>
    /// Gets the element at the index.
    /// </summary>
    @Override
    @SuppressWarnings("unchecked")
    public TElement get(int index) { return (TElement)super.get(index); } // PORT: §3.10 'new' indexer → covariant override

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<TElement> iterator() // PORT: §3.10 'new' GetEnumerator → covariant override
    {
        return (Iterator<TElement>)(Iterator<?>)this.getElements().iterator();
    }

    // IEnumerator IEnumerable.GetEnumerator() is covered by iterator() // PORT: §3.10

    /// <summary>
    /// Creates a copy of this <see cref="SyntaxList{TElement}"/>
    /// </summary>
    @Override
    @SuppressWarnings("unchecked")
    public SyntaxList1<TElement> clone(boolean includeDiagnostics) { return (SyntaxList1<TElement>)this.cloneCore(includeDiagnostics); } // PORT: §3.10 'new' hiding → covariant override

    @Override
    public SyntaxList1<TElement> clone() { return clone(true); } // PORT: §3.12

    @Override
    @SuppressWarnings("unchecked")
    protected SyntaxElement cloneCore(boolean includeDiagnostics)
    {
        var oldElements = this.getElements();
        var newElements = new SyntaxElement[oldElements.size()]; // PORT: §3.10 new TElement[] → SyntaxElement[]

        for (int i = 0; i < newElements.length; i++)
        {
            newElements[i] = (TElement)oldElements.get(i).clone(includeDiagnostics);
        }

        return new SyntaxList1<TElement>((TElement[])newElements);
    }

    @SuppressWarnings("unchecked")
    public static <TElement extends SyntaxElement> SyntaxList1<TElement> empty()
    {
        return new SyntaxList1<TElement>((TElement[])new SyntaxElement[0]); // PORT: §3.10 new TElement[0] → SyntaxElement[0]
    }
}
