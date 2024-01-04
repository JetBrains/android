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
package com.android.tools.idea.wearwhs.communication

import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsText
import com.android.tools.idea.wearwhs.WHS_CAPABILITIES
import com.android.tools.idea.wearwhs.WhsCapability

const val whsUri: String = "content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config"

/**
 * Content provider implementation of [WearHealthServicesDeviceManager].
 *
 * This class uses the content provider for the synthetic HAL in WHS to sync the current state
 * of the UI to the selected Wear OS device.
 */
internal class ContentProviderDeviceManager(private val adbSession: AdbSession, private var capabilities: List<WhsCapability> = WHS_CAPABILITIES) : WearHealthServicesDeviceManager {
  private var serialNumber: String? = null

  // TODO(b/309608749): Implement loadCapabilities method
  override suspend fun loadCapabilities() = capabilities

  // TODO(b/309607065): Implement loadCurrentCapabilityStates method
  override suspend fun loadCurrentCapabilityStates() = capabilities.associateWith {
    OnDeviceCapabilityState(false, null)
  }

  override fun setSerialNumber(serialNumber: String) {
    this.serialNumber = serialNumber
  }

  // TODO(b/305924111) Implement loadOngoingExercise method
  override suspend fun loadOngoingExercise() = false

  private fun contentUpdateCapability(key: String, value: Boolean): String {
    return "content update --uri $whsUri --bind $key:b:$value"
  }

  private suspend fun setCapability(capability: WhsCapability, newValue: Boolean) {
    if (serialNumber == null) {
      // TODO: Log this error
      return
    }

    val device = DeviceSelector.fromSerialNumber(serialNumber!!)
    val contentUpdateCommand = contentUpdateCapability(capability.key.name, newValue)
    adbSession.deviceServices.shellAsText(device, contentUpdateCommand)
  }

  override suspend fun enableCapability(capability: WhsCapability) {
    setCapability(capability, true)
  }

  override suspend fun disableCapability(capability: WhsCapability) {
    setCapability(capability, false)
  }

  // TODO(b/305924073): Implement override methods
  override suspend fun overrideValue(capability: WhsCapability, value: Float?) {}
}

