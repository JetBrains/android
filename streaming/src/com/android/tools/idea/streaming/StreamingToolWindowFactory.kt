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
package com.android.tools.idea.streaming

import com.android.tools.idea.avdmanager.HardwareAccelerationCheck.isChromeOSAndIsNotHWAccelerated
import com.android.tools.idea.isAndroidEnvironment
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ToolWindowWindowAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowType

/**
 * [ToolWindowFactory] implementation for the Emulator tool window.
 */
class StreamingToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.setDefaultContentUiType(ToolWindowContentUiType.TABBED)
    StreamingToolWindowManager.initializeForProject(project)
  }

  override fun init(toolWindow: ToolWindow) {
    toolWindow.stripeTitle = RUNNING_DEVICES_TOOL_WINDOW_TITLE
    toolWindow.setTitleActions(listOf(object : ToolWindowWindowAction() {
      override fun update(e: AnActionEvent) {
        if (getToolWindow(e)?.type.let { it == ToolWindowType.FLOATING || it == ToolWindowType.WINDOWED }) {
          e.presentation.isEnabledAndVisible = false
          return
        }
        super.update(e)
        e.presentation.icon = AllIcons.Actions.MoveToWindow
      }
    }))
  }

  override fun isApplicable(project: Project): Boolean {
    val available = isAndroidEnvironment(project) && (canLaunchEmulator() || DeviceMirroringSettings.getInstance().deviceMirroringEnabled)
    if (available) {
      StreamingToolWindowManager.initializeForProject(project)
    }
    return available
  }

  private fun canLaunchEmulator(): Boolean =
    !isChromeOSAndIsNotHWAccelerated()
}