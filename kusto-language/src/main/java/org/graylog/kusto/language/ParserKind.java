// Ported from: src/Kusto.Language/KustoCode.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

public enum ParserKind
{
    /// <summary>
    /// Use the grammar parser combinators to parse queries.
    /// </summary>
    Grammar,

    /// <summary>
    /// Use the faster query parser.
    /// </summary>
    Default
}
