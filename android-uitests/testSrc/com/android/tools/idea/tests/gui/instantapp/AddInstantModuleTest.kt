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
class AddInstantModuleTest {

  @Rule
  @JvmField
  val guiTest: GuiTestRule = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  /**
   * Verify a feature module can be created within a project.
   *
   * <p>TT ID: d239df75-a7fc-4327-a5af-d6b2f6caba11
   *
   * <pre>
   *   Test steps:
   *   1. Import a project with min API level at least 23
   *   2. Enable instant app by updating manifest file.
   *   3. Select Instant Dynamic Feature Module > Next > Finish, verify 2
   *   4. Build the app, verify 3
   *   Verify:
   *   1. Open app manifest and check for
   *      xmlns:dist="http://schemas.android.com/apk/distribution"
   *      dist:module dist:instant="true"
   *   2. Verify dynamic module is created and manifest file has below code:
   *      dist:instant="true"
   *      dist:fusing dist:include="false"
   *   3. The project build to success
   * </pre>
   */
  @Test
  fun testInstantDynamicModule(){
    val instantAppUtil = InstantAppSupportUtils()

    instantAppUtil.createEmptyActivityAndEnableInstantApp(guiTest)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    val ideFrame = guiTest.ideFrame()

    instantAppUtil.createInstantDynamicModule(ideFrame)

    val fileText = guiTest.getProjectFileText("dynamicfeature/src/main/AndroidManifest.xml")
    Truth.assertThat(fileText)
      .contains("dist:instant=\"true\"");
    Truth.assertThat(fileText)
      .contains("dist:fusing dist:include=\"false\"");
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    ideFrame.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(ideFrame.robot())
      .selectDeployAsInstantApp(true)
      .clickOk();
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    Truth.assertThat(instantAppUtil.buildProject(ideFrame))
      .isTrue()
  }
}