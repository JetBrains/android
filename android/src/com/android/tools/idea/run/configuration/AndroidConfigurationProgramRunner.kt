/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.configuration

import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.run.configuration.user.settings.AndroidConfigurationExecutionSettings
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.stats.RunStats
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

/**
 * Class required by platform to determine which execution buttons are available for a given configuration. See [canRun] method.
 *
 * Actual execution for a configuration, after build, happens in [AndroidConfigurationExecutor].
 */
class AndroidConfigurationProgramRunner : AsyncProgramRunner<RunnerSettings>() {
  companion object {
    val useNewExecutionForActivities: Boolean
      get() = StudioFlags.NEW_EXECUTION_FLOW_ENABLED.get() &&
              AndroidConfigurationExecutionSettings.getInstance().state.enableNewConfigurationFlow
  }

  override fun getRunnerId(): String = "AndroidConfigurationProgramRunner"

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    val supportExecutor = DefaultRunExecutor.EXECUTOR_ID == executorId || DefaultDebugExecutor.EXECUTOR_ID == executorId

    if (!supportExecutor) {
      return false
    }

    return profile is AndroidWearConfiguration || (profile is AndroidRunConfiguration && useNewExecutionForActivities)
  }

  @Throws(ExecutionException::class)
  public override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    val runProfile = environment.runProfile

    val executor = state as AndroidConfigurationExecutor

    FileDocumentManager.getInstance().saveAllDocuments()

    val stats = RunStats.from(environment)
    val promise = AsyncPromise<RunContentDescriptor?>()
    fun handleError(error: Throwable) {
      stats.fail()
      promise.setError(error)
    }

    ProgressManager.getInstance().run(object : Task.Backgroundable(environment.project, "Launching ${runProfile.name}") {
      override fun run(indicator: ProgressIndicator) {

        val swapInfo = environment.getUserData(SwapInfo.SWAP_INFO_KEY)

        val runner: (ProgressIndicator) -> Promise<RunContentDescriptor> =
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

        runner(indicator)
          .onSuccess {
            val processHandler = it.processHandler
                                 ?: throw RuntimeException("AndroidConfigurationExecutor returned RunContentDescriptor without process handler")
            AndroidSessionInfo.create(processHandler, runProfile as RunConfiguration, environment.executor.id, environment.executionTarget)
            promise.setResult(it)
          }
          .onError(::handleError)
      }

      override fun onThrowable(error: Throwable) = handleError(error)

      override fun onSuccess() = stats.success()

      override fun onCancel() = stats.abort()
    })

    return promise
  }
}