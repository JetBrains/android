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
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper
import com.android.sdklib.repository.meta.DetailsTypes.SysImgDetailsType
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioSdkUtil
import com.intellij.openapi.diagnostic.Logger
import kotlin.math.max
import kotlin.math.min

private val LOGGER = Logger.getInstance(ManagedVirtualDeviceCatalogState::class.java)

// Fix b/272562190 to enable Android TV images
private const val ANDROID_TV_IMAGE = "tv"

// Fix b/272562193 to enable Android Auto images
private const val ANDROID_AUTO_IMAGE = "auto"

/**
 * This class fetches and stores information from DeviceManager and RepoManager server to obtain
 * the latest device catalog for managed virtual devices
 */
class ManagedVirtualDeviceCatalog : GmdDeviceCatalog() {

  // Map of <device id, per Android device information>
  val devices: HashMap<String, AndroidDeviceInfo> = HashMap()
  val apiLevels: ArrayList<ApiVersionInfo> = ArrayList()

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
                          this.apiLevels.isEmpty()
    return this
  }

  override fun syncDeviceCatalog(): ManagedVirtualDeviceCatalog {
    val iLogger = LogWrapper(LOGGER)
    try {
      // Sync with server to obtain latest SDK list
      StudioSdkUtil.reloadRemoteSdkWithModalProgress()
      val progress: LoggerProgressIndicatorWrapper = object : LoggerProgressIndicatorWrapper(iLogger) {
        override fun logVerbose(s: String) = iLogger.verbose(s)
      }
      val repoManager = AndroidSdks.getInstance()?.tryToChooseSdkHandler()?.getSdkManager(progress) ?: null
      val systemImages = repoManager?.packages?.consolidatedPkgs ?: emptyMap()
      systemImages.filter {
        it.key.contains("system-images") &&
        !it.key.contains(ANDROID_TV_IMAGE) &&
        !it.key.contains(ANDROID_AUTO_IMAGE)
      }.forEach { (installId, updatablePackage) ->
        val propertyList = installId.split(";")
        val apiInfo = propertyList[1].substring(8)
        val abiInfo = propertyList[3]
        val apiLevel = ((updatablePackage.local ?: updatablePackage.remote)?.typeDetails as? SysImgDetailsType)?.apiLevel ?: -1
        val imageSource = propertyList[2].let { if (it == "default") "google" else it }

        this.apiLevels.add(ApiVersionInfo(
          apiLevel = apiLevel,
          apiPreview = if (apiInfo.toIntOrNull() == null) apiInfo else "",
          imageSource = imageSource,
          require64Bit = (!abiInfo.contains("arm") && abiInfo.contains("64")),
        ))
      }

      // There must be a max API level after we sync with repo manager server, else throw exception
      val maxApiLevel = this.apiLevels.maxOf { it.apiLevel }

      // Obtain all devices from Device Manager
      val allDevices =
        DeviceManager.createInstance(AndroidLocationsSingleton, AndroidLocationsSingleton.prefsLocation, iLogger)
          ?.getDevices(DeviceManager.ALL_DEVICES) ?: null
      allDevices?.forEach { device ->
        if (!device.isDeprecated) {
          val deviceSoftware = device.allSoftware
          val maxDeviceApiLevel = deviceSoftware.maxOfOrNull { software ->
            software.maxSdkLevel
          } ?: Int.MAX_VALUE

          val minDeviceApiLevel = deviceSoftware.minOfOrNull { software ->
            software.minSdkLevel
          } ?: Int.MIN_VALUE

          if (maxDeviceApiLevel >= minDeviceApiLevel) {
            this.devices[device.id] = AndroidDeviceInfo(deviceName = device.displayName,
                                                        supportedApis = (max(minDeviceApiLevel, 0)..
                                                          min(maxDeviceApiLevel, maxApiLevel)).toList(),
                                                        brand = device.manufacturer)
          }
        }
      }
      checkEmptyFields()
    }
    catch (e: Exception) {
      LOGGER.warn("ManagedVirtualDeviceCatalog failed to syncDeviceCatalog", e)
    }
    return this
  }
}
