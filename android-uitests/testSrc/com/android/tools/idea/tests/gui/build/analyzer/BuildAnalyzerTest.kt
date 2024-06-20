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
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.matcher.JButtonMatcher.withText
import org.fest.swing.fixture.JPanelFixture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(GuiTestRemoteRunner::class)
class BuildAnalyzerTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule().withTimeout(9, TimeUnit.MINUTES)

  /**
   * * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: fcf6beb5-9a59-4f2c-9fab-428f378dcdb2
   * <p>
   * Test user path through Info on download impact during sync.
   *
   * Test Steps:
   * The test opens an existing project.
   * Opens build tools window, checks for "Downloads info" node.
   * Checks if the table is present.
   * Triggers gradle sync again.
   * Checks if the node and table is present.
   */

  @Test
  fun testDownloadsInfoOnSync() {
    val ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("BuildAttributionApp")
    ideFrame.clearNotificationsPresentOnIdeFrame()

    ideFrame.buildToolWindow.also { buildToolWindow ->
      buildToolWindow.activate()
      guiTest.waitForAllBackgroundTasksToBeCompleted()

      buildToolWindow.gradleSyncEventTree.also { tree ->
        assertEquals("Download info", tree.valueAt(1))
        tree.clickRow(1)
      }
      buildToolWindow.syncContent.component.also {
        JPanelFixture(guiTest.robot(), guiTest.robot().finder().findByName(it, "downloads info build output panel", JPanel::class.java)).requireVisible()
      }

      ideFrame.requestProjectSyncAndWaitForSyncToFinish()
      guiTest.robot().waitForIdle()

      buildToolWindow.gradleSyncEventTree.also { tree ->
        assertEquals("Download info", tree.valueAt(1))
        tree.clickRow(1)
      }
      buildToolWindow.syncContent.component.also {
        JPanelFixture(guiTest.robot(), guiTest.robot().finder().findByName(it, "downloads info build output panel", JPanel::class.java)).requireVisible()
      }
    }
  }

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
    ideFrame.clearNotificationsPresentOnIdeFrame()

    ideFrame.buildToolWindow.also { buildToolWindow ->
      buildToolWindow.activate()
      guiTest.robot().waitForIdle()

      val result = ideFrame.invokeProjectMake()
      assertTrue(result.isBuildSuccessful)
      guiTest.robot().waitForIdle()

      buildToolWindow.openBuildAnalyzerUsingTabHeaderClick().also { view ->
        view.verifyOverviewPage()
        guiTest.waitForAllBackgroundTasksToBeCompleted()
        view.openTasksPage().also { page -> verifyTasksPage(view, page) }
        guiTest.waitForAllBackgroundTasksToBeCompleted()
        view.openWarningsPage().also { page -> verifyWarningsPage(view, page) }
        guiTest.waitForAllBackgroundTasksToBeCompleted()
        view.openDownloadsPage()
        guiTest.waitForAllBackgroundTasksToBeCompleted()
        view.openOverviewPage()
        guiTest.waitForAllBackgroundTasksToBeCompleted()
        view.verifyLinksPresentInOverviewPage()
      }
      buildToolWindow.closeBuildAnalyzerTab()

      buildToolWindow.openBuildAnalyzerUsingBuildOutputLink().verifyOverviewPage()
      buildToolWindow.closeBuildAnalyzerTab()

      buildToolWindow.gradleBuildEventTree.also { tree ->
        assertEquals("Download info", tree.valueAt(1))
        tree.clickRow(1)
      }
      buildToolWindow.buildContent.component.also {
        JPanelFixture(guiTest.robot(), guiTest.robot().finder().findByName(it, "downloads info build output panel", JPanel::class.java)).requireVisible()
      }
    }
    ideFrame.closeBuildPanel()
  }

  private fun BuildAnalyzerViewFixture.verifyOverviewPage() {
    // Open Overview page
    pageComboBox.requireSelection("Overview")
    overviewPage.requireVisible()
    overviewPage.toString().contains("Gradle Daemon Memory Utilization")
    overviewPage.toString().contains("Build finished on \\d{1,2}\\/\\d{1,2}\\/\\d{1,2}, \\d{2}:\\d{2} [A|P]M")
    overviewPage.toString().contains("Total build duration was \\d{1,2}.\\d{1.2}s.")
    overviewPage.toString().contains("Fine tune your JVM")
    overviewPage.toString().contains("Don't show this again")
    //Verifying the navigation links present in overview page
    overviewPage.verifyLinkPresent("Tasks impacting build duration")
    overviewPage.verifyLinkPresent("Plugins with tasks impacting build duration")
    overviewPage.verifyLinkPresent("All warnings")
    //Check if the settings dialog box can be opened.
    overviewPage.button(withText("Edit memory settings")).requireVisible().click()
    val settingsDialog = IdeSettingsDialogFixture.find(guiTest.ideFrame().robot())
    settingsDialog.requireVisible()
    settingsDialog.clickButton("Cancel")
    guiTest.waitForAllBackgroundTasksToBeCompleted()
  }

  private fun BuildAnalyzerViewFixture.verifyLinksPresentInOverviewPage() {
    overviewPage.clickLink("Tasks impacting build duration")
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    pageComboBox.requireSelection("Tasks")
    tasksGroupByComboBox.requireSelection("Task Category")
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    pageComboBox.selectItem("Overview")
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    overviewPage.clickLink("Plugins with tasks impacting build duration")
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    pageComboBox.requireSelection("Tasks")
    tasksGroupByComboBox.requireSelection("Plugin")
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    pageComboBox.selectItem("Overview")
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    overviewPage.clickLink("All warnings")
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    pageComboBox.requireSelection("Warnings")
    guiTest.waitForAllBackgroundTasksToBeCompleted()
  }

  private fun verifyTasksPage(view: BuildAnalyzerViewFixture, tasksPage: BuildAnalyzerViewFixture.BuildAnalyzerMasterDetailsPageFixture) {
    //Array of tasks that are going to run.
    val totalBuildTasks = arrayOf<String>("Android Resources", "Uncategorized")

    //Check if the right page is opened.
    view.pageComboBox.requireSelection("Tasks")

    //Check Group combo box content details
    assertTrue ( view.tasksGroupByComboBox.isEnabled )
    val taskGroupByContents = view.tasksGroupByComboBox.contents()
    assertTrue ( view.tasksGroupByComboBox.selectedItem().equals("Task Category", true) ) //Verifying if 'Task Category' is selected by default
    assertTrue ( taskGroupByContents.contains("Plugin") )
    assertTrue ( taskGroupByContents.contains("No Grouping") )
    assertTrue ( taskGroupByContents.contains("Task Category") )

    //Validate Group by: Task Category
    view.tasksGroupByComboBox.selectItem("Task Category")
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    //Validating the tasks.
    assertTrue ( tasksPage.tree.rowCount >= totalBuildTasks.size )  //Check total tasks present
    for (task in totalBuildTasks)
      tasksPage.verifyTaskDetails(task)
      guiTest.waitForAllBackgroundTasksToBeCompleted()

    //Validating errors shown in tasks.
    tasksPage.tree.clickPath("Android Resources")
    tasksPage.findDetailsPanel("Android Resources").also { detailsPanel ->
      detailsPanel.requireVisible()
      val androidResourcesPageDetails = detailsPanel.readTaskDetails()
      assertTrue ( androidResourcesPageDetails.contains(Regex("\\d+ warnings? associated")) )
      assertTrue ( androidResourcesPageDetails.contains("Non-transitive R classes are currently disabled."))
      //detailsPanel.verifyLinkPresent("Click here to migrate your project to use non-transitive R classes")
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    tasksPage.tree.expandPath("Uncategorized")
    tasksPage.tree.selectPath("Uncategorized/:app:sample1")
    tasksPage.findDetailsPanel(":app:sample1").also { detailsPanel ->
      detailsPanel.requireVisible()
      detailsPanel.clickGenerateReport()
      guiTest.waitForAllBackgroundTasksToBeCompleted()
      guiTest.ideFrame().waitForDialog("Plugin Issue Report").also { dialog ->
        findAndClickButton(dialog, "Copy")
        findAndClickButton(dialog, "Close")
        dialog.requireNotVisible()
      }
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    tasksPage.tree.selectPath("Uncategorized/:app:sample2")
    tasksPage.findDetailsPanel(":app:sample2").also { detailsPanel ->
      detailsPanel.requireVisible()
      detailsPanel.clickGenerateReport()
      guiTest.waitForAllBackgroundTasksToBeCompleted()
      guiTest.ideFrame().waitForDialog("Plugin Issue Report").also { dialog ->
        findAndClickButton(dialog, "Copy")
        findAndClickButton(dialog, "Close")
        dialog.requireNotVisible()
      }
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    //Validate Group by: Plugins
    view.tasksGroupByComboBox.selectItem("Plugin")
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    //Check tasks grouped by plugins and its associated warnings
    tasksPage.tree.clickPath("SamplePlugin")
    tasksPage.findDetailsPanel("SamplePlugin").also { detailsPanel ->
      detailsPanel.requireVisible()
      val samplePluginPageDetails = detailsPanel.readTaskDetails()
      tasksPage.verifyTaskDetails("SamplePlugin")
      assertTrue ( samplePluginPageDetails.contains(Regex("\\d+\\s+tasks")) )
      assertTrue ( samplePluginPageDetails.contains("app:sample2") )
      assertTrue ( samplePluginPageDetails.contains("app:sample1") )
      detailsPanel.clickNavigationLink("app:sample2")
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    tasksPage.findDetailsPanel(":app:sample2").also { detailsPanel ->
      detailsPanel.requireVisible()
      val appSample2 = detailsPanel.readTaskDetails()
      assertTrue ( appSample2.contains("Duration") )
      assertTrue ( appSample2.contains(Regex("Sub-project:\\s+:app")) )
      assertTrue ( appSample2.contains(Regex("Plugin:\\s+SamplePlugin")) )
      assertTrue ( appSample2.contains(Regex("Type:\\s+SampleTask")) )
      assertTrue ( appSample2.contains(Regex("Categories:\\s+Uncategorized")) )
      assertTrue ( appSample2.contains("Warnings") )
      assertTrue ( appSample2.contains("Reason task ran") )
      detailsPanel.clickGenerateReport()
      guiTest.waitForAllBackgroundTasksToBeCompleted()
      guiTest.ideFrame().waitForDialog("Plugin Issue Report").also { dialog ->
        findAndClickButton(dialog, "Copy")
        findAndClickButton(dialog, "Close")
        dialog.requireNotVisible()
      }
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    tasksPage.tree.collapsePath("SamplePlugin")
    tasksPage.tree.expandPath("SamplePlugin")
    tasksPage.tree.clickPath("SamplePlugin/:app:sample1")
    tasksPage.findDetailsPanel(":app:sample1").also { detailsPanel ->
      detailsPanel.requireVisible()
      val appSample1 = detailsPanel.readTaskDetails()
      assertTrue ( appSample1.contains("Duration") )
      assertTrue ( appSample1.contains(Regex("Sub-project:\\s+:app")) )
      assertTrue ( appSample1.contains(Regex("Plugin:\\s+SamplePlugin")) )
      assertTrue ( appSample1.contains(Regex("Type:\\s+SampleTask")) )
      assertTrue ( appSample1.contains(Regex("Categories:\\s+Uncategorized")) )
      assertTrue ( appSample1.contains("Warnings") )
      assertTrue ( appSample1.contains("Reason task ran") )
      detailsPanel.clickGenerateReport()
      guiTest.waitForAllBackgroundTasksToBeCompleted()
      guiTest.ideFrame().waitForDialog("Plugin Issue Report").also { dialog ->
        findAndClickButton(dialog, "Copy")
        findAndClickButton(dialog, "Close")
        dialog.requireNotVisible()
      }
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    tasksPage.tree.clickPath("SamplePlugin/:app:sample1")

    //Validate Group by: No grouping
    view.tasksGroupByComboBox.selectItem("No Grouping")
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    //Switching to "No grouping" should still highlight the selected task (or) switching group by options shouldnt affect the selection.
    tasksPage.findDetailsPanel(":app:sample1").also { detailsPanel ->
      detailsPanel.requireVisible()
      val appSample1 = detailsPanel.readTaskDetails()
      assertTrue ( appSample1.contains("Duration") )
      assertTrue ( appSample1.contains(Regex("Sub-project:\\s+:app")) )
      assertTrue ( appSample1.contains(Regex("Plugin:\\s+SamplePlugin")) )
      assertTrue ( appSample1.contains(Regex("Type:\\s+SampleTask")) )
      assertTrue ( appSample1.contains(Regex("Categories:\\s+Uncategorized")) )
      assertTrue ( appSample1.contains("Warnings") )
      assertTrue ( appSample1.contains("Reason task ran") )
      detailsPanel.clickGenerateReport()
      guiTest.waitForAllBackgroundTasksToBeCompleted()
      guiTest.ideFrame().waitForDialog("Plugin Issue Report").also { dialog ->
        findAndClickButton(dialog, "Copy")
        findAndClickButton(dialog, "Close")
        dialog.requireNotVisible()
      }
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    tasksPage.tree.clickPath(":app:sample2")
    tasksPage.findDetailsPanel(":app:sample2").also { detailsPanel ->
      detailsPanel.requireVisible()
      val appSample2 = detailsPanel.readTaskDetails()
      assertTrue ( appSample2.contains("Duration") )
      assertTrue ( appSample2.contains(Regex("Sub-project:\\s+:app")) )
      assertTrue ( appSample2.contains(Regex("Plugin:\\s+SamplePlugin")) )
      assertTrue ( appSample2.contains(Regex("Type:\\s+SampleTask")) )
      assertTrue ( appSample2.contains(Regex("Categories:\\s+Uncategorized")) )
      assertTrue ( appSample2.contains("Warnings") )
      assertTrue ( appSample2.contains("Reason task ran") )
      detailsPanel.clickGenerateReport()
      guiTest.waitForAllBackgroundTasksToBeCompleted()
      guiTest.ideFrame().waitForDialog("Plugin Issue Report").also { dialog ->
        findAndClickButton(dialog, "Copy")
        findAndClickButton(dialog, "Close")
        dialog.requireNotVisible()
      }
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    tasksPage.tree.focus()
    tasksPage.pressKeyboardRightOnTree()
    tasksPage.pressKeyboardDownOnTree()
    tasksPage.pressKeyboardDownOnTree()
  }

  private fun verifyWarningsPage(view: BuildAnalyzerViewFixture, warningsPage: BuildAnalyzerViewFixture.BuildAnalyzerMasterDetailsPageFixture) {
    view.pageComboBox.requireSelection("Warnings")

    warningsPage.tree.requireNoSelection()
    warningsPage.findDetailsPanel("empty-details").requireVisible()

    //Verifying if the warnings are being displayed without group-by plugins
    warningsPage.tree.selectPath("Always-Run Tasks")
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    warningsPage.findDetailsPanel("ALWAYS_RUN_TASKS").also { detailsPanel ->
      detailsPanel.requireVisible()
      val alwaysRunTasksDetailsPage = detailsPanel.readTaskDetails()
      assertTrue ( alwaysRunTasksDetailsPage.contains("Always-Run Tasks") )
      assertTrue ( alwaysRunTasksDetailsPage.contains("Duration:") )
      assertTrue ( alwaysRunTasksDetailsPage.contains(Regex("\\d+\\s+warnings?")) )
      assertTrue ( alwaysRunTasksDetailsPage.contains(":app:sample2") )
      assertTrue ( alwaysRunTasksDetailsPage.contains(":app:sample1") )
    }

    warningsPage.tree.expandPath("Always-Run Tasks")
    warningsPage.tree.selectPath("Always-Run Tasks/:app:sample1")
    warningsPage.findDetailsPanel("ALWAYS_RUN_TASKS-:app:sample1").requireVisible()
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    warningsPage.tree.selectPath("Always-Run Tasks/:app:sample2")
    warningsPage.findDetailsPanel("ALWAYS_RUN_TASKS-:app:sample2").requireVisible()
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    //Enable Group by plugin checkbox
    view.warningsGroupingCheckbox.click()
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    warningsPage.tree.requireNoSelection()
    warningsPage.findDetailsPanel("empty-details").requireVisible()

    //Verifying id all the warnings are being displayed by plugins
    warningsPage.tree.selectPath("SamplePlugin")
    warningsPage.findDetailsPanel("SamplePlugin").also { detailsPanel ->
      detailsPanel.requireVisible()
      val alwaysRunTasksDetailsPage = detailsPanel.readTaskDetails()
      assertTrue ( alwaysRunTasksDetailsPage.contains(Regex("SamplePlugin")) )
      assertTrue ( alwaysRunTasksDetailsPage.contains("Duration:") )
      assertTrue ( alwaysRunTasksDetailsPage.contains(Regex("\\d+\\s+warnings")) )
      assertTrue ( alwaysRunTasksDetailsPage.contains(":app:sample2") )
      assertTrue ( alwaysRunTasksDetailsPage.contains(":app:sample1") )
    }

    //Validating errors shown in warnings page
    warningsPage.tree.clickPath("Android Resources")
    warningsPage.findDetailsPanel("ANDROID_RESOURCES").also { detailsPanel ->
      detailsPanel.requireVisible()
      val androidResourcesPageDetails = detailsPanel.readTaskDetails()
      assertTrue ( androidResourcesPageDetails.contains(Regex("\\d+\\s+warnings?")) )
      assertTrue ( androidResourcesPageDetails.contains("Non-transitive R classes are currently disabled.") )
      //detailsPanel.verifyLinkPresent("Click here to migrate your project to use non-transitive R classes")
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    warningsPage.tree.expandPath("SamplePlugin")
    warningsPage.tree.clickPath("SamplePlugin/:app:sample1")
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    warningsPage.findDetailsPanel(":app:sample1").also { detailsPanel ->
      detailsPanel.requireVisible()
      val appSample1 = detailsPanel.readTaskDetails()
      assertTrue ( appSample1.contains("Duration") )
      assertTrue ( appSample1.contains(Regex("Sub-project:\\s+:app")) )
      assertTrue ( appSample1.contains(Regex("Plugin:\\s+SamplePlugin")) )
      assertTrue ( appSample1.contains(Regex("Type:\\s+SampleTask")) )
      assertTrue ( appSample1.contains(Regex("Categories:\\s+Uncategorized")) )
      assertTrue ( appSample1.contains("Warnings") )
      assertTrue ( appSample1.contains("Reason task ran") )
      detailsPanel.clickGenerateReport()
      guiTest.waitForAllBackgroundTasksToBeCompleted()
      guiTest.ideFrame().waitForDialog("Plugin Issue Report").also { dialog ->
        findAndClickButton(dialog, "Copy")
        findAndClickButton(dialog, "Close")
        dialog.requireNotVisible()
      }
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    warningsPage.tree.clickPath("SamplePlugin/:app:sample2")
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    warningsPage.findDetailsPanel(":app:sample2").also { detailsPanel ->
      detailsPanel.requireVisible()
      val appSample2 = detailsPanel.readTaskDetails()
      assertTrue ( appSample2.contains("Duration") )
      assertTrue ( appSample2.contains(Regex("Sub-project\\:\\s+\\:app")) )
      assertTrue ( appSample2.contains(Regex("Plugin:\\s+SamplePlugin")) )
      assertTrue ( appSample2.contains(Regex("Type:\\s+SampleTask")) )
      assertTrue ( appSample2.contains(Regex("Categories:\\s+Uncategorized")) )
      assertTrue ( appSample2.contains("Warnings") )
      assertTrue ( appSample2.contains("Reason task ran") )
      detailsPanel.clickGenerateReport()
      guiTest.waitForAllBackgroundTasksToBeCompleted()
      guiTest.ideFrame().waitForDialog("Plugin Issue Report").also { dialog ->
        findAndClickButton(dialog, "Copy")
        findAndClickButton(dialog, "Close")
        dialog.requireNotVisible()
      }
    }
  }
}

