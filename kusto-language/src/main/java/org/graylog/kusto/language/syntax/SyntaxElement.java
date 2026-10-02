// Ported from: src/Kusto.Language/Syntax/SyntaxElement.cs
// Ported from: src/Kusto.Language/Syntax/SyntaxNode_Semantics.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.DiagnosticFacts;
import org.graylog.kusto.language.DiagnosticLocationKind;
import org.graylog.kusto.language.DiagnosticSeverity;
import org.graylog.kusto.language.binding.SemanticInfo;
import org.graylog.kusto.language.editor.CompletionHint;
import org.graylog.kusto.language.parsing.TextFacts;
import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.utils.CancellationToken;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.Ensure;
import org.graylog.kusto.language.utils.Interlocked;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.ObjectPool;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Linq;

// ===== upstream part: SyntaxElement.cs =====

/// <summary>
/// A basic element of syntax.
/// </summary>
// [System.Diagnostics.DebuggerDisplay("{Kind}: {DebugText}")] dropped // PORT: §3.20
public abstract class SyntaxElement
{
    /// <summary>
    /// Either the current parent node or the containing syntax tree.
    /// </summary>
    private Object _parentOrTree;

    /// <summary>
    /// State flags that combine up the tree.
    /// Each flag is either explicitly set of it is the union of all the child node's flags.
    /// </summary>
    private short flags; // PORT: §3.17 [Flags] enum Flags : short → short

    /// <summary>
    /// Addition information (diagnostics, semantic info) associated with this element
    /// </summary>
    private volatile ExtendedData extendedData; // PORT: §3.13 CAS-published
    private static final VarHandle EXTENDED_DATA = Interlocked.handle(SyntaxElement.class, "extendedData", ExtendedData.class); // PORT: §3.13

    /// <summary>
    /// Kind of token
    /// </summary>
    public SyntaxKind kind() { return SyntaxKind.None; }

    /// <summary>
    /// For debugger display only.
    /// </summary>
    private String debugText() { return this.toString(IncludeTrivia.Minimal, 100); } // PORT: §3.12

    // region initialization
    protected SyntaxElement(List<Diagnostic> diagnostics)
    {
        setDiagnostics(diagnostics);
    }

    /// <summary>
    /// Initializes the element (does one-time computations)
    /// </summary>
    protected void init()
    {
        int offset = 0;

        for (int i = 0, n = this.childCount(); i < n; i++)
        {
            var child = this.getChild(i);
            if (child != null)
            {
                child.setOffsetInParent(offset);
                child.setIndexInParent(i);
                offset += child.fullWidth();
            }
        }
    }

    /// <summary>
    /// Attaches the <see cref="SyntaxElement"/> as a child of this <see cref="SyntaxNode"/>.
    /// </summary>
    protected <TElement extends SyntaxElement> TElement attach(TElement element, boolean optional)
    {
        if (element != null)
        {
            if (((SyntaxElement)element)._parentOrTree != null) // PORT: §3.10 private members are not reachable through a type variable; access via the bound
            {
                throw new IllegalStateException("The syntax element already has a parent."); // PORT: §3.16
            }

            ((SyntaxElement)element)._parentOrTree = (SyntaxNode)this; // PORT: §3.10
            this.flags |= ((SyntaxElement)element).flags; // PORT: §3.10
        }
        else if (!optional)
        {
            throw new NullPointerException("The element is not optional"); // PORT: §3.16
        }

        return element;
    }

    protected <TElement extends SyntaxElement> TElement attach(TElement element) // PORT: §3.12
    {
        return attach(element, false);
    }

    @Internal
    public void setTree(SyntaxTree tree)
    {
        if (_parentOrTree != null)
        {
            if (_parentOrTree instanceof SyntaxTree)
                throw new IllegalStateException("Cannot assign tree. This node already has a syntax tree."); // PORT: §3.16
            throw new IllegalStateException("Cannot assign tree. This node is not the root element."); // PORT: §3.16
        }
        else
        {
            _parentOrTree = tree;
        }
    }
    // endregion

    // region diagnostics
    /// <summary>
    /// True if this <see cref="SyntaxElement"/> or any child element has syntax diagnostics.
    /// </summary>
    public boolean containsSyntaxDiagnostics() { return (this.flags & Flags.ContainsDiagnostics) != 0; }

    /// <summary>
    /// True if this element has syntax diagnostics.
    /// </summary>
    public boolean hasSyntaxDiagnostics() { return this.containsSyntaxDiagnostics() && this.syntaxDiagnostics().size() > 0; }

    /// <summary>
    /// All syntax diagnostics located at this element.
    /// </summary>
    public List<Diagnostic> syntaxDiagnostics()
    {
        var data = this.getExtendedData(false); // PORT: §3.14
        var dx = data != null ? data.SyntaxDiagnostics : null;
        return dx != null ? dx : Diagnostic.NoDiagnostics;
    }

    /// <summary>
    /// Gets syntax diagnostics for this <see cref="SyntaxElement"/> an all child elements.
    /// </summary>
    public List<Diagnostic> getContainedSyntaxDiagnostics()
    {
        if (this.containsSyntaxDiagnostics())
        {
            var diagnostics = new ArrayList<Diagnostic>();
            gatherDiagnostics(this, diagnostics, DiagnosticsInclude.Syntactic);
            return Collections.unmodifiableList(diagnostics); // PORT: §3.17 AsReadOnly
        }
        else
        {
            return Diagnostic.NoDiagnostics;
        }
    }

