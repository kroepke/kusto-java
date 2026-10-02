// Ported from: src/Kusto.Language/Diagnostics/Diagnostic.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

import java.util.List;
import java.util.Objects;

import org.graylog.kusto.language.syntax.SyntaxElement;

// [System.Diagnostics.DebuggerDisplay("{Severity}: ({Start}..{Start+Length}): {Message}")] dropped // PORT: §3.20
public final class Diagnostic // PORT: D24 IEquatable<Diagnostic> → equals(Diagnostic) + equals(Object)
{
    /// <summary>
    /// The code that uniquely identifies the specific kind of diagnostic.
    /// </summary>
    private final String code;
    public String code() { return this.code; }

    /// <summary>
    /// The category of the diagnostic; Correctness, Performance, General, etc
    /// </summary>
    private final String category;
    public String category() { return this.category; }

    /// <summary>
    /// The severity of the diagnostic; Error, Warning, Suggestion, etc
    /// </summary>
    private final String severity;
    public String severity() { return this.severity; }

    /// <summary>
    /// A short description of the diagnostic.
    /// </summary>
    private final String description;
    public String description() { return this.description; }

    /// <summary>
    /// The message of the diagnostic.
    /// </summary>
    private final String message;
    public String message() { return this.message; }

    private final int start;
    private final int length;

    public Diagnostic(String code, String message)
    {
        this(code, null, null, null, message, DiagnosticLocationKind.Relative, 0, 0); // PORT: §3.12 named arguments → positional
    }

    public Diagnostic(String code, String category, String severity, String description)
    {
        this(code, category, severity, description, null, DiagnosticLocationKind.Relative, 0, 0); // PORT: §3.12 named arguments → positional
    }

    public Diagnostic(String code, String category, String severity, String description, String message)
    {
        this(code, category, severity, description, message, DiagnosticLocationKind.Relative, 0, 0); // PORT: §3.12 named arguments → positional
    }

    private Diagnostic(String code, String category, String severity, String description, String message, DiagnosticLocationKind locationKind, int start, int length)
    {
        this.code = code != null ? code : ""; // PORT: §3.14
        this.category = category != null ? category : DiagnosticCategory.General; // PORT: §3.14
        this.severity = severity != null ? severity : DiagnosticSeverity.Error; // PORT: §3.14
        this.description = description != null ? description : message != null ? message : ""; // PORT: §3.14
        this.message = message != null ? message : description != null ? description : ""; // PORT: §3.14
        this.locationKind = locationKind;
        this.start = start >= 0 ? start: 0;
        this.length = length >= 0 ? length: 0;
    }

    /// <summary>
    /// True if the diagnostic has an known source location
    /// </summary>
    public boolean hasLocation() { return this.locationKind() == DiagnosticLocationKind.Absolute; }

    /// <summary>
    /// The kind of diagnositc location.
    /// </summary>
    private final DiagnosticLocationKind locationKind;
    public DiagnosticLocationKind locationKind() { return this.locationKind; }

    /// <summary>
    /// Start of diagnostic location in the source.
    /// </summary>
    public int start() { return this.start; }

    /// <summary>
    /// Length of diagnostic location in the source.
    /// </summary>
    public int length() { return this.length; }

    /// <summary>
    /// The position after the end of the diagnostic in source.
    /// </summary>
    public int end() { return this.start() + this.length(); }

    // PORT: §3.12 private With(...) keeps only the full signature: every caller names its arguments,
    // so each call site passes the upstream defaults (null, -1) verbatim.
    private Diagnostic with(
        String code,
        String category,
        String severity,
        String description,
        String message,
        DiagnosticLocationKind locationKind,
        int start,
        int length)
    {
        code = code != null ? code : this.code(); // PORT: §3.14
        category = category != null ? category : this.category(); // PORT: §3.14
        severity = severity != null ? severity : this.severity(); // PORT: §3.14
        description = description != null ? description : this.description(); // PORT: §3.14
        message = message != null ? message : this.message(); // PORT: §3.14
        var useLocationKind = locationKind != null ? locationKind : this.locationKind(); // PORT: §3.7
        start = start >= 0 ? start : this.start;
        length = length >= 0 ? length : this.length;

        if (!Objects.equals(code, this.code()) // PORT: §3.14
            || !Objects.equals(category, this.code()) // PORT-BUG: upstream compares category with Code, so With almost always allocates
            || !Objects.equals(severity, this.severity())
            || !Objects.equals(description, this.description())
            || !Objects.equals(message, this.message())
            || useLocationKind != this.locationKind()
            || start != this.start
            || length != this.length)
        {
            return new Diagnostic(code, category, severity, description, message, useLocationKind, start, length);
        }
        else
        {
            return this;
        }
    }

    public Diagnostic withCode(String code)
    {
        return with(code, null, null, null, null, null, -1, -1); // PORT: §3.12
    }

    public Diagnostic withCategory(String category)
    {
        return with(null, category, null, null, null, null, -1, -1); // PORT: §3.12
    }

    public Diagnostic withSeverity(String severity)
    {
        return with(null, null, severity, null, null, null, -1, -1); // PORT: §3.12
    }

    public Diagnostic withDescription(String description)
    {
        return with(null, null, null, description, null, null, -1, -1); // PORT: §3.12
    }

    public Diagnostic withMessage(String message)
    {
        return with(null, null, null, null, message, null, -1, -1); // PORT: §3.12
    }

    public Diagnostic withLocation(SyntaxElement location)
    {
        return with(null, null, null, null, null, DiagnosticLocationKind.Absolute, location.textStart(), location.width()); // PORT: §3.12
    }

    public Diagnostic withLocation(int start, int length)
    {
        return with(null, null, null, null, null, DiagnosticLocationKind.Absolute, start, length); // PORT: §3.12
    }

    public Diagnostic withLocationKind(DiagnosticLocationKind locationKind)
    {
        return with(null, null, null, null, null, locationKind, -1, -1); // PORT: §3.12
    }

    public static List<Diagnostic> NoDiagnostics = List.of(); // PORT: §3.17 new Diagnostic[0] as IReadOnlyList

    public boolean equals(Diagnostic other)
    {
        return Objects.equals(this.code(), other.code()) // PORT: §3.14
            && Objects.equals(this.message(), other.message())
            && this.hasLocation() 
            && other.hasLocation()
            && this.start() == other.start()
            && this.length() == other.length();
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj instanceof Diagnostic d && equals(d);
    }

    @Override
    public int hashCode()
    {
        return this.code().hashCode() // PORT: §2.3 string.GetHashCode is randomized per process upstream; String.hashCode here
            + this.message().hashCode()
            + this.start();
    }
}
