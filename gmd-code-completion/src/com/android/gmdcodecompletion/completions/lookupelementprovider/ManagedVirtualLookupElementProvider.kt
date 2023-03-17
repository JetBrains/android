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
package com.android.gmdcodecompletion.completions.lookupelementprovider

import com.android.gmdcodecompletion.DevicePropertyName
import com.android.gmdcodecompletion.GmdDeviceCatalog
import com.android.gmdcodecompletion.MinAndTargetApiLevel
import com.android.gmdcodecompletion.completions.GmdCodeCompletionLookupElement
import com.android.gmdcodecompletion.completions.GmdDevicePropertyInsertHandler
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalog
import com.android.gmdcodecompletion.removeDoubleQuote
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Generates lookup suggestion list for managed virtual GMDs
 */
object ManagedVirtualLookupElementProvider : BaseLookupElementProvider() {

  private fun String.removeAtdFlag() = this.replace("-$ATD_FLAG", "")

  // If an Android OS image source contains this flag then it's an  automated test device image
  private const val ATD_FLAG = "atd"

  // short names for Android OS image source for managed virtual devices
  private val SYSTEM_IMAGE_SOURCE = listOf("aosp-atd", "aosp", "google-atd", "google")

  override fun generateDevicePropertyValueSuggestionList(devicePropertyName: DevicePropertyName,
                                                         deviceProperties: CurrentDeviceProperties,
                                                         minAndTargetApiLevel: MinAndTargetApiLevel,
                                                         deviceCatalog: GmdDeviceCatalog): Collection<LookupElement> {
    deviceCatalog as ManagedVirtualDeviceCatalog
    return when (devicePropertyName) {
      DevicePropertyName.DEVICE_ID -> generateManagedVirtualDeviceIdSuggestion(deviceProperties, minAndTargetApiLevel, deviceCatalog)
      DevicePropertyName.API_PREVIEW -> generateManagedVirtualDeviceApiPreviewSuggestion(deviceProperties, minAndTargetApiLevel,
                                                                                         deviceCatalog)

      DevicePropertyName.SYS_IMAGE_SOURCE -> generateManagedVirtualDeviceImageSource(deviceProperties, deviceCatalog)
      DevicePropertyName.API_LEVEL -> generateManagedVirtualDeviceApiLevelSuggestion(deviceProperties, minAndTargetApiLevel, deviceCatalog)
      DevicePropertyName.REQUIRE64BIT -> generateManagedVirtualDeviceRequire64Bit(deviceProperties, deviceCatalog)
      else -> emptyList()
    }
  }

  private fun generateManagedVirtualDeviceIdSuggestion(deviceProperties: CurrentDeviceProperties,
                                                       minAndTargetApiLevel: MinAndTargetApiLevel,
                                                       deviceCatalog: ManagedVirtualDeviceCatalog): Collection<LookupElement> {
    var specifiedApiLevel: Int? = deviceProperties[DevicePropertyName.API_LEVEL]?.toIntOrNull()
    val apiPreview = deviceProperties[DevicePropertyName.API_PREVIEW]?.removeDoubleQuote()
    if (specifiedApiLevel != null && apiPreview != null) return emptyList()
    if (apiPreview != null) {
      val apiPreviewLevel = deviceCatalog.apiLevels.filter { it.apiPreview == apiPreview }.map { it.apiLevel }.toSet()
      if (apiPreviewLevel.isNotEmpty()) {
        specifiedApiLevel = apiPreviewLevel.maxOrNull()
      }
    }
    return generateGmdDeviceIdSuggestionHelper(minAndTargetApiLevel, specifiedApiLevel ?: -1, deviceCatalog.devices)
  }

  private fun generateManagedVirtualDeviceApiLevelSuggestion(deviceProperties: CurrentDeviceProperties,
                                                             minAndTargetApiLevel: MinAndTargetApiLevel,
                                                             deviceCatalog: ManagedVirtualDeviceCatalog): Collection<LookupElement> {
    return managedVirtualDeviceApiInfoSuggestionHelper(deviceProperties, minAndTargetApiLevel,
                                                       deviceCatalog) { validApiInfoList, deviceId, targetSdk ->
      var apiList = validApiInfoList.map { it.apiLevel }.toSet()

      if (deviceId.isNotEmpty() && deviceCatalog.devices.contains(deviceId)) {
        apiList = deviceCatalog.devices[deviceId]!!.supportedApis.toSet().intersect(apiList)
      }
      return@managedVirtualDeviceApiInfoSuggestionHelper apiList.map {
        GmdCodeCompletionLookupElement(myValue = it.toString(), myScore = if (targetSdk == it) 1u else 0u)
      }
    }
  }

  private fun generateManagedVirtualDeviceApiPreviewSuggestion(deviceProperties: CurrentDeviceProperties,
                                                               minAndTargetApiLevel: MinAndTargetApiLevel,
                                                               deviceCatalog: ManagedVirtualDeviceCatalog): Collection<LookupElement> {
    return managedVirtualDeviceApiInfoSuggestionHelper(deviceProperties, minAndTargetApiLevel,
                                                       deviceCatalog) { validApiInfoList, deviceId, targetSdk ->
      var apiPreviewList = validApiInfoList.filter { it.apiPreview != "" }.map { Pair(it.apiPreview, it.apiLevel) }.toSet()

      if (deviceId.isNotEmpty() && deviceCatalog.devices.contains(deviceId)) {
        val supportedApis = deviceCatalog.devices[deviceId]!!.supportedApis
        apiPreviewList = apiPreviewList.filter { supportedApis.contains(it.second) }.toSet()
      }

      return@managedVirtualDeviceApiInfoSuggestionHelper apiPreviewList.map { apiPreview ->
        GmdCodeCompletionLookupElement(myValue = apiPreview.first, myScore = if (targetSdk == apiPreview.second) 1u else 0u,
                                       myInsertHandler = GmdDevicePropertyInsertHandler())
      }
    }
  }

