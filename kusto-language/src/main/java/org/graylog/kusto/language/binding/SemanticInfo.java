// Ported from: src/Kusto.Language/Binder/SemanticInfo.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.binding;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.symbols.EntityGroupSymbol;
import org.graylog.kusto.language.symbols.FunctionSymbol;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.symbols.VariableSymbol;
import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// The semantic information associated with a <see cref="SyntaxNode"/>.
/// </summary>
@Internal
public final class SemanticInfo
{
    private final Object _referencedSymbolOrSignature;

    /// <summary>
    /// The symbol referenced by the <see cref="SyntaxNode"/>,
    /// a column, function, operator, etc.
    /// May be null.
    /// </summary>
    public Symbol referencedSymbol()
    {
        if (_referencedSymbolOrSignature instanceof Signature sig)
        {
            return sig.symbol();
        }
        else
        {
            return _referencedSymbolOrSignature instanceof Symbol s ? s : null; // PORT: §3.15 as
        }
    }

    /// <summary>
    /// The matching signature of the function or operator symbol referenced by the <see cref="SyntaxNode"/>.
    /// May be null.
    /// </summary>
    public Signature referencedSignature()
    {
        if (_referencedSymbolOrSignature instanceof Signature sig)
        {
            return sig;
        }
        else if (_referencedSymbolOrSignature instanceof FunctionSymbol fn && fn.signatures().size() == 1)
        {
            return fn.signatures().get(0);
        }
        else if (_referencedSymbolOrSignature instanceof VariableSymbol vs && vs.type() instanceof FunctionSymbol vfn && vfn.signatures().size() == 1)
        {
            return vfn.signatures().get(0);
        }
        else if (_referencedSymbolOrSignature instanceof EntityGroupSymbol eg)
        {
            return eg.signature();
        }
        else
        {
            return null;
        }
    }

    private final TypeSymbol resultType;

    /// <summary>
    /// The result type of the expression.
    /// May be null if the node is not an expression.
    /// </summary>
    public TypeSymbol resultType() { return this.resultType; }

    private final boolean isConstant;

    /// <summary>
    /// If true then the expression is considered constant.
    /// </summary>
    public boolean isConstant() { return this.isConstant; }

    private final List<Diagnostic> diagnostics;

    /// <summary>
    /// Diagnostics discovered during binding.
    /// </summary>
    public List<Diagnostic> diagnostics() { return this.diagnostics; }

    private final FunctionCallInfo calledFunctionInfo;

    /// <summary>
    /// The expansion of the function called
    /// </summary>
    public FunctionCallInfo calledFunctionInfo() { return this.calledFunctionInfo; }

    private final List<SyntaxNode> alternates;

    /// <summary>
    /// A list of alternate versions of the associated node with differing semantics (or null).
    /// </summary>
    public List<SyntaxNode> alternates() { return this.alternates; }

    private SemanticInfo(
        Object referenced,
        TypeSymbol result,
        Iterable<Diagnostic> diagnostics,
        boolean isConstant,
        FunctionCallInfo calledFunctionInfo,
        List<SyntaxNode> alternates)
    {
        _referencedSymbolOrSignature = referenced;
        this.resultType = result;
        this.diagnostics = diagnostics != null ? ListExtensions.toReadOnly(diagnostics) : Diagnostic.NoDiagnostics;
        this.isConstant = isConstant;
        this.calledFunctionInfo = calledFunctionInfo;
        this.alternates = alternates;
    }

    public SemanticInfo(
        Symbol referencedSymbol,
        TypeSymbol result,
        Iterable<Diagnostic> diagnostics,
        boolean isConstant,
        FunctionCallInfo calledFunctionInfo)
    {
        this((Object) referencedSymbol, result, diagnostics, isConstant, calledFunctionInfo, null);
    }

    public SemanticInfo(Symbol referencedSymbol, TypeSymbol result, Iterable<Diagnostic> diagnostics, boolean isConstant) // PORT: §3.12 calledFunctionInfo = null
    {
        this(referencedSymbol, result, diagnostics, isConstant, null);
    }

    public SemanticInfo(Symbol referencedSymbol, TypeSymbol result, Iterable<Diagnostic> diagnostics) // PORT: §3.12 isConstant = false
    {
        this(referencedSymbol, result, diagnostics, false);
    }

    public SemanticInfo(Symbol referencedSymbol, TypeSymbol result) // PORT: §3.12 diagnostics = null
    {
        this(referencedSymbol, result, (Iterable<Diagnostic>) null);
    }

    public SemanticInfo(
        Signature referencedSignature,
        TypeSymbol result,
        Iterable<Diagnostic> diagnostics,
        boolean isConstant,
        FunctionCallInfo calledFunctionInfo)
    {
        this((Object) referencedSignature, result, diagnostics, isConstant, calledFunctionInfo, null);
    }

    public SemanticInfo(Signature referencedSignature, TypeSymbol result, Iterable<Diagnostic> diagnostics, boolean isConstant) // PORT: §3.12 calledFunctionInfo = null
    {
        this(referencedSignature, result, diagnostics, isConstant, null);
    }

