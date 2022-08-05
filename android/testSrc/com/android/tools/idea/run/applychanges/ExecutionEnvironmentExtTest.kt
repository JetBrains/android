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
import com.android.testutils.MockitoKt.whenever
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
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations

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
  lateinit var mockDebugManager: DebuggerManagerEx

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    whenever(mockEnv.project).thenReturn(mockProject)
    whenever(mockEnv.runProfile).thenReturn(mockRunProfile)
    whenever(mockEnv.executionTarget).thenReturn(mockExecutionTarget)

    whenever(mockProject.getService(eq(ExecutionManager::class.java))).thenReturn(mockExecutionManager)

    whenever(mockProject.getService(eq(DebuggerManager::class.java))).thenReturn(mockDebugManager)
    whenever(mockDebugManager.sessions).thenReturn(listOf())
  }

  @Test
  fun findExistingProcessHandler_sessionExists() {
    val mockProcessHandler = mock(ProcessHandler::class.java)
    val mockSessionInfo = mock(AndroidSessionInfo::class.java)
    whenever(mockProcessHandler.getUserData(eq(AndroidSessionInfo.KEY))).thenReturn(mockSessionInfo)
    whenever(mockSessionInfo.runConfiguration).thenReturn(mockRunProfile)
    whenever(mockSessionInfo.processHandler).thenReturn(mockProcessHandler)
    whenever(mockExecutionManager.getRunningProcesses()).thenReturn(arrayOf(mockProcessHandler))

    assertThat(mockEnv.findExistingProcessHandler(mockDevices)).isSameAs(mockProcessHandler)
  }

  @Test
  fun findExistingProcessHandler_fromSwapInfo() {
    val mockSwapInfo = mock(SwapInfo::class.java)
    whenever(mockEnv.getUserData(eq(SwapInfo.SWAP_INFO_KEY))).thenReturn(mockSwapInfo)
    val mockProcessHandler = mock(ProcessHandler::class.java)
    whenever(mockSwapInfo.handler).thenReturn(mockProcessHandler)
    whenever(mockExecutionManager.getRunningProcesses()).thenReturn(arrayOf(mockProcessHandler))

    assertThat(mockEnv.findExistingProcessHandler(mockDevices)).isSameAs(mockProcessHandler)
  }

  @Test
  fun findExistingProcessHandler_existingDebugSession() {
    val mockDevice = mock(IDevice::class.java)
    whenever(mockDevices.ifReady).thenReturn(listOf(mockDevice))
    val mockClient = mock(Client::class.java)
    whenever(mockDevice.clients).thenReturn(arrayOf(mockClient))
    whenever(mockClient.debuggerListenPort).thenReturn(1234)

    val mockDebuggerSession = mock(DebuggerSession::class.java)
    whenever(mockDebugManager.sessions).thenReturn(listOf(mockDebuggerSession))
    val mockDebugProcess = mock(DebugProcessImpl::class.java)
    whenever(mockDebuggerSession.process).thenReturn(mockDebugProcess)
    val mockRemoteConnection = mock(RemoteConnection::class.java)
    whenever(mockDebugProcess.connection).thenReturn(mockRemoteConnection)
    whenever(mockRemoteConnection.address).thenReturn("  1234  ")
    val mockProcessHandler = mock(ProcessHandler::class.java)
    whenever(mockDebugProcess.processHandler).thenReturn(mockProcessHandler)
    whenever(mockExecutionManager.getRunningProcesses()).thenReturn(arrayOf(mockProcessHandler))

    assertThat(mockEnv.findExistingProcessHandler(mockDevices)).isSameAs(mockProcessHandler)
  }

  @Test
  fun findExistingProcessHandler_noExistingSession() {
    whenever(mockExecutionManager.getRunningProcesses()).thenReturn(arrayOf())
    assertThat(mockEnv.findExistingProcessHandler(mockDevices)).isNull()
  }

  @Test
  fun findExistingProcessHandler_noExistingSession_nonAndroidRunProfile() {
    whenever(mockEnv.runProfile).thenReturn(mock(RunProfile::class.java))
    assertThat(mockEnv.findExistingProcessHandler(mockDevices)).isNull()
  }
}