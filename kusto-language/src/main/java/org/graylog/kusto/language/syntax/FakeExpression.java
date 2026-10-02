// Ported from: src/Kusto.Language/Syntax/FakeExpression.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

import org.graylog.kusto.language.binding.Binder;
import org.graylog.kusto.language.binding.SemanticInfo;
import org.graylog.kusto.language.symbols.TypeSymbol;

public final class FakeExpression
{
    private FakeExpression() // PORT: §3.5 static class
    {
    }

    public static Expression create(TypeSymbol type)
    {
        var ex = new LiteralExpression(SyntaxKind.TokenLiteralExpression, SyntaxToken.missing(SyntaxKind.IdentifierToken));
        Binder.defaultSetSemanticInfo(ex, new SemanticInfo(type));
        return ex;
    }

    public static Expression createNamed(String name, TypeSymbol type)
    {
        var named = new SimpleNamedExpression(
            new NameDeclaration(new TokenName(SyntaxToken.identifier("", name))),
            SyntaxToken.punctuation("", SyntaxKind.EqualToken),
            create(type));
        Binder.defaultSetSemanticInfo(named, new SemanticInfo(type));
        return named;
    }
}
