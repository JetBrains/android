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
package com.android.tools.idea.tests.gui.projectstructure

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectBuildVariantsConfigurable
import com.android.tools.idea.tests.util.WizardUtils
import com.android.tools.idea.wizard.template.BuildConfigurationLanguageForNewProject
import com.android.tools.idea.wizard.template.Language
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class UpdateBuildTypesTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  /**
   * Verifies that an existing build type can be updated.
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 50840081-9584-4e66-9333-6a50902b5853
   * <pre>
   *   Test Steps:
   *   1. Open an existing project
   *   2. Open the project structure dialog
   *   3. Select the Build Variants view
   *   4. Click the Build Types tab
   *   5. Select Debug or Release and modify some settings.
   *   Verification:
   *   1. Build type selection in gradle build file is updated with the changes.
   * </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  @Throws(Exception::class)
  fun testForExistingProject() {
    val ide = guiTest.importSimpleApplication()

    editBuildTypeInPSD(ide)
    assertThat(ide.invokeProjectMake(Wait.seconds(180)).isBuildSuccessful).isTrue()

    val gradleFileContents = ide
      .editor
      .open("/app/build.gradle")
      .currentFileContents
    assertThat(gradleFileContents).containsMatch("release \\{\n[^\\}]* debuggable\\s+true\n")
    assertThat(gradleFileContents).containsMatch("release \\{\n[^\\}]* versionNameSuffix\\s+'suffix'\n")
  }

  /**
   * Verifies that an existing build type can be updated.
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 50840081-9584-4e66-9333-6a50902b5853
   * <pre>
   *   Test Steps:
   *   1. Create a new project with kotlin as language and KTS as build configuration.
   *   2. Open the project structure dialog
   *   3. Select the Build Variants view
   *   4. Click the Build Types tab
   *   5. Select Debug or Release and modify some settings.
   *   Verification:
   *   1. Build type selection in gradle build file is updated with the changes.
   * </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  @Throws(Exception::class)
  fun testForNewProject() {
    WizardUtils.createNewProject(guiTest, "Basic Views Activity", Language.Kotlin, BuildConfigurationLanguageForNewProject.KTS)

    val ide = guiTest.ideFrame()

    editBuildTypeInPSD(ide)
    assertThat(ide.invokeProjectMake(Wait.seconds(180)).isBuildSuccessful).isTrue()

    val gradleFileContents = ide
      .editor
      .open("/app/build.gradle.kts")
      .currentFileContents
    assertThat(gradleFileContents).containsMatch("release \\{\n[^\\}]* isDebuggable = true\n")
    assertThat(gradleFileContents).containsMatch("release \\{\n[^\\}]* versionNameSuffix = \"suffix\"\n")
  }

  private fun editBuildTypeInPSD(ideFrame: IdeFrameFixture) {
    ideFrame.openPsd().run {
      selectBuildVariantsConfigurable().run {
        waitTillProjectStructureIsLoaded()
        selectBuildTypesTab().run {
          selectItemByPath("release")
          debuggable().selectItem("true")
          versionNameSuffix().enterText("suffix")
        }
      }
      clickOk()
    }
  }
}