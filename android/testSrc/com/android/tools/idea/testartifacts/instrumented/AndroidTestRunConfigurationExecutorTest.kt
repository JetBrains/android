package com.android.tools.idea.testartifacts.instrumented

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.DeviceImpl
import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.testutils.MockitoCleanerRule
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProjects
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.DefaultStudioProgramRunner
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.editor.NoApksProvider
import com.android.tools.idea.stats.RunStats
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.testframework.sm.TestHistoryConfiguration
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail

private const val ORCHESTRATOR_APP_ID = "android.support.test.orchestrator"
private const val ANDROIDX_ORCHESTRATOR_APP_ID = "androidx.test.orchestrator"

class AndroidTestRunConfigurationExecutorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.testProject(LightGradleSyncTestProjects.SIMPLE_APPLICATION)

  @get:Rule
  val fakeAdb = FakeAdbTestRule()

  @get:Rule
  val cleaner = MockitoCleanerRule()

  @After
  fun after() {
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }

    AndroidDebugBridge.getBridge()!!.devices.forEach {
      fakeAdb.server.disconnectDevice(it.serialNumber)
    }
  }

  @Test
  fun runSucceededAndSaveHistory() {
    val deviceState = fakeAdb.connectAndWaitForDevice()
    val startDownLatch = CountDownLatch(1)
    deviceState.setActivityManager { args, _ ->
      if (args[0] == "instrument") {
        startDownLatch.await()
      }
    }

    val historyLatch = CountDownLatch(1)
    val testHistoryConfiguration = mock<TestHistoryConfiguration>()
    whenever(testHistoryConfiguration.registerHistoryItem(any(), eq("test"), any())).then {
      historyLatch.countDown()
    }
    projectRule.project.replaceService(TestHistoryConfiguration::class.java, testHistoryConfiguration, projectRule.testRootDisposable)
    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val mockRunStats = Mockito.mock(RunStats::class.java)
    val env = getExecutionEnvironment(listOf(device)).apply {
      putUserData(RunStats.KEY, mockRunStats)
    }
    val executor = AndroidTestRunConfigurationExecutor(
      env,
      DeviceFutures.forDevices(listOf(device))
    ) { NoApksProvider() }

    val runContentDescriptor = executor.run(EmptyProgressIndicator())
    val processHandler = runContentDescriptor.processHandler!!
    processHandler.startNotify()

    Truth.assertThat(processHandler).isInstanceOf(AndroidProcessHandler::class.java)
    Truth.assertThat((processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo("testApplicationId")
    // AndroidProcessHandler should not be closed even if the target application process is killed. During an
    // instrumentation tests, the target application may be killed in between test cases by test executor. Only test
    // executor knows when all test run completes.
    Truth.assertThat(processHandler.autoTerminate).isEqualTo(false)
    Truth.assertThat(processHandler.isAssociated(device)).isEqualTo(true)
    startDownLatch.countDown()
    processHandler.waitFor()
    if (!historyLatch.await(20, TimeUnit.SECONDS)) {
      fail("History is not saved")
    }
  }

  @Test
  fun debugSucceeded() {
    val historyLatch = CountDownLatch(1)
    val testHistoryConfiguration = mock<TestHistoryConfiguration>()
    whenever(testHistoryConfiguration.registerHistoryItem(any(), eq("test"), any())).then {
      historyLatch.countDown()
    }
    projectRule.project.replaceService(TestHistoryConfiguration::class.java, testHistoryConfiguration, projectRule.testRootDisposable)
    val deviceState = fakeAdb.connectAndWaitForDevice()
    deviceState.setActivityManager { args, _ ->
      if (args[0] == "instrument") {
        FakeAdbTestRule.launchAndWaitForProcess(deviceState, 1235, "testApplicationId", true)
      }
    }
    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val mockRunStats = Mockito.mock(RunStats::class.java)
    val env = getExecutionEnvironment(listOf(device), isDebug = true).apply {
      putUserData(RunStats.KEY, mockRunStats)
    }
    val executor = AndroidTestRunConfigurationExecutor(
      env,

      DeviceFutures.forDevices(listOf(device))) { NoApksProvider() }

    val runContentDescriptor = executor.debug(EmptyProgressIndicator())

    assertThat(runContentDescriptor.executionConsole).isInstanceOf(AndroidTestSuiteView::class.java)

    deviceState.stopClient(1235)
    runContentDescriptor.processHandler!!.waitFor()
    if (!historyLatch.await(20, TimeUnit.SECONDS)) {
      fail("History is not saved")
    }
  }

  @Test
  fun runFailed() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)

    val env = getExecutionEnvironment(listOf(device))
    val executor = AndroidTestRunConfigurationExecutor(
      env,
      DeviceFutures.forDevices(listOf(device))) { ApkProvider { throw ExecutionException("Can't get apks") } }

    try {
      executor.run(EmptyProgressIndicator())
      fail("Run should fail")
    }
    catch (e: ExecutionException) {
      assertThat(e.message).isEqualTo("Can't get apks")
    }
  }


  @Test
  fun androidProcessHandlerMonitorsMasterProcessId() {
    fakeAdb.connectAndWaitForDevice()
    val device = AndroidDebugBridge.getBridge()!!.devices.single()
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

    val mockRunStats = Mockito.mock(RunStats::class.java)
    val env = getExecutionEnvironment(listOf(device), false, settings).apply {
      putUserData(RunStats.KEY, mockRunStats)
    }
    val executor = AndroidTestRunConfigurationExecutor(
      env,

      DeviceFutures.forDevices(listOf(device))) { NoApksProvider() }
    val historyLatch = CountDownLatch(3)
    val testHistoryConfiguration = mock<TestHistoryConfiguration>()
    whenever(testHistoryConfiguration.registerHistoryItem(any(), any(), any())).then {
      historyLatch.countDown()
    }
    projectRule.project.replaceService(TestHistoryConfiguration::class.java, testHistoryConfiguration, projectRule.testRootDisposable)
    run {
      executionOptions = TestExecutionOption.HOST
      val runContentDescriptor = executor.run(EmptyProgressIndicator())
      Truth.assertThat((runContentDescriptor.processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo("testApplicationId")
    }

    run {
      executionOptions = TestExecutionOption.ANDROID_TEST_ORCHESTRATOR
      val runContentDescriptor = executor.run(EmptyProgressIndicator())
      Truth.assertThat((runContentDescriptor.processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo(
        ORCHESTRATOR_APP_ID)
    }

    run {
      executionOptions = TestExecutionOption.ANDROIDX_TEST_ORCHESTRATOR
      val runContentDescriptor = executor.run(EmptyProgressIndicator())
      Truth.assertThat((runContentDescriptor.processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo(
        ANDROIDX_ORCHESTRATOR_APP_ID)
    }
    if (!historyLatch.await(60, TimeUnit.SECONDS)) {
      fail("History is not saved")
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
    return executionEnvironment
  }
}