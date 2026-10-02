// Ported from: src/Kusto.Language/Parser/Combinators/PartialParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiConsumer;

import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.ObjectPool;
import org.graylog.kusto.language.utils.SafeList;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// API's for parsing as much of a parser's grammar that matches the input.
/// </summary>
public final class PartialParser
{
    private PartialParser() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// Parses the input into either the expected output for the parser,
    /// or one or more outputs that correspond to the recognized parts the grammar.
    /// </summary>
    public static <TInput> int parsePartial( // PORT: §3.5 extension method
        Parser<TInput> parser, Source<TInput> input, int inputStart, List<Object> output, int outputStart, BiConsumer<Parser<TInput>, List<Object>> onFailure)
    {
        return Partial.parse(parser, input, inputStart, output, outputStart, onFailure);
    }

    public static <TInput> int parsePartial( // PORT: §3.12 optional parameter onFailure = null
        Parser<TInput> parser, Source<TInput> input, int inputStart, List<Object> output, int outputStart)
    {
        return parsePartial(parser, input, inputStart, output, outputStart, null);
    }

    /// <summary>
    /// Parses the input into the output of the partially parsed parser that consumes the most input.
    /// </summary>
    public static <TInput> int parsePartialBest( // PORT: §3.10 covariance: IReadOnlyList<Parser<TInput>> receives Parser<TInput, TOutput>[]
        List<? extends Parser<TInput>> parsers, Source<TInput> input, int inputStart, List<Object> output, int outputStart, List<Parser<TInput>> bestParsers, BiConsumer<Parser<TInput>, List<Object>> onFailure)
    {
        return Partial.parseBest(parsers, input, inputStart, output, outputStart, bestParsers, onFailure);
    }

    public static <TInput> int parsePartialBest( // PORT: §3.12 optional parameter onFailure = null
        List<? extends Parser<TInput>> parsers, Source<TInput> input, int inputStart, List<Object> output, int outputStart, List<Parser<TInput>> bestParsers)
    {
        return parsePartialBest(parsers, input, inputStart, output, outputStart, bestParsers, null);
    }

    /// <summary>
    /// Returns the number of input item that would be consumed by <see cref="ParsePartial"/>
    /// </summary>
    public static <TInput> int scanPartial( // PORT: §3.5 extension method
        Parser<TInput> parser, Source<TInput> input, int inputStart)
    {
        return Partial.scan(parser, input, inputStart);
    }

    /// <summary>
    /// Returns the number of input items that would be consumed by <see cref="ParsePartialBest"/>.
    /// </summary>
    public static <TInput> int scanPartialBest( // PORT: §3.10 covariance
        List<? extends Parser<TInput>> parsers, Source<TInput> input, int inputStart)
    {
        return Partial.scanBest(parsers, input, inputStart);
    }

    // PORT: §3.10 static class Partial<TInput>: Java static members cannot use a class type parameter, so the
    // static methods are generic in <TInput> and the nested types are generic static classes. Static fields
    // (the two pools, Path.Empty) are shared raw statics (§3.9).
    private static final class Partial
    {
        public static <TInput> int scan(Parser<TInput> parser, Source<TInput> input, int inputStart)
        {
            var pathFinder = new PathFinder<TInput>();
            var path = pathFinder.findPath(parser, input, inputStart);
            return path != null && path != Path.Empty ? path.InputLength : -1;
        }

        public static <TInput> int parse(Parser<TInput> parser, Source<TInput> input, int inputStart, List<Object> output, int outputStart, BiConsumer<Parser<TInput>, List<Object>> onFailure)
        {
            var pathFinder = new PathFinder<TInput>();
            var path = pathFinder.findPath(parser, input, inputStart);
            return parsePath(path, input, inputStart, output, outputStart, onFailure);
        }

        // PORT: §3.10 public with a public constructor: SourceCache.getOrCreate(Class) instantiates it reflectively.
        // The cache key is the Class, so one MemoizedInfo serves every TInput (C# keys the closed type); a source has one TInput.
        public static final class MemoizedInfo
        {
            public LinkedHashMap<BestPathKey<?>, BestPathInfo<?>> BestPaths = // PORT: §3.17
                new LinkedHashMap<BestPathKey<?>, BestPathInfo<?>>();

            public MemoizedInfo()
            {
            }
        }

        // PORT: §3.2 struct with custom equality -> final class with upstream equals/hashCode (never a record)
        private static final class BestPathKey<TInput>
        {
            public List<? extends Parser<TInput>> Parsers; // PORT: §3.10 covariance
            public int InputStart;

            public BestPathKey(
                List<? extends Parser<TInput>> parsers,
                int inputStart)
            {
                this.Parsers = parsers;
                this.InputStart = inputStart;
                _hc = 0;
                computeHashCode();
            }

            private int _hc;
            private void computeHashCode()
            {
                int hc = this.InputStart;
                for (var parser : this.Parsers)
                {
                    hc += parser.hashCode();
                }
                _hc = hc;
            }

            public boolean equals(BestPathKey<TInput> other)
            {
                if (other.InputStart != this.InputStart)
                    return false;
                if (other.Parsers.size() != this.Parsers.size())
                    return false;
                for (int i = 0; i < this.Parsers.size(); i++)
                {
                    if (other.Parsers.get(i) != this.Parsers.get(i))
                        return false;
                }
                return true;
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean equals(Object obj)
            {
                return obj instanceof BestPathKey<?> other // PORT: §3.15
                    && equals((BestPathKey<TInput>) other);
            }

            @Override
            public int hashCode()
            {
                return _hc;
            }
        }

