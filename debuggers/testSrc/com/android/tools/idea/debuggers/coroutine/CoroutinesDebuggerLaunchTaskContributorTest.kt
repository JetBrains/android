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
import com.android.tools.idea.run.LaunchOptions
import com.intellij.testFramework.LightPlatformTestCase

class CoroutinesDebuggerLaunchTaskContributorTest : LightPlatformTestCase() {
  fun testContributorHasNoTask() {
    val launchOptions = LaunchOptions.builder().build()
    val contributor = CoroutineDebuggerLaunchTaskContributor()
    val task = contributor.getTask(module, "com.test.application", launchOptions)
    assertNull(task)
  }

  fun testNoAmOptionsIfFlagIsDisabled() {
    val launchOptions = LaunchOptions.builder().build()
    val contributor = CoroutineDebuggerLaunchTaskContributor()
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)

    runWithFlagState(false) {
      val amStartOptions = contributor.getAmStartOptions(module, "com.test.application", launchOptions, device)
      assertEmpty(amStartOptions)
    }
  }

  fun testNoAmOptionsIfNotDebuggable() {
    val launchOptions = LaunchOptions.builder().setDebug(false).build()
    val contributor = CoroutineDebuggerLaunchTaskContributor()
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)

    runWithFlagState(true) {
      val amStartOptions = contributor.getAmStartOptions(module, "com.test.application", launchOptions, device)
      assertEmpty(amStartOptions)
    }
  }

  fun testAmOptionsIsCorrect() {
    val launchOptions = LaunchOptions.builder().setDebug(true).build()
    val contributor = CoroutineDebuggerLaunchTaskContributor()
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)

    runWithFlagState(true) {
      val amStartOptions = contributor.getAmStartOptions(module, "com.test.application", launchOptions, device)
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