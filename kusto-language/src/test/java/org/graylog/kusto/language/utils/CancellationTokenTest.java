// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for CancellationToken (Utils/Cancellation.cs, BRIDGE shape, D22).
package org.graylog.kusto.language.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicBoolean;

import org.graylog.kusto.language.utils.dotnet.OperationCanceledException;
import org.junit.jupiter.api.Test;

class CancellationTokenTest {
    @Test
    void noneIsNeverCancelled() {
        assertDoesNotThrow(() -> CancellationToken.NONE.throwIfCancellationRequested());
        assertDoesNotThrow(() -> new CancellationToken().throwIfCancellationRequested());
        assertDoesNotThrow(() -> new CancellationToken(null).throwIfCancellationRequested());
    }

    @Test
    void cancelledSupplierThrows() {
        var token = CancellationToken.of(() -> true);
        assertThrows(OperationCanceledException.class, token::throwIfCancellationRequested);
    }

    @Test
    void notCancelledSupplierDoesNotThrow() {
        var token = CancellationToken.of(() -> false);
        assertDoesNotThrow(token::throwIfCancellationRequested);
    }

    @Test
    void supplierIsEvaluatedOnEveryCheck() {
        var flag = new AtomicBoolean();
        var token = CancellationToken.of(flag::get);
        assertDoesNotThrow(token::throwIfCancellationRequested);
        flag.set(true);
        assertThrows(OperationCanceledException.class, token::throwIfCancellationRequested);
    }
}
