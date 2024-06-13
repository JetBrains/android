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

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture
import com.android.tools.idea.tests.util.WizardUtils
import com.android.tools.idea.wizard.template.BuildConfigurationLanguageForNewProject
import com.android.tools.idea.wizard.template.Language

import org.fest.swing.timing.Wait


class InstantAppSupportUtils {

  fun createEmptyActivityAndEnableInstantApp (guiTest: GuiTestRule) {
    WizardUtils.createDefaultEmptyActivity(guiTest)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    updateManifestFileToEnableInstantAppSupport(guiTest.ideFrame())
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    guiTest.ideFrame()
      .requestProjectSync()
    guiTest.waitForAllBackgroundTasksToBeCompleted()
  }

  private fun updateManifestFileToEnableInstantAppSupport(ideFrame: IdeFrameFixture) {
    ideFrame.editor
      .open("app/src/main/AndroidManifest.xml")
      .moveBetween("tools\"",">")
      .enterText("\n")
      .enterText("""xmlns:dist="http://schemas.android.com/apk/distribution"""")
      .moveBetween("","<application")
      .enterText("""<dist:module dist:instant="true" />""" + "\n")
  }

  fun createInstantDynamicModule(ideFrame: IdeFrameFixture) {
    ideFrame.invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToInstantDynamicFeature()
      .wizard()
      .clickFinishAndWaitForSyncToFinish()
  }

  fun buildProject(ideFrame: IdeFrameFixture): Boolean {
    val status = ideFrame.invokeProjectMake(Wait.seconds(450))
    return status.isBuildSuccessful
  }
}