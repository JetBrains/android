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

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.sdklib.ISystemImage
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.tools.idea.adddevicedialog.TableSelectionState
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.collections.contains
import kotlin.math.max
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList

internal class ConfigureDevicePanelState(
  device: VirtualDevice,
  skins: ImmutableCollection<Skin>,
  image: ISystemImage?,
  val deviceNameValidator: DeviceNameValidator,
  fileSystem: FileSystem = FileSystems.getDefault(),
  val maxCpuCoreCount: Int = max(1, Runtime.getRuntime().availableProcessors() / 2),
) {
  var device by mutableStateOf(device)
  private var skins by mutableStateOf(skins)
  val systemImageTableSelectionState = TableSelectionState(image)
  val storageGroupState = StorageGroupState(device, fileSystem)
  val emulatedPerformanceGroupState = EmulatedPerformanceGroupState(device)

  var isSystemImageTableSelectionValid by mutableStateOf(true)
  var isPreferredAbiValid by mutableStateOf(true)

  val isValid
    get() =
      device.isValid &&
        deviceNameError == null &&
        isSystemImageTableSelectionValid &&
        isPreferredAbiValid

  fun hasPlayStore(): Boolean {
    val image = systemImageTableSelectionState.selection
    return if (image == null) false else device.hasPlayStore(image)
  }

  fun setDeviceName(deviceName: String) {
    device = device.copy(name = deviceName)
  }

  val deviceNameError by derivedStateOf { deviceNameValidator.validate(this.device.name) }

  fun setSystemImageSelection(systemImage: ISystemImage) {
    systemImageTableSelectionState.selection = systemImage
    updatePreferredAbiValidity()
  }

  fun setPreferredAbi(preferredAbi: String?) {
    device = device.copy(preferredAbi = preferredAbi)
    updatePreferredAbiValidity()
  }

  private fun updatePreferredAbiValidity() {
    isPreferredAbiValid =
      device.preferredAbi == null ||
        systemImageTableSelectionState.selection == null ||
        systemImageTableSelectionState.selection.allAbiTypes().contains(device.preferredAbi)
  }

  fun initDeviceSkins(path: Path) {
    val skin = getSkin(path)
    device = device.copy(skin = skin, defaultSkin = skin)
  }

  fun initDefaultSkin(path: Path) {
    device = device.copy(defaultSkin = getSkin(path))
  }

  fun setSkin(path: Path) {
    val skin = getSkin(path)
    device = device.copy(skin = if (skin !in skins()) device.defaultSkin else skin)
  }

  fun skins(): Iterable<Skin> =
    if (hasPlayStore()) setOf(NoSkin.INSTANCE, device.defaultSkin) else skins

  private fun getSkin(path: Path): Skin {
    var skin = skins.firstOrNull { it.path() == path }

    if (skin == null) {
      skin = DefaultSkin(path)
      skins = (skins + skin).sorted().toImmutableList()
    }

    return skin
  }

  fun resetPlayStoreFields() {
    if (!hasPlayStore()) return

    device =
      device.copy(
        expandedStorage = Custom(storageGroupState.custom.valid().storageCapacity.withMaxUnit()),
        cpuCoreCount = EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES,
        graphicsMode = GraphicsMode.AUTO,
        ram = device.defaultRam,
        vmHeapSize = device.defaultVmHeapSize,
      )
  }
}