    private void setDiagnostics(List<Diagnostic> diagnostics)
    {
        // bind diagnostics to this element
        // this function should only be called once during construction of this node.
        if (diagnostics != null && diagnostics.size() > 0)
        {
            this.getExtendedData(true).SyntaxDiagnostics = ListExtensions.toReadOnly(diagnostics); // PORT: §3.5
            this.flags |= Flags.ContainsDiagnostics;
        }
    }

    /// <summary>
    /// Creates a copy of this <see cref="SyntaxElement"/> with the specified diagnostics.
    /// </summary>
    public SyntaxElement withDiagnostics(Iterable<Diagnostic> diagnostics)
    {
        var clone = this.clone();
        clone.setDiagnostics(Collections.unmodifiableList(Linq.toList(diagnostics))); // PORT: §3.6
        return clone;
    }

    /// <summary>
    /// Creates a copy of this <see cref="SyntaxElement"/> with the specified diagnostics added.
    /// </summary>
    public SyntaxElement withAdditionalDiagnostics(Iterable<Diagnostic> diagnostics)
    {
        return this.withDiagnostics(Linq.concat(this.syntaxDiagnostics(), diagnostics)); // PORT: §3.6
    }

    /// <summary>
    /// Creates a copy of this <see cref="SyntaxElement"/> with the specified diagnostics added.
    /// </summary>
    public SyntaxElement withAdditionalDiagnostics(Diagnostic... diagnostics)
    {
        return this.withDiagnostics(Linq.concat(this.syntaxDiagnostics(), Arrays.asList(diagnostics))); // PORT: §3.6
    }

    // [Flags]
    private static final class Flags // PORT: §3.17 [Flags] enum : short → short constants holder
    {
        static final short ContainsDiagnostics = 0x1;
    }

    @Internal
    public static class ExtendedData
    {
        public List<Diagnostic> SyntaxDiagnostics;
        public SemanticInfo SemanticInfo;
    }

    @Internal
    public ExtendedData getExtendedData(boolean create)
    {
        var data = this.extendedData;
        if (data == null && create)
        {
            var tmp = new ExtendedData();
            Interlocked.compareExchange(EXTENDED_DATA, this, tmp, null); // PORT: §3.13
            data = this.extendedData;
        }

        return data;
    }

    // endregion

    /// <summary>
    /// True if the <see cref="SyntaxElement"/> is a <see cref="SyntaxToken"/>.
    /// </summary>
    public boolean isToken() { return false; }

    /// <summary>
    /// True if the element is taking the place of a missing element.
    /// </summary>
    public boolean isMissing() { return this.width() == 0 && this.containsSyntaxDiagnostics(); }

    // region navigation

    /// <summary>
    /// The parent node of this element.
    /// </summary>
    public SyntaxNode parent() { return _parentOrTree instanceof SyntaxNode n ? n : null; }

    /// <summary>
    /// The root element
    /// </summary>
    public SyntaxElement root()
    {
        var element = this;
        var parent = element.parent();

        while (parent != null)
        {
            element = parent;
            parent = element.parent();
        }

        return element;
    }

    /// <summary>
    /// The <see cref="SyntaxTree"/> that contains this <see cref="SyntaxElement"/>.
    /// </summary>
    public SyntaxTree tree()
    {
        var element = this;
        while (element._parentOrTree != null)
        {
            if (element._parentOrTree instanceof SyntaxTree tree)
                return tree;

            element = element._parentOrTree instanceof SyntaxNode n ? n : null; // PORT: §3.15 'as'
        }

        return null;
    }

    /// <summary>
    /// Index of this element in parent node's child elements.
    /// </summary>
    private int indexInParent;
    public int indexInParent() { return this.indexInParent; }
    private void setIndexInParent(int value) { this.indexInParent = value; }

    /// <summary>
    /// The number of immediate child elements this element has.
    /// </summary>
    public int childCount() { return 0; }

    /// <summary>
    /// Get the child element of this node at the specified index.
    /// </summary>
    public SyntaxElement getChild(int index) { throw new IndexOutOfBoundsException(); } // PORT: §3.16

    // #if DEBUG private SyntaxElement[] Children (debugger-only property) dropped // PORT: §3.20

    /// <summary>
    /// True if the child element at the specified index is optional and may contain a null value.
    /// </summary>
    public boolean isOptional(int index) { return false; }

    /// <summary>
    /// Gets the name of the child element at the specified index.
    /// </summary>
    public String getName(int index) { return ""; }

    /// <summary>
    /// The <see cref="CompletionHint"/> to use for this child element index in the syntax tree.
    /// </summary>
    public int getCompletionHint(int index) { return this.getCompletionHintCore(index); } // PORT: §3.17 CompletionHint is an int holder (D23)

    /// <summary>
    /// The generated <see cref="CompletionHint"/> to use for this child element index in the syntax tree.
    /// </summary>
    protected int getCompletionHintCore(int index) { return CompletionHint.Inherit; } // PORT: §3.17

    /// <summary>
    /// True if the element or any of its descendants have missing children
    /// </summary>
    public boolean hasMissingChildren()
    {
        if (this.containsSyntaxDiagnostics())
        {
            for (int n = this.childCount() - 1; n >= 0; n--)
            {
                var child = this.getChild(n);

                // TODO: redo this to not be recursive
                if (child != null && (child.isMissing() || child.hasMissingChildren()))
                    return true;
            }
        }

        return false;
    }

