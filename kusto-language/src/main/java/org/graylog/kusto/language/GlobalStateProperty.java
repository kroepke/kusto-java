// Ported from: src/Kusto.Language/GlobalState.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

public abstract class GlobalStateProperty
{
    private final String name;
    public String name() { return this.name; }

    protected GlobalStateProperty(String name)
    {
        if (name == null) throw new NullPointerException("name" /* nameof */); // PORT: §3.16, §3.14 'name ?? throw'
        this.name = name;
    }
}
