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
package com.android.tools.idea.tests.gui.instantapp

import com.android.SdkConstants
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditConfigurationsDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.EnableInstantAppSupportDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import com.sun.jna.Library
import com.sun.jna.Native
import org.fest.swing.edt.GuiTask
import org.fest.swing.util.PatternTextMatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@RunWith(GuiTestRemoteRunner::class)
class CreateAndRunInstantAppTest {
  private val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)
  private val avdTestRule = AvdTestRule.buildAvdTestRule {
    AvdSpec.Builder().setSystemImageGroup(AvdSpec.SystemImageGroups.X86)
      .setSystemImageSpec(ChooseSystemImageStepFixture.SystemImage(
        "Oreo",
        "26",
        "x86",
        "Android 8.0 (Google APIs)"
      ))
  }

  @Rule @JvmField val myTestRules = RuleChain.emptyRuleChain()
    .around(avdTestRule)
    .around(guiTest)

  private val projectApplicationId = "com.android.devtools.simple"
  private var oldAndroidSdkRootEnv: String? = null
  private var oldPathEnv: String? = null

  @Before
  fun setupSdk() {
    val newSdk = avdTestRule.generatedSdkLocation!!
    GuiTask.execute {
      ApplicationManager.getApplication().runWriteAction {
        IdeSdks.getInstance().setAndroidSdkPath(newSdk)
      }
    }

    // Set ANDROID_SDK_ROOT_ENV and PATH environment variables because the instant apps
    // SDK searches for ADB on its own. We don't want the SDK to use a different
    // ADB than the one we already have in the Android SDK:
    oldAndroidSdkRootEnv = System.getenv(SdkConstants.ANDROID_SDK_ROOT_ENV)
    oldPathEnv = System.getenv("PATH")

    Environment.libC.setenv(SdkConstants.ANDROID_SDK_ROOT_ENV, newSdk.absolutePath, 1)

    val currentPath = System.getenv("PATH") ?: ""
    val newPath = if (currentPath.isNotEmpty()) {
      "${newSdk.absolutePath}:${currentPath}"
    } else {
      newSdk.absolutePath
    }
    Environment.libC.setenv("PATH", newPath, 1)
  }

  /**
   * Verify created instant apps can be deployed to an emulator running API 26 or newer.
   *
   * <p>TT ID: 84f8150d-0319-4e7e-b510-8227890aca3f
   *
   * <pre>
   *   Test steps:
   *   1. Create an instant app project.
   *   2. Set up an emulator running API 26.
   *   3. Run the instantapp run configuration.
   *   Verify:
   *   1. Check if the run tool window appears.
   *   2. Check if the "Connected to process" message appears in the run tool window.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.SANITY_BAZEL)
  fun createAndRun() {
    val runConfigName = "My_Application.app"
    val avdName = avdTestRule.myAvd?.name ?: throw IllegalStateException("AVD does not have a name")

    val ideFrame = guiTest
      .welcomeFrame()
      .createNewProject()
      .clickNext()
      .configureNewAndroidProjectStep
      .enterPackageName(projectApplicationId)
      .selectMinimumSdkApi(23)
      .wizard()
      .clickFinishAndWaitForSyncToFinish()

    ideFrame.projectView
      .selectAndroidPane()
      .clickPath("app")
      .invokeMenuPath("Refactor", "Enable Instant Apps Support...")

    ideFrame.actAndWaitForGradleProjectSyncToFinish {
      EnableInstantAppSupportDialogFixture.find(ideFrame).clickOk()
      // Wait for Gradle sync to finish, then the Run -> Edit Configurations... will be enabled.
    }

    // The project is not deployed as an instant app by default anymore. Enable
    // deploying the project as an instant app:
    ideFrame.invokeMenuPath("Run", "Edit Configurations...")
    EditConfigurationsDialogFixture.find(ideFrame.robot())
      .selectDeployAsInstantApp(true)
      .clickOk()

    ideFrame.runApp(runConfigName, avdName)

    val runWindow = ideFrame.runToolWindow
    runWindow.activate()
    val runWindowContent = runWindow.findContent(runConfigName)

    val runOutputPattern = Pattern.compile(".*Instant app started.*", Pattern.DOTALL)
    runWindowContent.waitForOutput(PatternTextMatcher(runOutputPattern), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS)

    runWindowContent.waitForStopClick()
  }

  @After
  fun restoreEnvironment() {
    val oldAndroidSdk = oldAndroidSdkRootEnv
    if (oldAndroidSdk != null) {
      Environment.libC.setenv(SdkConstants.ANDROID_SDK_ROOT_ENV, oldAndroidSdk, 1)
    } else {
      Environment.libC.unsetenv(SdkConstants.ANDROID_SDK_ROOT_ENV)
    }

    val oldPath = oldPathEnv
    if (oldPath != null) {
      Environment.libC.setenv("PATH", oldPath, 1)
    } else {
      Environment.libC.unsetenv("PATH")
    }
  }

  private class Environment {
    companion object {
      val libC = Native.loadLibrary("c", LibC::class.java)
    }

    interface LibC: Library {
      fun setenv(name: String, value: String, overwrite: Int): Int
      fun unsetenv(name: String): Int
    }
  }
}
