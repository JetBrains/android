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

import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.environment.Logger
import com.android.tools.log.LogWrapper
import com.android.utils.ILogger
import com.intellij.openapi.application.ApplicationManager

/**
 * Service that allows certain [Device]s to be excluded from the device manager.
 * Currently only used to exclude the XR devices on the stable Android Studio version.
 */
interface DeviceManagerDeviceFilter {
  /**
   * For a [Device] return if it should be exposed by the [DeviceManager].
   */
  fun isSupportedDevice(device: Device): Boolean

  companion object {
    /**
     * Default filter when the [DeviceManager] is running outside of Android Studio.
     */
    private val NO_FILTER = object: DeviceManagerDeviceFilter {
      override fun isSupportedDevice(device: Device): Boolean = true
    }

    @JvmStatic
    fun getInstance(): DeviceManagerDeviceFilter =
      ApplicationManager.getApplication()?.getService(DeviceManagerDeviceFilter::class.java) ?: NO_FILTER
  }
}

class DeviceManagerCache(val logger: ILogger) {
  private val deviceManagers = mutableMapOf<AndroidSdkHandler, DeviceManager>()

  fun getDeviceManager(sdkHandler: AndroidSdkHandler): DeviceManager =
    synchronized(deviceManagers) {
      deviceManagers.computeIfAbsent(sdkHandler) {
        DeviceManager.createInstance(sdkHandler, logger) { device ->
          DeviceManagerDeviceFilter.getInstance().isSupportedDevice(device)
        }
      }
    }
}

/**
 * The [DeviceManagerCache] wrapper.
 */
object DeviceManagers {
  private val logger = LogWrapper(Logger.getInstance(DeviceManager::class.java))
  val cache = DeviceManagerCache(logger)

  @JvmStatic
  fun getDeviceManager(sdkHandler: AndroidSdkHandler): DeviceManager =
    cache.getDeviceManager(sdkHandler)
}
