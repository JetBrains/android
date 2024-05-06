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

import com.intellij.execution.ExecutionListener
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager

/**
 * When running Profiler executors, the Run tool window's icon stripe title gets overridden with the executor's actionName.
 * This happens when a process start is scheduled and when a process is started from {@link com.intellij.execution.impl.}
 * and more specifically in {@link com.intellij.execution.ui.RunContentManagerImpl#getOrCreateContentManagerForToolWindow}
 * when the run tool window is either updated or registered.
 *
 * This listener's purpose is to restore the Run window's stripe title to "Run" after it gets overridden.
 */
class AndroidProfilerRunWindowRestorerExecutionListener constructor(val project: Project) : ExecutionListener {

  override fun processStartScheduled(executorId: String, env: ExecutionEnvironment) {
    super.processStartScheduled(executorId, env)
    maybeRestoreRunWindowStripeTitle(executorId)
  }

  override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
    super.processStarted(executorId, env, handler)
    maybeRestoreRunWindowStripeTitle(executorId)
  }

  private fun maybeRestoreRunWindowStripeTitle(executorId: String) {
    if (!ProfilerProgramRunner.isProfilerExecutor(executorId)) {
      return
    }

    ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.RUN)?.apply {
      stripeTitle = DefaultRunExecutor.getRunExecutorInstance().actionName
    }
  }
}