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
  private val documentAppender by lazy { DocumentAppender(projectRule.project, document) }
  private val markupModel by lazy { DocumentMarkupModel.forDocument(document, projectRule.project, /* create= */ false) }

  @Test
  fun appendToDocument_appendsText() {
    document.setText("Start\n")

    documentAppender.appendToDocument(TextAccumulator().apply { accumulate("Added Text") })

    assertThat(document.text).isEqualTo("""
      Start
      Added Text
    """.trimIndent())
  }

  @Test
  fun appendToDocument_cyclicBuffer() {
    document.setCyclicBufferSize(10)
    document.setText("Start\n")

    documentAppender.appendToDocument(TextAccumulator().apply { accumulate("Added Text") })

    assertThat(document.text).isEqualTo("""
      Added Text
    """.trimIndent())
  }

  @Test
  fun appendToDocument_setsHighlightRanges() {
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
  fun appendToDocument_setsHighlightRanges_cyclicBuffer() {
    // This size will truncate in the middle of the second line
    document.setCyclicBufferSize(8)

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("abcd\n", blue)
      accumulate("efgh\n", red)
      accumulate("ijkl\n", blue)
    })

    assertThat(markupModel.allHighlighters.map(RangeHighlighter::toHighlighterRange)).containsExactly(
      TextAccumulator.Range(0, 3, red),
      getHighlighterRangeForText("ijkl\n", blue)
    )
  }

  @Test
  fun appendToDocument_setsHintRanges() {
    document.setText("Start\n")

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("No hint\n")
      accumulate("Foo\n", hint = "foo")
      accumulate("Bar\n", hint = "bar")
    })

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
  fun appendToDocument_setsHintRanges_cyclicBuffer() {
    // This size will truncate in the middle of the second line
    document.setCyclicBufferSize(8)

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("abcd\n", hint = "foo")
      accumulate("efgh\n", hint = "bar")
      accumulate("ijkl\n", hint = "duh")
    })

    val rangeMarkers = mutableListOf<RangeMarker>()
    document.processRangeMarkers {
      if (it.getUserData(LOGCAT_HINT_KEY) != null) {
        rangeMarkers.add(it)
      }
      true
    }
    assertThat(rangeMarkers.map(RangeMarker::toHintRange)).containsExactly(
      TextAccumulator.Range(0, 3, "bar"),
      getHighlighterRangeForText("ijkl\n", "duh")
    )
  }

  private fun <T> getHighlighterRangeForText(text: String, data: T): TextAccumulator.Range<T>? {
    val start = document.text.indexOf(text)
    if (start < 0) {
      return null
    }
    return TextAccumulator.Range(start, start + text.length, data)
  }
}

private fun RangeHighlighter.toHighlighterRange() = TextAccumulator.Range(range!!.startOffset, range!!.endOffset, getTextAttributes(null)!!)

private fun RangeMarker.toHintRange() = TextAccumulator.Range(startOffset, endOffset, getUserData(LOGCAT_HINT_KEY))
