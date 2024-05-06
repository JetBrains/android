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
import com.android.tools.adtui.instructions.RenderInstruction
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.WorkBenchFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.getSplitEditorFixture
import com.android.tools.idea.tests.util.WizardUtils
import com.google.common.truth.Truth
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws

@RunWith(GuiTestRemoteRunner::class)
class RemovePreviewTest {

  @get:Rule
  val guiTest = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  private val NO_PREVIEW_FOUND = "No preview found."

  private val ADD_PREVIEW_TEXT = "Add preview by annotating Composables with @Preview"

  /**
   * Remove a @Preview.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 46cdfb06-9dfe-42f4-a60b-6f2acd66be82
   * <p>
   *   <pre>
   *     Test Steps
   *     1) Setup basic Compose Project.
   *     2) Press the ""Build"" notification in the yellow notification area or select Build > Make Project
   *     3) Wait for the build to finish
   *     4) Add a preview such as below if there is no preview present
   *           @Preview
   *           @Composable
   *           fun Preview() {
   *             Greeting(""Preview"")
   *          }
   *     4) Remove the first preview
   *
   *     Verification:
   *     1) First preview should disappear from the preview panel without needing to refresh.
   *   </pre>
   * <p>
   */
  @Test
  @Throws(Exception::class)
  fun removePreviewTest() {
    WizardUtils.createNewProject(guiTest, FormFactor.MOBILE, "Empty Activity")
    var ideFrame: IdeFrameFixture = guiTest.ideFrame()
    var editor = ideFrame.editor
    ideFrame.invokeProjectMake(Wait.seconds(300))
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    // Selecting the split windows is flaky on remote machines.
    // Adding an extra wait to make sure split mode is enabled.
    Wait
      .seconds(20)
      .expecting("Waiting for split mode to load ...")
      .until {
        editor.selectEditorTab(EditorFixture.Tab.SPLIT)
        WorkBenchFixture.findShowing(ideFrame.target(),ideFrame.robot())
          .isShowingContent()
      }

    guiTest.waitForAllBackgroundTasksToBeCompleted()
    editor.getSplitEditorFixture()
      .waitForRenderToFinish()
      .waitForSceneViewsCount(1)

    editor.select("(@Preview)").selectCurrentLine()
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE)

    val workBenchFixture= WorkBenchFixture.findShowing(ideFrame.target(),ideFrame.robot())
    // Wait for the preview panel to be updated with no previews.
    // Verify the workbench do not contain any preview content.
    Wait
      .seconds(10)
      .expecting("Waiting for preview to be deleted...")
      .until {!workBenchFixture.isShowingContent()}

    Truth.assertThat(workBenchFixture.getInstructionsPanelDisplayText()
                       .contains(NO_PREVIEW_FOUND)).isTrue()

    Truth.assertThat(workBenchFixture.getInstructionsPanelDisplayText()
                       .contains(ADD_PREVIEW_TEXT)).isTrue()
    editor.close()
  }
}