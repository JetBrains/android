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
import com.android.sdklib.internal.avd.ColdBoot
import com.android.sdklib.internal.avd.ExternalSdCard
import com.android.sdklib.internal.avd.GpuMode
import com.android.sdklib.internal.avd.InternalSdCard
import com.android.sdklib.internal.avd.OnDiskSkin
import com.android.sdklib.internal.avd.QuickBoot
import com.android.sdklib.internal.avd.SdCard
import com.android.tools.idea.avdmanager.SkinUtils
import com.android.tools.idea.avdmanager.skincombobox.Skin
import java.nio.file.Files
import java.nio.file.Path

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
)

internal fun AvdBuilder.copyFrom(device: VirtualDevice) {
  this.device = device.device
  avdName = device.name
  displayName = device.name

  sdCard = device.expandedStorage.toSdCard()
  skin = OnDiskSkin(device.skin.path()).takeUnless { device.skin == SkinUtils.noSkin() }

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

  bootMode =
    when (device.defaultBoot) {
      Boot.COLD -> ColdBoot
      Boot.QUICK -> QuickBoot
    // TODO(b/343544613): Boot from snapshot?
    }
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
