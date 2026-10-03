// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: shared helpers for the Signature/Parameter/TableSymbol/SchemaDisplay unit tests.
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.graylog.kusto.language.KustoFacts;
import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.LiteralExpression;
import org.graylog.kusto.language.syntax.NameDeclaration;
import org.graylog.kusto.language.syntax.SimpleNamedExpression;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.SyntaxToken;
import org.graylog.kusto.language.syntax.TokenName;
import org.junit.jupiter.api.function.Executable;

final class W1bTestSupport {
    private W1bTestSupport() {
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

    /** Skips the calling test until KustoFacts (W2) replaces its skeleton. */
    static void assumeKustoFacts() {
        boolean available;
        try {
            KustoFacts.bracketNameIfNecessary("a");
            available = true;
        } catch (UnsupportedOperationException e) {
            available = false;
        }
        assumeTrue(available, "KustoFacts.bracketNameIfNecessary is PORT-PENDING: W2");
    }

    /** Asserts that the call reaches a skeleton member of the given wave. */
    static void assertPending(String wave, Executable call) {
        var ex = assertThrows(UnsupportedOperationException.class, call);
        assertEquals("PORT-PENDING: " + wave, ex.getMessage());
    }
}
