// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/SourceProducer.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

/// <summary>
/// A function that converts one or more input elements into a single output element.
/// </summary>
/// <typeparam name="TInput">The type of the input elements.</typeparam>
/// <typeparam name="TOutput">The type of the produced output.</typeparam>
/// <param name="source">The source of the input elements.</param>
/// <param name="start">The starting offset of the first input element.</param>
/// <param name="length">The number of input elements successfully scanned.</param>
@FunctionalInterface
public interface SourceProducer<TInput, TOutput> // PORT: §3.8 custom delegate -> @FunctionalInterface; Invoke -> invoke
{
    TOutput invoke(Source<TInput> source, int start, int length);
}
