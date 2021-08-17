/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.ddms

/**
 * DeviceNameProperties holds the system properties from a device that are required to render the name of the device.
 */
data class DeviceNameProperties(val model: String?,
                                val manufacturer: String?,
                                val buildVersion: String?,
                                val apiLevel: String?) {
  fun getName() = getName(model, manufacturer)

  companion object {
    @JvmStatic
    fun getName(model: String?, manufacturer: String?): String {
      if (model == null && manufacturer == null) {
        return "Device"
      }

      if (model == null) {
        return "$manufacturer Device"
      }

      if (manufacturer == null) {
        return model
      }

      return "$manufacturer $model"
    }
  }
}
