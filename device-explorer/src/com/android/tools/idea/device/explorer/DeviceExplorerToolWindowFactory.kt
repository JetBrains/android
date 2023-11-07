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
package com.android.tools.idea.device.explorer

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.device.explorer.common.DeviceExplorerTabController
import com.android.tools.idea.device.explorer.files.DeviceExplorerFileManager
import com.android.tools.idea.device.explorer.files.DeviceFileExplorerControllerImpl
import com.android.tools.idea.device.explorer.files.DeviceFileExplorerModel
import com.android.tools.idea.device.explorer.files.ui.DeviceFileExplorerViewImpl
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorControllerImpl
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorModel
import com.android.tools.idea.device.explorer.monitor.adbimpl.AdbDeviceService
import com.android.tools.idea.device.explorer.monitor.processes.DeviceProcessService
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorViewImpl
import com.android.tools.idea.device.explorer.ui.DeviceExplorerViewImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import icons.StudioIcons
import java.nio.file.Path

class DeviceExplorerToolWindowFactory : DumbAware, ToolWindowFactory {

  override fun isApplicable(project: Project) = true

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.setIcon(StudioIcons.Shell.ToolWindows.DEVICE_EXPLORER)
    toolWindow.isAvailable = true
    toolWindow.setToHideOnEmptyContent(true)
    toolWindow.title = TOOL_WINDOW_ID
    val model = DeviceExplorerModel(project)
    val view = DeviceExplorerViewImpl(project, model, TOOL_WINDOW_ID)
    val tabController = listOf(
      createDeviceFilesController(project),
      createDeviceMonitorController(project)
    )
    val deviceExplorerController = DeviceExplorerController(project, model, view, tabController)
    deviceExplorerController.setup()
    val contentManager = toolWindow.contentManager
    val toolWindowContent = contentManager.factory.createContent(view.component, "", true)
    contentManager.addContent(toolWindowContent)
  }

  private fun createDeviceMonitorController(project: Project): DeviceExplorerTabController {
    val adbService = project.getService(AdbDeviceService::class.java)
    val processService = project.getService(DeviceProcessService::class.java)
    val model = DeviceMonitorModel(project, processService)
    val view = DeviceMonitorViewImpl(project, model)
    return DeviceMonitorControllerImpl(project, model, view, adbService)
  }

  private fun createDeviceFilesController(project: Project): DeviceExplorerTabController {
    val fileManager = project.getService(DeviceExplorerFileManager::class.java)
    val model = DeviceFileExplorerModel()
    val view = DeviceFileExplorerViewImpl(project, model, TOOL_WINDOW_ID)
    return DeviceFileExplorerControllerImpl(project, model, view, fileManager,
                                        object : DeviceFileExplorerControllerImpl.FileOpener {
                                          @UiThread
                                          override suspend fun openFile(localPath: Path) {
                                            fileManager.openFile(localPath)
                                          }
                                        })
  }

  companion object {
    /**
     * IntelliJ tool window ID. This should be the same value as the "id" attribute of the "toolWindow" XML tag.
     */
    const val TOOL_WINDOW_ID = "Device Explorer"
    private const val DEVICE_EXPLORER_ENABLED = "android.device.explorer.enabled"
  }
}