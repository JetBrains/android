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
package com.android.tools.idea.run

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.sdklib.AndroidVersion.MIN_RECOMMENDED_API
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.run.tasks.LaunchResult
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTasksProvider
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.stats.RunStats
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.util.concurrent.Futures
import com.intellij.execution.Executor
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import java.util.function.BiConsumer

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
  lateinit var mockConsoleConsumer: BiConsumer<String, HyperlinkInfo>
  @Mock
  lateinit var mockExecutor: Executor
  @Mock
  lateinit var mockProgramRunner: ProgramRunner<*>
  @Mock
  lateinit var mockExecutionEnvironment: ExecutionEnvironment
  @Mock
  lateinit var mockConsoleProvider: ConsoleProvider

  private val launchInfo: LaunchInfo by lazy {
    LaunchInfo(mockExecutor, mockProgramRunner, mockExecutionEnvironment, mockConsoleProvider)
  }

  private val progressIndicator: ProgressIndicator by lazy {
    EmptyProgressIndicator()
  }

  @Before
  fun setUp() {
    whenever(mockExecutor.toolWindowId).thenReturn("toolWindowId")
    whenever(mockExecutor.id).thenReturn("id")
  }

  private fun createDeviceFutures(numDevices: Int = 1): DeviceFutures {
    val devices = (1..numDevices).map {
      val device = mock<AndroidDevice>()
      val iDevice = mock<IDevice>()
      whenever(iDevice.isOnline).thenReturn(true)
      whenever(iDevice.version).thenReturn(AndroidVersion(MIN_RECOMMENDED_API, null))
      whenever(device.launchedDevice).thenReturn(Futures.immediateFuture(iDevice))
      device
    }.toList()
    return DeviceFutures(devices)
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

  private fun createLaunchTaskRunner(deviceFutures: DeviceFutures): LaunchTaskRunner {
    return LaunchTaskRunner(
      projectRule.project,
      "configName",
      "applicationId",
      "executionTargetName",
      launchInfo,
      mockProcessHandler,
      deviceFutures,
      mockLaunchTasksProvider,
      mockRunStats,
      mockConsoleConsumer
    )
  }

  @Test
  fun runSucceeded() {
    val deviceFutures = createDeviceFutures()
    val runner = createLaunchTaskRunner(deviceFutures)

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
    val deviceFutures = createDeviceFutures()
    setWarningLaunchTask()
    val runner = createLaunchTaskRunner(deviceFutures)

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
    val deviceFutures = createDeviceFutures()
    val runner = createLaunchTaskRunner(deviceFutures)

    setSwapInfo()
    runner.run(progressIndicator)

    verify(mockProcessHandler, never()).addTargetDevice(eq(deviceFutures.get()[0].get()))
    verify(mockProcessHandler, never()).detachDevice(any())
    verify(mockProcessHandler, never()).detachProcess()
    verify(mockProcessHandler, never()).destroyProcess()

    verify(mockRunStats).endLaunchTasks()
  }

  @Test
  fun runFailedAndProcessHandlerShouldBeDestroyed() {
    val deviceFutures = createDeviceFutures()
    val runner = createLaunchTaskRunner(deviceFutures)

    setFailingLaunchTask()
    runner.run(progressIndicator)

    verify(mockProcessHandler).addTargetDevice(eq(deviceFutures.get()[0].get()))
    verify(mockProcessHandler).detachDevice(eq(deviceFutures.get()[0].get()))
    verify(mockProcessHandler).destroyProcess()

    verify(mockRunStats).endLaunchTasks()
  }

  @Test
  fun runFailedWithMultipleDevicesAndProcessHandlerShouldBeDestroyed() {
    val deviceFutures = createDeviceFutures(numDevices = 2)
    val runner = createLaunchTaskRunner(deviceFutures)

    setFailingLaunchTask()
    runner.run(progressIndicator)

    verify(mockProcessHandler).addTargetDevice(eq(deviceFutures.get()[0].get()))
    verify(mockProcessHandler).addTargetDevice(eq(deviceFutures.get()[1].get()))
    verify(mockProcessHandler).detachDevice(eq(deviceFutures.get()[0].get()))
    verify(mockProcessHandler).detachDevice(eq(deviceFutures.get()[1].get()))
    verify(mockProcessHandler).destroyProcess()

    verify(mockRunStats).endLaunchTasks()
  }

  @Test
  fun runFailedOnOneDeviceWithMultipleDevicesAndProcessHandlerShouldNotBeDestroyed() {
    val deviceFutures = createDeviceFutures(numDevices = 2)
    val runner = createLaunchTaskRunner(deviceFutures)

    val device1 = deviceFutures.get()[0].get()
    val device2 = deviceFutures.get()[1].get()

    setFailingLaunchTask(device1)
    runner.run(progressIndicator)

    verify(mockProcessHandler).addTargetDevice(eq(device1))
    verify(mockProcessHandler).addTargetDevice(eq(device2))
    verify(mockProcessHandler).detachDevice(eq(device1))
    verify(mockProcessHandler, never()).detachDevice(eq(device2))
    verify(mockProcessHandler, never()).destroyProcess()

    verify(mockRunStats).endLaunchTasks()
  }

  @Test
  fun swapRunFailedButProcessHandlerShouldNotBeDetached() {
    val deviceFutures = createDeviceFutures()
    val runner = createLaunchTaskRunner(deviceFutures)

    setSwapInfo()
    setFailingLaunchTask()
    runner.run(progressIndicator)

    verify(mockProcessHandler, never()).addTargetDevice(eq(deviceFutures.get()[0].get()))
    verify(mockProcessHandler, never()).detachDevice(any())
    verify(mockProcessHandler, never()).detachProcess()
    verify(mockProcessHandler, never()).destroyProcess()

    verify(mockRunStats).endLaunchTasks()
  }
}