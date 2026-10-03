# kusto-language-benchmarks

JMH benchmarks for parse and parse+bind. Informational only. Not a gate.

## Run

```
mvn -q -o -pl kusto-language-benchmarks package -DskipTests
java -jar kusto-language-benchmarks/target/benchmarks.jar            # full run
java -jar kusto-language-benchmarks/target/benchmarks.jar -f 1 -wi 1 -i 2 -r 1 -w 1   # smoke
```

## Workloads

- short: `T | where a > 1 | project a, b`
- sentinel: ~1.9 KB Azure-Sentinel rule (corpus record `sentinel/0030`, MIT, see the conformance corpus PROVENANCE/LICENSE files)
- union: ~40 KB synthetic `union (T | where a == i | extend c = a * 2), ...`, verified free of syntax diagnostics in `@Setup`
- contention: `parseAndAnalyze` on the short query, 4 threads, one shared `GlobalState` vs one per thread

Schema: `T(a: long, b: string, ts: datetime)`. The sentinel rule references tables outside it, so it binds with semantic diagnostics. That is intended.

## Smoke numbers (2026-10-03)

Average time per op, microseconds. 1 fork, 1 warmup x 1 s, 2 measurement x 1 s. Very short runs: expect noise.
OpenJDK 21.0.9, AMD Ryzen 9 7900X (24 threads), WSL2.

| Benchmark | us/op |
|---|---|
| parseShort | 1.83 |
| parseAndAnalyzeShort | 4.75 |
| parseSentinel | 32.7 |
| parseAndAnalyzeSentinel | 136.6 |
| parseUnion (40 KB) | 1218.7 |
| parseAndAnalyzeUnion (40 KB) | 4132.5 |
| contendedSharedGlobalState (4 threads) | 6.44 |
| contendedPerThreadGlobalState (4 threads) | 6.25 |

Shared vs per-thread `GlobalState` differs by about 3 percent: no visible contention. The default JMH settings (3x2 s warmup, 5x2 s measurement) give steadier numbers.
