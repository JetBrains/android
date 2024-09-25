/*
 * Copyright (C) 2024 The Android Open Source Project
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

//import com.android.tools.idea.profilers.ProfilerProgramRunner.Companion.isApiLevelSupported
//import com.android.tools.idea.profilers.ProfilerProgramRunner.Companion.isProjectSupported
//import com.android.tools.idea.profilers.ProfilerProgramRunner.Companion.isSystemSupported
import com.android.tools.idea.profilers.actions.ProfileAction
import com.android.tools.idea.profilers.actions.ProfileDebuggableAction
import com.android.tools.idea.profilers.actions.ProfileProfileableAction
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

object ProfilerBuildAndLaunch {
  private fun getLogger() = Logger.getInstance(ProfilerBuildAndLaunch::class.java)

  /**
   * Builds and launches a profiling action associated with the given project.
   *
   * This method determines the appropriate profiling action based on the project's profiling mode support and the desired profileable
   * mode. It then proceeds to build and launch the selected action.
   *
   * @param project The project for which the profiling action is to be built and launched.
   * @param profileableMode If the project supports profiling mode (is a gradle-based project), this boolean flag indicates whether to use
   *                        a profileable or a debuggable profiling action. Otherwise, this parameter is ignored.
   * @param device The device used for the build and launch.
   */
  @JvmStatic
  fun buildAndLaunchAction(project: Project, profileableMode: Boolean, device: ProcessListModel.ProfilerDeviceSelection) {
    val action = if (project.getProjectSystem().supportsProfilingMode()) {
      // Only non-debuggable devices with API > 28 can support profileable builds.
      //if (isApiLevelSupported(device.featureLevel) && isSystemSupported(device.isDebuggable) && isProjectSupported(project)) {
      //  if (profileableMode) ProfileProfileableAction() else ProfileDebuggableAction()
      //}
      //else {
        ProfileDebuggableAction()
      //}
    }
    else {
      ProfileAction()
    }

    doBuildAndLaunchAction(action)
  }

  private fun doBuildAndLaunchAction(action: AnAction) {
    // This is the only way to acquire the data context without providing a JComponent or AnActionEvent.
    DataManager.getInstance().dataContextFromFocusAsync.onSuccess {
      val event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, it)
      action.actionPerformed(event)
    }.onError {
      getLogger().error(it.message)
    }
  }
}