        private static class BestPathInfo<TInput>
        {
            public Path<TInput> BestPath;
            public List<Parser<TInput>> BestParsers;

            public BestPathInfo(
                Path<TInput> bestPath,
                List<Parser<TInput>> bestParsers)
            {
                this.BestPath = bestPath;
                this.BestParsers = bestParsers;
            }
        }

        @SuppressWarnings("unchecked")
        private static <TInput> BestPathInfo<TInput> getBestPath(
            List<? extends Parser<TInput>> parsers,
            Source<TInput> source,
            int inputStart)
        {
            var cached = source.cache().getOrCreate(MemoizedInfo.class); // PORT: §3.10 GetOrCreate<MemoizedInfo>()
            var key = new BestPathKey<TInput>(parsers, inputStart);
            var info = (BestPathInfo<TInput>) cached.BestPaths.get(key); // PORT: §3.3 TryGetValue; values are never null; §3.9 shared raw map
            if (info != null)
                return info;
            var pathFinder = new PathFinder<TInput>();
            var bestParsers = new ArrayList<Parser<TInput>>();
            var path = pathFinder.findBestPath(parsers, source, inputStart, bestParsers);
            info = new BestPathInfo<TInput>(path, bestParsers);
            cached.BestPaths.put(key, info);
            return info;
        }

        public static <TInput> int scanBest(List<? extends Parser<TInput>> parsers, Source<TInput> input, int inputStart)
        {
            var info = getBestPath(parsers, input, inputStart);
            return info.BestPath != null && info.BestPath != Path.Empty 
                ? info.BestPath.InputLength 
                : -1;
        }

        public static <TInput> int parseBest(List<? extends Parser<TInput>> parsers, Source<TInput> input, int inputStart, List<Object> output, int outputStart, List<Parser<TInput>> bestParsers, BiConsumer<Parser<TInput>, List<Object>> onFailure)
        {
            var best = getBestPath(parsers, input, inputStart);
            if (bestParsers != null)
                bestParsers.addAll(best.BestParsers);
            return parsePath(best.BestPath, input, inputStart, output, outputStart, onFailure);
        }

        private static <TInput> int parsePath(Path<TInput> path, Source<TInput> input, int inputStart, List<Object> output, int outputStart, BiConsumer<Parser<TInput>, List<Object>> onFailure)
        {
            if (path == null || path == Path.Empty)
                return -1;

            var inputPosition = inputStart;
            var steps = getSteps(path);

            for (var step : steps)
            {
                if (step.IsError)
                {
                    if (onFailure != null) // PORT: §3.14 ?.Invoke
                        onFailure.accept(step.Parser, output);
                }
                else
                {
                    var len = step.Parser.parse(input, inputPosition, output, output.size());
                    inputPosition += len;
                }
            }

            return inputPosition - inputStart;
        }

        @SuppressWarnings("unchecked")
        private static <TInput> List<Path<TInput>> getSteps(Path<TInput> path)
        {
            var steps = (Path<TInput>[]) new Path[path.Length]; // PORT: §3.10 generic array
            while (path != Path.Empty)
            {
                steps[path.Length - 1] = path;
                path = path.Previous;
            }
            return Arrays.asList(steps); // PORT: §3.17 array as IReadOnlyList
        }

        /// <summary>
        /// A pool of path lists.
        /// </summary>
        @SuppressWarnings("rawtypes")
        private static final ObjectPool<List> _pathListPool = // PORT: §3.9 per-closed-type static -> one shared raw pool
            new ObjectPool<List>(() -> new ArrayList(), list -> list.clear(), 20);

        /// <summary>
        /// A pool of output lists.
        /// </summary>
        @SuppressWarnings("rawtypes")
        private static final ObjectPool<List> _outputListPool = // PORT: §3.9 per-closed-type static -> one shared raw pool
            new ObjectPool<List>(() -> new ArrayList(), list -> list.clear(), 20);

        /// <summary>
        /// True if the output path has succesfully scanned something relative to the input path.
        /// </summary>
        private static <TInput> boolean isSuccess(Path<TInput> input, Path<TInput> output)
        {
            return output != input && !output.IsError;
        }

        /// <summary>
        /// True if the output path has made some progress, either successfully or partially scanned.
        /// </summary>
        private static <TInput> boolean hasProgress(Path<TInput> input, Path<TInput> output)
        {
            return output != input;
        }

        /// <summary>
        /// Replace one or more repeated instances of inner parser at end of path with outer parser,
        /// consuming the same amount out input.
        /// </summary>
        private static <TInput> Path<TInput> adjustPath(Parser<TInput> outer, Parser<TInput> inner, Path<TInput> initial, Path<TInput> result, int maxRepeats)
        {
            if (!result.IsError)
            {
                int n = 0;
                Path<TInput> path = result;

                while (path.Parser == inner)
                {
                    path = path.Previous;
                    n++;
                }

                if (path == initial && n > 0 && n <= maxRepeats)
                {
                    return initial.add(outer, result.InputLength - initial.InputLength);
                }
            }

            return result;
        }

        private static <TInput> Path<TInput> adjustPath(Parser<TInput> outer, Parser<TInput> inner, Path<TInput> initial, Path<TInput> result) // PORT: §3.12 optional parameter maxRepeats = 1
        {
            return adjustPath(outer, inner, initial, result, 1);
        }

