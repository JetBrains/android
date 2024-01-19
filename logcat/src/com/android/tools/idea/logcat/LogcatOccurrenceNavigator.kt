/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.logcat

import com.android.tools.idea.logcat.LogcatOccurrenceNavigator.Companion.FOLLOWED_HYPERLINK_ATTRIBUTES
import com.android.tools.idea.logcat.LogcatOccurrenceNavigator.Direction.DOWN
import com.android.tools.idea.logcat.LogcatOccurrenceNavigator.Direction.UP
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.execution.filters.HyperlinkInfoBase
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.ide.OccurenceNavigator
import com.intellij.ide.OccurenceNavigator.OccurenceInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.pom.NavigatableAdapter
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.CommonProcessors.CollectProcessor
import com.intellij.util.CommonProcessors.FindFirstProcessor
import org.jetbrains.annotations.VisibleForTesting

private val EXCEPTION_LINE_PATTERN = Regex("^\\s*at .+\\(.+\\)$")

@VisibleForTesting
internal val ORIGINAL_HYPERLINK_TEXT_ATTRIBUTES =
  Key.create<TextAttributes>("ORIGINAL_HYPERLINK_TEXT_ATTRIBUTES")

/**
 * A [OccurenceNavigator] for navigating stack trace frames.
 *
 * Based on ConsoleViewImpl and EditorHyperlinkSupport.getHyperlinkInfo() but improved:
 * 1. Does not navigate to random hyperlinks, only stack frames.
 * 2. hasNextOccurence & hasPreviousOccurence are optimized by not having to find all stack traces,
 *    just the existence of at least one.
 * 3. Navigation is based on the current caret position. Wraps around if caret is at the start or
 *    end of the document.
 */
internal class LogcatOccurrenceNavigator(private val project: Project, private val editor: Editor) :
  OccurenceNavigator {
  private enum class Direction {
    DOWN,
    UP,
  }

  private val document = editor.document
  private val markupModel = editor.markupModel as MarkupModelEx

  /** Returns true if the editor contains any stack trace hyperlinks, regardless of position. */
  override fun hasNextOccurence(): Boolean = hasOccurrences()

  /** Returns true if the editor contains any stack trace hyperlinks, regardless of position. */
  override fun hasPreviousOccurence(): Boolean = hasOccurrences()

  /** Go to the next stack frame starting from the line below the caret */
  override fun goNextOccurence(): OccurenceInfo? = goOccurrence(DOWN)

  /** Go to the previous stack frame starting from the line above the caret */
  override fun goPreviousOccurence(): OccurenceInfo? = goOccurrence(UP)

  override fun getNextOccurenceActionName(): String =
    ExecutionBundle.message("down.the.stack.trace")

  override fun getPreviousOccurenceActionName(): String =
    ExecutionBundle.message("up.the.stack.trace")

  private fun goOccurrence(direction: Direction): OccurenceInfo? {
    val line = document.getLineNumber(editor.caretModel.offset)
    val pos =
      if (direction == DOWN) document.getLineEndOffset(line) else document.getLineStartOffset(line)
    val framesUp = getStackFrameHyperlinks(0, pos)
    val framesDown = getStackFrameHyperlinks(pos, editor.document.textLength - 1)

    val numOccurrences = framesUp.size + framesDown.size
    if (numOccurrences == 0) {
      return null
    }
    val (occurrenceRange, index) =
      when {
        direction == DOWN && framesDown.isEmpty() -> framesUp.first() to 1
        direction == DOWN -> framesDown.first() to framesUp.size + 1
        framesUp.isEmpty() -> framesDown.last() to framesDown.size
        else -> framesUp.last() to framesUp.size
      }
    return OccurenceInfo(
      LogcatNavigatableAdapter(project, editor, occurrenceRange, framesUp + framesDown),
      index,
      numOccurrences,
    )
  }

  private fun hasOccurrences(): Boolean {
    val processor = FindFirstStackFrameRange(editor)
    markupModel.processRangeHighlightersOverlappingWith(0, editor.document.textLength, processor)
    return processor.isFound
  }

  private fun getStackFrameHyperlinks(start: Int, end: Int): Collection<RangeHighlighter> {
    val processor = CollectStackFrameRanges(editor)
    markupModel.processRangeHighlightersOverlappingWith(start, end, processor)
    return processor.results
  }

  companion object {
    @VisibleForTesting
    internal val FOLLOWED_HYPERLINK_ATTRIBUTES =
      EditorColorsManager.getInstance()
        .globalScheme
        .getAttributes(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES)
  }
}

