# kusto-java

A Java 21 port of Microsoft's Kusto.Language, the KQL parser.
It is a mirror port: structure and behavior follow upstream.

Derived from [microsoft/Kusto-Query-Language](https://github.com/microsoft/Kusto-Query-Language).
Licensed under Apache-2.0.

## Modules

| Module | Purpose |
|---|---|
| `kusto-language` | The port. Zero runtime dependencies. |
| `kusto-language-generator` | Build-time SyntaxNode source generator. |
| `kusto-language-conformance` | Tests comparing the port against .NET oracle goldens. |
| `kusto-language-benchmarks` | JMH benchmarks. |

## Build

```
mvn verify
```

Requires Java 21 and Maven 3.9+. No .NET needed.

## Docs

- [PLAN.md](PLAN.md): scope, waves, risks.
- [PORTING.md](PORTING.md): porting rules and layout.
- [UPSTREAM.md](UPSTREAM.md): upstream pin and sync notes.
- [NOTICE](NOTICE): attribution.
