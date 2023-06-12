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
package com.android.tools.idea.execution.common.debug

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.openapi.project.Project
import javax.swing.Icon

internal fun createFakeExecutionEnvironment(project: Project, name: String): ExecutionEnvironment {
  val runProfile = object : RunProfile {
    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? = null
    override fun getName() = name
    override fun getIcon(): Icon? = null
  }

  val programRunner = object : GenericProgramRunner<RunnerSettings>() {
    override fun canRun(executorId: String, profile: RunProfile): Boolean = true
    override fun getRunnerId() = "FakeDebuggerRunner"
  }

  return ExecutionEnvironmentBuilder(project, DefaultDebugExecutor.getDebugExecutorInstance())
    .runProfile(runProfile)
    .runner(programRunner)
    .build()
}