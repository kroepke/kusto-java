// Ported from: src/Kusto.Language/Symbols/ParameterLayouts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import java.util.List;

import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public class CustomParameterLayout extends ParameterLayout
{
    private final ParameterLayoutBuilder _builder;

    public CustomParameterLayout(ParameterLayoutBuilder builder)
    {
        _builder = builder;
    }

    @Override
    public void getArgumentParameters(Signature signature, List<Expression> arguments, List<Parameter> argumentParameters)
    {
        _builder.invoke(signature, arguments, argumentParameters);

        // replace any nulls with UnknownParameter
        for (int i = 0; i < argumentParameters.size(); i++)
        {
            if (argumentParameters.get(i) == null)
                argumentParameters.set(i, Signature.UnknownParameter);
        }

        // fill out any parameters left unspecified
        while (argumentParameters.size() < arguments.size())
        {
            argumentParameters.add(Signature.UnknownParameter);
        }
    }
}
