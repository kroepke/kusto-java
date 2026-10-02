// Ported from: src/Kusto.Language/Symbols/VariableSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W1

package org.graylog.kusto.language.symbols;

import org.graylog.kusto.language.syntax.ValueInfo;

public final class VariableSymbol extends Symbol
{
    public boolean isConstant() // PORT-PENDING: W1
    {
        throw new UnsupportedOperationException("PORT-PENDING: W1");
    }

    public ValueInfo constantValueInfo() // PORT-PENDING: W1
    {
        throw new UnsupportedOperationException("PORT-PENDING: W1");
    }
}
