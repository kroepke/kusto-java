// Ported from: src/Kusto.Language/Syntax/SyntaxNode_Semantics.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

// [Flags]
public final class DiagnosticsInclude // PORT: §3.17 [Flags] enum → int constants holder (D23)
{
    private DiagnosticsInclude() // PORT: §3.17
    {
    }

    public static final int Syntactic  = 0b0001;
    public static final int Semantic   = 0b0010;
    public static final int Expansion  = 0b0100;
}
