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
import com.android.tools.idea.wearwhs.WhsDataType
import com.android.tools.idea.wearwhs.WhsDataValue

/** Interface for the Wear Health Services Device Manager. */
internal interface WearHealthServicesDeviceManager {
  /** @return Capabilities of WHS. */
  fun getCapabilities(): List<WhsCapability>

  /**
   * Checks if there's an ongoing exercise
   *
   * @return true if there's an ongoing exercise, false otherwise.
   */
  suspend fun loadActiveExercise(): Result<Boolean>

  /** Set multiple WHS capabilities on the device. */
  suspend fun setCapabilities(capabilityUpdates: Map<WhsDataType, Boolean>): Result<Unit>

  /** Overrides the sensor value for the given capabilities. */
  suspend fun overrideValues(overrideUpdates: List<WhsDataValue>): Result<Unit>

  /** Loads the current state from WHS to compare with the current UI. */
  suspend fun loadCurrentCapabilityStates(): Result<Map<WhsDataType, CapabilityState>>

  /** Deletes all data from the WHS content provider */
  suspend fun clearContentProvider(): Result<Unit>

  /** Returns if the WHS version is supported. */
  suspend fun isWhsVersionSupported(): Result<Boolean>

  /** Sets the serial number of the emulator to connect. */
  fun setSerialNumber(serialNumber: String)

  /** Sends an event trigger to the device. */
  suspend fun triggerEvent(eventTrigger: EventTrigger): Result<Unit>
}

internal data class CapabilityState(val enabled: Boolean, val overrideValue: WhsDataValue) {
  fun enable(): CapabilityState = CapabilityState(true, overrideValue)

  fun disable(): CapabilityState = CapabilityState(false, overrideValue)

  fun override(overrideValue: WhsDataValue): CapabilityState {
    return CapabilityState(enabled, overrideValue)
  }

  fun clearOverride(): CapabilityState {
    return withNoValue(enabled, overrideValue.type)
  }

  companion object {
    fun withNoValue(enabled: Boolean, type: WhsDataType): CapabilityState =
      if (enabled) enabled(type) else disabled(type)

    fun enabled(type: WhsDataType) = CapabilityState(true, type.noValue())

    fun disabled(type: WhsDataType) = CapabilityState(false, type.noValue())
  }
}

internal class ConnectionLostException(message: String, cause: Throwable? = null) :
  Exception(message, cause)
