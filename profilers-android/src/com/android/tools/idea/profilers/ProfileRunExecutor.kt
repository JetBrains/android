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
import com.android.tools.idea.util.CommonAndroidUtil
import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.project.Project
import icons.StudioIcons
import org.jetbrains.android.util.AndroidUtils
import javax.swing.Icon

class ProfileRunExecutor : DefaultRunExecutor() {
  override fun getIcon(): Icon = StudioIcons.Shell.Toolbar.PROFILER

  override fun getDisabledIcon(): Icon = StudioIcons.Shell.ToolWindows.ANDROID_PROFILER

  override fun getDescription(): String = AndroidProfilerBundle.message("android.profiler.action.profile.description")

  override fun getActionName(): String = AndroidProfilerBundle.message("android.profiler.action.profile")

  override fun getId(): String = EXECUTOR_ID

  override fun getStartActionText(): String = AndroidProfilerBundle.message("android.profiler.action.profile")

  override fun getContextActionId(): String = "ProfileRunClass"

  override fun getHelpId(): String? = null

  override fun isApplicable(project: Project): Boolean {
    val isProfilingModeSupported = project.getProjectSystem().supportsProfilingMode() == true
    if (isProfilingModeSupported && StudioFlags.PROFILEABLE_BUILDS.get()) return false
    return CommonAndroidUtil.getInstance().isAndroidProject(project)
  }

  companion object {
    const val EXECUTOR_ID = AndroidProfilerToolWindowFactory.ID

    fun getInstance(): Executor? {
      return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID)
    }
  }
}