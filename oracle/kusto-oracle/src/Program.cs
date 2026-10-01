using System;
using System.Collections.Generic;
using System.Threading;

namespace Kusto.Oracle
{
    /// <summary>
    /// kusto-oracle: .NET reference runner for the Java port's conformance goldens.
    ///   dump &lt;corpus.jsonl&gt; --out &lt;golden.jsonl.gz&gt; [--schemas dir] [--server-kind Engine] [--upstream sha] [--sources-sha256 hex]
    ///   facts &lt;cases.txt&gt; --out &lt;dotnet-facts.json&gt;
    ///   probe
    ///   census &lt;corpus.jsonl&gt; [--schemas dir] [--server-kind Engine]
    /// Common: [--exception-map porting/exception-map.json]
    /// All work runs on one thread with a 256 MB stack.
    /// </summary>
    public static class Program
    {
        private const int StackSize = 256 * 1024 * 1024;

        public static int Main(string[] args)
        {
            int exit = 0;
            Exception failure = null;
            var thread = new Thread(() =>
            {
                try { exit = Run(args); }
                catch (Exception ex) { failure = ex; }
            }, StackSize);
            thread.Name = "kusto-oracle";
            thread.Start();
            thread.Join();
            if (failure != null)
            {
                Console.Error.WriteLine("kusto-oracle: " + failure);
                return 1;
            }
            return exit;
        }

        private static int Usage(string message = null)
        {
            if (message != null) Console.Error.WriteLine("error: " + message);
            Console.Error.WriteLine("usage: kusto-oracle dump <corpus.jsonl> --out <golden.jsonl.gz> [--schemas <dir>] [--server-kind Engine] [--upstream <sha>] [--sources-sha256 <hex>]");
            Console.Error.WriteLine("       kusto-oracle facts <cases.txt> --out <dotnet-facts.json>");
            Console.Error.WriteLine("       kusto-oracle probe");
            Console.Error.WriteLine("       kusto-oracle census <corpus.jsonl> [--schemas <dir>] [--server-kind Engine]");
            Console.Error.WriteLine("       common option: --exception-map <path>");
            return 2;
        }

        private static int Run(string[] args)
        {
            if (args.Length == 0) return Usage();
            var command = args[0];
            var positional = new List<string>();
            var options = new Dictionary<string, string>(StringComparer.Ordinal);
            for (int i = 1; i < args.Length; i++)
            {
                if (args[i].StartsWith("--", StringComparison.Ordinal))
                {
                    if (i + 1 >= args.Length) return Usage("missing value for " + args[i]);
                    options[args[i].Substring(2)] = args[++i];
                }
                else positional.Add(args[i]);
            }

            var mapPath = options.GetValueOrDefault("exception-map") ?? ExceptionMap.FindDefault();
            if (mapPath == null) return Usage("porting/exception-map.json not found; pass --exception-map");
            ExceptionMap.Load(mapPath);

            var serverKind = options.GetValueOrDefault("server-kind") ?? "Engine";
            switch (command)
            {
                case "dump":
                    if (positional.Count != 1 || !options.ContainsKey("out")) return Usage();
                    Golden.Dump(positional[0], new Schemas(options.GetValueOrDefault("schemas"), serverKind), options["out"],
                        options.GetValueOrDefault("upstream") ?? "unknown",
                        options.GetValueOrDefault("sources-sha256") ?? "unknown",
                        serverKind);
                    return 0;
                case "facts":
                    if (positional.Count != 1 || !options.ContainsKey("out")) return Usage();
                    return Facts.Run(positional[0], options["out"]);
                case "probe":
                    Probe.Run();
                    return 0;
                case "census":
                    if (positional.Count != 1) return Usage();
                    Golden.Census(positional[0], new Schemas(options.GetValueOrDefault("schemas"), serverKind));
                    return 0;
                default:
                    return Usage("unknown command " + command);
            }
        }
    }
}
