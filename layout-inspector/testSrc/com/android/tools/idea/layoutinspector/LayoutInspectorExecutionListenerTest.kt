/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.testing.FakeAdbRule
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProjects
import com.android.tools.idea.layoutinspector.pipeline.adb.FakeShellCommandHandler
import com.android.tools.idea.layoutinspector.pipeline.adb.findDevice
import com.android.tools.idea.layoutinspector.pipeline.appinspection.DebugViewAttributes
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

// Comes from LightGradleSyncTestProjects.SIMPLE_APPLICATION
private const val PROCESS_NAME = "applicationId"

class LayoutInspectorExecutionListenerTest {
  private val commandHandler = FakeShellCommandHandler()
  private val projectRule =
    AndroidProjectRule.testProject(LightGradleSyncTestProjects.SIMPLE_APPLICATION)
  private val adbRule = FakeAdbRule().withDeviceCommandHandler(commandHandler)
  private val adbService = AdbServiceRule(projectRule::project, adbRule)
  private lateinit var env: ExecutionEnvironment

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(adbRule).around(adbService)!!

  @Before
  fun resetAttributes() {
    DebugViewAttributes.reset()
    env =
      ExecutionEnvironmentBuilder.create(
          DefaultRunExecutor.getRunExecutorInstance(),
          RunManager.getInstance(projectRule.project)
            .createConfiguration("app", AndroidRunConfigurationType.getInstance().factory)
        )
        .build()

    (env.runProfile as AndroidRunConfiguration).setModule(projectRule.module)
  }

  @Test
  fun testLaunchWithoutDebugAttributes() {
    val device = attachDevice(MODERN_DEVICE)
    (env.runProfile as AndroidRunConfiguration).INSPECTION_WITHOUT_ACTIVITY_RESTART = false

    // Start the process
    val handler = AndroidProcessHandler(PROCESS_NAME)
    AndroidSessionInfo.create(handler, listOf(device), PROCESS_NAME)
    LayoutInspectorExecutionListener().processStarted("123", env, handler)

    // Make sure the debug attributes are untouched.
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

    // Kill process p1 and check that the debug attributes are still untouched.
    handler.killProcess()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }

  @Test
  fun testLaunchWithDebugAttributes() =
    runWithFlagState(false) {
      val device = attachDevice(MODERN_DEVICE)
      (env.runProfile as AndroidRunConfiguration).INSPECTION_WITHOUT_ACTIVITY_RESTART = true

      // Start the process
      val handler = AndroidProcessHandler(PROCESS_NAME).apply { startNotify() }
      AndroidSessionInfo.create(handler, listOf(device), PROCESS_NAME)
      LayoutInspectorExecutionListener().processStarted("123", env, handler)

      // Make sure the debug attributes are set.
      assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(PROCESS_NAME)
      assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

      // Kill process p1 and check that the debug attributes are reset.
      handler.killProcess()
      assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
      assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
    }

  @Test
  fun testLaunchWithDebugAttributesAlreadySet() =
    runWithFlagState(false) {
      commandHandler.debugViewAttributesApplicationPackage = PROCESS_NAME

      val device = attachDevice(MODERN_DEVICE)
      env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(listOf(device)))
      (env.runProfile as AndroidRunConfiguration).INSPECTION_WITHOUT_ACTIVITY_RESTART = true

      // Start the process
      val handler = AndroidProcessHandler(PROCESS_NAME).apply { startNotify() }
      LayoutInspectorExecutionListener().processStarted("123", env, handler)

      // Make sure the debug attributes are untouched.
      assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(PROCESS_NAME)
      assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

      // Kill process p1 and check that the debug attributes are still untouched.
      handler.killProcess()
      assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(PROCESS_NAME)
      assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
    }

  @Test
  fun testLaunchWithDebugAttributesOnLegacyDevice() {
    val device = attachDevice(LEGACY_DEVICE)
    (env.runProfile as AndroidRunConfiguration).INSPECTION_WITHOUT_ACTIVITY_RESTART = true

    // Start the process
    val handler = AndroidProcessHandler(PROCESS_NAME).apply { startNotify() }
    AndroidSessionInfo.create(handler, listOf(device), PROCESS_NAME)
    LayoutInspectorExecutionListener().processStarted("123", env, handler)

    // Make sure the debug attributes are untouched.
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

    // Kill process p1 and check that the debug attributes are still untouched.
    handler.killProcess()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }

  @Test
  fun testPerDeviceViewDebugAttributesIsNotCleared() =
    runWithFlagState(true) {
      val device = attachDevice(MODERN_DEVICE)
      (env.runProfile as AndroidRunConfiguration).INSPECTION_WITHOUT_ACTIVITY_RESTART = true

      // Start the process
      val handler = AndroidProcessHandler(PROCESS_NAME).apply { startNotify() }
      AndroidSessionInfo.create(handler, listOf(device), PROCESS_NAME)
      LayoutInspectorExecutionListener().processStarted("123", env, handler)

      // Make sure the debug attributes are set.
      assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
      assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

      // Kill process p1 and check that the debug attributes are not reset.
      handler.killProcess()

      assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
      assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)
    }

  private fun runWithFlagState(desiredFlagState: Boolean, task: () -> Unit) {
    val flag = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED
    val flagPreviousState = flag.get()
    flag.override(desiredFlagState)

    task()

    // restore flag state
    flag.override(flagPreviousState)
  }

  private fun attachDevice(device: DeviceDescriptor): IDevice {
    adbRule.attachDevice(
      device.serial,
      device.manufacturer,
      device.model,
      device.version,
      device.apiLevel.toString()
    )
    val adb = AndroidDebugBridge.getBridge()!!
    return adb.findDevice(device)!!
  }
}