        /// <summary>
        /// Replace sequences of inner parsers at end of path with outer parser,
        /// consuming the same amount out input.
        /// </summary>
        private static <TInput> Path<TInput> adjustPath(Parser<TInput> outer, List<? extends Parser<TInput>> inners, Path<TInput> initial, Path<TInput> result) // PORT: §3.10 covariance
        {
            if (result.Length == initial.Length + inners.size()
                && !result.IsError)
            {
                // check that all inners are represented in sequence
                var path = result;
                for (int i = inners.size() - 1; i >= 0; i--, path = path.Previous)
                {
                    if (path.Parser != inners.get(i))
                        return result;
                }

                return initial.add(outer, result.InputLength - initial.InputLength);
            }

            return result;
        }

        /// <summary>
        /// Replace one or more repeated instances of inner parser at end of each path with outer parser,
        /// consuming the same amount out input.
        /// </summary>
        @SuppressWarnings("unchecked")
        private static <TInput> ScanOutput<TInput> adjustPaths(ScanOutput<TInput> output, Parser<TInput> outer, Parser<TInput> inner, int maxRepeats)
        {
            if (output.hasMultiplePaths())
            {
                List<Path<TInput>> outputPaths = _pathListPool.allocateFromPool(); // PORT: §3.9 shared raw pool
                try
                {
                    for (var path : output.paths())
                    {
                        outputPaths.add(adjustPath(outer, inner, output.input().Path, path, maxRepeats));
                    }

                    return output.withPaths(outputPaths);
                }
                finally
                {
                    _pathListPool.returnToPool(outputPaths);
                }
            }
            else
            {
                return output.withPath(adjustPath(outer, inner, output.input().Path, output.path(), maxRepeats));
            }
        }

        private static <TInput> ScanOutput<TInput> adjustPaths(ScanOutput<TInput> output, Parser<TInput> outer, Parser<TInput> inner) // PORT: §3.12 optional parameter maxRepeats = 1
        {
            return adjustPaths(output, outer, inner, 1);
        }

        /// <summary>
        /// Gets the single best path from the collection of paths.
        /// </summary>
        private static <TInput> Path<TInput> getBestPath(List<Path<TInput>> paths, Path<TInput> input)
        {
            var maxInput = Linq.max(paths, p -> p.InputLength); // PORT: §3.6; throws IllegalStateException on empty like Enumerable.Max
            var bestPaths = Linq.where(paths, p -> p.InputLength == maxInput); // PORT: §3.6

            if (bestPaths.size() > 1)
            {
                var firstSuccess = Linq.firstOrDefault(bestPaths, p -> isSuccess(input, p)); // PORT: §3.6
                if (firstSuccess != null)
                    return firstSuccess;

                var withoutError = Linq.firstOrDefault(bestPaths, p -> !p.IsError); // PORT: §3.6
                if (withoutError != null)
                    return withoutError;

                // all paths are errors... combine them
                var combinedErrors = new BestParser<TInput>(ListExtensions.toReadOnly(Linq.<Path<TInput>, Parser<TInput>>select(bestPaths, p -> p.Parser))); // PORT: §3.6, §3.5
                var combinedPath = bestPaths.get(0).Previous.addError(combinedErrors);
                return combinedPath;
            }
            else
            {
                return bestPaths.get(0);
            }
        }


        /// <summary>
        /// A parser visitor that finds paths that are legal full or partial scan of an input.
        /// </summary>
        private static class PathFinder<TInput> extends ParserVisitor3<TInput, ScanInput<TInput>, ScanOutput<TInput>>
        {
            public PathFinder()
            {
            }

            /// <summary>
            /// Finds the sequence of least granular parsers in the grammar that consume the most input.
            /// </summary>
            public Path<TInput> findPath(Parser<TInput> parser, Source<TInput> source, int inputStart)
            {
                return findBestPath(Collections.singletonList(parser), source, inputStart); // PORT: §3.17 new[] { parser }
            }

            // PORT: §3.2 ValueTuple (parser, path) in FindBestPath -> private record
            private record ParserAndPath<TInput>(Parser<TInput> parser, Path<TInput> path)
            {
            }

            public Path<TInput> findBestPath(
                List<? extends Parser<TInput>> parsers,
                Source<TInput> source,
                int inputStart,
                List<Parser<TInput>> bestParsers)
            {
                var input = new ScanInput<TInput>(source, inputStart, Path.<TInput>empty());

                // PORT: §3.6 hot path: parsers.SelectMany(parser => parser.Accept(this, input).Paths.Select(path => (parser, path))).ToList()
                var results = new ArrayList<ParserAndPath<TInput>>();
                for (var parser : parsers)
                {
                    var output = parser.accept(this, input);
                    for (var path : output.paths())
                    {
                        results.add(new ParserAndPath<TInput>(parser, path));
                    }
                }

                var maxInput = Linq.max(results, r -> r.path().InputLength); // PORT: §3.6 Enumerable.Max: IllegalStateException on an empty sequence (InvalidOperationException upstream)

                // PORT: §3.6 hot path: results.Where(r => r.path.InputLength == maxInput).ToList()
                var bestResults = new ArrayList<ParserAndPath<TInput>>();
                for (var r : results)
                {
                    if (r.path().InputLength == maxInput)
                        bestResults.add(r);
                }

                if (bestParsers != null)
                {
                    // PORT: §3.6 hot path: bestParsers.AddRange(bestResults.Select(r => r.parser).Distinct())
                    var selected = new ArrayList<Parser<TInput>>();
                    for (var r : bestResults)
                    {
                        selected.add(r.parser());
                    }
                    bestParsers.addAll(Linq.distinct(selected));
                }

                // PORT: §3.6 hot path: bestResults.Select(r => r.path).ToList()
                var bestPaths = new ArrayList<Path<TInput>>();
                for (var r : bestResults)
                {
                    bestPaths.add(r.path());
                }
                var bestPath = getBestPath(bestPaths, Path.<TInput>empty());

// #if false
//                  var nonPartialResults = bestResults.Where(r => IsSuccess(Path.Empty, r.path)).ToList();
//                  var firstNonPartial = nonPartialResults.FirstOrDefault();
//                  if (firstNonPartial != default)
//                  {
//                      return firstNonPartial.path;
//                  }
//
//                  // if all are otherwise equal, return the first one
//                  var bestPath = bestResults[0].path;
//
// #endif
                return bestPath;
            }

