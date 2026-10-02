// Ported from: src/Kusto.Language/Symbols/ParameterLayouts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.binding.Binder;
import org.graylog.kusto.language.binding.ParameterMatchKind;
import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.utils.ObjectPool;
import org.graylog.kusto.language.utils.dotnet.IntRef;
import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public class RepeatingParameterLayout extends ParameterLayout
{
    private final boolean _allowSkippingOptionalParameters;

    public RepeatingParameterLayout(boolean allowSkippingOptionalParameters)
    {
        _allowSkippingOptionalParameters = allowSkippingOptionalParameters;
    }

    @Override
    public void getArgumentParameters(Signature signature, List<Expression> arguments, List<Parameter> argumentParameters)
    {
        var argTypes = s_typeListPool.allocateFromPool();
        try
        {
            getArgumentTypes(arguments, argTypes);

            var iCurrentParameter = new IntRef(0); // PORT: §3.3 ref int
            var iCurrentParameterCount = new IntRef(0); // PORT: §3.3 ref int
            getArgumentParameters(signature, arguments, argTypes, argumentParameters, iCurrentParameter, iCurrentParameterCount);
        }
        finally
        {
            s_typeListPool.returnToPool(argTypes);
        }
    }

    @Override
    public void getArgumentParametersForTypes(Signature signature, List<TypeSymbol> argumentTypes, List<Parameter> argumentParameters) // PORT: §2.5
    {
        var iCurrentParameter = new IntRef(0); // PORT: §3.3 ref int
        var iCurrentParameterCount = new IntRef(0); // PORT: §3.3 ref int
        getArgumentParameters(signature, null, argumentTypes, argumentParameters, iCurrentParameter, iCurrentParameterCount);
    }

    private void getArgumentParameters(Signature signature, List<Expression> arguments, List<TypeSymbol> argumentTypes, List<Parameter> argumentParameters,
        IntRef iCurrentParameter, IntRef iCurrentParameterCount) // PORT: §3.3 ref int → IntRef
    {
        for (int iArg = 0; iArg < argumentTypes.size(); iArg++)
        {
            if (iArg >= signature.maxArgumentCount())
            {
                argumentParameters.add(Signature.UnknownParameter);
                continue;
            }

            var currentParam = signature.parameters().get(iCurrentParameter.value);

            // if we already have the max number of current parameters, use next parameter
            if (iCurrentParameter.value < signature.parameters().size() - 1
                && iCurrentParameterCount.value >= currentParam.maxOccurring())
            {
                iCurrentParameter.value++;
                iCurrentParameterCount.value = 0;
                currentParam = signature.parameters().get(iCurrentParameter.value);
            }

            if (iCurrentParameterCount.value < currentParam.minOccurring()
                || (iCurrentParameterCount.value == 0 && currentParam.minOccurring() == 0 && !_allowSkippingOptionalParameters))
            {
                // based on min occurring, current parameter is still required
                argumentParameters.add(currentParam);
                iCurrentParameterCount.value++;
            }
            else if (iCurrentParameter.value == signature.parameters().size() - 1)
            {
                if (iCurrentParameterCount.value < currentParam.maxOccurring())
                {
                    argumentParameters.add(currentParam);
                    iCurrentParameterCount.value++;
                }
                else
                {
                    argumentParameters.add(Signature.UnknownParameter);
                }
            }
            else
            {
                // otherwise compare to next parameter to see which is better
                var arg = arguments != null ? arguments.get(iArg) : null;
                var argType = argumentTypes.get(iArg);
                var currentMatch = Binder.getParameterMatchKind(
                    signature, argumentParameters, argumentTypes, currentParam, arg, argType, /*allowImplicitArgumentCoercion:*/ false); // PORT: §3.12

                var iNextParameter = iCurrentParameter.value + 1;
                while (iNextParameter < signature.parameters().size())
                {
                    var nextParam = signature.parameters().get(iNextParameter);
                    var nextMatch = Binder.getParameterMatchKind(
                        signature, argumentParameters, argumentTypes, nextParam, arg, argType, /*allowImplicitArgumentCoercion:*/ false); // PORT: §3.12

                    if (currentMatch.compareTo(nextMatch) >= 0 && currentMatch != ParameterMatchKind.None) // PORT: §3.17 enum >= compares declaration order
                    {
                        // current is better
                        argumentParameters.add(currentParam);
                        iCurrentParameterCount.value++;
                        break;
                    }
                    else if (nextMatch != ParameterMatchKind.None)
                    {
                        // next is better
                        argumentParameters.add(nextParam);
                        iCurrentParameter.value = iNextParameter;
                        iCurrentParameterCount.value = 1;
                        break;
                    }
                    else if (nextParam.minOccurring() == 0
                        && _allowSkippingOptionalParameters
                        && iNextParameter < signature.parameters().size() - 1)
                    {
                        // next parameter and its optional, so try one after that
                        iNextParameter++;
                        continue;
                    }
                    else
                    {
                        // no parameter is a match.. use the current one
                        argumentParameters.add(currentParam);
                        iCurrentParameterCount.value++;
                        break;
                    }
                }
            }
        }
    }

    private static void getArgumentTypes(List<Expression> arguments, List<TypeSymbol> types)
    {
        for (var arg : arguments)
        {
            types.add(arg.resultType());
        }
    }

    private static final ObjectPool<List<TypeSymbol>> s_typeListPool =
        new ObjectPool<List<TypeSymbol>>(() -> new ArrayList<TypeSymbol>(), list -> list.clear());

    private static final ObjectPool<List<Parameter>> s_parameterListPool =
        new ObjectPool<List<Parameter>>(() -> new ArrayList<Parameter>(), list -> list.clear());

    @Override
    public void getNextPossibleParameters(Signature signature, List<Expression> arguments, List<Parameter> possibleParameters)
    {
        var existingArgumentParameters = s_parameterListPool.allocateFromPool();
        var argumentTypes = s_typeListPool.allocateFromPool();
        try
        {
            getArgumentTypes(arguments, argumentTypes);

            var iCurrentParameter = new IntRef(0); // PORT: §3.3 ref int
            var iCurrentParameterCount = new IntRef(0); // PORT: §3.3 ref int
            getArgumentParameters(signature, arguments, argumentTypes, existingArgumentParameters, iCurrentParameter, iCurrentParameterCount);

            if (arguments.size() >= signature.maxArgumentCount())
            {
                // beyond all possible args, nothing to suggest!
                return;
            }

            var currentParam = signature.parameters().get(iCurrentParameter.value);

            // if we already have the max number of current parameters, use next parameter
            if (iCurrentParameter.value < signature.parameters().size() - 1
                && iCurrentParameterCount.value >= currentParam.maxOccurring())
            {
                iCurrentParameter.value++;
                iCurrentParameterCount.value = 0;
                currentParam = signature.parameters().get(iCurrentParameter.value);
            }

            if (iCurrentParameterCount.value < currentParam.minOccurring())
            {
                // based on min occurring, current parameter is still required
                possibleParameters.add(currentParam);
                iCurrentParameterCount.value++;
            }
            else if (iCurrentParameter.value == signature.parameters().size() - 1)
            {
                // last possible parameter, so this must be it (if we've not already used it up)
                if (iCurrentParameterCount.value < currentParam.maxOccurring())
                {
                    possibleParameters.add(currentParam);
                    iCurrentParameterCount.value++;
                }
            }
            else
            {
                possibleParameters.add(currentParam);

                if (iCurrentParameterCount.value > 0 || _allowSkippingOptionalParameters)
                {
                    // add all parameters that might occur next
                    var iNextParameter = iCurrentParameter.value + 1;
                    while (iNextParameter < signature.parameters().size())
                    {
                        var nextParam = signature.parameters().get(iNextParameter);
                        possibleParameters.add(nextParam);

                        if (nextParam.minOccurring() == 0
                            && _allowSkippingOptionalParameters
                            && iNextParameter < signature.parameters().size() - 1)
                        {
                            // next parameter and its optional, so try one after that
                            iNextParameter++;
                            continue;
                        }
                        else
                        {
                            break;
                        }
                    }
                }
            }
        }
        finally
        {
            s_typeListPool.returnToPool(argumentTypes);
            s_parameterListPool.returnToPool(existingArgumentParameters);
        }
    }
}
