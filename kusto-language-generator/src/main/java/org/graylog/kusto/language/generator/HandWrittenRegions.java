// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: origin table and parser for the <hand-written> regions of generated syntax nodes (PORTING.md 4.4).
package org.graylog.kusto.language.generator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The generated classes whose upstream counterparts have hand-written {@code partial} halves
 * ({@code porting/inventory/generators.md} 2.3), and the upstream line ranges of those halves.
 *
 * <p>Each origin becomes one region at the end of the generated class body:
 * <pre>
 *     // &lt;hand-written from="src/Kusto.Language/Syntax/SyntaxNode.cs:107-123"&gt;
 *     ...
 *     // &lt;/hand-written&gt;
 * </pre>
 * A class with at least one origin also gets one {@code // <hand-written-imports>} region after the
 * generated imports, because Java imports cannot live inside the class body.
 *
 * <p>The ranges are per class (the {@code partial class} line through its closing brace) at the
 * pinned upstream commit; generators.md lists some of them as one combined range.
 */
public final class HandWrittenRegions {
    private HandWrittenRegions() {
    }

    static final String SYNTAX_NODE = "src/Kusto.Language/Syntax/SyntaxNode.cs";
    static final String SYNTAX_NODE_SEMANTICS = "src/Kusto.Language/Syntax/SyntaxNode_Semantics.cs";
    static final String SYNTAX_VISITOR = "src/Kusto.Language/Syntax/SyntaxVisitor.cs";

    /** Class name (Java) to the ordered list of region origins ("path:first-last"). */
    static final Map<String, List<String>> ORIGINS = new LinkedHashMap<>();

    static {
        origin("Expression", SYNTAX_NODE + ":107-123", SYNTAX_NODE_SEMANTICS + ":233-280");
        origin("LiteralExpression", SYNTAX_NODE + ":125-131");
        origin("CompoundStringLiteralExpression", SYNTAX_NODE + ":133-153");
        origin("TypeOfLiteralExpression", SYNTAX_NODE + ":155-160");
        origin("DynamicExpression", SYNTAX_NODE + ":162-190");
        origin("NameDeclaration", SYNTAX_NODE + ":192-200");
        origin("NameReference", SYNTAX_NODE + ":202-215");
        origin("Name", SYNTAX_NODE + ":217-220");
        origin("TokenName", SYNTAX_NODE + ":222-225");
        origin("BracketedName", SYNTAX_NODE + ":227-230");
        origin("BracedName", SYNTAX_NODE + ":232-235");
        origin("WildcardedName", SYNTAX_NODE + ":237-240");
        origin("BracketedWildcardedName", SYNTAX_NODE + ":242-245");
        origin("NamedParameter", SYNTAX_NODE + ":247-260");
        origin("Directive", SYNTAX_NODE + ":262-296");
        origin("SyntaxVisitor", SYNTAX_VISITOR + ":10-15");
        origin("DefaultSyntaxVisitor", SYNTAX_VISITOR + ":21-37");
        origin("SyntaxVisitor1", SYNTAX_VISITOR + ":39-44");
        origin("DefaultSyntaxVisitor1", SYNTAX_VISITOR + ":46-62");
    }

    private static void origin(String className, String... froms) {
        ORIGINS.put(className, List.of(froms));
    }

    /** The region origins expected in {@code className}, in emit order; empty when none. */
    public static List<String> originsFor(String className) {
        return ORIGINS.getOrDefault(className, List.of());
    }

    /** The distinct upstream files of the origins of {@code className}, in order. */
    public static List<String> filesFor(String className) {
        var files = new ArrayList<String>();
        for (String from : originsFor(className)) {
            String file = from.substring(0, from.lastIndexOf(':'));
            if (!files.contains(file)) {
                files.add(file);
            }
        }
        return files;
    }

    /** Thrown when an existing file's regions do not match the origin table or are malformed. */
    public static final class RegionException extends RuntimeException {
        RegionException(String message) {
            super(message);
        }
    }

    /** The hand-written content found in an existing file. */
    public record Regions(List<String> imports, Map<String, List<String>> bodies) {
        static final Regions EMPTY = new Regions(null, Map.of());

        /** The content of the region from {@code from}, or no lines when absent. */
        public List<String> body(String from) {
            return bodies.getOrDefault(from, List.of());
        }

        /** The content of the imports region, or no lines when absent. */
        public List<String> importLines() {
            return imports == null ? List.of() : imports;
        }
    }

    private static final Pattern BEGIN = Pattern.compile("^\\s*// <hand-written from=\"([^\"]*)\">\\s*$");
    private static final Pattern END = Pattern.compile("^\\s*// </hand-written>\\s*$");
    private static final Pattern IMPORTS_BEGIN = Pattern.compile("^\\s*// <hand-written-imports>\\s*$");
    private static final Pattern IMPORTS_END = Pattern.compile("^\\s*// </hand-written-imports>\\s*$");
    private static final Pattern ANY_MARKER = Pattern.compile("^\\s*//\\s*</?hand-written");

    /**
     * Extracts the regions of an existing file of {@code className}.
     *
     * @param source the file text, or null when the file does not exist yet
     * @throws RegionException on a region the origin table does not expect for this class, a duplicate,
     *         nested or unterminated region, or a malformed marker
     */
    public static Regions extract(String fileName, String className, String source) {
        if (source == null) {
            return Regions.EMPTY;
        }
        List<String> expected = originsFor(className);
        List<String> imports = null;
        var bodies = new LinkedHashMap<String, List<String>>();
        String[] lines = source.replace("\r\n", "\n").split("\n", -1);
        String open = null;      // origin of the open region; "" for the imports region
        int openLine = 0;
        List<String> content = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNo = i + 1;
            Matcher begin = BEGIN.matcher(line);
            boolean isBegin = begin.matches();
            boolean isEnd = END.matcher(line).matches();
            boolean isImportsBegin = IMPORTS_BEGIN.matcher(line).matches();
            boolean isImportsEnd = IMPORTS_END.matcher(line).matches();
            if (open == null) {
                if (isBegin) {
                    String from = begin.group(1);
                    if (!expected.contains(from)) {
                        throw new RegionException(fileName + ":" + lineNo + ": unexpected hand-written region from=\""
                                + from + "\"; the origin table (HandWrittenRegions) expects "
                                + (expected.isEmpty() ? "no regions in " + className : expected));
                    }
                    if (bodies.containsKey(from)) {
                        throw new RegionException(fileName + ":" + lineNo + ": duplicate hand-written region from=\"" + from + "\"");
                    }
                    open = from;
                    openLine = lineNo;
                    content = new ArrayList<>();
                } else if (isImportsBegin) {
                    if (expected.isEmpty()) {
                        throw new RegionException(fileName + ":" + lineNo + ": unexpected hand-written-imports region; "
                                + "the origin table (HandWrittenRegions) expects no regions in " + className);
                    }
                    if (imports != null) {
                        throw new RegionException(fileName + ":" + lineNo + ": duplicate hand-written-imports region");
                    }
                    open = "";
                    openLine = lineNo;
                    content = new ArrayList<>();
                } else if (isEnd || isImportsEnd || ANY_MARKER.matcher(line).find()) {
                    throw new RegionException(fileName + ":" + lineNo + ": malformed or unmatched hand-written marker: " + line.strip());
                }
            } else {
                boolean closes = open.isEmpty() ? isImportsEnd : isEnd;
                if (closes) {
                    if (open.isEmpty()) {
                        imports = List.copyOf(content);
                    } else {
                        bodies.put(open, List.copyOf(content));
                    }
                    open = null;
                    content = null;
                } else if (isBegin || isEnd || isImportsBegin || isImportsEnd) {
                    throw new RegionException(fileName + ":" + lineNo + ": hand-written marker inside the region opened at line "
                            + openLine + ": " + line.strip());
                } else {
                    content.add(line);
                }
            }
        }
        if (open != null) {
            throw new RegionException(fileName + ":" + openLine + ": unterminated hand-written region");
        }
        return new Regions(imports, bodies);
    }
}