            public Path<TInput> findBestPath( // PORT: §3.12 optional parameter bestParsers = null
                List<? extends Parser<TInput>> parsers,
                Source<TInput> source,
                int inputStart)
            {
                return findBestPath(parsers, source, inputStart, null);
            }

            @Override
            public ScanOutput<TInput> visitMatch(MatchParser<TInput> parser, ScanInput<TInput> input)
            {
                return scanParser(parser, parser, input);
            }

            @Override
            public <TOutput> ScanOutput<TInput> visitMatch(MatchParser2<TInput, TOutput> parser, ScanInput<TInput> input)
            {
                return scanParser(parser, parser, input);
            }

            @Override
            public <TOutput> ScanOutput<TInput> visitConvert(ConvertParser<TInput, TOutput> parser, ScanInput<TInput> input)
            {
                return scanParser(parser, parser.pattern(), input);
            }

            @Override
            public <TOutput> ScanOutput<TInput> visitMap(MapParser<TInput, TOutput> parser, ScanInput<TInput> input)
            {
                return scanParser(parser, parser, input);
            }

            @Override
            public ScanOutput<TInput> visitFails(FailsParser<TInput> parser, ScanInput<TInput> input)
            {
                return scanParser(parser, parser, input);
            }

            @Override
            public ScanOutput<TInput> visitNot(NotParser<TInput> parser, ScanInput<TInput> input)
            {
                return scanParser(parser, parser, input);
            }

            /// <summary>
            /// Just simple scan of parser.
            /// </summary>
            private ScanOutput<TInput> scanParser(Parser<TInput> parser, Parser<TInput> scanned, ScanInput<TInput> input)
            {
                var len = scanned.scan(input.Source, input.InputStart + input.Path.InputLength);
                if (len > 0)
                {
                    return new ScanOutput<TInput>(input, input.Path.add(parser, len));
                }
                return ScanOutput.from(input); // PORT: §3.8 implicit conversion
            }

            @Override
            public <TOutput> ScanOutput<TInput> visitForward(ForwardParser<TInput, TOutput> parser, ScanInput<TInput> input)
            {
                // don't partial parse a forward parser, since it implies cyclic behavior
                var deferred = parser.deferredParser().get(); // PORT: §3.8 Func<T> -> Supplier<T>
                return scanParser(parser, deferred, input);
            }

            @Override
            public <TOutput> ScanOutput<TInput> visitIf(IfParser2<TInput, TOutput> parser, ScanInput<TInput> input)
            {
                return scanIf(parser, parser.test(), parser.parser(), input);
            }

            @Override
            public ScanOutput<TInput> visitIf(IfParser<TInput> parser, ScanInput<TInput> input)
            {
                return scanIf(parser, parser.test(), parser.parser(), input);
            }

            private ScanOutput<TInput> scanIf(Parser<TInput> outer, Parser<TInput> test, Parser<TInput> inner, ScanInput<TInput> input)
            {
                var len = test.scan(input.Source, input.InputStart + input.Path.InputLength);
                if (len > 0)
                {
                    var result = inner.accept(this, input);
                    return adjustPaths(result, outer, inner);
                }
                return ScanOutput.from(input); // PORT: §3.8 implicit conversion
            }

            @Override
            public <TOutput> ScanOutput<TInput> visitLimit(LimitParser<TInput, TOutput> parser, ScanInput<TInput> input)
            {
                var len = parser.Limiter.scan(input.Source, input.InputStart + input.Path.InputLength);
                if (len >= 0)
                {
                    var limitSource = new LimitSource<TInput>(input.Source, input.InputStart + len);
                    var result = parser.Limited.accept(this, input.withSource(limitSource));
                    return adjustPaths(result, parser, parser.Limited);
                }
                return ScanOutput.from(input); // PORT: §3.8 implicit conversion
            }

            @Override
            public ScanOutput<TInput> visitSequence(SequenceParser<TInput> parser, ScanInput<TInput> input)
            {
                return scanSequence(parser, parser.parsers(), input);
            }

            @Override
            public <TOutput> ScanOutput<TInput> visitRule(RuleParser<TInput, TOutput> parser, ScanInput<TInput> input)
            {
                return scanSequence(parser, parser.parsers(), input);
            }

            @Override
            public <TOutput> ScanOutput<TInput> visitProduce(ProduceParser<TInput, TOutput> parser, ScanInput<TInput> input)
            {
                return adjustPaths(parser.parser().accept(this, input), parser, parser.parser());
            }

            @SuppressWarnings("unchecked")
            private ScanOutput<TInput> scanSequence(Parser<TInput> outer, List<? extends Parser<TInput>> stepParsers, ScanInput<TInput> input) // PORT: §3.10 covariance
            {
                List<Path<TInput>> paths = _pathListPool.allocateFromPool(); // PORT: §3.9 shared raw pool
                try
                {
                    scanSequenceSteps(stepParsers, 0, input, input.Path, paths);

                    var best = getBestPath(paths, input.Path);
                    best = adjustPath(outer, stepParsers, input.Path, best);

                    return new ScanOutput<TInput>(input, best);
                }
                finally
                {
                    _pathListPool.returnToPool(paths);
                }
            }

