// Ported from: src/Kusto.Language/Editor/CompletionItem.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// Represents an individual option in an intellisense completion list.
/// </summary>
// PORT: §3.20 [DebuggerDisplay] dropped
public class CompletionItem {
    private final CompletionKind kind;
    private final String displayText;
    private final String matchText;
    private final String orderText;
    private final List<CompletionText> applyTexts;

    /// <summary>
    /// The kind of <see cref="CompletionItem"/>.
    /// </summary>
    public CompletionKind kind() {
        return kind;
    }

    /// <summary>
    /// The text to show in the completion list.
    /// </summary>
    public String displayText() {
        return displayText;
    }

    /// <summary>
    /// The text to match on when typing.
    /// </summary>
    public String matchText() {
        return matchText;
    }

    /// <summary>
    /// The text to order the item by.
    /// </summary>
    public String orderText() {
        return orderText;
    }

    /// <summary>
    /// The text segments that are applied on completion of this item.
    /// </summary>
    public List<CompletionText> applyTexts() {
        return applyTexts;
    }

    private String _beforeText;
    private String _afterText;

    /// <summary>
    /// The text to apply before the cursor/caret.
    /// </summary>
    public String beforeText() {
        if (_beforeText == null && this.applyTexts().size() > 0) {
            var caretIndex = this.caretIndex();
            if (caretIndex > 0) {
                // PORT: §3.6 string.Concat(this.ApplyTexts.Take(caretIndex).Select(t => t.Text)); Concat treats null as empty (§3.14)
                _beforeText = DotNetStrings.join("", Linq.select(Linq.take(this.applyTexts(), caretIndex), t -> t.text()));
            } else if (caretIndex < 0) {
                // PORT: §3.6 string.Concat(this.ApplyTexts.Select(t => t.Text))
                _beforeText = DotNetStrings.join("", Linq.select(this.applyTexts(), t -> t.text()));
            } else {
                _beforeText = "";
            }
        }

        return _beforeText != null ? _beforeText : ""; // PORT: §3.14 ??
    }

    /// <summary>
    /// The text apply after the cursor/caret.
    /// </summary>
    public String afterText() {
        if (_afterText == null && this.applyTexts().size() > 0) {
            var caretIndex = this.caretIndex();
            if (caretIndex >= 0) {
                // PORT: §3.6 string.Concat(this.ApplyTexts.Skip(caretIndex).Select(t => t.Text))
                _afterText = DotNetStrings.join("", Linq.select(Linq.skip(this.applyTexts(), caretIndex), t -> t.text()));
            } else {
                _afterText = "";
            }
        }

        return _afterText != null ? _afterText : ""; // PORT: §3.14 ??
    }

    @Deprecated // PORT: §3.20 [Obsolete("Use BeforeText or ApplyTexts")]
    public String editText() {
        return beforeText();
    }

    private final boolean retrigger;

    /// <summary>
    /// True if completion should be retriggered automatically after this item is inserted and the caret positioned.
    /// </summary>
    public boolean retrigger() {
        return retrigger;
    }

    private final CompletionRank rank;

    /// <summary>
    /// The ranking that controls the ordering of categories of completion items.
    /// </summary>
    @Internal
    public CompletionRank rank() {
        return rank;
    }

    private final CompletionPriority priority;

    /// <summary>
    /// The priority of the completion item within its rank.
    /// </summary>
    @Internal
    public CompletionPriority priority() {
        return priority;
    }

    private CompletionItem(
        CompletionKind kind,
        String displayText,
        List<CompletionText> applyTexts,
        String matchText,
        String orderText,
        CompletionRank rank,
        CompletionPriority priority,
        boolean retrigger) {
        this.kind = kind;
        this.displayText = displayText != null ? displayText : ""; // PORT: §3.14 ??
        this.matchText = matchText != null ? matchText : displayText; // PORT: §3.14 ??
        this.orderText = orderText != null ? orderText : this.matchText; // PORT: §3.14 ??
        this.applyTexts = ListExtensions.toReadOnly(applyTexts); // PORT: §3.5
        this.rank = rank;
        this.priority = priority;
        this.retrigger = retrigger;
    }

