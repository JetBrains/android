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
import com.android.gmdcodecompletion.GmdDeviceCatalogService
import com.android.gmdcodecompletion.MANAGED_VIRTUAL_DEVICE_CATALOG_UPDATE_FREQUENCY
import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioSdkUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting
import java.util.Calendar
import java.util.EnumSet
import kotlin.concurrent.withLock

// Fix b/272562190 to enable Android TV images
private const val ANDROID_TV_IMAGE = "tv"

// Fix b/272562193 to enable Android Auto images
private const val ANDROID_AUTO_IMAGE = "auto"

private val LOGGER = Logger.getInstance(ManagedVirtualDeviceCatalogService::class.java)

/**
 * This class updates managed virtual devices catalog when local cache is outdated, empty or corrupted
 *
 * PersistentStateComponent must disable roaming type
 */
@State(
  name = "ManagedVirtualDeviceCatalogService",
  storages = [Storage(value = "ManagedVirtualDeviceCatalogService.xml", roamingType = RoamingType.DISABLED)],
)
@Service(Service.Level.APP)
class ManagedVirtualDeviceCatalogService
  : GmdDeviceCatalogService<ManagedVirtualDeviceCatalogState>(ManagedVirtualDeviceCatalogState(), "ManagedVirtualDeviceCatalogService") {
  companion object {
    @JvmStatic
    fun getInstance(): ManagedVirtualDeviceCatalogService =
      ApplicationManager.getApplication().getService(ManagedVirtualDeviceCatalogService::class.java)!!

    @VisibleForTesting
    fun syncDeviceCatalog(): ManagedVirtualDeviceCatalog {
      val deviceCatalog = ManagedVirtualDeviceCatalog()
      val iLogger = LogWrapper(LOGGER)
      try {
        // Sync with server to obtain latest SDK list
        StudioSdkUtil.reloadRemoteSdk(false)
        val progress: LoggerProgressIndicatorWrapper = object : LoggerProgressIndicatorWrapper(iLogger) {
          override fun logVerbose(s: String) = iLogger.verbose(s)
        }
        val repoManager = AndroidSdks.getInstance()?.tryToChooseSdkHandler()?.getSdkManager(progress)
        val systemImages = repoManager?.packages?.consolidatedPkgs ?: emptyMap()
        systemImages.filter {
          it.key.contains("system-images") &&
          !it.key.contains(ANDROID_TV_IMAGE) &&
          !it.key.contains(ANDROID_AUTO_IMAGE)
        }.forEach { (installId, updatablePackage) ->
          val propertyList = installId.split(";")
          val apiInfo = propertyList[1].substring(8)
          val abiInfo = propertyList[3]
          val apiLevel = ((updatablePackage.local ?: updatablePackage.remote)?.typeDetails as? DetailsTypes.SysImgDetailsType)?.apiLevel
                         ?: -1
          val imageSource = propertyList[2].let { if (it == "default") "google" else it }

          if (apiLevel > 0) {
            deviceCatalog.apiLevels.add(ManagedVirtualDeviceCatalog.ApiVersionInfo(
              apiLevel = apiLevel,
              apiPreview = if (apiInfo.toIntOrNull() == null) apiInfo else "",
              imageSource = imageSource,
              require64Bit = (!abiInfo.contains("arm") && abiInfo.contains("64")),
            ))
          }
        }

        val availableApis = deviceCatalog.apiLevels.map { it.apiLevel }

        // Obtain all devices from Device Manager except custom managed devices
        val allDevices =
          DeviceManager.createInstance(AndroidLocationsSingleton, AndroidLocationsSingleton.prefsLocation, iLogger)
            ?.getDevices(EnumSet.of(DeviceManager.DeviceFilter.DEFAULT, DeviceManager.DeviceFilter.VENDOR))
        allDevices?.forEach { device ->
          if (!device.isDeprecated) {
            deviceCatalog.devices[device.displayName] = AndroidDeviceInfo(
              deviceName = "",
              supportedApis = availableApis,
              brand = device.manufacturer
            )
          }
        }
        deviceCatalog.isEmptyCatalog = deviceCatalog.devices.isEmpty() && deviceCatalog.apiLevels.isEmpty()
      }
      catch (e: Exception) {
        LOGGER.warn("ManagedVirtualDeviceCatalog failed to syncDeviceCatalog", e)
      }
      return deviceCatalog
    }
  }

  override fun updateDeviceCatalogTaskAction(project: Project, indicator: ProgressIndicator) {
    indicator.isIndeterminate = false
    indicator.fraction = 0.0

    myLock.withLock {
      indicator.text = "Checking cache freshness"
      if (this.state.isCacheFresh()) return
      val calendar = Calendar.getInstance() // Specify the number of days that device catalog should be updated
      calendar.add(Calendar.DATE, MANAGED_VIRTUAL_DEVICE_CATALOG_UPDATE_FREQUENCY)
      this.state = ManagedVirtualDeviceCatalogState(calendar.time, syncDeviceCatalog())
    }
    indicator.text = "Cache updated"
    indicator.fraction = 1.0
  }
}