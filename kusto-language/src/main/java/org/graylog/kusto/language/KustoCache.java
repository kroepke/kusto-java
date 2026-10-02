// Ported from: src/Kusto.Language/KustoCache.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

import java.util.function.Supplier;

import org.graylog.kusto.language.utils.ThreadSafeDictionary;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.Out;

public class KustoCache
{
    private final GlobalState globals;
    public GlobalState globals() { return this.globals; }

    // PORT: §3.10 System.Type keys -> Class<?>
    private final ThreadSafeDictionary<Class<?>, Object> m_cache =
        new ThreadSafeDictionary<Class<?>, Object>();

    public KustoCache(GlobalState globals)
    {
        this.globals = globals;
    }

    public KustoCache withGlobals(GlobalState globals)
    {
        if (this.globals == globals)
        {
            return this;
        }
        else
        {
            return new KustoCache(globals);
        }
    }

    /// <summary>
    /// Gets or creates a new instance of <see cref="P:T"/>
    /// </summary>
    @SuppressWarnings("unchecked")
    public <T> T getOrCreate(Class<T> type) // PORT: §3.10 where T : new() -> Class<T> + DotNet.newInstance
    {
        var value = new Out<Object>(); // PORT: §3.3 out parameter
        if (!m_cache.tryGetValue(type, value))
        {
            value.value = m_cache.getOrAdd(type, (Object) DotNet.newInstance(type));
        }

        return (T) value.value;
    }

    /// <summary>
    /// Gets or creates a new instance of <see cref="P:T"/>
    /// </summary>
    @SuppressWarnings("unchecked")
    public <T> T getOrCreate(Class<T> type, Supplier<T> creator) // PORT: §3.10 typeof(T) -> Class<T>
    {
        var value = new Out<Object>(); // PORT: §3.3 out parameter
        if (!m_cache.tryGetValue(type, value))
        {
            T newValue = creator.get(); // PORT-BUG: upstream calls creator() twice and discards this first result (KustoCache.cs:54); mirrored
            value.value = m_cache.getOrAdd(type, k -> (Object) creator.get());
        }

        return (T) value.value;
    }

    /// <summary>
    /// Gets the value associated with the type or returns false.
    /// </summary>
    @SuppressWarnings("unchecked")
    public <T> boolean tryGetValue(Class<T> type, Out<T> value) // PORT: §3.3 out parameter
    {
        var obj = new Out<Object>();
        if (m_cache.tryGetValue(type, obj))
        {
            value.value = (T) obj.value;
            return true;
        }
        else
        {
            value.value = null; // default(T)
            return false;
        }
    }
}
