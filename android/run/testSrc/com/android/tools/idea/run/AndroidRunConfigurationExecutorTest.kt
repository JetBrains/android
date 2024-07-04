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

import com.android.adblib.ddmlibcompatibility.AdbLibIDeviceManagerFactory
import com.android.adblib.ddmlibcompatibility.testutils.createAdbSession
import com.android.adblib.testingutils.CloseablesRule
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.idevicemanager.IDeviceManagerFactory
import com.android.ddmlib.internal.DeviceImpl
import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.flags.junit.FlagRule
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoCleanerRule
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.whenever
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.deployer.Deployer
import com.android.tools.deployer.DeployerException
import com.android.tools.idea.backup.BackupManager
import com.android.tools.idea.editors.liveedit.LiveEditService
import com.android.tools.idea.editors.liveedit.LiveEditServiceImpl
import com.android.tools.idea.execution.common.AndroidExecutionException
import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.ApplicationDeployer
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.execution.common.assertTaskPresentedInStats
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.execution.common.processhandler.AndroidRemoteDebugProcessHandler
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.execution.common.stats.RunStatsService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.projectsystem.applicationProjectContextForTests
import com.android.tools.idea.run.AndroidRunConfiguration.Companion.DO_NOTHING
import com.android.tools.idea.run.activity.launch.EmptyTestConsoleView
import com.android.tools.idea.run.configuration.execution.createApp
import com.android.tools.idea.run.deployment.liveedit.LiveEditApp
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ProjectServiceRule
import com.android.tools.idea.testing.executeMakeBeforeRunStepInTest
import com.android.tools.idea.testing.flags.override
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.content.Content
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail

/**
 * Unit test for [AndroidRunConfigurationExecutor].
 */
class AndroidRunConfigurationExecutorTest {
  companion object {
    val APPLICATION_ID = "google.simpleapplication"
    val ACTIVITY_NAME = "google.simpleapplication.MyActivity"
  }

  val fakeAdb: FakeAdbTestRule = FakeAdbTestRule().withIDeviceManagerFactoryFactory { iDeviceManagerFactoryFactory() }

  val projectRule = AndroidProjectRule.testProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

  val cleaner = MockitoCleanerRule()

  val closeables = CloseablesRule()

  val usageTrackerRule = UsageTrackerRule()

  @get:Rule
  val chain = RuleChain.outerRule(cleaner)
    .around(closeables)
    .around(usageTrackerRule)
    .around(projectRule)
    .around(fakeAdb)
    .around(FlagRule(StudioFlags.BACKUP_ENABLED, true))

  private val mockBackupManager = mock<BackupManager>()
  private val iDeviceManagerFactoryFactory: () -> IDeviceManagerFactory = {
    val adbSession = fakeAdb.createAdbSession(closeables)
    AdbLibIDeviceManagerFactory(adbSession)
  }

  @Before
  fun setUp() {
    projectRule.project.replaceService(BackupManager::class.java, mockBackupManager, projectRule.testRootDisposable)
  }

