// Ported from: src/Kusto.Language/Editor/ClientDirective.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.editor;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.parsing.TextFacts;
import org.graylog.kusto.language.parsing.TokenParser;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.dotnet.DotNetNumber;
import org.graylog.kusto.language.utils.dotnet.Out;

/// <summary>
/// A directive to be processed by the client before executing the query.
/// </summary>
public class ClientDirective {
    private final EditString text;
    private final String name;
    private final EditString afterNameText;
    private final EditString argumentsText;
    private final List<ClientDirectiveArgument> arguments;
    private final EditString afterDirectiveText;

    /// <summary>
    /// The full text of the directive.
    /// </summary>
    public EditString text() {
        return text;
    }

    /// <summary>
    /// The name of the directive
    /// </summary>
    public String name() {
        return name;
    }

    /// <summary>
    /// All text after the name
    /// </summary>
    public EditString afterNameText() {
        return afterNameText;
    }

    /// <summary>
    /// The rest of the text on the directive line after the name
    /// </summary>
    public EditString argumentsText() {
        return argumentsText;
    }

    /// <summary>
    /// The parsed arguments as a list of zero or more optionally-named values.
    /// </summary>
    public List<ClientDirectiveArgument> arguments() {
        return arguments;
    }

    /// <summary>
    /// Any text following the directive line that may contain a command, query or another directive
    /// </summary>
    public EditString afterDirectiveText() {
        return afterDirectiveText;
    }

    public ClientDirective(
        EditString text,
        String name,
        EditString afterNameText,
        EditString argumentsText,
        List<ClientDirectiveArgument> arguments,
        EditString afterArgumentsText
        ) {
        this.text = text;
        this.name = name;
        this.afterNameText = afterNameText;
        this.argumentsText = argumentsText;
        this.arguments = arguments != null ? arguments : EmptyReadOnlyList.<ClientDirectiveArgument>instance(); // PORT: §3.14 ??, §3.9
        this.afterDirectiveText = afterArgumentsText;
    }