            /// <summary>
            /// Scans the sequence steps from stepIndex forward to the end of the sequence,
            /// placing all the resulting paths in the paths collection.
            /// </summary>
            private void scanSequenceSteps(List<? extends Parser<TInput>> stepParsers, int stepIndex, ScanInput<TInput> initialInput, Path<TInput> path, List<Path<TInput>> resultPaths) // PORT: §3.10 covariance
            {
                var stepParser = stepParsers.get(stepIndex);

                // scan this step
                var stepOutput = stepParser.accept(this, initialInput.withPath(path));

                if (stepOutput.hasMultiplePaths())
                {
                    for (var outputPath : stepOutput.paths())
                    {
                        if (stepIndex < stepParsers.size() - 1 && isSuccess(path, outputPath))
                        {
                            scanSequenceSteps(stepParsers, stepIndex + 1, initialInput, outputPath, resultPaths);
                        }
                        else
                        {
                            var resultPath = addSequenceError(stepParser, initialInput.Path, stepOutput.input().Path, outputPath);
                            resultPaths.add(resultPath);
                        }
                    }
                }
                else if (stepIndex < stepParsers.size() - 1 && isSuccess(path, stepOutput.path()))
                {
                    scanSequenceSteps(stepParsers, stepIndex + 1, initialInput, stepOutput.path(), resultPaths);
                }
                else
                {
                    var resultPath = addSequenceError(stepParser, initialInput.Path, stepOutput.input().Path, stepOutput.path());
                    resultPaths.add(resultPath);
                }
            }

            /// <summary>
            /// Adds an error to the final path when parsing a sequence.
            /// </summary>
            private static <TInput> Path<TInput> addSequenceError(Parser<TInput> parser, Path<TInput> initialPath, Path<TInput> previousStepPath, Path<TInput> finalPath) // PORT: §3.10 static member of generic class gets its own TInput
            {
                // if the path is the same as the initial path, return it without any error (it will be condered to have failed scanning).
                // if the made no progress over the last step (previous) then it should have an error since this is a partial parsing of the sequence.
                // don't add an error on top an already existing error that might exist.
                if (finalPath != initialPath && finalPath == previousStepPath && !finalPath.IsError)
                    return finalPath.addError(parser);
                return finalPath;
            }

            @Override
            public <TOutput> ScanOutput<TInput> visitOptional(OptionalParser<TInput, TOutput> parser, ScanInput<TInput> input)
            {
                return scanOptional(parser, parser.parser(), input);
            }

            @Override
            public <TOutput> ScanOutput<TInput> visitRequired(RequiredParser<TInput, TOutput> parser, ScanInput<TInput> input)
            {
                return scanOptional(parser, parser.parser(), input);
            }

            private ScanOutput<TInput> scanOptional(Parser<TInput> outer, Parser<TInput> inner, ScanInput<TInput> input)
            {
                var result = adjustPaths(inner.accept(this, input), outer, inner);

                // add path where option consumes nothing
                if (!result.hadSuccess())
                    result = result.addPath(input.Path.add(outer, 0)); 

                return result;
            }

            @Override
            public ScanOutput<TInput> visitOneOrMore(OneOrMoreParser<TInput> parser, ScanInput<TInput> input)
            {
                return scanRepeat(parser, parser.parser(), 1, Integer.MAX_VALUE, input);
            }

            @Override
            public ScanOutput<TInput> visitZeroOrMore(ZeroOrMoreParser<TInput> parser, ScanInput<TInput> input)
            {
                return scanRepeat(parser, parser.parser(), 0, Integer.MAX_VALUE, input);
            }

            private ScanOutput<TInput> scanRepeat(
                Parser<TInput> outer,
                Parser<TInput> inner,
                int min,
                int max,
                ScanInput<TInput> input)
            {
                var repeat = scanRepeat(inner, min, max, input, ScanOutput.from(input)); // PORT: §3.2 tuple deconstruction; §3.8 implicit conversion
                var result = repeat.result();
                var maxSuccesses = repeat.maxSuccesses();
                var maxSuccessInputLength = repeat.maxSuccessInputLength();

                result = adjustPaths(result, outer, inner, max);

                // if we had more than min successful scans, but the result has no fully successful paths
                // add a path that represents the maximal successful scan of the outer parser.
                if (maxSuccesses >= min && !result.hadSuccess())
                {
                    result = result.addPath(input.Path.add(outer, maxSuccessInputLength - input.Path.InputLength));
                }

                return result;
            }

            // PORT: §3.2 ValueTuple (ScanOutput result, int maxSuccesses, int maxSuccessInputLength) -> private record
            private record ScanRepeatResult<TInput>(ScanOutput<TInput> result, int maxSuccesses, int maxSuccessInputLength)
            {
            }

