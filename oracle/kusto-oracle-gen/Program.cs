// Oracle generator driver: reproduces the nine T4 outputs of upstream Kusto.Language.
// Mirrors the .tt templates under Kusto.Language/Syntax/CodeGen and Kusto.Language/Parser/CodeGen.
using System;
using System.IO;
using System.Text;
using Kusto.Language.Generator;
using Kusto.Language.Generators;

namespace Kusto.Oracle.Gen
{
    public static class Program
    {
        public static int Main(string[] args)
        {
            if (args.Length != 1)
            {
                Console.Error.WriteLine("usage: kusto-oracle-gen <output-dir>");
                return 2;
            }

            var outDir = args[0];
            Directory.CreateDirectory(outDir);

            // GeneratedSyntaxNodes.tt
            Write(outDir, "GeneratedSyntaxNodes.cs",
                SyntaxNodeGenerator.Generate(SyntaxNodeInfos.All, SyntaxNodeInfos.KnownTypes));

            // Parser/CodeGen/*.tt, in the order of the templates.
            Write(outDir, "EngineCommands.cs", new CommandGenerator().GenerateSymbols("EngineCommands", typeof(EngineCommandInfos)));
            Write(outDir, "EngineCommandGrammar.cs", new CommandGenerator().GenerateParser("EngineCommandGrammar", typeof(EngineCommandInfos)));
            Write(outDir, "DataManagerCommands.cs", new CommandGenerator().GenerateSymbols("DataManagerCommands", typeof(DataManagerCommandInfos)));
            Write(outDir, "DataManagerCommandGrammar.cs", new CommandGenerator().GenerateParser("DataManagerCommandGrammar", typeof(DataManagerCommandInfos)));
            Write(outDir, "ClusterManagerCommands.cs", new CommandGenerator().GenerateSymbols("ClusterManagerCommands", typeof(ClusterManagerCommandInfos)));
            Write(outDir, "ClusterManagerCommandGrammar.cs", new CommandGenerator().GenerateParser("ClusterManagerCommandGrammar", typeof(ClusterManagerCommandInfos)));
            Write(outDir, "AriaBridgeCommands.cs", new CommandGenerator().GenerateSymbols("AriaBridgeCommands", typeof(AriaBridgeCommandInfos)));
            Write(outDir, "AriaBridgeCommandGrammar.cs", new CommandGenerator().GenerateParser("AriaBridgeCommandGrammar", typeof(AriaBridgeCommandInfos)));
            return 0;
        }

        private static void Write(string dir, string name, string text)
        {
            // UTF-8 without BOM; text exactly as the generator returned it.
            File.WriteAllText(Path.Combine(dir, name), text, new UTF8Encoding(false));
            Console.WriteLine($"wrote {name} ({text.Length} chars)");
        }
    }
}
