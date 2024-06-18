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
package com.android.tools.idea.avd

import com.android.sdklib.AndroidVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VirtualDeviceProfileTest {
  @Test
  fun builder() {
    val devices = readTestDevices()
    val device = devices.first { it.id == "pixel_8" }
    val deviceProfile =
      VirtualDeviceProfile.Builder()
        .apply { initializeFromDevice(device, setOf(AndroidVersion(34))) }
        .build()

    assertThat(deviceProfile.name).startsWith("Pixel 8")
    assertThat(deviceProfile.toBuilder().build()).isEqualTo(deviceProfile)
    assertThat(deviceProfile.update { name = "SquarePhone" }.name).isEqualTo("SquarePhone")
  }
}
