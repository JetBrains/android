/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.run

import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.execution.common.AndroidConfigurationProgramRunner
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.run.configuration.AndroidDeclarativeWatchFaceConfiguration
import com.android.tools.idea.run.configuration.AndroidDeclarativeWatchFaceConfigurationType
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.execution.runners.ProgramRunner

/**
 * [ProgramRunner] for [AndroidDeclarativeWatchFaceConfigurationExecutor] which only
 * supports [DefaultRunExecutor]. [DefaultDebugExecutor] is not available for
 * Watch Face Formats as they do not have any code associated with them,
 * only XML files.
 *
 * For more context, see https://developer.android.com/training/wearables/wff
 */
class AndroidDeclarativeWatchFaceProgramRunner(
  private var getSyncManager: (Project) -> ProjectSystemSyncManager = { it.getSyncManager() }
) : AndroidConfigurationProgramRunner() {

  override fun getRunnerId() = "AndroidDeclarativeWatchFaceProgramRunner"

  override val supportedConfigurationTypeIds =
    listOf(AndroidDeclarativeWatchFaceConfigurationType.ID)

  override fun canRunWithMultipleDevices(executorId: String) =
    DefaultRunExecutor.EXECUTOR_ID == executorId

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    if (profile !is AndroidDeclarativeWatchFaceConfiguration) {
      return false
    }
    if (!super.canRun(executorId, profile)) {
      return false
    }
    if (DefaultRunExecutor.EXECUTOR_ID != executorId) {
      return false
    }
    val syncManager = getSyncManager(profile.project)
    return !syncManager.isSyncInProgress() && !syncManager.isSyncNeeded()
  }

  override fun run(
    environment: ExecutionEnvironment,
    executor: AndroidConfigurationExecutor,
    indicator: ProgressIndicator,
  ): RunContentDescriptor {
    return when {
      DefaultRunExecutor.EXECUTOR_ID == environment.executor.id -> executor.run(indicator)
      else -> throw RuntimeException("Unsupported executor")
    }
  }
}