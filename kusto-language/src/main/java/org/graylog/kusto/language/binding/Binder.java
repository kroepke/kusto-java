// Ported from: src/Kusto.Language/Binder/Binder_API.cs
// Ported from: src/Kusto.Language/Binder/Binder_AsContextBuilder.cs
// Ported from: src/Kusto.Language/Binder/Binder_ContextBuilder.cs
// Ported from: src/Kusto.Language/Binder/Binder_FunctionCalls.cs
// Ported from: src/Kusto.Language/Binder/Binder_Misc.cs
// Ported from: src/Kusto.Language/Binder/Binder_Names.cs
// Ported from: src/Kusto.Language/Binder/Binder_NodeBinder.cs
// Ported from: src/Kusto.Language/Binder/Binder_Operators.cs
// Ported from: src/Kusto.Language/Binder/Binder_Projection.cs
// Ported from: src/Kusto.Language/Binder/Binder_SearchPredicateBinder.cs
// Ported from: src/Kusto.Language/Binder/Binder_TablesAndColumns.cs
// Ported from: src/Kusto.Language/Binder/Binder_TreeBinder.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W6

package org.graylog.kusto.language.binding;

import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public final class Binder
{
    // ===== upstream part: Binder_API.cs =====

    @Internal
    public static void defaultSetSemanticInfo(SyntaxNode node, SemanticInfo info) // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }
}
