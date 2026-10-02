// Ported from: src/Kusto.Language/Syntax/SyntaxElement.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

public enum IncludeTrivia
{
    /// <summary>
    /// All trivia is included.
    /// </summary>
    All,

    /// <summary>
    /// Only interior trivia is included.
    /// Trivia before the first token is not included.
    /// </summary>
    Interior,

    /// <summary>
    /// Only minimal trivia on the interior is included. 
    /// Trivia before the first token is not included, all other trivia becomes a single space or line feed.
    /// </summary>
    Minimal,

    /// <summary>
    /// Same as <see cref="Minimal"/> except no line breaks are preserved.
    /// </summary>
    SingleLine
}
