// Ported from: src/Kusto.Language/Symbols/EntityGroupSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.KustoFacts;
import org.graylog.kusto.language.utils.ArgumentCheckers;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// A symbol representing an entity group.
/// </summary>
public final class EntityGroupSymbol extends TypeSymbol
{
    private final String definition;
    public String definition() { return this.definition; }

    private final String description;
    public String description() { return this.description; }

    private final List<Symbol> _members;

    private final Signature signature;

    @Internal
    public Signature signature() { return this.signature; }

    public EntityGroupSymbol(String name, String definition, String description)
    {
        super(name);
        this.definition = definition;
        this.description = description != null ? description : ""; // PORT: §3.14 ??
        this.signature = createSignature(definition);
        this.signature().setSymbol(this);
        _members = EmptyReadOnlyList.instance(); // PORT: §3.9 EmptyReadOnlyList<Symbol>.Instance
    }

    public EntityGroupSymbol(String name, String definition) // PORT: §3.12 optional parameter description = null
    {
        this(name, definition, (String) null);
    }

    @SuppressWarnings("unchecked")
    public EntityGroupSymbol(String name, Iterable<? extends Symbol> members, String description)
    {
        super(name);
        this.definition = null;
        this.description = description != null ? description : ""; // PORT: §3.14 ??
        // PORT: §3.10 covariance IEnumerable<Symbol>
        _members = ArgumentCheckers.checkArgumentNullOrElementNull(ListExtensions.toReadOnly((Iterable<Symbol>) members), "members" /* nameof */);
        this.signature = new Signature(this);
        this.signature().setSymbol(this);
    }

    public EntityGroupSymbol(String name, Iterable<? extends Symbol> members) // PORT: §3.12 optional parameter description = null
    {
        this(name, members, (String) null);
    }

    public EntityGroupSymbol(String name, Symbol... members)
    {
        this(name, members != null ? Arrays.asList(members) : null, (String) null); // PORT: §3.10 (IEnumerable<Symbol>)members
    }

    public EntityGroupSymbol(Symbol... members)
    {
        this("", members != null ? Arrays.asList(members) : null, (String) null); // PORT: §3.10 (IEnumerable<Symbol>)members
    }

    private static Signature createSignature(String definition)
    {
        var body = getBodyFromDefinition(definition);
        return new Signature(body, Tabularity.Tabular);
    }

    @Internal
    public static String getBodyFromDefinition(String definition)
    {
        if (definition == null)
        {
            return "entity_group []";
        }

        definition = DotNetStrings.trim(definition);

        // already a entity group expression
        if (definition.startsWith("entity_group")) // PORT: §5.4 culture-sensitive StartsWith, ordinal
        {
            return definition;
        }

        String expressionList = definition;

        // remove brackets
        if (definition.startsWith("[") && definition.endsWith("]")) // PORT: §5.4 culture-sensitive StartsWith/EndsWith, ordinal
        {
            expressionList = definition.substring(1, 1 + (definition.length() - 2)); // PORT: §5.4 Substring(start, length)
        }

        expressionList = DotNetStrings.trim(expressionList);

        // get literal value
        if (expressionList.startsWith("\"") || expressionList.startsWith("'")) // PORT: §5.4 culture-sensitive StartsWith, ordinal
        {
            expressionList = KustoFacts.getStringLiteralValue(expressionList);
        }

        return "entity_group [" + DotNet.str(expressionList) + "]"; // PORT: §3.14 interpolation
    }

    @Override
    public List<Symbol> members() { return _members; }

    @Override
    public Tabularity tabularity() { return Tabularity.None; }

    @Override
    public SymbolKind kind() { return SymbolKind.EntityGroup; }

    public static final EntityGroupSymbol Empty = new EntityGroupSymbol();
}