    /// <summary>
    /// Parses a directive from text
    /// </summary>
    public static boolean tryParse(EditString text, Out<ClientDirective> directive) { // PORT: §3.3 out parameter
        // skip over whitespace and comments looking for directive 
        var pos = TokenParser.scanTrivia(text.toString(), 0); // PORT: §3.8 EditString -> string

        if (pos >= text.length()
            || text.charAt(pos) != '#') {
            directive.value = null;
            return false;
        }

        // move past #
        pos++;

        // directive name
        var nameLen = TokenParser.scanIdentifier(text.toString(), pos); // PORT: §3.8 EditString -> string
        if (nameLen < 0)
            nameLen = 0;
        var name = text.substring(pos, nameLen); // PORT: §5.4 (start, length), stub EditString.substring(start, length)
        pos += nameLen;

        // all the text immediately after the name
        var afterNameText = text.substring(pos, text.length() - pos); // PORT: §5.4 (start, length) on EditString

        // determine end of directive line
        var endOfLine = TextFacts.getLineEnd(text.toString(), pos); // PORT: §3.8 EditString -> string

        // argument text is any remaining text on the directive line (after whitespace)
        pos += TokenParser.scanWhitespace(text.toString(), pos); // PORT: §3.8 EditString -> string
        var argLen = endOfLine - pos;
        var argumentText = EditString.Empty;
        if (argLen > 0) {
            argumentText = text.substring(pos, argLen); // PORT: §5.4 (start, length) on EditString
        }

        // any text after the end of the directive line
        var afterDirectiveStart = TextFacts.getNextLineStart(text.toString(), pos); // PORT: §3.8 EditString -> string
        var afterDirectiveText = afterDirectiveStart >= pos ? text.substring(afterDirectiveStart) : EditString.Empty;

        List<ClientDirectiveArgument> args = new ArrayList<ClientDirectiveArgument>();
        String argName = null;

        // switch to parsing argument text
        pos = 0;
        while (pos < argumentText.length()) {
            argName = null;

            // skip whitespace
            pos += TokenParser.scanWhitespace(argumentText.toString(), pos); // PORT: §3.8 EditString -> string

            // name= ?
            var len = TokenParser.scanIdentifier(argumentText.toString(), pos); // PORT: §3.8 EditString -> string
            if (len > 0) {
                // check for name = prefix
                var lookahead = len + TokenParser.scanWhitespace(argumentText.toString(), pos + len); // PORT: §3.8 EditString -> string
                if (pos + lookahead < argumentText.length() && argumentText.charAt(pos + lookahead) == '=') {
                    argName = argumentText.substring(pos, len).toString(); // PORT: §3.8 EditString -> string, §5.4 (start, length)
                    pos += lookahead + 1; // extra one for `=`
                }
            }

            // skip whitespace
            pos += TokenParser.scanWhitespace(argumentText.toString(), pos); // PORT: §3.8 EditString -> string

            // string literal (no escapes)?
            char ch = '\0'; // Java requires definite assignment; ch is always assigned before it is read
            var argStart = pos;

            // string literal?
            if (pos < argumentText.length() && ((ch = argumentText.charAt(pos)) == '"' || ch == '\'')) {
                pos++;
                while (pos < argumentText.length() && argumentText.charAt(pos) != ch) {
                    pos++;
                }

                if (pos < argumentText.length() && argumentText.charAt(pos) == ch) {
                    pos++;
                    var argText = argumentText.substring(argStart, pos - argStart); // PORT: §5.4 (start, length) on EditString
                    var value = argumentText.substring(argStart + 1, (pos - argStart) - 2).currentText(); // PORT: §5.4 (start, length) on EditString
                    args.add(new ClientDirectiveArgument(argName, argText.toString(), value)); // PORT: §3.8 EditString -> string
                    argName = null;
                    continue;
                } else {
                    var argText = argumentText.substring(argStart, pos - argStart); // PORT: §5.4 (start, length) on EditString
                    var value = argumentText.substring(argStart + 1, (pos - argStart) - 1).currentText(); // PORT: §5.4 (start, length) on EditString
                    args.add(new ClientDirectiveArgument(argName, argText.toString(), value)); // PORT: §3.8 EditString -> string
                    argName = null;
                    continue;
                }
            }

            // find sequential text; not whitespace or separators
            while (pos < argumentText.length()
                && !TextFacts.isWhitespace(ch = argumentText.charAt(pos))
                && ch != ','
                && ch != ';') {
                pos++;
            }

            argLen = pos - argStart;
            if (argLen > 0) {
                // real number?
                len = TokenParser.scanRealLiteral(argumentText.toString(), argStart); // PORT: §3.8 EditString -> string
                if (len >= 0 && len == argLen) {
                    var argText = argumentText.substring(argStart, argLen); // PORT: §5.4 (start, length) on EditString
                    double value = DotNetNumber.parseDoubleOrZero(argText.toString()); // PORT: §5.2 double.TryParse(argText, out value): 0 on failure; §3.8 EditString -> string
                    args.add(new ClientDirectiveArgument(argName, argText.toString(), value)); // PORT: §3.8 EditString -> string, boxed Double
                    argName = null;
                    continue;
                }

                // long number?
                len = TokenParser.scanLongLiteral(argumentText.toString(), argStart); // PORT: §3.8 EditString -> string
                if (len > 0 && len == argLen) {
                    var argText = argumentText.substring(argStart, argLen); // PORT: §5.4 (start, length) on EditString
                    long value = DotNetNumber.parseLongOrZero(argText.toString()); // PORT: §5.2 long.TryParse(argText, out value): 0 on failure; §3.8 EditString -> string
                    args.add(new ClientDirectiveArgument(argName, argText.toString(), value)); // PORT: §3.8 EditString -> string, boxed Long
                    argName = null;
                    continue;
                }

                // otherwise, just the text of the arg is the value
                {
                    var argText = argumentText.substring(argStart, argLen); // PORT: §5.4 (start, length) on EditString
                    args.add(new ClientDirectiveArgument(argName, argText.toString(), argText.currentText())); // PORT: §3.8 EditString -> string
                    argName = null;
                    continue;
                }
            }

            // name= but no value?
            if (argName != null) {
                args.add(new ClientDirectiveArgument(argName, "", null));
                continue;
            }

            // unknown character: skip over it
            pos++;
        }

        directive.value = new ClientDirective(text, name.toString(), afterNameText, argumentText, args, afterDirectiveText); // PORT: §3.8 EditString -> string for name
        return true;
    }
}
