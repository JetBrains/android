/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.tasks

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.fakeadbserver.DeviceState
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.logcat.AndroidLogcatService
import com.android.tools.idea.run.debug.createFakeExecutionEnvironment
import com.android.tools.idea.run.editor.AndroidDebuggerState
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
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random


/**
 * Unit tests for [ReattachingConnectDebuggerTask].
 */
class ReattachingConnectDebuggerTaskTest {

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
  fun testPerform() {
    val ADDITIONAL_CLIENTS = 2
    // RunContentManagerImpl.showRunContent content does nothing on showRunContent in Unit tests, we want to check it was invoked.
    val runContentManagerImplMock = Mockito.mock(RunContentManager::class.java)

    project.registerServiceInstance(RunContentManager::class.java, runContentManagerImplMock)

    FakeAdbTestRule.launchAndWaitForProcess(deviceState, 1111, MASTER_PROCESS_NAME, false)

    var pid = Random.nextInt()
    FakeAdbTestRule.launchAndWaitForProcess(deviceState, pid, FakeAdbTestRule.CLIENT_PACKAGE_NAME, true)

    val reattachingDebuggerTask = ReattachingConnectDebuggerTask(AndroidJavaDebugger(), AndroidDebuggerState(),
                                                                 MASTER_PROCESS_NAME, 15)

    val androidProcessHandler = AndroidProcessHandler(project, APP_ID)

    val firstStartDebugLatch = CountDownLatch(1)
    whenever(runContentManagerImplMock.showRunContent(any(), any())).thenAnswer {
      firstStartDebugLatch.countDown()
    }

    reattachingDebuggerTask.perform(device, APP_ID, executionEnvironment, androidProcessHandler)

    if (!firstStartDebugLatch.await(20, TimeUnit.SECONDS)) {
      Assert.fail("First session tab wasn't open")
    }

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
      if (!latchStartDebug.await(1, TimeUnit.MINUTES)) {
        Assert.fail("Session tab wasn't open for additional process")
      }
    }

    assertThat(tabsOpened.get()).isEqualTo(ADDITIONAL_CLIENTS)
  }

  private fun waitForProcessToStop(pid: Int) {
    val latch = CountDownLatch(1)

    val deviceListener: AndroidDebugBridge.IDeviceChangeListener = object : AndroidDebugBridge.IDeviceChangeListener {
      override fun deviceConnected(device: IDevice) {}
      override fun deviceDisconnected(device: IDevice) {}
      override fun deviceChanged(changedDevice: IDevice, changeMask: Int) {
        if (changeMask and IDevice.CHANGE_CLIENT_LIST == IDevice.CHANGE_CLIENT_LIST) {
          latch.countDown()
        }
      }
    }
    AndroidDebugBridge.addDeviceChangeListener(deviceListener)
    deviceState.stopClient(pid)
    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
    AndroidDebugBridge.removeDeviceChangeListener(deviceListener)
  }
}
