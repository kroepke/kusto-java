// Ported from: src/Kusto.Language/Binder/LocalBindingCache.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W6

package org.graylog.kusto.language.binding;

import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// Binding state that exists for the duration of the binder.
/// </summary>
@Internal
public class LocalBindingCache
{
    // PORT-PENDING: W6 fields (SignaturesComputingExpansion, CallSiteToExpansionMap, CallSiteToResultTypeMap,
    // NonDatabaseFunctionBodyFacts, FunctionExpansionCounts); KustoCode only needs the default constructor.
}
