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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.resources.ScreenOrientation
import com.android.sdklib.ISystemImage
import com.android.sdklib.devices.Camera
import com.android.sdklib.devices.CameraLocation
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
import com.android.sdklib.internal.avd.InternalSdCard
import com.android.sdklib.internal.avd.OnDiskSkin
import com.android.sdklib.internal.avd.QuickBoot
import com.android.sdklib.internal.avd.SdCard
import com.android.sdklib.internal.avd.Skin as AvdSkin
import com.android.sdklib.internal.avd.UserSettingsKey
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import java.nio.file.Path

/** A mutable state holder for a virtual device in the Add Device dialog. */
@Stable
internal class VirtualDevice(
  val deviceProfile: Device,
  // These properties are derivative of deviceProfile; they are exposed only for testing
  val hasPlaystore: Boolean = deviceProfile.hasPlayStore(),
  val isFoldable: Boolean = deviceProfile.defaultHardware.screen.isFoldable,
  val cameraLocations: Collection<CameraLocation> =
    deviceProfile.defaultHardware.cameras.map(Camera::getLocation),
  val formFactor: String = deviceProfile.formFactor,
  val defaultRam: StorageCapacity =
    EmulatedProperties.defaultRamSize(deviceProfile).toStorageCapacity(),
  val defaultVmHeapSize: StorageCapacity =
    EmulatedProperties.defaultVmHeapSize(deviceProfile).toStorageCapacity(),
) {
  var name: String by mutableStateOf("")
  var image: ISystemImage? by mutableStateOf(null)
  var skin: Skin by mutableStateOf(NoSkin.INSTANCE)
  var frontCamera: AvdCamera by mutableStateOf(AvdCamera.NONE)
  var rearCamera: AvdCamera by mutableStateOf(AvdCamera.NONE)
  var speed: AvdNetworkSpeed by mutableStateOf(EmulatedProperties.DEFAULT_NETWORK_SPEED)
  var latency: AvdNetworkLatency by mutableStateOf(EmulatedProperties.DEFAULT_NETWORK_LATENCY)
  var orientation: ScreenOrientation by mutableStateOf(ScreenOrientation.PORTRAIT)
  var defaultBoot: Boot by mutableStateOf(Boot.QUICK)
  var internalStorage: StorageCapacity? by mutableStateOf(null)
  var expandedStorage: ExpandedStorage? by mutableStateOf(null)
  var existingCustomExpandedStorage: Custom? by mutableStateOf(null)
  var cpuCoreCount: Int by mutableIntStateOf(EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES)
  var graphicsMode: GraphicsMode by mutableStateOf(GraphicsMode.AUTO)
  var ram: StorageCapacity? by mutableStateOf(defaultRam)
  var vmHeapSize: StorageCapacity? by mutableStateOf(defaultVmHeapSize)
  var preferredAbi: String? by mutableStateOf(null)

  /**
   * The value of the [skin] property after it is initialized by [ConfigurationPage] via
   * [ConfigureDevicePanelState]. [ConfigureDevicePanelState] uses this to reset [skin] to the
   * default when a user selects a Play system image. It also uses this to generate the restricted
   * list of skins for the skin drop down for Play.
   *
   * It's its own property for ease of testing, like [VirtualDevice.hasPlaystore], [isFoldable], etc
   */
  var defaultSkin: Skin by mutableStateOf(skin)

  val isValid
    get() =
      image != null &&
        internalStorage != null &&
        expandedStorage != null &&
        ram != null &&
        vmHeapSize != null

  fun hasPlayStore(image: ISystemImage) =
    hasPlaystore && image.getServices() == Services.GOOGLE_PLAY_STORE

  /** Initializes the device based on its device profile. */
  fun initializeFromProfile() {
    name = deviceProfile.displayName
    skin = NoSkin.INSTANCE
    frontCamera = if (deviceProfile.hasFrontCamera()) AvdCamera.EMULATED else AvdCamera.NONE
    rearCamera = if (deviceProfile.hasRearCamera()) AvdCamera.VIRTUAL_SCENE else AvdCamera.NONE
    orientation = deviceProfile.defaultState.orientation
    internalStorage = EmulatedProperties.defaultInternalStorage(deviceProfile).toStorageCapacity()
    expandedStorage = Custom(StorageCapacity(512, StorageCapacity.Unit.MB))
    cpuCoreCount = EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES
    graphicsMode = GraphicsMode.AUTO
    ram = EmulatedProperties.defaultRamSize(deviceProfile).toStorageCapacity()
    vmHeapSize = EmulatedProperties.defaultVmHeapSize(deviceProfile).toStorageCapacity()
  }

  /** Copies an existing AVD definition into this device. */
  internal fun copyFrom(avdBuilder: AvdBuilder) {
    name = avdBuilder.displayName
    image = avdBuilder.systemImage
    skin = avdBuilder.skin.toSkin()
    frontCamera = avdBuilder.frontCamera
    rearCamera = avdBuilder.backCamera
    speed = avdBuilder.networkSpeed
    latency = avdBuilder.networkLatency
    orientation = avdBuilder.screenOrientation
    defaultBoot = avdBuilder.bootMode.toBoot()
    internalStorage = avdBuilder.internalStorage.toStorageCapacity()
    expandedStorage = avdBuilder.sdCard.toExpandedStorage()
    cpuCoreCount = avdBuilder.cpuCoreCount
    graphicsMode = avdBuilder.gpuMode.toGraphicsMode()
    ram = avdBuilder.ram.toStorageCapacity()
    vmHeapSize = avdBuilder.vmHeap.toStorageCapacity()
    preferredAbi = avdBuilder.userSettings[UserSettingsKey.PREFERRED_ABI]
  }

  companion object {
    val MIN_INTERNAL_STORAGE = StorageCapacity(2, StorageCapacity.Unit.GB)

    val MIN_CUSTOM_EXPANDED_STORAGE_FOR_PLAY_STORE = StorageCapacity(100, StorageCapacity.Unit.MB)

    val MIN_CUSTOM_EXPANDED_STORAGE = StorageCapacity(10, StorageCapacity.Unit.MB)
    val MIN_RAM = StorageCapacity(128, StorageCapacity.Unit.MB)
    val MIN_VM_HEAP_SIZE = StorageCapacity(16, StorageCapacity.Unit.MB)
  }
}