  @Test
  fun runSucceeded() {
    val deviceState = fakeAdb.connectAndWaitForDevice()
    val latch = CountDownLatch(1)
    deviceState.setActivityManager { args, _ ->
      val command = args.joinToString(" ")
      if (command == "start -n google.simpleapplication/google.simpleapplication.MyActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER") {
        deviceState.startClient(1234, 1235, APPLICATION_ID, false)
        latch.countDown()
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))

    val stats = RunStatsService.get(projectRule.project).create()
    val env = getExecutionEnvironment(listOf(device)).apply {
      putUserData(RunStats.KEY, stats)
    }
    val configuration = env.runProfile as AndroidRunConfiguration
    configuration.CLEAR_APP_STORAGE = true
    configuration.CLEAR_LOGCAT = true
    configuration.RESTORE_ENABLED = true
    configuration.RESTORE_FILE = "foo.backup"
    configuration.executeMakeBeforeRunStepInTest(device)


    var logcatCleared = false
    projectRule.project.messageBus.connect(projectRule.testRootDisposable)
      .subscribe(ClearLogcatListener.TOPIC, ClearLogcatListener { logcatCleared = true })

    val runner = AndroidRunConfigurationExecutor(
      configuration.applicationIdProvider!!,
      configuration.applicationProjectContextForTests,
      env,
      deviceFutures,
      configuration.apkProvider!!,
      applicationDeployer = testApplicationDeployer(device, ApplicationDeployer::fullDeploy.name)
    )

    val runContentDescriptor = ProgressManager.getInstance()
      .runProcess(Computable { runner.run(ProgressManager.getInstance().progressIndicator) }, EmptyProgressIndicator())

    stats.success()
    assertTaskPresentedInStats(usageTrackerRule.usages, "CLEAR_APP_STORAGE_TASK")
    assertTaskPresentedInStats(usageTrackerRule.usages, "DEFAULT_ACTIVITY")

    val processHandler = runContentDescriptor.processHandler!!
    processHandler.startNotify()

    assertThat(logcatCleared).isTrue() // comes from [com.android.tools.idea.run.tasks.ClearAppStorageTaskKt.clearAppStorage]
    assertThat(deviceState.pmLogs).contains("list packages google.simpleapplication")
    assertThat(processHandler).isInstanceOf(AndroidProcessHandler::class.java)
    assertThat((processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo(APPLICATION_ID)
    assertThat(processHandler.autoTerminate).isEqualTo(true)
    assertThat(processHandler.isAssociated(device)).isEqualTo(true)
    assertThat(AndroidSessionInfo.from(processHandler)).isNotNull()
    runBlocking {
      verify(mockBackupManager).restore("test_device_001", Path.of("foo.backup"), null, false)
    }

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
      if (command == "start -n google.simpleapplication/google.simpleapplication.MyActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -D") {
        deviceState.startClient(1234, 1235, APPLICATION_ID, true)
        startInvocation++
      }
    }
    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))

    val stats = RunStatsService.get(projectRule.project).create()
    val env = getExecutionEnvironment(listOf(device), isDebug = true).apply {
      putUserData(RunStats.KEY, stats)
    }
    val configuration = env.runProfile as AndroidRunConfiguration
    configuration.executeMakeBeforeRunStepInTest(device)
    configuration.setLaunchActivity(ACTIVITY_NAME)
    configuration.RESTORE_ENABLED = true
    configuration.RESTORE_FILE = "foo.backup"

    val runner = AndroidRunConfigurationExecutor(
      configuration.applicationIdProvider!!,
      configuration.applicationProjectContextForTests,
      env,
      deviceFutures,
      configuration.apkProvider!!,
      applicationDeployer = testApplicationDeployer(device, ApplicationDeployer::fullDeploy.name)
    )

    val processHandler = (ProgressManager.getInstance()
      .runProcess(
        Computable { runner.debug(ProgressManager.getInstance().progressIndicator) },
        EmptyProgressIndicator()
      )).processHandler as AndroidRemoteDebugProcessHandler

    stats.success()
    assertTaskPresentedInStats(usageTrackerRule.usages, "waitForProcessTermination")
    assertTaskPresentedInStats(usageTrackerRule.usages, "SPECIFIC_ACTIVITY")
    assertTaskPresentedInStats(usageTrackerRule.usages, "startDebuggerSession")
    runBlocking {
      verify(mockBackupManager).restore("test_device_001", Path.of("foo.backup"), null, false)
    }

