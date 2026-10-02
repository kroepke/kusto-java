// Ported from: src/Kusto.Language/Symbols/PatternSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import java.lang.invoke.VarHandle;
import java.util.List;

import org.graylog.kusto.language.syntax.FunctionBody;
import org.graylog.kusto.language.utils.Interlocked;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Linq;

public class PatternSignature
{
    private PatternSymbol symbol;
    public PatternSymbol symbol() { return this.symbol; }
    @Internal
    public void setSymbol(PatternSymbol symbol) { this.symbol = symbol; } // PORT: §2.6 internal setter

    private final List<String> argumentValues;
    public List<String> argumentValues() { return this.argumentValues; }
    private final String pathValue;
    public String pathValue() { return this.pathValue; }

    private final String _bodyText;
    private final FunctionBody _bodySyntax;
    private volatile Signature _signature; // PORT: §3.13 CAS-published
    private static final VarHandle SIGNATURE = Interlocked.handle(PatternSignature.class, "_signature", Signature.class); // PORT: §3.13

    private PatternSignature(
        List<String> argumentValues,
        String pathValue,
        String bodyText,
        FunctionBody bodySyntax)
    {
        this.argumentValues = argumentValues;
        this.pathValue = pathValue;
        _bodyText = bodyText;
        _bodySyntax = bodySyntax;
    }

    public PatternSignature(
        List<String> argumentValues,
        String pathValue,
        String body)
    {
        this(argumentValues, pathValue, body, null);
    }

    public PatternSignature(
        List<String> argumentValues,
        String pathValue,
        FunctionBody body)
    {
        this(argumentValues, pathValue, null, body);
    }

    public Signature signature()
    {
        if (_signature == null && this.symbol() != null)
        {
            Signature sig = null;

            var pms = this.symbol().parameters();
            if (this.symbol() != null && this.symbol().pathParameter() != null) // PORT: §3.14 ?.
                pms = Linq.toList(Linq.concat(pms, List.of(this.symbol().pathParameter()))); // PORT: §3.6

            if (_bodySyntax != null)
            {
                sig = new Signature(_bodySyntax, pms);
                sig.setSymbol(this.symbol());
            }
            else if (_bodyText != null)
            {
                sig = new Signature(_bodyText, Tabularity.Unspecified, pms);
                sig.setSymbol(this.symbol());
            }

            Interlocked.compareExchange(SIGNATURE, this, sig, null); // PORT: §3.13
        }

        return _signature;
    }
}
