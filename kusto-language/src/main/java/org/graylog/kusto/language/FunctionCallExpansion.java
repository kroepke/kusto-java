// Ported from: src/Kusto.Language/Binder/FunctionCallExpansion.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W6

package org.graylog.kusto.language;

import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.syntax.SyntaxTree;
import org.graylog.kusto.language.utils.dotnet.Internal;

// Type ported in full ahead of W6 (PORTING.md 2.8): it only forwards to SyntaxTree.
/// <summary>
/// A <see cref="SyntaxTree"/> that represents the evaluated body of the function called,
/// as if it were expanded inline at the location of the call with the arguments and local variables in scope considered.
/// </summary>
@Internal
public class FunctionCallExpansion extends SyntaxTree
{
    public FunctionCallExpansion(SyntaxNode root, SyntaxTree original, int offsetInOriginal)
    {
        super(root, original, offsetInOriginal);
    }

    public FunctionCallExpansion(SyntaxNode root, SyntaxTree original) // PORT: §3.12
    {
        this(root, original, 0);
    }

    public FunctionCallExpansion(SyntaxNode root) // PORT: §3.12
    {
        this(root, null);
    }
}
