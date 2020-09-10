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
package com.android.tools.idea.tests.gui.build.analyzer

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButton
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.BuildAnalyzerViewFixture
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(GuiTestRemoteRunner::class)
@RunIn(TestGroup.UNRELIABLE)
class BuildAnalyzerTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Before
  fun setUp() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.override(true)
    StudioFlags.NEW_BUILD_ANALYZER_UI_NAVIGATION_ENABLED.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.clearOverride()
    StudioFlags.NEW_BUILD_ANALYZER_UI_NAVIGATION_ENABLED.clearOverride()
  }

  /**
   * Test user path through Build Analyzer feature.
   * Use project with two fake tasks added to the build to have warnings in the report.
   * Sync and build to get the report.
   *
   * Open build analyzer tab, check pages are listed as expected and page changing works.
   * Check detailed report dialog can be opened and closed.
   * Check tab can be closed and re-opened from a build output link.
   */
  @Test
  fun testBuildAnalyzerFlow() {
    val ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("BuildAttributionApp")

    ideFrame.buildToolWindow.also { buildToolWindow ->
      buildToolWindow.activate()

      val result = ideFrame.invokeProjectMake()
      assertTrue(result.isBuildSuccessful)

      buildToolWindow.openBuildAnalyzerUsingTabHeaderClick().also { view ->
        view.verifyInitState()
        view.openTasksPage().also { page -> verifyTasksPage(view, page) }
        view.openWarningsPage().also { page -> verifyWarningsPage(page) }
        view.openOverviewPage()
      }
      buildToolWindow.closeBuildAnalyzerTab()

      buildToolWindow.openBuildAnalyzerUsingBuildOutputLink().verifyInitState()
      buildToolWindow.closeBuildAnalyzerTab()
    }

    ideFrame.closeBuildPanel()
  }

  private fun BuildAnalyzerViewFixture.verifyInitState() {
    pageComboBox.requireSelection("Overview")
    overviewPage.requireVisible()
  }

  private fun verifyTasksPage(view: BuildAnalyzerViewFixture, tasksPage: BuildAnalyzerViewFixture.BuildAnalyzerMasterDetailsPageFixture) {
    tasksPage.tree.requireNoSelection()
    tasksPage.findDetailsPanel("empty-details").requireVisible()

    tasksPage.tree.selectPath(":app:sample1")
    tasksPage.findDetailsPanel(":app:sample1").also { detailsPanel ->
      detailsPanel.requireVisible()
      detailsPanel.findWarningPanel("ALWAYS_RUN_TASKS").requireVisible()
      detailsPanel.clickGenerateReport()
      guiTest.ideFrame().waitForDialog("Plugin Issue Report").also { dialog ->
        findAndClickButton(dialog, "Copy")
        findAndClickButton(dialog, "Close")
        dialog.requireNotVisible()
      }
    }

    tasksPage.tree.selectPath(":app:sample2")
    tasksPage.findDetailsPanel(":app:sample2").requireVisible()

    // Switch to plugins
    view.tasksGroupingCheckbox.click()
    // Selected task should stay the same under plugin
    tasksPage.tree.requireSelection("SamplePlugin/:app:sample2")
    tasksPage.findDetailsPanel(":app:sample2").requireVisible()
    // Select plugin page with keyboard left key
    tasksPage.pressKeyboardLeftOnTree()
    tasksPage.tree.requireSelection("SamplePlugin")
    tasksPage.findDetailsPanel("SamplePlugin").also { detailsPanel ->
      detailsPanel.requireVisible()
      detailsPanel.clickNavigationLink(":app:sample1")
    }

    tasksPage.tree.requireSelection("SamplePlugin/:app:sample1")
    tasksPage.findDetailsPanel(":app:sample1").requireVisible()
  }

  private fun verifyWarningsPage(warningsPage: BuildAnalyzerViewFixture.BuildAnalyzerMasterDetailsPageFixture) {
    warningsPage.tree.requireNoSelection()
    warningsPage.findDetailsPanel("empty-details").requireVisible()

    warningsPage.tree.selectPath("Always-Run Tasks")
    warningsPage.findDetailsPanel("ALWAYS_RUN_TASKS").requireVisible()

    warningsPage.tree.focus()
    warningsPage.pressKeyboardRightOnTree()
    warningsPage.pressKeyboardDownOnTree()
    warningsPage.tree.requireSelection(1)
    warningsPage.pressKeyboardDownOnTree()
    warningsPage.tree.requireSelection(2)

    warningsPage.tree.selectPath("Always-Run Tasks/:app:sample1")
    warningsPage.findDetailsPanel("ALWAYS_RUN_TASKS-:app:sample1").requireVisible()

    warningsPage.tree.selectPath("Always-Run Tasks/:app:sample2")
    warningsPage.findDetailsPanel("ALWAYS_RUN_TASKS-:app:sample2").requireVisible()
  }
}
