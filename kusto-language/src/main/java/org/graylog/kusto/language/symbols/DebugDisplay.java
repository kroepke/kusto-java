// Ported from: src/Kusto.Language/Symbols/DebugDisplay.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import org.graylog.kusto.language.KustoFacts;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// Generates the debug text for the <see cref="Symbol"/>
/// </summary>
@Internal
public final class DebugDisplay
{
    private DebugDisplay() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// Returns the debug text for the <see cref="Symbol"/>.
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
            return DotNet.str(c.name()) + ": " + DotNet.str(getText(c.type())); // PORT: §3.14 interpolation
        }
        else if (symbol instanceof TableSymbol t)
        {
            return DotNet.str(t.name()) + ": (" + DotNetStrings.join(", ", Linq.select(t.columns(), m -> getText(m))) + ")"; // PORT: §3.6; §3.14
        }
        else if (symbol instanceof DatabaseSymbol d)
        {
            return KustoFacts.getSingleQuotedStringLiteral(!DotNetStrings.isNullOrEmpty(d.alternateName()) ? d.alternateName() : d.name());
        }
        else if (symbol instanceof ClusterSymbol c)
        {
            return KustoFacts.getSingleQuotedStringLiteral(c.name());
        }
        else if (symbol instanceof GroupSymbol g)
        {
            return "[" + DotNetStrings.join(", ", Linq.select(g.members(), s -> getText(s))) + "]"; // PORT: §3.6; §3.14
        }
        else if (symbol instanceof FunctionSymbol f)
        {
            return DotNet.str(f.name()) + " = " + DotNet.str(getText(f.signatures().get(0))); // PORT: §3.14 interpolation
        }
        else if (symbol instanceof ParameterSymbol p)
        {
            return DotNet.str(p.name()) + ": " + DotNet.str(getText(p.type())); // PORT: §3.14 interpolation
        }
        else if (symbol instanceof GraphSymbol g)
        {
            if (g.nodeShape() != null)
            {
                return DotNet.str(g.name()) + ": [Edge" + DotNet.str(getText(g.edgeShape())) + ", Node" + DotNet.str(getText(g.nodeShape())) + "]"; // PORT: §3.14 interpolation
            }
            else
            {
                return DotNet.str(g.name()) + ": [Edge" + DotNet.str(getText(g.edgeShape())) + "]"; // PORT: §3.14 interpolation
            }
        }
        else if (symbol instanceof EntityGroupSymbol e)
        {
            if (e.definition() != null)
            {
                return DotNet.str(e.name()) + ": " + DotNet.str(e.definition()); // PORT: §3.14 interpolation
            }
            else
            {
                return DotNet.str(e.name()) + ": " + DotNetStrings.join(", ", Linq.select(e.members(), m -> getText(m))); // PORT: §3.6; §3.14
            }
        }
        else if (symbol instanceof PatternSymbol pat)
        {
            return DotNet.str(pat.name()) + ": (" + DotNetStrings.join(", ", Linq.select(pat.parameters(), p -> getText(p))) + ")"; // PORT: §3.6; §3.14
        }
        else if (symbol instanceof VariableSymbol v)
        {
            return DotNet.str(v.name()) + " = " + DotNet.str(getText(v.type())); // PORT: §3.14 interpolation
        }

        else if (symbol instanceof TupleSymbol t)
        {
            return "[" + DotNetStrings.join(", ", Linq.select(t.members(), m -> getText(m))) + "]"; // PORT: §3.6; §3.14
        }
        else if (symbol instanceof PrimitiveSymbol prv)
        {
            return prv.name();
        }
        else if (symbol instanceof DynamicAnySymbol)
        {
            return "dynamic";
        }
        else if (symbol instanceof DynamicPrimitiveSymbol d)
        {
            return "dynamic(" + DotNet.str(getText(d.underlyingType())) + ")"; // PORT: §3.14 interpolation
        }
        else if (symbol instanceof DynamicArraySymbol d)
        {
            return d.elementType() == ScalarTypes.Dynamic
                ? "dynamic([])"
                : "dynamic([" + DotNet.str(getText(d.elementType())) + "])"; // PORT: §3.14 interpolation
        }
        else if (symbol instanceof DynamicBagSymbol d)
        {
            return d.properties().size() == 0
                ? "dynamic({})"
                : "dynamic({" + DotNetStrings.join(", ", Linq.select(d.properties(), p -> getText(p))) + "})"; // PORT: §3.6; §3.14
        }
        else
        {
            return symbol.name();
        }
    }

    /// <summary>
    /// Returns the debug text for the parameter.
    /// </summary>
    public static String getText(Parameter parameter)
    {
        return parameter.typeKind() == ParameterTypeKind.Declared
            ? DotNet.str(parameter.name()) + ": " + DotNetStrings.join("|", Linq.select(parameter.declaredTypes(), t -> getText(t))) // PORT: §3.6; §3.14
            : DotNet.str(parameter.name()) + ": <" + DotNet.str(parameter.typeKind()) + ">"; // PORT: §3.14 enum ToString → name
    }

    /// <summary>
    /// Returns the debug text for the <see cref="Signature"/>
    /// </summary>
    public static String getText(Signature sig, boolean includeSymbolName, boolean verbose)
    {
        var builder = new StringBuilder();

        for (int i = 0; i < sig.parameters().size(); i++)
        {
            var p = sig.parameters().get(i);

            if (i > 0)
            {
                builder.append(", ");
            }

            if (p.isOptional())
            {
                // everything after this must be optional too, so just denote the entire section as optional.
                builder.append("[");
                builder.append(DotNet.str(getText(sig, p, verbose))); // PORT: §3.14
                builder.append("]");
            }
            else
            {
                builder.append(DotNet.str(getText(sig, p, verbose))); // PORT: §3.14
            }
        }

        if (sig.hasRepeatableParameters())
        {
            builder.append(", ...");
        }

        var prms = builder.toString();

        if (includeSymbolName && !DotNetStrings.isNullOrEmpty(sig.symbol().name()))
        {
            return DotNet.str(sig.symbol().name()) + "(" + prms + ")"; // PORT: §3.14 interpolation
        }
        else
        {
            return "(" + prms + ")";
        }
    }

    public static String getText(Signature sig, boolean includeSymbolName) // PORT: §3.12 verbose = false
    {
        return getText(sig, includeSymbolName, false);
    }

    public static String getText(Signature sig) // PORT: §3.12 includeSymbolName = false, verbose = false
    {
        return getText(sig, false, false);
    }

    /// <summary>
    /// Returns the debug text for the signature+parameter combination
    /// </summary>
    private static String getText(Signature signature, Parameter parameter, boolean verbose)
    {
        if (verbose)
        {
            var typeText = getTypeText(signature, parameter);

            if (!DotNetStrings.isNullOrEmpty(typeText))
            {
                return DotNet.str(parameter.name()) + ": " + DotNet.str(typeText); // PORT: §3.14 interpolation
            }
        }

        return parameter.name();
    }

    private static String getTypeText(Signature signature, Parameter parameter)
    {
        switch (parameter.typeKind())
        {
            case Declared:
                return DotNetStrings.join("|", Linq.select(parameter.declaredTypes(), t -> getText(t))); // PORT: §3.6; §3.14
            case Integer:
                return "integer";
            case IntegerOrArray:
                return "integer|array";
            case DynamicArray:
                return "array|dynamic";
            case DynamicBag:
                return "bag|dynamic";
            case Number:
                return "number";
            case NumberOrBool:
                return "number|bool";
            case RealOrDecimal:
                return "real|decimal";
            case Summable:
                return "summable";
            case StringOrDynamic:
                return "string|dynamic";
            case StringOrArray:
                return "string|array";
            case Parameter0:
                return getTypeText(signature, signature.parameters().get(0));
            case Parameter1:
                return getTypeText(signature, signature.parameters().get(1));
            case Parameter2:
                return getTypeText(signature, signature.parameters().get(2));
            case Tabular:
                return "()";
            case Cluster:
                return "cluster";
            case Database:
                return "database";
            case Scalar:
            default:
                return "scalar";
        }
    }
}
