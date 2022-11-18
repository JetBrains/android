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
package com.android.tools.idea.run.debug

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.services.ServiceOutput
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.processhandler.AndroidRemoteDebugProcessHandler
import com.android.tools.idea.logcat.AndroidLogcatService
import com.android.tools.idea.run.configuration.execution.DebugSessionStarter
import com.android.tools.idea.run.editor.AndroidJavaDebugger
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.xdebugger.XDebuggerManager
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random


class StartReattachingDebuggerTest {

  private val APP_ID = FakeAdbTestRule.CLIENT_PACKAGE_NAME
  private val MASTER_PROCESS_NAME = "com.master.test"

  @get:Rule
  var fakeAdbRule: FakeAdbTestRule = FakeAdbTestRule()

  @get:Rule
  val projectRule = ProjectRule()

  val project
    get() = projectRule.project

  private lateinit var executionEnvironment: ExecutionEnvironment
  private lateinit var device: IDevice
  private lateinit var deviceState: DeviceState

  @Before
  fun setUp() {
    val emptyLogcatService = Mockito.mock(AndroidLogcatService::class.java)
    ApplicationManager.getApplication().replaceService(AndroidLogcatService::class.java, emptyLogcatService, project)

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
  fun testStartReattachingDebuggerForOneClient() {
    FakeAdbTestRule.launchAndWaitForProcess(deviceState, true)
    val firstSession = DebugSessionStarter.attachReattachingDebuggerToStartedProcess(
      device,
      APP_ID,
      MASTER_PROCESS_NAME,
      executionEnvironment,
      AndroidJavaDebugger(),
      AndroidJavaDebugger().createState(),
      destroyRunningProcess = { }).blockingGet(20, TimeUnit.SECONDS)

    assertThat(firstSession).isNotNull()
    assertThat(firstSession!!.sessionName).isEqualTo("myTestConfiguration")
    assertThat(firstSession.debugProcess.processHandler).isInstanceOf(
      AndroidRemoteDebugProcessHandler::class.java)
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
  fun testStartReattachingDebuggerForFewClients() {

    val ADDITIONAL_CLIENTS = 2
    // RunContentManagerImpl.showRunContent content does nothing on showRunContent in Unit tests, we want to check it was invoked.
    val runContentManagerImplMock = Mockito.mock(RunContentManager::class.java)

    project.registerServiceInstance(RunContentManager::class.java, runContentManagerImplMock)

    FakeAdbTestRule.launchAndWaitForProcess(deviceState, 1111, MASTER_PROCESS_NAME, false)

    var pid = Random.nextInt()
    FakeAdbTestRule.launchAndWaitForProcess(deviceState, pid, FakeAdbTestRule.CLIENT_PACKAGE_NAME, true)

    DebugSessionStarter.attachReattachingDebuggerToStartedProcess(
      device,
      APP_ID,
      MASTER_PROCESS_NAME,
      executionEnvironment,
      AndroidJavaDebugger(),
      AndroidJavaDebugger().createState(),
      destroyRunningProcess = { it.forceStop(APP_ID) }).blockingGet(20, TimeUnit.SECONDS)

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
  fun testStopping() {

    FakeAdbTestRule.launchAndWaitForProcess(deviceState, 1111, MASTER_PROCESS_NAME, false)
    FakeAdbTestRule.launchAndWaitForProcess(deviceState, true)

    val latch = CountDownLatch(1)

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")


      when (wholeCommand) {
        "force-stop $MASTER_PROCESS_NAME" -> latch.countDown()
        "force-stop $APP_ID" -> error("Should stop only master process")
      }
    }


    val sessionImpl = DebugSessionStarter.attachReattachingDebuggerToStartedProcess(
      device,
      APP_ID,
      MASTER_PROCESS_NAME,
      executionEnvironment,
      AndroidJavaDebugger(),
      AndroidJavaDebugger().createState(),
      destroyRunningProcess = { it.forceStop(APP_ID) }).blockingGet(20, TimeUnit.SECONDS)!!

    // when we stop for debug, master process should be stopped too
    sessionImpl.runContentDescriptor.processHandler!!.destroyProcess()
    sessionImpl.runContentDescriptor.processHandler!!.waitFor()

    if (!latch.await(20, TimeUnit.SECONDS)) {
      fail("Process is not stopped")
    }
  }
}