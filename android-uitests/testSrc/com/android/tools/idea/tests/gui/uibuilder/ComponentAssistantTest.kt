/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.uibuilder

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/**
 * UI test for the component assistant in the properties panel
 */
@RunWith(GuiTestRemoteRunner::class)
class ComponentAssistantTest {

  @JvmField
  @Rule
  val guiTest = GuiTestRule()

  @JvmField
  @Rule
  val renderTaskLeakCheckRule = RenderTaskLeakCheckRule()

  @Before
  fun setUp() {
    StudioFlags.NELE_SAMPLE_DATA_UI.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.NELE_SAMPLE_DATA_UI.clearOverride()
  }

  @Test
  fun testRecyclerViewAssistantAvailable() {
    val layout = guiTest.importSimpleApplication()
      .editor
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getLayoutEditor(true)

    layout.dragComponentToSurface("Containers", "RecyclerView")
    MessagesFixture.findByTitle(guiTest.robot(), "Add Project Dependency").clickOk()
    val editor = guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish()
      .editor

    layout.waitForRenderToFinish()
      .findView("android.support.v7.widget.RecyclerView", 0)
      .click()
      .openComponentAssistant()
      .getRecyclerViewAssistant().apply {
        spinner.nextButton().click()
        //TODO: Chage itemCount
      }
      .close()

    // Verify changes
    editor.selectEditorTab(EditorFixture.Tab.EDITOR)
    assertTrue(editor.currentFileContents.contains("tools:listitem=\"@layout/recycler_view_item\""))

    editor.selectEditorTab(EditorFixture.Tab.DESIGN)
    layout.findView("android.support.v7.widget.RecyclerView", 0)
      .click()
      .openComponentAssistant()
      .getRecyclerViewAssistant().apply {
        // Verify that changes persist
        assertEquals("E-mail Client", spinner.value())
        // Restore back to default
        spinner.prevButton().click()
      }
      .close()

    editor.selectEditorTab(EditorFixture.Tab.EDITOR)
    assertFalse(editor.currentFileContents.contains("tools:listitem=\"@layout/recycler_view_item\""))
  }

  @Test
  fun testTextViewAssistantAvailable() {
    val layout = guiTest.importSimpleApplication()
      .editor
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getLayoutEditor(true)

    layout.dragComponentToSurface("Text", "TextView")
    layout.findView("TextView", 1)
      .click()
      .openComponentAssistant()
      .getTextViewAssistant().apply {
        combo.selectItem("first_names")
      }

    // Verify changes
    val editor = guiTest.ideFrame().editor
    editor.selectEditorTab(EditorFixture.Tab.EDITOR)
    val contents = editor.currentFileContents
    assertTrue(contents.contains("tools:text=\"@tools:sample/first_names\""))
    editor.selectEditorTab(EditorFixture.Tab.DESIGN)

    // Verify that the element still displays as selected in the assistant panel
    layout.findView("TextView", 1)
      .click()
      .openComponentAssistant()
      .getTextViewAssistant()
      .combo
      .requireSelection("first_names")
  }
}
