/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.android.tools.profilers.stacktrace.DataViewer
import com.google.common.truth.Truth.assertThat
import com.intellij.json.JsonFileType
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.IdeaTestCase
import java.awt.Dimension
import java.awt.image.BufferedImage

class IntellijDataViewerTest : IdeaTestCase() {
  fun testCanCreateRawTextViewer() {
    val dummyText = "ASDF ".repeat(100)
    val viewer = IntellijDataViewer.createRawTextViewer(dummyText.toByteArray())

    assertThat(viewer.style).isEqualTo(DataViewer.Style.RAW)
    assertThat(viewer.imageDimension).isNull()
    assertThat(viewer.component.preferredSize.height).isGreaterThan(0)
  }

  fun testCanCreatePrettyEditorViewer() {
    val jsonText = """{product: "Studio", version: 3.14}"""
    val viewer = IntellijDataViewer.createPrettyViewerIfPossible(project, jsonText.toByteArray(), JsonFileType.INSTANCE)

    assertThat(viewer.style).isEqualTo(DataViewer.Style.PRETTY)
    assertThat(viewer.imageDimension).isNull()
    assertThat(viewer.component.preferredSize.height).isGreaterThan(0)
    assertExpectedEditorSettings(viewer)
  }

  fun testPlainTextCreatesPlainEditorViewerInsteadOfPrettyEditorViewer() {
    val dummyText = "ASDF ".repeat(100)
    val viewer = IntellijDataViewer.createPrettyViewerIfPossible(project, dummyText.toByteArray(), PlainTextFileType.INSTANCE)

    assertThat(viewer.style).isEqualTo(DataViewer.Style.RAW)
    assertThat(viewer.imageDimension).isNull()
    assertThat(viewer.component.preferredSize.height).isGreaterThan(0)
    assertExpectedEditorSettings(viewer)
  }

  fun testCanCreateImageViewer() {
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
    val viewer = IntellijDataViewer.createImageViewer(image)

    assertThat(viewer.style).isEqualTo(DataViewer.Style.RAW)
    assertThat(viewer.imageDimension).isEqualTo(Dimension(100, 100))
    assertThat(viewer.component.preferredSize.height).isGreaterThan(0)
  }

  fun testCanCreateInvalidViewer() {
    val viewer = IntellijDataViewer.createInvalidViewer()

    assertThat(viewer.style).isEqualTo(DataViewer.Style.INVALID)
    assertThat(viewer.imageDimension).isNull()
    assertThat(viewer.component.preferredSize.height).isGreaterThan(0)
  }

  fun testHandlesTextWithWindowsNewlines() {
    val textWithWindowsNewlines = "Content\r\nWith\r\nWindows\r\nNewlines"
    val viewer = IntellijDataViewer.createPrettyViewerIfPossible(project, textWithWindowsNewlines.toByteArray(), PlainTextFileType.INSTANCE)

    // At one point, windows newlines would have caused an exception, returning an invalid viewer
    assertThat(viewer.style).isNotEqualTo(DataViewer.Style.INVALID)
  }

  private fun assertExpectedEditorSettings(viewer: IntellijDataViewer) {
    val editor = EditorFactory.getInstance().allEditors.find { it.component == viewer.component }!!
    assertThat(editor.settings.isLineNumbersShown).isFalse()
    assertThat(editor.settings.softMargins).isEmpty()
    assertThat(editor.settings.isFoldingOutlineShown).isTrue()
  }
}