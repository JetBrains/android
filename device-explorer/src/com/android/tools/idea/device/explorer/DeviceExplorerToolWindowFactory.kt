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
import com.android.tools.idea.device.explorer.files.DeviceExplorerFileManager
import com.android.tools.idea.device.explorer.files.DeviceFileExplorerController
import com.android.tools.idea.device.explorer.files.DeviceFileExplorerModel
import com.android.tools.idea.device.explorer.files.adbimpl.AdbDeviceFileSystemService
import com.android.tools.idea.device.explorer.files.ui.DeviceFileExplorerViewImpl
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorController
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorModel
import com.android.tools.idea.device.explorer.monitor.adbimpl.AdbDeviceService
import com.android.tools.idea.device.explorer.monitor.processes.DeviceProcessService
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorViewImpl
import com.android.tools.idea.device.explorer.ui.DeviceExplorerViewImpl
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.isAndroidEnvironment
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import icons.StudioIcons
import java.nio.file.Path

class DeviceExplorerToolWindowFactory : DumbAware, ToolWindowFactory {

  override fun isApplicable(project: Project) =
    StudioFlags.MERGED_DEVICE_FILE_EXPLORER_AND_DEVICE_MONITOR_TOOL_WINDOW_ENABLED.get()  && isAndroidEnvironment(project)

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.setIcon(StudioIcons.Shell.ToolWindows.DEVICE_EXPLORER)
    toolWindow.isAvailable = true
    toolWindow.setToHideOnEmptyContent(true)
    toolWindow.title = TOOL_WINDOW_ID
    val model = DeviceExplorerModel(project)
    val view = DeviceExplorerViewImpl(project, model, TOOL_WINDOW_ID)
    val deviceMonitorController = createDeviceMonitorController(project)
    val deviceFilesController = createDeviceFilesController(project)
    val deviceExplorerController = DeviceExplorerController(project, model, view, deviceFilesController, deviceMonitorController)
    deviceExplorerController.setup()
    val contentManager = toolWindow.contentManager
    val toolWindowContent = contentManager.factory.createContent(view.component, "", true)
    contentManager.addContent(toolWindowContent)
  }

  private fun createDeviceMonitorController(project: Project): DeviceMonitorController {
    val adbService = project.getService(AdbDeviceService::class.java)
    val processService = project.getService(DeviceProcessService::class.java)
    val model = DeviceMonitorModel(processService)
    val view = DeviceMonitorViewImpl()
    return DeviceMonitorController(project, model, view, adbService)
  }

  private fun createDeviceFilesController(project: Project): DeviceFileExplorerController {
    val fileManager = project.getService(DeviceExplorerFileManager::class.java)
    val model = DeviceFileExplorerModel()
    val view = DeviceFileExplorerViewImpl(project, model, TOOL_WINDOW_ID)
    return DeviceFileExplorerController(project, model, view, fileManager,
                                        object : DeviceFileExplorerController.FileOpener {
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
  }
}