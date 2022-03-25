/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.util

import com.android.ddmlib.IDevice
import kotlinx.coroutines.guava.await

/**
 * Provides an IDevice for a given device identifier.
 *
 * The device identifier is the serial number for physical devices and the AVD name for emulators.
 */
internal interface AdbAdapter {
  suspend fun getDevice(deviceId: String): IDevice?
}

suspend fun IDevice.getDeviceId(): String = if (isEmulator) avdData.await().name ?: "" else serialNumber
