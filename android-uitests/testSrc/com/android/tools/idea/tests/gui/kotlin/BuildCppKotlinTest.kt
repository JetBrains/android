/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.kotlin

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class BuildCppKotlinTest {

  @Rule @JvmField val guiTest = GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  /**
   * Verifies new project with Kotlin and C++ support can be built
   *
   * This is run to qualify releases. Please involve the test team in substantial changes.
   *
   * This test is a part of the test case with identifier
   * 4d4c36b0-23a7-4f16-9293-061e2fb1310f. This test case was too large, so it has been
   * split into smaller automated tests. Please search for other usages of
   * 4d4c36b0-23a7-4f16-9293-061e2fb1310f to find other test cases that are a part of
   * this test case.
   *
   * The other known test cases of this test case are:
   * * [CreateCppKotlinProjectTest.createCppKotlinProject]
   *
   * TT ID: 4d4c36b0-23a7-4f16-9293-061e2fb1310f
   *
   * Test Steps:
   *   1. Create a basic Kotlin project following the default steps.
   *   2. Select the "include Kotlin" and "C++" support checkbox [verify 1 & 2].
   *   3. Build and run project on an emulator, and verify step 3.
   *
   *   Verification steps:
   *   1. Check if build is successful.
   *   2. C++ code is created, MainActivity has .kt extension.
   *
   * This particular automated test just imports the pre-created project "CppKotlin"
   * created from following the flow for creating new C++ and Kotlin projects.
   * The test then builds the project to see if the project can be built
   * successfully.
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  fun buildCppKotlinProj() {
    val ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("CppKotlin", Wait.seconds(TimeUnit.MINUTES.toSeconds(7)))
    ideFrame.invokeAndWaitForBuildAction(Wait.seconds(300), "Build", "Rebuild Project")
  }
}
