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
import com.android.tools.idea.avdmanager.AvdManagerUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Computable
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet

private val DEVICE_CACHES = ContainerUtil.createSoftMap<Configuration, Map<DeviceGroup, List<Device>>>()

enum class DeviceGroup {
  NEXUS,
  NEXUS_XL,
  NEXUS_TABLET,
  WEAR,
  TV,
  AUTOMOTIVE,
  GENERIC,
  OTHER,
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
  return groupDevices(configuration.configurationManager.devices)
}

/**
 * Group the given devices by [DeviceGroup]:
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
fun groupDevices(devices: List<Device>): Map<DeviceGroup, List<Device>> =
  devices.filterNot { Configuration.CUSTOM_DEVICE_ID == it.id || ConfigurationManager.isAvdDevice(it) }
    .apply { sortDevicesByScreenSize(this) }
    .groupBy {
      when {
        isAutomotive(it) -> DeviceGroup.AUTOMOTIVE
        isWear(it) -> DeviceGroup.WEAR
        isTv(it) -> DeviceGroup.TV
        isNexus(it) && it.manufacturer != HardwareConfig.MANUFACTURER_GENERIC -> sizeGroupNexus(it)
        isMobile(it) -> DeviceGroup.GENERIC
        else -> DeviceGroup.OTHER
      }
    }
    .toSortedMap()

private fun sizeGroupNexus(device: Device): DeviceGroup {
  val diagonalLength = device.defaultHardware.screen.diagonalLength
  return when {
    diagonalLength < 5 -> DeviceGroup.NEXUS
    diagonalLength < 6.5 -> DeviceGroup.NEXUS_XL
    else -> DeviceGroup.NEXUS_TABLET
  }
}

/**
 * Return the avd devices in the module of [configuration]
 */
fun getAvdDevices(configuration: Configuration): List<Device> {
  // Unlikely, but has happened - see http://b.android.com/68091
  val facet = AndroidFacet.getInstance(configuration.module) ?: return emptyList()
  val configurationManager = configuration.configurationManager
  val avdManager = AvdManagerUtils.getAvdManagerSilently(facet) ?: return emptyList()
  return avdManager.validAvds.mapNotNull { configurationManager.createDeviceForAvd(it) }
}

/**
 * The must-have meta-data tag for a WearOS project.
 */
private const val WEAR_OS_APPLICATION_META_DATA = "com.google.android.wearable.standalone"

/**
 * Return if the default device is wear device in the given [Module].
 */
@Slow
fun isUseWearDeviceAsDefault(module: Module): Boolean {
  val facet = AndroidFacet.getInstance(module)
  if (facet == null || facet.isDisposed) {
    return false
  }
  val manifest = Manifest.getMainManifest(facet) ?: return false
  return ApplicationManager.getApplication().runReadAction(Computable<Boolean> {
    manifest.application?.metaDatas?.any { metadata -> WEAR_OS_APPLICATION_META_DATA == metadata.name.value }
  })
}
