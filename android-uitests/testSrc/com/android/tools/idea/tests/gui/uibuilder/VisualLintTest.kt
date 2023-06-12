/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.ProblemsPaneFixture
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class VisualLintTest {
  @JvmField
  @Rule
  val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)

  @JvmField
  @Rule
  val renderTaskLeakCheckRule = RenderTaskLeakCheckRule()

  @Before
  fun setup() {
    StudioFlags.NELE_VISUAL_LINT.override(true)
    StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.NELE_VISUAL_LINT.clearOverride()
    StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.clearOverride()
  }

  @Test
  @Throws(Exception::class)
  fun testOpenProblemsPaneFromLayoutValidation() {
    val ideFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("VisualLintApplication")
    openProblemsPaneFromLayoutValidation(ideFixture)
  }

  @Test
  @UseBleak
  @RunIn(TestGroup.PERFORMANCE)
  @Throws(Exception::class)
  fun testOpenProblemsPaneFromLayoutValidationWithBleak() {
    val ideFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("VisualLintApplication")
    guiTest.runWithBleak {
      openProblemsPaneFromLayoutValidation(ideFixture)
    }
  }

  @Throws(Exception::class)
  private fun openProblemsPaneFromLayoutValidation(ideFixture: IdeFrameFixture) {
    val editor = ideFixture.editor
    val layoutFile = "app/src/main/res/layout/activity_main.xml"

    editor.open(layoutFile)
    editor.visualizationTool.waitForRenderToFinish()
    editor.visualizationTool.openProblemsPanel()
    ProblemsPaneFixture(ideFixture).waitUntilIsVisible().close()
    editor.visualizationTool.hide()
    editor.closeFile(layoutFile)
  }
}