# Upstream diff cd24dcf3..008b1e80

Summary: patch 12, re-check exclusion 7, remove 12, triage 9

| Path | Git | Manifest | Class | +/- | Bridge | Catalog data/logic | Notes |
|---|---|---|---|---|---|---|---|
| src/Kusto.Language.Bridge/.npmrc | M | excluded | re-check exclusion | +0/-2 |  |  |  |
| src/Kusto.Language.Bridge/Kusto.Language.Bridge.csproj | M | excluded | re-check exclusion | +1/-12 |  |  |  |
| src/Kusto.Language.Generators/AriaBridgeCommandInfos.cs | D | excluded | remove | +0/-21 |  |  | manifest status excluded |
| src/Kusto.Language.Generators/ClusterManagerCommandInfos.cs | D | excluded | remove | +0/-21 |  |  | manifest status excluded |
| src/Kusto.Language.Generators/CodeGenerator.cs | D | ported | remove | +0/-880 |  |  | manifest status ported; Java: CodeGenerator.java, IndentedTextWriter.java, Indentation.java |
| src/Kusto.Language.Generators/CommandGenerator.cs | D | excluded | remove | +0/-2110 |  |  | manifest status excluded |
| src/Kusto.Language.Generators/CommandInfo.cs | D | excluded | remove | +0/-26 |  |  | manifest status excluded |
| src/Kusto.Language.Generators/DataManagerCommandInfos.cs | D | excluded | remove | +0/-21 |  |  | manifest status excluded |
| src/Kusto.Language.Generators/EngineCommandInfos.cs | D | excluded | remove | +0/-3738 |  |  | manifest status excluded |
| src/Kusto.Language.Generators/Grammar.cs | D | excluded | remove | +0/-2427 |  |  | manifest status excluded |
| src/Kusto.Language.Generators/Kusto.Language.Generators.csproj | D | excluded | remove | +0/-5 |  |  | manifest status excluded |
| src/Kusto.Language.Generators/SyntaxNodeGenerator.cs | D | ported | remove | +0/-682 |  |  | manifest status ported; Java: KnownTypeInfo.java, SyntaxNodeInfo.java, ConstructorGenerationOptions.java, SyntaxNodeCloneOptions.java, SyntaxNodeProperty.java, SyntaxNodeGenerator.java |
| src/Kusto.Language.Generators/SyntaxNodeInfos.cs | D | ported | remove | +0/-3344 |  |  | manifest status ported; Java: SyntaxNodeInfos.java |
| src/Kusto.Language/Binder/Binder_Names.cs | M | partial | patch | +7/-0 |  |  |  |
| src/Kusto.Language/Binder/Binder_NodeBinder.cs | M | partial | patch | +152/-217 |  |  |  |
| src/Kusto.Language/Binder/Binder_TreeBinder.cs | M | partial | patch | +1/-11 |  |  |  |
| src/Kusto.Language/Diagnostics/DiagnosticFacts.cs | M | ported | patch | +4/-35 |  |  |  |
| src/Kusto.Language/Directory.Build.targets | M | excluded | re-check exclusion | +0/-3 |  |  |  |
| src/Kusto.Language/Editor/FormattingOptions.cs | M | excluded | re-check exclusion | +18/-465 |  |  |  |
| src/Kusto.Language/Editor/Kusto/KustoFormatter.cs | M | excluded | re-check exclusion | +159/-674 |  |  |  |
| src/Kusto.Language/Editor/SpacingStyle.cs | D | excluded | remove | +0/-53 |  |  | manifest status excluded |
| src/Kusto.Language/Functions.cs | M | ported | patch | +6/-9 |  | 14/1 |  |
| src/Kusto.Language/Kusto.Language.csproj | M | excluded | re-check exclusion | +81/-0 |  |  |  |
| src/Kusto.Language/Operators.cs | M | ported | patch | +0/-2 |  | 2/0 dataOnly |  |
| src/Kusto.Language/Parser/CodeGen/AriaBridgeCommandGrammar.cs | A | - | triage | +47/-0 |  |  | no scope rule |
| src/Kusto.Language/Parser/CodeGen/AriaBridgeCommands.cs | A | - | triage | +29/-0 |  |  | no scope rule |
| src/Kusto.Language/Parser/CodeGen/ClusterManagerCommandGrammar.cs | A | - | triage | +47/-0 |  |  | no scope rule |
| src/Kusto.Language/Parser/CodeGen/ClusterManagerCommands.cs | A | - | triage | +29/-0 |  |  | no scope rule |
| src/Kusto.Language/Parser/CodeGen/DataManagerCommandGrammar.cs | A | - | triage | +47/-0 |  |  | no scope rule |
| src/Kusto.Language/Parser/CodeGen/DataManagerCommands.cs | A | - | triage | +29/-0 |  |  | no scope rule |
| src/Kusto.Language/Parser/CodeGen/EngineCommandGrammar.cs | A | - | triage | +8431/-0 |  |  | no scope rule |
| src/Kusto.Language/Parser/CodeGen/EngineCommands.cs | A | - | triage | +2594/-0 |  |  | no scope rule |
| src/Kusto.Language/Parser/KustoFacts.cs | M | ported | patch | +1/-2 |  |  |  |
| src/Kusto.Language/Parser/KustoFacts_Keywords.cs | M | ported | patch | +0/-3 |  |  |  |
| src/Kusto.Language/Parser/QueryGrammar.cs | M | ported | patch | +6/-80 |  |  |  |
| src/Kusto.Language/Parser/QueryParser.cs | M | ported | patch | +13/-61 |  |  |  |
| src/Kusto.Language/PlugIns.cs | M | ported | patch | +0/-10 |  | 9/1 |  |
| src/Kusto.Language/Syntax/CodeGen/GeneratedSyntaxNodes.cs | A | - | triage | +18883/-0 |  |  | no scope rule |
| src/Kusto.Language/Syntax/SyntaxFacts.cs | M | ported | patch | +0/-4 |  |  |  |
| src/Kusto.Language/version.txt | M | excluded | re-check exclusion | +1/-1 |  |  |  |
