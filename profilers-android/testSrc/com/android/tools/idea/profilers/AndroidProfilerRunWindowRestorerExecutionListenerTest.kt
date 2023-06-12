/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerServiceInstance
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

class AndroidProfilerRunWindowRestorerExecutionListenerTest {
  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private val mockToolWindowManager = mock<ToolWindowManager>()
  private val mockRunToolWindow = mock<ToolWindow>()
  private val mockExecutionEnvironment = mock<ExecutionEnvironment>()
  private val mockProcessHandler = mock<ProcessHandler>()

  private lateinit var listener: AndroidProfilerRunWindowRestorerExecutionListener

  @Before
  fun setup() {
    projectRule.project.registerServiceInstance(ToolWindowManager::class.java, mockToolWindowManager)
    whenever(mockToolWindowManager.getToolWindow(ToolWindowId.RUN)).thenReturn(mockRunToolWindow)
    listener = AndroidProfilerRunWindowRestorerExecutionListener(projectRule.project)
    registerExecutors(listOf(DefaultRunExecutor(), ProfileRunExecutor(), ProfileRunExecutorGroup()))
  }

  @Test
  fun `the run tool window is not restored when the executor is not a profiler executor`() {
    val executorId = "some random executor id"

    listener.processStartScheduled(executorId, mockExecutionEnvironment)
    listener.processStarted(executorId, mockExecutionEnvironment, mockProcessHandler)

    verifyNoInteractions(mockRunToolWindow)
  }

  @Test
  fun `the run tool window is restored on processStartScheduled when the executor is a profiler executor`() {
    for (profilerExecutorId in profileGroupExecutorIds()) {
      verifyRunToolWindowIsRestoredAfter {
        listener.processStartScheduled(profilerExecutorId, mockExecutionEnvironment)
      }
    }
  }

  @Test
  fun `the run tool window is restored on processStarted when the executor is a profiler executor`() {
    for (profilerExecutorId in profileGroupExecutorIds()) {
      verifyRunToolWindowIsRestoredAfter {
        listener.processStarted(profilerExecutorId, mockExecutionEnvironment, mockProcessHandler)
      }
    }
  }

  private fun verifyRunToolWindowIsRestoredAfter(runnable: () -> Unit) {
    reset(mockRunToolWindow)

    runnable()

    verify(mockRunToolWindow).stripeTitle = "Run"
  }

  private fun profileGroupExecutorIds(): List<String> = requireNotNull(
    ProfileRunExecutorGroup.getInstance()).childExecutors().map { it.id } + requireNotNull(ProfileRunExecutor.getInstance()).id

  private fun registerExecutors(executors: List<Executor>) {
    ExtensionTestUtil.maskExtensions(Executor.EXECUTOR_EXTENSION_NAME,
                                     executors,
                                     disposableRule.disposable)
  }
}