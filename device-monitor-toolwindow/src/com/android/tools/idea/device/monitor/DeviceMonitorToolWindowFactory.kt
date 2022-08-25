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
package com.android.tools.idea.device.monitor

import com.android.tools.idea.device.monitor.adbimpl.AdbDeviceListService
import com.android.tools.idea.device.monitor.adbimpl.AdbDeviceNameRendererFactory
import com.android.tools.idea.device.monitor.ui.DeviceMonitorViewImpl
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.isAndroidEnvironment
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import icons.StudioIcons

class DeviceMonitorToolWindowFactory : DumbAware, ToolWindowFactory {
  override fun isApplicable(project: Project): Boolean {
    return StudioFlags.ADB_DEVICE_MONITOR_TOOL_WINDOW_ENABLED.get() && isAndroidEnvironment(project)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    //TODO: Custom Icon
    toolWindow.setIcon(StudioIcons.Shell.ToolWindows.DEVICE_EXPLORER)
    toolWindow.isAvailable = true
    toolWindow.setToHideOnEmptyContent(true)
    toolWindow.title = TOOL_WINDOW_ID
    val adbService = project.getService(AdbDeviceListService::class.java)
    val deviceNameRendererFactory: DeviceNameRendererFactory = AdbDeviceNameRendererFactory(adbService)
    val model = DeviceMonitorModel()
    val view = DeviceMonitorViewImpl(project, deviceNameRendererFactory, model)
    val controller = DeviceMonitorController(project, model, view, adbService)
    controller.setup()
    val contentManager = toolWindow.contentManager
    val toolWindowContent = contentManager.factory.createContent(view.component, "", true)
    contentManager.addContent(toolWindowContent)
  }

  companion object {
    /**
     * IntelliJ tool window ID. This should be the same value as the "id" attribute of the "toolWindow" XML tag.
     */
    const val TOOL_WINDOW_ID = "Device Monitor"
  }
}