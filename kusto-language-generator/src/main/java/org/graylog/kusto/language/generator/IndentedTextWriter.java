// Ported from: src/Kusto.Language.Generators/CodeGenerator.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.generator;

import java.io.PrintWriter;
import java.io.Writer;

/**
 * An implementation of {@code TextWriter} that can indent new lines
 * by a tbaStringToken
 *
 * <p>This code is copied from .NET's source code, as a temporary measure
 * to help porting to .NET Core 2.x.
 */
// PORT: §4 does not extend java.io.Writer: Writer.write(int) writes a char, while TextWriter.Write(int)
// writes the number. The inner writer is a PrintWriter, which never throws, like a StringWriter-backed
// TextWriter. Line terminators come from newLine ("\n"), never from the platform.
// PORT: §2.6 internal; package-private.
class IndentedTextWriter {
    private PrintWriter writer;
    private int indentLevel;
    private boolean tabsPending;
    private String tabString;
    private String newLine = "\n"; // PORT: §4 TextWriter.NewLine of the inner writer

    /**
     * [To be supplied.]
     */
    public static final String DefaultTabString = "    ";

    /**
     * Initializes a new instance of {@code System.CodeDom.Compiler.IndentedTextWriter} using the specified
     * text writer and default tab string.
     */
    public IndentedTextWriter(Writer writer) {
        this(writer, DefaultTabString);
    }

    /**
     * Initializes a new instance of {@code System.CodeDom.Compiler.IndentedTextWriter} using the specified
     * text writer and tab string.
     */
    public IndentedTextWriter(Writer writer, String tabString) {
        this.writer = new PrintWriter(writer); // PORT: §4 base(CultureInfo.InvariantCulture): Java formatting below is locale-free
        this.tabString = tabString;
        indentLevel = 0;
        tabsPending = false;
    }

    // PORT: §4 the Encoding property has no java.io counterpart and is not ported.

    /**
     * Gets or sets the new line character to use.
     */
    public String newLine() {
        return newLine;
    }

    public void setNewLine(String value) {
        newLine = value;
    }

    /**
     * Gets or sets the number of spaces to indent.
     */
    public int indent() {
        return indentLevel;
    }

    public void setIndent(int value) {
        assert value >= 0 : "Bogus Indent... probably caused by mismatched Indent++ and Indent--"; // PORT: §3.20 Debug.Assert
        if (value < 0) {
            value = 0;
        }
        indentLevel = value;
    }

    /**
     * Gets or sets the TextWriter to use.
     */
    public Writer innerWriter() {
        return writer;
    }

    String tabString() {
        return tabString;
    }

    /**
     * Closes the document being written to.
     */
    public void close() {
        writer.close();
    }

    /**
     * [To be supplied.]
     */
    public void flush() {
        writer.flush();
    }

    /**
     * [To be supplied.]
     */
    protected void outputTabs() {
        if (tabsPending) {
            for (int i = 0; i < indentLevel; i++) {
                writer.write(tabString);
            }
            tabsPending = false;
        }
    }

    /**
     * Writes a string
     * to the text stream.
     */
    public void write(String s) {
        outputTabs();
        if (s != null) { // PORT: §3.14 TextWriter.Write(null) writes nothing
            writer.write(s);
        }
    }

    /**
     * Writes the text representation of a Boolean value to the text stream.
     */
    public void write(boolean value) {
        outputTabs();
        writer.write(value ? "True" : "False"); // PORT: §4 bool.ToString()
    }

    /**
     * Writes a character to the text stream.
     */
    public void write(char value) {
        outputTabs();
        writer.write(value);
    }

    /**
     * Writes a
     * character array to the text stream.
     */
    public void write(char[] buffer) {
        outputTabs();
        writer.write(buffer);
    }

    /**
     * Writes a subarray
     * of characters to the text stream.
     */
    public void write(char[] buffer, int index, int count) {
        outputTabs();
        writer.write(buffer, index, count);
    }

    /**
     * Writes the text representation of a Double to the text stream.
     */
    public void write(double value) {
        outputTabs();
        writer.write(String.valueOf(value)); // PORT: §4 not round-trip identical to .NET; unused by the generator
    }

    /**
     * Writes the text representation of
     * a Single to the text
     * stream.
     */
    public void write(float value) {
        outputTabs();
        writer.write(String.valueOf(value)); // PORT: §4 not round-trip identical to .NET; unused by the generator
    }

    /**
     * Writes the text representation of an integer to the text stream.
     */
    public void write(int value) {
        outputTabs();
        writer.write(Integer.toString(value));
    }

