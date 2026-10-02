// Ported from: src/Kusto.Language/Parser/LexicalToken.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.syntax.SyntaxFacts;
import org.graylog.kusto.language.syntax.SyntaxKind;

/// <summary>
/// A piece of source text that represents simple identifiers, keywords, literals or punctuation.
/// <see cref="LexicalToken"/>'s are produced by <see cref="TokenParser"/>
/// </summary>
// [DebuggerDisplay("{DebugText}")] dropped // PORT: §3.20
public class LexicalToken
{
    /// <summary>
    /// The kind of the token
    /// </summary>
    private final SyntaxKind kind;
    public SyntaxKind kind() { return this.kind; }

    /// <summary>
    /// The trivia (whitespace, etc) that preceeds the proper text of the token.
    /// </summary>
    private final String trivia;
    public String trivia() { return this.trivia; }

    /// <summary>
    /// The text of the token.
    /// </summary>
    private final String text;
    public String text() { return this.text; }

    /// <summary>
    /// Any diagnostics associated with the token.
    /// </summary>
    private final List<Diagnostic> diagnostics;
    public List<Diagnostic> diagnostics() { return this.diagnostics; }

    public LexicalToken(SyntaxKind kind, String trivia, String text, List<Diagnostic> diagnostics)
    {
        this.kind = kind;
        this.trivia = trivia != null ? trivia : ""; // PORT: §3.14
        this.text = text != null ? text : ""; // PORT: §3.14
        this.diagnostics = diagnostics != null ? diagnostics : Diagnostic.NoDiagnostics; // PORT: §3.14
    }

    public LexicalToken(SyntaxKind kind, String trivia, String text) // PORT: §3.12 optional parameter diagnostics = null
    {
        this(kind, trivia, text, (List<Diagnostic>) null);
    }

    public LexicalToken(SyntaxKind kind, String trivia, String text, Diagnostic diagnostic)
    {
        this(kind, trivia, text, diagnostic != null ? Arrays.asList(new Diagnostic[] { diagnostic }) : null); // PORT: §3.17 new[] { d } as IReadOnlyList
    }

    /// <summary>
    /// The combined length of the trivia and text of the token
    /// </summary>
    public int length() { return this.trivia().length() + this.text().length(); }

    @SuppressWarnings("unused")
    private String debugText() { return this.text().length() > 0 ? this.text() : SyntaxFacts.getText(this.kind()); } // PORT: §3.5
}
