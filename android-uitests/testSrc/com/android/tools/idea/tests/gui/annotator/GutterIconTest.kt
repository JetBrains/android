/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.annotator

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.CompactResourcePickerFixture
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRemoteRunner::class)
class GutterIconTest {
  @get:Rule
  val guiTest = GuiTestRule()

  @Test
  fun xmlDrawableGutterIconPickerTest() {
    val editor = guiTest.importSimpleApplication().editor
    val editorLine = editor
                       .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
                       .moveBetween("TextView", "")
                       .enterText("\nandroid:background=\"@drawable/vector\"")
                       .moveBetween("drawable/", "vector")
                       .currentLineNumber - 1 // Line number is 1-based, but we need the 0-based index

    // Wait for the gutter to be populated
    val editorGutter = guiTest.robot().finder().findByType(EditorGutterComponentEx::class.java)
    Wait.seconds(10L).expecting("Drawable gutter icon to be available").until {
      runInEdtAndGet {
        editorGutter.getGutterRenderers(editorLine).isNotEmpty()
      }
    }

    // Find and click the drawable gutter Icon
    val gutterIconPoint =
      runInEdtAndGet { editorGutter.getCenterPoint(editorGutter.getGutterRenderers(editorLine).firstIsInstance<GutterIconRenderer>())!! }
    guiTest.robot().click(editorGutter, gutterIconPoint)

    // It should open the CompactResourcePicker popup, select a different drawable in it
    CompactResourcePickerFixture.find(guiTest)
      .selectResource("ic_launcher")

    // The editor should reflect the change of the new selected drawable
    assertEquals("android:background=\"@drawable/ic_launcher\"", editor.currentLine.trimIndent())
  }
}