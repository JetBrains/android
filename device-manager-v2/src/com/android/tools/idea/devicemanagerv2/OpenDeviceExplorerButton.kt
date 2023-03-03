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
package com.android.tools.idea.devicemanagerv2

import com.android.adblib.serialNumber
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.tools.idea.device.explorer.DeviceExplorerService
import com.android.tools.idea.file.explorer.toolwindow.DeviceExplorer
import com.android.tools.idea.flags.StudioFlags
import com.google.wireless.android.sdk.stats.DeviceManagerEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

internal class OpenDeviceExplorerButton(private val project: Project, private val handle: DeviceHandle) :
  IconButton(AllIcons.Actions.MenuOpen) {

  private var isVirtual = false

  init {
    toolTipText = DeviceManagerBundle.message("openDeviceExplorerButton.tooltip")

    // TODO: log usage in UsageTracker
    addActionListener {
      DeviceManagerUsageTracker.logEvent(
        DeviceManagerEvent.newBuilder()
          .setKind(
            if (isVirtual) DeviceManagerEvent.EventKind.VIRTUAL_DEVICE_FILE_EXPLORER_ACTION
            else DeviceManagerEvent.EventKind.PHYSICAL_DEVICE_FILE_EXPLORER_ACTION
          )
          .build()
      )

      // We need to use an invokeLater to avoid a NPE, for convoluted
      // reasons documented in b/200165926.
      ApplicationManager.getApplication().invokeLater {
        val serialNumber = handle.state.connectedDevice?.serialNumber
        if (!project.isDisposed && serialNumber != null) {
          when {
            StudioFlags.MERGED_DEVICE_FILE_EXPLORER_AND_DEVICE_MONITOR_TOOL_WINDOW_ENABLED.get() ->
              DeviceExplorerService.openAndShowDevice(project, serialNumber)
            else -> DeviceExplorer.openAndShowDevice(project, serialNumber)
          }
        }
      }
    }
  }

  fun updateState(state: DeviceRowData) {
    isEnabled = state.handle.state is DeviceState.Connected
    isVirtual = state.isVirtual
  }
}
