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
import icons.StudioIconsCompose
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.jewel.ui.component.Icon

open class TestDeviceSource : DeviceSource {
  override val profiles = mutableListOf<DeviceProfile>()

  val selectedProfile = MutableStateFlow<DeviceProfile?>(null)

  fun add(device: TestDevice) {
    profiles.add(device.toBuilder().apply { source = this@TestDeviceSource.javaClass }.build())
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
  override val displayDiagonalLength: Double = 0.0,
  override val isRound: Boolean = false,
  override val isVirtual: Boolean = true,
  override val isRemote: Boolean = false,
  override val abis: List<Abi> = listOf(Abi.ARM64_V8A),
  override val formFactor: String = FormFactors.PHONE,
  override val isAlreadyPresent: Boolean = false,
  override val availabilityEstimate: Duration = Duration.ZERO,
  override val source: Class<out DeviceSource> = TestDeviceSource::class.java,
) : DeviceProfile {

  override fun toBuilder(): Builder = Builder().apply { copyFrom(this@TestDevice) }

  @Composable
  override fun Icon(modifier: Modifier) {
    val painterProvider =
      when (formFactor) {
        FormFactors.TV -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceTv()
        FormFactors.AUTO -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceCar()
        FormFactors.WEAR -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceWear()
        // TODO: Add icon for tablet
        else -> StudioIconsCompose.DeviceExplorer.VirtualDevicePhone()
      }
    Icon(
      painter = painterProvider.getPainter().value,
      contentDescription = "$formFactor Test Device",
      modifier = modifier,
    )
  }

  class Builder : DeviceProfile.Builder() {
    lateinit var source: Class<out DeviceSource>

    fun copyFrom(profile: TestDevice) {
      super.copyFrom(profile)
      source = profile.source
    }

    override fun build(): TestDevice =
      TestDevice(
        apiRange = apiRange,
        manufacturer = manufacturer,
        name = name,
        resolution = resolution,
        displayDensity = displayDensity,
        displayDiagonalLength = displayDiagonalLength,
        isRound = isRound,
        isVirtual = isVirtual,
        isRemote = isRemote,
        abis = abis,
        formFactor = formFactor,
        isAlreadyPresent = isAlreadyPresent,
        availabilityEstimate = availabilityEstimate,
        source = source,
      )
  }
}
