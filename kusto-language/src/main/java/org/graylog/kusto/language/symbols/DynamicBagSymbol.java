// Ported from: src/Kusto.Language/Symbols/DynamicSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.utils.ArgumentCheckers;
import org.graylog.kusto.language.utils.Interlocked;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Linq;
import org.graylog.kusto.language.utils.dotnet.Out;

/// <summary>
/// A symbol representing a bag of properties (aka json object),
/// stored in a dynamic column.
/// </summary>
public final class DynamicBagSymbol extends DynamicSymbol
{
    private final List<ColumnSymbol> properties;
    public List<ColumnSymbol> properties() { return properties; }

    @Override
    public List<Symbol> members() { return Collections.unmodifiableList(this.properties()); } // PORT: §3.10 covariance

    @Override
    public SymbolKind kind() { return SymbolKind.Bag; }

    @Internal
    public DynamicBagSymbol(Iterable<ColumnSymbol> properties)
    {
        super("dynamic");
        this.properties = ArgumentCheckers.checkArgumentNullOrElementNull(ListExtensions.toReadOnly(properties), "properties" /* nameof */); // PORT: §3.5
    }

    public DynamicBagSymbol(ColumnSymbol... properties)
    {
        this(properties != null ? Arrays.asList(properties) : (Iterable<ColumnSymbol>)null); // PORT: §3.17 array as IEnumerable
    }

    @Override
    public Tabularity tabularity() { return Tabularity.Scalar; }

    private volatile LinkedHashMap<String, ColumnSymbol> _nameToPropertyMap; // PORT: §3.13 CAS-published field is volatile; §3.17 Dictionary → LinkedHashMap

    private static final VarHandle NAME_TO_PROPERTY_MAP = Interlocked.handle(DynamicBagSymbol.class, "_nameToPropertyMap", LinkedHashMap.class); // PORT: §3.13

    /// <summary>
    /// Gets the property with the specified name or null.
    /// </summary>
    public boolean tryGetProperty(String name, Out<ColumnSymbol> property) // PORT: §3.3
    {
        if (_nameToPropertyMap == null)
        {
            var tmp = new LinkedHashMap<String, ColumnSymbol>(); // PORT: §3.17

            for (var prop : this.properties())
            {
                tmp.put(prop.name(), prop);
            }

            Interlocked.compareExchange(NAME_TO_PROPERTY_MAP, this, tmp, null); // PORT: §3.13
        }

        if (name == null)
            throw new NullPointerException("key"); // PORT: §3.16 Dictionary.TryGetValue(null) throws ArgumentNullException

        property.value = _nameToPropertyMap.get(name); // PORT: §3.3 values are never null
        return property.value != null;
    }

    /// <summary>
    /// Returns a new <see cref="DynamicBagSymbol"/> instance with the specfied properties,
    /// if the properties are different than the current set of properties.
    /// </summary>
    public DynamicBagSymbol withProperties(Iterable<ColumnSymbol> properties)
    {
        if (properties != this.properties())
        {
            return new DynamicBagSymbol(properties);
        }
        else
        {
            return this;
        }
    }

    /// <summary>
    /// Returns a <see cref="DynamicBagSymbol"/> with the property added or updated.
    /// </summary>
    public DynamicBagSymbol addOrUpdateProperty(ColumnSymbol property)
    {
        if (property == null)
            return this;

        Out<ColumnSymbol> existingProperty = new Out<>(); // PORT: §3.3
        if (tryGetProperty(property.name(), existingProperty))
        {
            // replace existing property with new property
            // PORT-BUG: every property other than the replaced one becomes existingProperty (DynamicSymbol.cs:146); mirrored verbatim
            return new DynamicBagSymbol(Linq.toList(Linq.select(this.properties(), p -> p == existingProperty.value ? property : existingProperty.value))); // PORT: §3.6
        }
        else
        {
            // add new property
            return new DynamicBagSymbol(Linq.concat(this.properties(), Arrays.asList(new ColumnSymbol[] { property }))); // PORT: §3.6
        }
    }

    /// <summary>
    /// Returns a new <see cref="DynamicBagSymbol"/> instance with all properties
    /// modified to reference the specified source.
    /// </summary>
    public DynamicBagSymbol withSource(SyntaxNode source)
    {
        return new DynamicBagSymbol(Linq.select(this.properties(), p -> p.withSource(source))); // PORT: §3.6
    }

    /// <summary>
    /// A dynamic object symbol with no known schema.
    /// </summary>
    public static DynamicBagSymbol Empty = new DynamicBagSymbol(); // not readonly upstream (DynamicSymbol.cs:167); mirrored as a non-final static
}
