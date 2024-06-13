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
import com.android.tools.idea.tests.gui.framework.fixture.EditConfigurationsDialogFixture
import com.google.common.truth.Truth
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class BuildInstantAppTest {

  @Rule
  @JvmField
  val guiTest: GuiTestRule = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  /**
   * Verify imported instant apps can be built without error.
   *
   * <p>TT ID: 56be2a70-25a2-4b1f-9887-c19073874aa2
   *
   * <pre>
   *   Test steps:
   *   1. Import a sample app project
   *   2. Refactor > Enable Instant app support > Select app module > OK
   *   3. After gradle sync, Click the Run configuration drop-down,
   *      choose "Edit Configuration" and Enable checkbox for "Deploy as instant app"
   *   Verify:
   *   1. Build to success
   * </pre>
   */
  @Test
  fun testBuildInstantApp(){
    val instantAppUtil = InstantAppSupportUtils()

    instantAppUtil.createEmptyActivityAndEnableInstantApp(guiTest)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    val ideFrame = guiTest.ideFrame()
    ideFrame.waitUntilProgressBarNotDisplayed()

    ideFrame.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(ideFrame.robot())
      .selectDeployAsInstantApp(true)
      .clickOk();
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    Truth.assertThat(instantAppUtil.buildProject(ideFrame))
      .isTrue()
  }
}