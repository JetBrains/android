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

import com.android.gmdcodecompletion.AndroidDeviceInfo
import com.android.gmdcodecompletion.DevicePropertyName
import com.android.gmdcodecompletion.GmdDeviceCatalog
import com.android.gmdcodecompletion.completions.GmdCodeCompletionLookupElement
import com.android.gmdcodecompletion.completions.GmdDevicePropertyInsertHandler
import com.android.gmdcodecompletion.ftl.FtlDeviceCatalog
import com.android.gmdcodecompletion.lookupElementProviderTestHelper
import com.android.gmdcodecompletion.testFtlDeviceLocale
import com.android.gmdcodecompletion.testFtlDeviceOrientation
import com.android.gmdcodecompletion.testMinAndTargetApiLevel
import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.junit.Test

class FtlLookupElementProviderTest {

  private fun ftlTestHelper(devicePropertyName: DevicePropertyName, currentDeviceProperties: CurrentDeviceProperties,
                            deviceCatalog: GmdDeviceCatalog, expectedResult: List<GmdCodeCompletionLookupElement>) =
    lookupElementProviderTestHelper(devicePropertyName, currentDeviceProperties, deviceCatalog, expectedResult, FtlLookupElementProvider)

  @Test
  fun testGenerateFtlLocaleSuggestion() {
    val testDeviceCatalog = FtlDeviceCatalog().apply { this.locale.putAll(testFtlDeviceLocale) }
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = "lang1",
                                     myScore = 0u,
                                     myPresentation = LookupElementPresentation().apply {
                                       itemText = "lang1"
                                       tailText = "  langName1  region1"
                                     }),
      GmdCodeCompletionLookupElement(myValue = "lang2",
                                     myScore = 0u,
                                     myPresentation = LookupElementPresentation().apply {
                                       itemText = "lang2"
                                       tailText = "  langName2  region2"
                                     }),
      GmdCodeCompletionLookupElement(myValue = "lang3",
                                     myScore = 0u,
                                     myPresentation = LookupElementPresentation().apply {
                                       itemText = "lang3"
                                       tailText = "  langName3  region3"
                                     })
    )
    ftlTestHelper(DevicePropertyName.LOCALE, hashMapOf(), testDeviceCatalog, expectedResult)
  }

  @Test
  fun testGenerateFtlOrientationSuggestion() {
    val testDeviceCatalog = FtlDeviceCatalog().apply { this.orientation.addAll(testFtlDeviceOrientation) }
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = "default",
                                     myScore = 0u,
                                     myInsertHandler = GmdDevicePropertyInsertHandler()),
      GmdCodeCompletionLookupElement(myValue = "horizontal",
                                     myScore = 0u,
                                     myInsertHandler = GmdDevicePropertyInsertHandler()),
      GmdCodeCompletionLookupElement(myValue = "vertical",
                                     myScore = 0u,
                                     myInsertHandler = GmdDevicePropertyInsertHandler())
    )
    ftlTestHelper(DevicePropertyName.ORIENTATION, hashMapOf(), testDeviceCatalog, expectedResult)
  }

  @Test
  fun testGenerateDeviceIdSuggestion_apiLevelTooLow() {
    val testFtlDeviceInfoMap = hashMapOf(
      "testdevice2" to AndroidDeviceInfo(supportedApis = listOf(testMinAndTargetApiLevel.minSdk - 1)))
    val testDeviceCatalog = FtlDeviceCatalog().apply { this.devices.putAll(testFtlDeviceInfoMap) }
    ftlTestHelper(DevicePropertyName.DEVICE_ID, hashMapOf(), testDeviceCatalog, emptyList())
  }

  @Test
  fun testGenerateDeviceIdSuggestion_matchSpecifiedApi() {
    val currentDeviceProperties: CurrentDeviceProperties = hashMapOf(DevicePropertyName.API_LEVEL to "25")
    val deviceMap = mapOf(
      "device1" to AndroidDeviceInfo(
        deviceName = "Phone1",
        deviceForm = "physical",
        formFactor = "phone",
        brand = "Google",
        supportedApis = listOf(24)
      ),
      "device2" to AndroidDeviceInfo(
        deviceName = "Phone2",
        deviceForm = "virtual",
        formFactor = "tablet",
        brand = "Samsung",
        supportedApis = listOf(25)
      ),
    )
    val testDeviceCatalog = FtlDeviceCatalog().apply { this.devices.putAll(deviceMap) }
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = "device2",
                                     myScore = 35u,
                                     myInsertHandler = GmdDevicePropertyInsertHandler(),
                                     myPresentation = LookupElementPresentation().apply {
                                       itemText = "device2"
                                       tailText = "  Phone2  virtual"
                                     })
    )
    ftlTestHelper(DevicePropertyName.DEVICE_ID, currentDeviceProperties, testDeviceCatalog, expectedResult)
  }

  @Test
  fun testGenerateDeviceIdSuggestion_matchTargetApi() {
    val deviceMap = mapOf(
      "device1" to AndroidDeviceInfo(
        deviceName = "Phone1",
        deviceForm = "physical",
        formFactor = "phone",
        brand = "Google",
        supportedApis = listOf(testMinAndTargetApiLevel.targetSdk - 1)
      ),
      "device2" to AndroidDeviceInfo(
        deviceName = "Phone2",
        deviceForm = "virtual",
        formFactor = "tablet",
        brand = "Samsung",
        supportedApis = listOf(testMinAndTargetApiLevel.targetSdk)
      ),
    )
    val testDeviceCatalog = FtlDeviceCatalog().apply { this.devices.putAll(deviceMap) }
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = "device2",
                                     myScore = 99u,
                                     myInsertHandler = GmdDevicePropertyInsertHandler(),
                                     myPresentation = LookupElementPresentation().apply {
                                       itemText = "device2"
                                       tailText = "  Phone2  "
                                     }),
      GmdCodeCompletionLookupElement(myValue = "device1",
                                     myScore = 44u,
                                     myInsertHandler = GmdDevicePropertyInsertHandler(),
                                     myPresentation = LookupElementPresentation().apply {
                                       itemText = "device1"
                                       tailText = "  Phone1  "
                                     }),
    )
    ftlTestHelper(DevicePropertyName.DEVICE_ID, hashMapOf(), testDeviceCatalog, expectedResult)
  }

  @Test
  fun testGenerateDeviceIdSuggestion_brandPriority() {
    val deviceMap = mapOf(
      "device1" to AndroidDeviceInfo(
        deviceName = "Phone1",
        formFactor = "phone",
        brand = "Samsung",
        supportedApis = listOf(testMinAndTargetApiLevel.targetSdk)
      ),
      "device2" to AndroidDeviceInfo(
        deviceName = "Phone2",
        formFactor = "tablet",
        brand = "Google",
        supportedApis = listOf(testMinAndTargetApiLevel.targetSdk)
      ),
    )
    val testDeviceCatalog = FtlDeviceCatalog().apply { this.devices.putAll(deviceMap) }
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = "device2",
                                     myScore = 107u,
                                     myInsertHandler = GmdDevicePropertyInsertHandler(),
                                     myPresentation = LookupElementPresentation().apply {
                                       itemText = "device2"
                                       tailText = "  Phone2  "
                                     }),
      GmdCodeCompletionLookupElement(myValue = "device1",
                                     myScore = 100u,
                                     myInsertHandler = GmdDevicePropertyInsertHandler(),
                                     myPresentation = LookupElementPresentation().apply {
                                       itemText = "device1"
                                       tailText = "  Phone1  "
                                     }),
    )
    ftlTestHelper(DevicePropertyName.DEVICE_ID, hashMapOf(), testDeviceCatalog, expectedResult)
  }

  @Test
  fun testGenerateDeviceIdSuggestion_deviceForm() {
    val deviceMap = mapOf(
      "device1" to AndroidDeviceInfo(
        deviceName = "Phone1",
        formFactor = "tablet",
        brand = "Google",
        supportedApis = listOf(testMinAndTargetApiLevel.targetSdk)
      ),
      "device2" to AndroidDeviceInfo(
        deviceName = "Phone2",
        formFactor = "phone",
        brand = "Google",
        supportedApis = listOf(testMinAndTargetApiLevel.targetSdk)
      ),
    )
    val testDeviceCatalog = FtlDeviceCatalog().apply { this.devices.putAll(deviceMap) }
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = "device2",
                                     myScore = 108u,
                                     myInsertHandler = GmdDevicePropertyInsertHandler(),
                                     myPresentation = LookupElementPresentation().apply {
                                       itemText = "device2"
                                       tailText = "  Phone2  "
                                     }),
      GmdCodeCompletionLookupElement(myValue = "device1",
                                     myScore = 107u,
                                     myInsertHandler = GmdDevicePropertyInsertHandler(),
                                     myPresentation = LookupElementPresentation().apply {
                                       itemText = "device1"
                                       tailText = "  Phone1  "
                                     }),
    )
    ftlTestHelper(DevicePropertyName.DEVICE_ID, hashMapOf(), testDeviceCatalog, expectedResult)
  }

  @Test
  fun testGenerateApiLevelSuggestion_filterWithMinSdk() {
    val testDeviceCatalog = FtlDeviceCatalog().apply {
      this.apiLevels.addAll(listOf(31, 33, 32, 19))
    }
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = "33", myScore = 1u),
      GmdCodeCompletionLookupElement(myValue = "31", myScore = 0u),
      GmdCodeCompletionLookupElement(myValue = "32", myScore = 0u),
    )
    ftlTestHelper(DevicePropertyName.API_LEVEL, hashMapOf(), testDeviceCatalog, expectedResult)
  }

  @Test
  fun testGenerateApiLevelSuggestion_filterWithDeviceSupportedApiAndMinSdk() {
    val testDeviceId = "device1"
    val currentDeviceProperties: CurrentDeviceProperties = hashMapOf(DevicePropertyName.DEVICE_ID to testDeviceId)
    val testDeviceCatalog = FtlDeviceCatalog().apply {
      this.apiLevels.addAll(listOf(31, 33, 32, 19))
      this.devices[testDeviceId] = AndroidDeviceInfo(
        deviceName = "Phone1",
        formFactor = "tablet",
        brand = "Google",
        supportedApis = listOf(19, 20, 21)
      )
    }
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = "20", myScore = 0u),
      GmdCodeCompletionLookupElement(myValue = "21", myScore = 0u),
    )
    ftlTestHelper(DevicePropertyName.API_LEVEL, currentDeviceProperties, testDeviceCatalog, expectedResult)
  }

  @Test
  fun testGenerateApiLevelSuggestion_filterWithDeviceSupportedApiAndTargetSdk() {
    val testDeviceId = "device1"
    val currentDeviceProperties: CurrentDeviceProperties = hashMapOf(DevicePropertyName.DEVICE_ID to testDeviceId)
    val testDeviceCatalog = FtlDeviceCatalog().apply {
      this.apiLevels.addAll(listOf(31, 33, 32, 19))
      this.devices[testDeviceId] = AndroidDeviceInfo(
        deviceName = "Phone1",
        formFactor = "tablet",
        brand = "Google",
        supportedApis = listOf(19, 20, 21, 33)
      )
    }
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = "33", myScore = 1u),
      GmdCodeCompletionLookupElement(myValue = "20", myScore = 0u),
      GmdCodeCompletionLookupElement(myValue = "21", myScore = 0u),
    )
    ftlTestHelper(DevicePropertyName.API_LEVEL, currentDeviceProperties, testDeviceCatalog, expectedResult)
  }
}