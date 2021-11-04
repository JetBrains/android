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
package com.android.tools.idea.debuggers.coroutine

import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.DeviceImpl
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.LaunchOptions
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class CoroutinesDebuggerLaunchTaskContributorTest : LightPlatformTestCase() {
  
  private val configuration = mock( AndroidRunConfigurationBase
  ::class.java)
  fun testContributorHasNoTask() {
    val contributor = CoroutineDebuggerLaunchTaskContributor()
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val task = contributor.getTask( "com.test.application", configuration, device, DefaultRunExecutor.getRunExecutorInstance())
    assertNull(task)
  }

  fun testNoAmOptionsIfFlagIsDisabled() {
    val contributor = CoroutineDebuggerLaunchTaskContributor()
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)

    runWithFlagState(false) {
      val amStartOptions = contributor.getAmStartOptions("com.test.application", configuration, device,
                                                         DefaultRunExecutor.getRunExecutorInstance())
      assertEmpty(amStartOptions)
    }
  }

  fun testNoAmOptionsIfNotDebuggable() {
    val contributor = CoroutineDebuggerLaunchTaskContributor()
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)

    runWithFlagState(true) {
      val amStartOptions = contributor.getAmStartOptions("com.test.application", configuration, device,
                                                         DefaultRunExecutor.getRunExecutorInstance())
      assertEmpty(amStartOptions)
    }
  }

  fun testNoAmOptionsOnAPI28AndLower() {
    val contributor = CoroutineDebuggerLaunchTaskContributor()
    val device = Mockito.spy(DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE))

    `when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.P))

    runWithFlagState(true) {
      val amStartOptions = contributor.getAmStartOptions("com.test.application", configuration, device,
                                                         DefaultDebugExecutor.getDebugExecutorInstance())
      assertEquals("", amStartOptions)
    }

    `when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.O))

    runWithFlagState(true) {
      val amStartOptions = contributor.getAmStartOptions("com.test.application", configuration, device,
                                                         DefaultDebugExecutor.getDebugExecutorInstance())
      assertEquals("", amStartOptions)
    }

    `when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.N))

    runWithFlagState(true) {
      val amStartOptions = contributor.getAmStartOptions("com.test.application", configuration, device,
                                                         DefaultDebugExecutor.getDebugExecutorInstance())
      assertEquals("", amStartOptions)
    }

    `when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.M))

    runWithFlagState(true) {
      val amStartOptions = contributor.getAmStartOptions("com.test.application", configuration, device,
                                                         DefaultDebugExecutor.getDebugExecutorInstance())
      assertEquals("", amStartOptions)
    }
  }

  fun testAmOptionsIsCorrect() {
    val contributor = CoroutineDebuggerLaunchTaskContributor()
    val device = Mockito.spy(DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE))

    `when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.Q))


    runWithFlagState(true) {
      val amStartOptions = contributor.getAmStartOptions("com.test.application", configuration, device,
                                                         DefaultDebugExecutor.getDebugExecutorInstance())
      assertEquals("--attach-agent /data/data/com.test.application/code_cache/coroutine_debugger_agent.so", amStartOptions)
    }
  }

  private fun runWithFlagState(flagState: Boolean, task: () -> Unit) {
    val flagPreviousState = FlagController.isCoroutineDebuggerEnabled
    FlagController.enableCoroutineDebugger(true)

    task()

    FlagController.enableCoroutineDebugger(flagPreviousState)
  }
}