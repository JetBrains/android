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
import com.android.tools.idea.run.configuration.AndroidComplicationConfigurationType
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner
import com.android.tools.idea.run.configuration.AndroidTileConfigurationType
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfigurationType
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.concurrency.Promise

//TODO(b/266232023): define a better way for running ComposePreviewRunConfiguration and get rid of this constant.
const val composePreviewRunConfigurationId = "ComposePreviewRunConfiguration"

/**
 * [com.intellij.execution.runners.ProgramRunner] for the default [com.intellij.execution.Executor]
 * [com.intellij.openapi.actionSystem.AnAction], such as [DefaultRunExecutor] and [DefaultDebugExecutor].
 */
class DefaultStudioProgramRunner : AndroidConfigurationProgramRunner {
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

  override val supportedConfigurationTypeIds = listOf(
    AndroidRunConfigurationType().id,
    AndroidTestRunConfigurationType().id,
    AndroidComplicationConfigurationType().id,
    AndroidWatchFaceConfigurationType().id,
    AndroidTileConfigurationType().id,
    composePreviewRunConfigurationId
  )

  override fun getRunner(environment: ExecutionEnvironment, state: RunProfileState): (ProgressIndicator) -> RunContentDescriptor {
    val executor = state as AndroidConfigurationExecutor

    val swapInfo = environment.getUserData(SwapInfo.SWAP_INFO_KEY)

    val runner: (ProgressIndicator) -> RunContentDescriptor =
      if (swapInfo != null) {
        when (swapInfo.type) {
          SwapInfo.SwapType.APPLY_CHANGES -> executor::applyChanges
          SwapInfo.SwapType.APPLY_CODE_CHANGES -> executor::applyCodeChanges
        }
      }
      else {
        when (environment.executor.id) {
          DefaultRunExecutor.EXECUTOR_ID -> executor::run
          DefaultDebugExecutor.EXECUTOR_ID -> executor::debug
          else -> throw RuntimeException("Unsupported executor")
        }
      }
    return runner
  }

  override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    val activateToolWindowBeforeRun = environment.runnerAndConfigurationSettings?.isActivateToolWindowBeforeRun ?: false
    val showLogcatAutomatically = (environment.runProfile as? AndroidRunConfiguration)?.SHOW_LOGCAT_AUTOMATICALLY ?: false
    if (showLogcatAutomatically) {
      environment.runnerAndConfigurationSettings?.isActivateToolWindowBeforeRun = false
    }
    try {
      return super.execute(environment, state)
    }
    finally {
      environment.runnerAndConfigurationSettings?.isActivateToolWindowBeforeRun = activateToolWindowBeforeRun
    }
  }
}