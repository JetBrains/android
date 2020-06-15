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
package com.android.tools.idea.tests.gui.npw.kts

import com.android.SdkConstants.FN_SETTINGS_GRADLE
import com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS
import com.android.tools.idea.testing.FileSubject.file
import com.google.common.truth.Truth.assertAbout

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(GuiTestRemoteRunner::class)
class AddNewModuleToKtsProjectTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  /**
   * Verifies addition of new module to Project that has a "settings.gradle.kts".
   *
   * Test Steps
   * 1. Import SimpleApplicationKtsSettings Project (A simple project where "settings.gradle" was refactored to "settings.gradle.kts"
   * 1. File -> new module > Select Phone & Tablet module > Choose no activity > Name module "application_module" > Finish
   * 3. Wait for build to complete
   * Verification
   * - There should be no "settings.gradle" at the root of the project
   * - "settings.gradle.kts" should have a new entry -> include("application_module")
   */
  @Test
  fun createNewAppModuleWithDefaults() {
    val ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleApplicationKtsSettings")
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()

    val settingsGroovyFilePath = File(ideFrame.projectPath, FN_SETTINGS_GRADLE)
    val settingsKtsFilePath = File(ideFrame.projectPath, FN_SETTINGS_GRADLE_KTS)

    assertAbout(file()).that(settingsGroovyFilePath).doesNotExist()
    assertAbout(file()).that(settingsKtsFilePath).isFile()

    ideFrame.invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextPhoneAndTabletModule()
      .enterModuleName("application_module")
      .wizard()
      .clickNext()
      .chooseActivity("No Activity")
      .clickFinish()
      .waitForGradleProjectSyncToFinish()

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()
    assertAbout(file()).that(settingsGroovyFilePath).doesNotExist()
    assertThat(guiTest.getProjectFileText("settings.gradle.kts")).contains("""include(":application_module")""")
  }
}
