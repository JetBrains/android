/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run

import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.project.Project

/**
 * [com.intellij.execution.runners.ProgramRunner] for the default [com.intellij.execution.Executor]
 * [com.intellij.openapi.actionSystem.AnAction], such as [DefaultRunExecutor] and [DefaultDebugExecutor].
 */
class DefaultStudioProgramRunner : StudioProgramRunner {
  constructor()

  @VisibleForTesting
  constructor(getGradleSyncState: (Project) -> GradleSyncState, getAndroidTarget: (Project, RunConfiguration) -> AndroidExecutionTarget?)
    : super(getGradleSyncState, getAndroidTarget)

  override fun getRunnerId() = "DefaultStudioProgramRunner"

  override fun canRun(executorId: String, config: RunProfile): Boolean {
    return super.canRun(executorId, config) &&
           (DefaultDebugExecutor.EXECUTOR_ID == executorId || DefaultRunExecutor.EXECUTOR_ID == executorId)
  }

  override fun canRunWithMultipleDevices(executorId: String): Boolean {
    return DefaultRunExecutor.EXECUTOR_ID == executorId
  }
}