    /// <summary>
    /// Gets the index of the element within this element's childred, or -1 if the element is not a child of this element.
    /// </summary>
    public int getChildIndex(SyntaxElement child)
    {
        if (child.parent() == this)
        {
            return child.indexInParent();
        }
        else
        {
            return -1;
        }

// #if false
//          for (int i = 0, n = this.ChildCount; i < n; i++)
//          {
//              if (this.GetChild(i) == child)
//                  return i;
//          }
//
//          return -1;
// #endif
    }

    /// <summary>
    /// The depth of this node below the root.
    /// </summary>
    public int depth()
    {
        int depth = 0;

        for (var element = this; element.parent() != null; element = element.parent())
        {
            depth++;
        }

        return depth;
    }

    /// <summary>
    /// Gets the common ancestor between two elements a and b.
    /// </summary>
    public static SyntaxNode getCommonAncestor(SyntaxElement a, SyntaxElement b)
    {
        if (a == null || b == null)
            return null;

        var da = a.depth();
        var db = b.depth();

        while (da > db && da > 0)
        {
            a = a.parent();
            da--;
        }

        while (db > da && db > 0)
        {
            b = b.parent();
            db--;
        }

        if (da > 0 && a.parent() == b.parent())
        {
            return a.parent();
        }

        return null;
    }

    /// <summary>
    /// Gets the child node index for the subtree that the descendant is part of
    /// </summary>
    public int getDescendantIndex(SyntaxElement descendant)
    {
        if (descendant == null)
            return -1;

        if (descendant.parent() != this)
        {
            var d = this.depth();
            var dd = descendant.depth();

            while (dd > d + 1)
            {
                descendant = descendant.parent();
                dd--;
            }
        }

        if (descendant.parent() == this)
        {
            return descendant.indexInParent();
        }
        else
        {
            return -1;
        }
    }


    /// <summary>
    /// Returns true if this element is the ancestor of the specified element.
    /// </summary>
    public boolean isAncestorOf(SyntaxElement element)
    {
        while (element != null)
        {
            if (element.parent() == this)
                return true;

            element = element.parent();
        }

        return false;
    }

    /// <summary>
    /// The name of the element given by the parent.
    /// </summary>
    public String nameInParent()
    {
        if (this.parent() != null)
        {
            return this.parent().getName(this.parent().getChildIndex(this));
        }
        else
        {
            return "";
        }
    }

    /// <summary>
    /// Gets the first ancestor of this element that matches the specified type and predicate.
    /// </summary>
    public <TElement extends SyntaxElement> TElement getFirstAncestor(Class<TElement> type, Predicate<TElement> predicate) // PORT: §3.10
    {
        for (SyntaxElement elem = this.parent(); elem != null; elem = elem.parent())
        {
            if (type.isInstance(elem)) // PORT: §3.10
            {
                TElement e = type.cast(elem);
                if (predicate == null || predicate.test(e))
                {
                    return e;
                }
            }
        }

        return null; // PORT: §3.10 default(TElement)
    }

    public <TElement extends SyntaxElement> TElement getFirstAncestor(Class<TElement> type) // PORT: §3.12
    {
        return getFirstAncestor(type, null);
    }

    /// <summary>
    /// Gets the first ancestor of this element (including itself) that matches the specified type and predicate.
    /// </summary>
    public <TElement extends SyntaxElement> TElement getFirstAncestorOrSelf(Class<TElement> type, Predicate<TElement> predicate) // PORT: §3.10
    {
        for (var elem = this; elem != null; elem = elem.parent())
        {
            if (type.isInstance(elem)) // PORT: §3.10
            {
                TElement e = type.cast(elem);
                if (predicate == null || predicate.test(e))
                {
                    return e;
                }
            }
        }

        return null; // PORT: §3.10 default(TElement)
    }

    public <TElement extends SyntaxElement> TElement getFirstAncestorOrSelf(Class<TElement> type) // PORT: §3.12
    {
        return getFirstAncestorOrSelf(type, null);
    }

    /// <summary>
    /// Gets the all ancestors of this element (including itself) that match the specified type and predicate.
    /// </summary>
    public <TElement extends SyntaxElement> List<TElement> getAncestors(Class<TElement> type, Predicate<TElement> predicate) // PORT: §3.10
    {
        List<TElement> list = null;

        for (SyntaxElement elem = this.parent(); elem != null; elem = elem.parent())
        {
            if (type.isInstance(elem)) // PORT: §3.10
            {
                TElement e = type.cast(elem);
                if (predicate == null || predicate.test(e))
                {
                    if (list == null)
                    {
                        list = new ArrayList<TElement>();
                    }

                    list.add(e);
                }
            }
        }

        return list != null ? ListExtensions.toReadOnly(list) : emptyList(); // PORT: §3.5, §3.10
    }

    public <TElement extends SyntaxElement> List<TElement> getAncestors(Class<TElement> type) // PORT: §3.12
    {
        return getAncestors(type, null);
    }

