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
package com.android.tools.idea.run.configuration.execution

import com.android.tools.idea.run.editor.DeployTarget
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.concurrency.Promise


interface AndroidConfigurationExecutor {
  val configuration: RunConfiguration
  val deployTarget: DeployTarget

  fun run(indicator: ProgressIndicator): Promise<RunContentDescriptor>
  fun runAsInstantApp(indicator: ProgressIndicator): Promise<RunContentDescriptor>
  fun debug(indicator: ProgressIndicator): Promise<RunContentDescriptor>
  fun applyChanges(indicator: ProgressIndicator): Promise<RunContentDescriptor>
  fun applyCodeChanges(indicator: ProgressIndicator): Promise<RunContentDescriptor>
}

class AndroidConfigurationExecutorRunProfileState(executor: AndroidConfigurationExecutor) : AndroidConfigurationExecutor by executor, RunProfileState {
  override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult? {
    throw RuntimeException("Unexpected code path")
  }
}

