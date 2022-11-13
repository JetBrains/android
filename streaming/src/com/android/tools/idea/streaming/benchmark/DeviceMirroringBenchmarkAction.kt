/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming.benchmark

import com.android.tools.idea.streaming.AbstractDisplayView
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.RunningDevicePanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager

/** Action to bring up a dialog that controls device mirroring benchmarking. */
class DeviceMirroringBenchmarkAction : AnAction() {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = getTarget(event.project) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    getTarget(event.project)?.let {
      DeviceMirroringBenchmarkDialog(it).createWrapper(event.project).show()
    }
  }

  private fun getTarget(project: Project?): DeviceMirroringBenchmarkTarget? {
    val panel = project
                  ?.getEmulatorToolWindow()
                  ?.contentManager
                  ?.selectedContent
                  ?.component as? RunningDevicePanel ?: return null
    val view = panel.preferredFocusableComponent as? AbstractDisplayView ?: return null
    return DeviceMirroringBenchmarkTarget(panel.title, panel.id.serialNumber, view)
  }

  private fun Project.getEmulatorToolWindow(): ToolWindow? =
    ToolWindowManager.getInstance(this).getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID)
}