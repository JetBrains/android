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
import com.android.tools.deployer.DeployerException
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.run.DefaultStudioProgramRunner
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfigurationType
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.registerServiceInstance
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.test.fail

class AndroidWatchFaceConfigurationExecutorTest : AndroidConfigurationExecutorBaseTest() {
  // Expected commands am commands
  private val forceStop = "force-stop com.example.app"
  private val checkVersion = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version"
  private val setWatchFace = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-watchface --ecn component com.example.app/com.example.app.Component"
  private val showWatchFace = "broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-watchface"
  private val unsetWatchFace = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation unset-watchface"
  private val setDebugAppAm = "set-debug-app -w 'com.example.app'"
  private val clearDebugAppAm = "clear-debug-app"
  private val clearDebugAppBroadcast = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'clear-debug-app'"

  private fun getExecutionEnvironment(executorInstance: Executor): ExecutionEnvironment {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run WatchFace", AndroidWatchFaceConfigurationType().configurationFactories.single())
    // Use debug executor
    return ExecutionEnvironment(executorInstance, DefaultStudioProgramRunner(), configSettings, project)
  }

  @Test
  fun testRun() {
    // Use DefaultRunExecutor, equivalent of pressing run button.
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val receivedAmCommands = ArrayList<String>()

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        checkVersion -> serviceOutput.writeStdout(
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"3\"")

        setWatchFace -> serviceOutput.writeStdout(
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"Favorite Id=[2] Runtime=[1]\"")
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val deviceFutures = DeviceFutures.forDevices(listOf(device))
    val settings = object : AppRunSettings {
      override val deployOptions = DeployOptions(emptyList(), "", true, true)
      override val componentLaunchOptions = WatchFaceLaunchOptions().apply {
        componentName = this@AndroidWatchFaceConfigurationExecutorTest.componentName
      }
      override val module = myModule
    }

    val executor = Mockito.spy(
      AndroidWatchFaceConfigurationExecutor(env, deviceFutures, settings, TestApplicationIdProvider(appId), TestApksProvider(appId)))

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).whenever(executor).getApplicationDeployer(any())

    getRunContentDescriptorForTests { executor.run(EmptyProgressIndicator()) }

    // Verify commands sent to device.

