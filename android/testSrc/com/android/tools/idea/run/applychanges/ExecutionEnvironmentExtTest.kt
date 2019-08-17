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
package com.android.tools.idea.run.applychanges

import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.AndroidSessionInfo
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.util.SwapInfo
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.ExecutionManager
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.picocontainer.PicoContainer

/**
 * Unit tests for [ExecutionEnvironment] extension functions.
 */
@RunWith(JUnit4::class)
class ExecutionEnvironmentExtTest {

  @Mock
  lateinit var mockEnv: ExecutionEnvironment
  @Mock
  lateinit var mockProject: Project
  @Mock
  lateinit var mockDevices: DeviceFutures
  @Mock
  lateinit var mockRunProfile: AndroidRunConfigurationBase
  @Mock
  lateinit var mockExecutionTarget: ExecutionTarget
  @Mock
  lateinit var mockExecutionManager: ExecutionManager
  @Mock
  lateinit var mockPicoContainer: PicoContainer
  @Mock
  lateinit var mockDebugManager: DebuggerManagerEx

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    `when`(mockEnv.project).thenReturn(mockProject)
    `when`(mockEnv.runProfile).thenReturn(mockRunProfile)
    `when`(mockEnv.executionTarget).thenReturn(mockExecutionTarget)

    `when`(mockProject.picoContainer).thenReturn(mockPicoContainer)
    `when`(mockPicoContainer.getComponentInstance(eq(ExecutionManager::class.java.name))).thenReturn(mockExecutionManager)
    `when`(mockExecutionManager.runningProcesses).thenReturn(arrayOf())

    `when`(mockProject.getComponent(eq(DebuggerManager::class.java))).thenReturn(mockDebugManager)
    `when`(mockDebugManager.sessions).thenReturn(listOf())
  }

  @Test
  fun findExistingProcessHandler_sessionExists() {
    val mockProcessHandler = mock(ProcessHandler::class.java)
    `when`(mockExecutionManager.runningProcesses).thenReturn(arrayOf(mockProcessHandler))
    val mockSessionInfo = mock(AndroidSessionInfo::class.java)
    `when`(mockProcessHandler.getUserData(eq(AndroidSessionInfo.KEY))).thenReturn(mockSessionInfo)
    `when`(mockSessionInfo.runConfiguration).thenReturn(mockRunProfile)
    `when`(mockSessionInfo.processHandler).thenReturn(mockProcessHandler)

    assertThat(mockEnv.findExistingProcessHandler(mockDevices)).isSameAs(mockProcessHandler)
  }

  @Test
  fun findExistingProcessHandler_fromSwapInfo() {
    val mockSwapInfo = mock(SwapInfo::class.java)
    `when`(mockEnv.getUserData(eq(SwapInfo.SWAP_INFO_KEY))).thenReturn(mockSwapInfo)
    val mockProcessHandler = mock(ProcessHandler::class.java)
    `when`(mockSwapInfo.handler).thenReturn(mockProcessHandler)

    assertThat(mockEnv.findExistingProcessHandler(mockDevices)).isSameAs(mockProcessHandler)
  }

  @Test
  fun findExistingProcessHandler_existingDebugSession() {
    val mockDevice = mock(IDevice::class.java)
    `when`(mockDevices.ifReady).thenReturn(listOf(mockDevice))
    val mockClient = mock(Client::class.java)
    `when`(mockDevice.clients).thenReturn(arrayOf(mockClient))
    `when`(mockClient.debuggerListenPort).thenReturn(1234)

    val mockDebuggerSession = mock(DebuggerSession::class.java)
    `when`(mockDebugManager.sessions).thenReturn(listOf(mockDebuggerSession))
    val mockDebugProcess = mock(DebugProcessImpl::class.java)
    `when`(mockDebuggerSession.process).thenReturn(mockDebugProcess)
    val mockRemoteConnection = mock(RemoteConnection::class.java)
    `when`(mockDebugProcess.connection).thenReturn(mockRemoteConnection)
    `when`(mockRemoteConnection.address).thenReturn("  1234  ")
    val mockProcessHandler = mock(ProcessHandler::class.java)
    `when`(mockDebugProcess.processHandler).thenReturn(mockProcessHandler)

    assertThat(mockEnv.findExistingProcessHandler(mockDevices)).isSameAs(mockProcessHandler)
  }

  @Test
  fun findExistingProcessHandler_noExistingSession() {
    assertThat(mockEnv.findExistingProcessHandler(mockDevices)).isNull()
  }
}