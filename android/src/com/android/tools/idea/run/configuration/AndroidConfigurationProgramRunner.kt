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

import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutorBase
import com.android.tools.idea.stats.RunStatsService
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
 * Class required by platform to determine which execution buttons are available for given configuration. See [canRun] method.
 *
 * Actual execution for configuration, after build, happens in [AndroidConfigurationExecutorBase]
 */
class AndroidConfigurationProgramRunner : AsyncProgramRunner<RunnerSettings>() {
  override fun getRunnerId(): String = "AndroidConfigurationProgramRunner"

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    return (DefaultRunExecutor.EXECUTOR_ID == executorId || DefaultDebugExecutor.EXECUTOR_ID == executorId) &&
           (profile is AndroidWearConfiguration ||
            (profile is AndroidRunConfiguration && profile.useNewExecution))
  }

  override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    val promise = AsyncPromise<RunContentDescriptor?>()
    assert(state is AndroidConfigurationExecutorBase)

    FileDocumentManager.getInstance().saveAllDocuments()
    val stats = RunStatsService.get(environment.project).create()

    ProgressManager.getInstance().run(object : Task.Backgroundable(environment.project, "Launching ${environment.runProfile.name}") {
      override fun run(indicator: ProgressIndicator) {
        promise.setResult((state as AndroidConfigurationExecutorBase).execute(stats))
      }

      override fun onThrowable(error: Throwable) {
        stats.fail()
        promise.setError(error)
      }

      override fun onSuccess() = stats.success()

      override fun onCancel() = stats.abort()
    })

    return promise
  }
}