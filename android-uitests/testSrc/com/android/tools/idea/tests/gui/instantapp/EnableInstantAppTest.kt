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

import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.util.WizardUtils
import com.google.common.truth.Truth
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class EnableInstantAppTest {

  @Rule
  @JvmField
  val guiTest: GuiTestRule = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  /**
   * Verifies - Creating  a project with AIA enabled
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: 84f8150d-0319-4e7e-b510-8227890aca3f
   *
   * <pre>
   *   Test Steps
   *   1. Import sample "Google Analytics Example"
   *      "Google Analytics Example" sample has build errors
   *      Test creates a new projects and enables the instant apps support.
   *   2. Run instant app on emulator (Verify 1)
   *   Verification
   *   1)  App deploys on emulator without any errors
   * </pre>
   */
  @Test
  fun createProjectAndEnableAIA(){
    val instantAppSupportUtil = InstantAppSupportUtils()

    instantAppSupportUtil.createEmptyActivityAndEnableInstantApp(guiTest)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    Truth.assertThat(instantAppSupportUtil.buildProject(guiTest.ideFrame()))
      .isTrue()
  }
}