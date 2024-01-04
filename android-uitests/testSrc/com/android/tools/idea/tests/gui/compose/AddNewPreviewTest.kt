/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.compose

import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.WorkBenchFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.getSplitEditorFixture
import com.android.tools.idea.tests.util.WizardUtils
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws

@RunWith(GuiTestRemoteRunner::class)
class AddNewPreviewTest {

  @get:Rule
  val guiTest = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  @Test
  @Throws(Exception::class)
  fun testAddAdditionalPreview(){
    WizardUtils.createNewProject(guiTest, FormFactor.MOBILE, "Empty Activity")
    var ideFrame: IdeFrameFixture = guiTest.ideFrame()
    var editor = ideFrame.editor
    ideFrame.invokeProjectMake(Wait.seconds(300))
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    // Selecting the split windows is flaky on remote machines.
    // Adding an extra wait to make sure split mode is selected.
    Wait.seconds(60)
      .expecting("Waiting for split mode to load ...")
      .until {
        editor.selectEditorTab(EditorFixture.Tab.SPLIT)
        WorkBenchFixture.findShowing(ideFrame.target(),
                                     ideFrame.robot()).isShowingContent()
      }

    guiTest.waitForAllBackgroundTasksToBeCompleted()
    editor.getSplitEditorFixture()
      .waitForRenderToFinish()
      .waitForSceneViewsCount(1)

    editor.invokeAction(EditorFixture.EditorAction.TEXT_END)
      .pressAndReleaseKeys(KeyEvent.VK_ENTER)
      // The closing braces are not needed since they are added by the editor automatically
      .typeText("""
        @Preview(name = "Second")
        @Composable
        fun SecondPreview() {
          MaterialTheme {
            Text(text = "A second preview")
      """.trimIndent())

    guiTest.waitForAllBackgroundTasksToBeCompleted()
    // Check the new preview has been added
    editor.getSplitEditorFixture()
      .waitForRenderToFinish()
      .waitForSceneViewsCount(2)
  }
}