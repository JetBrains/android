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
package com.android.tools.idea.run.configuration.execution

import com.android.ddmlib.AndroidDebugBridge
import com.android.fakeadbserver.services.ServiceOutput
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail

internal class AndroidActivityConfigurationExecutorTest : AndroidConfigurationExecutorBaseTest() {

  private val forceStop = "force-stop com.example.app"

  private fun getExecutionEnvironment(executorInstance: Executor): ExecutionEnvironment {
    val configSettings = RunManager.getInstance(project).createConfiguration("run App", AndroidRunConfigurationType().factory)
    val configuration = configSettings.configuration as AndroidRunConfiguration
    configuration.setModule(myModule)
    configuration.setLaunchActivity(componentName)
    return ExecutionEnvironment(executorInstance, AndroidConfigurationProgramRunner(), configSettings, project)
  }

  @Test
  fun testRun() {
    // Use DefaultRunExecutor, equivalent of pressing run button.
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    (env.runProfile as AndroidRunConfiguration).ACTIVITY_EXTRA_FLAGS = "--user 123"

    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val receivedAmCommands = ArrayList<String>()

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val executor = Mockito.spy(AndroidActivityConfigurationExecutor(env, TestDeployTarget(device)))

    val app = createApp(device, appId, servicesName = listOf(), activitiesName = listOf(componentName))
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).whenever(executor).getApplicationInstaller(any())

    executor.doOnDevices(listOf(device))

    // Verify commands sent to device.

    // force stop.
    assertThat(receivedAmCommands[0]).isEqualTo(forceStop)
    // Start activity.
    assertThat(receivedAmCommands[1]).isEqualTo(
      "start -n com.example.app/com.example.app.Component -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --user 123")
  }

  @Test
  fun testDebug() {
    // Use DefaultRunExecutor, equivalent of pressing debug button.
    val env = getExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance())

    val startCommand = "start -n com.example.app/com.example.app.Component -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -D"

    val processTerminatedLatch = CountDownLatch(2)
    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val receivedAmCommands = ArrayList<String>()

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        startCommand -> {
          deviceState.startClient(1234, 1235, appId, true)
        }
        forceStop -> {
          deviceState.stopClient(1234)
          processTerminatedLatch.countDown()
        }
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    // Executor we test.
    val executor = Mockito.spy(AndroidActivityConfigurationExecutor(env, TestDeployTarget(device)))

    val app = createApp(device, appId, servicesName = listOf(), activitiesName = listOf(componentName))
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).whenever(executor).getApplicationInstaller(any())

    val runContentDescriptor = executor.doOnDevices(listOf(device)).blockingGet(10, TimeUnit.SECONDS)
    assertThat(runContentDescriptor!!.processHandler).isNotNull()

    // Emulate stopping debug session.
    val processHandler = runContentDescriptor.processHandler!!
    processHandler.destroyProcess()
    if (!processTerminatedLatch.await(10, TimeUnit.SECONDS)) {
      fail("process is not terminated")
    }

    // Verify commands sent to device.

    // force stop.
    assertThat(receivedAmCommands[0]).isEqualTo(forceStop)
    // Start Activity with -D flag.
    assertThat(receivedAmCommands[1]).isEqualTo(startCommand)
    // Stop debug process
    assertThat(receivedAmCommands[2]).isEqualTo(forceStop)
  }
}