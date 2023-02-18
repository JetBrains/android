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
package com.android.tools.inspectors.common.ui.dataviewer

import com.google.common.truth.Truth.assertThat
import com.intellij.json.JsonFileType
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.HeavyPlatformTestCase
import javax.swing.text.JTextComponent

class IntellijDataViewerTest : HeavyPlatformTestCase() {
  fun testCanCreateRawTextViewer() {
    val sampleText = "ASDF ".repeat(100)
    val viewer = IntellijDataViewer.createRawTextViewer(sampleText.toByteArray())

    assertThat(viewer.style).isEqualTo(DataViewer.Style.RAW)
    assertThat(viewer.component.preferredSize.height).isGreaterThan(0)
  }

  fun testCanCreatePrettyEditorViewer() {
    val jsonText = """{product: "Studio", version: 3.14}"""
    val viewer = IntellijDataViewer.createPrettyViewerIfPossible(project, jsonText.toByteArray(), JsonFileType.INSTANCE, true)

    assertThat(viewer.style).isEqualTo(DataViewer.Style.PRETTY)
    assertThat(viewer.component.preferredSize.height).isGreaterThan(0)
    assertExpectedEditorSettings(viewer)
  }

  fun testPlainTextCreatesPlainEditorViewerInsteadOfPrettyEditorViewer() {
    val sampleText = "ASDF ".repeat(100)
    val viewer = IntellijDataViewer.createPrettyViewerIfPossible(project, sampleText.toByteArray(), PlainTextFileType.INSTANCE, false)

    assertThat(viewer.style).isEqualTo(DataViewer.Style.RAW)
    assertThat(viewer.component.preferredSize.height).isGreaterThan(0)
    assertExpectedEditorSettings(viewer)
  }

  fun testCanCreateInvalidViewer() {
    val viewer = IntellijDataViewer.createInvalidViewer()

    assertThat(viewer.style).isEqualTo(DataViewer.Style.INVALID)
    assertThat(viewer.component.preferredSize.height).isGreaterThan(0)
  }

  fun testHandlesTextWithWindowsNewlines() {
    val textWithWindowsNewlines = "Content\r\nWith\r\nWindows\r\nNewlines"
    val viewer = IntellijDataViewer.createPrettyViewerIfPossible(project, textWithWindowsNewlines.toByteArray(), PlainTextFileType.INSTANCE, false)

    // At one point, windows newlines would have caused an exception, returning an invalid viewer
    assertThat(viewer.style).isNotEqualTo(DataViewer.Style.INVALID)
  }

  fun testRawTextViewerLimitsStringLength() {
    val veryLongText = "very long string".repeat(100)
    val viewer = IntellijDataViewer.createRawTextViewer(veryLongText.toByteArray())

    assertThat((viewer.component as JTextComponent).text).hasLength(500)
  }

  private fun assertExpectedEditorSettings(viewer: IntellijDataViewer) {
    val editor = EditorFactory.getInstance().allEditors.find { it.component == viewer.component }!!
    assertThat(editor.settings.isLineNumbersShown).isFalse()
    assertThat(editor.settings.softMargins).isEmpty()
    assertThat(editor.settings.isFoldingOutlineShown).isTrue()
  }
}