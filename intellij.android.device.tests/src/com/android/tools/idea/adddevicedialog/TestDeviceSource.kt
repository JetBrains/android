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
package com.android.tools.idea.adddevicedialog

import com.android.sdklib.deviceprovisioner.Resolution
import com.android.sdklib.devices.Abi
import com.google.common.collect.Range
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow

class TestDeviceSource : DeviceSource {
  override val profiles = mutableListOf<DeviceProfile>()

  val selectedProfile = MutableStateFlow<DeviceProfile?>(null)

  fun add(device: TestDevice) {
    profiles.add(device)
  }

  override fun WizardPageScope.selectionUpdated(profile: DeviceProfile) {
    selectedProfile.value = profile
  }
}

data class TestDevice(
  override val apiRange: Range<Int> = Range.closed(24, 34),
  override val manufacturer: String = "Google",
  override val name: String,
  override val resolution: Resolution = Resolution(2000, 1200),
  override val displayDensity: Int = 300,
  override val isVirtual: Boolean = true,
  override val isRemote: Boolean = false,
  override val abis: List<Abi> = listOf(Abi.ARM64_V8A),
  override val isAlreadyPresent: Boolean = false,
  override val availabilityEstimate: Duration = Duration.ZERO,
) : DeviceProfile {
  override val source: Class<out DeviceSource>
    get() = TestDeviceSource::class.java

  override fun toBuilder(): Builder = Builder().apply { copyFrom(this@TestDevice) }

  class Builder : DeviceProfile.Builder() {
    override fun build(): TestDevice =
      TestDevice(
        apiRange = apiRange,
        manufacturer = manufacturer,
        name = name,
        resolution = resolution,
        displayDensity = displayDensity,
        isVirtual = isVirtual,
        isRemote = isRemote,
        abis = abis,
        isAlreadyPresent = isAlreadyPresent,
        availabilityEstimate = availabilityEstimate,
      )
  }
}
