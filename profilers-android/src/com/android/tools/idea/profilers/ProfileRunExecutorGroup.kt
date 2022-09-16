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
import com.android.tools.idea.run.ExecutorIconProvider
import com.android.tools.idea.run.profiler.AbstractProfilerExecutorGroup
import com.android.tools.idea.run.profiler.ProfilingMode
import com.android.tools.profilers.sessions.SessionsManager
import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import icons.StudioIcons
import org.jetbrains.android.util.AndroidUtils
import javax.swing.Icon

/**
 * Executor group to support profiling app as profileable or debuggable in a dropdown menu.
 */
class ProfileRunExecutorGroup : AbstractProfilerExecutorGroup<ProfileRunExecutorGroup.ProfilerSetting>(), ExecutorIconProvider {
  /**
   * A setting maps to a child executor in the group, containing metadata for the child executor.
   */
  class ProfilerSetting(profilingMode: ProfilingMode) : AbstractProfilerSetting(profilingMode) {
    override val actionName: String
      get() = "Profile ${profilingMode.value}"

    override val icon: Icon
      get() = when (profilingMode) {
        ProfilingMode.PROFILEABLE -> PROFILEABLE_ICON
        ProfilingMode.DEBUGGABLE -> DEBUGGABLE_ICON
        else -> StudioIcons.Shell.Toolbar.PROFILER
      }

    override val startActionText = actionName
    override fun canRun(profile: RunProfile) = true
    override fun isApplicable(project: Project) = true
    override fun getStartActionText(configurationName: String) = when (profilingMode) {
      ProfilingMode.PROFILEABLE -> "Profile '$configurationName' with low overhead"
      ProfilingMode.DEBUGGABLE -> "Profile '$configurationName' with complete data"
      else -> "Profile '$configurationName'"
    }
  }

  private class GroupWrapper(actionGroup: ActionGroup) : ExecutorGroupWrapper(actionGroup) {
    override fun groupShouldBeVisible(e: AnActionEvent) = StudioFlags.PROFILEABLE_BUILDS.get()

    override fun updateDisabledActionPresentation(eventPresentation: Presentation) {
      eventPresentation.icon = PROFILEABLE_ICON
      eventPresentation.text = "Profile"
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

  override fun getDescription(): String = "Profile selected configuration"

  override fun getActionName(): String = "Profile"

  override fun getId(): String = EXECUTOR_ID

  override fun getStartActionText(): String = "Profile"

  override fun getContextActionId(): String = "ProfileGroupRunClass"

  override fun getHelpId(): String? = null

  override fun getExecutorIcon(project: Project, executor: Executor): Icon {
    AndroidProfilerToolWindowFactory.getProfilerToolWindow(project)?.profilers?.let {
      if (SessionsManager.isSessionAlive(it.sessionsManager.profilingSession)) {
        return ExecutionUtil.getLiveIndicator(icon)
      }
    }
    return icon
  }

  override fun isApplicable(project: Project): Boolean = AndroidUtils.hasAndroidFacets(project)

  override fun getRunToolbarActionText(param: String): String = "Profile"

  override fun getRunToolbarChooserText(): String = "Profile"

  override fun getToolWindowIcon(): Icon = StudioIcons.Shell.ToolWindows.ANDROID_PROFILER

  /**
   * WARNING: do not call this to get the Profiler tool window ID, instead use [AndroidProfilerToolWindowFactory.ID] directly.
   *
   * Because "Profile" is a Run task, the Run tool window is also updated. This tool window ID is used by the IDEA platform
   * (RunContentManager) to look up the Run tool window, so it should be "Run" instead of "Android Profiler".
   * On the other hand, Profiler instantiates its tool window using [AndroidProfilerToolWindowFactory.ID] directly and never calls this
   * method.
   */
  override fun getToolWindowId(): String = ToolWindowId.RUN

  override fun createExecutorGroupWrapper(actionGroup: ActionGroup): ExecutorGroupWrapper = GroupWrapper(actionGroup)

  companion object {
    private val PROFILEABLE_ICON = StudioIcons.Shell.Toolbar.PROFILER

    // TODO(b/213946909): replace with real icon.
    private val DEBUGGABLE_ICON = StudioIcons.Shell.Toolbar.PROFILER

    @JvmStatic
    fun getInstance(): ProfileRunExecutorGroup? {
      return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID) as? ProfileRunExecutorGroup
    }
  }
}