    /// <summary>
    /// Gets the all ancestors of this element (including itself) that match the specified type and predicate.
    /// </summary>
    public <TElement extends SyntaxElement> List<TElement> getAncestorsOrSelf(Class<TElement> type, Predicate<TElement> predicate) // PORT: §3.10
    {
        List<TElement> list = null;

        for (var elem = this; elem != null; elem = elem.parent())
        {
            if (type.isInstance(elem)) // PORT: §3.10
            {
                TElement e = type.cast(elem);
                if (predicate == null || predicate.test(e))
                {
                    if (list == null)
                    {
                        list = new ArrayList<TElement>();
                    }

                    list.add(e);
                }
            }
        }

        return list != null ? ListExtensions.toReadOnly(list) : emptyList(); // PORT: §3.5, §3.10
    }

    public <TElement extends SyntaxElement> List<TElement> getAncestorsOrSelf(Class<TElement> type) // PORT: §3.12
    {
        return getAncestorsOrSelf(type, null);
    }

    /// <summary>
    /// Gets the first descendant of this element that matches the specified type and predicate.
    /// </summary>
    public <TElement extends SyntaxElement> TElement getFirstDescendant(Class<TElement> type, Predicate<TElement> predicate) // PORT: §3.10
    {
        return getFirstDescendant(type, this, predicate, false); // PORT: §3.12
    }

    public <TElement extends SyntaxElement> TElement getFirstDescendant(Class<TElement> type) // PORT: §3.12
    {
        return getFirstDescendant(type, (Predicate<TElement>)null);
    }

    /// <summary>
    /// Gets the first descendant of this element (including itself) that matches the specified type and predicate.
    /// </summary>
    public <TElement extends SyntaxElement> TElement getFirstDescendantOrSelf(Class<TElement> type, Predicate<TElement> predicate) // PORT: §3.10
    {
        return getFirstDescendant(type, this, predicate, true); // PORT: §3.12
    }

    public <TElement extends SyntaxElement> TElement getFirstDescendantOrSelf(Class<TElement> type) // PORT: §3.12
    {
        return getFirstDescendantOrSelf(type, null);
    }

    private static <TElement extends SyntaxElement> TElement getFirstDescendant(Class<TElement> type, SyntaxElement element, Predicate<TElement> predicate, boolean includeSelf) // PORT: §3.10
    {
        if (includeSelf && type.isInstance(element)) // PORT: §3.10
        {
            TElement telem = type.cast(element);
            if (predicate == null || predicate.test(telem))
            {
                return telem;
            }
        }

        var root = element;
        var childIndex = 0;

        while (element != null)
        {
            if (childIndex < element.childCount() && childIndex >= 0)
            {
                // walk down
                var child = element.getChild(childIndex);
                if (child != null)
                {
                    element = child;
                    childIndex = 0;

                    if (type.isInstance(element)) // PORT: §3.10
                    {
                        TElement telem2 = type.cast(element);
                        if (predicate == null || predicate.test(telem2))
                        {
                            return telem2;
                        }
                    }
                }
                else
                {
                    childIndex++;
                }
            }
            else if (element == root)
            {
                break;
            }
            else
            {
                // walk up
                childIndex = element.indexInParent() + 1;
                element = element.parent();
            }
        }

        return null;
    }

    /// <summary>
    /// Gets all descendants of this element that match the specified type and predicate.
    /// </summary>
    public <TElement extends SyntaxElement> List<TElement> getDescendants(Class<TElement> type, Predicate<TElement> predicate) // PORT: §3.10
    {
        return getDescendants(type, this, predicate, false); // PORT: §3.12
    }

    public <TElement extends SyntaxElement> List<TElement> getDescendants(Class<TElement> type) // PORT: §3.12
    {
        return getDescendants(type, (Predicate<TElement>)null);
    }

    /// <summary>
    /// Gets all descendants of this element (including itself) that match the specified type and predicate.
    /// </summary>
    public <TElement extends SyntaxElement> List<TElement> getDescendantsOrSelf(Class<TElement> type, Predicate<TElement> predicate) // PORT: §3.10
    {
        return getDescendants(type, this, predicate, true); // PORT: §3.12
    }

    public <TElement extends SyntaxElement> List<TElement> getDescendantsOrSelf(Class<TElement> type) // PORT: §3.12
    {
        return getDescendantsOrSelf(type, null);
    }

    /// <summary>
    /// Gets the descendants of the specified element that match the specified type and predicate.
    /// </summary>
    private static <TElement extends SyntaxElement> List<TElement> getDescendants( // PORT: §3.10
        Class<TElement> type,
        SyntaxElement element, 
        Predicate<TElement> predicate, 
        boolean includeSelf)
    {
        List<TElement> list = null;

        if (includeSelf && type.isInstance(element)) // PORT: §3.10
        {
            TElement telem = type.cast(element);
            if (predicate == null || predicate.test(telem))
            {
                list = list != null ? list : new ArrayList<TElement>(); // PORT: §3.14
                list.add(telem);
            }
        }

        var root = element;
        var childIndex = 0;

        while (element != null)
        {
            if (childIndex < element.childCount() && childIndex >= 0)
            {
                // walk down
                var child = element.getChild(childIndex);
                if (child != null)
                {
                    element = child;
                    childIndex = 0;

                    if (type.isInstance(element)) // PORT: §3.10
                    {
                        TElement telem2 = type.cast(element);
                        if (predicate == null || predicate.test(telem2))
                        {
                            list = list != null ? list : new ArrayList<TElement>(); // PORT: §3.14
                            list.add(telem2);
                        }
                    }
                }
                else
                {
                    childIndex++;
                }
            }
            else if (element == root)
            {
                break;
            }
            else
            {
                // walk up
                childIndex = element.indexInParent() + 1;
                element = element.parent();
            }
        }

        return list != null ? ListExtensions.toReadOnly(list) : emptyList(); // PORT: §3.5, §3.10
    }

