/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager.actions

import com.android.sdklib.devices.Device
import com.android.tools.idea.deviceManager.displayList.EmulatorDisplayList.Companion.deviceManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.Messages
import java.awt.event.ActionEvent

/**
 * Action to delete a selected [Device].
 */
class DeleteDeviceAction(provider: DeviceProvider) : DeviceUiAction(provider, "Delete") {
  override fun actionPerformed(e: ActionEvent) {
    val device = provider.device
    val result = Messages.showYesNoDialog(
      provider.project,
      "Do you really want to delete Device ${device!!.displayName}?",
      "Confirm Deletion",
      AllIcons.General.QuestionDialog
    )
    if (result == Messages.YES) {
      deviceManager.deleteDevice(device)
      provider.refreshDevices()
      provider.selectDefaultDevice()
    }
  }

  override fun isEnabled(): Boolean {
    val device = provider.device
    return device != null && deviceManager.isUserDevice(device)
  }
}