// Ported from: src/Kusto.Language.Generators/SyntaxNodeGenerator.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A code-generating class that is used to generate code for
 * classes that derive Kusto.DataNode.Csl.CslNode.
 *
 * <p>PORT: §4 emits Java per PORTING.md 4.3 instead of C#: one file per class (Java allows one public
 * top-level type per file), so {@link #generate} returns file name to file text instead of one string.
 */
public class SyntaxNodeGenerator {
    // PORT: §4 constants of the Java output (upstream: the C# namespace and usings in WriteFileStart).
    public static final String UPSTREAM_COMMIT = "9d95a2d5bb085d151f14e88e07b703755fd914e1";
    public static final String PACKAGE = "org.graylog.kusto.language.syntax";
    static final String TEMPLATE = "src/Kusto.Language/Syntax/CodeGen/GeneratedSyntaxNodes.tt";
    static final String IMPORT_LIST = "java.util.List";
    static final String IMPORT_DIAGNOSTIC = "org.graylog.kusto.language.Diagnostic";
    static final String IMPORT_COMPLETION_HINT = "org.graylog.kusto.language.editor.CompletionHint";
    static final String IMPORT_SYMBOL_MATCH = "org.graylog.kusto.language.symbols.SymbolMatch";
    /** The visitor classes, in emit order (PORTING.md 2.4 arity names). */
    public static final List<String> VISITORS = List.of("SyntaxVisitor", "DefaultSyntaxVisitor", "SyntaxVisitor1", "DefaultSyntaxVisitor1");

    // region Private data
    private SyntaxNodeInfo[] m_classes;
    private LinkedHashMap<String, SyntaxNodeInfo> m_nameToClass; // PORT: §3.17
    private LinkedHashMap<String, KnownTypeInfo> m_knownTypes; // PORT: §3.17

    private CodeGenerator m_writer;
    // PORT: §4 per-file state: the finished files, the source of existing files (for their regions),
    // and the regions of the file being written.
    private LinkedHashMap<String, String> m_files;
    private Function<String, String> m_existingSource;
    private HandWrittenRegions.Regions m_regions;
    // endregion

    /**
     * Generates every node file and the four visitor files.
     *
     * @param existingSource returns the current text of a file by name ({@code "BinaryExpression.java"}),
     *        or null when it does not exist; its {@code <hand-written>} regions are carried over
     * @return file name to file text, nodes in array order, then the visitors
     */
    public static LinkedHashMap<String, String> generate(
        SyntaxNodeInfo[] nodeInfos,
        KnownTypeInfo[] knownTypes,
        Function<String, String> existingSource // PORT: §4 region preservation
        ) {
        validateAndNormalizeClasses(nodeInfos);

        var eg = new SyntaxNodeGenerator();

        eg.m_classes = nodeInfos;
        eg.m_nameToClass = new LinkedHashMap<>();
        for (var info : nodeInfos) {
            dictionaryAdd(eg.m_nameToClass, info.Name, info);
        }

        eg.m_knownTypes = new LinkedHashMap<>();
        for (var knownType : knownTypes) {
            dictionaryAdd(eg.m_knownTypes, knownType.Name, knownType);
            if (!CodeGenerator.isNullOrWhiteSpace(knownType.Namespace)) {
                dictionaryAdd(eg.m_knownTypes, knownType.Namespace + "." + knownType.Name, knownType);
            }
        }

        // PORT: §4 upstream: one CodeGenerator, WriteFileStart, WriteNamespaceDeclaration(..., WriteClasses),
        // WriteFileEnd. Here each class opens and closes its own file inside WriteClasses.
        eg.m_files = new LinkedHashMap<>();
        eg.m_existingSource = existingSource;

        eg.writeClasses();

        var ret = eg.m_files;
        return ret;
    }

    public static LinkedHashMap<String, String> generate(SyntaxNodeInfo[] nodeInfos, KnownTypeInfo[] knownTypes) { // PORT: §4 no existing files
        return generate(nodeInfos, knownTypes, name -> null);
    }

    // PORT: §3.17 Dictionary.Add throws on a duplicate key
    private static <V> void dictionaryAdd(LinkedHashMap<String, V> map, String key, V value) {
        if (map.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("An item with the same key has already been added. Key: " + key);
        }
    }

    // region File scope
    private static void throwSyntaxErrorException(String message) {
        throw new IllegalStateException("SyntaxError: " + message); // PORT: §3.16 Exception
    }

    private static void ensureArgIsNotNull(Object arg, String name) {
        if (arg == null) {
            throwSyntaxErrorException("Argument '" + name + "' cannot be null.");
        }
    }

    private static void ensureArgIsNotNullOrWhitespace(String arg, String name) {
        if (CodeGenerator.isNullOrWhiteSpace(arg)) {
            if (arg == null) {
                throwSyntaxErrorException("Argument '" + name + "' cannot be null.");
            } else if (arg.equals("")) {
                throwSyntaxErrorException("Argument '" + name + "' cannot be empty.");
            } else {
                throwSyntaxErrorException("Argument '" + name + "' cannot be whitespace.");
            }
        }
    }

    private static void validateAndNormalizeClasses(SyntaxNodeInfo[] classes) {
        ensureArgIsNotNull(classes, "classes");

        int i = 0;
        for (var c : classes) {
            ensureArgIsNotNullOrWhitespace(c.Name, "classes[" + i + "].Name");
            ensureArgIsNotNullOrWhitespace(c.Base, "classes[" + c.Name + "].Base");
            if (c.Properties == null) {
                c.Properties = new SyntaxNodeProperty[0];
            }
            i++;
        }
    }

    // PORT: §4 per file instead of once: the PORTING.md 4.3 header, the package clause, the imports and, for
    // classes with hand-written halves, the imports region. Reads the existing file's regions first.
    private void writeFileStart(String className, String note, List<String> imports) {
        String fileName = className + ".java";
        m_regions = HandWrittenRegions.extract(fileName, className, m_existingSource.apply(fileName));

        m_writer = new CodeGenerator();

        var portedFrom = new ArrayList<String>();
        portedFrom.add(TEMPLATE + " (" + note + " " + className + ")");
        portedFrom.addAll(HandWrittenRegions.filesFor(className)); // PORT: §2.1 merged partials list every upstream file
        m_writer.writeJavaHeader(portedFrom, UPSTREAM_COMMIT);
        m_writer.writePackageDeclaration(PACKAGE);

        m_writer.writeImportBlock(imports);

        if (!HandWrittenRegions.originsFor(className).isEmpty()) {
            m_writer.writeLine();
            m_writer.writeHandWrittenImports(m_regions.importLines());
        }
    }

    // PORT: §4 closes the current file: hand-written regions are written by the caller before the class's brace.
    private void writeFileEnd(String className) {
        m_files.put(className + ".java", m_writer.getText());
        m_writer = null;
        m_regions = null;
    }

    // PORT: §4 PORTING.md 4.4: one region per origin at the end of the class body, existing content kept.
    private void writeHandWrittenRegions(String className) {
        var origins = HandWrittenRegions.originsFor(className);
        if (origins.isEmpty()) {
            return;
        }
        m_writer.writeEmptyLineIfNeeded();
        for (String from : origins) {
            m_writer.writeHandWrittenRegion(from, m_regions.body(from));
        }
    }
    // endregion

    // region WriteClasses
    private void writeClasses() {
        // PORT: §4 C# #region SyntaxNodes / Visitors have no Java counterpart.
        writeClassesImpl();

        writeVisitorsImpl();
    }
    // endregion

    // region WriteVisitors
    private void writeVisitorsImpl() {
        writeVisitorImpl();
        writeVisitorTImpl();
    }

    private void writeVisitorImpl() {
        writeFileStart("SyntaxVisitor", "visitor", List.of());
        m_writer.writeEmptyLineIfNeeded();
        // PORT: §4 abstract comes from the hand-written half (SyntaxVisitor.cs:10)
        m_writer.writeJavaScope("public abstract class SyntaxVisitor", () -> {
            for (var c : m_classes) {
                if (!c.Abstract) {
                    m_writer.writeLine("public abstract void visit" + c.Name + "(" + c.Name + " node);");
                }
            }
            writeHandWrittenRegions("SyntaxVisitor");
        });
        writeFileEnd("SyntaxVisitor");

        writeFileStart("DefaultSyntaxVisitor", "visitor", List.of());
        m_writer.writeEmptyLineIfNeeded();
        m_writer.writeJavaScope("public abstract class DefaultSyntaxVisitor extends SyntaxVisitor", () -> {
            m_writer.writeLine("protected abstract void defaultVisit(SyntaxNode node);");
            m_writer.writeEmptyLineIfNeeded();

            for (var c : m_classes) {
                if (!c.Abstract) {
                    // PORT: §4 one-line members (PORTING.md 4.3 layout)
                    m_writer.writeLine("@Override public void visit" + c.Name + "(" + c.Name + " node) { this.defaultVisit(node); }");
                }
            }
            writeHandWrittenRegions("DefaultSyntaxVisitor");
        });
        writeFileEnd("DefaultSyntaxVisitor");
    }

    private void writeVisitorTImpl() {
        // PORT: §2.4 SyntaxVisitor<TResult> is SyntaxVisitor1
        writeFileStart("SyntaxVisitor1", "visitor", List.of());
        m_writer.writeEmptyLineIfNeeded();
        m_writer.writeJavaScope("public abstract class SyntaxVisitor1<TResult>", () -> {
            for (var c : m_classes) {
                if (!c.Abstract) {
                    m_writer.writeLine("public abstract TResult visit" + c.Name + "(" + c.Name + " node);");
                }
            }
            writeHandWrittenRegions("SyntaxVisitor1");
        });
        writeFileEnd("SyntaxVisitor1");

        // PORT: §2.4 DefaultSyntaxVisitor<TResult> is DefaultSyntaxVisitor1
        writeFileStart("DefaultSyntaxVisitor1", "visitor", List.of());
        m_writer.writeEmptyLineIfNeeded();
        m_writer.writeJavaScope("public abstract class DefaultSyntaxVisitor1<TResult> extends SyntaxVisitor1<TResult>", () -> {
            m_writer.writeLine("protected abstract TResult defaultVisit(SyntaxNode node);");
            m_writer.writeEmptyLineIfNeeded();

            for (var c : m_classes) {
                if (!c.Abstract) {
                    m_writer.writeLine("@Override public TResult visit" + c.Name + "(" + c.Name + " node) { return this.defaultVisit(node); }");
                }
            }
            writeHandWrittenRegions("DefaultSyntaxVisitor1");
        });
        writeFileEnd("DefaultSyntaxVisitor1");
    }
    // endregion

    // region WriteClassesImpl
    private void writeClassesImpl() {
        // PORT: §4 upstream separates classes with an empty line and wraps each in "#region class X";
        // here each class is its own file.
        for (var c : m_classes) {
            writeFileStart(c.Name, "node", getImports(c));

            m_writer.writeEmptyLineIfNeeded();
            m_writer.writeJavaDocString(c.Doc, c.Remarks);
            m_writer.writeJavaScope("public " + getAbstractOrSealed(c) + "class " + c.Name + " extends " + c.Base, () -> {
                // #region Properties
                if (c.Properties != null /* && c.Properties.Length > 0 */) {
                    if (!c.Abstract) {
                        if (c.Kind != null) {
                            m_writer.writeLine("@Override public SyntaxKind kind() { return SyntaxKind." + c.Kind + "; }");
                        } else {
                            m_writer.writeLine("private final SyntaxKind kind;");
                            m_writer.writeLine("@Override public SyntaxKind kind() { return this.kind; }");
                        }
                    }

                    for (var property : c.Properties) {
                        // PORT: §3.1 auto-property: private field plus accessor (setter for PublicSetter).
                        // Abstract classes never assign their properties upstream (empty constructor body),
                        // so their fields cannot be final.
                        var type = getJavaType(property.Type);
                        var field = CodeGenerator.getJavaCamelCase(property.Name);
                        var isFinal = !property.PublicSetter && !c.Abstract;
                        m_writer.writeLine("private " + (isFinal ? "final " : "") + type + " " + field + ";");
                        m_writer.writeJavaDocLine(property.Doc/*, property.Remarks*/);
                        m_writer.writeLine("public " + type + " " + field + "() { return " + field + "; }");
                        if (property.PublicSetter) {
                            m_writer.writeLine("public void set" + property.Name + "(" + type + " value) { this." + field + " = value; }");
                        }
                    }
                }

                m_writer.writeEmptyLineIfNeeded();
                m_writer.writeLine("/** Constructs a new instance of {@link " + c.Name + "}. */", false); // PORT: §4 <see cref> is {@link}

                var lineage = getLineage(c);
                var props = new ArrayList<SyntaxNodeProperty[]>();
                for (var cslNode : lineage) {
                    props.add(cslNode.Properties);
                }
                var flattenedProps = new ArrayList<SyntaxNodeProperty>();
                for (var propsArray : props) {
                    Collections.addAll(flattenedProps, propsArray);
                }

                // PORT: §3.12 default values become telescoping overloads; collect (declaration, name, default).
                var parameters = new ArrayList<String[]>();
                if (c.Kind == null && !c.Abstract) {
                    parameters.add(new String[] { "SyntaxKind kind", "kind", null });
                }
                for (var property : flattenedProps) {
                    var name = CodeGenerator.getJavaCamelCase(property.Name);
                    parameters.add(new String[] { getJavaType(property.Type) + " " + name, name, property.DefaultValue });
                }
                parameters.add(new String[] { "List<Diagnostic> diagnostics", "diagnostics", "null" });

                var flattenedBaseProps = new ArrayList<SyntaxNodeProperty>();
                for (var propsArray : props.subList(0, props.size() - 1)) {
                    Collections.addAll(flattenedBaseProps, propsArray);
                }
                var baseArguments = new ArrayList<String>();
                for (var property : flattenedBaseProps) {
                    baseArguments.add(CodeGenerator.getJavaCamelCase(property.Name));
                }
                baseArguments.add("diagnostics");
                var baseParametersList = String.join(", ", baseArguments);

                m_writer.writeJavaScope("public " + c.Name + "(" + joinColumn(parameters, 0, parameters.size()) + ")", () -> { // PORT: §4.1 public, not internal
                    m_writer.writeLine("super(" + baseParametersList + ");");
                    if (!c.Abstract) {
                        if (c.Kind == null) {
                            m_writer.writeLine("this.kind = kind;");
                        }

                        for (var property : c.Properties) {
                            var field = CodeGenerator.getJavaCamelCase(property.Name);
                            if (property.IsSyntax) {
                                m_writer.writeLine("this." + field + " = attach(" + field + (property.Optional ? ", true" : "") + ");"); // PORT: §3.12 optional: true is positional
                            } else {
                                m_writer.writeLine("this." + field + " = " + field + ";");
                            }
                        }

                        m_writer.writeLine("this.init();");
                    }
                });

                // PORT: §3.12 one overload per trailing defaulted parameter, longest first.
                int firstDefault = parameters.size();
                while (firstDefault > 0 && parameters.get(firstDefault - 1)[2] != null) {
                    firstDefault--;
                }
                for (int keep = parameters.size() - 1; keep >= firstDefault; keep--) {
                    var arguments = new ArrayList<String>();
                    for (int i = 0; i < parameters.size(); i++) {
                        arguments.add(i < keep ? parameters.get(i)[1] : parameters.get(i)[2]);
                    }
                    m_writer.writeLine("public " + c.Name + "(" + joinColumn(parameters, 0, keep) + ") { this(" + String.join(", ", arguments) + "); }");
                }

                var syntaxProperties = new ArrayList<SyntaxNodeProperty>();
                for (var p : c.Properties) {
                    if (p.IsSyntax) {
                        syntaxProperties.add(p);
                    }
                }

                if (!c.Abstract /*&& c.Properties.Length > 0*/) {
                    // PORT: §4 one-line members without blank lines between them (PORTING.md 4.3 layout)
                    // ChildCount
                    m_writer.writeEmptyLineIfNeeded();
                    m_writer.writeLine("@Override public int childCount() { return " + syntaxProperties.size() + "; }");

                    // GetChild
                    var getChild = new StringBuilder("@Override public SyntaxElement getChild(int index) { switch (index) { ");
                    for (int i = 0; i < syntaxProperties.size(); i++) {
                        getChild.append("case ").append(i).append(": return ").append(CodeGenerator.getJavaCamelCase(syntaxProperties.get(i).Name)).append("; ");
                    }
                    getChild.append("default: throw new IndexOutOfBoundsException(); } }"); // PORT: §3.16
                    m_writer.writeLine(getChild.toString());

                    // GetName
                    var getName = new StringBuilder("@Override public String getName(int index) { switch (index) { ");
                    for (int i = 0; i < syntaxProperties.size(); i++) {
                        // PORT: §2.3 nameof(P) is the C# property name
                        getName.append("case ").append(i).append(": return \"").append(syntaxProperties.get(i).Name).append("\"; ");
                    }
                    getName.append("default: throw new IndexOutOfBoundsException(); } }"); // PORT: §3.16
                    m_writer.writeLine(getName.toString());

                    // IsOptional
                    if (syntaxProperties.stream().anyMatch(p -> p.Optional)) {
                        var isOptional = new StringBuilder("@Override public boolean isOptional(int index) { switch (index) { ");
                        for (int i = 0; i < syntaxProperties.size(); i++) {
                            if (syntaxProperties.get(i).Optional) {
                                isOptional.append("case ").append(i).append(": ");
                            }
                        }

                        isOptional.append("return true; ");

                        isOptional.append("default: return false; } }");
                        m_writer.writeLine(isOptional.toString());
                    }

                    // GetCompletionHint
                    if (syntaxProperties.stream().anyMatch(p -> p.Completion != null)) {
                        // PORT: §3.17 CompletionHint is a [Flags] int-constant holder, so the hint is an int
                        var hint = new StringBuilder("@Override protected int getCompletionHintCore(int index) { switch (index) { ");
                        for (int i = 0; i < syntaxProperties.size(); i++) {
                            if (syntaxProperties.get(i).Completion != null) {
                                hint.append("case ").append(i).append(": return CompletionHint.").append(syntaxProperties.get(i).Completion).append("; ");
                            }
                        }

                        hint.append("default: return CompletionHint.Inherit; } }");
                        m_writer.writeLine(hint.toString());
                    }
                }

                // #region CslNode implementation
                if (!c.Abstract) {
                    m_writer.writeLine("@Override public void accept(SyntaxVisitor visitor) { visitor.visit" + c.Name + "(this); }");

                    // PORT: §2.4 SyntaxVisitor<TResult> is SyntaxVisitor1
                    m_writer.writeLine("@Override public <TResult> TResult accept(SyntaxVisitor1<TResult> visitor) { return visitor.visit" + c.Name + "(this); }");

                    // PORT: §4 the "#if false" Accept<TContext, TResult> variant is not emitted upstream either.

                    if (c.CloneOptions != SyntaxNodeCloneOptions.Custom && c.Properties != null /*&& c.Properties.Length > 0*/) {
                        var args = new ArrayList<String>();
                        for (var p : c.Properties) {
                            var field = CodeGenerator.getJavaCamelCase(p.Name);
                            // PORT: §4 (T)P?.Clone(d) is "p != null ? (T) p.clone(d) : null"
                            args.add(p.IsSyntax ? field + " != null ? (" + getJavaType(p.Type) + ") " + field + ".clone(includeDiagnostics) : null" : field);
                        }

                        args.add("includeDiagnostics ? this.syntaxDiagnostics() : null");

                        if (c.Kind == null) {
                            // PORT: §4 this.Kind shares the first line with the first property (PORTING.md 4.3 layout)
                            args.set(0, "this.kind(), " + args.get(0));
                        }

                        m_writer.writeJavaScope("@Override @SuppressWarnings(\"unchecked\") protected SyntaxElement cloneCore(boolean includeDiagnostics)", () -> {
                            // PORT: §4 one argument per line after the first (PORTING.md 4.3 layout)
                            if (args.size() == 1) {
                                m_writer.writeLine("return new " + c.Name + "(" + args.get(0) + ");");
                                return;
                            }
                            m_writer.writeLine("return new " + c.Name + "(" + args.get(0) + ",");
                            m_writer.unsafeIndent();
                            for (int i = 1; i < args.size(); i++) {
                                m_writer.writeLine(args.get(i) + (i == args.size() - 1 ? ");" : ","));
                            }
                            m_writer.unsafeUnindent();
                        });
                    }
                }

                writeHandWrittenRegions(c.Name);
            });

            writeFileEnd(c.Name);
        }
    }

    private static String joinColumn(List<String[]> parameters, int from, int to) {
        var parts = new ArrayList<String>();
        for (int i = from; i < to; i++) {
            parts.add(parameters.get(i)[0]);
        }
        return String.join(", ", parts);
    }

    private boolean isDerivedFromCslNode(String type) {
        return m_nameToClass.containsKey(type);
    }

    private SyntaxNodeInfo getBaseOrNull(SyntaxNodeInfo c) {
        SyntaxNodeInfo ret = null;
        ret = m_nameToClass.get(c.Base);
        return ret;
    }

    private List<SyntaxNodeInfo> getLineage(SyntaxNodeInfo c) {
        var lineage = new ArrayList<SyntaxNodeInfo>(); // First is the ultimate base, last is us
        var current = c;
        while (current != null) {
            lineage.add(current);
            current = getBaseOrNull(current);
        }
        Collections.reverse(lineage);
        return lineage;
    }

    private String getAbstractOrSealed(SyntaxNodeInfo c) {
        if (c.Abstract) {
            if (c.Sealed) {
                throw new IllegalStateException(c.Name + ": A class can't be both abstract and sealed."); // PORT: §3.16 Exception
            }
            return "abstract "; // Note the trailing space
        } else if (c.Sealed) {
            return "final "; // Note the trailing space // PORT: §4 sealed is final
        }
        return "";
    }

    private String getPublicOrProtected(SyntaxNodeInfo c) {
        if (c.Abstract) {
            return "protected";
        } else {
            return "public";
        }
    }

    // PORT: §4 the Java type of a property (generators.md 3.4): §2.4 arity names for the generic syntax
    // lists, §3.17 for IReadOnlyList and the [Flags] enums CompletionHint and SymbolMatch (int-constant
    // holders, so their values are ints), string is String; node types pass through unchanged.
    static String getJavaType(String type) {
        switch (type) {
            case "string": return "String";
            case "IReadOnlyList<string>": return "List<String>";
            case "CompletionHint": return "int";
            case "Kusto.Language.Symbols.SymbolMatch": return "int";
            default:
                return GENERIC_SYNTAX_LIST.matcher(type).replaceAll(m -> m.group(1) + "1<");
        }
    }

    private static final Pattern GENERIC_SYNTAX_LIST = Pattern.compile("\\b(SyntaxList|SeparatedElement)<");

    // PORT: §4 imports used by the generated text of a node (PORTING.md 4.3: only when used).
    private List<String> getImports(SyntaxNodeInfo c) {
        var imports = new ArrayList<String>();
        imports.add(IMPORT_LIST); // the constructor's List<Diagnostic> diagnostics
        boolean completionHint = false;
        boolean symbolMatch = false;
        for (var cslNode : getLineage(c)) {
            for (var p : cslNode.Properties == null ? new SyntaxNodeProperty[0] : cslNode.Properties) {
                if (p.DefaultValue != null && p.DefaultValue.contains("CompletionHint.")) {
                    completionHint = true;
                }
                if (getJavaType(p.Type).equals("SymbolMatch") || (p.DefaultValue != null && p.DefaultValue.contains("SymbolMatch."))) {
                    symbolMatch = true;
                }
            }
        }
        if (!c.Abstract && c.Properties != null) {
            for (var p : c.Properties) {
                if (p.IsSyntax && p.Completion != null) {
                    completionHint = true;
                }
            }
        }
        imports.add(IMPORT_DIAGNOSTIC);
        if (completionHint) {
            imports.add(IMPORT_COMPLETION_HINT);
        }
        if (symbolMatch) {
            imports.add(IMPORT_SYMBOL_MATCH);
        }
        return imports;
    }

    // endregion
}