    // force-stop running app
    assertThat(receivedAmCommands[0]).isEqualTo(forceStop)
    // check WatchFace API version.
    assertThat(receivedAmCommands[1]).isEqualTo(checkVersion)
    // Set WatchFace.
    assertThat(receivedAmCommands[2]).isEqualTo(setWatchFace)
    // Showing WatchFace.
    assertThat(receivedAmCommands[3]).isEqualTo(showWatchFace)
  }

  @Test
  fun testDebug() {
    // Use DefaultRunExecutor, equivalent of pressing debug button.
    val env = getExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance())

    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val receivedAmCommands = ArrayList<String>()
    val processTerminatedLatch = CountDownLatch(1)

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        checkVersion -> serviceOutput.writeStdout(
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"3\"")
        // clearDebugAppBroadcast -> serviceOutput.writeStdout("")
        clearDebugAppAm -> {
          serviceOutput.writeStdout("")
          processTerminatedLatch.countDown()
        }
        setWatchFace -> {
          deviceState.startClient(1234, 1235, appId, true)
          serviceOutput.writeStdout(
            "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
            "Broadcast completed: result=1, data=\"Favorite Id=[2] Runtime=[1]\"")
        }

        unsetWatchFace -> {
          deviceState.stopClient(1234)
          serviceOutput.writeStdout("Broadcast completed: result=1")
        }
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val settings = object : AppRunSettings {
      override val deployOptions = DeployOptions(emptyList(), "", true, true)
      override val componentLaunchOptions = WatchFaceLaunchOptions().apply {
        componentName = this@AndroidWatchFaceConfigurationExecutorTest.componentName
      }
      override val module = myModule
    }

    // Executor we test.
    val executor = Mockito.spy(
      AndroidWatchFaceConfigurationExecutor(env, DeviceFutures.forDevices(listOf(device)), settings, TestApplicationIdProvider(appId),
                                            TestApksProvider(appId)))

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).whenever(executor).getApplicationDeployer(any())

    val runContentDescriptor = getRunContentDescriptorForTests { executor.debug(EmptyProgressIndicator()) }

    // Stop configuration.
    runContentDescriptor.processHandler!!.destroyProcess()
    processTerminatedLatch.await(1, TimeUnit.SECONDS)

    // Verify commands sent to device.

    // force-stop running app
    assertThat(receivedAmCommands[0]).isEqualTo(forceStop)
    // check WatchFace API version.
    assertThat(receivedAmCommands[1]).isEqualTo(checkVersion)
    // Set debug app.
    assertThat(receivedAmCommands[2]).isEqualTo(setDebugAppAm)
    // Set WatchFace.
    assertThat(receivedAmCommands[3]).isEqualTo(setWatchFace)
    // Showing WatchFace.
    assertThat(receivedAmCommands[4]).isEqualTo(showWatchFace)
    // Unset watch face
    assertThat(receivedAmCommands[5]).isEqualTo(unsetWatchFace)
    // Clear debug app
    assertThat(receivedAmCommands[6]).isEqualTo(clearDebugAppBroadcast)
    assertThat(receivedAmCommands[7]).isEqualTo(clearDebugAppAm)
  }

  @Test
  fun testComponentActivationException() {
    // Use DefaultRunExecutor, equivalent of pressing run button.
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    val failedResponse = "Component not found."

    val deviceState = fakeAdbRule.connectAndWaitForDevice()

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")

      when (wholeCommand) {
        checkVersion -> serviceOutput.writeStdout("Broadcast completed: result=1, data=\"3\"")
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val settings = object : AppRunSettings {
      override val deployOptions = DeployOptions(emptyList(), "", true, true)
      override val componentLaunchOptions = WatchFaceLaunchOptions().apply {
        componentName = this@AndroidWatchFaceConfigurationExecutorTest.componentName
      }
      override val module = myModule
    }

    // Executor we test.
    val executor = Mockito.spy(
      AndroidWatchFaceConfigurationExecutor(env, DeviceFutures.forDevices(listOf(device)), settings, TestApplicationIdProvider(appId),
                                            TestApksProvider(appId)))

    val app = Mockito.mock(App::class.java)
    Mockito.doThrow(DeployerException.componentActivationException(failedResponse))
      .whenever(app).activateComponent(any(), any(), any(AppComponent.Mode::class.java), any())
    val appInstaller = TestApplicationInstaller(appId, app) // Mock app installation.
    Mockito.doReturn(appInstaller).whenever(executor).getApplicationDeployer(any())

    assertFailsWith<ExecutionException>("Error while launching watch face, message: $failedResponse") {
      executor.debug(EmptyProgressIndicator())
    }
  }

  @Test
  fun testAttachingDebuggerFails() {
    // Use DefaultDebugExecutor, equivalent of pressing debug button.
    val env = getExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance())

    val debuggerManagerExMock = Mockito.mock(DebuggerManagerEx::class.java)
    project.registerServiceInstance(DebuggerManager::class.java, debuggerManagerExMock)
    whenever(debuggerManagerExMock.attachVirtualMachine(any())).thenThrow(ExecutionException("Exception on debug start"))

    val processTerminatedLatch = CountDownLatch(4) // force-stop x2, unsetWatchFace, clearDebugAppAm

    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val receivedAmCommands = ArrayList<String>()

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        forceStop -> {
          deviceState.stopClient(1234)
          processTerminatedLatch.countDown()
        }
        checkVersion -> serviceOutput.writeStdout(
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"3\"")
        // clearDebugAppBroadcast -> serviceOutput.writeStdout("")
        clearDebugAppAm -> {
          serviceOutput.writeStdout("")
          processTerminatedLatch.countDown()
        }
        setWatchFace -> {
          deviceState.startClient(1234, 1235, appId, true)
          serviceOutput.writeStdout(
            "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
            "Broadcast completed: result=1, data=\"Favorite Id=[2] Runtime=[1]\"")
        }

        unsetWatchFace -> {
          deviceState.stopClient(1234)
          serviceOutput.writeStdout("Broadcast completed: result=1")
          processTerminatedLatch.countDown()
        }
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val settings = object : AppRunSettings {
      override val deployOptions = DeployOptions(emptyList(), "", true, true)
      override val componentLaunchOptions = WatchFaceLaunchOptions().apply {
        componentName = this@AndroidWatchFaceConfigurationExecutorTest.componentName
      }
      override val module = myModule
    }

    // Executor we test.
    val executor = Mockito.spy(
      AndroidWatchFaceConfigurationExecutor(env, DeviceFutures.forDevices(listOf(device)), settings, TestApplicationIdProvider(appId),
                                            TestApksProvider(appId)))

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).whenever(executor).getApplicationDeployer(any())

    // We expect the debugger to fail to attach, and we catch the corresponding exception. That happens only in this test as we
    // mocked DebuggerManagerEx to fail above.
    assertFailsWith<ExecutionException>("Exception on debug start") { executor.debug(EmptyProgressIndicator()) }
    if (!processTerminatedLatch.await(10, TimeUnit.SECONDS)) {
      fail("process is not terminated after debugger failed to connect")
    }
  }
}