// Ported from: src/Kusto.Language/Symbols/ParameterLayouts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import java.util.List;

import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.SimpleNamedExpression;
import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public class NonRepeatingParameterLayout extends ParameterLayout
{
    @Override
    public void getArgumentParameters(Signature signature, List<Expression> arguments, List<Parameter> argumentParameters)
    {
        layoutParameters(signature, arguments.size(), arguments, argumentParameters);
    }

    @Override
    public void getArgumentParametersForTypes(Signature signature, List<TypeSymbol> argumentTypes, List<Parameter> argumentParameters) // PORT: §2.5
    {
        layoutParameters(signature, argumentTypes.size(), null, argumentParameters);
    }

    @Internal
    public static void layoutParameters(Signature signature, int nArguments, List<Expression> arguments, List<Parameter> argumentParameters)
    {
        for (int i = 0; i < nArguments; i++)
        {
            if (signature.allowsNamedArguments() && arguments != null && arguments.get(i) instanceof SimpleNamedExpression sn)
            {
                var p = signature.getParameter(sn.name().simpleName());
                argumentParameters.add(p != null ? p : Signature.UnknownParameter); // PORT: §3.14 ??
            }
            else if (i < signature.parameters().size())
            {
                argumentParameters.add(signature.parameters().get(i));
            }
            else
            {
                argumentParameters.add(Signature.UnknownParameter);
            }
        }
    }

    @Override
    public void getNextPossibleParameters(Signature signature, List<Expression> existingArguments, List<Parameter> possibleParameters)
    {
        var iParameter = existingArguments.size();
        if (iParameter < signature.parameters().size())
        {
            possibleParameters.add(signature.parameters().get(iParameter));
        }
    }
}