    /// <summary>
    /// Gets all the tokens contained by this <see cref="SyntaxElement"/> in lexical order.
    /// </summary>
    public List<SyntaxToken> getTokens(boolean includeZeroWidthTokens)
    {
        var tokens = new ArrayList<SyntaxToken>();

        SyntaxToken token = null;
        while ((token = getNextToken(this, token, includeZeroWidthTokens)) != null)
        {
            tokens.add(token);
        }

        return ListExtensions.toReadOnly(tokens); // PORT: §3.5
    }

    public List<SyntaxToken> getTokens() // PORT: §3.12
    {
        return getTokens(false);
    }

    /// <summary>
    /// Invokes the action for each token contained by this <see cref="SyntaxElement"/>
    /// </summary>
    public void walkTokens(Consumer<SyntaxToken> action)
    {
        walkTokens(this.triviaStart(), this.end(), action);
    }

    /// <summary>
    /// Invokes the action for each token contained by this <see cref="SyntaxElement"/>
    /// between the <see cref="p:start"/> and <see cref="p:end"/> text position.
    /// </summary>
    public void walkTokens(int start, int end, Consumer<SyntaxToken> action)
    {
        start = Math.max(start, this.triviaStart());
        end = Math.min(end, this.end());

        if (start < end)
        {
            for (var token = this.getTokenAt(start);
                token != null && token.triviaStart() < end;
                token = getNextToken(this, token, false)) // PORT: §3.12
            {
                action.accept(token);
            }
        }
    }

    /// <summary>
    /// Invokes the action for the element and its descendants, in lexical order, top down.
    /// </summary>
    /// <param name="action">The action that is invoked for each <see cref="SyntaxElement"/></param>
    public void walkElements(Consumer<SyntaxElement> action)
    {
        walkElements(this, action);
    }

    /// <summary>
    /// Walks this element and its descendants in lexical order, invoking the actions for each <see cref="SyntaxElement"/> including the root element.
    /// </summary>
    /// <param name="root">The root element of the walk. The walk includes this element and any descendant elements.</param>
    /// <param name="fnBefore">An optional function that is invoked for each element before any child elements are visited.</param>
    /// <param name="fnAfter">An optional function that is invoked for each element after any child elements have been visited.</param>
    /// <param name="fnDescend">An optional function that determines whether the children of an element are visited.</param>
    public static void walkElements(
        SyntaxElement root, 
        Consumer<SyntaxElement> fnBefore, 
        Consumer<SyntaxElement> fnAfter,
        Predicate<SyntaxElement> fnDescend)
    {
        if (root == null)
            throw new NullPointerException("root" /* nameof */); // PORT: §3.16

        var node = root;
        var childIndex = 0;

        // the root before walking children
        if (fnBefore != null) fnBefore.accept(root); // PORT: §3.14

        while (node != null)
        {
            if (childIndex < node.childCount() && childIndex >= 0 && (fnDescend == null || fnDescend.test(node)))
            {
                // walk down
                var child = node.getChild(childIndex);
                if (child != null)
                {
                    node = child;
                    childIndex = 0;

                    // before walking children
                    if (fnBefore != null) fnBefore.accept(node); // PORT: §3.14
                }
                else
                {
                    childIndex++;
                }
            }
            else
            {
                // after walking children
                if (fnAfter != null) fnAfter.accept(node); // PORT: §3.14

                // stop if we are done with root node
                if (node == root)
                    break;

                // walk up
                childIndex = node.indexInParent() + 1;
                node = node.parent();
            }
        }
    }

    public static void walkElements(SyntaxElement root, Consumer<SyntaxElement> fnBefore, Consumer<SyntaxElement> fnAfter) // PORT: §3.12
    {
        walkElements(root, fnBefore, fnAfter, null);
    }

    public static void walkElements(SyntaxElement root, Consumer<SyntaxElement> fnBefore) // PORT: §3.12
    {
        walkElements(root, fnBefore, null);
    }

    public static void walkElements(SyntaxElement root) // PORT: §3.12
    {
        walkElements(root, null);
    }

    /// <summary>
    /// Walks this node and its descendants in lexical order, invoking the actions for each <see cref="SyntaxElement"/> including the root node.
    /// </summary>
    /// <param name="root">The root node of the walk. The walk includes this node and any descendant nodes.</param>
    /// <param name="fnBefore">An optional function that is invoked for each node before any child nodes are visited.</param>
    /// <param name="fnAfter">An optional function that is invoked for each node after any child nodes have been visited.</param>
    /// <param name="fnDescend">An optional function that determines whether the child nodes of an node are visited.</param>
    public static void walkNodes(
        SyntaxNode root,
        Consumer<SyntaxNode> fnBefore,
        Consumer<SyntaxNode> fnAfter,
        Predicate<SyntaxNode> fnDescend)
    {
        if (root == null)
            throw new NullPointerException("root" /* nameof */); // PORT: §3.16

        var node = root;
        var childIndex = 0;

        // the root before walking children
        if (fnBefore != null) fnBefore.accept(root); // PORT: §3.14

        while (node != null)
        {
            if (childIndex < node.childCount() && childIndex >= 0 && (fnDescend == null || fnDescend.test(node)))
            {
                // walk down
                var child = node.getChild(childIndex) instanceof SyntaxNode sn ? sn : null; // PORT: §3.15 'as'
                if (child != null)
                {
                    node = child;
                    childIndex = 0;

                    // before walking children
                    if (fnBefore != null) fnBefore.accept(node); // PORT: §3.14
                }
                else
                {
                    childIndex++;
                }
            }
            else
            {
                // after walking children
                if (fnAfter != null) fnAfter.accept(node); // PORT: §3.14

                // stop if we are done with root node
                if (node == root)
                    break;

                // walk up
                childIndex = node.indexInParent() + 1;
                node = node.parent();
            }
        }
    }

