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

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.refactoring.suggested.range
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import java.awt.Color

private val blue = TextAttributes().apply { foregroundColor = Color.blue }
private val red = TextAttributes().apply { foregroundColor = Color.red }

/**
 * Tests for [DocumentAppender]
 */
@RunsInEdt
class DocumentAppenderTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  private val document: DocumentEx = DocumentImpl("", /* allowUpdatesWithoutWriteAction= */ true)
  private val markupModel by lazy { DocumentMarkupModel.forDocument(document, projectRule.project, /* create= */ false) }

  @Test
  fun appendToDocument_appendsText() {
    val documentAppender = documentAppender(document)
    document.setText("Start\n")

    documentAppender.appendToDocument(TextAccumulator().apply { accumulate("Added Text") })

    assertThat(document.text).isEqualTo("""
      Start
      Added Text
    """.trimIndent())
  }

  @Test
  fun appendToDocument_cyclicBuffer() {
    val documentAppender = documentAppender(document, 30)
    document.setText("""
      Added Line 1
      Added Line 2

    """.trimIndent())

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("""
      Added Line 3
      Added Line 4

    """.trimIndent())
    })
  }

  @Test
  fun appendToDocument_cyclicBuffer_trimsNothing() {
    val documentAppender = documentAppender(document, 30)
    document.setText("""
      Added Line 1
      Added Line 2

    """.trimIndent())

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("""
      Added Line 3

    """.trimIndent())
    })

    assertThat(document.text).isEqualTo("""
      Added Line 1
      Added Line 2
      Added Line 3

    """.trimIndent())
  }

  @Test
  fun appendToDocument_cyclicBuffer_appendLongText() {
    val documentAppender = documentAppender(document, 30)
    document.setText("""
      Added Line 1
      Added Line 2

    """.trimIndent())

    // Cut line is in the middle of the first line
    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("""
      Added Line 3
      Added Line 4
      Added Line 5

    """.trimIndent())
    })

    assertThat(document.text).isEqualTo("""
      Added Line 3
      Added Line 4
      Added Line 5

    """.trimIndent())
  }

  @Test
  fun appendToDocument_cyclicBuffer_appendVeryLongText() {
    val documentAppender = documentAppender(document, 30)
    document.setText("""
      Added Line 1
      Added Line 2

    """.trimIndent())

    // Cut line is in the middle of the second line
    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("""
      Added Line 3
      Added Line 4
      Added Line 5
      Added Line 6

    """.trimIndent())
    })

    assertThat(document.text).isEqualTo("""
      Added Line 4
      Added Line 5
      Added Line 6

    """.trimIndent())
  }

  @Test
  fun appendToDocument_setsHighlightRanges() {
    val documentAppender = documentAppender(document)
    document.setText("Start\n")

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("No color\n")
      accumulate("Red\n", red)
      accumulate("Blue\n", blue)
    })

    assertThat(markupModel.allHighlighters.map(RangeHighlighter::toHighlighterRange)).containsExactly(
      getHighlighterRangeForText("Red\n", red),
      getHighlighterRangeForText("Blue\n", blue)
    )
  }

  @Test
  fun appendToDocument_setsHighlightRanges_ignoresRangesOutsideCyclicBuffer() {
    // This size will truncate in the beginning of the second line
    val documentAppender = documentAppender(document, 8)

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("abcd\n", blue)
      accumulate("efgh\n", red)
      accumulate("ijkl\n", blue)
    })

    assertThat(markupModel.allHighlighters.map(RangeHighlighter::toHighlighterRange)).containsExactly(
      getHighlighterRangeForText("efgh\n", red),
      getHighlighterRangeForText("ijkl\n", blue),
    )
  }

  @Test
  fun appendToDocument_setsHintRanges() {
    val documentAppender = documentAppender(document)
    document.setText("Start\n")

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("No hint\n")
      accumulate("Foo\n", hint = "foo")
      accumulate("Bar\n", hint = "bar")
    })

    System.gc() // Range markers are weak refs so make sure they survive garbage collection
    val rangeMarkers = mutableListOf<RangeMarker>()
    document.processRangeMarkers {
      if (it.getUserData(LOGCAT_HINT_KEY) != null) {
        rangeMarkers.add(it)
      }
      true
    }
    assertThat(rangeMarkers.map(RangeMarker::toHintRange)).containsExactly(
      getHighlighterRangeForText("Foo\n", "foo"),
      getHighlighterRangeForText("Bar\n", "bar")
    )
  }

  @Test
  fun appendToDocument_setsHintRanges_ignoresRangesOutsideCyclicBuffer() {
    // This size will truncate in the beginning of the second line
    val documentAppender = documentAppender(document, 8)

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("abcd\n", hint = "foo")
      accumulate("efgh\n", hint = "bar")
      accumulate("ijkl\n", hint = "duh")
    })

    System.gc() // Range markers are weak refs so make sure they survive garbage collection
    val rangeMarkers = mutableListOf<RangeMarker>()
    document.processRangeMarkers {
      if (it.getUserData(LOGCAT_HINT_KEY) != null) {
        rangeMarkers.add(it)
      }
      true
    }
    assertThat(rangeMarkers.map(RangeMarker::toHintRange)).containsExactly(
      getHighlighterRangeForText("efgh\n", "bar"),
      getHighlighterRangeForText("ijkl\n", "duh"),
    )
  }

  // There seems to be a bug where a range that is exactly the same as a portion that's deleted remains valid but has a 0 size.
  // This test uses a range that IS NOT exactly deleted.
  @Test
  fun appendToDocument_setsHintRanges_removesRangesOutsideCyclicBuffer() {
    // This size will truncate in the beginning of the second line
    val documentAppender = documentAppender(document, 8)

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("1")
      accumulate("234\n", hint = "pre")
    })
    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("abcd\n", hint = "foo")
      accumulate("efgh\n", hint = "bar")
      accumulate("ijkl\n", hint = "duh")
    })

    System.gc() // Range markers are weak refs so make sure they survive garbage collection
    val rangeMarkers = mutableListOf<RangeMarker>()
    document.processRangeMarkers {
      if (it.getUserData(LOGCAT_HINT_KEY) != null) {
        rangeMarkers.add(it)
      }
      true
    }
    assertThat(rangeMarkers.map(RangeMarker::toHintRange)).containsExactly(
      getHighlighterRangeForText("efgh\n", "bar"),
      getHighlighterRangeForText("ijkl\n", "duh"),
    )
    assertThat(documentAppender.hintRanges).containsExactlyElementsIn(rangeMarkers)
  }

  // There seems to be a bug where a range that is exactly the same as a portion that's deleted remains valid but has a 0 size.
  // This test uses a range that IS exactly deleted.
  @Test
  fun appendToDocument_setsHintRanges_removesRangesOutsideCyclicBuffer_exactRange() {
    // This size will truncate in the beginning of the second line
    val documentAppender = documentAppender(document, 8)

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("1234\n", hint = "pre")
    })
    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("abcd\n", hint = "foo")
      accumulate("efgh\n", hint = "bar")
      accumulate("ijkl\n", hint = "duh")
    })

    System.gc() // Range markers are weak refs so make sure they survive garbage collection
    val rangeMarkers = mutableListOf<RangeMarker>()
    document.processRangeMarkers {
      if (it.getUserData(LOGCAT_HINT_KEY) != null) {
        rangeMarkers.add(it)
      }
      true
    }
    assertThat(rangeMarkers.map(RangeMarker::toHintRange)).containsExactly(
      getHighlighterRangeForText("efgh\n", "bar"),
      getHighlighterRangeForText("ijkl\n", "duh"),
    )
    assertThat(documentAppender.hintRanges).containsExactlyElementsIn(rangeMarkers)
  }

  private fun <T> getHighlighterRangeForText(text: String, data: T): TextAccumulator.Range<T>? {
    val start = document.text.indexOf(text)
    if (start < 0) {
      return null
    }
    return TextAccumulator.Range(start, start + text.length, data)
  }

  private fun documentAppender(document: DocumentEx = this.document, maxDocumentSize: Int = Int.MAX_VALUE) = DocumentAppender(
    projectRule.project, document, maxDocumentSize)
}

private fun RangeHighlighter.toHighlighterRange() = TextAccumulator.Range(range!!.startOffset, range!!.endOffset, getTextAttributes(null)!!)

private fun RangeMarker.toHintRange() = TextAccumulator.Range(startOffset, endOffset, getUserData(LOGCAT_HINT_KEY))
