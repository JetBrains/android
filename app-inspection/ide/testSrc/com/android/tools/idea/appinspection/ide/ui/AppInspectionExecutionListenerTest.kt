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
package com.android.tools.idea.appinspection.ide.ui

import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.DeviceImpl
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.FakeAndroidDevice
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

class AppInspectionExecutionListenerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testRecentProcess() {
    val project = projectRule.project
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val env =
      ExecutionEnvironmentBuilder.create(
          DefaultRunExecutor.getRunExecutorInstance(),
          AndroidRunConfiguration(
            projectRule.project,
            AndroidRunConfigurationType.getInstance().factory,
          ),
        )
        .build()
        .apply {
          executionId = Random.nextLong()
          putCopyableUserData(DeviceFutures.KEY, FakeAndroidDevice.forDevices(listOf(device)))
        }

    val handler1 = AndroidProcessHandler("com.example.p1")
    run { // Start process "p1"
      AndroidSessionInfo.create(handler1, listOf(device), "com.example.p1")

      project.messageBus
        .syncPublisher(ExecutionManager.EXECUTION_TOPIC)
        .processStarted(DefaultRunExecutor.EXECUTOR_ID, env, handler1)

      // Make sure that the process p1 is recorded as the recent process:
      assertThat(RecentProcess.get(project)!!.deviceSerialNumber).isSameAs(device.serialNumber)
      assertThat(RecentProcess.get(project)!!.packageName).isEqualTo("com.example.p1")
    }

    // Start process "p2"
    run {
      val handler2 = AndroidProcessHandler("com.example.p2")
      AndroidSessionInfo.create(handler2, listOf(device), "com.example.p2")

      project.messageBus
        .syncPublisher(ExecutionManager.EXECUTION_TOPIC)
        .processStarted(DefaultRunExecutor.EXECUTOR_ID, env, handler2)

      // Make sure that the process p2 is now recorded as the recent process:
      assertThat(RecentProcess.get(project)!!.deviceSerialNumber).isSameAs(device.serialNumber)
      assertThat(RecentProcess.get(project)!!.packageName).isEqualTo("com.example.p2")

      // Kill process p1 and check that the recent process is still p2:
      handler1.killProcess()
      assertThat(RecentProcess.get(project)!!.deviceSerialNumber).isSameAs(device.serialNumber)
      assertThat(RecentProcess.get(project)!!.packageName).isEqualTo("com.example.p2")
    }
  }
}
