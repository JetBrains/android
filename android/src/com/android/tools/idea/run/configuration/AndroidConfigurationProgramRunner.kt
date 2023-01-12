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

import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.stats.RunStats
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.ThreeState
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

/**
 * Class required by platform to determine which execution buttons are available for a given configuration. See [canRun] method.
 *
 * Actual execution for a configuration, after build, happens in [AndroidConfigurationExecutor].
 */
abstract class AndroidConfigurationProgramRunner internal constructor(
  private val getGradleSyncState: (Project) -> GradleSyncState,
  private val getAndroidTarget: (Project, RunConfiguration) -> AndroidExecutionTarget?
) : AsyncProgramRunner<RunnerSettings>() {
  constructor() : this(
    { project -> GradleSyncState.getInstance(project) },
    { project, profile -> getAvailableAndroidTarget(project, profile) })

  companion object {
    private fun getAvailableAndroidTarget(project: Project, profile: RunConfiguration): AndroidExecutionTarget? {
      return ExecutionTargetManager.getInstance(project).getTargetsFor(profile)
        .filterIsInstance<AndroidExecutionTarget>()
        .firstOrNull()
    }
  }

  protected abstract fun canRunWithMultipleDevices(executorId: String): Boolean
  protected abstract val supportedConfigurationTypeIds: List<String>
  protected abstract fun getRunner(environment: ExecutionEnvironment,
                                   state: RunProfileState): (ProgressIndicator) -> Promise<RunContentDescriptor>

  override fun getRunnerId(): String = "AndroidConfigurationProgramRunner"

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    if (profile !is RunConfiguration) {
      return false
    }
    if (!supportedConfigurationTypeIds.contains(profile.type.id)) {
      return false
    }
    val target = getAndroidTarget(profile.project, profile) ?: return false
    if (target.availableDeviceCount > 1 && !canRunWithMultipleDevices(executorId)) {
      return false
    }
    val syncState = getGradleSyncState(profile.project)
    return !syncState.isSyncInProgress && syncState.isSyncNeeded() == ThreeState.NO
  }

  @Throws(ExecutionException::class)
  public override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    val runProfile = environment.runProfile

    FileDocumentManager.getInstance().saveAllDocuments()

    val stats = RunStats.from(environment)
    val promise = AsyncPromise<RunContentDescriptor?>()
    fun handleError(error: Throwable) {
      stats.fail()
      promise.setError(error)
    }

    ProgressManager.getInstance().run(object : Task.Backgroundable(environment.project, "Launching ${runProfile.name}") {
      override fun run(indicator: ProgressIndicator) {

        getRunner(environment, state)(indicator)
          .onSuccess {
            val processHandler = it.processHandler
                                 ?: throw RuntimeException(
                                   "AndroidConfigurationExecutor returned RunContentDescriptor without process handler")
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