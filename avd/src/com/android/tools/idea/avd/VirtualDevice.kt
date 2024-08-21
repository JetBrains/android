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

import androidx.compose.runtime.Immutable
import com.android.resources.ScreenOrientation
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Storage
import com.android.sdklib.internal.avd.AvdBuilder
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdNetworkLatency
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.sdklib.internal.avd.BootMode
import com.android.sdklib.internal.avd.BootSnapshot
import com.android.sdklib.internal.avd.ColdBoot
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.ExternalSdCard
import com.android.sdklib.internal.avd.GenericSkin
import com.android.sdklib.internal.avd.GpuMode
import com.android.sdklib.internal.avd.InternalSdCard
import com.android.sdklib.internal.avd.OnDiskSkin
import com.android.sdklib.internal.avd.QuickBoot
import com.android.sdklib.internal.avd.SdCard
import com.android.sdklib.internal.avd.Skin as AvdSkin
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Immutable
internal data class VirtualDevice
internal constructor(
  val name: String,
  val device: Device,
  internal val androidVersion: AndroidVersion,
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
) {
  companion object {
    fun withDefaults(device: Device): VirtualDevice =
      VirtualDevice(
        name = device.displayName,
        device = device,
        androidVersion = device.androidVersionRange.upperEndpoint(),
        // TODO(b/335267252): Set the skin appropriately.
        skin = NoSkin.INSTANCE,
        frontCamera = AvdCamera.EMULATED,
        // TODO We're assuming the emulator supports this feature
        rearCamera = AvdCamera.VIRTUAL_SCENE,
        speed = EmulatedProperties.DEFAULT_NETWORK_SPEED,
        latency = EmulatedProperties.DEFAULT_NETWORK_LATENCY,
        orientation = device.defaultState.orientation,
        defaultBoot = Boot.QUICK,
        internalStorage = StorageCapacity(2_048, StorageCapacity.Unit.MB),
        expandedStorage = Custom(StorageCapacity(512, StorageCapacity.Unit.MB)),
        cpuCoreCount = EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES,
        graphicAcceleration = GpuMode.AUTO,
        simulatedRam = StorageCapacity(2_048, StorageCapacity.Unit.MB),
        vmHeapSize = StorageCapacity(256, StorageCapacity.Unit.MB),
      )
  }
}

internal fun VirtualDevice.copyFrom(avdInfo: AvdBuilder): VirtualDevice {
  // TODO: System image
  // TODO: Preferred ABI

  return copy(
    name = avdInfo.displayName,
    androidVersion = avdInfo.androidVersion!!,
    skin = avdInfo.skin.toSkin(),
    frontCamera = avdInfo.frontCamera,
    rearCamera = avdInfo.backCamera,
    speed = avdInfo.networkSpeed,
    latency = avdInfo.networkLatency,
    orientation = avdInfo.screenOrientation,
    defaultBoot = avdInfo.bootMode.toBoot(),
    internalStorage = avdInfo.internalStorage.toStorageCapacity(),
    expandedStorage = avdInfo.sdCard.toExpandedStorage(),
    cpuCoreCount = avdInfo.cpuCoreCount,
    graphicAcceleration = avdInfo.gpuMode,
    simulatedRam = avdInfo.ram.toStorageCapacity(),
    vmHeapSize = avdInfo.vmHeap.toStorageCapacity(),
  )
}

internal fun AvdBuilder.copyFrom(device: VirtualDevice) {
  this.device = device.device
  displayName = device.name

  sdCard = device.expandedStorage.toSdCard()
  skin = device.skin.toAvdSkin()

  screenOrientation = device.orientation
  cpuCoreCount = device.cpuCoreCount ?: 1
  ram = device.simulatedRam.toStorage()
  vmHeap = device.vmHeapSize.toStorage()
  internalStorage = device.internalStorage.toStorage()

  frontCamera = device.frontCamera
  backCamera = device.rearCamera

  gpuMode = device.graphicAcceleration

  networkSpeed = device.speed
  networkLatency = device.latency

  bootMode = device.defaultBoot.toBootMode()
}

private fun StorageCapacity.toStorage(): Storage {
  return Storage(value * unit.byteCount)
}

private fun Storage.toStorageCapacity(): StorageCapacity {
  val unit = getAppropriateUnits()
  return StorageCapacity(getSizeAsUnit(unit), StorageCapacity.Unit.valueOf(unit.displayValue))
}

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

internal fun ExpandedStorage.toSdCard(): SdCard? =
  when (this) {
    is Custom -> InternalSdCard(value.valueIn(StorageCapacity.Unit.B))
    is ExistingImage -> ExternalSdCard(toString())
    None -> null
  }

internal fun SdCard?.toExpandedStorage() =
  when (this) {
    null -> None
    is ExternalSdCard -> ExistingImage(Paths.get(path))
    is InternalSdCard -> Custom(StorageCapacity(size, StorageCapacity.Unit.B).withMaxUnit())
  }

internal fun Skin.toAvdSkin(): AvdSkin? = OnDiskSkin(path()).takeUnless { this == NoSkin.INSTANCE }

internal fun AvdSkin?.toSkin(): Skin =
  when (this) {
    null -> NoSkin.INSTANCE
    is OnDiskSkin -> DefaultSkin(path)
    is GenericSkin -> DefaultSkin(Paths.get(name))
  }

internal fun Boot.toBootMode() =
  when (this) {
    Boot.COLD -> ColdBoot
    Boot.QUICK -> QuickBoot
  // TODO(b/343544613): Boot from snapshot?
  }

internal fun BootMode.toBoot() =
  when (this) {
    ColdBoot -> Boot.COLD
    QuickBoot -> Boot.QUICK
    // TODO(b/343544613): Boot from snapshot?
    is BootSnapshot -> Boot.QUICK
  }
