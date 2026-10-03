// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: API proof (PLAN.md 5.5), a KQL to normalised-KQL printer on SyntaxVisitor1.

package org.graylog.kusto.language.conformance;

import org.graylog.kusto.language.KustoCode;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.SchemaDisplay;
import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.syntax.DefaultSyntaxVisitor1;
import org.graylog.kusto.language.syntax.FunctionCallExpression;
import org.graylog.kusto.language.syntax.NameReference;
import org.graylog.kusto.language.syntax.SyntaxElement;
import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.syntax.SyntaxToken;

/**
 * Not a real translator. Prints a syntax tree back to KQL.
 *
 * <ul>
 *   <li>{@link Mode#WHITESPACE_ONLY}: every token's trivia becomes one space or nothing.</li>
 *   <li>{@link Mode#ANNOTATED}: original trivia is kept. Each column reference is followed by a
 *       {@code // column name: type} line comment, each call to a function with a body by a
 *       {@code // call f => body} comment, and the statement by {@code // result: type}.</li>
 * </ul>
 */
final class ExampleTranslator extends DefaultSyntaxVisitor1<String> {
    enum Mode { WHITESPACE_ONLY, ANNOTATED }

    private final Mode mode;

    private ExampleTranslator(Mode mode) {
        this.mode = mode;
    }

    /** Prints an analyzed (or merely parsed) code object. */
    static String translate(KustoCode code, Mode mode) {
        String out = code.syntax().accept(new ExampleTranslator(mode));
        if (mode == Mode.WHITESPACE_ONLY) {
            return out.trim();
        }
        return code.hasSemantics() && code.resultType() != null
                ? out + "\n// result: " + SchemaDisplay.getText(code.resultType()) + "\n"
                : out;
    }

    @Override
    protected String defaultVisit(SyntaxNode node) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < node.childCount(); i++) {
            SyntaxElement child = node.getChild(i);
            if (child == null) {
                continue;
            }
            sb.append(child instanceof SyntaxToken t ? token(t) : ((SyntaxNode) child).accept(this));
        }
        return sb.toString();
    }

    private String token(SyntaxToken t) {
        String trivia = mode == Mode.WHITESPACE_ONLY ? (t.trivia().isEmpty() ? "" : " ") : t.trivia();
        return trivia + t.text();
    }

    @Override
    public String visitNameReference(NameReference node) {
        String text = defaultVisit(node);
        if (mode == Mode.ANNOTATED && node.referencedSymbol() instanceof ColumnSymbol c) {
            return text + " // column " + c.name() + ": " + SchemaDisplay.getText(c.type()) + "\n";
        }
        return text;
    }

    @Override
    public String visitFunctionCallExpression(FunctionCallExpression node) {
        String text = defaultVisit(node);
        SyntaxNode body = node.getCalledFunctionBody();
        Symbol fn = node.referencedSymbol();
        if (mode == Mode.ANNOTATED && body != null && fn != null) {
            String flat = body.accept(new ExampleTranslator(Mode.WHITESPACE_ONLY)).trim();
            return text + " // call " + fn.name() + " => " + flat.replace('\n', ' ').replace('\r', ' ') + "\n";
        }
        return text;
    }
}
