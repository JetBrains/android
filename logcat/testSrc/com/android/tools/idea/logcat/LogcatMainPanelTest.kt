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
package com.android.tools.idea.logcat

import com.android.ddmlib.Log.LogLevel.WARN
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.awt.BorderLayout.CENTER
import java.awt.BorderLayout.NORTH
import java.time.Instant
import java.time.ZoneId

/**
 * Tests for [LogcatMainPanel]
 */
@RunsInEdt
class LogcatMainPanelTest {
  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val edtRule = EdtRule()

  private lateinit var logcatMainPanel: LogcatMainPanel

  @After
  fun tearDown() {
    logcatMainPanel.dispose()
  }

  @Test
  fun createsComponents() {
    logcatMainPanel = LogcatMainPanel(projectRule.project, LogcatColors(), state = null)

    val borderLayout = logcatMainPanel.layout as BorderLayout

    assertThat(logcatMainPanel.componentCount).isEqualTo(2)
    assertThat(borderLayout.getLayoutComponent(NORTH)).isInstanceOf(LogcatHeaderPanel::class.java)
    assertThat(borderLayout.getLayoutComponent(CENTER)).isSameAs(logcatMainPanel.editor.component)
  }

  @Test
  fun setsUpEditor() {
    logcatMainPanel = LogcatMainPanel(projectRule.project, LogcatColors(), state = null)

    assertThat(logcatMainPanel.editor.gutterComponentEx.isPaintBackground).isFalse()
    val editorSettings = logcatMainPanel.editor.settings
    assertThat(editorSettings.isAllowSingleLogicalLineFolding).isTrue()
    assertThat(editorSettings.isLineMarkerAreaShown).isFalse()
    assertThat(editorSettings.isIndentGuidesShown).isFalse()
    assertThat(editorSettings.isLineNumbersShown).isFalse()
    assertThat(editorSettings.isFoldingOutlineShown).isTrue()
    assertThat(editorSettings.isAdditionalPageAtBottom).isFalse()
    assertThat(editorSettings.additionalColumnsCount).isEqualTo(0)
    assertThat(editorSettings.additionalLinesCount).isEqualTo(0)
    assertThat(editorSettings.isRightMarginShown).isFalse()
    assertThat(editorSettings.isCaretRowShown).isFalse()
    assertThat(editorSettings.isShowingSpecialChars).isFalse()
  }

  @Test
  fun setsDocumentCyclicBuffer() {
    // Set a buffer of 1k
    System.setProperty("idea.cycle.buffer.size", "1")
    logcatMainPanel = LogcatMainPanel(projectRule.project, LogcatColors(), state = null)
    val document = logcatMainPanel.editor.document as DocumentImpl

    // Insert 2000 chars
    for (i in 1..200) {
      document.insertString(document.textLength, "123456789\n")
    }

    assertThat(document.text.length).isAtMost(1024)
  }

  /**
   * Basic test of print. Comprehensive tests of the underlying print() code are in [LogcatDocumentPrinterTest]
   */
  @Test
  fun print() {
    logcatMainPanel = LogcatMainPanel(projectRule.project, LogcatColors(), state = null, ZoneId.of("Asia/Yerevan"))

    logcatMainPanel.print(LogCatMessage(LogCatHeader(WARN, 1, 2, "app", "tag", Instant.ofEpochMilli(1000)), "message"))

    assertThat(logcatMainPanel.editor.document.text).isEqualTo("1970-01-01 04:00:01.000      1-2      tag app W message\n")
  }
}