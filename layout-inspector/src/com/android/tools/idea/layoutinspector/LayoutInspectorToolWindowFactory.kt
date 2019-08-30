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

import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.InspectorPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

const val TOOL_WINDOW_ID = "Layout Inspector"

private val INSPECTOR_PANEL = Key.create<InspectorPanel>("InspectorPanel")

/**
 * Get the DeviceViewPanel from the specified layout inspector [toolWindow].
 */
fun lookupDeviceWindow(toolWindow: ToolWindow): DeviceViewPanel? =
  toolWindow.contentManager?.getContent(0)?.getUserData(INSPECTOR_PANEL)?.deviceViewPanel

/**
 * ToolWindowFactory: For creating a layout inspector tool window for the project.
 */
class LayoutInspectorToolWindowFactory : ToolWindowFactory {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val contentManager = toolWindow.contentManager
    val panel = InspectorPanel(project, TOOL_WINDOW_ID)
    val content = contentManager.factory.createContent(panel, "", true)
    content.putUserData(INSPECTOR_PANEL, panel)
    contentManager.addContent(content)
    project.messageBus.connect(project).subscribe(ToolWindowManagerListener.TOPIC, LayoutInspectorToolWindowManagerListener(project))
  }
}

/**
 * Listen to state changes for the create layout inspector tool window.
 *
 * When the layout inspector is made visible (from a non visible state) attempt to auto connect.
 */
private class LayoutInspectorToolWindowManagerListener(private val project: Project) : ToolWindowManagerListener {
  private var wasWindowVisible = false

  override fun stateChanged() {
    val preferredProcess = getPreferredInspectorProcess(project) ?: return
    val window = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
    val isWindowVisible = window.isVisible // Layout Inspector tool window is expanded.
    val windowVisibilityChanged = isWindowVisible != wasWindowVisible
    wasWindowVisible = isWindowVisible
    if (!windowVisibilityChanged || !isWindowVisible) {
      return
    }
    val panel = lookupDeviceWindow(window) ?: return
    if (!panel.layoutInspector.client.isConnected) {
      panel.layoutInspector.client.attach(preferredProcess)
    }
  }
}
