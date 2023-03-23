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
package com.android.tools.idea.profilers

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.execution.Executor
import com.intellij.execution.Executor.ActionWrapper
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.EmptyAction
import com.intellij.openapi.project.Project
import icons.StudioIcons
import org.jetbrains.android.util.AndroidUtils
import javax.swing.Icon

class ProfileRunExecutor : DefaultRunExecutor() {
  override fun getIcon(): Icon = StudioIcons.Shell.Toolbar.PROFILER

  override fun getDisabledIcon(): Icon = StudioIcons.Shell.ToolWindows.ANDROID_PROFILER

  override fun getDescription(): String = "Profile selected configuration"

  override fun getActionName(): String = "Profile"

  override fun getId(): String = EXECUTOR_ID

  override fun getStartActionText(): String = "Profile"

  override fun getContextActionId(): String = "ProfileRunClass"

  override fun getHelpId(): String? = null

  override fun isApplicable(project: Project): Boolean = AndroidUtils.hasAndroidFacets(project)

  /**
   * Wraps the original action so that it's only visible when the feature flag is true or if the project's system doesn't support profiling
   * mode (e.g. Blaze).
   */
  override fun runnerActionsGroupExecutorActionCustomizer() = ActionWrapper { original ->
    object : EmptyAction.MyDelegatingAction(original) {
      override fun update(e: AnActionEvent) {
        val isProfilingModeSupported = e.project?.getProjectSystem()?.supportsProfilingMode() ?: false
        if (isProfilingModeSupported && StudioFlags.PROFILEABLE_BUILDS.get()) {
          e.presentation.isEnabledAndVisible = false
        }
        else {
          super.update(e)
        }
      }
    }
  }

  companion object {
    const val EXECUTOR_ID = AndroidProfilerToolWindowFactory.ID

    fun getInstance(): Executor? {
      return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID)
    }
  }
}