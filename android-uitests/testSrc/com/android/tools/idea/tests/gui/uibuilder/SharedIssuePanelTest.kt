/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.bleak.UseBleak
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.ProblemsPaneFixture
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

private const val LAYOUT_TAB_TITLE = "Layout and Qualifiers"
private const val LAYOUT_FILE_PATH = "app/src/main/res/layout/frames.xml"
private const val JAVA_FILE_PATH = "app/src/main/java/google/simpleapplication/MyActivity.java"

/**
 * UI test for the visualization tool window
 */
@RunWith(GuiTestRemoteRunner::class)
class SharedIssuePanelTest {
  @JvmField
  @Rule
  val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)

  @JvmField
  @Rule
  val renderTaskLeakCheckRule = RenderTaskLeakCheckRule()

  @Ignore("b/270432776")
  @Test
  @UseBleak
  @Throws(Exception::class)
  fun checkSharedIssuePanelVisibilityWithBleak() {
    val ideFixture = guiTest.importSimpleApplication()
    guiTest.runWithBleak { checkSharedIssuePanelVisibility(ideFixture.editor, ProblemsPaneFixture(ideFixture)) }
  }

  @Test
  fun checkSharedIssuePanelVisibility() {
    val ideFixture = guiTest.importSimpleApplication()
    checkSharedIssuePanelVisibility(ideFixture.editor, ProblemsPaneFixture(ideFixture))
  }

  private fun checkSharedIssuePanelVisibility(editor: EditorFixture, problemsPane: ProblemsPaneFixture) {
    problemsPane.activate()

    editor.open(LAYOUT_FILE_PATH).layoutEditor.waitForRenderToFinish()
    assertWithMessage("The '$LAYOUT_TAB_TITLE' should be visible after opening a layout")
      .that(problemsPane.doesTabExist(LAYOUT_TAB_TITLE)).isTrue()

    editor.open(JAVA_FILE_PATH)
    assertWithMessage("The '$LAYOUT_TAB_TITLE' should not be visible if the layout file is open in the background")
      .that(problemsPane.doesTabExist(LAYOUT_TAB_TITLE)).isFalse()

    // Test switching back. The tab should appear but the selected tab is still "Current File" tab because the layout file is opened
    // already.
    editor.open(LAYOUT_FILE_PATH).layoutEditor.waitForRenderToFinish()
    assertWithMessage("The '$LAYOUT_TAB_TITLE' should not be visible if the layout file is open in the background")
      .that(problemsPane.doesTabExist(LAYOUT_TAB_TITLE)).isTrue()

    editor.closeFile(LAYOUT_FILE_PATH)
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    guiTest.robot().waitForIdle()
    assertWithMessage("The '$LAYOUT_TAB_TITLE' should not be visible if the layout file is closed")
      .that(problemsPane.doesTabExist(LAYOUT_TAB_TITLE)).isFalse()

    editor.closeFile(LAYOUT_FILE_PATH).closeFile(JAVA_FILE_PATH)
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    guiTest.robot().waitForIdle()
  }
}
