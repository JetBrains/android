/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.editors.fonts

import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.ui.UIUtil
import javax.swing.JLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FontEditorTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  @Test
  fun testHtmlDisabledInLabel() {
    val fontPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData/fonts/customfont.ttf")
    val fontFile = fontPath.toFile()
    assertTrue("Font file not found at ${fontFile.absolutePath}", fontFile.exists())

    val bytes = FileUtil.loadFileBytes(fontFile)
    val tempFile = fixture.tempDirFixture.createFile("customfont.ttf")
    runWriteAction { tempFile.setBinaryContent(bytes) }

    val editor = FontEditor(tempFile)
    Disposer.register(fixture.testRootDisposable, editor)
    val rootPanel = editor.component
    val label = requireNotNull(UIUtil.findComponentOfType(rootPanel, JLabel::class.java)) { "JLabel not found in FontEditor" }

    val htmlDisable = label.getClientProperty("html.disable")
    assertEquals(true, htmlDisable)
  }
}
