// Ported from: src/Kusto.Language/QueryOperatorParameters.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W3

package org.graylog.kusto.language;

import java.util.List;

import org.graylog.kusto.language.symbols.Symbol;

/// <summary>
/// Describes a parameter that a query operator may have.
/// </summary>
public class QueryOperatorParameter extends Symbol
{
    protected QueryOperatorParameter(String name) // PORT-PENDING: W3
    {
        super(name);
        throw new UnsupportedOperationException("PORT-PENDING: W3");
    }

    public List<String> aliases() // PORT-PENDING: W3
    {
        throw new UnsupportedOperationException("PORT-PENDING: W3");
    }
}