    /**
     * Writes the text representation of an 8-byte integer to the text stream.
     */
    public void write(long value) {
        outputTabs();
        writer.write(Long.toString(value));
    }

    /**
     * Writes the text representation of an object
     * to the text stream.
     */
    public void write(Object value) {
        outputTabs();
        if (value != null) {
            writer.write(String.valueOf(value));
        }
    }

    /**
     * Writes out a formatted string, using the same semantics as specified.
     */
    public void write(String format, Object arg0) {
        outputTabs();
        writer.write(CodeGenerator.format(format, arg0)); // PORT: §4 C# composite format
    }

    /**
     * Writes out a formatted string,
     * using the same semantics as specified.
     */
    public void write(String format, Object arg0, Object arg1) {
        outputTabs();
        writer.write(CodeGenerator.format(format, arg0, arg1)); // PORT: §4 C# composite format
    }

    /**
     * Writes out a formatted string,
     * using the same semantics as specified.
     */
    public void write(String format, Object... arg) {
        outputTabs();
        writer.write(CodeGenerator.format(format, arg)); // PORT: §4 C# composite format
    }

    /**
     * Writes the specified
     * string to a line without tabs.
     */
    public void writeLineNoTabs(String s) {
        writer.write(s + newLine);
    }

    /**
     * Writes the specified string followed by
     * a line terminator to the text stream.
     */
    public void writeLine(String s) {
        outputTabs();
        writer.write((s == null ? "" : s) + newLine);
        tabsPending = true;
    }

    /**
     * Writes a line terminator.
     */
    public void writeLine() {
        outputTabs();
        writer.write(newLine);
        tabsPending = true;
    }

    /**
     * Writes the text representation of a Boolean followed by a line terminator to
     * the text stream.
     */
    public void writeLine(boolean value) {
        outputTabs();
        writer.write((value ? "True" : "False") + newLine); // PORT: §4 bool.ToString()
        tabsPending = true;
    }

    /**
     * [To be supplied.]
     */
    public void writeLine(char value) {
        outputTabs();
        writer.write(value + newLine);
        tabsPending = true;
    }

    /**
     * [To be supplied.]
     */
    public void writeLine(char[] buffer) {
        outputTabs();
        writer.write(new String(buffer) + newLine);
        tabsPending = true;
    }

    /**
     * [To be supplied.]
     */
    public void writeLine(char[] buffer, int index, int count) {
        outputTabs();
        writer.write(new String(buffer, index, count) + newLine);
        tabsPending = true;
    }

    /**
     * [To be supplied.]
     */
    public void writeLine(double value) {
        outputTabs();
        writer.write(value + newLine); // PORT: §4 not round-trip identical to .NET; unused by the generator
        tabsPending = true;
    }

    /**
     * [To be supplied.]
     */
    public void writeLine(float value) {
        outputTabs();
        writer.write(value + newLine); // PORT: §4 not round-trip identical to .NET; unused by the generator
        tabsPending = true;
    }

    /**
     * [To be supplied.]
     */
    public void writeLine(int value) {
        outputTabs();
        writer.write(value + newLine);
        tabsPending = true;
    }

    /**
     * [To be supplied.]
     */
    public void writeLine(long value) {
        outputTabs();
        writer.write(value + newLine);
        tabsPending = true;
    }

    /**
     * [To be supplied.]
     */
    public void writeLine(Object value) {
        outputTabs();
        writer.write((value == null ? "" : String.valueOf(value)) + newLine);
        tabsPending = true;
    }

    /**
     * [To be supplied.]
     */
    public void writeLine(String format, Object arg0) {
        outputTabs();
        writer.write(CodeGenerator.format(format, arg0) + newLine); // PORT: §4 C# composite format
        tabsPending = true;
    }

    /**
     * [To be supplied.]
     */
    public void writeLine(String format, Object arg0, Object arg1) {
        outputTabs();
        writer.write(CodeGenerator.format(format, arg0, arg1) + newLine); // PORT: §4 C# composite format
        tabsPending = true;
    }

    /**
     * [To be supplied.]
     */
    public void writeLine(String format, Object... arg) {
        outputTabs();
        writer.write(CodeGenerator.format(format, arg) + newLine); // PORT: §4 C# composite format
        tabsPending = true;
    }

    // PORT: §3.7 WriteLine(UInt32) has no Java counterpart (no unsigned int); writeLine(long) covers it.

    void internalOutputTabs() {
        for (int i = 0; i < indentLevel; i++) {
            writer.write(tabString);
        }
    }
}
