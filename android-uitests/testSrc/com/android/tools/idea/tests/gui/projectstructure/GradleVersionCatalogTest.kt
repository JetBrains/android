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
package com.android.tools.idea.tests.gui.projectstructure

import com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.gradle.AGPUpgradeAssistantToolWindowFixture
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(GuiTestRemoteRunner::class)
class GradleVersionCatalogTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule().withTimeout(10, TimeUnit.MINUTES)
  private val agpVersionBeforeUpgrade: String = "7.4.1"
  private val projectName: String = "VersionCatalogProject"
  private val versionsFilePath: String = "gradle/libs.versions.toml"

  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  @Throws(Exception::class)
  fun testAGPUpgradeUsingUpgradeAssistant() {

    /**
     * Gradle version catalog - Update/Revert agp version using AGP upgrade assistant
     * <p>
     * This is run to qualify releases. Please involve the test team in substantial changes.
     * <p>
     * Bug: b/262759141
     * <p>
     *
     * <pre>
     *   Test Prerequisite:
     *    1. Sample Project with "libs.versions.toml" already present.
     *   Test Steps:
     *    1. Open Sample project with older AGP version (AGP version <<< current AGP release) and wait for the project sync to be completed.
     *    2. Open/Check "libs.versions.toml" file is present.
     *    3. Open Upgrade Assistant (Verify 1)
     *    4. Select the latest AGP version from the Combo box and upgrade the version. (Verify 2, 3, 4).
     *    5. Click on the revert project files. (Verify 2, 4, 6)
     *   Verify:
     *    1. AGP upgrade assistant tool window should open.
     *    2. AGP upgrade should be successful.
     *    3. Check if the AGP version is updated in the "libs.versions.toml" file.
     *    4. Verify if the AGP upgrade assistant window is not stuck in loading state.
     *    5. Check if the AGP version is reverted to the previous AGP in "libs.versions.toml" file.
     * </pre>
     * <p>
     */

    val ideFrame: IdeFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish(projectName)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    ideFrame.clearNotificationsPresentOnIdeFrame()

    val editor: EditorFixture = ideFrame.editor

    val versionsFileContentsBeforeUpgrade: String = editor.open(versionsFilePath).currentFileContents
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    assertTrue { versionsFileContentsBeforeUpgrade.contains(agpVersionBeforeUpgrade) }

    val upgradeAssistant: AGPUpgradeAssistantToolWindowFixture = ideFrame.getUgradeAssistantToolWindow(true)

    upgradeAssistant.selectAGPVersion(ANDROID_GRADLE_PLUGIN_VERSION)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    upgradeAssistant.clickRunSelectedStepsButton()
    ideFrame.waitForGradleSyncToFinish(null)
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    assertTrue { upgradeAssistant.syncStatus }
    assertTrue { upgradeAssistant.isRefreshButtonEnabled }
    assertFalse { upgradeAssistant.isShowUsagesEnabled }
    assertFalse { upgradeAssistant.isRunSelectedStepsButtonEnabled }

    upgradeAssistant.hide()
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    val versionsFileContentsAfterUpgrade: String = editor.open(versionsFilePath).currentFileContents
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    assertFalse { versionsFileContentsAfterUpgrade.contains(agpVersionBeforeUpgrade) }
    assertTrue { versionsFileContentsAfterUpgrade.contains(ANDROID_GRADLE_PLUGIN_VERSION) }

    upgradeAssistant.activate()
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    upgradeAssistant.clickRevertProjectFiles()
    ideFrame.waitForGradleSyncToFinish(null)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    assertTrue { upgradeAssistant.isRefreshButtonEnabled }
    assertTrue { upgradeAssistant.isShowUsagesEnabled }
    assertTrue { upgradeAssistant.isRunSelectedStepsButtonEnabled}

    upgradeAssistant.hide();
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    val versionsFileContentsAfterRevertingProject: String = editor.open(versionsFilePath).currentFileContents
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    assertFalse { versionsFileContentsAfterRevertingProject.contains(ANDROID_GRADLE_PLUGIN_VERSION) }
    assertTrue { versionsFileContentsAfterRevertingProject.contains(agpVersionBeforeUpgrade) }
  }
}