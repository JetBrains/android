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
import com.android.gmdcodecompletion.ftl.FtlDeviceCatalog
import com.android.gmdcodecompletion.removeDoubleQuote
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

/**
 * Generates lookup suggestion list for FTL GMDs
 */
object FtlLookupElementProvider : BaseLookupElementProvider() {

  override fun generateDevicePropertyValueSuggestionList(devicePropertyName: DevicePropertyName,
                                                         deviceProperties: CurrentDeviceProperties,
                                                         minAndTargetApiLevel: MinAndTargetApiLevel,
                                                         deviceCatalog: GmdDeviceCatalog): Collection<LookupElement> {
    deviceCatalog as FtlDeviceCatalog
    return when (devicePropertyName) {
      DevicePropertyName.DEVICE_ID -> generateFtlDeviceIdSuggestion(deviceProperties, minAndTargetApiLevel, deviceCatalog)
      DevicePropertyName.LOCALE -> generateFtlLocaleSuggestion(deviceCatalog)
      DevicePropertyName.ORIENTATION -> generateFtlOrientationSuggestion(deviceCatalog)
      DevicePropertyName.API_LEVEL -> generateFtlApiLevelSuggestion(deviceProperties, minAndTargetApiLevel, deviceCatalog)
      else -> emptyList()
    }
  }

  private fun generateFtlApiLevelSuggestion(deviceProperties: CurrentDeviceProperties,
                                            minAndTargetApiLevel: MinAndTargetApiLevel,
                                            ftlDeviceCatalog: FtlDeviceCatalog): Collection<LookupElement> {
    if (deviceProperties[DevicePropertyName.API_LEVEL] != null) return emptyList()
    val deviceId = deviceProperties[DevicePropertyName.DEVICE_ID]?.removeDoubleQuote() ?: ""

    val apiList = if (deviceId.isEmpty() || !ftlDeviceCatalog.devices.contains(deviceId)) {
      ftlDeviceCatalog.apiLevels.map { it }
    }
    else {
      ftlDeviceCatalog.devices[deviceId]?.supportedApis ?: return emptyList()
    }

    return apiList.filter { it >= minAndTargetApiLevel.minSdk }.map {
      GmdCodeCompletionLookupElement(myValue = it.toString(), myScore = if (minAndTargetApiLevel.targetSdk == it) 1u else 0u)
    }
  }

  private fun generateFtlLocaleSuggestion(deviceCatalog: FtlDeviceCatalog): Collection<LookupElement> {
    return deviceCatalog.locale.map { (localeId, localeInfo) ->
      val presentation = LookupElementPresentation()
      presentation.itemText = localeId
      presentation.tailText = "  ${localeInfo.languageName}  ${localeInfo.region}"
      GmdCodeCompletionLookupElement(myValue = localeId, myPresentation = presentation,
                                     myInsertHandler = GmdDevicePropertyInsertHandler())
    }
  }

  private fun generateFtlOrientationSuggestion(deviceCatalog: FtlDeviceCatalog): Collection<LookupElement> {
    return deviceCatalog.orientation.map {
      GmdCodeCompletionLookupElement(myValue = it.toLowerCaseAsciiOnly(),
                                     myInsertHandler = GmdDevicePropertyInsertHandler())
    }
  }

  private fun generateFtlDeviceIdSuggestion(deviceProperties: CurrentDeviceProperties,
                                            minAndTargetApiLevel: MinAndTargetApiLevel,
                                            deviceCatalog: FtlDeviceCatalog): Collection<LookupElement> {
    val specifiedApiLevel = deviceProperties[DevicePropertyName.API_LEVEL]?.toIntOrNull() ?: -1
    return generateGmdDeviceIdSuggestionHelper(minAndTargetApiLevel, specifiedApiLevel, deviceCatalog.devices)
  }
}