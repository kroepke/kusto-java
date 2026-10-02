// Ported from: src/Kusto.Language/Parser/Combinators/Parsers.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

// PORT: §3.2 struct -> record; properties Element/Separator -> components element()/separator()
public record ElementAndSeparator<TElement, TSeparator>(TElement element, TSeparator separator)
{
    public ElementAndSeparator
    {
        if (element == null)
            throw new NullPointerException("element" /* nameof */); // PORT: §3.16 ArgumentNullException
    }

    public ElementAndSeparator(TElement element) // PORT: §3.12 optional parameter separator = default(TSeparator)
    {
        this(element, null);
    }
}
