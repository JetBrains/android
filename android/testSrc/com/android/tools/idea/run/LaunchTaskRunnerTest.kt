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
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.literals.LiveEditServiceImpl
import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.execution.common.processhandler.AndroidRemoteDebugProcessHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProjects
import com.android.tools.idea.project.AndroidRunConfigurations
import com.android.tools.idea.run.activity.launch.EmptyTestConsoleView
import com.android.tools.idea.run.deployment.liveedit.LiveEditApp
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.override
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.content.Content
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail


/**
 * Unit test for [LaunchTaskRunner].
 */
class LaunchTaskRunnerTest {

  val fakeAdb: FakeAdbTestRule = FakeAdbTestRule()

  val projectRule = AndroidProjectRule.testProject(LightGradleSyncTestProjects.SIMPLE_APPLICATION)

  val cleaner = MockitoCleanerRule()

  @get:Rule
  val chain = RuleChain.outerRule(cleaner).around(projectRule).around(fakeAdb)

  @Before
  fun setUp() {
    val androidFacet = projectRule.project.gradleModule(":app")!!.androidFacet
    AndroidRunConfigurations.instance.createRunConfiguration(androidFacet!!)
  }

  @Test
  fun runSucceeded() {
    val deviceState = fakeAdb.connectAndWaitForDevice()
    val latch = CountDownLatch(1)
    deviceState.setActivityManager { args, _ ->
      val command = args.joinToString(" ")
      if (command == "start -n \"applicationId/MainActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER") {
        deviceState.startClient(1234, 1235, "applicationId", false)
        latch.countDown()
      }
    }
    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val deviceFutures = DeviceFutures.forDevices(listOf(device))

    val env = getExecutionEnvironment(listOf(device))
    (env.runProfile as AndroidRunConfiguration).setLaunchActivity("MainActivity")
    (env.runProfile as AndroidRunConfiguration).CLEAR_APP_STORAGE = true
    (env.runProfile as AndroidRunConfiguration).CLEAR_LOGCAT = true

    var logcatCleared = false
    projectRule.project.messageBus.connect(projectRule.testRootDisposable)
      .subscribe(ClearLogcatListener.TOPIC, ClearLogcatListener { logcatCleared = true })

    val runner = LaunchTaskRunner(FakeApplicationIdProvider(), env, deviceFutures, { emptyList<ApkInfo>() })

    val runContentDescriptor = runner.run(EmptyProgressIndicator())
    val processHandler = runContentDescriptor.processHandler!!
    processHandler.startNotify()

    assertThat(logcatCleared).isTrue() // comes from [com.android.tools.idea.run.tasks.ClearAppStorageTaskKt.clearAppStorage]
    assertThat(deviceState.pmLogs).contains("list packages applicationId")
    assertThat(processHandler).isInstanceOf(AndroidProcessHandler::class.java)
    assertThat((processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo("applicationId")
    assertThat(processHandler.autoTerminate).isEqualTo(true)
    assertThat(processHandler.isAssociated(device)).isEqualTo(true)

    if (!latch.await(10, TimeUnit.SECONDS)) {
      fail("Activity is not started")
    }
    deviceState.stopClient(1234) // TODO: flaky test b/273744887
    //if (!processHandler.waitFor(5000)) {
    //  fail("Process handler didn't stop when debug process terminated")
    //}
    processHandler.destroyProcess()
  }

  @Test
  fun debugSucceeded() { //TODO: write handler in fakeAdb for "am capabilities --protobuf"
    StudioFlags.DEBUG_ATTEMPT_SUSPENDED_START.override(false, projectRule.testRootDisposable)

    val deviceState = fakeAdb.connectAndWaitForDevice()
    var startInvocation = 0
    deviceState.setActivityManager { args, output ->
      val command = args.joinToString(" ")
      if (command == "start -n \"applicationId/MainActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -D") {
        deviceState.startClient(1234, 1235, "applicationId", true)
        startInvocation++
      }
    }
    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val deviceFutures = DeviceFutures.forDevices(listOf(device))
    val env = getExecutionEnvironment(listOf(device), isDebug = true)
    (env.runProfile as AndroidRunConfiguration).setLaunchActivity("MainActivity")
    val runner = LaunchTaskRunner(FakeApplicationIdProvider(), env, deviceFutures, { emptyList<ApkInfo>() })

    val processHandler = (runner.debug(EmptyProgressIndicator()).processHandler as AndroidRemoteDebugProcessHandler)
    assertThat(!processHandler.isProcessTerminating || !processHandler.isProcessTerminated)
    deviceState.stopClient(1234)
    if (!processHandler.waitFor(5000)) {
      fail("Process handler didn't stop when debug process terminated")
    }
    assertThat(startInvocation).isEqualTo(1)
  }

  @Test
  fun swapRunSucceeded() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deviceFutures = DeviceFutures.forDevices(listOf(device))
    val env = getExecutionEnvironment(listOf(device))
    val runningProcessHandler = setSwapInfo(env)
    runningProcessHandler.addTargetDevice(device)

    var liveEditServiceNotified = false
    val liveEditServiceImpl = LiveEditServiceImpl(projectRule.project).apply { Disposer.register(projectRule.testRootDisposable, this) }
    val liveEditService = object : LiveEditService by liveEditServiceImpl {
      override fun notifyAppDeploy(
        runProfile: RunProfile, executor: Executor, packageName: String, device: IDevice, app: LiveEditApp
      ): Boolean {
        liveEditServiceNotified = true
        return true
      }
    }

    val runner = LaunchTaskRunner(
      FakeApplicationIdProvider(), env, deviceFutures, { emptyList<ApkInfo>() }, liveEditService
    )

    val runContentDescriptor = runner.applyChanges(EmptyProgressIndicator())
    assertThat(runContentDescriptor.isHiddenContent).isEqualTo(true)
    assertThat(liveEditServiceNotified).isEqualTo(true)

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
    val runner = LaunchTaskRunner(FakeApplicationIdProvider(), env, deviceFutures, { throw ExecutionException("Exception") })

    try {
      runner.run(EmptyProgressIndicator())
      fail("Run should fail")
    } catch (_: ExecutionException) {

    }
  }

  @Test
  fun swapRunFailedButProcessHandlerShouldNotBeDetached() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deviceFutures = DeviceFutures.forDevices(listOf(device))
    val env = getExecutionEnvironment(listOf(device))
    val runningProcessHandler = setSwapInfo(env)
    runningProcessHandler.addTargetDevice(device)
    val runner = LaunchTaskRunner(FakeApplicationIdProvider(), env, deviceFutures, { throw ExecutionException("Exception") })

    try {
      runner.applyChanges(EmptyProgressIndicator())
      fail("Run should fail")
    } catch (_: ExecutionException) {
    }

    assertThat(runningProcessHandler.isAssociated(device)).isEqualTo(true)
    assertThat(runningProcessHandler.isProcessTerminated).isEqualTo(false)
    assertThat(runningProcessHandler.isProcessTerminating).isEqualTo(false)
  }