    /// <summary>
    /// Creates a <see cref="CompletionItem"/> instance.
    /// </summary>
    /// <param name="kind">The kind of completion item.</param>
    /// <param name="displayText">The text to display in the completion list for the item.</param>
    /// <param name="beforeText">The text to apply before the cursor/caret. If not specified the displayText is used.</param>
    /// <param name="afterText">The text to apply after the cursor/caret.</param>
    /// <param name="matchText">The text to match against user typing. If not specified the displayText is used.</param>
    /// <param name="orderText">The text to order the item by.</param>
    /// <param name="rank">The rank of the completion item determines the category for ordering in the completion list.</param>
    /// <param name="priority">The priority of the completion item determines the ordering within the items rank.</param>
    /// <param name="retrigger">If true, the editor will retrigger completion after this item is inserted.</param>
    public CompletionItem(
        CompletionKind kind,
        String displayText,
        String beforeText,
        String afterText,
        String matchText,
        String orderText,
        CompletionRank rank,
        CompletionPriority priority,
        boolean retrigger) {
        this(
              kind,
              displayText,
              createApplyTexts(beforeText != null ? beforeText : displayText, afterText), // PORT: §3.11 computed this(...) argument, §3.14 ??
              matchText,
              orderText,
              rank,
              priority,
              retrigger);
    }

    // PORT: §3.12 optional parameters retrigger = false
    public CompletionItem(CompletionKind kind, String displayText, String beforeText, String afterText, String matchText, String orderText, CompletionRank rank, CompletionPriority priority) {
        this(kind, displayText, beforeText, afterText, matchText, orderText, rank, priority, false);
    }

    // PORT: §3.12 optional parameters priority = CompletionPriority.Normal, retrigger = false
    public CompletionItem(CompletionKind kind, String displayText, String beforeText, String afterText, String matchText, String orderText, CompletionRank rank) {
        this(kind, displayText, beforeText, afterText, matchText, orderText, rank, CompletionPriority.Normal, false);
    }

    // PORT: §3.12 optional parameters rank = CompletionRank.Default, priority = CompletionPriority.Normal, retrigger = false
    public CompletionItem(CompletionKind kind, String displayText, String beforeText, String afterText, String matchText, String orderText) {
        this(kind, displayText, beforeText, afterText, matchText, orderText, CompletionRank.Default, CompletionPriority.Normal, false);
    }

    // PORT: §3.12 optional parameters orderText = null, rank = CompletionRank.Default, priority = CompletionPriority.Normal, retrigger = false
    public CompletionItem(CompletionKind kind, String displayText, String beforeText, String afterText, String matchText) {
        this(kind, displayText, beforeText, afterText, matchText, null, CompletionRank.Default, CompletionPriority.Normal, false);
    }

    // PORT: §3.12 optional parameters matchText = null, orderText = null, rank = CompletionRank.Default, priority = CompletionPriority.Normal, retrigger = false
    public CompletionItem(CompletionKind kind, String displayText, String beforeText, String afterText) {
        this(kind, displayText, beforeText, afterText, null, null, CompletionRank.Default, CompletionPriority.Normal, false);
    }

    // PORT: §3.12 optional parameters afterText = null, matchText = null, orderText = null, rank = CompletionRank.Default, priority = CompletionPriority.Normal, retrigger = false
    public CompletionItem(CompletionKind kind, String displayText, String beforeText) {
        this(kind, displayText, beforeText, null, null, null, CompletionRank.Default, CompletionPriority.Normal, false);
    }

    // PORT: §3.12 optional parameters beforeText = null, afterText = null, matchText = null, orderText = null, rank = CompletionRank.Default, priority = CompletionPriority.Normal, retrigger = false
    public CompletionItem(CompletionKind kind, String displayText) {
        this(kind, displayText, null, null, null, null, CompletionRank.Default, CompletionPriority.Normal, false);
    }

    /// <summary>
    /// Creates a <see cref="CompletionItem"/> instance.
    /// </summary>
    /// <param name="displayText">The text to display in the completion list for the item.</param>
    /// <param name="beforeText">The text to apply before the cursor/caret. If not specified the displayText is used.</param>
    /// <param name="afterText">The text to apply after the cursor/caret.</param>
    /// <param name="matchText">The text to match against user typing. If not specified the displayText is used.</param>
    /// <param name="orderText">The text to order the item by.</param>
    /// <param name="rank">The rank of the completion item determines the category for ordering in the completion list.</param>
    /// <param name="priority">The priority of the completion item determines the ordering within the items rank.</param>
    /// <param name="retrigger">If true, the editor will retrigger completion after this item is inserted.</param>
    public CompletionItem(
        String displayText,
        String beforeText,
        String afterText,
        String matchText,
        String orderText,
        CompletionRank rank,
        CompletionPriority priority,
        boolean retrigger) {
        this(
              CompletionKind.Syntax,
              displayText,
              createApplyTexts(beforeText != null ? beforeText : displayText, afterText), // PORT: §3.11 computed this(...) argument, §3.14 ??
              matchText,
              orderText,
              rank,
              priority,
              retrigger);
    }

    // PORT: §3.12 optional parameters retrigger = false
    public CompletionItem(String displayText, String beforeText, String afterText, String matchText, String orderText, CompletionRank rank, CompletionPriority priority) {
        this(displayText, beforeText, afterText, matchText, orderText, rank, priority, false);
    }

