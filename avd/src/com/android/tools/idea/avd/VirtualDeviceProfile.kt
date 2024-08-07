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
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.Resolution
import com.android.sdklib.devices.Abi
import com.android.sdklib.devices.Device
import com.android.tools.idea.adddevicedialog.DeviceProfile
import com.android.tools.idea.adddevicedialog.DeviceSource
import com.android.tools.idea.adddevicedialog.FormFactors
import com.google.common.collect.Range
import icons.StudioIconsCompose
import java.util.NavigableSet
import java.util.TreeSet
import kotlin.time.Duration

@Immutable
internal data class VirtualDeviceProfile(
  val device: Device,
  override val apiLevels: NavigableSet<AndroidVersion>,
  override val manufacturer: String,
  override val name: String,
  override val resolution: Resolution,
  override val displayDensity: Int,
  override val displayDiagonalLength: Double,
  override val isRound: Boolean,
  override val abis: List<Abi>,
  override val formFactor: String,
) : DeviceProfile {

  override val source: Class<out DeviceSource>
    get() = LocalVirtualDeviceSource::class.java

  override val isVirtual
    get() = true

  override val isRemote
    get() = false

  @Composable
  override fun Icon(modifier: Modifier) {
    val painterProvider =
      when (formFactor) {
        FormFactors.TV -> StudioIconsCompose.DeviceExplorer.VirtualDeviceTv()
        FormFactors.AUTO -> StudioIconsCompose.DeviceExplorer.VirtualDeviceCar()
        FormFactors.WEAR -> StudioIconsCompose.DeviceExplorer.VirtualDeviceWear()
        // TODO: Add icon for tablet
        else -> StudioIconsCompose.DeviceExplorer.VirtualDevicePhone()
      }
    org.jetbrains.jewel.ui.component.Icon(
      painter = painterProvider.getPainter().value,
      contentDescription = "$formFactor AVD",
      modifier = modifier,
    )
  }

  override val isAlreadyPresent: Boolean
    get() = false

  override val availabilityEstimate: Duration
    get() = Duration.ZERO

  override fun toBuilder(): Builder = Builder().apply { copyFrom(this@VirtualDeviceProfile) }

  class Builder : DeviceProfile.Builder() {
    lateinit var device: Device

    fun initializeFromDevice(device: Device, androidVersions: Set<AndroidVersion>) {
      this.device = device
      apiLevels = androidVersions.filterTo(TreeSet()) { device.androidVersionRange.contains(it) }
      manufacturer = device.manufacturer
      name = device.displayName
      val screen = device.defaultHardware.screen
      resolution = Resolution(screen.xDimension, screen.yDimension)
      displayDensity = screen.pixelDensity.dpiValue
      displayDiagonalLength = screen.diagonalLength
      isRound = screen.screenRound == ScreenRound.ROUND
      abis = device.defaultHardware.supportedAbis + device.defaultHardware.translatedAbis
      formFactor = device.formFactor
    }

    fun copyFrom(profile: VirtualDeviceProfile) {
      super.copyFrom(profile)
      device = profile.device
    }

    override fun build(): VirtualDeviceProfile =
      VirtualDeviceProfile(
        device = device,
        apiLevels = apiLevels,
        manufacturer = manufacturer,
        name = name,
        resolution = resolution,
        displayDensity = displayDensity,
        displayDiagonalLength = displayDiagonalLength,
        isRound = isRound,
        abis = abis,
        formFactor = formFactor,
      )
  }
}

private val Device.androidVersionRange: Range<AndroidVersion>
  get() =
    allSoftware
      .map { Range.closed(AndroidVersion(it.minSdkLevel), AndroidVersion(it.maxSdkLevel)) }
      .reduce(Range<AndroidVersion>::span)

private val Device.formFactor: String
  get() =
    when {
      Device.isWear(this) -> FormFactors.WEAR
      Device.isAutomotive(this) -> FormFactors.AUTO
      Device.isTv(this) -> FormFactors.TV
      Device.isTablet(this) -> FormFactors.TABLET
      else -> FormFactors.PHONE
    }

internal fun VirtualDeviceProfile.update(
  block: VirtualDeviceProfile.Builder.() -> Unit
): VirtualDeviceProfile = toBuilder().apply(block).build()
