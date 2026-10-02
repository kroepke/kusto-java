// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: one test per PORTING.md section 5 trap policy; filled in from W2.

package org.graylog.kusto.language.conformance;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * One test per behavioural-trap policy (PORTING.md section 5), checked against
 * {@code dotnet-facts.json} and the {@code traps/*} golden records that pin each policy. The
 * traps corpus ids are assigned in T0b; each test names the policy it will cite.
 */
class TrapsTest {
    /** PORTING.md 5.1: {@code TextFacts.IsWhitespace} is the literal 24-character switch (U+200B, U+FEFF included). */
    @Test
    void whitespaceSwitch() {
        // PORT-PENDING: W2 (traps records for 5.1 whitespace; tokens/trivia layers)
        Assumptions.abort("PORT-PENDING: W2");
    }

    /** PORTING.md 5.1: {@code TextFacts.IsLetter/IsDigit/IsLetterOrDigit/IsHexDigit} are ASCII-only. */
    @Test
    void asciiIdentifierClasses() {
        // PORT-PENDING: W2 (traps records for 5.1 ASCII identifiers)
        Assumptions.abort("PORT-PENDING: W2");
    }

    /** PORTING.md 5.1: lexer dispatch uses {@code char.IsDigit}; U+0663 lexes as a long literal with value 0. */
    @Test
    void unicodeDigitLexing() {
        // PORT-PENDING: W2 (traps records for 5.1 Unicode digits; dotnet-facts char.IsDigit table, D21)
        Assumptions.abort("PORT-PENDING: W2");
    }

    /** PORTING.md 5.2: {@code Int32/Int64.TryParse}, failure gives 0. */
    @Test
    void literalValuesInt() {
        // PORT-PENDING: W2 (traps records for 5.2 int/long literals; dotnet-facts Int32/Int64.TryParse)
        Assumptions.abort("PORT-PENDING: W2");
    }

    /** PORTING.md 5.2: {@code Double.TryParse} with Float | AllowThousands, overflow to infinity. */
    @Test
    void literalValuesReal() {
        // PORT-PENDING: W2 (traps records for 5.2 real literals; dotnet-facts Double.TryParse/ToString)
        Assumptions.abort("PORT-PENDING: W2");
    }

    /** PORTING.md 5.2: {@code Decimal.TryParse} with NumberStyles.Number, 28-digit scale. */
    @Test
    void literalValuesDecimal() {
        // PORT-PENDING: W2 (traps records for 5.2 decimal literals; dotnet-facts Decimal.TryParse/ToString)
        Assumptions.abort("PORT-PENDING: W2");
    }

    /** PORTING.md 5.2: {@code TimeSpan.TryParse} then unit suffixes; overflow and saturation rules. */
    @Test
    void literalValuesTimeSpan() {
        // PORT-PENDING: W2 (traps records for 5.2 timespan literals; dotnet-facts TimeSpan.*)
        Assumptions.abort("PORT-PENDING: W2");
    }

    /** PORTING.md 5.2: {@code DateTime.TryParse}, Z/offset values converted to UTC (D11). */
    @Test
    void literalValuesDateTime() {
        // PORT-PENDING: W2 (traps records for 5.2 datetime literals; dotnet-facts DateTime.*; D11)
        Assumptions.abort("PORT-PENDING: W2");
    }

    /** PORTING.md 5.2: {@code Guid.TryParse} for N, D, B, P, X; never {@code UUID.fromString}. */
    @Test
    void literalValuesGuid() {
        // PORT-PENDING: W2 (traps records for 5.2 guid literals; dotnet-facts Guid.TryParse/ToString)
        Assumptions.abort("PORT-PENDING: W2");
    }

    /** PORTING.md 5.3: {@code SyntaxFacts.TryGetKind("")} is {@code (true, None)} (D13). */
    @Test
    void tryGetKindEmpty() {
        // PORT-PENDING: W2 (dotnet-facts SyntaxFacts.TryGetKind; D13)
        Assumptions.abort("PORT-PENDING: W2");
    }

    /** PORTING.md 5.3: {@code Distinct()} keeps insertion order. */
    @Test
    void distinctInsertionOrder() {
        // PORT-PENDING: W2 (traps records for 5.3 ordering)
        Assumptions.abort("PORT-PENDING: W2");
    }

    /** PORTING.md 5.3/5.4: {@code OrdinalIgnoreCase} upper-cases per char ('_' vs 'a', K vs Kelvin sign). */
    @Test
    void ordinalIgnoreCaseCompare() {
        // PORT-PENDING: W2 (dotnet-facts String.CompareOrdinalIgnoreCase/EqualsOrdinalIgnoreCase)
        Assumptions.abort("PORT-PENDING: W2");
    }

    /** PORTING.md 5.4: {@code string.Compare(a, ia, b, ib, len)} clamps to the remaining length. */
    @Test
    void substringClampedCompare() {
        // PORT-PENDING: W2 (dotnet-facts String.Compare five-argument cases; SyntaxToken.cs:552-563)
        Assumptions.abort("PORT-PENDING: W2");
    }
}