private fun Device.hasFrontCamera() =
  defaultHardware.cameras.any { it.location == CameraLocation.FRONT }

private fun Device.hasRearCamera() =
  defaultHardware.cameras.any { it.location == CameraLocation.BACK }

internal fun AvdBuilder.copyFrom(device: VirtualDevice) {
  this.device = device.deviceProfile
  displayName = device.name

  systemImage = device.image

  sdCard = requireNotNull(device.expandedStorage).toSdCard()
  skin = device.skin.toAvdSkin()

  screenOrientation = device.orientation
  cpuCoreCount = device.cpuCoreCount
  ram = requireNotNull(device.ram).toStorage()
  vmHeap = requireNotNull(device.vmHeapSize).toStorage()
  internalStorage = requireNotNull(device.internalStorage).toStorage()

  frontCamera = device.frontCamera
  backCamera = device.rearCamera

  gpuMode = device.graphicsMode.toGpuMode(device.image!!)

  networkSpeed = device.speed
  networkLatency = device.latency

  bootMode = device.defaultBoot.toBootMode()

  when (val preferredAbi = device.preferredAbi) {
    null -> userSettings.remove(UserSettingsKey.PREFERRED_ABI)
    else -> userSettings[UserSettingsKey.PREFERRED_ABI] = preferredAbi
  }
}

private fun StorageCapacity.toStorage(): Storage {
  return Storage(value * unit.byteCount)
}

internal fun Storage.toStorageCapacity(): StorageCapacity {
  val unit = getAppropriateUnits()
  return StorageCapacity(getSizeAsUnit(unit), StorageCapacity.Unit.valueOf(unit.displayValue))
}

internal data class Custom(val value: StorageCapacity) : ExpandedStorage() {
  internal fun withMaxUnit() = Custom(value.withMaxUnit())

  override fun toString() = value.toString()
}

internal data class ExistingImage(private val value: Path) : ExpandedStorage() {
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
    is ExternalSdCard -> ExistingImage(Path.of(path))
    is InternalSdCard -> Custom(StorageCapacity(size, StorageCapacity.Unit.B).withMaxUnit())
  }

internal fun Skin.toAvdSkin(): AvdSkin? = OnDiskSkin(path()).takeUnless { this == NoSkin.INSTANCE }

internal fun AvdSkin?.toSkin(): Skin =
  when (this) {
    null -> NoSkin.INSTANCE
    is OnDiskSkin -> DefaultSkin(path)
    is GenericSkin -> NoSkin.INSTANCE
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