    public static void walkNodes(SyntaxNode root, Consumer<SyntaxNode> fnBefore, Consumer<SyntaxNode> fnAfter) // PORT: §3.12
    {
        walkNodes(root, fnBefore, fnAfter, null);
    }

    public static void walkNodes(SyntaxNode root, Consumer<SyntaxNode> fnBefore) // PORT: §3.12
    {
        walkNodes(root, fnBefore, null);
    }

    public static void walkNodes(SyntaxNode root) // PORT: §3.12
    {
        walkNodes(root, null);
    }

    /// <summary>
    /// Gets the next <see cref="SyntaxElement"/> sibling of this element or null if there is no next sibling.
    /// </summary>
    public SyntaxElement getNextSibling(boolean includeZeroWidthElements)
    {
        if (this.parent() != null)
        {
            for (int i = this.indexInParent() + 1, n = this.parent().childCount(); i < n && i >= 0; i++)
            {
                var sibling = this.parent().getChild(i);
                if (sibling != null && (includeZeroWidthElements || sibling.fullWidth() > 0))
                    return sibling;
            }
        }

        return null;
    }

    public SyntaxElement getNextSibling() // PORT: §3.12
    {
        return getNextSibling(false);
    }

    /// <summary>
    /// Gets the previous <see cref="SyntaxElement"/> sibling of this element or null if there is no previous sibling.
    /// </summary>
    public SyntaxElement getPreviousSibling(boolean includeZeroWidthElements)
    {
        if (this.parent() != null)
        {
            for (int i = this.indexInParent() - 1; i >= 0; i--)
            {
                var sibling = this.parent().getChild(i);
                if (sibling != null && (includeZeroWidthElements || sibling.fullWidth() > 0))
                    return sibling;
            }
        }

        return null;
    }

    public SyntaxElement getPreviousSibling() // PORT: §3.12
    {
        return getPreviousSibling(false);
    }

    /// <summary>
    /// Gets the first descendant token of this <see cref="SyntaxElement"/> in lexical order.
    /// </summary>
    public SyntaxToken getFirstToken(boolean includeZeroWidthTokens)
    {
        return getNextToken(this, null, includeZeroWidthTokens);
    }

    public SyntaxToken getFirstToken() // PORT: §3.12
    {
        return getFirstToken(false);
    }

    /// <summary>
    /// Gets the last descendant token of this <see cref="SyntaxElement"/> in lexical order.
    /// </summary>
    public SyntaxToken getLastToken(boolean includeZeroWidthTokens)
    {
        return getPreviousToken(this, null, includeZeroWidthTokens);
    }

    public SyntaxToken getLastToken() // PORT: §3.12
    {
        return getLastToken(false);
    }

    protected static SyntaxToken getNextToken(SyntaxElement root, SyntaxToken token, boolean includeZeroWidthTokens)
    {
        SyntaxElement node = token != null ? token.parent() : root;
        var childIndex = token != null ? token.indexInParent() + 1: 0;

        while (node != null)
        {
            if (childIndex < node.childCount() && childIndex >= 0)
            {
                var child = node.getChild(childIndex);
                if (child != null)
                {
                    node = child;
                    childIndex = 0;

                    if (node instanceof SyntaxToken t && (includeZeroWidthTokens || t.fullWidth() > 0))
                    {
                        return t;
                    }
                }
                else
                {
                    childIndex++;
                }
            }
            else if (node == root)
            {
                return null;
            }
            else
            {
                childIndex = node.indexInParent() + 1;
                node = node.parent();
            }
        }

        return null;
    }

    protected static SyntaxToken getPreviousToken(SyntaxElement root, SyntaxToken token, boolean includeZeroWidthTokens)
    {
        SyntaxElement node = token != null ? token.parent() : root;
        var childIndex = token != null ? token.indexInParent() - 1 : root.childCount() - 1;

        while (node != null)
        {
            if (childIndex < node.childCount() && childIndex >= 0)
            {
                var child = node.getChild(childIndex);
                if (child != null)
                {
                    node = child;
                    childIndex = node.childCount() - 1;

                    if (node instanceof SyntaxToken t && (includeZeroWidthTokens || t.fullWidth() > 0))
                    {
                        return t;
                    }
                }
                else
                {
                    childIndex--;
                }
            }
            else if (node == root)
            {
                return null;
            }
            else
            {
                childIndex = node.indexInParent() - 1;
                node = node.parent();
            }
        }

        return null;
    }

