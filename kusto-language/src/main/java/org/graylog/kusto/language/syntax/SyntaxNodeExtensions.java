// Ported from: src/Kusto.Language/Syntax/SyntaxNode.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

public final class SyntaxNodeExtensions
{
    private SyntaxNodeExtensions() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// Copies this node as the root of a separate syntax tree fragment.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <T extends SyntaxNode> T copyAsFragment(T node) // PORT: §3.5
    {
        var cloned = node.clone();
        // creating new tree attaches to cloned node
        new SyntaxTree(cloned, node.tree(), node.triviaStart()); // PORT: §2.3 'var _ =' discard; '_' is reserved in Java
        return (T)cloned;
    }
}
