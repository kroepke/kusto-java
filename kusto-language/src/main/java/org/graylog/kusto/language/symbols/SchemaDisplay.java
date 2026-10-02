// Ported from: src/Kusto.Language/Symbols/SchemaDisplay.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import org.graylog.kusto.language.KustoFacts;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// Generates text for symbols that can be parsed as table schema or function parameters.
/// </summary>
public final class SchemaDisplay
{
    private SchemaDisplay() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// Gets the schema display text for the <see cref="Symbol"/>.
    /// </summary>
    public static String getText(Symbol symbol)
    {
        // PORT: §3.15 type-pattern switch → instanceof chain in upstream case order; `case null` first (§3.14)
        if (symbol == null)
        {
            return "";
        }
        else if (symbol instanceof ColumnSymbol c)
        {
            return DotNet.str(KustoFacts.bracketNameIfNecessary(c.name())) + ": " + DotNet.str(getScalarTypeText(c.type())); // PORT: §3.14 interpolation
        }
        else if (symbol instanceof ParameterSymbol p)
        {
            return DotNet.str(KustoFacts.bracketNameIfNecessary(p.name())) + ": " + DotNet.str(getScalarTypeText(p.type())); // PORT: §3.14 interpolation
        }
        else if (symbol instanceof TableSymbol t)
        {
            return "(" + DotNetStrings.join(", ", Linq.select(t.columns(), m -> getText(m))) + ")"; // PORT: §3.6; §3.14
        }
        else if (symbol instanceof FunctionSymbol f)
        {
            var s = f.signatures().get(0);
            return "(" + DotNetStrings.join(", ", Linq.select(s.parameters(), p -> DotNet.str(KustoFacts.bracketNameIfNecessary(p.name())) + ": " + DotNet.str(getParameterTypeText(p)))) + ")"; // PORT: §3.6; §3.14
        }
        else
        {
            return symbol.name();
        }
    }

    /// <summary>
    /// Returns the type display text for the scalar type symbol.
    /// </summary>
    public static String getScalarTypeText(TypeSymbol type)
    {
        // PORT: §3.15 type-pattern switch → instanceof chain; a null type falls to default, as in C#
        if (type instanceof PrimitiveSymbol p)
        {
            return p.name();
        }
        else if (type instanceof DynamicSymbol)
        {
            return "dynamic";
        }
        else
        {
            return "";
        }
    }

    /// <summary>
    /// Returns the type display text for the parameter's type.
    /// </summary>
    public static String getParameterTypeText(Parameter parameter)
    {
        // this only works for user defined functions
        // since they only have a single declared type per parameter.
        if (parameter.typeKind() == ParameterTypeKind.Declared
            && parameter.declaredTypes().size() == 1)
        {
            var type = parameter.declaredTypes().get(0);
            if (type instanceof TableSymbol t
                && t.columns().size() == 0)
            {
                return "(*)";
            }

            return getText(type);
        }

        // if we get here and don't know the type, then use dynamic
        // since user defined functions auto-convert all arguments.
        return "dynamic";
    }
}
