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
package com.android.tools.idea.layoutinspector

import com.android.adblib.serialNumber
import com.android.tools.idea.devicemanagerv2.DeviceManagerOverflowActionContributor
import com.android.tools.idea.devicemanagerv2.deviceRowData
import com.android.tools.idea.deviceprovisioner.deviceHandle
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import icons.StudioIcons
import org.jetbrains.annotations.VisibleForTesting

class LayoutInspectorDeviceManagerOverflowActionContributor :
  DeviceManagerOverflowActionContributor {
  override fun getAction(): AnAction {
    return OpenStandaloneLayoutInspectorAction()
  }
}

/**
 * Action that opens standalone Layout Inspector. If standalone Layout Inspector is disabled, this
 * action enables it in the settings.
 */
@VisibleForTesting
class OpenStandaloneLayoutInspectorAction :
  DumbAwareAction(
    LayoutInspectorBundle.message("inspect.with.layout.inspector"),
    LayoutInspectorBundle.message("inspect.with.layout.inspector"),
    StudioIcons.Shell.ToolWindows.CAPTURES,
  ) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val deviceRowData = e.deviceRowData()
    val deviceHandle = e.deviceHandle()

    // For now keep the action always disabled.
    e.presentation.isVisible = false
    e.presentation.isEnabled = false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val deviceRowData = e.deviceRowData()
    val serialNumber = deviceRowData?.handle?.state?.connectedDevice?.serialNumber

    if (LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled) {
      // TODO: disable embedded Layout Inspector and register the tool window.
    }

    // Open the tool window.
    ToolWindowManager.getInstance(project)
      .getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
      ?.activate(null)

    // Attempt to start foreground process detection on the device.
    val layoutInspector = LayoutInspectorProjectService.getInstance(project).getLayoutInspector()
    val device = layoutInspector.deviceModel?.devices?.find { it.serial == serialNumber }
    device?.let { layoutInspector.foregroundProcessDetection?.startPollingDevice(it) }
  }
}
