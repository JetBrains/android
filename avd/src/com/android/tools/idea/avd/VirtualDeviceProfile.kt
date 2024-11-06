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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.android.resources.ScreenRound
import com.android.sdklib.deviceprovisioner.Resolution
import com.android.sdklib.devices.Abi
import com.android.sdklib.devices.Device
import com.android.tools.idea.adddevicedialog.DeviceProfile
import com.android.tools.idea.adddevicedialog.FormFactors
import com.google.common.collect.Range
import icons.StudioIconsCompose
import kotlin.math.max

/** A [DeviceProfile] based on a [Device], used for creating an AVD. */
@Immutable
internal data class VirtualDeviceProfile(
  val device: Device,
  override val apiRange: Range<Int>,
  override val manufacturer: String,
  override val name: String,
  override val resolution: Resolution,
  override val displayDensity: Int,
  override val displayDiagonalLength: Double,
  override val isRound: Boolean,
  override val abis: List<Abi>,
  override val formFactor: String,
  val isDeprecated: Boolean,
  val isGooglePlaySupported: Boolean,
) : DeviceProfile {

  override val isVirtual
    get() = true

  override val isRemote
    get() = false

  @Composable
  override fun Icon(modifier: Modifier) {
    val iconKey =
      when (formFactor) {
        FormFactors.TV -> StudioIconsCompose.DeviceExplorer.VirtualDeviceTv
        FormFactors.AUTO -> StudioIconsCompose.DeviceExplorer.VirtualDeviceCar
        FormFactors.WEAR -> StudioIconsCompose.DeviceExplorer.VirtualDeviceWear
        FormFactors.XR -> StudioIconsCompose.DeviceExplorer.VirtualDeviceHeadset
        // TODO: Add icon for tablet
        else -> StudioIconsCompose.DeviceExplorer.VirtualDevicePhone
      }
    org.jetbrains.jewel.ui.component.Icon(
      iconKey,
      contentDescription = "$formFactor AVD",
      modifier = modifier,
      iconClass = StudioIconsCompose::class.java,
    )
  }

  override fun toBuilder(): Builder = Builder().apply { copyFrom(this@VirtualDeviceProfile) }

  class Builder : DeviceProfile.Builder() {
    lateinit var device: Device
    var isDeprecated: Boolean = false
    var isGooglePlaySupported: Boolean = false

    fun initializeFromDevice(device: Device) {
      this.device = device
      apiRange = device.androidVersionRange
      manufacturer = device.manufacturer
      name = device.displayName
      val screen = device.defaultHardware.screen
      resolution = Resolution(screen.xDimension, screen.yDimension)
      displayDensity = screen.pixelDensity.dpiValue
      displayDiagonalLength = screen.diagonalLength
      isRound = screen.screenRound == ScreenRound.ROUND
      abis = device.defaultHardware.supportedAbis + device.defaultHardware.translatedAbis
      formFactor = device.formFactor
      isDeprecated = device.isDeprecated
      isGooglePlaySupported = device.hasPlayStore()
    }

    fun copyFrom(profile: VirtualDeviceProfile) {
      super.copyFrom(profile)
      device = profile.device
      isDeprecated = profile.isDeprecated
      isGooglePlaySupported = profile.isGooglePlaySupported
    }

    override fun build(): VirtualDeviceProfile =
      VirtualDeviceProfile(
        device = device,
        apiRange = apiRange,
        manufacturer = manufacturer,
        name = name,
        resolution = resolution,
        displayDensity = displayDensity,
        displayDiagonalLength = displayDiagonalLength,
        isRound = isRound,
        abis = abis,
        formFactor = formFactor,
        isDeprecated = isDeprecated,
        isGooglePlaySupported = isGooglePlaySupported,
      )
  }
}

private val Device.androidVersionRange: Range<Int>
  get() =
    allSoftware
      .map {
        val minLevel = max(1, it.minSdkLevel)
        if (it.maxSdkLevel == Int.MAX_VALUE) Range.atLeast(minLevel)
        else Range.closed(minLevel, it.maxSdkLevel)
      }
      .reduce(Range<Int>::span)

internal val Device.formFactor: String
  get() =
    when {
      Device.isWear(this) -> FormFactors.WEAR
      Device.isAutomotive(this) -> FormFactors.AUTO
      Device.isTv(this) -> FormFactors.TV
      Device.isTablet(this) -> FormFactors.TABLET
      Device.isDesktop(this) -> FormFactors.DESKTOP
      Device.isXr(this) -> FormFactors.XR
      else -> FormFactors.PHONE
    }

internal fun VirtualDeviceProfile.update(
  block: VirtualDeviceProfile.Builder.() -> Unit
): VirtualDeviceProfile = toBuilder().apply(block).build()