            @SuppressWarnings("unchecked")
            private ScanRepeatResult<TInput> scanRepeat(
                Parser<TInput> inner,
                int min,
                int max,
                ScanInput<TInput> input,
                ScanOutput<TInput> previous)
            {
                List<ScanOutput<TInput>> previousOutputs = _outputListPool.allocateFromPool(); // PORT: §3.9 shared raw pool
                List<ScanOutput<TInput>> newOutputs = _outputListPool.allocateFromPool(); // PORT: §3.9 shared raw pool
                List<Path<TInput>> resultPaths = _pathListPool.allocateFromPool(); // PORT: §3.9 shared raw pool

                try
                {
                    previousOutputs.add(previous);

                    int maxSuccessInputLength = input.Path.InputLength;

                    int i = 0;
                    int maxSuccesses = 0;
                    boolean errorOnFirst = previous.input().Path != input.Path;

                    for (; i <= max && previousOutputs.size() > 0; i++)
                    {
                        newOutputs.clear();
                        for (var prevOutput : previousOutputs)
                        {
                            if (prevOutput.hasMultiplePaths())
                            {
                                for (var path : prevOutput.paths())
                                {
                                    if (i == 0 || (i < max && isSuccess(prevOutput.input().Path, path)))
                                    {
                                        var output = inner.accept(this, input.withPath(path));
                                        newOutputs.add(output);

                                        if (output.hadSuccess())
                                        {
                                            maxSuccesses = i + 1;
                                            maxSuccessInputLength = Math.max(maxSuccessInputLength, output.maxSuccessInputLength());
                                        }
                                    }
                                    else
                                    {
                                        // repeats less than minimum are a logically a sequence
                                        var resultPath = i <= min
                                            ? addSequenceError(inner, input.Path, prevOutput.input().Path, path)
                                            : path;
                                        resultPaths.add(resultPath);
                                    }
                                }
                            }
                            else if (i == 0 || (i < max && isSuccess(prevOutput.input().Path, prevOutput.path())))
                            {
                                var output = inner.accept(this, input.withPath(prevOutput.path()));
                                newOutputs.add(output);

                                if (output.hadSuccess())
                                {
                                    maxSuccesses = i + 1;
                                    maxSuccessInputLength = Math.max(maxSuccessInputLength, output.maxSuccessInputLength());
                                }
                            }
                            else
                            {
                                // repeats less than minimum are a logically a sequence
                                var resultPath = i <= min
                                    ? addSequenceError(inner, input.Path, prevOutput.input().Path, prevOutput.path())
                                    : prevOutput.path();
                                resultPaths.add(resultPath);
                            }
                        }

                        // swap previous and new outputs and try again...
                        var tmp = previousOutputs;
                        previousOutputs = newOutputs;
                        newOutputs = tmp;
                    }

                    var result = new ScanOutput<TInput>(input, resultPaths);
                    return new ScanRepeatResult<TInput>(result, maxSuccesses, maxSuccessInputLength);
                }
                finally
                {
                    _outputListPool.returnToPool(previousOutputs);
                    _outputListPool.returnToPool(newOutputs);
                    _pathListPool.returnToPool(resultPaths);
                }
            }

            @Override
            public <TOutput> ScanOutput<TInput> visitBest(BestParser2<TInput, TOutput> parser, ScanInput<TInput> input)
            {
                return scanBest(parser, parser.parsers(), input);
            }

            @Override
            public ScanOutput<TInput> visitBest(BestParser<TInput> parser, ScanInput<TInput> input)
            {
                return scanBest(parser, parser.parsers(), input);
            }

            @SuppressWarnings("unchecked")
            private ScanOutput<TInput> scanBest(Parser<TInput> outer, List<? extends Parser<TInput>> alternates, ScanInput<TInput> input) // PORT: §3.10 covariance
            {
                List<Path<TInput>> candidates = _pathListPool.allocateFromPool(); // PORT: §3.9 shared raw pool
                try
                {
                    for (var alt : alternates)
                    {
                        var output = adjustPaths(alt.accept(this, input), outer, alt);
                        output.getPaths(candidates);
                    }

                    var best = getBestPath(candidates, input.Path);

                    return new ScanOutput<TInput>(input, best);
                }
                finally
                {
                    _pathListPool.returnToPool(candidates);
                }
            }

            @Override
            public <TOutput> ScanOutput<TInput> visitFirst(FirstParser2<TInput, TOutput> parser, ScanInput<TInput> input)
            {
                return scanFirst(parser, parser.parsers(), input);
            }

            @Override
            public ScanOutput<TInput> visitFirst(FirstParser<TInput> parser, ScanInput<TInput> input)
            {
                return scanFirst(parser, parser.parsers(), input);
            }

            @SuppressWarnings("unchecked")
            public ScanOutput<TInput> scanFirst(Parser<TInput> outer, List<? extends Parser<TInput>> alternates, ScanInput<TInput> input) // PORT: §3.10 covariance
            {
                List<Path<TInput>> candidates = _pathListPool.allocateFromPool(); // PORT: §3.9 shared raw pool
                try
                {
                    for (var alt : alternates)
                    {
                        var accepted = alt.accept(this, input);
                        var output = adjustPaths(accepted, outer, alt);
                        output.getPaths(candidates);
                    }

                    // first no-error path is obviously first
                    var first = Linq.firstOrDefault(candidates, c -> isSuccess(input.Path, c)); // PORT: §3.6

                    // if only error paths, then take best one?
                    if (first == null)
                        first = getBestPath(candidates, input.Path);

                    return new ScanOutput<TInput>(input, first);
                }
                finally
                {
                    _pathListPool.returnToPool(candidates);
                }
            }

            @Override
            public <TLeft, TOutput> ScanOutput<TInput> visitApply(ApplyParser<TInput, TLeft, TOutput> outer, ScanInput<TInput> input)
            {
                var leftOutput = outer.leftParser().accept(this, input);

                if (!leftOutput.hadSuccess())
                    return leftOutput;

                var min = outer.applyKind() == ApplyKind.One ? 1 : 0;
                var max = outer.applyKind() == ApplyKind.One || outer.applyKind() == ApplyKind.ZeroOrOne ? 1 : Integer.MAX_VALUE;

                var repeat = scanRepeat(outer.rightParser(), min, max, input, leftOutput); // PORT: §3.2 tuple deconstruction
                var result = repeat.result();
                var maxSuccesses = repeat.maxSuccesses();
                var maxSuccessInputLength = repeat.maxSuccessInputLength();

                result = adjustApplyPaths(result, outer, outer.leftParser(), outer.rightParser(), max);

                // if we had more than min successful scans (putting us in the or-more territory)
                // we found no paths that were successfully scanned
                // add a path that represents the maximal successful scan of the outer parser.
                if (maxSuccesses >= min && !result.hadSuccess())
                {
                    result = result.addPath(input.Path.add(outer, maxSuccessInputLength - input.Path.InputLength));
                }

                return result;
            }

