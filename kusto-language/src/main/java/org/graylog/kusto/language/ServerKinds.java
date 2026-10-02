// Ported from: src/Kusto.Language/ServerKinds.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

/// <summary>
/// The kinds of servers that can be connected to.
/// </summary>
public final class ServerKinds
{
    private ServerKinds() // PORT: §3.5 static class
    {
    }

    public static final String Engine = "Engine";
    public static final String DataManager = "DataManager";
    public static final String ClusterManager = "ClusterManager";
    public static final String AriaBridge = "AriaBridge";
}
