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
import com.android.tools.idea.profilers.AndroidProfilerBundle.message
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.profiler.AbstractProfilerExecutorGroup
import com.android.tools.idea.run.profiler.ProfilingMode
import com.android.tools.idea.util.CommonAndroidUtil
import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.configurations.RunProfile
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import icons.StudioIcons
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Executor group to support profiling app as profileable or debuggable in a dropdown menu.
 */
class ProfileRunExecutorGroup : AbstractProfilerExecutorGroup<ProfileRunExecutorGroup.ProfilerSetting>() {
  /**
   * A setting maps to a child executor in the group, containing metadata for the child executor.
   */
  class ProfilerSetting(profilingMode: ProfilingMode) : AbstractProfilerSetting(profilingMode) {
    @get:Nls
    override val actionName: String
      get() = if (StudioFlags.PROFILER_TASK_BASED_UX.get()) {
        when (profilingMode) {
          ProfilingMode.PROFILEABLE -> "Profiler: Run as profileable (low overhead)"
          ProfilingMode.DEBUGGABLE -> "Profiler: Run as debuggable (complete data)"
          else -> "Profiler: Run"
        }
      }
      else {
        when (profilingMode) {
          ProfilingMode.PROFILEABLE -> "Profile with low overhead (profileable)"
          ProfilingMode.DEBUGGABLE -> "Profile with complete data (debuggable)"
          else -> "Profile"
        }
      }

    override val icon: Icon
      get() = when (profilingMode) {
        ProfilingMode.PROFILEABLE -> PROFILEABLE_ICON
        ProfilingMode.DEBUGGABLE -> DEBUGGABLE_ICON
        else -> StudioIcons.Shell.Toolbar.PROFILER
      }

    override val startActionText = "Profile"
    override fun canRun(profile: RunProfile) = true

    override fun isApplicable(project: Project): Boolean {
      val isProfilingModeSupported = project.getProjectSystem().supportsProfilingMode() == true
      return isProfilingModeSupported && StudioFlags.PROFILEABLE_BUILDS.get()
    }

    @Nls
    override fun getStartActionText(configurationName: String) = when (profilingMode) {
      ProfilingMode.PROFILEABLE -> message("android.profiler.action.profile.configuration.with.low.overhead", configurationName)
      ProfilingMode.DEBUGGABLE -> message("android.profiler.action.profile.configuration.with.complete.data", configurationName)
      else -> message("android.profiler.action.profile.configuration", configurationName)
    }
  }

  init {
    // Register profiling modes as RunExecutorSettings, each mapped to a child executor.
    // To determine which profiling mode is selected by the user action, perform a look-up
    // via ProfileRunExecutorGroup#getRegisteredSettings(executorId).
    registerSettings(ProfilerSetting(ProfilingMode.PROFILEABLE))
    registerSettings(ProfilerSetting(ProfilingMode.DEBUGGABLE))
  }

  override fun getIcon(): Icon = PROFILEABLE_ICON

  override fun getDisabledIcon(): Icon = toolWindowIcon

  override fun getDescription(): String = message("android.profiler.action.profile.description")

  override fun getActionName(): String = message("android.profiler.action.profile")

  override fun getId(): String = EXECUTOR_ID

  override fun getStartActionText(): String = message("android.profiler.action.profile")

  override fun getContextActionId(): String = "ProfileGroupRunClass"

  override fun getHelpId(): String? = null

  override fun isApplicable(project: Project): Boolean = CommonAndroidUtil.getInstance().isAndroidProject(project)

  override fun getRunToolbarActionText(param: String): String = "Profile"

  override fun getRunToolbarChooserText(): String = "Profile"

  override fun getToolWindowIcon(): Icon = AllIcons.Toolwindows.ToolWindowRun

  /**
   * WARNING: do not call this to get the Profiler tool window ID, instead use [AndroidProfilerToolWindowFactory.ID] directly.
   *
   * Because "Profile" is a Run task, the Run tool window is also updated. This tool window ID is used by the IDEA platform
   * (RunContentManager) to look up the Run tool window, so it should be "Run" instead of "Android Profiler".
   * On the other hand, Profiler instantiates its tool window using [AndroidProfilerToolWindowFactory.ID] directly and never calls this
   * method.
   */
  override fun getToolWindowId(): String = ToolWindowId.RUN

  /**
   * Returns the child executor for the given profiling mode.
   *
   * The child executor's IDs are in the format of "Android Profiler Group#1", per the implementation of ExecutorGroup. Because
   * the IDs are not very readable, action name is compared to locate the executor.
   */
  fun getChildExecutor(profilingMode: ProfilingMode): Executor = childExecutors().filter { e ->
    e.actionName == ProfilerSetting(profilingMode).actionName
  }.first()

  companion object {
    private val PROFILEABLE_ICON = StudioIcons.Shell.Toolbar.PROFILER_LOW_OVERHEAD
    private val DEBUGGABLE_ICON = StudioIcons.Shell.Toolbar.PROFILER_DETAILED

    @JvmStatic
    fun getInstance(): ProfileRunExecutorGroup? {
      return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID) as? ProfileRunExecutorGroup
    }
  }
}