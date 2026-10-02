// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: runs port calls on a 16 MB-stack thread with a timeout (PORTING.md section 6).

package org.graylog.kusto.language.conformance;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

/** Runs a call on {@code new Thread(null, r, "kql", 16L << 20)} and waits for it. */
public final class KqlThread {
    private KqlThread() {
    }

    /** Thrown when the call itself threw; the cause is the original {@link Throwable}. */
    public static final class CallFailed extends Exception {
        private static final long serialVersionUID = 1L;

        CallFailed(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Runs {@code c} on a fresh daemon thread with a 16 MB stack. A call still running after
     * {@code timeoutMillis} is abandoned (the daemon thread keeps running) and
     * {@link TimeoutException} is thrown.
     */
    public static <T> T call(Callable<T> c, long timeoutMillis) throws CallFailed, TimeoutException {
        Object[] result = new Object[1];
        Throwable[] error = new Throwable[1];
        Thread t = new Thread(null, () -> {
            try {
                result[0] = c.call();
            } catch (Throwable e) {
                error[0] = e;
            }
        }, "kql", Harness.KQL_STACK);
        t.setDaemon(true);
        t.start();
        try {
            t.join(timeoutMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TimeoutException("interrupted");
        }
        if (t.isAlive()) {
            throw new TimeoutException("still running after " + timeoutMillis + " ms");
        }
        if (error[0] != null) {
            throw new CallFailed(error[0]);
        }
        @SuppressWarnings("unchecked")
        T r = (T) result[0];
        return r;
    }
}
