/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.run.AndroidLaunchTaskContributor
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchResult
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTaskDurations
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager

private val PREFERRED_PROCESS = Key.create<LayoutInspectorPreferredProcess>("LayoutInspector.Preferred.Process")

fun getPreferredInspectorProcess(project: Project): LayoutInspectorPreferredProcess? = project.getUserData(PREFERRED_PROCESS)

/**
 * AndroidLaunchTaskContributor: provides a task whenever an Android process is started from Studio.
 */
class LayoutInspectorAndroidLaunchTaskContributor : AndroidLaunchTaskContributor {

  override fun getTask(module: Module, applicationId: String, launchOptions: LaunchOptions): LaunchTask {
    return LayoutInspectorLaunchTask(module)
  }
}

/**
 * LaunchTask: When an Android process is started from Studio, auto connect the
 * layout inspector if it is open (and not already connected to a different process).
 *
 * If the layout inspector is not open, store information with the project such
 * that we can auto start the process communication when the layout inspector is
 * opened.
 */
private class LayoutInspectorLaunchTask(private val module: Module): LaunchTask {

  override fun getDescription() = "Launching the Layout Inspector"

  override fun getDuration() = LaunchTaskDurations.ASYNC_TASK

  override fun run(launchContext: LaunchContext): LaunchResult? {
    val project = module.project
    val window = ToolWindowManager.getInstance(project).getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID) ?: return LaunchResult.success()
    val packageName = AndroidModuleInfo.getInstance(module)?.`package`
    val preferredProcess = LayoutInspectorPreferredProcess(launchContext.device, packageName)
    if (window.isVisible) {
      lookupLayoutInspector(window)?.allClients?.find { it.attachIfSupported(preferredProcess) != null }
    }
    project.putUserData(PREFERRED_PROCESS, preferredProcess)

    // TODO: Register a callback for clearing the preferred process when the process ends.
    return LaunchResult.success()
  }

  override fun getId() = LAYOUT_INSPECTOR_TOOL_WINDOW_ID
}
