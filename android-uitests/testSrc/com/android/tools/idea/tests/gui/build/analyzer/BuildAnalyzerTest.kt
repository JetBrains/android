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

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButton
import com.android.tools.idea.tests.gui.framework.fixture.BuildAnalyzerViewFixture
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.matcher.JButtonMatcher.withText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

@RunWith(GuiTestRemoteRunner::class)
class BuildAnalyzerTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule().withTimeout(7, TimeUnit.MINUTES)

  /**
   * * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: fcf6beb5-9a59-4f2c-9fab-428f378dcdb2
   * <p>
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

      guiTest.robot().waitForIdle()

      buildToolWindow.openBuildAnalyzerUsingTabHeaderClick().also { view ->
        view.verifyInitState()
        guiTest.waitForBackgroundTasks()
        guiTest.robot().waitForIdle()
        view.openTasksPage().also { page -> verifyTasksPage(view, page) }
        guiTest.waitForBackgroundTasks()
        guiTest.robot().waitForIdle()
        view.openWarningsPage().also { page -> verifyWarningsPage(page) }
        guiTest.waitForBackgroundTasks()
        guiTest.robot().waitForIdle()
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
    overviewPage.toString().contains("Gradle Daemon Memory Utilization")
    overviewPage.toString().contains("Build finished on \\d{1,2}\\/\\d{1,2}\\/\\d{1,2}, \\d{2}:\\d{2} [A|P]M")
    overviewPage.toString().contains("Total build duration was \\d{1,2}.\\d{1.2}s.")
    overviewPage.button(withText("Edit memory settings")).requireVisible()
    overviewPage.verifyLinkPresent("Tasks impacting build duration")
    overviewPage.verifyLinkPresent("Plugins with tasks impacting build duration")
    overviewPage.verifyLinkPresent("All warnings")
    overviewPage.toString().contains("Fine tune your JVM")
    overviewPage.toString().contains("Don't show this again")
  }

  private fun verifyTasksPage(view: BuildAnalyzerViewFixture, tasksPage: BuildAnalyzerViewFixture.BuildAnalyzerMasterDetailsPageFixture) {
    tasksPage.tree.requireNoSelection()
    tasksPage.findDetailsPanel("empty-details").requireVisible()

    tasksPage.tree.selectPath(":app:sample1")
    tasksPage.findDetailsPanel(":app:sample1").also { detailsPanel ->
      detailsPanel.requireVisible()
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
    guiTest.waitForBackgroundTasks()
    guiTest.robot().waitForIdle()
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
