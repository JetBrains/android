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
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.VIRTUAL_WIPE_DATA_ACTION
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageDialogBuilder
import icons.StudioIcons

class WipeDataAction :
  DumbAwareAction("Wipe Data", "Wipe the user data of this AVD", StudioIcons.Common.CLEAR) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.updateFromDeviceActionOrDeactivateAction(DeviceHandle::wipeDataAction)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val deviceRowData = e.deviceRowData() ?: return
    val deviceHandle = deviceRowData.handle ?: return
    val wipeDataAction = deviceHandle.wipeDataAction ?: return

    val isRunning = deviceRowData.status == DeviceRowData.Status.ONLINE
    val runningSuffix = " This will stop the device.".takeIf { isRunning } ?: ""
    if (
      MessageDialogBuilder.yesNo(
          "Confirm Data Wipe",
          "Do you really want to wipe user files from ${deviceHandle.state.properties.title}?$runningSuffix",
        )
        .ask(e.componentToRestoreFocusTo())
    ) {
      deviceHandle.launchCatchingDeviceActionException(project = e.project) {
        if (isRunning) {
          deactivationAction?.deactivate()
        }

        DeviceManagerUsageTracker.logDeviceManagerEvent(VIRTUAL_WIPE_DATA_ACTION)
        wipeDataAction.wipeData()
      }
    }
  }
}
