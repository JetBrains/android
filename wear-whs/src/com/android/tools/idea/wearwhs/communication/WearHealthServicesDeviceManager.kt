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

import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WhsCapability

/**
 * Interface for the Wear Health Services Device Manager.
 */
internal interface WearHealthServicesDeviceManager {
  /**
   * Loads the capabilities of the WHS in current device.
   *
   * @return the capabilities of the device.
   */
  suspend fun loadCapabilities(): List<WhsCapability>

  /**
   * Checks if there's an ongoing exercise
   *
   * @return true if there's an ongoing exercise, false otherwise.
   */
  suspend fun loadOngoingExercise(): Boolean

  /**
   * Enables a capability of WHS on the device.
   */
  suspend fun enableCapability(capability: WhsCapability)

  /**
   * Disables a capability of WHS on the device.
   */
  suspend fun disableCapability(capability: WhsCapability)

  /**
   * Overrides the sensor value for the given capability.
   */
  suspend fun overrideValue(capability: WhsCapability, value: Number?)

  /**
   * Loads the current state from WHS to compare with the current UI.
   */
  suspend fun loadCurrentCapabilityStates(): Map<WhsCapability, OnDeviceCapabilityState>

  /**
   * Sets the serial number of the emulator to connect.
   */
  fun setSerialNumber(serialNumber: String)
  suspend fun triggerEvent(eventTrigger: EventTrigger)
}

internal data class OnDeviceCapabilityState(
  var enabled: Boolean,
  var overrideValue: Float?,
)

internal class ConnectionLostException(message: String) : Exception(message)
