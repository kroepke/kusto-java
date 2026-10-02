// Ported from: src/Kusto.Language/Binder/FunctionCallInfo.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W6

package org.graylog.kusto.language.binding;

import java.util.List;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.FunctionBodyFacts;
import org.graylog.kusto.language.FunctionCallExpansion;
import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public class FunctionCallInfo
{
    /// <summary>
    /// The function body facts associated with the called function.
    /// </summary>
    public FunctionBodyFacts facts() // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    /// <summary>
    /// The expansion (analyzed syntax tree in context of call arguments) of the called function.
    /// </summary>
    public FunctionCallExpansion expansion() // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    public boolean hasErrors() // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    public List<Diagnostic> diagnostics() // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }
}
