// Ported from: src/Kusto.Language/Parser/Combinators/RightParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// A parser that is allowed on the right side of an Apply.
/// </summary>
// PORT: §3.2 struct -> record; the component accessor parser() is the internal Parser property
public record RightParser<TInput, TOutput>(
    /// <summary>
    /// The underlying parser that is on the right side of an Apply.
    /// </summary>
    @Internal Parser2<TInput, TOutput> parser)
{
    /// <summary>
    /// Creates a copy of this <see cref="RightParser{TInput, TOutput}"/> with the tag specified.
    /// </summary>
    public RightParser<TInput, TOutput> withTag(String tag)
    {
        return new RightParser<TInput, TOutput>(this.parser().withTag(tag));
    }

    /// <summary>
    /// Creates a copy of this <see cref="RightParser{TInput, TOutput}"/> with the annotations specified.
    /// </summary>
    public RightParser<TInput, TOutput> withAnnotations(Iterable<?> annotations) // PORT: §3.10 IEnumerable<object> is covariant upstream
    {
        return new RightParser<TInput, TOutput>(this.parser().withAnnotations(annotations));
    }

    /// <summary>
    /// Creates a copy of this <see cref="RightParser{TInput, TOutput}"/> with the IsHidden property specified.
    /// </summary>
    public RightParser<TInput, TOutput> withIsHidden(boolean isHidden)
    {
        return new RightParser<TInput, TOutput>(this.parser().withIsHidden(isHidden));
    }

    /// <summary>
    /// Creates a copy of this <see cref="RightParser{TInput, TOutput}"/> with the IsHidden property set to true.
    /// </summary>
    public RightParser<TInput, TOutput> hide()
    {
        return this.withIsHidden(true);
    }
}
