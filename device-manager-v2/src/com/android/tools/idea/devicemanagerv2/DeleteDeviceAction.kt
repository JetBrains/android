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

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.adtui.actions.componentToRestoreFocusTo
import com.android.tools.idea.deviceprovisioner.launchCatchingDeviceActionException
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.PHYSICAL_DELETE_ACTION
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.VIRTUAL_DELETE_ACTION
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageDialogBuilder
import icons.StudioIcons

class DeleteAction : DumbAwareAction("Delete", "Delete this device", StudioIcons.Common.DELETE) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.updateFromDeviceActionOrDeactivateAction(DeviceHandle::deleteAction)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val deviceRowData = e.deviceRowData() ?: return
    val deviceHandle = deviceRowData.handle ?: return
    val deleteAction = deviceHandle.deleteAction ?: return

    val isRunning = deviceRowData.status == DeviceRowData.Status.ONLINE
    val runningSuffix = " This will stop the device.".takeIf { isRunning } ?: ""
    if (
      MessageDialogBuilder.yesNo(
          "Confirm Deletion",
          "Do you really want to delete ${deviceHandle.state.properties.title}?$runningSuffix"
        )
        .ask(e.componentToRestoreFocusTo())
    ) {
      DeviceManagerUsageTracker.logDeviceManagerEvent(
        when {
          deviceHandle.state.properties.isVirtual == true -> VIRTUAL_DELETE_ACTION
          else -> PHYSICAL_DELETE_ACTION
        }
      )

      deviceHandle.launchCatchingDeviceActionException(project = e.project) {
        if (isRunning) {
          deactivationAction?.deactivate()
        }

        deleteAction.delete()
      }
    }
  }
}
