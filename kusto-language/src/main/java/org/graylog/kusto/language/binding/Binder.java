// Ported from: src/Kusto.Language/Binder/Binder_API.cs
// Ported from: src/Kusto.Language/Binder/Binder_AsContextBuilder.cs
// Ported from: src/Kusto.Language/Binder/Binder_ContextBuilder.cs
// Ported from: src/Kusto.Language/Binder/Binder_FunctionCalls.cs
// Ported from: src/Kusto.Language/Binder/Binder_Misc.cs
// Ported from: src/Kusto.Language/Binder/Binder_Names.cs
// Ported from: src/Kusto.Language/Binder/Binder_NodeBinder.cs
// Ported from: src/Kusto.Language/Binder/Binder_Operators.cs
// Ported from: src/Kusto.Language/Binder/Binder_Projection.cs
// Ported from: src/Kusto.Language/Binder/Binder_SearchPredicateBinder.cs
// Ported from: src/Kusto.Language/Binder/Binder_TablesAndColumns.cs
// Ported from: src/Kusto.Language/Binder/Binder_TreeBinder.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W6

package org.graylog.kusto.language.binding;

import java.util.List;

import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.Parameter;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.NameAndTypeDeclaration;
import org.graylog.kusto.language.syntax.SeparatedElement1;
import org.graylog.kusto.language.syntax.SyntaxList1;
import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.syntax.TypeExpression;
import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public final class Binder
{
    // ===== upstream part: Binder_API.cs =====

    @Internal
    public static void defaultSetSemanticInfo(SyntaxNode node, SemanticInfo info) // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    /// <summary>
    /// Gets the computed return type for functions specified with a body or declaration.
    /// </summary>
    public static TypeSymbol getComputedReturnType(Signature signature, GlobalState globals, List<TypeSymbol> argumentTypes) // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    public static TypeSymbol getComputedReturnType(Signature signature, GlobalState globals) // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    // ===== upstream part: Binder_FunctionCalls.cs =====

    /// <summary>
    /// Determines the kind of match that the argument has with its corresponding signature parameter.
    /// </summary>
    public static ParameterMatchKind getParameterMatchKind( // PORT-PENDING: W6
        Signature signature,
        List<Parameter> argumentParameters,
        List<TypeSymbol> argumentTypes,
        Parameter parameter,
        Expression argument,
        TypeSymbol argumentType,
        boolean allowImplicitArgumentCoercion)
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    // ===== upstream part: Binder_Misc.cs =====

    @Internal
    public static TypeSymbol getDeclaredType(TypeExpression typeExpression) // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    // ===== upstream part: Binder_TablesAndColumns.cs =====

    @Internal
    public static void unifyColumnsWithSameNameAndType(List<ColumnSymbol> columns) // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    @Internal
    public static void unifyColumnsWithSameName(List<ColumnSymbol> columns) // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    @Internal
    public static void makeColumnNamesUnique(List<ColumnSymbol> columns) // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    public static void createColumnsFromRowSchema(SyntaxList1<SeparatedElement1<NameAndTypeDeclaration>> schemaColumns, List<ColumnSymbol> columns) // PORT-PENDING: W6
    {
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }
}
