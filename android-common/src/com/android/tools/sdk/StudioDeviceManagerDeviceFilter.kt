/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.sdk

import com.android.sdklib.devices.Device
import com.android.tools.idea.flags.StudioFlags

class StudioDeviceManagerDeviceFilter : DeviceManagerDeviceFilter {
  override fun isSupportedDevice(device: Device): Boolean =
    when {
      Device.isAiGlasses(device) -> StudioFlags.AI_GLASSES_DEVICE_SUPPORT_ENABLED.get()
      Device.isXrGlasses(device) -> StudioFlags.XR_GLASSES_DEVICE_SUPPORT_ENABLED.get()
      Device.isXrHeadset(device) -> StudioFlags.XR_DEVICE_SUPPORT_ENABLED.get()
      else -> true
    }
}