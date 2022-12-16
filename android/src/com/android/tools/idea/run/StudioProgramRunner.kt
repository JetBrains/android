/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner.Companion.useNewExecutionForActivities
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.util.ThreeState
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

/**
 * Base [com.intellij.execution.runners.ProgramRunner] for all Android Studio (not ASWB) program runners.
 * It provides the necessary support and management for working with hot swap (Apply (Code) Changes).
 */
abstract class StudioProgramRunner // @VisibleForTesting
internal constructor(
  private val getGradleSyncState: (Project) -> GradleSyncState,
  private val getAndroidTarget: (Project, RunConfiguration) -> AndroidExecutionTarget?
) : AsyncProgramRunner<RunnerSettings>() {
  constructor() : this(
    { project -> GradleSyncState.getInstance(project) },
    { project, profile -> getAvailableAndroidTarget(project, profile) }
  )

  override fun canRun(executorId: String, config: RunProfile): Boolean {
    if (config !is AndroidRunConfigurationBase) {
      return false
    }
    val target = getAndroidTarget(config.project, config) ?: return false
    if (target.availableDeviceCount > 1 && !canRunWithMultipleDevices(executorId)) {
      return false
    }
    if (config.canRunWithoutSync()) {
      return true
    }
    if (config is AndroidRunConfiguration && useNewExecutionForActivities) {
      // In this case [AndroidConfigurationProgramRunner] is going to be used.
      return false
    }
    val syncState = getGradleSyncState(config.project)
    return !syncState.isSyncInProgress && syncState.isSyncNeeded() == ThreeState.NO
  }

  @Throws(ExecutionException::class)
  override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    if (state is AndroidConfigurationExecutor) {
      return executeWithExecutor(environment, state)
    }
    val project = environment.project
    val executor = environment.executor
    val executorId = executor.id
    val isTestConfig = environment.runProfile is AndroidTestRunConfiguration
    val settings = environment.runnerAndConfigurationSettings
    val swapInfo = environment.getUserData(SwapInfo.SWAP_INFO_KEY)
    if (settings != null && swapInfo == null) {
      settings.isActivateToolWindowBeforeRun = isTestConfig || settings.isActivateToolWindowBeforeRun
    }
    FileDocumentManager.getInstance().saveAllDocuments()
    val result = state.execute(executor, this)
    var descriptor: RunContentDescriptor? = null
    if (swapInfo != null && result != null) {
      // If we're hotswapping, we want to use the currently-running ContentDescriptor,
      // instead of making a new one (which "show"RunContent actually does).
      val manager = RunContentManager.getInstance(project)
      // Note we may still end up with a null descriptor since the user could close the tool tab after starting a hotswap.
      descriptor = manager.findContentDescriptor(executor, result.processHandler)
    }
    if (descriptor == null || descriptor.attachedContent == null) {
      descriptor = showRunContent(result, environment)
    }
    else if (descriptor !is HiddenRunContentDescriptor) {
      // Since we got to this branch, it implies we're swapping for the first time. In this case,
      // create a wrapper descriptor so that the ExecutionManager doesn't try to "show" it
      // (which actually creates a new descriptor and wipes out the old one).
      val content = descriptor.attachedContent
      descriptor = HiddenRunContentDescriptor(descriptor)
      content!!.putUserData(RunContentDescriptor.DESCRIPTOR_KEY, descriptor)
    }
    if (descriptor != null) {
      if (swapInfo != null) {
        // Don't show the tool window when we're applying (code) changes.
        descriptor.isActivateToolWindowWhenAdded = false
      }
      val processHandler = descriptor.processHandler!!
      val runProfile = environment.runProfile
      val runConfiguration = if (runProfile is RunConfiguration) runProfile else null
      AndroidSessionInfo.create(processHandler, runConfiguration, executorId, environment.executionTarget)
    }
    return resolvedPromise(descriptor)
  }

  private fun executeWithExecutor(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    return AndroidConfigurationProgramRunner().execute(environment, state)
  }

  protected abstract fun canRunWithMultipleDevices(executorId: String): Boolean

  companion object {
    private fun getAvailableAndroidTarget(project: Project, profile: RunConfiguration): AndroidExecutionTarget? {
      return ExecutionTargetManager.getInstance(project).getTargetsFor(profile)
        .filterIsInstance<AndroidExecutionTarget>()
        .firstOrNull()
    }
  }
}