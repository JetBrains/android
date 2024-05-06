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

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.WorkBenchFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.getSplitEditorFixture
import com.google.common.truth.Truth
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class CyclicMultiPreviewTest {

  @get:Rule
  val guiTest = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  /**
   * Basic functionality of multiple preview.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: a7eee7a5-dd40-4928-b349-9fd30ded4aaf
   * <p>
   *   <pre>
   *     Test Steps
   *     1)  Import the same project from below link
   *         https://drive.google.com/file/d/1umxatB8wjuLBg3q7cLYxPO53JgvuN55t/view
   *         (Sample has cyclic preview and multi previews in compose file)
   *     2) Open MainActivity.kt, select the ‘Split’ mode, and build the project(Verify 1)
   *
   *     Verification:
   *     1) Many previews should appear in the Preview panel of MainActivity.kt
   *   </pre>
   * <p>
   */
  @Test
  @Throws(Exception::class)
  fun cyclicMultiPreviewTest(){
    var ideFrame: IdeFrameFixture =  guiTest
      .importProjectAndWaitForProjectSyncToFinish("ComposeMultiPreviewSample")
    var editor = ideFrame.editor
      .open("app/src/main/java/com/example/composemultipreviewsample/MainActivity.kt")

    Truth.assertThat(guiTest.ideFrame()
                       .invokeProjectMake(Wait.seconds(180))
                       .isBuildSuccessful).isTrue()
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
      .waitForSceneViewsCount(9)

  }
}