    public SemanticInfo(Signature referencedSignature, TypeSymbol result, Iterable<Diagnostic> diagnostics) // PORT: §3.12 isConstant = false
    {
        this(referencedSignature, result, diagnostics, false);
    }

    public SemanticInfo(Signature referencedSignature, TypeSymbol result) // PORT: §3.12 diagnostics = null
    {
        this(referencedSignature, result, (Iterable<Diagnostic>) null);
    }

    public SemanticInfo(TypeSymbol result, Iterable<Diagnostic> diagnostics, boolean isConstant, FunctionCallInfo calledFunctionInfo)
    {
        this((Symbol) null, result, diagnostics, isConstant, calledFunctionInfo);
    }

    public SemanticInfo(TypeSymbol result, Iterable<Diagnostic> diagnostics, boolean isConstant) // PORT: §3.12 calledFunctionInfo = null
    {
        this(result, diagnostics, isConstant, null);
    }

    public SemanticInfo(TypeSymbol result, Iterable<Diagnostic> diagnostics) // PORT: §3.12 isConstant = false
    {
        this(result, diagnostics, false);
    }

    public SemanticInfo(TypeSymbol result) // PORT: §3.12 diagnostics = null
    {
        this(result, (Iterable<Diagnostic>) null);
    }

    public SemanticInfo(Symbol referencedSymbol, TypeSymbol result, Diagnostic diagnostic)
    {
        this(referencedSymbol, result, diagnostic != null ? ListExtensions.toReadOnly(singleton(diagnostic)) : Diagnostic.NoDiagnostics);
    }

    public SemanticInfo(Signature referencedSignature, TypeSymbol result, Diagnostic diagnostic)
    {
        this(referencedSignature, result, diagnostic != null ? ListExtensions.toReadOnly(singleton(diagnostic)) : Diagnostic.NoDiagnostics);
    }

    public SemanticInfo(TypeSymbol result, Diagnostic diagnostic)
    {
        this((Symbol) null, result, diagnostic);
    }

    public SemanticInfo(Iterable<Diagnostic> diagnostics)
    {
        this((Symbol) null, null, diagnostics);
    }

    // PORT: §3.17 new List<Diagnostic> { diagnostic }.AsReadOnly() inside a this(...) call needs a static helper
    private static List<Diagnostic> singleton(Diagnostic diagnostic)
    {
        var list = new ArrayList<Diagnostic>();
        list.add(diagnostic);
        return list;
    }

    public SemanticInfo withReferencedSymbol(Symbol symbol)
    {
        if (this.referencedSymbol() != symbol)
        {
            return new SemanticInfo(symbol, this.resultType(), this.diagnostics(), this.isConstant(), this.calledFunctionInfo(), this.alternates());
        }
        else
        {
            return this;
        }
    }

    public SemanticInfo withReferencedSignature(Signature signature)
    {
        if (this.referencedSignature() != signature)
        {
            return new SemanticInfo(signature, this.resultType(), this.diagnostics(), this.isConstant(), this.calledFunctionInfo(), this.alternates());
        }
        else
        {
            return this;
        }
    }

    public SemanticInfo withResultType(TypeSymbol type)
    {
        if (this.resultType() != type)
        {
            return new SemanticInfo(this.referencedSymbol(), type, this.diagnostics(), this.isConstant(), this.calledFunctionInfo(), this.alternates());
        }
        else
        {
            return this;
        }
    }

    public SemanticInfo withDiagnostics(Iterable<Diagnostic> diagnostics)
    {
        if (this.diagnostics() != diagnostics)
        {
            return new SemanticInfo(this.referencedSymbol(), this.resultType(), diagnostics, this.isConstant(), this.calledFunctionInfo(), this.alternates());
        }
        else
        {
            return this;
        }
    }

    public SemanticInfo withIsConstant(boolean isConstant)
    {
        if (this.isConstant() != isConstant)
        {
            return new SemanticInfo(this.referencedSymbol(), this.resultType(), this.diagnostics(), isConstant, this.calledFunctionInfo(), this.alternates());
        }
        else
        {
            return this;
        }
    }

    public SemanticInfo withCalledFunctionInfo(FunctionCallInfo calledFunctionInfo)
    {
        if (this.calledFunctionInfo() != calledFunctionInfo)
        {
            return new SemanticInfo(this.referencedSymbol(), this.resultType(), this.diagnostics(), this.isConstant(), calledFunctionInfo, this.alternates());
        }
        else
        {
            return this;
        }
    }

    public SemanticInfo withAlternates(List<SyntaxNode> alternates)
    {
        if (this.alternates() != alternates)
        {
            return new SemanticInfo(this.referencedSymbol(), this.resultType(), this.diagnostics(), this.isConstant(), this.calledFunctionInfo(), alternates);
        }
        else
        {
            return this;
        }
    }

    /// <summary>
    /// A default <see cref="SemanticInfo"/> for nodes that are determined to have not information.
    /// </summary>
    public static final SemanticInfo Empty = new SemanticInfo((TypeSymbol) null);
}