    assertThat(!processHandler.isProcessTerminating || !processHandler.isProcessTerminated).isTrue()
    deviceState.stopClient(1234)
    if (!processHandler.waitFor(5000)) {
      fail("Process handler didn't stop when debug process terminated")
    }
    assertThat(startInvocation).isEqualTo(1)
  }

  @Test
  fun applyChangesSucceeded() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))
    val env = getExecutionEnvironment(listOf(device))
    val runningDescriptor = setSwapInfo(env, device)
    val runningProcessHandler = runningDescriptor.processHandler as AndroidProcessHandler

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

    val configuration = env.runProfile as AndroidRunConfiguration
    configuration.executeMakeBeforeRunStepInTest(device)

    val runner = AndroidRunConfigurationExecutor(
      configuration.applicationIdProvider!!,
      configuration.applicationProjectContextForTests,
      env,
      deviceFutures,
      configuration.apkProvider!!,
      liveEditService,
      testApplicationDeployer(device, ApplicationDeployer::applyChangesDeploy.name)
    )

    val runContentDescriptor = ProgressManager.getInstance()
      .runProcess(Computable { runner.applyChanges(ProgressManager.getInstance().progressIndicator) }, EmptyProgressIndicator())
    assertThat(runContentDescriptor.isHiddenContent).isEqualTo(true)
    assertThat(liveEditServiceNotified).isEqualTo(false) // Live Edit doesn't need to know if AC was performed.

    val processHandler = runContentDescriptor.processHandler

    assertThat(processHandler).isEqualTo(runningProcessHandler)
    assertThat(runContentDescriptor.executionConsole).isEqualTo(runningDescriptor.executionConsole)
    val printedMessage = (runContentDescriptor.executionConsole as EmptyTestConsoleView).printedMessages.map { it.first }.first()
    assertThat(printedMessage).endsWith("Applying changes to app on 'TestTarget'.\n")
    assertThat((processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo(APPLICATION_ID)
    assertThat(processHandler.autoTerminate).isEqualTo(true)
    assertThat(processHandler.isAssociated(device)).isEqualTo(true)
    assertThat(processHandler.isProcessTerminated).isEqualTo(false)
    assertThat(processHandler.isProcessTerminating).isEqualTo(false)
  }

  @Test
  fun applyCodeChangesSucceeded() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))
    val env = getExecutionEnvironment(listOf(device))
    val runningDescriptor = setSwapInfo(env, device)
    val runningProcessHandler = runningDescriptor.processHandler as AndroidProcessHandler
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

    val configuration = env.runProfile as AndroidRunConfiguration
    configuration.executeMakeBeforeRunStepInTest(device)

    val runner = AndroidRunConfigurationExecutor(
      configuration.applicationIdProvider!!,
      configuration.applicationProjectContextForTests,
      env,
      deviceFutures,
      configuration.apkProvider!!,
      liveEditService,
      applicationDeployer = testApplicationDeployer(device, ApplicationDeployer::applyCodeChangesDeploy.name)
    )

    val runContentDescriptor =
      ProgressManager.getInstance().runProcess(Computable { runner.applyCodeChanges(ProgressManager.getInstance().progressIndicator) }, EmptyProgressIndicator())

    assertThat(runContentDescriptor.isHiddenContent).isEqualTo(true)
    assertThat(liveEditServiceNotified).isEqualTo(false) // Live Edit doesn't need to know if AC was performed.

    val processHandler = runContentDescriptor.processHandler

    assertThat(processHandler).isEqualTo(runningProcessHandler)
    assertThat(runContentDescriptor.executionConsole).isEqualTo(runningDescriptor.executionConsole)
    val printedMessage = (runContentDescriptor.executionConsole as EmptyTestConsoleView).printedMessages.map { it.first }.first()
    assertThat(printedMessage).endsWith("Applying code changes to app on 'TestTarget'.\n")
    assertThat((processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo(APPLICATION_ID)
    assertThat(processHandler.autoTerminate).isEqualTo(true)
    assertThat(processHandler.isAssociated(device)).isEqualTo(true)
    assertThat(processHandler.isProcessTerminated).isEqualTo(false)
    assertThat(processHandler.isProcessTerminating).isEqualTo(false)
  }

  @Test
  fun runFailedApkProvisionException() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))
    val env = getExecutionEnvironment(listOf(device))
    val configuration = env.runProfile as AndroidRunConfiguration
    configuration.executeMakeBeforeRunStepInTest(device)

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

    val runner = AndroidRunConfigurationExecutor(
      configuration.applicationIdProvider!!,
      configuration.applicationProjectContextForTests,
      env,
      deviceFutures,
      apkProvider = { throw ApkProvisionException("ApkProvisionException") },
      liveEditService
      )

    val thrown = assertThrows(ExecutionException::class.java) {
      ProgressManager.getInstance()
        .runProcess(Computable { runner.run(ProgressManager.getInstance().progressIndicator) }, EmptyProgressIndicator())
    }
    assertThat(thrown).hasMessageThat().contains("ApkProvisionException")
    assertThat(liveEditServiceNotified).isEqualTo(false)
  }

  @Test
  fun runGetApplicationIdException() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))
    val env = getExecutionEnvironment(listOf(device))
    val configuration = env.runProfile as AndroidRunConfiguration
    configuration.executeMakeBeforeRunStepInTest(device)

    val runner = AndroidRunConfigurationExecutor(
      applicationIdProvider = object : ApplicationIdProvider{
        override fun getPackageName(): String {
          throw ApkProvisionException("AndroidExecutionException packageName")
        }

        override fun getTestPackageName(): String? {
          throw ApkProvisionException("AndroidExecutionException testPackageName")
        }
      },
      applicationContext = object: ApplicationProjectContext {
        override val applicationId: String
          get() = error("Not supposed to be invoked")
      },
      env,
      deviceFutures,
      apkProvider = { throw ApkProvisionException("ApkProvisionException") })

    val thrown = assertThrows(AndroidExecutionException::class.java) {
      ProgressManager.getInstance()
        .runProcess(Computable { runner.run(ProgressManager.getInstance().progressIndicator) }, EmptyProgressIndicator())
    }
    assertThat(thrown).hasMessageThat().contains("AndroidExecutionException packageName")
  }

  @Test
  fun runNoLaunchOptions() {
    val deviceState = fakeAdb.connectAndWaitForDevice()
    val latch = CountDownLatch(1)
    deviceState.setActivityManager { args, _ ->
      val command = args.joinToString(" ")
      if (command == "start -n google.simpleapplication/google.simpleapplication.MyActivity" +
        " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER") {
        deviceState.startClient(1234, 1235, APPLICATION_ID, false)
        latch.countDown()
      }
    }
    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))

    val stats = RunStatsService.get(projectRule.project).create()
    val env = getExecutionEnvironment(listOf(device)).apply {
      putUserData(RunStats.KEY, stats)
    }
    val configuration = env.runProfile as AndroidRunConfiguration
    configuration.CLEAR_APP_STORAGE = true
    configuration.CLEAR_LOGCAT = true
    configuration.MODE = DO_NOTHING
    configuration.executeMakeBeforeRunStepInTest(device)

    var logcatCleared = false
    projectRule.project.messageBus.connect(projectRule.testRootDisposable)
      .subscribe(ClearLogcatListener.TOPIC, ClearLogcatListener { logcatCleared = true })

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

    val runner = AndroidRunConfigurationExecutor(
      configuration.applicationIdProvider!!,
      configuration.applicationProjectContextForTests,
      env,
      deviceFutures,
      configuration.apkProvider!!,
      applicationDeployer = testApplicationDeployer(device, ApplicationDeployer::fullDeploy.name),
      liveEditService = liveEditService
    )

    val runContentDescriptor = ProgressManager.getInstance()
      .runProcess(Computable { runner.run(ProgressManager.getInstance().progressIndicator) }, EmptyProgressIndicator())

    stats.success()
    assertTaskPresentedInStats(usageTrackerRule.usages, "CLEAR_APP_STORAGE_TASK")
    assertTaskPresentedInStats(usageTrackerRule.usages, "NO_LAUNCH")

    val processHandler = runContentDescriptor.processHandler!!
    processHandler.startNotify()

    assertThat(logcatCleared).isTrue() // comes from [com.android.tools.idea.run.tasks.ClearAppStorageTaskKt.clearAppStorage]
    assertThat(deviceState.pmLogs).contains("list packages google.simpleapplication")
    assertThat(processHandler).isInstanceOf(AndroidProcessHandler::class.java)
    assertThat((processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo(APPLICATION_ID)
    assertThat(processHandler.autoTerminate).isEqualTo(true)
    assertThat(processHandler.isAssociated(device)).isEqualTo(true)
    assertThat(AndroidSessionInfo.from(processHandler)).isNotNull()
    assertThat(liveEditServiceNotified).isEqualTo(false) // Live Edit doesn't need to know if AC was performed.

    deviceState.stopClient(1234) // TODO: flaky test b/273744887
    //if (!processHandler.waitFor(5000)) {
    //  fail("Process handler didn't stop when debug process terminated")
    //}
    processHandler.destroyProcess()
  }

  @Test
  fun runFailedDeployException() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))
    val env = getExecutionEnvironment(listOf(device))

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

    val configuration = env.runProfile as AndroidRunConfiguration
    configuration.executeMakeBeforeRunStepInTest(device)

    val runner = AndroidRunConfigurationExecutor(
      configuration.applicationIdProvider!!,
      configuration.applicationProjectContextForTests,
      env,
      deviceFutures,
      configuration.apkProvider!!,
      liveEditService,
      applicationDeployer = object : ApplicationDeployer {
        override fun fullDeploy(
          device: IDevice,
          app: ApkInfo,
          deployOptions: DeployOptions,
          indicator: ProgressIndicator
        ): Deployer.Result {
          throw DeployerException.pmFlagsNotSupported()
        }

        override fun applyChangesDeploy(
          device: IDevice,
          app: ApkInfo,
          deployOptions: DeployOptions,
          indicator: ProgressIndicator
        ): Deployer.Result {
          throw DeployerException.pmFlagsNotSupported()
        }

        override fun applyCodeChangesDeploy(
          device: IDevice,
          app: ApkInfo,
          deployOptions: DeployOptions,
          indicator: ProgressIndicator
        ): Deployer.Result {
          throw DeployerException.pmFlagsNotSupported()
        }
      }
    )
    val thrown = assertThrows(ExecutionException::class.java) {
      ProgressManager.getInstance()
        .runProcess(Computable { runner.run(ProgressManager.getInstance().progressIndicator) }, EmptyProgressIndicator())
    }
    assertThat(thrown).hasMessageThat().contains(DeployerException.pmFlagsNotSupported().message)
    assertThat(liveEditServiceNotified).isEqualTo(false)
  }

  @Test
  fun swapRunFailedButProcessHandlerShouldNotBeDetached() {
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))
    val env = getExecutionEnvironment(listOf(device))
    val configuration = env.runProfile as AndroidRunConfiguration
    configuration.executeMakeBeforeRunStepInTest(device)
    val runningDescriptor = setSwapInfo(env, device)
    val runningProcessHandler = runningDescriptor.processHandler as AndroidProcessHandler
    runningProcessHandler.addTargetDevice(device)
    val runner = AndroidRunConfigurationExecutor(
      configuration.applicationIdProvider!!,
      configuration.applicationProjectContextForTests,
      env, deviceFutures, { throw ApkProvisionException("Exception") })

    assertThrows(ExecutionException::class.java) {
      ProgressManager.getInstance()
        .runProcess(Computable { runner.applyChanges(ProgressManager.getInstance().progressIndicator) }, EmptyProgressIndicator())
    }

    assertThat(runningProcessHandler.isAssociated(device)).isEqualTo(true)
    assertThat(runningProcessHandler.isProcessTerminated).isEqualTo(false)
    assertThat(runningProcessHandler.isProcessTerminating).isEqualTo(false)
  }

  @Test
  fun applyChangesNeedsRestart() {
    val deviceState = fakeAdb.connectAndWaitForDevice()
    val restartHappened = CountDownLatch(1)
    deviceState.setActivityManager { args, _ ->
      val command = args.joinToString(" ")
      if (command == "start -n google.simpleapplication/google.simpleapplication.MyActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER") {
        deviceState.startClient(1234, 1235, APPLICATION_ID, false)
        restartHappened.countDown()
      }
    }
    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))

    val env = getExecutionEnvironment(listOf(device))
    val configuration = env.runProfile as AndroidRunConfiguration
    configuration.executeMakeBeforeRunStepInTest(device)
    val runningDescriptor = setSwapInfo(env, device)
    val runningProcessHandler = runningDescriptor.processHandler as AndroidProcessHandler
    runningProcessHandler.addTargetDevice(device)


    val result =
      Deployer.Result(false, /*needsRestart */ true, false, createApp(device, APPLICATION_ID, activitiesName = listOf(ACTIVITY_NAME)))
    val applicationDeployer = testApplicationDeployer(device, ApplicationDeployer::applyChangesDeploy.name, result)

    val runner = AndroidRunConfigurationExecutor(
      configuration.applicationIdProvider!!,
      configuration.applicationProjectContextForTests,
      env,
      deviceFutures,
      configuration.apkProvider!!,
      applicationDeployer = applicationDeployer
    )

    val newProcessHandler = ProgressManager.getInstance()
      .runProcess(Computable { runner.applyChanges(ProgressManager.getInstance().progressIndicator) }, EmptyProgressIndicator()).processHandler

    if (!restartHappened.await(10, TimeUnit.SECONDS)) {
      fail("Activity is not restarted")
    }

    // New process handler should be created if we restarted Activity
    assertThat(newProcessHandler).isNotEqualTo(runningProcessHandler)
    assertThat((newProcessHandler as AndroidProcessHandler).isAssociated(device)).isEqualTo(true)
  }

  @Test
  fun applyCodeChangesNeedsRestartForDebug() {

    //TODO: write handler in fakeAdb for "am capabilities --protobuf"
    StudioFlags.DEBUG_ATTEMPT_SUSPENDED_START.override(false, projectRule.testRootDisposable)

    val deviceState = fakeAdb.connectAndWaitForDevice()
    val restartHappened = CountDownLatch(1)
    deviceState.setActivityManager { args, output ->
      val command = args.joinToString(" ")
      if (command == "start -n google.simpleapplication/google.simpleapplication.MyActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -D") {
        deviceState.startClient(1234, 1235, APPLICATION_ID, true)
        restartHappened.countDown()
      }
    }
    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))

    val env = getExecutionEnvironment(listOf(device))
    val configuration = env.runProfile as AndroidRunConfiguration
    configuration.executeMakeBeforeRunStepInTest(device)
    val runningDescriptor = setSwapInfo(env, device)
    val runningProcessHandler = runningDescriptor.processHandler as AndroidProcessHandler
    runningProcessHandler.addTargetDevice(device)


    val result =
      Deployer.Result(false, /*needsRestart */ true, false, createApp(device, APPLICATION_ID, activitiesName = listOf(ACTIVITY_NAME)))
    val applicationDeployer = testApplicationDeployer(device, ApplicationDeployer::applyCodeChangesDeploy.name, result)

    val runner = AndroidRunConfigurationExecutor(
      configuration.applicationIdProvider!!,
      configuration.applicationProjectContextForTests,
      env,
      deviceFutures,
      configuration.apkProvider!!,
      applicationDeployer = applicationDeployer
    )

    val newProcessHandler =
      ProgressManager.getInstance()
        .runProcess(Computable { runner.applyCodeChanges(ProgressManager.getInstance().progressIndicator) }, EmptyProgressIndicator()).processHandler

    if (!restartHappened.await(10, TimeUnit.SECONDS)) {
      fail("Activity is not restarted")
    }

    // New process handler should be created if we restarted Activity
    assertThat(newProcessHandler).isNotEqualTo(runningProcessHandler)
    assertThat(newProcessHandler!!).isInstanceOf(AndroidRemoteDebugProcessHandler::class.java)

    deviceState.stopClient(1234)
    if (!newProcessHandler.waitFor(5000)) {
      fail("Process handler didn't stop when debug process terminated")
    }
  }

  private fun testApplicationDeployer(
    device: IDevice,
    expectedMethod: String,
    result: Deployer.Result = Deployer.Result(
      false,
      false,
      false,
      createApp(device, APPLICATION_ID, activitiesName = listOf(ACTIVITY_NAME))
    )
  ) = object : ApplicationDeployer {
    override fun fullDeploy(
      deviceToInstall: IDevice, app: ApkInfo, deployOptions: DeployOptions, indicator: ProgressIndicator
    ): Deployer.Result {
      if (expectedMethod != ::fullDeploy.name) {
        throw RuntimeException("Method invocation is not expected")
      }
      if (deviceToInstall == device) {
        return result
      }
      throw RuntimeException("Unexpected device")
    }

    override fun applyChangesDeploy(
      deviceToInstall: IDevice, app: ApkInfo, deployOptions: DeployOptions, indicator: ProgressIndicator
    ): Deployer.Result {
      if (expectedMethod != ::applyChangesDeploy.name) {
        throw RuntimeException("Method invocation is not expected")
      }
      if (deviceToInstall == device) {
        return result
      }
      throw RuntimeException("Unexpected device")
    }

    override fun applyCodeChangesDeploy(
      deviceToInstall: IDevice, app: ApkInfo, deployOptions: DeployOptions, indicator: ProgressIndicator
    ): Deployer.Result {
      if (expectedMethod != ::applyCodeChangesDeploy.name) {
        throw RuntimeException("Method invocation is not expected")
      }
      if (deviceToInstall == device) {
        return result
      }
      throw RuntimeException("Unexpected device")
    }
  }


  private fun getExecutionEnvironment(
    devices: List<IDevice>, isDebug: Boolean = false
  ): ExecutionEnvironment {
    val configSettings = RunManager.getInstance(projectRule.project).allSettings.single { it.configuration is AndroidRunConfiguration }
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

  private fun setSwapInfo(env: ExecutionEnvironment, device: IDevice): RunContentDescriptor {
    val processHandlerForSwap = AndroidProcessHandler(APPLICATION_ID).apply { addTargetDevice(device) }
    processHandlerForSwap.startNotify()
    Disposer.register(projectRule.project) {
      processHandlerForSwap.detachProcess()
    }
    var runContentDescriptor: RunContentDescriptor? = null
    runInEdtAndWait {
      runContentDescriptor = showRunContent(DefaultExecutionResult(EmptyTestConsoleView(), processHandlerForSwap), env)!!.apply {
        setAttachedContent(mock(Content::class.java))
      }

      val mockRunContentManager = mock(RunContentManager::class.java)
      whenever(mockRunContentManager.findContentDescriptor(eq(env.executor), eq(processHandlerForSwap))).thenReturn(runContentDescriptor)
      projectRule.project.replaceService(RunContentManager::class.java, mockRunContentManager, projectRule.testRootDisposable)

      val mockExecutionManager = mock(ExecutionManagerImpl::class.java)
      whenever(mockExecutionManager.getRunningDescriptors(any())).thenReturn(listOf(runContentDescriptor!!))
      projectRule.project.replaceService(ExecutionManager::class.java, mockExecutionManager, projectRule.testRootDisposable)
    }
    AndroidSessionInfo.create(processHandlerForSwap, listOf(device), APPLICATION_ID)
    return runContentDescriptor!!
  }

  @Test
  fun runAPI33() {
    println("Starting runAPI33")
    val deviceState = fakeAdb.connectAndWaitForDevice()
    val latch = CountDownLatch(1)
    deviceState.setActivityManager { args, _ ->
      val command = args.joinToString(" ")
      if (command == "start -n google.simpleapplication/google.simpleapplication.MyActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --splashscreen-show-icon") {
        deviceState.startClient(1234, 1235, APPLICATION_ID, false)
        latch.countDown()
      }
    }

    val device = spy(AndroidDebugBridge.getBridge()!!.devices.single())
    Mockito.`when`(device.version).thenReturn(AndroidVersion(33))
    Mockito.`when`(device.forceStop(any())).thenThrow(RuntimeException("Should not kill"))

    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))

    val stats = RunStatsService.get(projectRule.project).create()
    val env = getExecutionEnvironment(listOf(device)).apply {
      putUserData(RunStats.KEY, stats)
    }
    val configuration = env.runProfile as AndroidRunConfiguration
    configuration.CLEAR_APP_STORAGE = true
    configuration.CLEAR_LOGCAT = true
    configuration.executeMakeBeforeRunStepInTest(device)

    var logcatCleared = false
    projectRule.project.messageBus.connect(projectRule.testRootDisposable)
      .subscribe(ClearLogcatListener.TOPIC, ClearLogcatListener { logcatCleared = true })

    val runner = AndroidRunConfigurationExecutor(
      configuration.applicationIdProvider!!,
      configuration.applicationProjectContextForTests,
      env,
      deviceFutures,
      configuration.apkProvider!!,
      applicationDeployer = testApplicationDeployer(device, ApplicationDeployer::fullDeploy.name)
    )

    val runContentDescriptor = ProgressManager.getInstance()
      .runProcess(Computable { runner.run(ProgressManager.getInstance().progressIndicator) }, EmptyProgressIndicator())

    stats.success()
    assertTaskPresentedInStats(usageTrackerRule.usages, "CLEAR_APP_STORAGE_TASK")
    assertTaskPresentedInStats(usageTrackerRule.usages, "DEFAULT_ACTIVITY")

    val processHandler = runContentDescriptor.processHandler!!
    processHandler.startNotify()

    assertThat(logcatCleared).isTrue() // comes from [com.android.tools.idea.run.tasks.ClearAppStorageTaskKt.clearAppStorage]
    assertThat(deviceState.pmLogs).contains("list packages google.simpleapplication")
    assertThat(processHandler).isInstanceOf(AndroidProcessHandler::class.java)
    assertThat((processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo(APPLICATION_ID)
    assertThat(processHandler.autoTerminate).isEqualTo(true)
    assertThat(processHandler.isAssociated(device)).isEqualTo(true)
    assertThat(AndroidSessionInfo.from(processHandler)).isNotNull()

    if (!latch.await(10, TimeUnit.SECONDS)) {
      fail("Activity is not started")
    }
    deviceState.stopClient(1234) // TODO: flaky test b/273744887
    //if (!processHandler.waitFor(5000)) {
    //  fail("Process handler didn't stop when debug process terminated")
    //}
    processHandler.destroyProcess()
    println("Finished runAPI33")
  }
}
