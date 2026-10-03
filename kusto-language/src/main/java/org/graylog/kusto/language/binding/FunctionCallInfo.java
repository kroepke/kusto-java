// Ported from: src/Kusto.Language/Binder/FunctionCallInfo.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.binding;

import java.util.List;
import java.util.function.Supplier;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.DiagnosticSeverity;
import org.graylog.kusto.language.FunctionBodyFacts;
import org.graylog.kusto.language.FunctionCallExpansion;
import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// Extended semantic information for function calls.
/// </summary>
@Internal
public class FunctionCallInfo
{
    /// <summary>
    /// A function that returns the expanded body of the referenced function at the call site.
    /// </summary>
    private final Supplier<FunctionCallExpansion> _expander; // PORT: §3.8 Func<T> -> Supplier<T>
    private FunctionCallExpansion _expansion;
    private FunctionBodyFacts _facts;
    private Boolean _hasErrors; // PORT: §3.7 bool?

    public FunctionCallInfo(Supplier<FunctionCallExpansion> expander, FunctionBodyFacts facts)
    {
        _expander = expander;
        _facts = facts;
    }

    public FunctionCallInfo(FunctionCallExpansion expansion, FunctionBodyFacts facts)
    {
        _expansion = expansion;
        _facts = facts;
        _expander = null; // PORT: §3.1 final field is default (null) in C#
    }

    /// <summary>
    /// The function body facts associated with the called function.
    /// </summary>
    public FunctionBodyFacts facts() { return _facts; }

    /// <summary>
    /// The expansion (analyzed syntax tree in context of call arguments) of the called function.
    /// </summary>
    public FunctionCallExpansion expansion()
    {
        if (_expansion == null && this._expander != null)
        {
            _expansion = this._expander.get();
        }

        return _expansion;
    }

    public boolean hasErrors()
    {
        if (_hasErrors == null)
        {
            // PORT: §3.6 _facts.HasSyntaxErrors || this.Diagnostics.Any(d => d.Severity == DiagnosticSeverity.Error)
            var hasErrors = _facts.hasSyntaxErrors();
            if (!hasErrors)
            {
                for (var d : this.diagnostics())
                {
                    if (java.util.Objects.equals(d.severity(), DiagnosticSeverity.Error)) // PORT: §3.14 string ==
                    {
                        hasErrors = true;
                        break;
                    }
                }
            }

            _hasErrors = hasErrors;
        }

        return _hasErrors.booleanValue();
    }

    private List<Diagnostic> _diagnostics;

    public List<Diagnostic> diagnostics()
    {
        if (_diagnostics == null)
        {
            var expansion = this.expansion(); // PORT: §3.14 ?.
            if (expansion != null && expansion.root() instanceof SyntaxNode root)
            {
                // relocate diagnostics in original tree positions
                // PORT: §3.6 root.GetContainedDiagnostics().Select(d => d.WithLocation(root.GetPositionInOriginalTree(d.Start), d.Length)).ToReadOnly()
                var relocated = new java.util.ArrayList<Diagnostic>();
                for (var d : root.getContainedDiagnostics())
                {
                    relocated.add(d.withLocation(root.getPositionInOriginalTree(d.start()), d.length()));
                }

                _diagnostics = ListExtensions.toReadOnly(relocated);
            }
            else
            {
                _diagnostics = Diagnostic.NoDiagnostics;
            }
        }

        return _diagnostics;
    }
}
