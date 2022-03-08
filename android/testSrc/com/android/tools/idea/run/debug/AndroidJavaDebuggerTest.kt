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
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.configuration.execution.RunnableClientsService
import com.android.tools.idea.run.editor.AndroidJavaDebugger
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.JavaCodeInsightTestCase
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.xdebugger.XDebuggerManager
import org.junit.Test
import org.mockito.Mockito

/**
 * Tests for [attachJavaDebuggerToClient], method will eventually replace all [AndroidJavaDebugger] code.
 *
 * See [StudioFlags.NEW_EXECUTION_FLOW_FOR_JAVA_DEBUGGER].
 */
class AndroidJavaDebuggerTest : JavaCodeInsightTestCase() {
  private val APP_PACKAGE = "com.android.example"
  private lateinit var client: Client
  private lateinit var runnableClientsService: RunnableClientsService
  private lateinit var executionEnvironment: ExecutionEnvironment
  override fun setUp() {
    super.setUp()
    StudioFlags.NEW_EXECUTION_FLOW_FOR_JAVA_DEBUGGER.override(true)
    executionEnvironment = createFakeExecutionEnvironment(project, "myConfiguration")
    runnableClientsService = RunnableClientsService(testRootDisposable)
    client = runnableClientsService.startClient(Mockito.mock(IDevice::class.java), APP_PACKAGE)
  }

  override fun tearDown() {
    XDebuggerManager.getInstance(project).debugSessions.forEach {
      it.stop()
    }
    runnableClientsService.stop()
    StudioFlags.NEW_EXECUTION_FLOW_FOR_JAVA_DEBUGGER.clearOverride()
    super.tearDown()
  }

  @Test
  fun test() {
    val session = attachJavaDebuggerToClient(myProject, client, executionEnvironment, null).blockingGet(1000)
    assertThat(session).isNotNull()
    assertThat(session!!.sessionName).isEqualTo("myConfiguration")
  }

  @Test
  fun testCallback() {
    var callbackCount = 0
    val onDebugProcessStarted: () -> Unit = {
      callbackCount++
    }
    val session = attachJavaDebuggerToClient(myProject, client, executionEnvironment,
                                             onDebugProcessStarted = onDebugProcessStarted).blockingGet(1000)
    assertThat(session).isNotNull()
    assertThat(callbackCount).isEqualTo(1)
  }

  @Test
  fun testSessionName() {
    val session = attachJavaDebuggerToClientAndShowTab(myProject, client).blockingGet(1000)
    assertThat(session).isNotNull()
    assertThat(client.debuggerListenPort).isAtLeast(0)
    assertThat(client.clientData.pid).isAtLeast(0)
    assertThat(session!!.sessionName).isEqualTo(
      "Android Debugger (pid: ${client.clientData.pid}, debug port: ${client.debuggerListenPort})")
  }

  @Test
  fun testKillAppOnDestroy() {
    val mockDevice = client.device
    val session = attachJavaDebuggerToClient(myProject, client, executionEnvironment).blockingGet(1000)!!
    session.debugProcess.processHandler.destroyProcess()
    session.debugProcess.processHandler.waitFor()
    Thread.sleep(100)
    Mockito.verify(mockDevice, Mockito.times(1)).forceStop(eq("com.android.example"))
  }

  @Test
  fun testVMExitedNotifierIsInvoked() {
    val session = attachJavaDebuggerToClient(myProject, client, executionEnvironment).blockingGet(1000)!!
    session.debugProcess.processHandler.detachProcess()
    session.debugProcess.processHandler.waitFor()
    Mockito.verify(client, Mockito.times(1)).notifyVmMirrorExited()
  }
}