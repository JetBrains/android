/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.tasks

import com.android.tools.adtui.TreeWalker
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

private const val ID = "CLEAR_LOGCAT"
private const val LOGCAT_TOOL_WINDOW_ID = "Logcat"

/**
 * A [LaunchTask] that clears Logcat components.
 */
class ClearLogcatTask(private val project: Project) : LaunchTask {
  override fun getDescription(): String = "Clearing Logcat"

  override fun getDuration(): Int = LaunchTaskDurations.ASYNC_TASK

  override fun getId(): String = ID

  override fun run(launchContext: LaunchContext): LaunchResult {
    invokeLater {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(LOGCAT_TOOL_WINDOW_ID) ?: return@invokeLater
      val device = launchContext.device
      for (content in toolWindow.contentManager.contents) {
        TreeWalker(content.component).descendantStream().forEach {
          if (it is ClearableLogcatComponent && it.getConnectedDevice() == device) {
            it.clearLogcat()
          }
        }
      }
    }
    return LaunchResult.success()
  }
}
