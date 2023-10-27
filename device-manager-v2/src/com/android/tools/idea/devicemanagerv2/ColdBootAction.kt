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
import com.android.tools.idea.deviceprovisioner.deviceHandle
import com.android.tools.idea.deviceprovisioner.launchCatchingDeviceActionException
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.VIRTUAL_COLD_BOOT_NOW_ACTION
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/** Activates the device with a cold boot. */
class ColdBootAction() : DumbAwareAction("Cold Boot") {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.updateFromDeviceAction(DeviceHandle::coldBootAction)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val deviceHandle = e.deviceHandle()
    val coldBootAction = deviceHandle?.coldBootAction ?: return

    DeviceManagerUsageTracker.logDeviceManagerEvent(VIRTUAL_COLD_BOOT_NOW_ACTION)

    deviceHandle.launchCatchingDeviceActionException(project = e.project) {
      coldBootAction.activate()
    }
  }
}
