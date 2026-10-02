// Ported from: src/Kusto.Language/Syntax/CustomNode.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

import java.util.Objects;

import org.graylog.kusto.language.editor.CompletionHint;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;

/// <summary>
/// Describes facts about a child element of a <see cref="CustomNode"/>.
/// </summary>
public class CustomElementDescriptor
{
    /// <summary>
    /// The name of the element.
    /// </summary>
    private final String name;
    public String name() { return this.name; }

    /// <summary>
    /// If true, then the element is optional (can be null)
    /// </summary>
    private final boolean isOptional;
    public boolean isOptional() { return this.isOptional; }

    /// <summary>
    /// The <see cref="CompletionHint"/> associated with this element.
    /// </summary>
    private final int completionHint; // PORT: §3.17 CompletionHint is an int holder (D23)
    public int completionHint() { return this.completionHint; }

    public CustomElementDescriptor(String name, int hint, boolean isOptional)
    {
        this.name = name != null ? name : ""; // PORT: §3.14
        this.isOptional = isOptional;
        this.completionHint = hint;
    }

    public CustomElementDescriptor(String name, int hint) // PORT: §3.12
    {
        this(name, hint, false);
    }

    public CustomElementDescriptor(int hint, boolean isOptional)
    {
        this("", hint, isOptional);
    }

    public CustomElementDescriptor(int hint) // PORT: §3.12
    {
        this(hint, false);
    }

    public static CustomElementDescriptor from(String name, int hint, boolean isOptional)
    {
        if (DotNetStrings.isNullOrEmpty(name)
            && hint == CompletionHint.Syntax
            && !isOptional)
        {
            return Default;
        }
        else
        {
            return new CustomElementDescriptor(name, hint, isOptional);
        }
    }

    public static CustomElementDescriptor from(String name, int hint) // PORT: §3.12
    {
        return from(name, hint, false);
    }

    public static CustomElementDescriptor from(String name) // PORT: §3.12
    {
        return from(name, CompletionHint.Syntax);
    }

    public static CustomElementDescriptor from(int hint, boolean isOptional)
    {
        return from(null, hint, isOptional);
    }

    public static CustomElementDescriptor from(int hint) // PORT: §3.12
    {
        return from(hint, false);
    }

    public static CustomElementDescriptor from(boolean isOptional)
    {
        return from(null, CompletionHint.Syntax, isOptional);
    }

    public CustomElementDescriptor withName(String name)
    {
        if (Objects.equals(this.name(), name)) // PORT: §3.14
            return this;
        return new CustomElementDescriptor(name, this.completionHint(), this.isOptional());
    }

    public CustomElementDescriptor withHint(int hint)
    {
        if (this.completionHint() == hint)
            return this;
        return new CustomElementDescriptor(this.name(), hint, this.isOptional());
    }

    public CustomElementDescriptor withIsOptional(boolean isOptional)
    {
        if (this.isOptional() == isOptional)
            return this;
        return new CustomElementDescriptor(this.name(), this.completionHint(), this.isOptional()); // PORT-BUG: upstream passes this.IsOptional, not the argument; mirrored
    }

    public boolean isDefault()
    {
        return (this == Default)
            || (DotNetStrings.isNullOrEmpty(this.name())
                && this.completionHint() == CompletionHint.Syntax
                && this.isOptional() == false);
    }

    public static CustomElementDescriptor Default =
        new CustomElementDescriptor("", CompletionHint.Syntax, false);
}
