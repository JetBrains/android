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
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.tools.idea.device.explorer.DeviceExplorerService
import com.android.tools.idea.deviceprovisioner.deviceHandle
import com.google.wireless.android.sdk.stats.DeviceManagerEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class OpenDeviceExplorerAction :
  DumbAwareAction(
    "Open in Device Explorer",
    DeviceManagerBundle.message("openDeviceExplorerButton.tooltip"),
    AllIcons.Actions.MenuOpen,
  ) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    if (e.project == null) {
      e.presentation.isEnabledAndVisible = false
    } else {
      e.presentation.isEnabled = e.deviceHandle()?.state is DeviceState.Connected
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val deviceRowData = e.deviceRowData() ?: return
    val serialNumber = deviceRowData.handle?.state?.connectedDevice?.serialNumber ?: return
    val project = e.project ?: return

    if (!project.isDisposed) {
      DeviceManagerUsageTracker.logDeviceManagerEvent(
        if (deviceRowData.isVirtual)
          DeviceManagerEvent.EventKind.VIRTUAL_DEVICE_FILE_EXPLORER_ACTION
        else DeviceManagerEvent.EventKind.PHYSICAL_DEVICE_FILE_EXPLORER_ACTION
      )

      DeviceExplorerService.openAndShowDevice(project, serialNumber)
    }
  }
}
