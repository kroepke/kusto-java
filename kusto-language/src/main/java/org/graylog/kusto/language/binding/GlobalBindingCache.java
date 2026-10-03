// Ported from: src/Kusto.Language/Binder/GlobalBindingCache.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.binding;

import java.util.List;

import org.graylog.kusto.language.FunctionBodyFacts;
import org.graylog.kusto.language.FunctionCallExpansion;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.utils.MostRecentlyUsedCache;
import org.graylog.kusto.language.utils.ReadOnlyListComparer;
import org.graylog.kusto.language.utils.ThreadSafeDictionary;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// Binding state that persists across multiple bindings (lifetime of <see cref="KustoCache"/>)
/// </summary>
@Internal
public class GlobalBindingCache
{
    @Internal
    public final ThreadSafeDictionary<List<TableSymbol>, TableSymbol> UnifiedNameColumnsMap =
        new ThreadSafeDictionary<List<TableSymbol>, TableSymbol>(ReadOnlyListComparer.<TableSymbol>defaultComparer()); // PORT: §3.9

    @Internal
    public final ThreadSafeDictionary<List<TableSymbol>, TableSymbol> UnifiedNameAndTypeColumnsMap =
        new ThreadSafeDictionary<List<TableSymbol>, TableSymbol>(ReadOnlyListComparer.<TableSymbol>defaultComparer()); // PORT: §3.9

    @Internal
    public final ThreadSafeDictionary<List<TableSymbol>, TableSymbol> CommonColumnsMap =
        new ThreadSafeDictionary<List<TableSymbol>, TableSymbol>(ReadOnlyListComparer.<TableSymbol>defaultComparer()); // PORT: §3.9

    @Internal
    public ThreadSafeDictionary<Signature, MostRecentlyUsedCache<CallSiteInfo, FunctionCallExpansion>> CallSiteToExpansionMap =
        new ThreadSafeDictionary<Signature, MostRecentlyUsedCache<CallSiteInfo, FunctionCallExpansion>>();

    @Internal
    public ThreadSafeDictionary<Signature, MostRecentlyUsedCache<CallSiteInfo, TypeSymbol>> CallSiteToResultTypeMap =
        new ThreadSafeDictionary<Signature, MostRecentlyUsedCache<CallSiteInfo, TypeSymbol>>();

    @Internal
    public final ThreadSafeDictionary<Signature, FunctionBodyFacts> DatabaseFunctionBodyFacts =
        new ThreadSafeDictionary<Signature, FunctionBodyFacts>();
}
