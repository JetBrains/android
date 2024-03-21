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

class TestDeviceSource : DeviceSource {
  override val profiles = mutableListOf<DeviceProfile>()

  data class Device(
    override val source: DeviceSource,
    override val apiRange: Range<Int>,
    override val manufacturer: String,
    override val name: String,
    override val resolution: Resolution,
    override val displayDensity: Int,
    override val isVirtual: Boolean,
    override val isRemote: Boolean,
    override val abis: List<Abi>,
    override val isAlreadyPresent: Boolean,
    override val availabilityEstimateSeconds: Int,
  ) : DeviceProfile

  fun add(device: Device) {
    profiles.add(device)
  }

  fun device(
    apiRange: Range<Int> = Range.closed(24, 34),
    manufacturer: String = "Google",
    name: String,
    resolution: Resolution = Resolution(2000, 1200),
    displayDensity: Int = 300,
    isVirtual: Boolean = true,
    isRemote: Boolean = false,
    abis: List<Abi> = listOf(Abi.ARM64_V8A),
    isAlreadyPresent: Boolean = false,
    availabilityEstimateSeconds: Int = 0,
  ): Device =
    Device(
      this,
      apiRange,
      manufacturer,
      name,
      resolution,
      displayDensity,
      isVirtual,
      isRemote,
      abis,
      isAlreadyPresent,
      availabilityEstimateSeconds,
    )

  override fun WizardPageScope.selectionUpdated(profile: DeviceProfile) {}
}
