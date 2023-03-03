package com.android.tools.idea.testartifacts.instrumented

import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.DeviceImpl
import com.android.testutils.MockitoKt
import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProjects
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.DefaultStudioProgramRunner
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.tasks.ConnectDebuggerTask
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTasksProvider
import com.android.tools.idea.stats.RunStats
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import kotlin.test.fail

private const val ORCHESTRATOR_APP_ID = "android.support.test.orchestrator"
private const val ANDROIDX_ORCHESTRATOR_APP_ID = "androidx.test.orchestrator"

class AndroidTestRunConfigurationExecutorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.testProject(LightGradleSyncTestProjects.SIMPLE_APPLICATION)

  private var mockRunStats = Mockito.mock(RunStats::class.java)

  @Test
  fun runSucceeded() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)

    val env = getExecutionEnvironment(listOf(device))
    val launchTaskProvider = getLaunchTaskProvider()
    val runner = AndroidTestRunConfigurationExecutor(
      FakeApplicationIdProvider(),
      env,
      DeviceFutures.forDevices(listOf(device)),      launchTaskProvider
    )

    val runContentDescriptor = runner.run(EmptyProgressIndicator())
    val processHandler = runContentDescriptor.processHandler!!


    Truth.assertThat(processHandler).isInstanceOf(AndroidProcessHandler::class.java)
    Truth.assertThat((processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo("applicationId")
    // AndroidProcessHandler should not be closed even if the target application process is killed. During an
    // instrumentation tests, the target application may be killed in between test cases by test runner. Only test
    // runner knows when all test run completes.
    Truth.assertThat(processHandler.autoTerminate).isEqualTo(false)
    Truth.assertThat(processHandler.isAssociated(device)).isEqualTo(true)

    Mockito.verify(mockRunStats).endLaunchTasks()
    // TODO: 264666049
    processHandler.startNotify()
    processHandler.destroyProcess()
    processHandler.waitFor()
  }

  @Test
  fun debugSucceeded() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)

    val env = getExecutionEnvironment(listOf(device), isDebug = true)
    val launchTaskProvider = getLaunchTaskProvider(isDebug = true)
    val runner = AndroidTestRunConfigurationExecutor(
      FakeApplicationIdProvider(),
      env,
      DeviceFutures.forDevices(listOf(device)),      launchTaskProvider
    )

    runner.debug(EmptyProgressIndicator())

    Mockito.verify(mockRunStats).endLaunchTasks()
  }

  @Test
  fun runFailed() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val env = getExecutionEnvironment(listOf(device))
    val runner = AndroidTestRunConfigurationExecutor(
      FakeApplicationIdProvider(),
      env,
      DeviceFutures.forDevices(listOf(device)),
      getFailingLaunchTaskProvider()
    )

    try {
      runner.run(EmptyProgressIndicator())
      fail("Run should fail")
    }
    catch (_: ExecutionException) {

    }
    Mockito.verify(mockRunStats).endLaunchTasks()
  }


  @Test
  fun androidProcessHandlerMonitorsMasterProcessId() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)

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
    val runner = AndroidTestRunConfigurationExecutor(
      FakeApplicationIdProvider(),
      env,
      DeviceFutures.forDevices(listOf(device)),      launchTaskProvider
    )

    run {
      executionOptions = TestExecutionOption.HOST
      val runContentDescriptor = runner.run(EmptyProgressIndicator())
      Truth.assertThat((runContentDescriptor.processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo("applicationId")
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
      Truth.assertThat((runContentDescriptor.processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo(
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
      Truth.assertThat((runContentDescriptor.processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo(
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
    val configSettings = settings ?: RunManager.getInstance(projectRule.project).createConfiguration("test",
                                                                                                     AndroidTestRunConfigurationType.getInstance().factory)
    (configSettings.configuration as AndroidTestRunConfiguration).setModule(projectRule.module)
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
    override fun getTasks(device: IDevice) = listOf(object : LaunchTask {
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
          val xDebugSessionImpl = Mockito.mock(XDebugSessionImpl::class.java)
          MockitoKt.whenever(xDebugSessionImpl.runContentDescriptor).thenReturn(Mockito.mock(RunContentDescriptor::class.java))
          xDebugSessionImpl
        }
      }
      return null
    }
  }


  private fun getFailingLaunchTaskProvider(): LaunchTasksProvider {
    return object : LaunchTasksProvider {
      override fun getTasks(device: IDevice) = listOf(object : LaunchTask {
        override fun getDescription() = "TestTask"
        override fun getDuration() = 0
        override fun run(launchContext: LaunchContext) = throw ExecutionException("error")
        override fun getId() = "ID"
      })

      override fun getConnectDebuggerTask() = null
    }
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