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
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdNetworkLatency
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.sdklib.internal.avd.GpuMode
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
) {
  internal val isFoldable
    get() = device.defaultHardware.screen.isFoldable
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
