// Ported from: src/Kusto.Language/KustoCode.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

// [Flags]
public final class IncludeFunctionKind // PORT: §3.17 [Flags] enum → int constants holder (D23)
{
    private IncludeFunctionKind() // PORT: §3.17
    {
    }

    public static final int BuiltInFunctions = 1;
    public static final int DatabaseFunctions = BuiltInFunctions << 1;
    public static final int LocalFunctions = DatabaseFunctions << 1;
    public static final int LocalViews = LocalFunctions << 1;

    public static final int None = 0;
    public static final int All = BuiltInFunctions | DatabaseFunctions | LocalFunctions | LocalViews;
}
