// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: JMH benchmarks for parse and parse+bind on fixed workloads, plus shared-GlobalState contention.
package org.graylog.kusto.language.benchmarks;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.KustoCode;
import org.graylog.kusto.language.symbols.ClusterSymbol;
import org.graylog.kusto.language.symbols.DatabaseSymbol;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Parse and parse+bind throughput on three fixed workloads, plus a contention case.
 *
 * <p>Informational only; results are not a gate.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class ParseBenchmark {

    static final String SHORT = "T | where a > 1 | project a, b";

    // Workload (b): Azure-Sentinel detection rule, corpus record sentinel/0030 (1924 chars),
    // source Detections/AuditLogs/ApplicationRedirectURLUpdate.yaml of Azure/Azure-Sentinel
    // (commit cfb3c3ded5cb65c907abb6b71b28b9ed25806ee2), MIT License, Copyright (c) Microsoft Corporation.
    // See kusto-language-conformance/src/test/resources/corpus/{PROVENANCE.json,LICENSE-Azure-Sentinel.txt}.
    // AuditLogs is not in the benchmark schema, so binding reports semantic diagnostics; that is intended.
    static final String SENTINEL = """
        AuditLogs
          | where Category =~ "ApplicationManagement"
          | where Result =~ "success"
          | where OperationName =~ 'Update Application'
          | where TargetResources has "AppAddress"
          | mv-expand TargetResources
          | mv-expand TargetResources.modifiedProperties
          | where TargetResources_modifiedProperties.displayName =~ "AppAddress"
          | extend Key = tostring(TargetResources_modifiedProperties.displayName)
          | extend NewValue = TargetResources_modifiedProperties.newValue
          | extend OldValue = TargetResources_modifiedProperties.oldValue
          | where isnotempty(Key) and isnotempty(NewValue)
          | project-reorder Key, NewValue, OldValue
          | extend NewUrls = extract_all('"Address":([^,]*)', tostring(NewValue))
          | extend OldUrls = extract_all('"Address":([^,]*)', tostring(OldValue))
          | extend AddedUrls = set_difference(NewUrls, OldUrls)
          | where array_length(AddedUrls) > 0
          | extend UserAgent = iif(tostring(AdditionalDetails[0].key) == "User-Agent", tostring(AdditionalDetails[0].value), "")
          | extend InitiatingAppName = tostring(InitiatedBy.app.displayName)
          | extend InitiatingAppServicePrincipalId = tostring(InitiatedBy.app.servicePrincipalId)
          | extend InitiatingUserPrincipalName = tostring(InitiatedBy.user.userPrincipalName)
          | extend InitiatingAadUserId = tostring(InitiatedBy.user.id)
          | extend InitiatingIPAddress = tostring(InitiatedBy.user.ipAddress)
          | extend AddedBy = iif(isnotempty(InitiatingUserPrincipalName), InitiatingUserPrincipalName, InitiatingAppName)
          | extend TargetAppName = tostring(TargetResources.displayName)
          | extend InitiatingAccountName = tostring(split(InitiatingUserPrincipalName, "@")[0]), InitiatingAccountUPNSuffix = tostring(split(InitiatingUserPrincipalName, "@")[1])
          | project-reorder TimeGenerated, TargetAppName, InitiatingAppName, InitiatingAppServicePrincipalId, InitiatingUserPrincipalName, InitiatingAadUserId, InitiatingIPAddress, AddedUrls, AddedBy, UserAgent

        """;

    static GlobalState newGlobals() {
        DatabaseSymbol db = new DatabaseSymbol("db", new TableSymbol("T", "(a: long, b: string, ts: datetime)"));
        return GlobalState.default_().withCluster(new ClusterSymbol("cluster", db)).withDatabase(db);
    }

    // Workload (c): ~40 KB synthetic union. Branch count chosen so the text is >= 40000 chars.
    static String buildUnion() {
        StringBuilder sb = new StringBuilder("union ");
        int i = 0;
        while (sb.length() < 40_000) {
            if (i > 0) {
                sb.append(",\n      ");
            }
            sb.append("(T | where a == ").append(i).append(" | extend c = a * 2)");
            i++;
        }
        return sb.toString();
    }

    @State(Scope.Benchmark)
    public static class Workloads {
        GlobalState globals;
        String union;

        @Setup
        public void setup() {
            globals = newGlobals();
            union = buildUnion();
            check("short", SHORT);
            check("sentinel", SENTINEL);
            check("union", union);
        }

        // syntax diagnostics must be empty for every workload
        private void check(String name, String text) {
            List<Diagnostic> diags = KustoCode.parse(text, globals).getSyntaxDiagnostics();
            if (!diags.isEmpty()) {
                throw new IllegalStateException("workload " + name + " has syntax diagnostics: " + diags);
            }
        }
    }

    @Benchmark
    public KustoCode parseShort(Workloads w) {
        return KustoCode.parse(SHORT, w.globals);
    }

    @Benchmark
    public KustoCode parseSentinel(Workloads w) {
        return KustoCode.parse(SENTINEL, w.globals);
    }

    @Benchmark
    public KustoCode parseUnion(Workloads w) {
        return KustoCode.parse(w.union, w.globals);
    }

    @Benchmark
    public KustoCode parseAndAnalyzeShort(Workloads w) {
        return KustoCode.parseAndAnalyze(SHORT, w.globals);
    }

    @Benchmark
    public KustoCode parseAndAnalyzeSentinel(Workloads w) {
        return KustoCode.parseAndAnalyze(SENTINEL, w.globals);
    }

    @Benchmark
    public KustoCode parseAndAnalyzeUnion(Workloads w) {
        return KustoCode.parseAndAnalyze(w.union, w.globals);
    }

    /** One GlobalState shared by all benchmark threads. */
    @State(Scope.Benchmark)
    public static class SharedGlobals {
        GlobalState globals = newGlobals();
    }

    /** One GlobalState per benchmark thread. */
    @State(Scope.Thread)
    public static class PerThreadGlobals {
        GlobalState globals = newGlobals();
    }

    @Benchmark
    @Threads(4)
    public KustoCode contendedSharedGlobalState(SharedGlobals s) {
        return KustoCode.parseAndAnalyze(SHORT, s.globals);
    }

    @Benchmark
    @Threads(4)
    public KustoCode contendedPerThreadGlobalState(PerThreadGlobals s) {
        return KustoCode.parseAndAnalyze(SHORT, s.globals);
    }
}
