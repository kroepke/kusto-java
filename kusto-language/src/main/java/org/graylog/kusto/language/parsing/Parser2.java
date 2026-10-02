// Ported from: src/Kusto.Language/Parser/Combinators/Parser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

/// <summary>
/// A parser that will produce exactly one output item if it succeeds.
/// </summary>
public abstract class Parser2<TInput, TOutput> extends Parser<TInput> // PORT: §2.4 Parser<TInput, TOutput>
{
    /// <summary>
    /// Parses input source items and produces a single output item.
    /// </summary>
    public abstract ParseResult<TOutput> parse(Source<TInput> input, int inputStart);

    /// <summary>
    /// Creates a copy of this <see cref="Parser{TInput}"/> with the tag specified.
    /// </summary>
    @Override
    @SuppressWarnings("unchecked")
    public Parser2<TInput, TOutput> withTag(String tag) { return (Parser2<TInput, TOutput>) super.withTag(tag); } // PORT: §3.10 `new` member hiding -> covariant override

    /// <summary>
    /// Creates a copy of this <see cref="Parser{TInput}"/> with the annotations specified.
    /// </summary>
    @Override
    @SuppressWarnings("unchecked")
    public Parser2<TInput, TOutput> withAnnotations(Iterable<?> annotations) { return (Parser2<TInput, TOutput>) super.withAnnotations(annotations); } // PORT: §3.10 `new` member hiding -> covariant override

    /// <summary>
    /// Creates a copy of this <see cref="Parser{TInput}"/> with the IsHidden property specified.
    /// </summary>
    @Override
    @SuppressWarnings("unchecked")
    public Parser2<TInput, TOutput> withIsHidden(boolean isHidden) { return (Parser2<TInput, TOutput>) super.withIsHidden(isHidden); } // PORT: §3.10 `new` member hiding -> covariant override

    /// <summary>
    /// Creates a copy of this <see cref="Parser{TInput}"/> with the IsHidden property set to true.
    /// </summary>
    @Override
    public Parser2<TInput, TOutput> hide() { return this.withIsHidden(true); } // PORT: §3.10 `new` member hiding -> covariant override

    /// <summary>
    /// Creates a copy of this <see cref="Parser{TInput, TOutput}"/> that converts its output to the specified type.
    /// </summary>
    /// <typeparam name="TNewOutput">The type to convert the output to.</typeparam>
    @SuppressWarnings("unchecked")
    public <TNewOutput> Parser2<TInput, TNewOutput> cast()
    {
        // PORT: §3.10 (TNewOutput)(object)o is an unchecked cast: no runtime check here (C# would throw InvalidCastException)
        return Parsers.<TInput, TOutput, TNewOutput>rule(this, o -> (TNewOutput) (Object) o).withTag(this.tag());
    }
}