  private fun getExecutionEnvironment(
    devices: List<IDevice>, isDebug: Boolean = false, settings: RunnerAndConfigurationSettings? = null
  ): ExecutionEnvironment {
    val configSettings = settings ?: RunManager.getInstance(projectRule.project).getConfigurationSettingsList(
      AndroidRunConfigurationType.getInstance()
    ).first()
    val executor = if (isDebug) DefaultRunExecutor.getRunExecutorInstance() else DefaultDebugExecutor.getDebugExecutorInstance()
    val executionEnvironment =
      ExecutionEnvironmentBuilder(projectRule.project, executor).runnerAndSettings(DefaultStudioProgramRunner(), configSettings)
        .target(object : AndroidExecutionTarget() {
          override fun getId() = "TestTarget"
          override fun getDisplayName() = "TestTarget"
          override fun getIcon() = null
          override fun getAvailableDeviceCount() = devices.size
          override fun getRunningDevices() = devices
        }).build()
    return executionEnvironment
  }

  private fun setSwapInfo(env: ExecutionEnvironment): AndroidProcessHandler {
    env.putUserData(SwapInfo.SWAP_INFO_KEY, SwapInfo(SwapInfo.SwapType.APPLY_CHANGES))

    val processHandlerForSwap = AndroidProcessHandler(projectRule.project, "applicationId")
    processHandlerForSwap.startNotify()
    Disposer.register(projectRule.project) {
      processHandlerForSwap.detachProcess()
    }
    runInEdtAndWait {
      val runContentDescriptor = showRunContent(DefaultExecutionResult(EmptyTestConsoleView(), processHandlerForSwap), env)!!.apply {
        setAttachedContent(mock(Content::class.java))
      }

      val mockRunContentManager = mock(RunContentManager::class.java)
      whenever(mockRunContentManager.findContentDescriptor(eq(env.executor), eq(processHandlerForSwap))).thenReturn(runContentDescriptor)
      projectRule.project.replaceService(RunContentManager::class.java, mockRunContentManager, projectRule.testRootDisposable)

      val mockExecutionManager = mock(ExecutionManagerImpl::class.java)
      whenever(mockExecutionManager.getRunningDescriptors(any())).thenReturn(listOf(runContentDescriptor))
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

