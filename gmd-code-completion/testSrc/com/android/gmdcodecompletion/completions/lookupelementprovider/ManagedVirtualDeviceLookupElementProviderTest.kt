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
import com.android.gmdcodecompletion.ConfigurationParameterName
import com.android.gmdcodecompletion.ConfigurationParameterName.API_LEVEL
import com.android.gmdcodecompletion.ConfigurationParameterName.API_PREVIEW
import com.android.gmdcodecompletion.ConfigurationParameterName.DEVICE_ID
import com.android.gmdcodecompletion.ConfigurationParameterName.REQUIRE64BIT
import com.android.gmdcodecompletion.ConfigurationParameterName.SYS_IMAGE_SOURCE
import com.android.gmdcodecompletion.GmdDeviceCatalog
import com.android.gmdcodecompletion.completions.GmdCodeCompletionLookupElement
import com.android.gmdcodecompletion.completions.GmdDevicePropertyInsertHandler
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalog
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalog.ApiVersionInfo
import com.android.gmdcodecompletion.testMinAndTargetApiLevel
import com.android.gmdcodecompletion.verifyConfigurationLookupElementProviderResult
import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.junit.Test

class ManagedVirtualDeviceLookupElementProviderTest {

  private fun managedVirtualTestHelper(configurationParameterName: ConfigurationParameterName,
                                       currentDeviceProperties: CurrentDeviceProperties,
                                       deviceCatalog: GmdDeviceCatalog,
                                       expectedResult: Collection<GmdCodeCompletionLookupElement>) {
    val result = ManagedVirtualLookupElementProvider.generateDevicePropertyValueSuggestionList(configurationParameterName,
                                                                                               currentDeviceProperties,
                                                                                               testMinAndTargetApiLevel,
                                                                                               deviceCatalog)
    verifyConfigurationLookupElementProviderResult(result, expectedResult)
  }