            @SuppressWarnings("unchecked")
            private static <TInput> ScanOutput<TInput> adjustApplyPaths(ScanOutput<TInput> output, Parser<TInput> outer, Parser<TInput> left, Parser<TInput> right, int maxRight) // PORT: §3.10 static member of generic class gets its own TInput
            {
                if (output.hasMultiplePaths())
                {
                    List<Path<TInput>> outputPaths = _pathListPool.allocateFromPool(); // PORT: §3.9 shared raw pool
                    try
                    {
                        for (var path : output.paths())
                        {
                            if (isSuccess(output.input().Path, path))
                            {
                                outputPaths.add(adjustApplyPath(outer, left, right, output.input().Path, path, maxRight));
                            }
                        }

                        return output.withPaths(outputPaths);
                    }
                    finally
                    {
                        _pathListPool.returnToPool(outputPaths);
                    }
                }
                else
                {
                    return output.withPath(adjustApplyPath(outer, left, right, output.input().Path, output.path(), maxRight));
                }
            }

            private static <TInput> Path<TInput> adjustApplyPath(Parser<TInput> outer, Parser<TInput> left, Parser<TInput> right, Path<TInput> initial, Path<TInput> result, int maxRight) // PORT: §3.10 static member of generic class gets its own TInput
            {
                if (!result.IsError)
                {
                    int nRight = 0;
                    Path<TInput> path = result;

                    while (path.Parser == right)
                    {
                        path = path.Previous;
                        nRight++;
                    }

                    if (path.Parser == left
                        && path.Previous == initial
                        && nRight <= maxRight)
                    {
                        return initial.add(outer, result.InputLength - initial.InputLength);
                    }
                }

                return result;
            }
        }

        // region Path, ScanInput, ScanOutput

        /// <summary>
        /// A sequence of <see cref="Parser{TInput}"/> that represent a full or partial scan of the input.
        /// </summary>
        public static class Path<TInput>
        {
            /// <summary>
            /// The previous path and all the previous steps.
            /// </summary>
            public final Path<TInput> Previous;

            /// <summary>
            /// The last parser used.
            /// </summary>
            public final Parser<TInput> Parser;

            /// <summary>
            /// The total number of nodes in this path.
            /// </summary>
            public final int Length;

            /// <summary>
            /// The total input length of the path.
            /// </summary>
            public final int InputLength;

            /// <summary>
            /// True if this step is an error.
            /// </summary>
            public final boolean IsError;

            /// <summary>
            /// An empty path
            /// </summary>
            @SuppressWarnings("rawtypes")
            public static final Path Empty = // PORT: §3.9 static field of a generic class: one shared raw static
                new Path<Object>(null, null, 0, 0, false);

            // PORT: §3.9 typed access to the shared Empty instance
            @SuppressWarnings("unchecked")
            public static <TInput> Path<TInput> empty()
            {
                return (Path<TInput>) Empty;
            }

            private Path(
                Path<TInput> previous,
                Parser<TInput> parser,
                int length,
                int inputLength,
                boolean isError)
            {
                this.Previous = previous;
                this.Parser = parser;
                this.Length = length;
                this.InputLength = inputLength;
                this.IsError = isError;
            }

            public Path<TInput> add(Parser<TInput> parser, int inputLength)
            {
                return new Path<TInput>(
                    this,
                    parser,
                    this.Length + 1,
                    this.InputLength + inputLength,
                    false
                    );
            }

            public Path<TInput> addError(Parser<TInput> parser)
            {
                return new Path<TInput>(
                    this,
                    parser,
                    this.Length + 1,
                    this.InputLength,
                    true
                   );
            }
        }

        /// <summary>
        /// The input for <see cref="PathFinder"/> visits.
        /// </summary>
        // PORT: §3.2 struct -> record; components keep the upstream public readonly field names
        public record ScanInput<TInput>(
            /// <summary>
            /// The input source in use.
            /// </summary>
            Source<TInput> Source,

            /// <summary>
            /// The input start position within the source
            /// </summary>
            int InputStart,

            /// <summary>
            /// The path before the parser is scanned
            /// </summary>
            Path<TInput> Path)
        {
            public ScanInput<TInput> withSource(Source<TInput> source, int inputStart)
            {
                return new ScanInput<TInput>(source, inputStart >= 0 ? inputStart : this.InputStart, this.Path);
            }

            public ScanInput<TInput> withSource(Source<TInput> source) // PORT: §3.12 optional parameter inputStart = -1
            {
                return withSource(source, -1);
            }

            public ScanInput<TInput> withPath(Path<TInput> path)
            {
                return new ScanInput<TInput>(this.Source, this.InputStart, path);
            }

            // PORT: §3.8 `implicit operator ScanOutput(ScanInput input)` is the explicit factory ScanOutput.from(ScanInput)
        }

        /// <summary>
        /// The output for <see cref="PathFinder"/> visits.
        /// </summary>
        // PORT: §3.2 struct -> final class (lazy Paths cache)
        public static final class ScanOutput<TInput>
        {
            private final ScanInput<TInput> input;
            public ScanInput<TInput> input()
            {
                return input;
            }

