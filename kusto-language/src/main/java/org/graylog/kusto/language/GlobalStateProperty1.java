// Ported from: src/Kusto.Language/GlobalState.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W4

package org.graylog.kusto.language;

// Type ported in full ahead of W4 (PORTING.md 2.8): Properties.MaxAnalysisDepth needs a real instance.
public class GlobalStateProperty1<T> extends GlobalStateProperty // PORT: §2.4 GlobalStateProperty<T>
{
    private final T defaultValue;
    public T defaultValue() { return this.defaultValue; }

    public GlobalStateProperty1(String name, T defaultValue)
    {
        super(name);
        this.defaultValue = defaultValue;
    }

    public GlobalStateProperty1(String name) // PORT: §3.12
    {
        this(name, null); // PORT: §3.10 default(T); W4 decides value-type defaults (e.g. GlobalStateProperty<bool>)
    }
}
