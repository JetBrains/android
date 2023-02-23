/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.android.tools.idea.streaming.STREAMING_CONTENT_PANEL_KEY
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Action used to turn Layout Inspector on and off in Running Devices tool window.
 */
class ToggleLayoutInspectorAction : ToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    val runningDevicesTabContext = e.createRunningDevicesTabContext() ?: return false

    return LayoutInspectorManager.getInstance(project).isEnabled(runningDevicesTabContext)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    val runningDevicesTabContext = e.createRunningDevicesTabContext() ?: return

    LayoutInspectorManager.getInstance(project).enableLayoutInspector(runningDevicesTabContext, state)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_IN_RUNNING_DEVICES_ENABLED.get()
  }
}

/**
 * Creates a [RunningDevicesTabContext] using data from the Running Devices Tool Window.
 */
private fun AnActionEvent.createRunningDevicesTabContext(): RunningDevicesTabContext? {
  val project = project ?: return null
  val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID) ?: return null
  val selectedContent = toolWindow.contentManager.selectedContent ?: return null

  val deviceSerialNumber = SERIAL_NUMBER_KEY.getData(dataContext) ?: return null
  val contentPanel = STREAMING_CONTENT_PANEL_KEY.getData(dataContext) ?: return null

  return RunningDevicesTabContext(
    project,
    selectedContent,
    deviceSerialNumber,
    contentPanel,
    contentPanel.parent
  )
}
