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
package com.android.gmdcodecompletion.managedvirtual

import com.android.gmdcodecompletion.AndroidDeviceInfo
import com.android.gmdcodecompletion.GmdDeviceCatalog
import com.android.prefs.AndroidLocationsSingleton
import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioSdkUtil
import com.intellij.openapi.diagnostic.Logger

/**
 * This class fetches and stores information from DeviceManager and RepoManager server to obtain
 * the latest device catalog for managed virtual devices
 */
class ManagedVirtualDeviceCatalog : GmdDeviceCatalog() {

  // Map of <device id, per Android device information>
  val devices: HashMap<String, AndroidDeviceInfo> = HashMap()
  val apiLevels: ArrayList<ApiVersionInfo> = ArrayList()
  val orientation: ArrayList<String> = ArrayList()

  // Stores all required information for emulator images
  data class ApiVersionInfo(
    val apiLevel: Int = 0,
    val imageSource: String = "",
    val apiPreview: String = "",
    // Default is false, On arm machines this value does not have any effect
    val require64Bit: Boolean = false,
  )

  override fun checkEmptyFields(): ManagedVirtualDeviceCatalog {
    this.isEmptyCatalog = this.devices.isEmpty() &&
                          this.apiLevels.isEmpty() &&
                          this.orientation.isEmpty()
    return this
  }

  override fun syncDeviceCatalog(): ManagedVirtualDeviceCatalog {
    val logger: Logger = Logger.getInstance(ManagedVirtualDeviceCatalogState::class.java)
    val iLogger = LogWrapper(logger)
    try {
      // Obtain all devices from Device Manager
      val allDevices =
        DeviceManager.createInstance(AndroidLocationsSingleton, AndroidLocationsSingleton.prefsLocation, iLogger)
          ?.getDevices(DeviceManager.ALL_DEVICES) ?: null
      allDevices?.forEach { device ->
        if (!device.isDeprecated) {
          val deviceSoftware = device.allSoftware
          val maxApiLevel = deviceSoftware.maxOfOrNull { software ->
            software.maxSdkLevel
          } ?: Int.MAX_VALUE

          val minApiLevel = deviceSoftware.minOfOrNull { software ->
            software.minSdkLevel
          } ?: 0

          if (maxApiLevel >= minApiLevel) {
            this.devices[device.id] = AndroidDeviceInfo(deviceName = device.displayName, supportedApiRange = (minApiLevel..maxApiLevel),
                                                        brand = device.manufacturer)
          }
        }
      }

      // Sync with server to obtain latest SDK list
      StudioSdkUtil.reloadRemoteSdkWithModalProgress()
      val progress: LoggerProgressIndicatorWrapper = object : LoggerProgressIndicatorWrapper(iLogger) {
        override fun logVerbose(s: String) = iLogger.verbose(s)
      }
      val repoManager = AndroidSdks.getInstance()?.tryToChooseSdkHandler()?.getSdkManager(progress) ?: null
      val systemImages = repoManager?.packages?.consolidatedPkgs ?: emptyMap()
      systemImages.filter { it.key.contains("system-images") }.forEach {
        val installId = it.key
        val propertyList = installId.split(";")
        val apiInfo = propertyList[1].substring(8)
        val abiInfo = propertyList[3]

        this.apiLevels.add(ApiVersionInfo(
          apiLevel = apiInfo.toIntOrNull() ?: -1,
          apiPreview = if (apiInfo.toIntOrNull() == null) apiInfo else "",
          imageSource = propertyList[2],
          require64Bit = (!abiInfo.contains("arm") && abiInfo.contains("64")),
        ))
      }
      this.orientation.addAll(ScreenOrientation.values().map { it.resourceValue })
      checkEmptyFields()
    } catch (e: Exception) {
      logger.warn("ManagedVirtualDeviceCatalog failed to syncDeviceCatalog", e)
    }
    return this
  }
}
