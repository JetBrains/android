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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.sdklib.deviceprovisioner.Resolution
import com.android.sdklib.devices.Abi
import com.google.common.collect.Range
import kotlin.time.Duration

/**
 * A description of a device that can be displayed in a [DeviceTable].
 *
 * This interface uses an "extensible data class" pattern. Concrete subtypes are implemented as data
 * classes, and are free to add additional fields. The Builder class exists in order to allow
 * updates to the common fields generically. (This ended up being mostly irrelevant after the
 * de-unification of the dialog.)
 */
interface DeviceProfile {
  val apiRange: Range<Int>
  val manufacturer: String
  val name: String
  val resolution: Resolution
  val displayDensity: Int
  /** The diagonal length of the screen, in inches, or 0 if unknown. */
  val displayDiagonalLength: Double
    get() = 0.0

  val isRound: Boolean
    get() = false

  val isVirtual: Boolean
  val isRemote: Boolean
  val abis: List<Abi>
  val formFactor: String

  fun toBuilder(): Builder

  @Composable fun Icon(modifier: Modifier)

  /**
   * Subtypes should extend this Builder with the additional fields needed, and create their own
   * [copyFrom] overload that calls super.copyFrom() to initialize the superclass fields.
   */
  abstract class Builder {
    lateinit var apiRange: Range<Int>
    lateinit var manufacturer: String
    lateinit var name: String
    lateinit var resolution: Resolution
    var displayDensity: Int = 0
    var displayDiagonalLength: Double = 0.0
    var isRound = false
    var isVirtual: Boolean = false
    var isRemote: Boolean = false
    lateinit var abis: List<Abi>
    lateinit var formFactor: String

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
      displayDiagonalLength = profile.displayDiagonalLength
      isRound = profile.isRound
      isVirtual = profile.isVirtual
      isRemote = profile.isRemote
      abis = profile.abis
      formFactor = profile.formFactor
    }
  }
}

fun DeviceProfile.update(block: DeviceProfile.Builder.() -> Unit): DeviceProfile =
  toBuilder().apply(block).build()

object FormFactors {
  const val PHONE = "Phone"
  const val TABLET = "Tablet"
  const val WEAR = "Wear OS"
  const val TV = "TV"
  const val AUTO = "Automotive"
  const val DESKTOP = "Desktop"
  const val XR = "XR"
}
