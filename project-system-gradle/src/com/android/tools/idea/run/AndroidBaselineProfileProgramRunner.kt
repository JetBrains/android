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
package com.android.tools.idea.run

import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.execution.common.AndroidConfigurationProgramRunner
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.run.configuration.AndroidBaselineProfileRunConfiguration
import com.android.tools.idea.run.configuration.AndroidBaselineProfileRunConfigurationType
import com.android.tools.idea.run.util.SwapInfo
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.ThreeState

class AndroidBaselineProfileProgramRunner : AndroidConfigurationProgramRunner() {
  private var getGradleSyncState: (Project) -> GradleSyncState = { GradleSyncState.getInstance(it) }

  override fun canRunWithMultipleDevices(executorId: String): Boolean = DefaultRunExecutor.EXECUTOR_ID == executorId

  override fun getRunnerId(): String = "AndroidBaselineProfileProgramRunner"

  override val supportedConfigurationTypeIds: List<String>
    get() = listOf(AndroidBaselineProfileRunConfigurationType.ID)

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    if (!super.canRun(executorId, profile)) {
      return false
    }
    if (profile !is AndroidBaselineProfileRunConfiguration) {
      return false
    }
    if (DefaultRunExecutor.EXECUTOR_ID != executorId) {
      return false
    }
    val syncState = getGradleSyncState(profile.project)
    return !syncState.isSyncInProgress && syncState.isSyncNeeded() == ThreeState.NO
  }

  override fun run(environment: ExecutionEnvironment,
                   executor: AndroidConfigurationExecutor,
                   indicator: ProgressIndicator): RunContentDescriptor {
    val swapInfo = environment.getUserData(SwapInfo.SWAP_INFO_KEY)

    return if (swapInfo != null) {
      throw RuntimeException("Apply (Code) Changes unsupported in this run config")
    }
    else {
      when (environment.executor.id) {
        DefaultRunExecutor.EXECUTOR_ID -> executor.run(indicator)
        else -> throw RuntimeException("Unsupported executor")
      }
    }
  }
}