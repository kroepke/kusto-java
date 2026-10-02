// Ported from: src/Kusto.Language/Symbols/ParameterLayouts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import java.util.List;

import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public class BlockRepeatingParameterLayout extends ParameterLayout
{
    @Override
    public void getArgumentParameters(Signature signature, List<Expression> arguments, List<Parameter> argumentParameters)
    {
        if (signature.hasRepeatableParameters())
        {
            getArgumentParameters(signature, arguments.size(), argumentParameters);
        }
        else
        {
            NonRepeatingParameterLayout.layoutParameters(signature, arguments.size(), arguments, argumentParameters);
        }
    }

    @Override
    public void getArgumentParametersForTypes(Signature signature, List<TypeSymbol> argumentTypes, List<Parameter> argumentParameters) // PORT: §2.5
    {
        if (signature.hasRepeatableParameters())
        {
            getArgumentParameters(signature, argumentTypes.size(), argumentParameters);
        }
        else
        {
            NonRepeatingParameterLayout.layoutParameters(signature, argumentTypes.size(), null, argumentParameters);
        }
    }

    private void getArgumentParameters(Signature signature, int nArguments, List<Parameter> argumentParameters)
    {
        var firstRepeatableParameter = ListExtensions.firstIndex(signature.parameters(), p -> p.isRepeatable()); // PORT: §3.5
        var lastRepeatableParameter = ListExtensions.lastIndex(signature.parameters(), p -> p.isRepeatable()); // PORT: §3.5

        var firstRepeatingArgument = firstRepeatableParameter;
        var numberOfRepeatingParameters = (lastRepeatableParameter - firstRepeatableParameter + 1);

        var minRepeats = signature.parameters().get(firstRepeatableParameter).minOccurring();
        var maxRepeats = signature.parameters().get(firstRepeatableParameter).maxOccurring();

        var minRepeatingArguments = numberOfRepeatingParameters * minRepeats;
        var maxRepeatingArguments = numberOfRepeatingParameters * maxRepeats;

        var parametersAfterLastRepeatingParameter = signature.parameters().size() - lastRepeatableParameter - 1;
        var possibleRepeatingArguments = (nArguments - firstRepeatingArgument) - parametersAfterLastRepeatingParameter;
        var expectedRepeatingArguments = Math.min(maxRepeatingArguments, Math.max(minRepeatingArguments, possibleRepeatingArguments));

        var repeatingArgumentGroups = (expectedRepeatingArguments + numberOfRepeatingParameters - 1) / numberOfRepeatingParameters;
        var totalRepeatingArguments = repeatingArgumentGroups * numberOfRepeatingParameters;
        var lastRepeatingArgument = firstRepeatableParameter + totalRepeatingArguments - 1;

        for (int i = 0; i < nArguments; i++)
        {
            if (i < firstRepeatingArgument)
            {
                argumentParameters.add(signature.parameters().get(i));
            }
            else if (i >= firstRepeatingArgument && i <= lastRepeatingArgument)
            {
                argumentParameters.add(signature.parameters().get(firstRepeatableParameter + ((i - firstRepeatingArgument) % numberOfRepeatingParameters)));
            }
            else
            {
                var index = lastRepeatableParameter + (i - lastRepeatingArgument);
                if (index < signature.parameters().size())
                {
                    argumentParameters.add(signature.parameters().get(index));
                }
                else
                {
                    argumentParameters.add(Signature.UnknownParameter);
                }
            }
        }
    }

    @Override
    public void getNextPossibleParameters(Signature signature, List<Expression> arguments, List<Parameter> possibleParameters)
    {
        var argumentIndex = arguments.size();

        if (signature.hasRepeatableParameters())
        {
            var firstRepeatableParameter = ListExtensions.firstIndex(signature.parameters(), p -> p.isRepeatable()); // PORT: §3.5
            var lastRepeatableParameter = ListExtensions.lastIndex(signature.parameters(), p -> p.isRepeatable()); // PORT: §3.5

            if (argumentIndex < firstRepeatableParameter)
            {
                // argument occurs within the fixed parameters before the start of the repeating block
                possibleParameters.add(signature.parameters().get(argumentIndex));
            }
            else
            {
                var nRepeatable = lastRepeatableParameter - firstRepeatableParameter + 1;
                var minOccurring = signature.parameters().get(firstRepeatableParameter).minOccurring();
                var maxOccurring = signature.parameters().get(firstRepeatableParameter).maxOccurring();

                var repeatGroup = (argumentIndex - firstRepeatableParameter) / nRepeatable;
                var repeatOffset = ((argumentIndex - firstRepeatableParameter) % nRepeatable);
                if (repeatGroup < maxOccurring)
                {
                    // if less than maxOccurring groups have repeated, then this argument position
                    // may either belong to one of the repeating parameters or one of the fixed parameters that follow
                    var iRepeatableParam = repeatOffset + firstRepeatableParameter;
                    possibleParameters.add(signature.parameters().get(iRepeatableParam));

                    // only show possible following fixed paramter if we've satisfied at least the minimum repeats
                    if (repeatGroup >= minOccurring)
                    {
                        var iEndParam = lastRepeatableParameter + repeatOffset + 1;
                        if (iEndParam < signature.parameters().size())
                        {
                            possibleParameters.add(signature.parameters().get(iEndParam));
                        }
                    }
                }
                else
                {
                    // maximum repeat blocks have occurred, only fixed parameters after block are possible
                    var lastRepeatingArgument = firstRepeatableParameter + nRepeatable * maxOccurring;
                    var iEndParam = (argumentIndex - lastRepeatingArgument) + lastRepeatableParameter + 1;
                    if (iEndParam < signature.parameters().size())
                    {
                        possibleParameters.add(signature.parameters().get(iEndParam));
                    }
                }
            }
        }
        else if (argumentIndex < signature.parameters().size())
        {
            // no repeatable parameters (so just use next parameter only)
            possibleParameters.add(signature.parameters().get(argumentIndex));
        }
    }

    @Override
    public boolean isValidArgumentCount(Signature signature, int argumentCount)
    {
        var isValid = super.isValidArgumentCount(signature, argumentCount);

        if (isValid && signature.hasRepeatableParameters())
        {
            var firstRepeatableParameter = ListExtensions.firstIndex(signature.parameters(), p -> p.isRepeatable()); // PORT: §3.5
            var lastRepeatableParameter = ListExtensions.lastIndex(signature.parameters(), p -> p.isRepeatable()); // PORT: §3.5

            var nVariable = (lastRepeatableParameter - firstRepeatableParameter + 1);
            var nBefore = firstRepeatableParameter;
            var nAfter = signature.parameters().size() - (nBefore + nVariable);
            var nFixed = nBefore + nAfter;

            // argument count must include an even multiple of the repeating parameters
            isValid = (argumentCount - nFixed) % nVariable == 0;
        }

        return isValid;
    }
}
