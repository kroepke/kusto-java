// Ported from: src/Kusto.Language/Syntax/SyntaxNode.cs
// Ported from: src/Kusto.Language/Syntax/SyntaxNode_Semantics.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

import java.util.List;
import java.util.function.Consumer;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.FunctionBodyFacts;
import org.graylog.kusto.language.FunctionCallExpansion;
import org.graylog.kusto.language.binding.FunctionCallInfo;
import org.graylog.kusto.language.binding.SemanticInfo;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.utils.dotnet.Internal;

// ===== upstream part: SyntaxNode.cs =====

/// <summary>
/// A non-terminal element in the syntax (contains one or more nodes/tokens/lists).
/// </summary>
public abstract class SyntaxNode extends SyntaxElement
{
    private int fullWidth;

    protected SyntaxNode(List<Diagnostic> diagnostics)
    {
        super(diagnostics);
    }

    @Override
    protected void init()
    {
        super.init();
        this.fullWidth = this.computeFullWidth();
    }

    @Override
    public int fullWidth() { return this.fullWidth; }

    /// <summary>
    /// Creates a copy of this <see cref="SyntaxNode"/>
    /// </summary>
    @Override
    public SyntaxNode clone(boolean includeDiagnostics) { return (SyntaxNode)this.cloneCore(includeDiagnostics); } // PORT: §3.10 'new' hiding → covariant override

    @Override
    public SyntaxNode clone() { return clone(true); } // PORT: §3.12

    public abstract void accept(SyntaxVisitor visitor);

    public abstract <TResult> TResult accept(SyntaxVisitor1<TResult> visitor);

    /// <summary>
    /// Invokes the action for this node and its descendant nodes, in lexical order, top down.
    /// </summary>
    /// <param name="action">The action that is invoked for each <see cref="SyntaxNode"/></param>
    public void walkNodes(Consumer<SyntaxNode> action)
    {
        walkNodes(this, action);
    }

    /// <summary>
    /// Returns the corresponding node in the original syntax tree
    /// for a node in a copied tree fragment.
    /// </summary>
    public SyntaxNode getOriginalNode()
    {
        var node = this;

        // PORT-BUG: loops forever when GetNodeAt returns null (no break upstream); mirrored verbatim.
        while (node.tree() != null && node.tree().original() != null) // PORT: §3.14
        {
            var startInOriginal = node.tree().offsetInOriginal() + node.textStart();
            var locationInOriginal = node.tree().original().root().getNodeAt(startInOriginal, node.width());

            if (locationInOriginal != null)
            {
                node = locationInOriginal;
                continue;
            }
        }

        return node;
    }

    /// <summary>
    /// Gets the equivalent position in the original syntax tree
    /// as the position within this copied tree fragment.
    /// </summary>
    public int getPositionInOriginalTree(int position)
    {
        var originalPosition = position;
        var tree = this.tree();

        while (tree.original() != null)
        {
            originalPosition += tree.offsetInOriginal();
            tree = tree.original();
        }

        return originalPosition;
    }

    // ===== upstream part: SyntaxNode_Semantics.cs =====

    /// <summary>
    /// The <see cref="Symbol"/> referenced by this node.
    /// </summary>
    public Symbol referencedSymbol()
    {
        var info = getSemanticInfo(); // PORT: §3.14
        return info != null ? info.referencedSymbol() : null;
    }

    /// <summary>
    /// The matching <see cref="Signature"/> for the referenced function or operator.
    /// </summary>
    public Signature referencedSignature()
    {
        var info = getSemanticInfo(); // PORT: §3.14
        return info != null ? info.referencedSignature() : null;
    }

    /// <summary>
    /// Gets the body of the referenced function evaluted in the context of the call site (or null).
    /// </summary>
    @Deprecated // [Obsolete("Use GetCalledFunctionBody() instead", error: true)] // PORT: §3.20
    public SyntaxNode getExpansion()
    {
        var info = getSemanticInfo(); // PORT: §3.14
        FunctionCallInfo called = info != null ? info.calledFunctionInfo() : null;
        FunctionCallExpansion expansion = called != null ? called.expansion() : null;
        return expansion != null ? expansion.root() : null;
    }

    /// <summary>
    /// Gets the body of the called function evaluated at this location (or null).
    /// </summary>
    public SyntaxNode getCalledFunctionBody()
    {
        var info = getSemanticInfo(); // PORT: §3.14
        FunctionCallInfo called = info != null ? info.calledFunctionInfo() : null;
        FunctionCallExpansion expansion = called != null ? called.expansion() : null;
        return expansion != null ? expansion.root() : null;
    }

    /// <summary>
    /// Gets the <see cref="FunctionBodyFacts"/> associated with the called function.
    /// </summary>
    public FunctionBodyFacts getCalledFunctionFacts()
    {
        var info = getSemanticInfo(); // PORT: §3.14
        FunctionCallInfo called = info != null ? info.calledFunctionInfo() : null;
        return called != null ? called.facts() : null;
    }

    /// <summary>
    /// Gets the diagnostics associated with the called function.
    /// </summary>
    public List<Diagnostic> getCalledFunctionDiagnostics()
    {
        var info = getSemanticInfo(); // PORT: §3.14
        FunctionCallInfo called = info != null ? info.calledFunctionInfo() : null;
        List<Diagnostic> dx = called != null ? called.diagnostics() : null;
        return dx != null ? dx : Diagnostic.NoDiagnostics;
    }

    /// <summary>
    /// True if the called function at this location has errors in its definition.
    /// </summary>
    public boolean calledFunctionHasErrors()
    {
        var info = getSemanticInfo(); // PORT: §3.14
        FunctionCallInfo called = info != null ? info.calledFunctionInfo() : null;
        return called != null ? called.hasErrors() : false;
    }

    /// <summary>
    /// A list of alternate versions of this node with differing semantics.
    /// For example, macro-expand statement lists may have multiple different 
    /// semantic evaluations based on entity group elements.
    /// </summary>
    public List<SyntaxNode> alternates()
    {
        var info = getSemanticInfo(); // PORT: §3.14
        return info != null ? info.alternates() : null;
    }

    /// <summary>
    /// Semantic diagnostics associated with this location.
    /// </summary>
    public List<Diagnostic> semanticDiagnostics()
    {
        var info = this.getSemanticInfo(); // PORT: §3.14
        List<Diagnostic> dx = info != null ? info.diagnostics() : null;
        return dx != null ? dx : Diagnostic.NoDiagnostics;
    }

    /// <summary>
    /// Gets the <see cref="SemanticInfo"/> stored in this node's extended data.
    /// </summary>
    @Internal
    public SemanticInfo getSemanticInfo()
    {
        var data = getExtendedData(false); // PORT: §3.12
        return data != null ? data.SemanticInfo : null; // PORT: §3.14
    }

    /// <summary>
    /// True if this node has already been bound.
    /// </summary>
    @Internal
    public boolean isBound()
    {
        return this.getSemanticInfo() != null;
    }
}
