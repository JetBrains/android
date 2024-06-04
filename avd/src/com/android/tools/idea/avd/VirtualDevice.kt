/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.resources.ScreenOrientation
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.Resolution
import com.android.sdklib.devices.Abi
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdNetworkLatency
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.sdklib.internal.avd.GpuMode
import com.android.tools.idea.adddevicedialog.DeviceProfile
import com.android.tools.idea.adddevicedialog.DeviceSource
import com.android.tools.idea.adddevicedialog.FormFactors
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.google.common.collect.Range
import icons.StudioIconsCompose
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import org.jetbrains.jewel.ui.component.Icon

@Immutable
internal data class VirtualDevice
internal constructor(
  internal val deviceId: String,
  override val apiRange: Range<Int>,
  override val manufacturer: String,
  override val name: String,
  override val resolution: Resolution,
  override val displayDensity: Int,
  override val displayDiagonalLength: Double,
  override val isRound: Boolean,
  override val abis: List<Abi>,
  override val formFactor: String,
  internal val sdkExtensionLevel: AndroidVersion,
  internal val skin: Skin,
  internal val frontCamera: AvdCamera,
  internal val rearCamera: AvdCamera,
  internal val speed: AvdNetworkSpeed,
  internal val latency: AvdNetworkLatency,
  internal val orientation: ScreenOrientation,
  internal val defaultBoot: Boot,
  internal val internalStorage: StorageCapacity,
  internal val expandedStorage: ExpandedStorage,
  internal val cpuCoreCount: Int?,
  internal val graphicAcceleration: GpuMode,
  internal val simulatedRam: StorageCapacity,
  internal val vmHeapSize: StorageCapacity,
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
    Icon(
      painter = painterProvider.getPainter().value,
      contentDescription = "$formFactor AVD",
      modifier = modifier,
    )
  }

  override val isAlreadyPresent: Boolean
    get() = false

  override val availabilityEstimate: Duration
    get() = Duration.ZERO

  override fun toBuilder(): Builder = Builder().apply { copyFrom(this@VirtualDevice) }

  class Builder : DeviceProfile.Builder() {
    lateinit var deviceId: String
    lateinit var sdkExtensionLevel: AndroidVersion
    lateinit var skin: Skin
    lateinit var frontCamera: AvdCamera
    lateinit var rearCamera: AvdCamera
    lateinit var speed: AvdNetworkSpeed
    lateinit var latency: AvdNetworkLatency
    lateinit var orientation: ScreenOrientation
    lateinit var defaultBoot: Boot
    lateinit var internalStorage: StorageCapacity
    lateinit var expandedStorage: ExpandedStorage
    var cpuCoreCount: Int? = null
    lateinit var graphicAcceleration: GpuMode
    lateinit var simulatedRam: StorageCapacity
    lateinit var vmHeapSize: StorageCapacity

    fun copyFrom(profile: VirtualDevice) {
      super.copyFrom(profile)
      deviceId = profile.deviceId
      sdkExtensionLevel = profile.sdkExtensionLevel
      skin = profile.skin
      frontCamera = profile.frontCamera
      rearCamera = profile.rearCamera
      speed = profile.speed
      latency = profile.latency
      orientation = profile.orientation
      defaultBoot = profile.defaultBoot
      internalStorage = profile.internalStorage
      expandedStorage = profile.expandedStorage
      cpuCoreCount = profile.cpuCoreCount
      graphicAcceleration = profile.graphicAcceleration
      simulatedRam = profile.simulatedRam
      vmHeapSize = profile.vmHeapSize
    }

    override fun build(): VirtualDevice =
      VirtualDevice(
        deviceId = deviceId,
        apiRange = apiRange,
        manufacturer = manufacturer,
        name = name,
        resolution = resolution,
        displayDensity = displayDensity,
        displayDiagonalLength = displayDiagonalLength,
        isRound = isRound,
        abis = abis,
        formFactor = formFactor,
        sdkExtensionLevel = sdkExtensionLevel,
        skin = skin,
        frontCamera = frontCamera,
        rearCamera = rearCamera,
        speed = speed,
        latency = latency,
        orientation = orientation,
        defaultBoot = defaultBoot,
        internalStorage = internalStorage,
        expandedStorage = expandedStorage,
        cpuCoreCount = cpuCoreCount,
        graphicAcceleration = graphicAcceleration,
        simulatedRam = simulatedRam,
        vmHeapSize = vmHeapSize,
      )
  }
}

internal fun VirtualDevice.update(block: VirtualDevice.Builder.() -> Unit): VirtualDevice =
  toBuilder().apply(block).build()

internal data class Custom internal constructor(internal val value: StorageCapacity) :
  ExpandedStorage() {

  override fun toString() = value.toString()
}

internal data class ExistingImage internal constructor(private val value: Path) :
  ExpandedStorage() {

  init {
    assert(Files.isRegularFile(value))
  }

  override fun toString() = value.toString()
}

internal object None : ExpandedStorage() {
  override fun toString() = ""
}

internal sealed class ExpandedStorage