    // PORT: §3.12 optional parameters priority = CompletionPriority.Normal, retrigger = false
    public CompletionItem(String displayText, String beforeText, String afterText, String matchText, String orderText, CompletionRank rank) {
        this(displayText, beforeText, afterText, matchText, orderText, rank, CompletionPriority.Normal, false);
    }

    // PORT: §3.12 optional parameters rank = CompletionRank.Default, priority = CompletionPriority.Normal, retrigger = false
    public CompletionItem(String displayText, String beforeText, String afterText, String matchText, String orderText) {
        this(displayText, beforeText, afterText, matchText, orderText, CompletionRank.Default, CompletionPriority.Normal, false);
    }

    // PORT: §3.12 optional parameters orderText = null, rank = CompletionRank.Default, priority = CompletionPriority.Normal, retrigger = false
    public CompletionItem(String displayText, String beforeText, String afterText, String matchText) {
        this(displayText, beforeText, afterText, matchText, null, CompletionRank.Default, CompletionPriority.Normal, false);
    }

    // PORT: §3.12 optional parameters matchText = null, orderText = null, rank = CompletionRank.Default, priority = CompletionPriority.Normal, retrigger = false
    public CompletionItem(String displayText, String beforeText, String afterText) {
        this(displayText, beforeText, afterText, null, null, CompletionRank.Default, CompletionPriority.Normal, false);
    }

    // PORT: §3.12 optional parameters afterText = null, matchText = null, orderText = null, rank = CompletionRank.Default, priority = CompletionPriority.Normal, retrigger = false
    public CompletionItem(String displayText, String beforeText) {
        this(displayText, beforeText, null, null, null, CompletionRank.Default, CompletionPriority.Normal, false);
    }

    // PORT: §3.12 optional parameters beforeText = null, afterText = null, matchText = null, orderText = null, rank = CompletionRank.Default, priority = CompletionPriority.Normal, retrigger = false
    public CompletionItem(String displayText) {
        this(displayText, null, null, null, null, CompletionRank.Default, CompletionPriority.Normal, false);
    }

    /// <summary>
    /// True if this <see cref="CompletionItem"/> adjusts the position of the caret.
    /// </summary>
    @Internal
    public boolean hasCaret() {
        // PORT: §3.6 this.ApplyTexts.Any(at => at.Caret)
        for (CompletionText at : this.applyTexts()) {
            if (at.caret())
                return true;
        }
        return false;
    }

    private static List<CompletionText> createApplyTexts(String beforeText, String afterText) {
        var list = new ArrayList<CompletionText>(2);
        list.add(CompletionText.create(beforeText != null ? beforeText : "")); // PORT: §3.14 ??
        if (afterText != null)
            list.add(CompletionText.create(afterText, true)); // PORT: §3.12 named argument caret: true
        return ListExtensions.toReadOnly(list); // PORT: §3.5
    }

    /// <summary>
    /// Creates a new <see cref="CompletionItem"/> with the <see cref="Kind"/> property modified.
    /// This is typically used to select an icon/glyph for the completion item UI.
    /// </summary>
    public CompletionItem withKind(CompletionKind kind) {
        return new CompletionItem(
            kind,
            this.displayText(),
            this.applyTexts(),
            this.matchText(),
            this.orderText(),
            this.rank(),
            this.priority(),
            this.retrigger());
    }

    /// <summary>
    /// Creates a new <see cref="CompletionItem"/> with the <see cref="DisplayText"/> property modified.
    /// </summary>
    public CompletionItem withDisplayText(String displayText) {
        return new CompletionItem(
            this.kind(),
            displayText,
            this.applyTexts(),
            this.matchText(),
            this.orderText(),
            this.rank(),
            this.priority(),
            this.retrigger());
    }

    /// <summary>
    /// Creates a new <see cref="CompletionItem"/> with the <see cref="MatchText"/> property modified.
    /// </summary>
    public CompletionItem withMatchText(String matchText) {
        return new CompletionItem(
            this.kind(),
            this.displayText(),
            this.applyTexts(),
            matchText,
            this.orderText(),
            this.rank(),
            this.priority(),
            this.retrigger()
            );
    }

    /// <summary>
    /// Creates a new <see cref="CompletionItem"/> with the <see cref="OrderText"/> property modified.
    /// </summary>
    public CompletionItem withOrderText(String orderText) {
        return new CompletionItem(
            this.kind(),
            this.displayText(),
            this.applyTexts(),
            this.matchText(),
            orderText,
            this.rank(),
            this.priority(),
            this.retrigger()
            );
    }

