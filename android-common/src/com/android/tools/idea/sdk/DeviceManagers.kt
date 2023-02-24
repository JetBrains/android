/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.sdk

import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.log.LogWrapper
import com.android.utils.ILogger

internal class DeviceManagerCache(val logger: ILogger) {
  private val deviceManagers = mutableMapOf<AndroidSdkHandler, DeviceManager>()

  fun getDeviceManager(sdkHandler: AndroidSdkHandler): DeviceManager =
    synchronized(deviceManagers) {
      deviceManagers.computeIfAbsent(sdkHandler) {
        DeviceManager.createInstance(sdkHandler, logger)
      }
    }
}

/**
 * The [DeviceManagerCache] wrapper.
 */
object DeviceManagers {
  private val logger =
    LogWrapper(DeviceManager::class.java).alwaysLogAsDebug(true).allowVerbose(false)
  internal val cache = DeviceManagerCache(logger)

  @JvmStatic
  fun getDeviceManager(sdkHandler: AndroidSdkHandler): DeviceManager =
    cache.getDeviceManager(sdkHandler)
}
