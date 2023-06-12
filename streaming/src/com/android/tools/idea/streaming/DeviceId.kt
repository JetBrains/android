/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.streaming

import com.android.tools.idea.streaming.emulator.EmulatorId

/**
 * Identifying information for a running Emulator or a connected physical device.
 */
sealed class DeviceId(val serialNumber: String) : Comparable<DeviceId> {

  data class EmulatorDeviceId(val emulatorId: EmulatorId) : DeviceId(emulatorId.serialNumber)

  class PhysicalDeviceId(serialNumber: String) : DeviceId(serialNumber) {

    override fun equals(other: Any?): Boolean = this === other || other is PhysicalDeviceId && other.serialNumber == serialNumber

    override fun hashCode(): Int = serialNumber.hashCode()
  }

  /**
   * Physical devices are sorted after AVDs. Within each group devices are sorted by serial number.
   */
  override fun compareTo(other: DeviceId): Int {
    return when {
      this::class == other::class -> serialNumber.compareTo(other.serialNumber)
      this is EmulatorDeviceId -> -1
      else -> 1
    }
  }

  companion object {
    fun ofEmulator(emulatorId: EmulatorId): DeviceId = EmulatorDeviceId(emulatorId)

    fun ofPhysicalDevice(serialNumber: String): DeviceId = PhysicalDeviceId(serialNumber)
  }
}