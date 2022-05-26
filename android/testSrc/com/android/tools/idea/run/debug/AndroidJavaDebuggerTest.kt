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
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.AndroidSessionInfo
import com.android.tools.idea.run.configuration.execution.RunnableClientsService
import com.android.tools.idea.run.editor.AndroidJavaDebugger
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerServiceInstance
import com.intellij.xdebugger.XDebuggerManager
import junit.framework.Assert.fail
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for [attachJavaDebuggerToClient], method will eventually replace all [AndroidJavaDebugger] code.
 *
 * See [StudioFlags.NEW_EXECUTION_FLOW_FOR_JAVA_DEBUGGER].
 */
class AndroidJavaDebuggerTest {
  private val APP_PACKAGE = "com.android.example"

  @get:Rule
  val projectRule = ProjectRule()

  val project
    get() = projectRule.project

  private lateinit var client: Client
  private lateinit var runnableClientsService: RunnableClientsService
  private lateinit var executionEnvironment: ExecutionEnvironment


  private fun createDevice(): IDevice {
    val mockDevice = Mockito.mock(IDevice::class.java)
    Mockito.`when`(mockDevice.version).thenReturn(AndroidVersion(26))
    Mockito.`when`(mockDevice.isOnline).thenReturn(true)
    return mockDevice
  }

  @Before
  fun setUp() {
    StudioFlags.NEW_EXECUTION_FLOW_FOR_JAVA_DEBUGGER.override(true)
    executionEnvironment = createFakeExecutionEnvironment(project, "myConfiguration")
    runnableClientsService = RunnableClientsService(project)
    val device = createDevice()
    client = runnableClientsService.startClient(device, APP_PACKAGE)
  }

  @After
  fun tearDown() {
    XDebuggerManager.getInstance(project).debugSessions.forEach {
      it.stop()
    }
    runnableClientsService.stop()
    StudioFlags.NEW_EXECUTION_FLOW_FOR_JAVA_DEBUGGER.clearOverride()
  }

  @Test
  fun testAllInformationForApplyChangesAndPositionManger() {
    val session = attachJavaDebuggerToClient(project, client, executionEnvironment, null,
                                             onDebugProcessDestroyed = { device -> device.forceStop(APP_PACKAGE) }).blockingGet(10,
                                                                                                                                TimeUnit.SECONDS)
    val processHandler = session!!.debugProcess.processHandler
    // For AndroidPositionManager.
    assertThat(processHandler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)).isEqualTo(AndroidVersion(26))
    // For Apply Changes.
    val sessionInfo = processHandler.getUserData(AndroidSessionInfo.KEY)!!
    assertThat(sessionInfo.processHandler).isEqualTo(processHandler)
    assertThat(sessionInfo.executor).isEqualTo(executionEnvironment.executor)
  }

  @Test
  fun testSessionCreated() {
    val session = attachJavaDebuggerToClient(project, client, executionEnvironment, null,
                                             onDebugProcessDestroyed = { device -> device.forceStop(APP_PACKAGE) }).blockingGet(10,
                                                                                                                                TimeUnit.SECONDS)
    assertThat(session).isNotNull()
    assertThat(session!!.sessionName).isEqualTo("myConfiguration")
  }

  @Test
  fun testOnDebugProcessStartedCallback() {
    var callbackCount = 0
    val onDebugProcessStarted: () -> Unit = {
      callbackCount++
    }

    val session = attachJavaDebuggerToClient(project, client, executionEnvironment,
                                             onDebugProcessStarted = onDebugProcessStarted,
                                             onDebugProcessDestroyed = { device -> device.forceStop(APP_PACKAGE) }).blockingGet(10,
                                                                                                                                TimeUnit.SECONDS)
    assertThat(session).isNotNull()
    assertThat(callbackCount).isEqualTo(1)
  }

  @Test
  fun testOnDebugProcessDestroyCallback() {
    val countDownLatch = CountDownLatch(1)
    val session = attachJavaDebuggerToClient(project, client, executionEnvironment,
                                             onDebugProcessDestroyed = { countDownLatch.countDown() }).blockingGet(10, TimeUnit.SECONDS)!!
    session.debugProcess.processHandler.destroyProcess()
    session.debugProcess.processHandler.waitFor()
    if (!countDownLatch.await(10, TimeUnit.SECONDS)) {
      fail("Callback wasn't called")
    }
  }

  @Test
  fun testSessionName() {
    val session = attachJavaDebuggerToClientAndShowTab(project, client).blockingGet(10, TimeUnit.SECONDS)
    assertThat(session).isNotNull()
    assertThat(client.debuggerListenPort).isAtLeast(0)
    assertThat(client.clientData.pid).isAtLeast(0)
    assertThat(session!!.sessionName).isEqualTo(
      "Android Debugger (pid: ${client.clientData.pid}, debug port: ${client.debuggerListenPort})")
  }

  @Test
  fun testCatchError() {
    val debuggerManagerExMock = Mockito.mock(DebuggerManagerEx::class.java)
    project.registerServiceInstance(DebuggerManager::class.java, debuggerManagerExMock)
    Mockito.`when`(debuggerManagerExMock.attachVirtualMachine(any())).thenThrow(
      ExecutionException("Test execution exception in test testCatchError"))

    try {
      attachJavaDebuggerToClientAndShowTab(project, client).blockingGet(30, TimeUnit.SECONDS)
      fail()
    }
    catch (e: Throwable) {
      /**
       * [e] is expected to be [java.util.concurrent.ExecutionException] for production code and
       * [com.intellij.testFramework.TestLogger.TestLoggerAssertionError] for Unit tests.
       **/
      assertThat(e.cause).isInstanceOf(ExecutionException::class.java)
      assertThat(e.cause!!.message).isEqualTo("Test execution exception in test testCatchError")
    }
  }

  @Test
  fun testKillAppOnDestroy() {
    val mockDevice = client.device

    val countDownLatch = CountDownLatch(1)
    Mockito.`when`(mockDevice.forceStop(any())).then {
      countDownLatch.countDown()
    }
    val session = attachJavaDebuggerToClient(project, client, executionEnvironment,
                                             onDebugProcessDestroyed = { device -> device.forceStop(APP_PACKAGE) }).blockingGet(10,
                                                                                                                                TimeUnit.SECONDS)!!
    session.debugProcess.processHandler.destroyProcess()
    session.debugProcess.processHandler.waitFor()
    if (!countDownLatch.await(10, TimeUnit.SECONDS)) {
      fail("Process wasn't killed")
    }
    Mockito.verify(mockDevice).forceStop(eq("com.android.example"))
  }

  @Test
  fun testVMExitedNotifierIsInvokedOnDetach() {
    val session = attachJavaDebuggerToClient(project, client, executionEnvironment,
                                             onDebugProcessDestroyed = { device -> device.forceStop(APP_PACKAGE) }).blockingGet(10,
                                                                                                                                TimeUnit.SECONDS)!!

    session.debugProcess.processHandler.detachProcess()
    session.debugProcess.processHandler.waitFor()
    Mockito.verify(client).notifyVmMirrorExited()
  }
}