/**
 * To be a stack frame hyperlink, the range must:
 * 1. be valid
 * 2. Be of type FileHyperlinkInfo
 * 3. Contain exactly one line of text
 * 4. Contain text that matches a stack trace regex
 * 5. Not be in a folded region
 */
private fun RangeHighlighter.isStackTraceLink(editor: Editor): Boolean {
  if (!isValid) {
    return false
  }
  val hyperlinkInfo = EditorHyperlinkSupport.getHyperlinkInfo(this) ?: return false
  if (hyperlinkInfo !is FileHyperlinkInfo) {
    return false
  }
  if (editor.foldingModel.getCollapsedRegionAtOffset(startOffset) != null) {
    return false
  }
  val document = document
  val startLine = document.getLineNumber(startOffset)
  val endLine = document.getLineNumber(endOffset)
  if (startLine != endLine) {
    return false
  }
  val startLineOffset = document.getLineStartOffset(startLine)
  val endLineOffset = document.getLineEndOffset(endLine)
  val line = document.immutableCharSequence.subSequence(startLineOffset, endLineOffset)
  return EXCEPTION_LINE_PATTERN.matches(line)
}

internal class LogcatNavigatableAdapter(
  private val project: Project,
  private val editor: Editor,
  @VisibleForTesting val occurrenceRange: RangeHighlighter,
  @VisibleForTesting val allRanges: List<RangeHighlighter>,
) : NavigatableAdapter() {
  override fun navigate(requestFocus: Boolean) {
    val offset = occurrenceRange.startOffset
    editor.caretModel.moveToOffset(offset)
    editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    val hyperlinkInfo = EditorHyperlinkSupport.getHyperlinkInfo(occurrenceRange)
    if (hyperlinkInfo is HyperlinkInfoBase) {
      val position = editor.offsetToVisualPosition(offset)
      val point =
        editor.visualPositionToXY(VisualPosition(position.getLine() + 1, position.getColumn()))
      hyperlinkInfo.navigate(project, RelativePoint(editor.contentComponent, point))
    } else {
      hyperlinkInfo?.navigate(project)
    }
    val markupModel = editor.markupModel as MarkupModelEx
    for (range in allRanges) {
      val textAttributes = range.getUserData(ORIGINAL_HYPERLINK_TEXT_ATTRIBUTES)
      if (textAttributes != null) {
        markupModel.setRangeHighlighterAttributes(range, textAttributes)
        range.putUserData(ORIGINAL_HYPERLINK_TEXT_ATTRIBUTES, null)
      }
    }
    occurrenceRange.putUserData(
      ORIGINAL_HYPERLINK_TEXT_ATTRIBUTES,
      occurrenceRange.getTextAttributes(editor.colorsScheme),
    )
    markupModel.setRangeHighlighterAttributes(occurrenceRange, FOLLOWED_HYPERLINK_ATTRIBUTES)
  }
}

private class CollectStackFrameRanges(private val editor: Editor) :
  CollectProcessor<RangeHighlighter>() {
  override fun accept(range: RangeHighlighter?): Boolean = range?.isStackTraceLink(editor) == true
}

private class FindFirstStackFrameRange(private val editor: Editor) :
  FindFirstProcessor<RangeHighlighter>() {
  override fun accept(range: RangeHighlighter?): Boolean = range?.isStackTraceLink(editor) == true
}
