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

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.sdklib.AndroidVersion.MIN_RECOMMENDED_API
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.editor.DeployTargetState
import com.android.tools.idea.run.tasks.LaunchResult
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTasksProvider
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.stats.RunStats
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.util.concurrent.Futures
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit

/**
 * Unit test for [LaunchTaskRunner].
 */
class LaunchTaskRunnerTest {
  @get:Rule
  var mockitoJunit = MockitoJUnit.rule()
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Mock
  lateinit var mockProcessHandler: AndroidProcessHandler

  @Mock
  lateinit var mockLaunchTasksProvider: LaunchTasksProvider

  @Mock
  lateinit var mockRunStats: RunStats

  @Mock
  lateinit var mockConsole: ConsoleView

  @Mock
  lateinit var mockExecutor: Executor

  @Mock
  lateinit var mockExecutionEnvironment: ExecutionEnvironment

  @Mock
  lateinit var mockRunConfiguration: RunConfiguration

  @Mock
  lateinit var mockExecutionTarget: ExecutionTarget


  private val progressIndicator: ProgressIndicator by lazy {
    EmptyProgressIndicator()
  }

  @Before
  fun setUp() {
    whenever(mockExecutionEnvironment.executor).thenReturn(mockExecutor)
    whenever(mockExecutionEnvironment.runProfile).thenReturn(mockRunConfiguration)
    whenever(mockExecutionEnvironment.executionTarget).thenReturn(mockExecutionTarget)
    whenever(mockRunConfiguration.name).thenReturn("app")
    whenever(mockExecutor.toolWindowId).thenReturn("toolWindowId")
    whenever(mockExecutor.id).thenReturn("id")
  }

  companion object {
    fun createDeployTarget(numDevices: Int = 1): DeployTarget {
      val devices = (1..numDevices).map {
        val device = mock<AndroidDevice>()
        val iDevice = mock<IDevice>()
        whenever(iDevice.isOnline).thenReturn(true)
        whenever(iDevice.version).thenReturn(AndroidVersion(MIN_RECOMMENDED_API, null))
        whenever(iDevice.serialNumber).thenReturn("serialNumber")
        whenever(device.launchedDevice).thenReturn(Futures.immediateFuture(iDevice))
        device
      }.toList()
      return object : DeployTarget {
        override fun hasCustomRunProfileState(executor: Executor) = false

        override fun getRunProfileState(executor: Executor, env: ExecutionEnvironment, state: DeployTargetState): RunProfileState {
          throw UnsupportedOperationException()
        }

        override fun getDevices(project: Project) = DeviceFutures(devices)
      }
    }
  }

  private fun setFailingLaunchTask(targetDevice: IDevice? = null) {
    val failingTask = mock<LaunchTask>()
    whenever(failingTask.shouldRun(any())).thenReturn(true)
    whenever(failingTask.run(any())).thenReturn(LaunchResult.error("", ""))
    whenever(mockLaunchTasksProvider.getTasks(
      targetDevice?.let { eq(targetDevice) } ?: any(),
      any(),
      any())).thenReturn(listOf(failingTask))
  }

  private fun setWarningLaunchTask(targetDevice: IDevice? = null) {
    val warningTask = mock<LaunchTask>()
    whenever(warningTask.shouldRun(any())).thenReturn(true)
    whenever(warningTask.run(any())).thenReturn(LaunchResult.warning(""))
    whenever(mockLaunchTasksProvider.getTasks(
      targetDevice?.let { eq(targetDevice) } ?: any(),
      any(),
      any())).thenReturn(listOf(warningTask))
  }

  private fun setSwapInfo() {
    whenever(mockExecutionEnvironment.getUserData(eq(SwapInfo.SWAP_INFO_KEY))).thenReturn(SwapInfo(SwapInfo.SwapType.APPLY_CHANGES))
  }

  private fun createLaunchTaskRunner(deployTarget: DeployTarget): LaunchTaskRunner {
    return LaunchTaskRunner(
      projectRule.project,
      "applicationId",
      mockExecutionEnvironment,
      mockProcessHandler,
      deployTarget,
      mockLaunchTasksProvider,
      mockRunStats,
      mockConsole
    )
  }

  @Test
  fun runSucceeded() {
    val deployTarget = createDeployTarget()
    val deviceFutures = deployTarget.getDevices(projectRule.project)!!
    val runner = createLaunchTaskRunner(deployTarget)

    runner.run(progressIndicator)

    verify(mockProcessHandler).addTargetDevice(eq(deviceFutures.get()[0].get()))
    // we should have two force-stop calls for APIs <= 25, which the mock IDevice is
    verify(deviceFutures.get()[0].get(), times(2)).forceStop(any())
    verify(mockProcessHandler, never()).detachDevice(any())
    verify(mockProcessHandler, never()).detachProcess()
    verify(mockProcessHandler, never()).destroyProcess()

    verify(mockRunStats).endLaunchTasks()
  }

