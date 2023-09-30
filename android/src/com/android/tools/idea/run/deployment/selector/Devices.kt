/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.selector

import com.android.sdklib.deviceprovisioner.DeviceId

internal object Devices {
  fun containsAnotherDeviceWithSameName(devices: Collection<Device>, device: Device): Boolean =
    devices.any { it.key != device.key && it.name == device.name }

  fun getBootOption(device: Device, target: Target): String? {
    return when {
      device.isConnected -> null
      device.snapshots.isEmpty() -> null
      else -> target.getText(device)
    }
  }

  // TODO: key is not the right disambiguator
  fun getText(device: Device, key: DeviceId? = null, bootOption: String? = null): String =
    buildString {
      append(device.name)
      key?.let { append(" [$it]") }
      bootOption?.let { append(" - $it") }
    }
}