  @Test
  fun testGenerateDeviceIdSuggestion_matchApiPreview() {
    val testApiPreview = "testPreview"
    val currentDeviceProperties: CurrentDeviceProperties = hashMapOf(API_PREVIEW to testApiPreview)
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
    val testDeviceCatalog = ManagedVirtualDeviceCatalog().apply {
      this.devices.putAll(deviceMap)
      this.apiLevels.add(ApiVersionInfo(apiLevel = 25, apiPreview = testApiPreview))
    }
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = "device2",
                                     myScore = 35u,
                                     myInsertHandler = GmdDevicePropertyInsertHandler(),
                                     myPresentation = LookupElementPresentation().apply {
                                       itemText = "device2"
                                       tailText = "  Phone2  virtual"
                                     })
    )
    managedVirtualTestHelper(DEVICE_ID, currentDeviceProperties, testDeviceCatalog, expectedResult)
  }

  @Test
  fun testGenerateApiLevelSuggestion_matchAllCriteria() {
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = "${testMinAndTargetApiLevel.targetSdk}", myScore = 1u),
      GmdCodeCompletionLookupElement(myValue = "${testMinAndTargetApiLevel.targetSdk - 2}", myScore = 0u)
    )
    testApiLevelAndPreviewHelper(API_LEVEL, expectedResult)
  }

  @Test
  fun testGenerateApiPreviewSuggestion_matchAllCriteria() {
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = "preview1", myScore = 1u),
      GmdCodeCompletionLookupElement(myValue = "preview4", myScore = 0u)
    )
    testApiLevelAndPreviewHelper(API_PREVIEW, expectedResult)
  }

  @Test
  fun testGenerateRequires64BitSuggestion_matchAllCriteria() {
    val testDeviceId = "testDeviceId"
    val testImageSource = "testSource"
    val testRequires64Bit = true
    val testApiPreview = "preview1"
    val currentDeviceProperties: CurrentDeviceProperties = hashMapOf(
      DEVICE_ID to testDeviceId,
      SYS_IMAGE_SOURCE to testImageSource,
      API_PREVIEW to testApiPreview
    )
    val deviceMap = mapOf(
      testDeviceId to AndroidDeviceInfo(
        deviceName = "Phone1",
        deviceForm = "physical",
        formFactor = "phone",
        brand = "Google",
        supportedApis = (testMinAndTargetApiLevel.minSdk..testMinAndTargetApiLevel.targetSdk).toList()
      )
    )
    val testApiInfo = listOf(
      ApiVersionInfo(
        apiLevel = testMinAndTargetApiLevel.targetSdk,
        imageSource = testImageSource,
        require64Bit = testRequires64Bit,
        apiPreview = testApiPreview
      ),
      ApiVersionInfo(
        apiLevel = testMinAndTargetApiLevel.targetSdk,
        imageSource = testImageSource,
        require64Bit = !testRequires64Bit,
        apiPreview = "preview2"
      )
    )
    val testDeviceCatalog = ManagedVirtualDeviceCatalog().apply {
      this.devices.putAll(deviceMap)
      this.apiLevels.addAll(testApiInfo)
    }
    val expectedResult = listOf(GmdCodeCompletionLookupElement(myValue = testRequires64Bit.toString()))
    managedVirtualTestHelper(REQUIRE64BIT, currentDeviceProperties, testDeviceCatalog, expectedResult)
  }

  @Test
  fun testGenerateSystemImageSuggestion_matchAllCriteria() {
    val testImageSource = "testSource"
    val testRequires64Bit = true
    val testApiPreview = "preview1"
    val currentDeviceProperties: CurrentDeviceProperties = hashMapOf(
      API_PREVIEW to testApiPreview,
      REQUIRE64BIT to testRequires64Bit.toString()
    )
    val testApiInfo = listOf(
      ApiVersionInfo(
        apiLevel = testMinAndTargetApiLevel.targetSdk,
        imageSource = testImageSource,
        require64Bit = testRequires64Bit,
        apiPreview = testApiPreview
      ),
      ApiVersionInfo(
        apiLevel = testMinAndTargetApiLevel.targetSdk,
        imageSource = testImageSource,
        require64Bit = testRequires64Bit,
        apiPreview = "$testApiPreview additional"
      )
    )
    val testDeviceCatalog = ManagedVirtualDeviceCatalog().apply {
      this.apiLevels.addAll(testApiInfo)
    }
    val expectedResult = listOf(GmdCodeCompletionLookupElement(myValue = testImageSource))
    managedVirtualTestHelper(SYS_IMAGE_SOURCE, currentDeviceProperties, testDeviceCatalog, expectedResult)
  }

  @Test
  fun testGenerateSystemImageSuggestion_suggestionRanker() {
    val testImageSource = "google"
    val testRequires64Bit = true
    val currentDeviceProperties: CurrentDeviceProperties = hashMapOf(
      API_LEVEL to testMinAndTargetApiLevel.targetSdk.toString(),
      REQUIRE64BIT to testRequires64Bit.toString()
    )
    val testApiInfo = listOf(
      ApiVersionInfo(
        apiLevel = testMinAndTargetApiLevel.targetSdk,
        imageSource = "${testImageSource}_atd_custom_test",
        require64Bit = testRequires64Bit,
      ),
      ApiVersionInfo(
        apiLevel = testMinAndTargetApiLevel.targetSdk,
        imageSource = "${testImageSource}-atd",
        require64Bit = testRequires64Bit,
      ),
      ApiVersionInfo(
        apiLevel = testMinAndTargetApiLevel.targetSdk,
        imageSource = testImageSource,
        require64Bit = testRequires64Bit,
      ),
      ApiVersionInfo(
        apiLevel = testMinAndTargetApiLevel.targetSdk,
        imageSource = "${testImageSource}excluded",
        require64Bit = !testRequires64Bit,
        apiPreview = "preview2"
      )
    )
    val testDeviceCatalog = ManagedVirtualDeviceCatalog().apply {
      this.apiLevels.addAll(testApiInfo)
    }
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = testImageSource, myScore = 4u),
      GmdCodeCompletionLookupElement(myValue = "${testImageSource}-atd", myScore = 3u),
      GmdCodeCompletionLookupElement(myValue = "${testImageSource}_atd_custom_test"),
    )
    managedVirtualTestHelper(SYS_IMAGE_SOURCE, currentDeviceProperties, testDeviceCatalog, expectedResult)
  }

  private fun testApiLevelAndPreviewHelper(configurationParameterName: ConfigurationParameterName,
                                           expectedResult: List<GmdCodeCompletionLookupElement>) {
    val testDeviceId = "testDeviceId"
    val testImageSource = "testSource"
    val testRequires64Bit = true
    val currentDeviceProperties: CurrentDeviceProperties = hashMapOf(
      DEVICE_ID to testDeviceId,
      REQUIRE64BIT to testRequires64Bit.toString(),
      SYS_IMAGE_SOURCE to testImageSource)
    val deviceMap = mapOf(
      testDeviceId to AndroidDeviceInfo(
        deviceName = "Phone1",
        deviceForm = "physical",
        formFactor = "phone",
        brand = "Google",
        supportedApis = (testMinAndTargetApiLevel.minSdk..testMinAndTargetApiLevel.targetSdk).toList()
      )
    )
    val testApiInfo = listOf(
      ApiVersionInfo(
        apiLevel = testMinAndTargetApiLevel.targetSdk,
        imageSource = testImageSource,
        require64Bit = testRequires64Bit,
        apiPreview = "preview1"
      ),
      ApiVersionInfo(
        apiLevel = testMinAndTargetApiLevel.targetSdk,
        imageSource = testImageSource,
        require64Bit = !testRequires64Bit,
        apiPreview = "preview2"
      ),
      ApiVersionInfo(
        apiLevel = testMinAndTargetApiLevel.targetSdk,
        imageSource = "${testImageSource}extra string",
        require64Bit = testRequires64Bit,
        apiPreview = "preview3"
      ),
      ApiVersionInfo(
        apiLevel = testMinAndTargetApiLevel.targetSdk - 2,
        imageSource = testImageSource,
        require64Bit = testRequires64Bit,
        apiPreview = "preview4"
      ),
    )
    val testDeviceCatalog = ManagedVirtualDeviceCatalog().apply {
      this.devices.putAll(deviceMap)
      this.apiLevels.addAll(testApiInfo)
    }
    managedVirtualTestHelper(configurationParameterName, currentDeviceProperties, testDeviceCatalog, expectedResult)
  }
}