    /// <summary>
    /// Gets the token at the specified position in the source text.
    /// If the position is within trivia, it will find the next token after the trivia.
    /// If the position is past the end of the tree, it will return the last token.
    /// </summary>
    public SyntaxToken getTokenAt(int position)
    {
        var element = this;

        if (this.isToken())
        {
            if (this.triviaStart() <= position && position < this.end())
                return (SyntaxToken)this;
        }
        else
        {
            element = this.root();
        }

        if (position >= element.fullWidth())
        {
            return element.getLastToken(true); // PORT: §3.12
        }

        // drill down until we find the token that covers this position.
    retry:
        while (true) // PORT: §3.15 goto retry → labelled loop
        {
            if (element != null && element.childCount() > 0)
            {
                for (int i = 0, n = element.childCount(); i < n; i++)
                {
                    var child = element.getChild(i);
                    if (child != null)
                    {
                        if (child.triviaStart() <= position && position < child.end())
                        {
                            if (child.isToken())
                                return (SyntaxToken)child;

                            element = child;
                            continue retry;
                        }
                    }
                }
            }

            return null;
        }
    }

    /// <summary>
    /// Gets the node that spans the specified range in the source text.
    /// </summary>
    /// <param name="position"></param>
    /// <param name="length"></param>
    public SyntaxNode getNodeAt(int position, int length)
    {
        var token = getTokenAt(position);
        var parent = token.parent();

        while (parent != null && (parent.end() < position + length || !(parent instanceof SyntaxNode)))
        {
            parent = parent.parent();
        }

        return parent instanceof SyntaxNode sn ? sn : null; // PORT: §3.15 'as'
    }
    // endregion

    // region bounds
    /// <summary>
    /// The position in the source of the start of the leading trivia.
    /// </summary>
    public int triviaStart() { return _triviaStart >= 0 ? _triviaStart : computeTriviaStart(); }

    /// <summary>
    /// The position in the source of the start of the leading trivia.
    /// </summary>
    private int _triviaStart = -1;

    @Internal
    public void initializeTriviaStarts()
    {
        SyntaxElement.walkElements(
            this.root(),
            element -> // PORT: §3.12 named argument fnBefore
            {
                if (Ensure.ENABLED) Ensure.isTrue(element.parent() == null || ((SyntaxElement)element.parent())._triviaStart >= 0); // PORT: §3.20 Debug.Assert; §3.10 private field via SyntaxElement
                element._triviaStart = (element.parent() != null ? ((SyntaxElement)element.parent())._triviaStart : 0) + element.offsetInParent(); // PORT: §3.14; §3.10 private field via SyntaxElement
            });
    }

    protected int computeTriviaStart()
    {
        if (this.parent() == null)
        {
            return 0;
        }
        else if (this.parent().parent() == null)
        {
            return this.offsetInParent();
        }
        else
        {
            var totalOffset = 0;

            for (var node = this; node != null; node = node.parent())
            {
                totalOffset += node.offsetInParent();
            }

            return totalOffset;
        }
    }

    /// <summary>
    /// The full width (in characters) of this element including leading trivia.
    /// </summary>
    public int fullWidth() { return 0; }

    protected int computeFullWidth()
    {
        int width = 0;

        for (int i = 0, n = this.childCount(); i < n; i++)
        {
            var child = this.getChild(i);
            if (child != null)
            {
                width += child.fullWidth();
            }
        }

        return width;
    }

    /// <summary>
    /// The width (in characters) of the leading trivia.
    /// </summary>
    public int triviaWidth()
    {
        var first = this.getFirstToken(); // PORT: §3.14
        return first != null ? first.triviaWidth() : 0;
    }

    /// <summary>
    /// The position in the source where the element's first token text starts.
    /// </summary>
    public int textStart() { return this.triviaStart() + this.triviaWidth(); }

    /// <summary>
    /// The position in the source immediately after this element.
    /// </summary>
    public int end() { return this.triviaStart() + this.fullWidth(); }

    /// <summary>
    /// The width (in characters) of this element, not including trivia.
    /// </summary>
    public int width() { return this.fullWidth() - this.triviaWidth(); }

    /// <summary>
    /// The offset in characters of this element from the start of the parent element.
    /// </summary>
    private int offsetInParent;
    protected int offsetInParent() { return this.offsetInParent; }
    private void setOffsetInParent(int value) { this.offsetInParent = value; }
    // endregion

    // region clone
    /// <summary>
    /// Creates a copy of this <see cref="SyntaxElement"/>
    /// </summary>
    public SyntaxElement clone(boolean includeDiagnostics) { return cloneCore(includeDiagnostics); }

    @Override
    public SyntaxElement clone() { return clone(true); } // PORT: §3.12 (overrides Object.clone, never throws)

    protected abstract SyntaxElement cloneCore(boolean includeDiagnostics);
    // endregion

    // region ToString
    @Override
    public String toString()
    {
        return this.toString(IncludeTrivia.All);
    }

    public String toString(IncludeTrivia includeTrivia)
    {
        return toString(includeTrivia, Integer.MAX_VALUE);
    }

    public String toString(IncludeTrivia includeTrivia, int maxLength)
    {
        var builder = new StringBuilder();
        var start = this.triviaStart();

        this.walkTokens(token ->
        {
            if (builder.length() < maxLength)
            {
                token.write(builder, includeTrivia, start);
            }
        });

        if (builder.length() > maxLength)
        {
            builder.setLength(maxLength);
        }
        
        return builder.toString();
    }

    // endregion

    @SuppressWarnings("unchecked")
    private static <T> List<T> emptyList() // PORT: §3.9 EmptyReadOnlyList<T>.Instance is one shared raw static
    {
        return (List<T>) (List<?>) EmptyReadOnlyList.Instance;
    }

