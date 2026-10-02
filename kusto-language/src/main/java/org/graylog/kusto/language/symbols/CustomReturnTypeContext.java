// Ported from: src/Kusto.Language/Symbols/CustomReturnTypeContext.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.ListExtensions;

/// <summary>
/// A binding context for a function/operator call.
/// </summary>
public abstract class CustomReturnTypeContext
{
    /// <summary>
    /// The location related to the function/operator call.
    /// </summary>
    public SyntaxNode location() { return null; }

    /// <summary>
    /// The arguments provided to the function call.
    /// </summary>
    public List<Expression> arguments() { return null; }

    /// <summary>
    /// The types of the arguments provided to the function call.
    /// </summary>
    public List<TypeSymbol> argumentTypes() { return null; }

    /// <summary>
    /// The <see cref="Parameter"/> associated with each argument provided to the function call.
    /// </summary>
    public List<Parameter> argumentParameters() { return null; }

    /// <summary>
    /// The input table/schema from the left of a pipe operator.
    /// </summary>
    public TableSymbol rowScope() { return null; }

    /// <summary>
    /// The signature of the function being called.
    /// </summary>
    public Signature signature() { return null; }

    /// <summary>
    /// The <see cref="GlobalState"/> in use.
    /// </summary>
    public GlobalState globals() { return null; }

    /// <summary>
    /// The current cluster at the call site.
    /// </summary>
    public ClusterSymbol currentCluster() { return null; }

    /// <summary>
    /// The current database as the call site.
    /// </summary>
    public DatabaseSymbol currentDatabase() { return null; }

    /// <summary>
    /// The function the call site is within.
    /// </summary>
    public FunctionSymbol currentFunction() { return null; }

    /// <summary>
    /// Returns the symbol referenced by the name or null if no such symbol exists in scope.
    /// </summary>
    public abstract Symbol getReferencedSymbol(String name);

    /// <summary>
    /// Returns the result type of the symbol referenced by the name or null if no such symbol exists in scope.
    /// </summary>
    public abstract TypeSymbol getResultType(String name);

    /// <summary>
    /// Gets the column name the expression would have in a projection list.
    /// </summary>
    public abstract String getResultName(Expression expr, String defaultName);

    public String getResultName(Expression expr) // PORT: §3.12 defaultName = ""
    {
        return getResultName(expr, "");
    }

    /// <summary>
    /// Gets the first argument associated with the named parameter, or null if no argument is associated with the specified parameter.
    /// </summary>
    public Expression getArgument(String parameterName)
    {
        var p = this.signature().getParameter(parameterName);
        if (p != null)
        {
            return getArgument(p);
        }

        return null;
    }

    /// <summary>
    /// Gets the first argument associated with the named parameter, or null if no argument is associated with the specified parameter.
    /// </summary>
    public Expression getArgument(Parameter parameter)
    {
        var argIndex = ListExtensions.indexOf(this.argumentParameters(), parameter); // PORT: §3.5 Utils IndexOf extension; Parameter keeps reference equality
        if (argIndex >= 0 && argIndex < this.arguments().size())
        {
            return this.arguments().get(argIndex);
        }

        return null;
    }

    /// <summary>
    /// Gets the arguments for the specified parameter.
    /// </summary>
    public List<Expression> getArguments(String parameterName)
    {
        var parameter = this.signature().getParameter(parameterName);
        if (parameter != null)
        {
            return getArguments(parameter);
        }
        else
        {
            return EmptyReadOnlyList.instance(); // PORT: §3.9
        }
    }

    /// <summary>
    /// Gets the arguments for the specified parameter.
    /// </summary>
    public List<Expression> getArguments(Parameter parameter)
    {
        List<Expression> arguments = null;

        for (int i = 0; i < this.argumentParameters().size(); i++)
        {
            if (this.argumentParameters().get(i) == parameter)
            {
                if (arguments == null)
                    arguments = new ArrayList<Expression>();
                arguments.add(this.arguments().get(i));
            }
        }

        return arguments != null ? arguments : EmptyReadOnlyList.instance(); // PORT: §3.14 ?? ; §3.9
    }
}
