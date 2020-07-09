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
package com.android.tools.idea.adb.wireless

import com.android.ddmlib.IDevice

/**
 * Snapshot of a device known to the underlying ADB implementation
 *
 * [id] identifies a device (physical or emulator) and can be used to
 * match [AdbDevice] instance created for the same device at different
 * times. It is *not* meant to be a user-friendly string.
 */
data class AdbDevice(val id: String, val name: String) {
  val displayString: String
    get() {
      return name
    }

  companion object {
    fun fromIDevice(d: IDevice, name: String): AdbDevice {
      return AdbDevice(d.serialNumber, name)
    }
  }
}