  @Test
  fun runWithWarnings() {
    // Ideally, we would like to assert that the warning text is emitted to the console and the notifications expose it, but the current
    // test infra doesn't have mechanism to do that so all we can do is assert that the warnings do not fail the launch.
    val deployTarget = createDeployTarget()
    val deviceFutures = deployTarget.getDevices(projectRule.project)!!
    setWarningLaunchTask()
    val runner = createLaunchTaskRunner(deployTarget)

    runner.run(progressIndicator)

    verify(mockProcessHandler).addTargetDevice(eq(deviceFutures.get()[0].get()))
    // we should have two force-stop calls for APIs <= 25, which the mock IDevice is
    verify(deviceFutures.get()[0].get(), times(2)).forceStop(any())
    verify(mockProcessHandler, never()).detachDevice(any())
    verify(mockProcessHandler, never()).detachProcess()
    verify(mockProcessHandler, never()).destroyProcess()

    verify(mockRunStats).endLaunchTasks()
  }

  @Test
  fun swapRunSucceeded() {
    val deployTarget = createDeployTarget()
    val deviceFutures = deployTarget.getDevices(projectRule.project)!!
    setSwapInfo()
    val runner = createLaunchTaskRunner(deployTarget)

    runner.run(progressIndicator)

    verify(mockProcessHandler, never()).addTargetDevice(eq(deviceFutures.get()[0].get()))
    verify(mockProcessHandler, never()).detachDevice(any())
    verify(mockProcessHandler, never()).detachProcess()
    verify(mockProcessHandler, never()).destroyProcess()

    verify(mockRunStats).endLaunchTasks()
  }

  @Test
  fun runFailedAndProcessHandlerShouldBeDestroyed() {
    val deployTarget = createDeployTarget()
    val deviceFutures = deployTarget.getDevices(projectRule.project)!!
    val runner = createLaunchTaskRunner(deployTarget)

    setFailingLaunchTask()
    try {
      runner.run(progressIndicator)
    }
    catch (_: ExecutionException) {
    }
    verify(mockProcessHandler).addTargetDevice(eq(deviceFutures.get()[0].get()))
    verify(mockProcessHandler).detachDevice(eq(deviceFutures.get()[0].get()))

    verify(mockRunStats).endLaunchTasks()
    verify(mockRunStats).fail()
  }

  @Test
  fun runFailedWithMultipleDevicesAndProcessHandlerShouldBeDestroyed() {
    val deployTarget = createDeployTarget(numDevices = 2)
    val deviceFutures = deployTarget.getDevices(projectRule.project)!!
    val runner = createLaunchTaskRunner(deployTarget)

    setFailingLaunchTask()
    try {
      runner.run(progressIndicator)
    }
    catch (_: ExecutionException) {
    }

    verify(mockProcessHandler).addTargetDevice(eq(deviceFutures.get()[0].get()))
    verify(mockProcessHandler).addTargetDevice(eq(deviceFutures.get()[1].get()))

    verify(mockRunStats).endLaunchTasks()
    verify(mockRunStats).fail()
  }

  @Test
  fun runFailedOnOneDeviceWithMultipleDevicesAndProcessHandlerShouldNotBeDestroyed() {
    val deployTarget = createDeployTarget(numDevices = 2)
    val deviceFutures = deployTarget.getDevices(projectRule.project)!!
    val runner = createLaunchTaskRunner(deployTarget)

    val device1 = deviceFutures.get()[0].get()
    val device2 = deviceFutures.get()[1].get()

    setFailingLaunchTask(device1)
    try {
      runner.run(progressIndicator)
    }
    catch (_: ExecutionException) {
    }
    verify(mockProcessHandler).addTargetDevice(eq(device1))
    verify(mockProcessHandler).addTargetDevice(eq(device2))

    verify(mockRunStats).endLaunchTasks()
    verify(mockRunStats).fail()
  }

  @Test
  fun swapRunFailedButProcessHandlerShouldNotBeDetached() {
    val deployTarget = createDeployTarget()
    val deviceFutures = deployTarget.getDevices(projectRule.project)!!
    setSwapInfo()
    val runner = createLaunchTaskRunner(deployTarget)

    setFailingLaunchTask()
    try {
      runner.run(progressIndicator)
    }
    catch (_: ExecutionException) {
    }

    verify(mockProcessHandler, never()).addTargetDevice(eq(deviceFutures.get()[0].get()))
    verify(mockProcessHandler, never()).detachDevice(any())
    verify(mockProcessHandler, never()).detachProcess()
    verify(mockProcessHandler, never()).destroyProcess()

    verify(mockRunStats).endLaunchTasks()
    verify(mockRunStats).fail()
  }
}