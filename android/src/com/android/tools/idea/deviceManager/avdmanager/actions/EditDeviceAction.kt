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

import com.android.tools.idea.deviceManager.avdmanager.ConfigureDeviceModel
import com.android.tools.idea.deviceManager.displayList.EmulatorDisplayList.Companion.deviceManager
import java.awt.event.ActionEvent

/**
 * Action to edit a given device
 */
class EditDeviceAction @JvmOverloads constructor(provider: DeviceProvider, text: String = "Edit") : DeviceUiAction(provider, text) {
  override fun actionPerformed(e: ActionEvent) {
    showHardwareProfileWizard(ConfigureDeviceModel(provider, provider.device, false))
  }

  override fun isEnabled(): Boolean {
    val device = provider.device ?: return false
    return deviceManager.isUserDevice(device)
  }
}