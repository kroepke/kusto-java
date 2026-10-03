// Ported from: src/Kusto.Language/Binder/LocalBindingCache.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.binding;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import org.graylog.kusto.language.FunctionBodyFacts;
import org.graylog.kusto.language.FunctionCallExpansion;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// Binding state that exists for the duration of the binder.
/// </summary>
@Internal
public class LocalBindingCache
{
    @Internal
    public final LinkedHashSet<Signature> SignaturesComputingExpansion // PORT: §3.17 HashSet -> LinkedHashSet
        = new LinkedHashSet<Signature>();

    @Internal
    public LinkedHashMap<CallSiteInfo, FunctionCallExpansion> CallSiteToExpansionMap = // PORT: §3.17 Dictionary -> LinkedHashMap
        new LinkedHashMap<CallSiteInfo, FunctionCallExpansion>();

    @Internal
    public LinkedHashMap<CallSiteInfo, TypeSymbol> CallSiteToResultTypeMap = // PORT: §3.17
        new LinkedHashMap<CallSiteInfo, TypeSymbol>();

    @Internal
    public final LinkedHashMap<Signature, FunctionBodyFacts> NonDatabaseFunctionBodyFacts = // PORT: §3.17
        new LinkedHashMap<Signature, FunctionBodyFacts>();

    @Internal
    public final LinkedHashMap<Signature, Integer> FunctionExpansionCounts = // PORT: §3.17
        new LinkedHashMap<Signature, Integer>();
}
