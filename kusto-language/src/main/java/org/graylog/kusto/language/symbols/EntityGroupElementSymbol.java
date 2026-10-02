// Ported from: src/Kusto.Language/Symbols/EntityGroupElementSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.List;

import org.graylog.kusto.language.utils.Ensure;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// A symbol representing an entity group element.
/// </summary>
public final class EntityGroupElementSymbol extends TypeSymbol
{
    /// <summary>
    /// The associated <see cref="EntityGroupSymbol"/>.
    /// </summary>
    private final EntityGroupSymbol entityGroup;
    public EntityGroupSymbol entityGroup() { return this.entityGroup; }

    /// <summary>
    /// The actual entity symbol within the entity group that is currently being referenced.
    /// </summary>
    private final TypeSymbol underlyingSymbol;
    public TypeSymbol underlyingSymbol() { return this.underlyingSymbol; }

    public EntityGroupElementSymbol(String name, EntityGroupSymbol entityGroup, TypeSymbol underlyingSymbol)
    {
        super(name);
        this.entityGroup = entityGroup != null ? entityGroup : EntityGroupSymbol.Empty; // PORT: §3.14 ??

        if (underlyingSymbol == null)
        {
            // Use the first entity in the group with members, otherwise the first entity.
            // PORT: §3.14 `entityGroup?.Members.OfType<TypeSymbol>().FirstOrDefault(entity => entity.Members.Count > 0) ?? entityGroup?.Members.OfType<TypeSymbol>().FirstOrDefault()`
            if (entityGroup != null)
            {
                underlyingSymbol = Linq.firstOrDefault(Linq.ofType(entityGroup.members(), TypeSymbol.class), entity -> entity.members().size() > 0);
                if (underlyingSymbol == null)
                {
                    underlyingSymbol = Linq.firstOrDefault(Linq.ofType(entityGroup.members(), TypeSymbol.class));
                }
            }
        }
        else
        {
            // if specified in constructor, must be one of symbols in group.
            if (Ensure.ENABLED) // PORT: §3.20 Debug.Assert
            {
                final TypeSymbol specified = underlyingSymbol;
                Ensure.isTrue(Linq.any(entityGroup.members(), m -> m == specified));
            }
        }

        if (underlyingSymbol == null)
            underlyingSymbol = ErrorSymbol.Instance;

        this.underlyingSymbol = underlyingSymbol;
    }

    public EntityGroupElementSymbol(String name, EntityGroupSymbol entityGroup)
    {
        this(name, entityGroup, null);
    }

    public EntityGroupElementSymbol(String name)
    {
        this(name, null, null);
    }

    @Override
    public List<Symbol> members() { return SpecialMembers; }

    @Override
    public Tabularity tabularity() { return this.underlyingSymbol().tabularity(); }

    @Override
    public SymbolKind kind() { return SymbolKind.EntityGroupElement; }

    // PORT: §3.17 IReadOnlyList<Symbol> backed by an array upstream; unmodifiable list here
    public static final List<Symbol> SpecialMembers =
        List.<Symbol>of(
            new VariableSymbol("$current_database", ScalarTypes.String),
            new VariableSymbol("$current_cluster_endpoint", ScalarTypes.String)
        );
}
