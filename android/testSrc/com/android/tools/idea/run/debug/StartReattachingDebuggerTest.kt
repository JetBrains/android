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
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.ClientImpl
import com.android.flags.junit.SetFlagRule
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.any
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.AndroidRemoteDebugProcessHandler
import com.android.tools.idea.run.configuration.execution.RunnableClientsService
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerServiceInstance
import com.intellij.xdebugger.XDebuggerManager
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger


class StartReattachingDebuggerTest {

  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val setFlagRule = SetFlagRule(StudioFlags.NEW_EXECUTION_FLOW_FOR_JAVA_DEBUGGER, true)

  val project
    get() = projectRule.project

  private val APP_ID = "com.android.example"
  private val MASTER_PROCESS_NAME = "com.master.test"

  private lateinit var runnableClientsService: RunnableClientsService
  private lateinit var executionEnvironment: ExecutionEnvironment
  private lateinit var mockDevice: IDevice

  @Before
  fun setUp() {
    runnableClientsService = RunnableClientsService(project)
    executionEnvironment = createFakeExecutionEnvironment(project, "myTestConfiguration")
    mockDevice = createDevice()
  }

  private fun createDevice(): IDevice {
    val mockDevice = Mockito.mock(IDevice::class.java)
    `when`(mockDevice.version).thenReturn(AndroidVersion(26))
    `when`(mockDevice.isOnline).thenReturn(true)
    return mockDevice
  }

  @After
  fun tearDown() {
    XDebuggerManager.getInstance(project).debugSessions.forEach {
      it.stop()
    }
    runnableClientsService.stop()
  }

  @Test
  fun testStartReattachingDebuggerForOneClient() {
    runnableClientsService.startClient(mockDevice, APP_ID)
    val firstSession = startJavaReattachingDebugger(project, mockDevice, MASTER_PROCESS_NAME, setOf(APP_ID), executionEnvironment)
      .blockingGet(20, TimeUnit.SECONDS)
    assertThat(firstSession).isNotNull()
    assertThat(firstSession!!.sessionName).isEqualTo("myTestConfiguration")
    assertThat(firstSession.debugProcess.processHandler).isInstanceOf(AndroidRemoteDebugProcessHandler::class.java)
  }

  @Test
  fun testStartReattachingDebuggerForFewClients() {

    val ADDITIONAL_CLIENTS = 2
    // RunContentManagerImpl.showRunContent content does nothing on showRunContent in Unit tests, we want to check it was invoked.
    val runContentManagerImplMock = Mockito.mock(RunContentManager::class.java)

    project.registerServiceInstance(RunContentManager::class.java, runContentManagerImplMock)

    runnableClientsService.startClient(mockDevice, APP_ID)

    startJavaReattachingDebugger(project, mockDevice, MASTER_PROCESS_NAME, setOf(APP_ID), executionEnvironment).blockingGet(20,
                                                                                                                            TimeUnit.SECONDS)
    val tabsOpened = AtomicInteger(0)
    repeat(ADDITIONAL_CLIENTS) {
      runnableClientsService.stopClient(mockDevice, APP_ID)
      val latchStartDebug = CountDownLatch(1)
      val additionalRunnableClient = runnableClientsService.startClient(mockDevice, APP_ID)
      AndroidDebugBridge.clientChanged(additionalRunnableClient as ClientImpl, Client.CHANGE_DEBUGGER_STATUS)
      `when`(runContentManagerImplMock.showRunContent(any(), any())).thenAnswer {
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
    runnableClientsService.startClient(mockDevice, MASTER_PROCESS_NAME)
    runnableClientsService.startClient(mockDevice, APP_ID)

    val latch = CountDownLatch(2)
    val masterIsRunning = AtomicBoolean(true)
    val appIsRunning = AtomicBoolean(true)

    `when`(mockDevice.forceStop(APP_ID)).thenAnswer {
      if (appIsRunning.getAndSet(false)) {
        latch.countDown()
      }
    }

    `when`(mockDevice.forceStop(MASTER_PROCESS_NAME)).thenAnswer {
      if (masterIsRunning.getAndSet(false)) {
        latch.countDown()
      }
    }

    val sessionImpl = startJavaReattachingDebugger(project, mockDevice, MASTER_PROCESS_NAME, setOf(APP_ID),
                                                   executionEnvironment).blockingGet(20, TimeUnit.SECONDS)

    // when we stop for debug, master process should be stopped too
    sessionImpl!!.debugProcess.processHandler.destroyProcess()
    sessionImpl.debugProcess.processHandler.waitFor()

    if (!latch.await(20, TimeUnit.SECONDS)) {
      fail("Processes are not stopped")
    }
  }
}