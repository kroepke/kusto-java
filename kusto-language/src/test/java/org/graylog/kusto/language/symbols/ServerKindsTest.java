// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for ServerKinds (ServerKinds.cs) and KustoDialect (KustoDialect.cs).
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.graylog.kusto.language.KustoDialect;
import org.graylog.kusto.language.ServerKinds;
import org.junit.jupiter.api.Test;

class ServerKindsTest {
    @Test
    void constantsMatchUpstream() {
        assertEquals("Engine", ServerKinds.Engine);
        assertEquals("DataManager", ServerKinds.DataManager);
        assertEquals("ClusterManager", ServerKinds.ClusterManager);
        assertEquals("AriaBridge", ServerKinds.AriaBridge);
    }

    @Test
    void dialectDeclarationOrderMatchesUpstream() {
        assertEquals(5, KustoDialect.values().length);
        assertEquals(0, KustoDialect.Unknown.ordinal());
        assertEquals(1, KustoDialect.ClusterManagerCommand.ordinal());
        assertEquals(2, KustoDialect.DataManagerCommand.ordinal());
        assertEquals(3, KustoDialect.EngineCommand.ordinal());
        assertEquals(4, KustoDialect.Query.ordinal());
    }
}
