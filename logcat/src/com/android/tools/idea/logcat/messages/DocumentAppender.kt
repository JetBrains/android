/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.messages

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.util.LOGGER
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.VisibleForTesting
import kotlin.math.max

internal val LOGCAT_FILTER_HINT_KEY = Key.create<TextAccumulator.FilterHint>("LogcatHint")
internal val LOGCAT_MESSAGE_KEY = Key.create<LogcatMessage>("LogcatMessage")

internal class DocumentAppender(project: Project, private val document: DocumentEx, private var maxDocumentSize: Int) {
  private val markupModel = DocumentMarkupModel.forDocument(document, project, true)

  /**
   * RangeMarker's are kept in the Document as weak reference (see IntervalTreeImpl#createGetter) so we need to keep them alive as long as
   * they are valid.
   */
  @VisibleForTesting
  internal val ranges = ArrayDeque<RangeMarker>()

  @UiThread
  fun appendToDocument(buffer: TextAccumulator) {
    LOGGER.debug { "Appending ${buffer.text.length} bytes to document" }
    val text = buffer.text
    if (text.length >= maxDocumentSize) {
      document.setText("")
      document.insertString(document.textLength, text.substring(text.lastIndexOf('\n', text.length - maxDocumentSize) + 1))
    }
    else {
      document.insertString(document.textLength, text)
      trimToSize()
    }

    LOGGER.debug {"Document text.length: ${document.text.length} immutableCharSequence.length: ${document.immutableCharSequence.length}" }

    // Document has a cyclic buffer, so we need to get document.textLength again after inserting text.
    val offset = document.textLength - text.length
    for (range in buffer.textAttributesRanges) {
      range.applyRange(offset) { start, end, textAttributes ->
        markupModel.addRangeHighlighter(start, end, HighlighterLayer.SYNTAX, textAttributes, HighlighterTargetArea.EXACT_RANGE)
      }
    }
    for (range in buffer.textAttributesKeyRanges) {
      range.applyRange(offset) { start, end, textAttributesKey ->
        markupModel.addRangeHighlighter(textAttributesKey, start, end, HighlighterLayer.SYNTAX, HighlighterTargetArea.EXACT_RANGE)
      }
    }
    for (range in buffer.filterHintRanges) {
      range.applyRange(offset) { start, end, hint ->
        ranges.add(document.createRangeMarker(start, end).apply {
          putUserData(LOGCAT_FILTER_HINT_KEY, hint)
        })
      }
    }

    for (range in buffer.messageRanges) {
      range.applyRange(offset) { start, end, message ->
        ranges.add(document.createRangeMarker(start, end).apply {
          putUserData(LOGCAT_MESSAGE_KEY, message)
        })
      }
    }

    while (!ranges.isEmpty() && !ranges.first().isReallyValid()) {
      ranges.removeFirst()
    }
  }

  fun setMaxDocumentSize(size: Int) {
    maxDocumentSize = size
    trimToSize()
  }

  /**
   * Trim the document to size at a line boundary (Based on Document.trimToSize).
   */
  private fun trimToSize() {
    if (document.textLength > maxDocumentSize) {
      val offset = document.textLength - maxDocumentSize
      document.deleteString(0, document.immutableCharSequence.lastIndexOf('\n', offset) + 1)
    }
  }
}

// There seems to be a bug where a range that is exactly the same as a portion that's deleted remains valid but has a 0 size
private fun RangeMarker.isReallyValid() = isValid && startOffset < endOffset

private fun <T> TextAccumulator.Range<T>.applyRange(offset: Int, apply: (start: Int, end: Int, data: T) -> Unit) {
  val rangeEnd = offset + end
  if (rangeEnd <= 0) {
    return
  }
  val rangeStart = max(offset + start, 0)
  apply(rangeStart, rangeEnd, data)
}