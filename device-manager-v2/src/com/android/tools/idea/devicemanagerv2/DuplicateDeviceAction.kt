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
import com.android.tools.idea.deviceprovisioner.deviceHandle
import com.android.tools.idea.deviceprovisioner.launchCatchingDeviceActionException
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.VIRTUAL_DUPLICATE_ACTION
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class DuplicateDeviceAction :
  DumbAwareAction("Duplicate", "Duplicate this device", AllIcons.Actions.Copy) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.updateFromDeviceAction(DeviceHandle::duplicateAction)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val deviceHandle = e.deviceHandle()
    val duplicateAction = deviceHandle?.duplicateAction ?: return

    DeviceManagerUsageTracker.logDeviceManagerEvent(VIRTUAL_DUPLICATE_ACTION)

    deviceHandle.launchCatchingDeviceActionException(project = e.project) {
      duplicateAction.duplicate(e.componentToRestoreFocusTo())
    }
  }
}
