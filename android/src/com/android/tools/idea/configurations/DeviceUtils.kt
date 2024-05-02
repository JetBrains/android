/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("DeviceUtils")

package com.android.tools.idea.configurations

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.HardwareConfigHelper.*
import com.android.ide.common.rendering.api.HardwareConfig
import com.android.sdklib.devices.Device
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.DEVICE_CLASS_DESKTOP_ID
import com.android.tools.configurations.DEVICE_CLASS_FOLDABLE_ID
import com.android.tools.configurations.DEVICE_CLASS_PHONE_ID
import com.android.tools.configurations.DEVICE_CLASS_TABLET_ID
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.android.dom.manifest.getPrimaryManifestXml
import org.jetbrains.android.facet.AndroidFacet
import kotlin.math.hypot

private val DEVICE_CACHES = ContainerUtil.createSoftMap<Configuration, Map<DeviceGroup, List<Device>>>()

enum class DeviceGroup {
  NEXUS,
  NEXUS_XL,
  NEXUS_TABLET,
  WEAR,
  DESKTOP,
  TV,
  AUTOMOTIVE,
  GENERIC,
  OTHER,
  ADDITIONAL_DEVICE,
  CANONICAL_DEVICE,
}

/**
 * Get the sorted devices which are grouped by [DeviceGroup]:
 * - For Nexus/Pixel devices which diagonal Length < 5 inch: [DeviceGroup.NEXUS]
 * - Nexus/Pixel devices which diagonal Length < 6.5 inch: [DeviceGroup.NEXUS_XL]
 * - Other Nexus/Pixel devices: [DeviceGroup.NEXUS_TABLET]
 * - Watch devices: [DeviceGroup.WEAR]
 * - TV devices: : [DeviceGroup.TV]
 * - Automotive devices: [DeviceGroup.AUTOMOTIVE]
 * - For mobiles devices which are *NOT* nexus devices: [DeviceGroup.GENERIC]
 * - Other devices: [DeviceGroup.OTHER]
 *
 * The order of devices is ascending by its screen size.
 *
 * @see groupDevices
 * @see DeviceGroup
 * @return map of sorted devices
 */
fun getSuitableDevices(configuration: Configuration): Map<DeviceGroup, List<Device>> = DEVICE_CACHES.getOrPut(configuration) {
  return groupDevices(configuration.settings.devices)
}

/**
 * Group the given devices by [DeviceGroup]:
 * - For canonical devices: [DeviceGroup.CANONICAL_DEVICE]
 * - For predefined devices in [AdditionalDeviceService]: [DeviceGroup.ADDITIONAL_DEVICE]
 * - For Nexus/Pixel devices which diagonal Length < 5 inch: [DeviceGroup.NEXUS]
 * - Nexus/Pixel devices which diagonal Length < 6.5 inch: [DeviceGroup.NEXUS_XL]
 * - Other Nexus/Pixel devices: [DeviceGroup.NEXUS_TABLET]
 * - Watch devices: [DeviceGroup.WEAR]
 * - TV devices: : [DeviceGroup.TV]
 * - Automotive devices: [DeviceGroup.AUTOMOTIVE]
 * - For mobiles devices which are *NOT* nexus devices: [DeviceGroup.GENERIC]
 * - Other devices: [DeviceGroup.OTHER]
 *
 * The order of devices is ascending by its screen size.
 *
 * @see DeviceGroup
 * @return map of sorted devices
 */
fun groupDevices(devices: List<Device>): Map<DeviceGroup, List<Device>> {
  return devices.filterNot { Configuration.CUSTOM_DEVICE_ID == it.id || ConfigurationManager.isAvdDevice(it) }
    .apply { sortDevicesByScreenSize(this) }
    .groupBy {
      when {
        isCanonicalDevice(it) -> DeviceGroup.CANONICAL_DEVICE
        isAdditionalDevice(it) -> DeviceGroup.ADDITIONAL_DEVICE
        Device.isAutomotive(it) -> DeviceGroup.AUTOMOTIVE
        Device.isWear(it) -> DeviceGroup.WEAR
        Device.isDesktop(it) -> DeviceGroup.DESKTOP
        Device.isTv(it) -> DeviceGroup.TV
        isNexus(it) && it.manufacturer != HardwareConfig.MANUFACTURER_GENERIC -> sizeGroupNexus(it)
        Device.isMobile(it) && it.manufacturer == HardwareConfig.MANUFACTURER_GENERIC -> DeviceGroup.GENERIC
        else -> DeviceGroup.OTHER
      }
    }
    .toSortedMap()
}

private fun isCanonicalDevice(device: Device): Boolean {
  val id = device.id
  return id == "SmallPhone" || id == "MediumPhone" || id == "MediumTablet"
}

