// Ported from: src/Kusto.Language/Parser/QueryParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public enum AllowedNameKind
{
    /// <summary>
    /// Allow either declared or known query operator names.
    /// </summary>
    DeclaredOrKnown,

    /// <summary>
    /// Allow any known query operator parameter name as a possible name.
    /// </summary>
    KnownOnly,

    /// <summary>
    /// Allow only query operator parameter names specifically declared for the operator
    /// </summary>
    DeclaredOnly
}
