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
package com.android.tools.sdk

import com.android.SdkConstants
import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.devices.DeviceManager.Companion.VENDOR_DEVICE_RESOURCES
import com.android.sdklib.devices.DeviceManager.DeviceCategory
import com.android.sdklib.devices.DeviceResourceTable
import com.android.sdklib.devices.SystemImageDeviceTable
import com.android.sdklib.devices.UserDeviceTable
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.environment.Logger
import com.android.tools.log.LogWrapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

/**
 * Service that allows certain [Device]s to be excluded from the device manager. Currently only used to exclude the XR devices on the stable
 * Android Studio version.
 */
interface DeviceManagerDeviceFilter {
  /** For a [Device] return if it should be exposed by the [DeviceManager]. */
  fun isSupportedDevice(device: Device): Boolean

  companion object {
    /** Default filter when the [DeviceManager] is running outside of Android Studio. */
    private val NO_FILTER =
      object : DeviceManagerDeviceFilter {
        override fun isSupportedDevice(device: Device): Boolean = true
      }

    @JvmStatic
    fun getInstance(): DeviceManagerDeviceFilter =
      ApplicationManager.getApplication()?.getService(DeviceManagerDeviceFilter::class.java) ?: NO_FILTER
  }
}

interface DeviceManagerCache {
  fun getDeviceManagerWithoutSystemImageDevices(): DeviceManager

  fun getDeviceManager(sdkHandler: AndroidSdkHandler): DeviceManager
}

internal class DeviceManagerCacheImpl : DeviceManagerCache {
  val logger = LogWrapper(Logger.getInstance(DeviceManager::class.java))

  fun isSupportedDevice(device: Device) = DeviceManagerDeviceFilter.getInstance().isSupportedDevice(device)

  val vendorDevices = DeviceResourceTable(logger, ::isSupportedDevice, VENDOR_DEVICE_RESOURCES)
  val defaultDevices = DeviceResourceTable(logger, isSupportedDevice = { true }, listOf("devices"))
  val userDevices =
    UserDeviceTable(logger, ::isSupportedDevice, AndroidLocationsSingleton.prefsLocation.resolve(SdkConstants.FN_DEVICES_XML))

  override fun getDeviceManagerWithoutSystemImageDevices(): DeviceManager =
    DeviceManager(
      mapOf(DeviceCategory.VENDOR to vendorDevices, DeviceCategory.DEFAULT to defaultDevices, DeviceCategory.USER to userDevices)
    )

  override fun getDeviceManager(sdkHandler: AndroidSdkHandler): DeviceManager {
    UserDevicesXmlHandler.backupUnsupportedUserDevicesXml(sdkHandler, logger)

    return sdkHandler.location?.let { AndroidSdkData.getSdkData(it)?.deviceManager } ?: getDeviceManagerWithoutSystemImageDevices()
  }
}

/** Service that simply holds a [DeviceManagerCache] singleton instance. Can be replaced in tests. */
@Service(Service.Level.APP)
class DeviceManagerCacheService {
  val cache: DeviceManagerCache = DeviceManagerCacheImpl()

  companion object {
    fun getDeviceManagerCache(): DeviceManagerCache = service<DeviceManagerCacheService>().cache
  }
}

object DeviceManagers {
  private val delegate
    get() = service<DeviceManagerCacheService>().cache

  fun getDeviceManagerWithoutSystemImageDevices(): DeviceManager = delegate.getDeviceManagerWithoutSystemImageDevices()

  fun getDeviceManager(sdkHandler: AndroidSdkHandler): DeviceManager = delegate.getDeviceManager(sdkHandler)

  // Only for use by AndroidSdkData.getData()
  internal fun createDeviceManager(sdkHandler: AndroidSdkHandler): DeviceManager =
    with(delegate as DeviceManagerCacheImpl) {
      DeviceManager(
        buildMap {
          put(DeviceCategory.SYSTEM_IMAGES, SystemImageDeviceTable(logger, ::isSupportedDevice, sdkHandler))
          put(DeviceCategory.VENDOR, vendorDevices)
          put(DeviceCategory.DEFAULT, defaultDevices)
          put(DeviceCategory.USER, userDevices)
        }
      )
    }
}
