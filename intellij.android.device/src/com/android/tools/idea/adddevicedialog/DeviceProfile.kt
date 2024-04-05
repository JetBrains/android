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

interface DeviceProfile {
  val source: Class<out DeviceSource>

  // TODO: alert icon, text

  val apiRange: Range<Int>

  val manufacturer: String
  val name: String
  val resolution: Resolution
  val displayDensity: Int
  val isVirtual: Boolean
  val isRemote: Boolean
  val abis: List<Abi>

  /** Indicates that the device already exists, and we cannot create another. */
  val isAlreadyPresent: Boolean
  /** An estimate of how long it will take to acquire the device. */
  val availabilityEstimate: Duration

  fun toBuilder(): Builder

  abstract class Builder {
    lateinit var apiRange: Range<Int>

    lateinit var manufacturer: String
    lateinit var name: String
    lateinit var resolution: Resolution
    var displayDensity: Int = 0
    var isVirtual: Boolean = false
    var isRemote: Boolean = false
    lateinit var abis: List<Abi>

    /** Indicates that the device already exists, and we cannot create another. */
    var isAlreadyPresent: Boolean = false
    /** An estimate of how many seconds it will take to acquire the device. */
    var availabilityEstimate: Duration = Duration.ZERO

    abstract fun build(): DeviceProfile

    fun copyFrom(profile: DeviceProfile) {
      apiRange = profile.apiRange
      manufacturer = profile.manufacturer
      name = profile.name
      resolution = profile.resolution
      displayDensity = profile.displayDensity
      isVirtual = profile.isVirtual
      isRemote = profile.isRemote
      abis = profile.abis
      isAlreadyPresent = profile.isAlreadyPresent
      availabilityEstimate = profile.availabilityEstimate
    }
  }
}
