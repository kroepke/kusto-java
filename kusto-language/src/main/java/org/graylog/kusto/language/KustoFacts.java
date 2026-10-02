// Ported from: src/Kusto.Language/Parser/KustoFacts.cs
// Ported from: src/Kusto.Language/Parser/KustoFacts_Keywords.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W2

package org.graylog.kusto.language;

public final class KustoFacts
{
    private KustoFacts() // PORT: §3.5 static class
    {
    }

    // ===== upstream part: KustoFacts.cs =====

    public static String getBracketedName(String name) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static String getStringLiteralValue(String literal) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    // ===== upstream part: KustoFacts_Keywords.cs =====
}
