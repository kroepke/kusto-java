// Ported from: src/Kusto.Language/Parser/Combinators/Source.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.Out;

public class SourceCache
{
    // cache does not need to be thread-safe since
    // it is only used by a single parser instance.
    private final LinkedHashMap<Class<?>, Object> m_cache = // PORT: §3.17 Dictionary<Type, object>; §3.10 typeof(T) -> Class<T>
        new LinkedHashMap<Class<?>, Object>();

    /// <summary>
    /// Gets or creates a new instance of <see cref="P:T"/>
    /// </summary>
    // PORT: §3.10 where T : new() -> Class<T> + DotNet.newInstance
    public <T> T getOrCreate(Class<T> type)
    {
        Object value;
        if (!m_cache.containsKey(type)) // PORT: §3.3 TryGetValue; the Supplier overload may store null
        {
            value = DotNet.newInstance(type);
            m_cache.put(type, value);
        }
        else
        {
            value = m_cache.get(type);
        }

        return type.cast(value);
    }

    /// <summary>
    /// Gets or creates a new instance of <see cref="P:T"/>
    /// </summary>
    // PORT: §3.10 typeof(T) -> Class<T>; Func<T> -> Supplier<T>
    public <T> T getOrCreate(Class<T> type, Supplier<T> creator)
    {
        Object value;
        if (!m_cache.containsKey(type)) // PORT: §3.3 TryGetValue; creator() may return null
        {
            // PORT-BUG: upstream calls creator() twice and discards the first result (Source.cs:63-64); mirrored.
            var newValue = creator.get();
            value = creator.get();
            m_cache.put(type, value);
        }
        else
        {
            value = m_cache.get(type);
        }

        return type.cast(value);
    }

    /// <summary>
    /// Gets the value associated with the type or returns false.
    /// </summary>
    // PORT: §3.3 out T value -> Out<T>; §3.10 typeof(T) -> Class<T>
    public <T> boolean tryGetValue(Class<T> type, Out<T> value)
    {
        if (m_cache.containsKey(type))
        {
            value.value = type.cast(m_cache.get(type));
            return true;
        }
        else
        {
            value.value = null; // PORT: §3.10 default(T)
            return false;
        }
    }
}