private fun isAdditionalDevice(device: Device): Boolean {
  val id = device.id

  return id == DEVICE_CLASS_PHONE_ID ||
         id == DEVICE_CLASS_FOLDABLE_ID ||
         id == DEVICE_CLASS_TABLET_ID ||
         id == DEVICE_CLASS_DESKTOP_ID
}

private fun sizeGroupNexus(device: Device): DeviceGroup {
  val screen = device.defaultHardware.screen
  // For foldables the device definition diagonal might be for the unfolded device, calculate ourselves.
  val diagonalLength = if (!screen.isFoldable)
    screen.diagonalLength
  else
    hypot(screen.xDimension/screen.pixelDensity.dpiValue.toDouble(), screen.yDimension/screen.pixelDensity.dpiValue.toDouble())

  return when {
    diagonalLength < 5 -> DeviceGroup.NEXUS
    diagonalLength < 7 -> DeviceGroup.NEXUS_XL
    else -> DeviceGroup.NEXUS_TABLET
  }
}

/**
 * The must-have uses-feature tag in AndroidManifest for a WearOS project.
 */
private const val WEAR_OS_USE_FEATURE_TAG = "android.hardware.type.watch"

/**
 * Return if the default device is wear device in the given [Module].
 */
@Slow
fun isUseWearDeviceAsDefault(module: Module): Boolean {
  val facet = AndroidFacet.getInstance(module)
  if (facet == null || facet.isDisposed) {
    return false
  }
  val manifestXml = runReadAction { facet.getPrimaryManifestXml() } ?: return false
  return runReadAction { manifestXml.usesFeature }.contains(WEAR_OS_USE_FEATURE_TAG)
}

enum class CanonicalDeviceType(val id: String) {
  SMALL_PHONE("SmallPhone"),
  MEDIUM_PHONE("MediumPhone"),
  MEDIUM_TABLET("MediumTablet"),
}

fun getCanonicalDevice(devices: Map<DeviceGroup, List<Device>>, type: CanonicalDeviceType): Device? =
  devices[DeviceGroup.CANONICAL_DEVICE]?.firstOrNull { it.id == type.id }

enum class ReferenceDeviceType {
  MEDIUM_PHONE,
  FOLDABLE,
  MEDIUM_TABLET,
  DESKTOP
}

/**
 * Helper function to find the reference device. The device in [AdditionalDeviceService] is picked
 */
fun getReferenceDevice(config: Configuration, type: ReferenceDeviceType) = getReferenceDevice(getSuitableDevices (config), type)

/**
 * Helper function to find the reference device.
 * @see [getReferenceDevice]
 */
fun getReferenceDevice(devices: Map<DeviceGroup, List<Device>>, type: ReferenceDeviceType): Device? {
  return when (type) {
    ReferenceDeviceType.MEDIUM_PHONE ->
      devices[DeviceGroup.ADDITIONAL_DEVICE]?.firstOrNull { it.id == DEVICE_CLASS_PHONE_ID }
    ReferenceDeviceType.FOLDABLE ->
      devices[DeviceGroup.ADDITIONAL_DEVICE]?.firstOrNull { it.id == DEVICE_CLASS_FOLDABLE_ID }
    ReferenceDeviceType.MEDIUM_TABLET ->
      devices[DeviceGroup.ADDITIONAL_DEVICE]?.firstOrNull { it.id == DEVICE_CLASS_TABLET_ID }
    ReferenceDeviceType.DESKTOP ->
      devices[DeviceGroup.ADDITIONAL_DEVICE]?.firstOrNull { it.id == DEVICE_CLASS_DESKTOP_ID }
  }
}

fun isUseWearDeviceAsDefault(configuration: Configuration): Boolean {
  val module = (configuration.configModule as StudioConfigurationModelModule).module
  return isUseWearDeviceAsDefault(module)
}

const val DEVICE_CLASS_PHONE_TOOLTIP = "This reference device uses the COMPACT width size class," +
                                       " which represents 99% of Android phones in portrait orientation."
const val DEVICE_CLASS_FOLDABLE_TOOLTIP = "This reference device uses the MEDIUM width size class," +
                                          " which represents foldables in unfolded portrait orientation," +
                                          " or 94% of all tablets in portrait orientation."
const val DEVICE_CLASS_TABLET_TOOLTIP = "This reference device uses the EXPANDED width size class," +
                                        " which represents 97% of Android tablets in landscape orientation."
const val DEVICE_CLASS_DESKTOP_TOOLTIP = "This reference device uses the EXPANDED width size class," +
                                         " which represents 97% of Android desktops in landscape orientation."
