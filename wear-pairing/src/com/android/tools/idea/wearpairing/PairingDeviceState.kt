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
package com.android.tools.idea.wearpairing

/**
 * Used to persist Pairing Device State. Class fields need default values to allow serialization.
 */
data class PairingDeviceState(
  var deviceID: String = "?",
  var displayName: String = "?",
  var apiLevel: Int = 0,
  var isEmulator: Boolean = false,
  var isWearDevice: Boolean = false,
  var hasPlayStore: Boolean = false,
)

fun PairingDeviceState.toPairingDevice(connectionSate: ConnectionState): PairingDevice =
  PairingDevice(
    deviceID = deviceID,
    displayName = displayName,
    apiLevel = apiLevel,
    isEmulator = isEmulator,
    isWearDevice = isWearDevice,
    hasPlayStore = hasPlayStore,
    state = connectionSate,
  )

fun PairingDevice.toPairingDeviceState(): PairingDeviceState =
  PairingDeviceState().apply {
    deviceID = this@toPairingDeviceState.deviceID
    displayName = this@toPairingDeviceState.displayName
    apiLevel = this@toPairingDeviceState.apiLevel
    isEmulator = this@toPairingDeviceState.isEmulator
    isWearDevice = this@toPairingDeviceState.isWearDevice
    hasPlayStore = this@toPairingDeviceState.hasPlayStore
  }
