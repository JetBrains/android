/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.instantapp

import com.android.tools.idea.npw.dynamicapp.DownloadInstallKind
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture
import com.android.tools.idea.tests.util.WizardUtils
import com.android.tools.idea.wizard.template.BuildConfigurationLanguageForNewProject
import com.android.tools.idea.wizard.template.Language
import com.google.common.truth.Truth
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit


@RunWith(GuiTestRemoteRunner::class)
class DynamicModuleInstallTimeTest {
  @Rule
  @JvmField
  val guiTest : GuiTestRule = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  /**
   * Verifies adding Dynamic Feature module - On Demand Inclusion.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: 9eedf9f5-3a10-4c00-86ca-6d70f1a6924d
   *
   * <pre>
   *   Test Steps
   *   1. Create a Project with Basic Activity with Min API 15
   *   2. After successful gradle sync > Right click on app module > New Module
   *   3. Select Dynamic Feature Module > Next > Next
   *   4. Select "Install-time inclusion" > "Include module at install-time"
   *   5. Check "Fusing" checkbox > Finish (Verify 1) (Verify 2)
   *   6. Run application on emulator/device
   *   Verification
   *   1) Open dynamic feature manifest file verify below code is included
   *   <dist:module
   *        dist:instant="false"
   *        dist:title="@string/title_dynamicfeature">
   *        <dist:delivery>
   *          <dist:install-time />
   *        </dist:delivery>
   *        <dist:fusing dist:include="true" />
   *   </dist:module>
   *   2) App deploys on to device/emulator without any errors
   * </pre>
   */
  @Test
  fun dynamicModuleInstallTimeTest(){
    WizardUtils.createNewProject(guiTest,
                                 "Empty Views Activity",
                                 Language.Kotlin,
                                 BuildConfigurationLanguageForNewProject.KTS)
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    val ideFrame = guiTest.ideFrame()
    ideFrame.invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToDynamicFeature()
      .enterFeatureModuleName("dynamic_install_module")
      .clickNextToConfigureDynamicDelivery()
      .setDownloadInstallKind(DownloadInstallKind.INCLUDE_AT_INSTALL_TIME)
      .wizard()
      .clickFinishAndWaitForSyncToFinish()

    guiTest.getProjectFileText("dynamic_install_module/src/main/AndroidManifest.xml").run {
      Truth.assertThat(this).contains("""<dist:delivery>""")
      Truth.assertThat(this).contains("""<dist:install-time />""")
      Truth.assertThat(this).doesNotContain("""<dist:on-demand />""")
      Truth.assertThat(this).contains("""</dist:delivery>""")
      Truth.assertThat(this).contains("""<dist:fusing dist:include="true" />""")
    }
    // Adding an extra step to sync the project to reduce the flakiness during
    // make project. Without extra sync, make project steps fails intermittently
    // with project indexing in progress.
    ideFrame.requestProjectSync()
    ideFrame.waitForGradleSyncToFinish(Wait.seconds(300))

    Truth.assertThat(guiTest.ideFrame().invokeProjectMake(Wait.seconds(500))
                       .isBuildSuccessful).isTrue()
  }
}