    // ===== upstream part: SyntaxNode_Semantics.cs =====

    /// <summary>
    /// Gets diagnostics for this <see cref="SyntaxNode"/> an all child elements.
    /// </summary>
    public List<Diagnostic> getContainedDiagnostics(
        int include,
        CancellationToken cancellationToken)
    {
        var list = new ArrayList<Diagnostic>();
        gatherDiagnostics(this, list, include, cancellationToken); // PORT: §3.12
        return ListExtensions.toReadOnly(Linq.distinct(list)); // PORT: §3.6, §3.5
    }

    public List<Diagnostic> getContainedDiagnostics(int include) // PORT: §3.12
    {
        return getContainedDiagnostics(include, CancellationToken.NONE);
    }

    public List<Diagnostic> getContainedDiagnostics() // PORT: §3.12
    {
        return getContainedDiagnostics(DiagnosticsInclude.Syntactic | DiagnosticsInclude.Semantic);
    }

    protected static void gatherDiagnostics(
        SyntaxElement root,
        List<Diagnostic> diagnostics,
        int include,
        CancellationToken cancellationToken)
    {
        cancellationToken.throwIfCancellationRequested();
        boolean includeSyntax = (include & DiagnosticsInclude.Syntactic) != 0;
        boolean includeSemantic = (include & DiagnosticsInclude.Semantic) != 0;
        boolean includeExpansion = (include & DiagnosticsInclude.Expansion) != 0;

        Predicate<SyntaxElement> fnDescend = (include == DiagnosticsInclude.Syntactic)
            ? (SyntaxElement e) -> e.containsSyntaxDiagnostics()
            : null;

        SyntaxElement.walkElements(root,
            element -> // PORT: §3.12 named argument fnBefore
            {
                if (element.hasSyntaxDiagnostics() && includeSyntax)
                {
                    // each syntax diagnostic is located at the element that carries it.
                    diagnostics.addAll(Linq.select(element.syntaxDiagnostics(), d -> d.hasLocation() ? d : setLocation(d, element))); // PORT: §3.6
                }

                if (includeSemantic && element instanceof SyntaxNode node && node.semanticDiagnostics().size() > 0)
                {
                    diagnostics.addAll(node.semanticDiagnostics());
                }
            },
            element -> // PORT: §3.12 named argument fnAfter
            {
                if (includeExpansion 
                    && element instanceof Expression expr 
                    && expr.getCalledFunctionBody() instanceof SyntaxNode calledBody)
                {
                    var originalCount = diagnostics.size();
                    gatherDiagnostics(calledBody, diagnostics, include, cancellationToken);

                    if (diagnostics.size() > originalCount)
                    {
                        var firstError = Linq.firstOrDefault(Linq.skip(diagnostics, originalCount), d -> Objects.equals(d.severity(), DiagnosticSeverity.Error)); // PORT: §3.6, §3.14
                        ListExtensions.setCount(diagnostics, originalCount); // PORT: §3.5

                        if (firstError != null)
                        {
                            Symbol referenced = expr.referencedSymbol(); // PORT: §3.14
                            var name = referenced != null && referenced.name() != null ? referenced.name() : "<unknown>";
                            SyntaxElement location = expr instanceof FunctionCallExpression fc ? fc.name() : expr;
                            var dx = DiagnosticFacts.getErrorInExpansion(name, firstError.message()).withLocation(location);
                            diagnostics.add(dx);
                        }
                    }
                }

                if (includeSemantic
                    && element instanceof SyntaxNode node
                    && node.alternates() != null)
                {
                    var tmpDiagnostics = _diagnosticListPool.allocateFromPool();
                    try
                    {
                        for (var alternate : node.alternates())
                        {
                            tmpDiagnostics.clear();
                            gatherDiagnostics(alternate, tmpDiagnostics, include, cancellationToken);
           
                            // add adjusted diagnostics
                            diagnostics.addAll(
                                Linq.select( // PORT: §3.6
                                    Linq.where(tmpDiagnostics, d -> d.hasLocation()),
                                    d -> d.withLocation(alternate.getPositionInOriginalTree(d.start()), d.length())));
                        }
                    }
                    finally
                    {
                        _diagnosticListPool.returnToPool(tmpDiagnostics);
                    }
                }
            },
            fnDescend);
    }

    protected static void gatherDiagnostics(SyntaxElement root, List<Diagnostic> diagnostics, int include) // PORT: §3.12
    {
        gatherDiagnostics(root, diagnostics, include, CancellationToken.NONE);
    }

    private static final ObjectPool<List<Diagnostic>> _diagnosticListPool =
        new ObjectPool<List<Diagnostic>>(() -> new ArrayList<Diagnostic>(), list -> list.clear());

    private static Diagnostic setLocation(Diagnostic d, SyntaxElement element)
    {
        switch (d.locationKind())
        {
            case Relative:
                // if token associated with diagnostics is empty use next token
                if (element.width() == 0 && element instanceof SyntaxToken token)
                {
                    // move location to next token if it is
                    // less than two spaces away and not separated by line breaks
                    var next = token.getNextToken();

                    if (next != null
                        && (next.textStart() - token.end()) < 2
                        && !TextFacts.hasLineBreaks(next.trivia()))
                    {
                        element = next;
                    }
                }

                return d.withLocation(element);

            case RelativeEnd:
                // location is after the end of this token
                return d.withLocation(element.end(), 0);

            default:
                return d;
        }
    }
}