            private Path<TInput> _path;

            public Path<TInput> path()
            {
                if (_paths != null && _paths.size() == 1)
                {
                    return _paths.get(0);
                }

                return _path;
            }

            private List<Path<TInput>> _paths;

            public List<Path<TInput>> paths()
            {
                if (_paths == null)
                {
                    _paths = ListExtensions.append(SafeList.<Path<TInput>>empty().asList(), this.path()); // PORT: §3.5, §3.10
                }

                return _paths;
            }

            public ScanOutput(ScanInput<TInput> input, Path<TInput> path)
            {
                this.input = input;
                _path = path;
                _paths = null;
            }

            public ScanOutput(ScanInput<TInput> input, Iterable<Path<TInput>> paths)
            {
                this.input = input;
                _paths = ListExtensions.toReadOnly(paths); // PORT: §3.5
                _path = null;
            }

            // PORT: §3.8 implicit operator ScanOutput(ScanInput) (declared in ScanInput, PartialParser.cs:959)
            public static <TInput> ScanOutput<TInput> from(ScanInput<TInput> input)
            {
                return new ScanOutput<TInput>(input, input.Path);
            }

            /// <summary>
            /// True if the output has multiple resulting paths.
            /// </summary>
            public boolean hasMultiplePaths()
            {
                return _paths != null && _paths.size() > 1;
            }

            /// <summary>
            /// True if any path made progress relative to the input path.
            /// </summary>
            public boolean madeProgress()
            {
                if (this.hasMultiplePaths())
                {
                    var me = this;
                    return Linq.any(this.paths(), p -> hasProgress(me.input().Path, p)); // PORT: §3.6
                }
                else
                {
                    return hasProgress(this.input().Path, this.path());
                }
            }

            /// <summary>
            /// True if any path had a success scan relative to input path.
            /// </summary>
            public boolean hadSuccess()
            {
                if (this.hasMultiplePaths())
                {
                    var me = this;
                    return Linq.any(this.paths(), p -> isSuccess(me.input().Path, p)); // PORT: §3.6
                }
                else
                {
                    return isSuccess(this.input().Path, this.path());
                }
            }

            /// <summary>
            /// The maximum input length of successfully scanned paths.
            /// </summary>
            public int maxSuccessInputLength()
            {
                if (this.hasMultiplePaths())
                {
                    var me = this;
                    return Linq.max(Linq.where(this.paths(), p -> isSuccess(me.input().Path, p)), p -> p.InputLength); // PORT: §3.6; Max throws IllegalStateException on empty
                }
                else if (isSuccess(this.input().Path, this.path()))
                {
                    return this.path().InputLength;
                }
                else
                {
                    return this.input().Path.Length; // PORT-BUG: returns the node count (Length), not InputLength; mirrored (PartialParser.cs:1076)
                }
            }

            /// <summary>
            /// Returns a new <see cref="ScanOutput"/> with the Input modified to the specified input.
            /// </summary>
            public ScanOutput<TInput> withInput(ScanInput<TInput> input)
            {
                if (hasMultiplePaths())
                {
                    return new ScanOutput<TInput>(input, this.paths());
                }
                else
                {
                    return new ScanOutput<TInput>(input, this.path());
                }
            }

            /// <summary>
            /// Return a new <see cref="ScanOutput"/> with the path modified to the specified path.
            /// </summary>
            public ScanOutput<TInput> withPath(Path<TInput> path)
            {
                return new ScanOutput<TInput>(this.input(), path);
            }

            /// <summary>
            /// Returns a new <see cref="ScanOutput"/> with the paths modified to the specified paths.
            /// </summary>
            public ScanOutput<TInput> withPaths(Iterable<Path<TInput>> paths)
            {
                return new ScanOutput<TInput>(this.input(), paths);
            }

            /// <summary>
            /// Returns a new <see cref="ScanOutput"/> with the paths modified to be the same paths as from the other output.
            /// </summary>
            public ScanOutput<TInput> withPaths(ScanOutput<TInput> other)
            {
                if (other.hasMultiplePaths())
                {
                    return new ScanOutput<TInput>(this.input(), other.paths());
                }
                else
                {
                    return new ScanOutput<TInput>(this.input(), other.path());
                }
            }

            /// <summary>
            /// Returns a new <see cref="ScanOutput"/> with paths modified to include the specified path.
            /// </summary>
            public ScanOutput<TInput> addPath(Path<TInput> path)
            {
                return withPaths(Linq.distinct(ListExtensions.append(this.paths(), path))); // PORT: §3.5, §3.6
            }

            /// <summary>
            /// Returns a new <see cref="ScanOutput"/> with paths modified to include the specified paths.
            /// </summary>
            public ScanOutput<TInput> addPaths(Iterable<Path<TInput>> paths)
            {
                return withPaths(Linq.distinct(Linq.concat(this.paths(), paths))); // PORT: §3.6
            }

            /// <summary>
            /// Returns a new <see cref="ScanOutput"/> with paths modified to include the paths from the other output.
            /// </summary>
            public ScanOutput<TInput> addPaths(ScanOutput<TInput> other)
            {
                if (other.hasMultiplePaths())
                {
                    return addPaths(other.paths());
                }
                else
                {
                    return addPath(other.path());
                }
            }

            /// <summary>
            /// Appends the paths to the list
            /// </summary>
            public void getPaths(List<Path<TInput>> paths)
            {
                if (hasMultiplePaths())
                {
                    paths.addAll(this.paths());
                }
                else
                {
                    paths.add(this.path());
                }
            }
        }

        // endregion
    }
}
