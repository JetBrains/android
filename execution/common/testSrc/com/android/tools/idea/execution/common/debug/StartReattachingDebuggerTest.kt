/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.execution.common.debug

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.services.ShellCommandOutput
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.execution.common.assertTaskPresentedInStats
import com.android.tools.idea.execution.common.debug.impl.java.AndroidJavaDebugger
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.execution.common.processhandler.AndroidRemoteDebugProcessHandler
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.execution.common.stats.RunStatsService
import com.android.tools.idea.projectsystem.TestApplicationProjectContext
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerServiceInstance
import com.intellij.xdebugger.XDebuggerManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

@Ignore("FakeAdbTestRule hangs")
class StartReattachingDebuggerTest {

  private val APP_ID = FakeAdbTestRule.CLIENT_PACKAGE_NAME
  private val MASTER_PROCESS_NAME = "com.master.test"

  @get:Rule(order = 0)
  val projectRule = ProjectRule()

  @get:Rule(order = 1)
  val fakeAdbRule: FakeAdbTestRule = FakeAdbTestRule()

  @get:Rule(order = 2)
  val debuggerThreadCleanupRule = DebuggerThreadCleanupRule { fakeAdbRule.server }

  @get:Rule
  val usageTrackerRule = UsageTrackerRule()

  val project
    get() = projectRule.project

  private lateinit var executionEnvironment: ExecutionEnvironment
  private lateinit var device: IDevice
  private lateinit var deviceState: DeviceState

  @Before
  fun setUp() {
    deviceState = fakeAdbRule.connectAndWaitForDevice()
    device = AndroidDebugBridge.getBridge()!!.devices.single()
    executionEnvironment = createFakeExecutionEnvironment(project, "myTestConfiguration")
  }

  @After
  fun tearDown() {
    XDebuggerManager.getInstance(project).debugSessions.forEach {
      it.stop()
    }
  }

  @Test
  fun testStartReattachingDebuggerForOneClient() = runTest {
    val stats = RunStatsService.get(project).create().also {
      executionEnvironment.putUserData(RunStats.KEY, it)
    }
    val masterProcessHandler = AndroidProcessHandler(MASTER_PROCESS_NAME, {})
    FakeAdbTestRule.launchAndWaitForProcess(deviceState, true)
    val firstSession = DebugSessionStarter.attachReattachingDebuggerToStartedProcess(
      device,
      TestApplicationProjectContext(APP_ID,),
      masterProcessHandler,
      executionEnvironment,
      AndroidJavaDebugger(),
      AndroidJavaDebugger().createState(),
      EmptyProgressIndicator()
    )

    assertThat(firstSession.sessionName).isEqualTo("myTestConfiguration")
    assertThat(firstSession.debugProcess.processHandler).isInstanceOf(
      AndroidRemoteDebugProcessHandler::class.java)
    stats.success()
    assertTaskPresentedInStats(usageTrackerRule.usages, "startReattachingDebuggerSession")
    // Clean up.
    // force close process monitor, as SingleDeviceAndroidProcessMonitor never connected and keep holding Project reference for 3 minutes
    masterProcessHandler.destroyProcess()
  }


  private fun waitForProcessToStop(pid: Int) {
    val latch = CountDownLatch(1)

    val deviceListener: IDeviceChangeListener = object : IDeviceChangeListener {
      override fun deviceConnected(device: IDevice) {}
      override fun deviceDisconnected(device: IDevice) {}
      override fun deviceChanged(changedDevice: IDevice, changeMask: Int) {
        if (changeMask and IDevice.CHANGE_CLIENT_LIST
          == IDevice.CHANGE_CLIENT_LIST) {
          latch.countDown()
        }
      }
    }
    AndroidDebugBridge.addDeviceChangeListener(deviceListener)
    deviceState.stopClient(pid)
    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
    AndroidDebugBridge.removeDeviceChangeListener(deviceListener)
  }

  @Test
  fun testStartReattachingDebuggerForFewClients() = runTest {

    val ADDITIONAL_CLIENTS = 2
    // RunContentManagerImpl.showRunContent content does nothing on showRunContent in Unit tests, we want to check it was invoked.
    val runContentManagerImplMock = Mockito.mock(RunContentManager::class.java)

    project.registerServiceInstance(RunContentManager::class.java, runContentManagerImplMock)
    val projectSystem = TestProjectSystem(project)
    projectSystem.useInTests()

    FakeAdbTestRule.launchAndWaitForProcess(deviceState, 1111, MASTER_PROCESS_NAME, false)

    var pid = Random.nextInt()
    FakeAdbTestRule.launchAndWaitForProcess(deviceState, pid, FakeAdbTestRule.CLIENT_PACKAGE_NAME, true)

    DebugSessionStarter.attachReattachingDebuggerToStartedProcess(
      device,
      TestApplicationProjectContext(APP_ID),
      MASTER_PROCESS_NAME,
      executionEnvironment,
      AndroidJavaDebugger(),
      AndroidJavaDebugger().createState(),
      destroyRunningProcess = { it.forceStop(APP_ID) }, EmptyProgressIndicator()
    )

    val tabsOpened = AtomicInteger(0)
    repeat(ADDITIONAL_CLIENTS) {
      waitForProcessToStop(pid)
      val latchStartDebug = CountDownLatch(1)
      pid = Random.nextInt()
      FakeAdbTestRule.launchAndWaitForProcess(deviceState, pid, FakeAdbTestRule.CLIENT_PACKAGE_NAME, true)
      whenever(runContentManagerImplMock.showRunContent(any(), any())).thenAnswer {
        tabsOpened.incrementAndGet()
        latchStartDebug.countDown()
      }
      if (!latchStartDebug.await(20, TimeUnit.SECONDS)) {
        fail("Session tab wasn't open for additional process")
      }
    }

    assertThat(tabsOpened.get()).isEqualTo(ADDITIONAL_CLIENTS)
  }

  @Test
  fun testStopping() = runTest {

    FakeAdbTestRule.launchAndWaitForProcess(deviceState, 1111, MASTER_PROCESS_NAME, false)
    FakeAdbTestRule.launchAndWaitForProcess(deviceState, true)

    val latch = CountDownLatch(1)

    deviceState.setActivityManager { args: List<String>, shellCommandOutput: ShellCommandOutput ->
      val wholeCommand = args.joinToString(" ")


      when (wholeCommand) {
        "force-stop $MASTER_PROCESS_NAME" -> latch.countDown()
        "force-stop $APP_ID" -> error("Should stop only master process")
      }
    }

    val sessionImpl = DebugSessionStarter.attachReattachingDebuggerToStartedProcess(
      device,
      TestApplicationProjectContext(APP_ID),
      MASTER_PROCESS_NAME,
      executionEnvironment,
      AndroidJavaDebugger(),
      AndroidJavaDebugger().createState(),
      destroyRunningProcess = {
        it.forceStop(APP_ID)
        it.forceStop(MASTER_PROCESS_NAME)
      },
      EmptyProgressIndicator()
    )

    // when we stop for debug, master process should be stopped too
    sessionImpl.runContentDescriptor.processHandler!!.destroyProcess()
    sessionImpl.runContentDescriptor.processHandler!!.waitFor()

    if (!latch.await(20, TimeUnit.SECONDS)) {
      fail("Process is not stopped")
    }
  }
}