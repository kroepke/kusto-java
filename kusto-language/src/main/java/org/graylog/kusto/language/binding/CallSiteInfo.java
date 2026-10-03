// Ported from: src/Kusto.Language/Binder/CallSiteInfo.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.binding;

import java.util.List;
import java.util.Objects;

import org.graylog.kusto.language.symbols.Parameter;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// Represents the call site info needed used to identify a unique function call.
/// </summary>
@Internal
public class CallSiteInfo
{
    private final Signature signature;

    /// <summary>
    /// The signature of the function being called.
    /// </summary>
    public Signature signature() { return this.signature; }

    private final List<Parameter> parameters;

    /// <summary>
    /// The parameters that make this call site unique.
    /// </summary>
    public List<Parameter> parameters() { return this.parameters; }

    private final List<Object> values;

    /// <summary>
    /// The list of values corresponding to the parameters.
    /// </summary>
    public List<Object> values() { return this.values; }

    public CallSiteInfo(
        Signature signature,
        List<Parameter> parameters,
        List<Object> values)
    {
        this.signature = signature;
        this.parameters = parameters != null ? parameters : EmptyReadOnlyList.<Parameter>instance(); // PORT: §3.14 ??
        this.values = values != null ? values : EmptyReadOnlyList.<Object>instance(); // PORT: §3.14 ??
        // PORT: §3.20 Debug.Assert(this.parameters.Count == this.values.Count, "parameter/values count mismatch") dropped (Release oracle)
    }

    @Override
    public String toString()
    {
        // PORT: §3.6 string.Join(",", Enumerable.Range(0, Count).Select(i => $"{Parameters[i].Name}={Values[i]}"))
        var parts = new java.util.ArrayList<String>();
        for (int i = 0; i < this.values.size(); i++)
        {
            parts.add(DotNet.str(this.parameters.get(i).name()) + "=" + DotNet.str(this.values.get(i)));
        }

        return DotNet.str(this.signature.symbol().name()) + "("
            + DotNetStrings.join(",", parts)
            + ")";
    }

    // PORT: §3.8 IEquatable<CallSiteInfo>.Equals(CallSiteInfo) is what Dictionary/MRU caches use -> equals(Object)
    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof CallSiteInfo other))
            return false;

        if (this.signature != other.signature)
            return false;

        if (this.parameters.size() != other.parameters.size())
            return false;

        for (int i = 0; i < this.parameters.size(); i++)
        {
            if (this.parameters.get(i) != other.parameters.get(i))
                return false;
        }

        for (int i = 0; i < this.values.size(); i++)
        {
            var vx = this.values.get(i);
            var vy = other.values.get(i);

            if (vx instanceof TableSymbol tx && vy instanceof TableSymbol ty)
            {
                if (!TableSymbol.areResultEquivalent(tx, ty))
                    return false;
            }
            else
            {
                if (!Objects.equals(vx, vy))
                    return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return this.signature.hashCode();
    }
}
