// Ported from: src/Kusto.Language/Syntax/SyntaxTree.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.Properties;
import org.graylog.kusto.language.utils.dotnet.IntRef;
import org.graylog.kusto.language.utils.dotnet.Internal;

public class SyntaxTree
{
    /// <summary>
    /// The root <see cref="SyntaxNode"/> of the <see cref="SyntaxTree"/>
    /// </summary>
    private final SyntaxNode root;
    public SyntaxNode root() { return this.root; }

    /// <summary>
    /// If not null, then the syntax tree this tree fragment was copied from.
    /// </summary>
    private final SyntaxTree original;
    public SyntaxTree original() { return this.original; }

    /// <summary>
    /// The position that this syntax tree fragment starts within the original tree it was copied from.
    /// </summary>
    private final int offsetInOriginal;
    public int offsetInOriginal() { return this.offsetInOriginal; }

    public SyntaxTree(SyntaxNode root, SyntaxTree original, int offsetInOriginal)
    {
        this.root = root;
        this.original = original;
        this.offsetInOriginal = offsetInOriginal;
        root.setTree(this);
        root.initializeTriviaStarts();
    }

    public SyntaxTree(SyntaxNode root, SyntaxTree original) // PORT: §3.12
    {
        this(root, original, 0);
    }

    public SyntaxTree(SyntaxNode root) // PORT: §3.12
    {
        this(root, null);
    }

    private int _depth = -1;

    /// <summary>
    /// The maximal depth of the nodes in the tree.
    /// </summary>
    public int depth()
    {
        if (_depth == -1)
        {
            _depth = computeMaxDepth(this.root());
        }

        return _depth;
    }

    /// <summary>
    /// True if the tree depth is shallow enough to allow stack recursion
    /// to walk the nodes of this tree.
    /// </summary>
    @Internal
    public boolean isSafeToRecurse(GlobalState state)
    {
        return depth() <= state.getProperty(Properties.MaxAnalysisDepth);
    }

    /// <summary>
    /// Walks the entire syntax tree and evaluates the maximum depth of all the nodes.
    /// </summary>
    private static int computeMaxDepth(SyntaxNode root)
    {
        var maxDepth = new IntRef(0); // PORT: §3.3 locals mutated inside lambdas → IntRef
        var depth = new IntRef(0);

        SyntaxElement.walkNodes(
            root,
            e -> // PORT: §3.12 named argument fnBefore
            {
                depth.value++;
                if (depth.value > maxDepth.value)
                    maxDepth.value = depth.value;
            },
            e -> depth.value--); // PORT: §3.12 named argument fnAfter

        return maxDepth.value;
    }
}