  private fun generateManagedVirtualDeviceImageSource(deviceProperties: CurrentDeviceProperties,
                                                      deviceCatalog: ManagedVirtualDeviceCatalog): Collection<LookupElement> {
    val apiLevel = deviceProperties[DevicePropertyName.API_LEVEL]?.toIntOrNull()
    val apiPreview = deviceProperties[DevicePropertyName.API_PREVIEW]?.removeDoubleQuote()
    val require64Bit = deviceProperties[DevicePropertyName.REQUIRE64BIT]?.toBoolean() ?: false
    if (apiLevel != null && apiPreview != null) return emptyList()
    // Drop any image source that is not consistent with existing device definition
    val imageSources = deviceCatalog.apiLevels.filter {
      (apiLevel == null || apiLevel == it.apiLevel) &&
      (apiPreview == null || it.apiPreview == apiPreview) &&
      it.require64Bit == require64Bit
    }.map { it.imageSource }.toSet()

    val mergedResult = (imageSources + SYSTEM_IMAGE_SOURCE.filter { shortImageSource ->
      imageSources.any { fullImageSource ->
        hasMatchingImageSource(fullImageSource, shortImageSource)
      }
    }).toSet()
    return mergedResult.map {
      GmdCodeCompletionLookupElement(myValue = it, myScore = (SYSTEM_IMAGE_SOURCE.indexOf(it) + 1).toUInt(),
                                     myInsertHandler = GmdDevicePropertyInsertHandler())
    }
  }

  private fun generateManagedVirtualDeviceRequire64Bit(deviceProperties: CurrentDeviceProperties,
                                                       deviceCatalog: ManagedVirtualDeviceCatalog): Collection<LookupElement> {
    val apiLevel = deviceProperties[DevicePropertyName.API_LEVEL]?.toIntOrNull()
    val apiPreview = deviceProperties[DevicePropertyName.API_PREVIEW]?.removeDoubleQuote()
    val imageSource = deviceProperties[DevicePropertyName.SYS_IMAGE_SOURCE]?.removeDoubleQuote()
    // API level and API preview should not be present at the same time for a given device
    if (apiLevel != null && apiPreview != null) return emptyList()
    val requires64Bit = deviceCatalog.apiLevels.filter {
      (apiLevel == null || apiLevel == it.apiLevel) &&
      (apiPreview == null || apiPreview == it.apiPreview) &&
      hasMatchingImageSource(imageSource, it.imageSource)
    }.map { it.require64Bit }.toSet()
    return requires64Bit.map { GmdCodeCompletionLookupElement(myValue = it.toString()) }
  }

  /**
   * Helper function that can generate suggestion list for API level and API preview of managed virtual devices
   *
   * @param position caret's current PSI element
   * @param deviceCatalog managed virtual devicess device catalog
   * @param callback function that generate the final suggestion list
   */
  private fun managedVirtualDeviceApiInfoSuggestionHelper(deviceProperties: CurrentDeviceProperties,
                                                          minAndTargetApiLevel: MinAndTargetApiLevel,
                                                          deviceCatalog: ManagedVirtualDeviceCatalog,
                                                          callback: (validApiInfoList: List<ManagedVirtualDeviceCatalog.ApiVersionInfo>,
                                                                     deviceId: String,
                                                                     targetSdk: Int) -> Collection<LookupElement>
  ): Collection<LookupElement> {
    // Return if API preview is set
    if (deviceProperties[DevicePropertyName.API_LEVEL] != null || deviceProperties[DevicePropertyName.API_PREVIEW] != null) return emptyList()

    val deviceId = deviceProperties[DevicePropertyName.DEVICE_ID]?.removeDoubleQuote() ?: ""
    val require64Bit = deviceProperties[DevicePropertyName.REQUIRE64BIT]?.toBoolean() ?: false
    val imageSource = deviceProperties[DevicePropertyName.SYS_IMAGE_SOURCE]?.removeDoubleQuote()

    val validApiInfoList = deviceCatalog.apiLevels.filter {
      it.apiLevel > 0 && it.apiLevel >= minAndTargetApiLevel.minSdk &&
      it.require64Bit == require64Bit && hasMatchingImageSource(imageSource, it.imageSource)
    }

    return callback(validApiInfoList, deviceId, minAndTargetApiLevel.targetSdk)
  }

  // Returns true if deviceImageSource is a subset of currentImageSource. Else returns false
  private fun hasMatchingImageSource(deviceImageSource: String?, currentImageSource: String): Boolean {
    return (deviceImageSource == null ||
            (deviceImageSource.contains(currentImageSource.removeAtdFlag()) &&
             (!currentImageSource.contains(ATD_FLAG) ||
              (currentImageSource.contains(ATD_FLAG) && deviceImageSource.contains(ATD_FLAG)))))
  }
}