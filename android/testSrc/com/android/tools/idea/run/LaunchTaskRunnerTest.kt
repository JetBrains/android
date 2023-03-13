/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")!!
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
package com.android.tools.idea.run

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.DeviceImpl
import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.testutils.MockitoCleanerRule
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.execution.common.processhandler.AndroidRemoteDebugProcessHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProjects
import com.android.tools.idea.project.AndroidRunConfigurations
import com.android.tools.idea.run.activity.launch.EmptyTestConsoleView
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.override
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail


/**
 * Unit test for [LaunchTaskRunner].
 */
class LaunchTaskRunnerTest {

  @get:Rule
  val projectRule = AndroidProjectRule.testProject(LightGradleSyncTestProjects.SIMPLE_APPLICATION)

  @get:Rule
  val fakeAdb = FakeAdbTestRule()

  @get:Rule
  val cleaner = MockitoCleanerRule()

  private val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)

  @Before
  fun setUp() {
    val androidFacet = projectRule.project.gradleModule(":app")!!.androidFacet
    AndroidRunConfigurations.instance.createRunConfiguration(androidFacet!!)
  }

  @After
  fun after() {
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }

    AndroidDebugBridge.getBridge()!!.devices.forEach {
      fakeAdb.server.disconnectDevice(it.serialNumber)
    }
  }

  @Test
  fun runSucceeded() {
    val deviceState = fakeAdb.connectAndWaitForDevice()
    val latch = CountDownLatch(1)
    deviceState.setActivityManager { args, _ ->
      if (args.joinToString(
          " ") == "start -n \"applicationId/MainActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER") {
        deviceState.startClient(1234, 1235, "applicationId", false)
        latch.countDown()
      }
    }
    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val deviceFutures = DeviceFutures.forDevices(listOf(device))

    val env = getExecutionEnvironment(listOf(device))
    (env.runProfile as AndroidRunConfiguration).setLaunchActivity("MainActivity")
    val runner = LaunchTaskRunner(
      FakeApplicationIdProvider(),
      env,
      deviceFutures
    ) { emptyList<ApkInfo>() }

    val runContentDescriptor = runner.run(EmptyProgressIndicator())
    val processHandler = runContentDescriptor.processHandler!!
    processHandler.startNotify()
    assertThat(processHandler).isInstanceOf(AndroidProcessHandler::class.java)
    assertThat((processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo("applicationId")
    assertThat(processHandler.autoTerminate).isEqualTo(true)
    assertThat(processHandler.isAssociated(device)).isEqualTo(true)

    if (!latch.await(10, TimeUnit.SECONDS)) {
      fail("Activity is not started")
    }
    deviceState.stopClient(1234)
    if (!processHandler.waitFor(5000)) {
      fail("Process handler didn't stop when debug process terminated")
    }
  }

  @Test
  fun debugSucceeded() {
    //TODO: write handler in fakeAdb for "am capabilities --protobuf"
    StudioFlags.DEBUG_ATTEMPT_SUSPENDED_START.override(false, projectRule.testRootDisposable)

    val deviceState = fakeAdb.connectAndWaitForDevice()
    deviceState.setActivityManager { args, output ->
      val command = args.joinToString(" ")
      if (command == "start -n \"applicationId/MainActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -D") {
        deviceState.startClient(1234, 1235, "applicationId", true)
      }
    }
    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val deviceFutures = DeviceFutures.forDevices(listOf(device))
    val env = getExecutionEnvironment(listOf(device), isDebug = true)
    (env.runProfile as AndroidRunConfiguration).setLaunchActivity("MainActivity")
    val runner = LaunchTaskRunner(
      FakeApplicationIdProvider(),
      env,
      deviceFutures
    ) { emptyList<ApkInfo>() }

    val processHandler = (runner.debug(EmptyProgressIndicator()).processHandler as AndroidRemoteDebugProcessHandler)
    assertThat(!processHandler.isProcessTerminating || !processHandler.isProcessTerminated)
    deviceState.stopClient(1234)
    if (!processHandler.waitFor(5000)) {
      fail("Process handler didn't stop when debug process terminated")
    }
  }

  @Test
  fun swapRunSucceeded() {
    val deviceFutures = DeviceFutures.forDevices(listOf(device))
    val env = getExecutionEnvironment(listOf(device))
    val runningProcessHandler = setSwapInfo(env)
    runningProcessHandler.addTargetDevice(device)

    val runner = LaunchTaskRunner(
      FakeApplicationIdProvider(),
      env,
      deviceFutures
    ) { emptyList<ApkInfo>() }

    val runContentDescriptor = runner.applyChanges(EmptyProgressIndicator())
    val processHandler = runContentDescriptor.processHandler

    assertThat(processHandler).isEqualTo(runningProcessHandler)
    assertThat((processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo("applicationId")
    assertThat(processHandler.autoTerminate).isEqualTo(true)
    assertThat(processHandler.isAssociated(device)).isEqualTo(true)
    assertThat(processHandler.isProcessTerminated).isEqualTo(false)
    assertThat(processHandler.isProcessTerminating).isEqualTo(false)
  }

  @Test
  fun runFailed() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deviceFutures = DeviceFutures.forDevices(listOf(device))
    val env = getExecutionEnvironment(listOf(device))
    val runner = LaunchTaskRunner(
      FakeApplicationIdProvider(),
      env,
      deviceFutures)
    { throw ExecutionException("Exception") }

    try {
      runner.run(EmptyProgressIndicator())
      fail("Run should fail")
    }
    catch (_: ExecutionException) {

    }
  }

  @Test
  fun swapRunFailedButProcessHandlerShouldNotBeDetached() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deviceFutures = DeviceFutures.forDevices(listOf(device))
    val env = getExecutionEnvironment(listOf(device))
    val runningProcessHandler = setSwapInfo(env)
    runningProcessHandler.addTargetDevice(device)
    val runner = LaunchTaskRunner(FakeApplicationIdProvider(), env, deviceFutures) { throw ExecutionException("Exception") }

    try {
      runner.applyChanges(EmptyProgressIndicator())
      fail("Run should fail")
    }
    catch (_: ExecutionException) {
    }

    assertThat(runningProcessHandler.isAssociated(device)).isEqualTo(true)
    assertThat(runningProcessHandler.isProcessTerminated).isEqualTo(false)
    assertThat(runningProcessHandler.isProcessTerminating).isEqualTo(false)
  }


  private fun getExecutionEnvironment(devices: List<IDevice>,
                                      isDebug: Boolean = false,
                                      settings: RunnerAndConfigurationSettings? = null): ExecutionEnvironment {
    val configSettings = settings ?: RunManager.getInstance(projectRule.project).getConfigurationSettingsList(
      AndroidRunConfigurationType.getInstance()).first()
    val executor = if (isDebug) DefaultRunExecutor.getRunExecutorInstance() else DefaultDebugExecutor.getDebugExecutorInstance()
    val executionEnvironment = ExecutionEnvironmentBuilder(projectRule.project, executor)
      .runnerAndSettings(DefaultStudioProgramRunner(), configSettings)
      .target(object : AndroidExecutionTarget() {
        override fun getId() = "TestTarget"
        override fun getDisplayName() = "TestTarget"
        override fun getIcon() = null
        override fun getAvailableDeviceCount() = devices.size
        override fun getRunningDevices() = devices
      })
      .build()
    return executionEnvironment
  }

  private fun setSwapInfo(env: ExecutionEnvironment): AndroidProcessHandler {
    env.putUserData(SwapInfo.SWAP_INFO_KEY, SwapInfo(SwapInfo.SwapType.APPLY_CHANGES))

    val processHandlerForSwap = AndroidProcessHandler(projectRule.project, "applicationId")
    processHandlerForSwap.startNotify()
    runInEdtAndWait {
      val runContentDescriptor = showRunContent(DefaultExecutionResult(EmptyTestConsoleView(), processHandlerForSwap), env)

      val mockRunContentManager = mock(RunContentManager::class.java)
      whenever(mockRunContentManager.allDescriptors).thenReturn(listOf(runContentDescriptor))
      projectRule.project.replaceService(RunContentManager::class.java, mockRunContentManager, projectRule.testRootDisposable)

      val mockExecutionManager = mock(ExecutionManager::class.java)
      whenever(mockExecutionManager.getRunningProcesses()).thenReturn(arrayOf(processHandlerForSwap))
      projectRule.project.replaceService(ExecutionManager::class.java, mockExecutionManager, projectRule.testRootDisposable)
    }

    return processHandlerForSwap
  }

  private class FakeApplicationIdProvider : ApplicationIdProvider {
    override fun getPackageName(): String {
      return "applicationId"
    }

    override fun getTestPackageName(): String {
      return "applicationId"
    }
  }
}

