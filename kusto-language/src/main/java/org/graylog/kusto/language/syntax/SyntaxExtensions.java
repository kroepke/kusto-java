// Ported from: src/Kusto.Language/Syntax/SyntaxExtensions.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

import java.util.Objects;

import org.graylog.kusto.language.QueryOperatorParameter;
import org.graylog.kusto.language.utils.dotnet.Linq;

public final class SyntaxExtensions
{
    private SyntaxExtensions() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// Gets the first <see cref="NamedParameter"/> that matches the <see cref="QueryOperatorParameter"/> definition
    /// </summary>
    public static NamedParameter getParameter(SyntaxList1<NamedParameter> list, QueryOperatorParameter parameter) // PORT: §3.5
    {
        return Linq.firstOrDefault(list, np -> Objects.equals(np.name().simpleName(), parameter.name()) || (parameter.aliases().size() > 0 && parameter.aliases().contains(np.name().simpleName()))); // PORT: §3.6, §3.14
    }

    /// <summary>
    /// Gets literal value of the named parameter, or the default value if the parameter is not in the list or the value is not a literal of the correct type.
    /// </summary>
    public static <TValue> TValue getParameterLiteralValue(SyntaxList1<NamedParameter> list, Class<TValue> valueType, QueryOperatorParameter declaration, TValue defaultValue) // PORT: §3.5, §3.10 Class<TValue> follows the receiver
    {
        if (getParameter(list, declaration) instanceof NamedParameter parameter // PORT: §3.5
            && parameter.expression() instanceof LiteralExpression lit
            && valueType.isInstance(lit.constantValue())) // PORT: §3.10 'is TValue value'
        {
            TValue value = valueType.cast(lit.constantValue());
            return value;
        }

        return defaultValue;
    }

    public static <TValue> TValue getParameterLiteralValue(SyntaxList1<NamedParameter> list, Class<TValue> valueType, QueryOperatorParameter declaration) // PORT: §3.12
    {
        return getParameterLiteralValue(list, valueType, declaration, null); // PORT: §3.10 default(TValue) is null for reference types
    }

    /// <summary>
    /// Gets name value of the query operator parameter, or null if the parameter is not present in the list or does not contains a name as a value.
    /// </summary>
    public static String getParameterNameValue(SyntaxList1<NamedParameter> list, QueryOperatorParameter declaration) // PORT: §3.5
    {
        if (getParameter(list, declaration) instanceof NamedParameter parameter) // PORT: §3.5
        {
            switch (parameter.expression()) // PORT: §3.15 pattern switch
            {
                case NameDeclaration nd:
                    return nd.name().simpleName();
                case NameReference nr:
                    return nr.name().simpleName();
                case LiteralExpression le:
                    if (le.kind() == SyntaxKind.StringLiteralExpression
                        || le.kind() == SyntaxKind.TokenLiteralExpression)
                    {
                        var info = le.literalValueInfo(); // PORT: §3.14
                        return info != null ? info.valueText() : null;
                    }
                    break;
                case CompoundStringLiteralExpression cs:
                    var csInfo = cs.literalValueInfo(); // PORT: §3.14
                    return csInfo != null ? csInfo.valueText() : null;
                case null, default:
                    break;
            }
        }

        return null;
    }
}
