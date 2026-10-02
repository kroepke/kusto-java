// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: pins deviation D33 (grammar-mode duplicate '=') on both parser paths.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.KustoCode;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.ParserKind;
import org.graylog.kusto.language.syntax.IncludeTrivia;
import org.junit.jupiter.api.Test;

/** D33: the grammar parser mirrors an upstream bug that duplicates a bare '=' in name position. */
class FuzzKnownFailuresTest {
    @Test
    void grammarPathDuplicatesEqualsLikeUpstream() {
        GlobalState g = GlobalState.default_()
                .withParseOptions(ParseOptions.Default.withParserKind(ParserKind.Grammar));
        KustoCode code = KustoCode.parse("print f(=)", g);
        assertEquals("print f(==)", code.syntax().toString(IncludeTrivia.All));
        assertEquals(11, code.syntax().fullWidth());
    }

    @Test
    void defaultPathRoundTripsExactly() {
        KustoCode code = KustoCode.parse("print f(=)");
        assertEquals("print f(=)", code.syntax().toString(IncludeTrivia.All));
        assertEquals(10, code.syntax().fullWidth());
    }
}
