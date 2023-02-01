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
import com.android.ddmlib.internal.DeviceImpl
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProjects
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.project.AndroidRunConfigurations
import com.android.tools.idea.run.activity.launch.EmptyTestConsoleView
import com.android.tools.idea.run.configuration.execution.TestDeployTarget
import com.android.tools.idea.run.tasks.ConnectDebuggerTask
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTasksProvider
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.stats.RunStats
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType
import com.android.tools.idea.testing.AndroidProjectRule
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
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import kotlin.test.fail

private const val ORCHESTRATOR_APP_ID = "android.support.test.orchestrator"
private const val ANDROIDX_ORCHESTRATOR_APP_ID = "androidx.test.orchestrator"

/**
 * Unit test for [LaunchTaskRunner].
 */
class LaunchTaskRunnerTest {

  @get:Rule
  val projectRule = AndroidProjectRule.testProject(LightGradleSyncTestProjects.SIMPLE_APPLICATION)

  private var mockRunStats = mock(RunStats::class.java)
  private var consoleProvider: ConsoleProvider = ConsoleProvider { _, _, _ -> EmptyTestConsoleView() }
  private val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)


  @Before
  fun setUp() {
    val androidFacet = projectRule.project.gradleModule(":app")!!.androidFacet
    AndroidRunConfigurations.instance.createRunConfiguration(androidFacet!!)
  }

  @Test
  fun runSucceeded() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deployTarget = TestDeployTarget(device)

    val env = getExecutionEnvironment(listOf(device))
    val launchTaskProvider = getLaunchTaskProvider()
    val runner = LaunchTaskRunner(
      consoleProvider,
      FakeApplicationIdProvider(),
      env,
      deployTarget,
      launchTaskProvider
    )

    val runContentDescriptor = runner.run(EmptyProgressIndicator())
    val processHandler = runContentDescriptor.processHandler!!


    assertThat(processHandler).isInstanceOf(AndroidProcessHandler::class.java)
    assertThat((processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo("applicationId")
    assertThat(processHandler.autoTerminate).isEqualTo(true)
    assertThat(processHandler.isAssociated(device)).isEqualTo(true)

    verify(mockRunStats).endLaunchTasks()
    // TODO: 264666049
    processHandler.startNotify()
    processHandler.destroyProcess()
    processHandler.waitFor()
  }

  @Test
  fun debugSucceeded() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deployTarget = TestDeployTarget(device)

    val env = getExecutionEnvironment(listOf(device), isDebug = true)
    val launchTaskProvider = getLaunchTaskProvider(isDebug = true)
    val runner = LaunchTaskRunner(
      consoleProvider,
      FakeApplicationIdProvider(),
      env,
      deployTarget,
      launchTaskProvider
    )

    runner.debug(EmptyProgressIndicator())

    verify(mockRunStats).endLaunchTasks()
  }

  @Test
  fun swapRunSucceeded() {
    val deployTarget = TestDeployTarget(device)
    val env = getExecutionEnvironment(listOf(device))
    val runningProcessHandler = setSwapInfo(env)
    runningProcessHandler.addTargetDevice(device)

    val launchTaskProvider = getLaunchTaskProvider()
    val runner = LaunchTaskRunner(
      consoleProvider,
      FakeApplicationIdProvider(),
      env,
      deployTarget,
      launchTaskProvider
    )

    val runContentDescriptor = runner.applyChanges(EmptyProgressIndicator())
    val processHandler = runContentDescriptor.processHandler

    assertThat(processHandler).isEqualTo(runningProcessHandler)
    assertThat((processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo("applicationId")
    assertThat(processHandler.autoTerminate).isEqualTo(true)
    assertThat(processHandler.isAssociated(device)).isEqualTo(true)
    assertThat(processHandler.isProcessTerminated).isEqualTo(false)
    assertThat(processHandler.isProcessTerminating).isEqualTo(false)

    verify(mockRunStats).endLaunchTasks()

    // TODO: 264666049
    processHandler.destroyProcess()
    processHandler.waitFor()
  }

  @Test
  fun runFailed() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deployTarget = TestDeployTarget(device)
    val env = getExecutionEnvironment(listOf(device))
    val runner = LaunchTaskRunner(
      consoleProvider,
      FakeApplicationIdProvider(),
      env,
      deployTarget,
      getFailingLaunchTaskProvider()
    )

    try {
      runner.run(EmptyProgressIndicator())
      fail("Run should fail")
    }
    catch (_: ExecutionException) {

    }
    verify(mockRunStats).endLaunchTasks()
  }

  @Test
  fun swapRunFailedButProcessHandlerShouldNotBeDetached() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deployTarget = TestDeployTarget(device)
    val env = getExecutionEnvironment(listOf(device))
    val runningProcessHandler = setSwapInfo(env)
    runningProcessHandler.addTargetDevice(device)
    val runner = LaunchTaskRunner(
      consoleProvider,
      FakeApplicationIdProvider(),
      env,
      deployTarget,
      getFailingLaunchTaskProvider()
    )

    try {
      runner.applyChanges(EmptyProgressIndicator())
      fail("Run should fail")
    }
    catch (_: ExecutionException) {
    }

    assertThat(runningProcessHandler.isAssociated(device)).isEqualTo(true)
    assertThat(runningProcessHandler.isProcessTerminated).isEqualTo(false)
    assertThat(runningProcessHandler.isProcessTerminating).isEqualTo(false)

    verify(mockRunStats).endLaunchTasks()

    // TODO: 264666049
    runningProcessHandler.destroyProcess()
    runningProcessHandler.waitFor()
  }

  @Test
  fun androidProcessHandlerMonitorsMasterProcessId() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deployTarget = TestDeployTarget(device)

    var executionOptions = TestExecutionOption.HOST

    val testConfiguration = object : AndroidTestRunConfiguration(projectRule.project,
                                                                 AndroidTestRunConfigurationType.getInstance().factory) {
      override fun getTestExecutionOption(facet: AndroidFacet?): TestExecutionOption {
        return executionOptions
      }
    }
    testConfiguration.setModule(projectRule.module)

    val settings = RunManager.getInstance(projectRule.project).createConfiguration(testConfiguration,
                                                                                   AndroidTestRunConfigurationType.getInstance().factory)

    val env = getExecutionEnvironment(listOf(device), false, settings)
    val launchTaskProvider = getLaunchTaskProvider()
    val runner = LaunchTaskRunner(
      consoleProvider,
      FakeApplicationIdProvider(),
      env,
      deployTarget,
      launchTaskProvider
    )

    run {
      executionOptions = TestExecutionOption.HOST
      val runContentDescriptor = runner.run(EmptyProgressIndicator())
      assertThat((runContentDescriptor.processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo("applicationId")
      // TODO: 264666049
      with(runContentDescriptor.processHandler!!) {
        startNotify()
        destroyProcess()
        waitFor()
      }
    }

    run {
      executionOptions = TestExecutionOption.ANDROID_TEST_ORCHESTRATOR
      val runContentDescriptor = runner.run(EmptyProgressIndicator())
      assertThat((runContentDescriptor.processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo(
        ORCHESTRATOR_APP_ID)
      // TODO: 264666049
      with(runContentDescriptor.processHandler!!) {
        startNotify()
        destroyProcess()
        waitFor()
      }
    }

    run {
      executionOptions = TestExecutionOption.ANDROIDX_TEST_ORCHESTRATOR
      val runContentDescriptor = runner.run(EmptyProgressIndicator())
      assertThat((runContentDescriptor.processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo(
        ANDROIDX_ORCHESTRATOR_APP_ID)
      // TODO: 264666049
      with(runContentDescriptor.processHandler!!) {
        startNotify()
        destroyProcess()
        waitFor()
      }
    }
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
        override fun isApplicationRunning(appPackage: String): Boolean {
          throw UnsupportedOperationException()
        }

        override fun getAvailableDeviceCount() = devices.size
        override fun getRunningDevices() = devices
      })
      .build()
    executionEnvironment.putUserData(RunStats.KEY, mockRunStats)
    return executionEnvironment
  }

  private fun getLaunchTaskProvider(isDebug: Boolean = false) = object : LaunchTasksProvider {
    override fun getTasks(device: IDevice,
                          consolePrinter: ConsolePrinter) = listOf(object : LaunchTask {
      override fun getDescription() = "TestTask"
      override fun getDuration() = 0
      override fun run(launchContext: LaunchContext) {
        return
      }

      override fun getId() = "ID"
    })

    override fun getConnectDebuggerTask(): ConnectDebuggerTask? {
      if (isDebug) {
        return ConnectDebuggerTask { _, _, _, _, _ ->
          val xDebugSessionImpl = mock(XDebugSessionImpl::class.java)
          whenever(xDebugSessionImpl.runContentDescriptor).thenReturn(mock(RunContentDescriptor::class.java))
          xDebugSessionImpl
        }
      }
      return null
    }
  }


  private fun getFailingLaunchTaskProvider(): LaunchTasksProvider {
    return object : LaunchTasksProvider {
      override fun getTasks(device: IDevice,
                            consolePrinter: ConsolePrinter) = listOf(object : LaunchTask {
        override fun getDescription() = "TestTask"
        override fun getDuration() = 0
        override fun run(launchContext: LaunchContext) = throw ExecutionException("error")
        override fun getId() = "ID"
      })

      override fun getConnectDebuggerTask() = null
    }
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

