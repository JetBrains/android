/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.testsuite

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.execution.common.DeployableToDevice
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testartifacts.testsuite.GradleRunConfigurationExtension.BooleanOptions.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.overrideForTest
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever

class GradleAndroidTestsTaskManagerTest {

  @get:Rule
  val rule = AndroidProjectRule.inMemory()

  private val mockId: ExternalSystemTaskId = mock()

  @Before
  fun setUp() {
    whenever(mockId.findProject()).thenReturn(rule.project)
  }

  @Test
  fun configureTasks_addsInitScript_whenShowTestResultInAndroidTestSuiteViewIsTrue() {
    val settings = GradleExecutionSettings()
    settings.putUserData(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey, true)

    val taskManager = GradleAndroidTestsTaskManager()
    taskManager.configureTasks("/path/to/project", mockId, settings, null)

    assertThat(settings.arguments.toString()).containsMatch("--init-script.*addTestListenerForAndroidTestSuiteView")
  }

  @Test
  fun configureTasks_doesNotAddInitScript_whenShowTestResultInAndroidTestSuiteViewIsFalse() {
    val settings = GradleExecutionSettings()
    settings.putUserData(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey, false)

    val taskManager = GradleAndroidTestsTaskManager()
    taskManager.configureTasks("/path/to/project", mockId, settings, null)

    assertThat(settings.arguments.toString()).doesNotContain("--init-script.*addTestListenerForAndroidTestSuiteView")
  }

  @Test
  fun configureTasks_doesNotAddInitScript_whenAdditionalOptionsAreDisabled() {
    StudioFlags.ENABLE_ADDITIONAL_TESTING_GRADLE_OPTIONS.overrideForTest(false, rule.testRootDisposable)
    val settings = GradleExecutionSettings()
    settings.putUserData(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey, true)

    val taskManager = GradleAndroidTestsTaskManager()
    taskManager.configureTasks("/path/to/project", mockId, settings, null)

    assertThat(settings.arguments.toString()).doesNotContain("--init-script.*addTestListenerForAndroidTestSuiteView")
  }

  @Test
  fun configureTasks_launchesDevicesAndConfiguresEnvVar() {
    // Feature only support for Android Studio
    if (!IdeInfo.getInstance().isAndroidStudio) {
      return
    }

    val taskManager = GradleAndroidTestsTaskManager(deviceLauncher = {
      listOf("device1", "device2")
    })
    val settings = GradleExecutionSettings()
    settings.putUserData(DeployableToDevice.KEY, true)

    taskManager.configureTasks("", mockId, settings, null)

    assertThat(settings.env["ANDROID_SERIAL"]).isEqualTo("device1,device2")
  }

  @Test
  fun configureTasks_throwsProcessCanceledException_whenNoDevicesAreLaunched() {
    // Feature only support for Android Studio
    if (!IdeInfo.getInstance().isAndroidStudio) {
      return
    }

    val taskManager = GradleAndroidTestsTaskManager(deviceLauncher = {
      emptyList()
    })
    val settings = GradleExecutionSettings()
    settings.putUserData(DeployableToDevice.KEY, true)

    assertThrows(ProcessCanceledException::class.java) {
      taskManager.configureTasks("", mockId, settings, null)
    }

    assertThat(settings.env["ANDROID_SERIAL"]).isNull()
  }

  @Test
  fun configureTasks_doesNotSetAndroidSerial_whenDeployableToDeviceIsFalse() {
    // Feature only support for Android Studio
    if (!IdeInfo.getInstance().isAndroidStudio) {
      return
    }

    val taskManager = GradleAndroidTestsTaskManager(deviceLauncher = {
      listOf("device1", "device2")
    })
    val settings = GradleExecutionSettings()
    settings.putUserData(DeployableToDevice.KEY, false)

    taskManager.configureTasks("", mockId, settings, null)

    assertThat(settings.env["ANDROID_SERIAL"]).isNull()
  }

  @Test
  fun configureTasks_doesNotSetAndroidSerial_whenFlagsDisabled() {
    // Feature only support for Android Studio
    if (!IdeInfo.getInstance().isAndroidStudio) {
      return
    }

    StudioFlags.ENABLE_ADDITIONAL_TESTING_GRADLE_OPTIONS.overrideForTest(false, rule.testRootDisposable)
    StudioFlags.AGP_TEST_SUITES_ENABLED.overrideForTest(false, rule.testRootDisposable)

    val taskManager = GradleAndroidTestsTaskManager(deviceLauncher = {
      listOf("device1", "device2")
    })
    val settings = GradleExecutionSettings()
    settings.putUserData(DeployableToDevice.KEY, true)

    taskManager.configureTasks("", mockId, settings, null)

    assertThat(settings.env["ANDROID_SERIAL"]).isNull()
  }

  @Test
  fun configureTasks_setsAndroidSerial_whenAgpTestSuitesEnabledIsTrue() {
    // Feature only support for Android Studio
    if (!IdeInfo.getInstance().isAndroidStudio) {
      return
    }

    StudioFlags.ENABLE_ADDITIONAL_TESTING_GRADLE_OPTIONS.overrideForTest(false, rule.testRootDisposable)
    StudioFlags.AGP_TEST_SUITES_ENABLED.overrideForTest(true, rule.testRootDisposable)

    val taskManager = GradleAndroidTestsTaskManager(deviceLauncher = {
      listOf("device1", "device2")
    })
    val settings = GradleExecutionSettings()
    settings.putUserData(DeployableToDevice.KEY, true)

    taskManager.configureTasks("", mockId, settings, null)

    assertThat(settings.env["ANDROID_SERIAL"]).isEqualTo("device1,device2")
  }
}
