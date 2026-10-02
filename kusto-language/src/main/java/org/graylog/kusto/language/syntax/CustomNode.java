// Ported from: src/Kusto.Language/Syntax/CustomNode.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// A <see cref="SyntaxNode"/> with variable shape.
/// </summary>
public class CustomNode extends SyntaxNode
{
    private final List<CustomElementDescriptor> shape;
    private final List<SyntaxElement> elements;

    @SuppressWarnings("unchecked")
    public CustomNode(List<CustomElementDescriptor> shape, List<SyntaxElement> elements, List<Diagnostic> diagnostics)
    {
        super(diagnostics);
        if (shape == null)
            throw new NullPointerException("shape" /* nameof */); // PORT: §3.16

        if (elements == null)
            throw new NullPointerException("elements" /* nameof */); // PORT: §3.16

        this.shape = shape;

        if (elements != null)
        {
            var elist = new ArrayList<SyntaxElement>(elements.size());

            for (int i = 0; i < elements.size(); i++)
            {
                var e = elements.get(i);
                var optional = this.shape.get(i).isOptional();
                var attached = attach(e, optional);
                elist.add(attached);
            }

            this.elements = Collections.unmodifiableList(elist); // PORT: §3.17 AsReadOnly
        }
        else
        {
            this.elements = (List<SyntaxElement>)(List<?>)EmptyReadOnlyList.Instance; // PORT: §3.9 shared raw static
        }

        this.init();
    }

    public CustomNode(List<CustomElementDescriptor> shape, SyntaxElement... elements)
    {
        this(shape, Arrays.asList(elements), null); // PORT: §3.17 array as IReadOnlyList
    }

    public CustomNode(SyntaxElement... elements)
    {
        this(getDefaultShape(elements.length), Arrays.asList(elements), null); // PORT: §3.17 array as IReadOnlyList
    }

    /// <summary>
    /// Gets the default shape for a <see cref="CustomNode"/> with the specified number of elements.
    /// </summary>
    public static List<CustomElementDescriptor> getDefaultShape(int count)
    {
        var elements = new CustomElementDescriptor[count];
        for (int i = 0; i < count; i++)
        {
            elements[i] = CustomElementDescriptor.Default;
        }

        return Arrays.asList(elements); // PORT: §3.17 array as IReadOnlyList
    }

    @Override
    public SyntaxKind kind() { return SyntaxKind.CustomNode; }

    @Override
    public int childCount() { return this.shape.size(); }

    @Override
    public SyntaxElement getChild(int index)
    {
        if (index < 0 || index >= this.shape.size())
        {
            throw new IndexOutOfBoundsException("index" /* nameof */); // PORT: §3.16
        }

        if (index < this.elements.size())
        {
            return this.elements.get(index);
        }
        else
        {
            return null;
        }
    }

    @Override
    public String getName(int index)
    {
        if (index < 0 || index >= this.shape.size())
        {
            throw new IndexOutOfBoundsException("index" /* nameof */); // PORT: §3.16
        }

        return this.shape.get(index).name();
    }

    @Override
    public int getCompletionHint(int index) // PORT: §3.17 CompletionHint is an int holder (D23)
    {
        if (index < 0 || index >= this.shape.size())
        {
            throw new IndexOutOfBoundsException("index" /* nameof */); // PORT: §3.16
        }

        return this.shape.get(index).completionHint();
    }

    @Override
    public boolean isOptional(int index)
    {
        if (index < 0 || index >= this.shape.size())
        {
            throw new IndexOutOfBoundsException("index" /* nameof */); // PORT: §3.16
        }

        return this.shape.get(index).isOptional();
    }

    @Override
    public void accept(SyntaxVisitor visitor)
    {
        visitor.visitCustom(this);
    }

    @Override
    public <TResult> TResult accept(SyntaxVisitor1<TResult> visitor)
    {
        return visitor.visitCustom(this);
    }

    @Override
    protected SyntaxElement cloneCore(boolean includeDiagnostics)
    {
        var clonedElements = Linq.toArray(Linq.select(this.elements, e -> e.clone(includeDiagnostics)), SyntaxElement[]::new); // PORT: §3.6
        return new CustomNode(this.shape, clonedElements);
    }
}
