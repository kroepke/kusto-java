// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: mirror of .NET OperationCanceledException (PORTING.md 3.16).

package org.graylog.kusto.language.utils.dotnet;

/** Mirror of {@code System.OperationCanceledException}. */
public class OperationCanceledException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OperationCanceledException() {
        super();
    }

    public OperationCanceledException(String message) {
        super(message);
    }

    public OperationCanceledException(String message, Throwable cause) {
        super(message, cause);
    }
}