    /// <summary>
    /// Creates a new <see cref="CompletionItem"/> with the <see cref="ApplyTexts"/> property modified.
    /// </summary>
    public CompletionItem withApplyTexts(List<CompletionText> applyTexts) {
        return new CompletionItem(
            this.kind(),
            this.displayText(),
            applyTexts,
            this.matchText(),
            this.orderText(),
            this.rank(),
            this.priority(),
            this.retrigger()
            );
    }

    /// <summary>
    /// Creates a new <see cref="CompletionItem"/> with the <see cref="ApplyTexts"/> property modified.
    /// </summary>
    public CompletionItem withApplyTexts(CompletionText... applyTexts) { // PORT: §3.10 params
        return withApplyTexts(Arrays.asList(applyTexts)); // PORT: §3.17 (IReadOnlyList<CompletionText>)array
    }

    /// <summary>
    /// Creates a new <see cref="CompletionItem"/> with the <see cref="ApplyTexts"/> property modified.
    /// </summary>
    public CompletionItem withApplyTexts(String textWithMarkers) {
        return withApplyTexts(CompletionText.parse(textWithMarkers));
    }

    /// <summary>
    /// Returns the index of the <see cref="ApplyTexts"/> that contains the caret
    /// </summary>
    private int caretIndex() {
        for (int i = 0; i < this.applyTexts().size(); i++) {
            if (this.applyTexts().get(i).caret())
                return i;
        }

        return -1;
    }

    /// <summary>
    /// Creates a new <see cref="CompletionItem"/> with the <see cref="BeforeText"/> property modified.
    /// </summary>
    public CompletionItem withBeforeText(String beforeText) {
        var newBeforeText = CompletionText.create(beforeText);
        var caretIndex = this.caretIndex();
        List<CompletionText> newApplyTexts = caretIndex >= 0
            ? ListExtensions.replaceRange(this.applyTexts(), 0, caretIndex, newBeforeText) // PORT: §3.5
            : Arrays.asList(newBeforeText); // PORT: §3.17 new[] { newBeforeText }

        return new CompletionItem(
            this.kind(),
            this.displayText(),
            newApplyTexts,
            this.matchText(),
            this.orderText(),
            this.rank(),
            this.priority(),
            this.retrigger());
    }


    @Deprecated // PORT: §3.20 [Obsolete("Use WithBeforeText")]
    public CompletionItem withEditText(String editText) {
        return withBeforeText(editText);
    }

    /// <summary>
    /// Creates a new <see cref="CompletionItem"/> with the <see cref="AfterText"/> property modified.
    /// </summary>
    public CompletionItem withAfterText(String afterText) {
        var newAfterText = CompletionText.create(afterText, true); // PORT: §3.12 named argument caret: true
        var caretIndex = this.caretIndex();
        List<CompletionText> newApplyTexts =
            caretIndex > 0 ? ListExtensions.replaceRange(this.applyTexts(), caretIndex, this.applyTexts().size() - caretIndex, newAfterText) // PORT: §3.5
            : caretIndex == 0 ? Arrays.asList(newAfterText) // PORT: §3.17 new[] { newAfterText }
            : ListExtensions.append(this.applyTexts(), newAfterText); // PORT: §3.5

        return new CompletionItem(
            this.kind(),
            this.displayText(),
            newApplyTexts,
            this.matchText(),
            this.orderText(),
            this.rank(),
            this.priority(),
            this.retrigger()
            );
    }

    /// <summary>
    /// Creates a new <see cref="CompletionItem"/> with the <see cref="Rank"/> property modified.
    /// </summary>
    @Internal
    public CompletionItem withRank(CompletionRank rank) {
        return new CompletionItem(
            this.kind(),
            this.displayText(),
            this.applyTexts(),
            this.matchText(),
            this.orderText(),
            rank,
            this.priority(),
            this.retrigger());
    }

    /// <summary>
    /// Creates a new <see cref="CompletionItem"/> with the <see cref="Priority"/> property modified.
    /// </summary>
    @Internal
    public CompletionItem withPriority(CompletionPriority priority) {
        return new CompletionItem(
            this.kind(),
            this.displayText(),
            this.applyTexts(),
            this.matchText(),
            this.orderText(),
            this.rank(),
            priority,
            this.retrigger()
            );
    }

    /// <summary>
    /// Creates a new <see cref="CompletionItem"/> with the <see cref="Priority"/> property modified.
    /// </summary>
    public CompletionItem withRetrigger(boolean retrigger) {
        return new CompletionItem(
            this.kind(),
            this.displayText(),
            this.applyTexts(),
            this.matchText(),
            this.orderText(),
            this.rank(),
            this.priority(),
            retrigger
            );
    }
}
