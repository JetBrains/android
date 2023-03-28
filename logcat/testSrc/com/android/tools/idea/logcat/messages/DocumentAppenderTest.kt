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
import com.intellij.openapi.editor.colors.TextAttributesKey
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
private val blueKey = TextAttributesKey.createTextAttributesKey("blue")
private val redKey = TextAttributesKey.createTextAttributesKey("red")

/**
 * Tests for [DocumentAppender]
 */
@RunsInEdt
class DocumentAppenderTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  private val document: DocumentEx = DocumentImpl("", true)
  private val markupModel by lazy { DocumentMarkupModel.forDocument(document, projectRule.project, false) }

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
  fun appendToDocument_setsTextAttributesRanges() {
    val documentAppender = documentAppender(document)
    document.setText("Start\n")

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("No color\n")
      accumulate("Red\n", textAttributes = red)
      accumulate("Blue\n", textAttributes = blue)
    })

    assertThat(markupModel.allHighlighters.map(RangeHighlighter::toTextAttributesRange)).containsExactly(
      getRangeForText("Red\n", red),
      getRangeForText("Blue\n", blue)
    )
  }

  @Test
  fun appendToDocument_setsTextAttributesRanges_ignoresRangesOutsideCyclicBuffer() {
    // This size will truncate in the beginning of the second line
    val documentAppender = documentAppender(document, 8)

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("abcd\n", textAttributes = blue)
      accumulate("efgh\n", textAttributes = red)
      accumulate("ijkl\n", textAttributes = blue)
    })

    assertThat(markupModel.allHighlighters.map(RangeHighlighter::toTextAttributesRange)).containsExactly(
      getRangeForText("efgh\n", red),
      getRangeForText("ijkl\n", blue),
    )
  }

  @Test
  fun appendToDocument_setsTextAttributesKeyRanges() {
    val documentAppender = documentAppender(document)
    document.setText("Start\n")

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("No color\n")
      accumulate("Red\n", textAttributesKey = redKey)
      accumulate("Blue\n", textAttributesKey = blueKey)
    })

    assertThat(markupModel.allHighlighters.map(RangeHighlighter::toTextAttributesKeyRange)).containsExactly(
      getRangeForText("Red\n", redKey),
      getRangeForText("Blue\n", blueKey)
    )
  }

  @Test
  fun appendToDocument_setsTextAttributesKeyRanges_ignoresRangesOutsideCyclicBuffer() {
    // This size will truncate in the beginning of the second line
    val documentAppender = documentAppender(document, 8)

    documentAppender.appendToDocument(TextAccumulator().apply {
      accumulate("abcd\n", textAttributesKey = blueKey)
      accumulate("efgh\n", textAttributesKey = redKey)
      accumulate("ijkl\n", textAttributesKey = blueKey)
    })

    assertThat(markupModel.allHighlighters.map(RangeHighlighter::toTextAttributesKeyRange)).containsExactly(
      getRangeForText("efgh\n", redKey),
      getRangeForText("ijkl\n", blueKey),
    )
  }

  private fun <T> getRangeForText(text: String, data: T): TextAccumulator.Range<T>? {
    val start = document.text.indexOf(text)
    if (start < 0) {
      return null
    }
    return TextAccumulator.Range(start, start + text.length, data)
  }

  private fun documentAppender(document: DocumentEx = this.document, maxDocumentSize: Int = Int.MAX_VALUE) = DocumentAppender(
    projectRule.project, document, maxDocumentSize)
}

private fun RangeHighlighter.toTextAttributesRange() =
  TextAccumulator.Range(range!!.startOffset, range!!.endOffset, getTextAttributes(null)!!)

private fun RangeHighlighter.toTextAttributesKeyRange() =
  TextAccumulator.Range(range!!.startOffset, range!!.endOffset, textAttributesKey!!)
