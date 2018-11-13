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

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.timing.Wait
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(GuiTestRemoteRunner::class)
class BuildCppKotlinTest {

  @Rule @JvmField val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

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
    val ideFrame = guiTest.importProject("CppKotlin")

    // TODO remove the following hack: b/110174414
    val androidSdk = IdeSdks.getInstance().androidSdkPath
    val ninja = File(androidSdk, "cmake/3.10.4819442/bin/ninja")

    val buildGradleFailure = AtomicReference<IOException>()
    ApplicationManager.getApplication().invokeAndWait {
      WriteCommandAction.runWriteCommandAction(ideFrame.project) {
        val pbm = ProjectBuildModel.get(ideFrame.project)
        val buildModel = pbm.getModuleBuildModel(ideFrame.getModule("app"))
        val cmakeModel = buildModel!!
          .android()
          .defaultConfig()
          .externalNativeBuild()
          .cmake()

        val cmakeArgsModel = cmakeModel.arguments()
        try {
          cmakeArgsModel.setValue("-DCMAKE_MAKE_PROGRAM=" + ninja.canonicalPath)
          buildModel.applyChanges()
        }
        catch (failureToWrite: IOException) {
          buildGradleFailure.set(failureToWrite)
        }
      }
    }
    val errorsWhileModifyingBuild = buildGradleFailure.get()
    if (errorsWhileModifyingBuild != null) {
      throw errorsWhileModifyingBuild
    }
    // TODO end hack for b/110174414

    try {
      ideFrame.invokeMenuPath("Build", "Rebuild Project").waitForBuildToFinish(BuildMode.REBUILD, Wait.seconds(60))
    }
    catch (timedout: WaitTimedOutError) {
      Assert.fail("Could not build a project!")
    }
  }
}
