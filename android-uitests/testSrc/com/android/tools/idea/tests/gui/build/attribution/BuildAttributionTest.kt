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
package com.android.tools.idea.tests.gui.build.attribution

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButton
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.BuildAttributionViewFixture
import com.android.tools.idea.tests.gui.framework.fixture.BuildToolWindowFixture
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.edt.GuiQuery
import org.fest.swing.timing.Wait
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(GuiTestRemoteRunner::class)
@RunIn(TestGroup.UNRELIABLE)
class BuildAttributionTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Before
  fun setUp() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.clearOverride()
  }

  /**
   * Test user path through Build Attribution feature.
   * First add two fake tasks to the build to have warnings in the report, sync and build.
   *
   * Open build attribution tab, check pages are listed as expected, page changing works both using tree and page links.
   * Check detailed report dialog can be opened and closed.
   * Check tab can be closed and re-opened from a build output link.
   */
  @Test
  fun testBuildAttributionFlow() {
    val ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("BuildAttributionApp")

    val buildToolWindow: BuildToolWindowFixture = ideFrame.buildToolWindow
    buildToolWindow.activate()

    val result = ideFrame.invokeProjectMake()
    assertTrue(result.isBuildSuccessful)

    buildToolWindow.waitTabExist("Build Analyzer")

    buildToolWindow.openBuildAttributionUsingTabHeaderClick().run {
      checkInitState()
      checkWarningsNode(listOf("Always-run Tasks"), 2)
      selectAndCheckBuildSummaryNode()
      checkTasks()
      checkIssues()
      checkPlugins()
    }
    buildToolWindow.closeBuildAttributionTab()


    buildToolWindow.openBuildAttributionUsingBuildOutputLink().checkInitState()
    buildToolWindow.closeBuildAttributionTab()

    ideFrame.closeBuildPanel()
  }

  private fun BuildAttributionViewFixture.checkTasks() {
    selectPageByPath(
      " Tasks determining this build's duration 2 warnings",
      "Tasks determining this build's duration \\(\\d+\\.\\d\\d\\d s\\)")
    expandSelectedNodeWithKeyStroke()

    selectPageByPath(" Tasks determining this build's duration 2 warnings/ :app:dummy1", ":app:dummy1")
    findHyperlabelByTextContainsAndClick("Always-run Tasks")
    requireOpenedPagePathAndHeader(" Warnings (2)/ Always-run Tasks 2 warnings/ :app:dummy1", ":app:dummy1")

    selectPageByPath(" Tasks determining this build's duration 2 warnings/ :app:dummy2", ":app:dummy2")
    findHyperlabelByTextContainsAndClick("Always-run Tasks")
    requireOpenedPagePathAndHeader(" Warnings (2)/ Always-run Tasks 2 warnings/ :app:dummy2", ":app:dummy2")
  }

  private fun BuildAttributionViewFixture.checkIssues() {
    selectPageByPath(" Warnings (2)/ Always-run Tasks 2 warnings", "Always-run Tasks")
    findHyperlabelByTextContainsAndClick(":app:dummy1")
    requireOpenedPagePathAndHeader(" Warnings (2)/ Always-run Tasks 2 warnings/ :app:dummy1", ":app:dummy1")

    findHyperlabelByTextContainsAndClick("Generate report.")

    val dialog = guiTest.ideFrame().waitForDialog("Plugin Issue Report")
    findAndClickButton(dialog, "Copy")
    findAndClickButton(dialog, "Close")
    Wait.seconds(5).expecting("dialog to disappear").until { GuiQuery.getNonNull { !dialog.target().isShowing() } }
  }

  private fun BuildAttributionViewFixture.checkPlugins() {
    selectPageByPath(
      " Plugins with tasks determining this build's duration 2 warnings",
      "Plugins with tasks determining this build's duration \\(\\d+\\.\\d\\d\\d s\\)"
    )
    tree.expandPath(" Plugins with tasks determining this build's duration 2 warnings")
    tree.requireSelectedNodeContainInOrder(listOf("com.android.application", "DummyPlugin 2 warnings"))

    // Move to Dummy plugin node using keyboard
    selectedNextNodeWithKeyStroke()
    requireOpenedPagePathAndHeader(
      " Plugins with tasks determining this build's duration 2 warnings/ com.android.application ",
      "com.android.application"
    )
    selectedNextNodeWithKeyStroke()
    requireOpenedPagePathAndHeader(
      " Plugins with tasks determining this build's duration 2 warnings/ DummyPlugin 2 warnings",
      "DummyPlugin"
    )

    findHyperlabelByTextContainsAndClick("Always-run Tasks (2)")
    requireOpenedPagePathAndHeader(
      " Plugins with tasks determining this build's duration 2 warnings/ DummyPlugin 2 warnings/ Warnings (2)/ Always-run Tasks 2 warnings",
      "DummyPlugin"
    )

    selectPageByPath(
      " Plugins with tasks determining this build's duration 2 warnings/ DummyPlugin 2 warnings",
      "DummyPlugin"
    )
    tree.requireSelectedNodeContainInOrder(listOf("Tasks determining this build's duration 2 warnings", "Warnings"))

    selectedNextNodeWithKeyStroke()
    expandSelectedNodeWithKeyStroke()
    selectedNextNodeWithKeyStroke()
    requireOpenedPagePathAndHeader(
      " Plugins with tasks determining this build's duration 2 warnings/ DummyPlugin 2 warnings/ Tasks determining this build's duration 2 warnings/ :app:dummy1",
      ":app:dummy1"
    )

    findHyperlabelByTextContainsAndClick("Always-run Tasks")
    requireOpenedPagePathAndHeader(
      " Plugins with tasks determining this build's duration 2 warnings/ DummyPlugin 2 warnings/ Warnings (2)/ Always-run Tasks 2 warnings/ :app:dummy1",
      ":app:dummy1")
  }
}