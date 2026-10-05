// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: shared helpers for the Signature/Parameter/TableSymbol/SchemaDisplay unit tests.
package org.graylog.kusto.language.symbols;


import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.LiteralExpression;
import org.graylog.kusto.language.syntax.NameDeclaration;
import org.graylog.kusto.language.syntax.SimpleNamedExpression;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.SyntaxToken;
import org.graylog.kusto.language.syntax.TokenName;

final class SymbolTestSupport {
    private SymbolTestSupport() {
    }

    /** A bare expression with no semantic info (unlike FakeExpression.Create, which goes through the Binder). */
    static Expression expr() {
        return new LiteralExpression(SyntaxKind.TokenLiteralExpression, SyntaxToken.missing(SyntaxKind.IdentifierToken));
    }

    /** {@code name = <expr>} as the parser would produce it for a named argument. */
    static Expression named(String name) {
        return new SimpleNamedExpression(
            new NameDeclaration(new TokenName(SyntaxToken.identifier("", name))),
            SyntaxToken.punctuation("", SyntaxKind.EqualToken),
            expr());
    }
}
