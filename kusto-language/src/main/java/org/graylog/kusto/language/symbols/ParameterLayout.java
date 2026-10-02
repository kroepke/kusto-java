// Ported from: src/Kusto.Language/Symbols/ParameterLayout.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.FakeExpression;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.ObjectPool;

public abstract class ParameterLayout
{
    /// <summary>
    /// Gets the parameters that correspond to the set of arguments for the specified signature.
    /// </summary>
    public abstract void getArgumentParameters(Signature signature, List<Expression> arguments, List<Parameter> argumentParameters);

    /// <summary>
    /// Gets the parameters corresponding to the set of argument types for the specified signature.
    /// </summary>
    public void getArgumentParametersForTypes(Signature signature, List<TypeSymbol> argumentTypes, List<Parameter> argumentParameters) // PORT: §2.5 GetArgumentParameters(…, IReadOnlyList<TypeSymbol>, …)
    {
        // default implementation makes a fake set of arguments corresponding to the specified types
        var arguments = s_expressionListPool.allocateFromPool();
        try
        {
            getFakeExpressions(argumentTypes, arguments);
            getArgumentParameters(signature, arguments, argumentParameters);
        }
        finally
        {
            s_expressionListPool.returnToPool(arguments);
        }
    }

    /// <summary>
    /// Gets the set of possible parameters for an argument that would follow after the specified set of existing arguments.
    /// </summary>
    public void getNextPossibleParameters(Signature signature, List<Expression> arguments, List<Parameter> possibleParameters)
    {
        // default implementation asks for the layout given an additional parameter with type Unknown
        var newArguments = s_expressionListPool.allocateFromPool();
        var argumentParameters = s_parameterListPool.allocateFromPool();
        try
        {
            // add fake argument w/ unknown type to end of arguments
            newArguments.addAll(arguments);
            newArguments.add(FakeExpression.create(ScalarTypes.Unknown));

            // try to get parameter layout for this extended list of arguments
            getArgumentParameters(signature, newArguments, argumentParameters);

            // use next parameter in this layout
            var argIndex = arguments.size();
            var argParam = argumentParameters.get(argIndex);
            possibleParameters.add(argParam);

            // if this is optional, also get next possible parameters too
            if (argParam.minOccurring() == 0)
            {
                // align arg index with first argument using this parameter in this sequence
                while (argIndex > 0 && argumentParameters.get(argIndex - 1) == argParam)
                {
                    argIndex--;
                }

                // if argument is in order with parameter definition also allow next consecutive parameters
                var paramIndex = ListExtensions.indexOf(signature.parameters(), argParam); // PORT: §3.5 Utils IndexOf extension
                if (paramIndex == argIndex && paramIndex < signature.parameters().size() - 1)
                {
                    paramIndex++;
                    Parameter nextParam;
                    do
                    {
                        nextParam = signature.parameters().get(paramIndex);
                        possibleParameters.add(nextParam);
                    }
                    while (nextParam.minOccurring() == 0 && paramIndex < signature.parameters().size() - 1);
                    // PORT-BUG: paramIndex is never advanced inside the loop: when nextParam is optional and not the
                    // last parameter, upstream adds it forever (unbounded loop). Mirrored verbatim. Only
                    // CustomParameterLayout inherits this method (reached from Binder_Misc.cs:1045 and the editor).
                }
            }
        }
        finally
        {
            s_expressionListPool.returnToPool(newArguments);
            s_parameterListPool.returnToPool(argumentParameters);
        }
    }

    public boolean isValidArgumentCount(Signature signature, int argumentCount)
    {
        return argumentCount >= signature.minArgumentCount() && argumentCount <= signature.maxArgumentCount();
    }

    private static final ObjectPool<List<Parameter>> s_parameterListPool =
        new ObjectPool<List<Parameter>>(() -> new ArrayList<Parameter>(), list -> list.clear());

    private static final ObjectPool<List<Expression>> s_expressionListPool =
        new ObjectPool<List<Expression>>(() -> new ArrayList<Expression>(), list -> list.clear());

    private static void getFakeExpressions(List<TypeSymbol> types, List<Expression> expressions)
    {
        for (var type : types)
        {
            expressions.add(FakeExpression.create(type));
        }
    }
}
