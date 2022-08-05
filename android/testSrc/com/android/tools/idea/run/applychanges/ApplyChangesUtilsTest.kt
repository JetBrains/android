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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.AndroidSessionInfo
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.util.SwapInfo
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.execution.ExecutionManager
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.content.Content
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/**
 * Unit tests for utility functions of apply-changes run pipeline.
 */
@RunWith(JUnit4::class)
class ApplyChangesUtilsTest {
  @Mock
  lateinit var mockEnv: ExecutionEnvironment
  @Mock
  lateinit var mockDevices: DeviceFutures
  @Mock
  lateinit var mockProject: Project
  @Mock
  lateinit var mockExecutionManager: ExecutionManager
  @Mock
  lateinit var mockRunProfile: AndroidRunConfigurationBase
  @Mock
  lateinit var mockExecutionTarget: ExecutionTarget
  @Mock
  lateinit var mockDebugManager: DebuggerManagerEx
  @Mock
  lateinit var mockRunContentManager: RunContentManager

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    whenever(mockEnv.project).thenReturn(mockProject)
    whenever(mockEnv.runProfile).thenReturn(mockRunProfile)
    whenever(mockEnv.executionTarget).thenReturn(mockExecutionTarget)
    whenever(mockProject.getService(eq(ExecutionManager::class.java))).thenReturn(mockExecutionManager)
    whenever(mockProject.getService(eq(DebuggerManager::class.java))).thenReturn(mockDebugManager)
    whenever(mockDebugManager.sessions).thenReturn(listOf())
    whenever(mockProject.getService(eq(RunContentManager::class.java))).thenReturn(mockRunContentManager)
  }

  @Test
  fun findExistingSessionAndMaybeDetachForColdSwap_noPreviousHandler() {
    whenever(mockExecutionManager.getRunningProcesses()).thenReturn(arrayOf())
    val prevHandler = findExistingSessionAndMaybeDetachForColdSwap(mockEnv, mockDevices)

    assertThat(prevHandler.processHandler).isNull()
    assertThat(prevHandler.executionConsole).isNull()
  }

  @Test
  fun findExistingSessionAndMaybeDetachForColdSwap_previousHandlerWithSwapInfo() {
    val mockProcessHandler = mock(ProcessHandler::class.java)
    val mockSessionInfo = mock(AndroidSessionInfo::class.java)
    whenever(mockProcessHandler.getUserData(eq(AndroidSessionInfo.KEY))).thenReturn(mockSessionInfo)
    whenever(mockSessionInfo.runConfiguration).thenReturn(mockRunProfile)
    whenever(mockSessionInfo.processHandler).thenReturn(mockProcessHandler)
    val mockRunContentDescriptor = mock(RunContentDescriptor::class.java)
    whenever(mockRunContentDescriptor.processHandler).thenReturn(mockProcessHandler)
    val mockConsole = mock(ExecutionConsole::class.java)
    whenever(mockRunContentDescriptor.executionConsole).thenReturn(mockConsole)
    whenever(mockRunContentManager.allDescriptors).thenReturn(listOf(mockRunContentDescriptor))
    val mockSwapInfo = mock(SwapInfo::class.java)
    whenever(mockEnv.getUserData(eq(SwapInfo.SWAP_INFO_KEY))).thenReturn(mockSwapInfo)
    whenever(mockExecutionManager.getRunningProcesses()).thenReturn(arrayOf(mockProcessHandler))

    val prevHandler = findExistingSessionAndMaybeDetachForColdSwap(mockEnv, mockDevices)

    assertThat(prevHandler.processHandler).isSameAs(mockProcessHandler)
    assertThat(prevHandler.executionConsole).isSameAs(mockConsole)
    verify(mockProcessHandler, never()).detachProcess()
  }

  @Test
  fun findExistingSessionAndMaybeDetachForColdSwap_previousHandlerWithSwapInfo_noPreviousConsole() {
    val mockProcessHandler = mock(ProcessHandler::class.java)
    val mockSessionInfo = mock(AndroidSessionInfo::class.java)
    whenever(mockProcessHandler.getUserData(eq(AndroidSessionInfo.KEY))).thenReturn(mockSessionInfo)
    whenever(mockSessionInfo.runConfiguration).thenReturn(mockRunProfile)
    whenever(mockSessionInfo.processHandler).thenReturn(mockProcessHandler)
    val mockRunContentDescriptor = mock(RunContentDescriptor::class.java)
    whenever(mockRunContentDescriptor.processHandler).thenReturn(mockProcessHandler)
    whenever(mockRunContentManager.allDescriptors).thenReturn(listOf(mockRunContentDescriptor))
    val mockSwapInfo = mock(SwapInfo::class.java)
    whenever(mockEnv.getUserData(eq(SwapInfo.SWAP_INFO_KEY))).thenReturn(mockSwapInfo)
    whenever(mockExecutionManager.getRunningProcesses()).thenReturn(arrayOf(mockProcessHandler))

    val prevHandler = findExistingSessionAndMaybeDetachForColdSwap(mockEnv, mockDevices)

    assertThat(prevHandler.processHandler).isSameAs(mockProcessHandler)
    assertThat(prevHandler.executionConsole).isNull()
    verify(mockProcessHandler, never()).detachProcess()
  }

  @Test
  fun findExistingSessionAndMaybeDetachForColdSwap_previousHandlerWithoutSwapInfo_triggersColdSwap() {
    val mockProcessHandler = mock(ProcessHandler::class.java)
    val mockSessionInfo = mock(AndroidSessionInfo::class.java)
    whenever(mockProcessHandler.getUserData(eq(AndroidSessionInfo.KEY))).thenReturn(mockSessionInfo)
    whenever(mockSessionInfo.runConfiguration).thenReturn(mockRunProfile)
    whenever(mockSessionInfo.processHandler).thenReturn(mockProcessHandler)
    val mockExecutor = mock(Executor::class.java)
    val mockContent = mock(Content::class.java)
    doAnswer {
      val key = it.arguments[0] as Key<*>
      if (key.toString() == "Executor") mockExecutor else null
    }.whenever(mockContent).getUserData(any(Key::class.java))
    val mockContentDescriptor = mock(RunContentDescriptor::class.java)
    whenever(mockContentDescriptor.processHandler).thenReturn(mockProcessHandler)
    whenever(mockContentDescriptor.attachedContent).thenReturn(mockContent)
    whenever(mockRunContentManager.allDescriptors).thenReturn(listOf(mockContentDescriptor))
    whenever(mockExecutionManager.getRunningProcesses()).thenReturn(arrayOf(mockProcessHandler))

    val prevHandler = findExistingSessionAndMaybeDetachForColdSwap(mockEnv, mockDevices)

    assertThat(prevHandler.processHandler).isNull()
    assertThat(prevHandler.executionConsole).isNull()

    // Make sure that the previous process handler is detached when the swap info is missing.
    verify(mockProcessHandler).detachProcess()
    verify(mockRunContentManager).removeRunContent(any(), any())
  }
}