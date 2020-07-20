/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.npw

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class AddNewActivityToExistingProjectTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  /**
   * Verifies addition of new module with Basic Activity to a Project builds successfully.
   *
   * Test Steps
   * 1. Create a project with Empty Activity
   * 1. File -> New > Activity > Basic Activity > Finish
   * 3. Wait for build to complete
   * Verification
   * - Project builds successfully
   */
  @Test
  fun createNewBasicActivityToExistingEmptyActivity() {
    // Create a new project with Empty Activity.
    val ideFrame = guiTest.welcomeFrame().createNewProject()
      .chooseAndroidProjectStep
      .chooseActivity("Empty Activity")
      .wizard()
      .clickNext()
      .configureNewAndroidProjectStep
      .enterName("EmptyApp")
      .enterPackageName("dev.tools")
      .wizard()
      .clickFinishAndWaitForSyncToFinish()

    ideFrame.invokeMenuPath("File", "New", "Activity", "Basic Activity")
    NewActivityWizardFixture.find(ideFrame)
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()
  }
}
