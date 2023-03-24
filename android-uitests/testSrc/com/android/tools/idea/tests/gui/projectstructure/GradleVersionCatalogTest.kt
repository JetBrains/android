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
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectDependenciesConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectProject
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectVariablesConfigurable
import com.google.common.truth.Truth
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
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
  private val appBuildFilePath: String = "app/build.gradle"

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

    val ideFrame: IdeFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish(projectName,
                                                                                       null,
                                                                                       "7.4.1",
                                                                                       "1.7.21",
                                                                                       null,
                                                                                       GuiTestRule.DEFAULT_IMPORT_AND_SYNC_WAIT)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    ideFrame.clearNotificationsPresentOnIdeFrame()

    val editor: EditorFixture = ideFrame.editor

    val versionsFileContentsBeforeUpgrade: String = editor.open(versionsFilePath).currentFileContents
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    assertTrue { versionsFileContentsBeforeUpgrade.contains(agpVersionBeforeUpgrade) }

    val upgradeAssistant: AGPUpgradeAssistantToolWindowFixture = ideFrame.getUgradeAssistantToolWindow(true)

    upgradeAssistant.selectAGPVersion(ANDROID_GRADLE_PLUGIN_VERSION)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    upgradeAssistant.clickRunSelectedStepsButton()
    ideFrame.waitForGradleSyncToFinish(null)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    assertTrue { upgradeAssistant.syncStatus }
    assertTrue { upgradeAssistant.isRefreshButtonEnabled }
    assertFalse { upgradeAssistant.isShowUsagesEnabled }
    assertFalse { upgradeAssistant.isRunSelectedStepsButtonEnabled }

    upgradeAssistant.hide()
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    val versionsFileContentsAfterUpgrade: String = editor.open(versionsFilePath).currentFileContents
    guiTest.waitForAllBackgroundTasksToBeCompleted()

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
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    assertFalse { versionsFileContentsAfterRevertingProject.contains(ANDROID_GRADLE_PLUGIN_VERSION) }
    assertTrue { versionsFileContentsAfterRevertingProject.contains(agpVersionBeforeUpgrade) }
  }

  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  @Throws(Exception::class)
  fun testProjectStructureDialog() {

    /**
     * Gradle version catalog - check PSD
     * <p>
     * This is run to qualify releases. Please involve the test team in substantial changes.
     * <p>
     * Bug: b/262756517
     * <p>
     *
     * <pre>
     *   Test Steps:
     *    1. Open Sample project and wait for the project sync to be completed.
     *    2. Open/Check "libs.versions.toml" file is present.
     *    3. Open Project Structure Dialog -> Project. (Verification 1)
     *   Test PSD variables.
     *    1. Open Project Structure Dialog -> Variables view. (Verification 2)
     *    2. Check the variables and versions. (Verification 3)
     *    3. Change any catalog version in variable.
     *    4. Click Ok. (Verification 4, 5)
     *   Test PSD Dependencies.
     *    1. Open Project Structure Dialog -> Dependencies view -> App. (Verification 6)
     *    2. Choose any of the dependencies and change the version from the dropdown.
     *    3. Click Ok (Verification 7)
     * </pre>
     * <p>
     */

    val ideFrame: IdeFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish(projectName,
                                                                                       null,
                                                                                       "7.4.1",
                                                                                       "1.7.21",
                                                                                       null,
                                                                                       GuiTestRule.DEFAULT_IMPORT_AND_SYNC_WAIT)

    ideFrame.clearNotificationsPresentOnIdeFrame()

    val editor: EditorFixture = ideFrame.editor

    val catalogFileContent: String = editor.open(versionsFilePath).currentFileContents
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    assertTrue { catalogFileContent.trim().isNotEmpty() }

    ideFrame.openPsd().run {
      selectProject().run {
        assertTrue(findPluginVersionEditor().getText() == "\$versions.agp")
      }
      clickCancel()
    }

    // test versions, update versions in PSD
    ideFrame.openPsd().run {
      selectVariablesConfigurable().run {
        selectCell("Default version catalog: libs (libs.versions.toml)")
        enter()
        Truth.assertThat(contents()).containsAllIn(listOf(
          "agp" to "7.4.1",
          "kotlin" to "1.7.21",
          "material" to "1.5.0",
          "appcompat" to "1.3.0",
          "constraintlayout" to "2.1.3",
          "junit" to "4.13.2",
          "testchangeversion" to "1.0.0"
        ))

        clickAdd()
        enterText("simpleVariableA")
        tab()
        enterText("1.0.0")
        selectCell("material")
        tab()
        enterText("1.7.0")

        selectCell("testchangeversion")
        enterText("testchangeversion2")

        selectCell("Default version catalog: libs (libs.versions.toml)")
        enter()
        Truth.assertThat(contents()).containsAllIn(listOf(
          "agp" to "7.4.1",
          "kotlin" to "1.7.21",
          "material" to "1.7.0",
          "appcompat" to "1.3.0",
          "constraintlayout" to "2.1.3",
          "junit" to "4.13.2",
          "testchangeversion2" to "1.0.0",
          "simpleVariableA" to "1.0.0"
        ))
      }
      clickOk()
    }

    // verify catalog variables in file
    val catalogFileContentAfterUpdates: String = editor.open(versionsFilePath).currentFileContents
    val versions = Pattern.compile("(?s)\\[versions\\](.*)\\[libraries\\]").matcher(catalogFileContentAfterUpdates)

    assertTrue(versions.find())
    val versionsString = versions.group(1)
    val versionMap = versionsString
      .split("\n")
      .map { it.trim().replace(" ", "").replace("\"", "") }
      .filter { it.isNotEmpty() }
      .associate { it.substringBefore("=") to it.substringAfter("=") }

      Truth.assertThat(versionMap.toList()).containsExactly(
      "agp" to "7.4.1",
      "kotlin" to "1.7.21",
      "material" to "1.7.0",
      "appcompat" to "1.3.0",
      "constraintlayout" to "2.1.3",
      "junit" to "4.13.2",
      "testchangeversion2" to "1.0.0",
      "simpleVariableA" to "1.0.0"
    )

    // check dependencies
    ideFrame.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("app")

        findDependenciesPanel().run {
          Truth.assertThat(
            findDependenciesTable().contents().map { it.toList() })
            .containsAllIn(listOf(
              listOf("androidx.appcompat:appcompat:1.3.0", "implementation"),
              listOf("com.google.android.material:material:1.7.0", "implementation")
            ))

          findDependenciesTable().cell("androidx.appcompat:appcompat:1.3.0").click()
          findVersionCombo().run {
            Truth.assertThat(selectedItem()).contains("versions.appcompat")
            selectItem(Pattern.compile(".*1\\.4\\.1.*"))
            Truth.assertThat(selectedItem()).contains("1.4.1")
          }
        }
      }
      clickOk()
    }

    val catalogAfterDependencyUpdates: String = editor.open(versionsFilePath).currentFileContents
    val cleanedCatalogString = catalogAfterDependencyUpdates.replace(" ","")
    assertTrue(cleanedCatalogString.contains("appcompat={group=\"androidx.appcompat\",name=\"appcompat\",version=\"1.4.1\"}"))

    val appBuildFileContent: String = editor.open(appBuildFilePath).currentFileContents

    assertTrue(appBuildFileContent.contains("implementation libs.appcompat"))

    // need run upgrade assistant before doing build
    val upgradeAssistant: AGPUpgradeAssistantToolWindowFixture = ideFrame.getUgradeAssistantToolWindow(true)

    upgradeAssistant.selectAGPVersion(ANDROID_GRADLE_PLUGIN_VERSION)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    upgradeAssistant.clickRunSelectedStepsButton()
    ideFrame.waitForGradleSyncToFinish(null)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    assertTrue { upgradeAssistant.syncStatus }
    assertTrue { upgradeAssistant.isRefreshButtonEnabled }
    assertFalse { upgradeAssistant.isShowUsagesEnabled }
    assertFalse { upgradeAssistant.isRunSelectedStepsButtonEnabled }

    upgradeAssistant.hide();
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    IdeFrameFixture.find(guiTest.robot()).requestFocusIfLost();

    val buildSuccess = guiTest.ideFrame().invokeProjectMake().isBuildSuccessful
    Truth.assertThat(buildSuccess).isTrue()
  }
}