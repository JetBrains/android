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
package com.android.gmdcodecompletion

import com.android.gmdcodecompletion.completions.GmdCodeCompletionLookupElement
import com.android.gmdcodecompletion.completions.lookupelementprovider.BaseLookupElementProvider
import com.android.gmdcodecompletion.completions.lookupelementprovider.CurrentDeviceProperties
import com.android.gmdcodecompletion.completions.lookupelementprovider.FtlLookupElementProvider
import com.android.gmdcodecompletion.ftl.FtlDeviceCatalog
import com.android.gmdcodecompletion.ftl.FtlDeviceCatalogState
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalog
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalogState
import com.android.sdklib.devices.DeviceManager
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mockStatic
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioSdkUtil
import com.google.api.services.testing.model.AndroidDeviceCatalog
import com.google.api.services.testing.model.AndroidModel
import com.google.api.services.testing.model.AndroidRuntimeConfiguration
import com.google.api.services.testing.model.AndroidVersion
import com.google.api.services.testing.model.Locale
import com.google.api.services.testing.model.Orientation
import org.junit.Assert.assertEquals
import java.util.Calendar

enum class BuildFileName(val fileName: String) {
  GROOVY_BUILD_FILE("build.gradle"),
  KOTLIN_BUILD_FILE("build.gradle.kts"),
  OTHER_FILE("build.otherfile"),
}

val fullAndroidModel: AndroidModel = AndroidModel()
  .setId("testDeviceId")
  .setName("testDeviceName")
  .setBrand("testDeviceBrand")
  .set("formFactor", "PHONE")
  .setForm("PHYSICAL")
  .setSupportedVersionIds(listOf("31", "32"))

val fullAndroidVersion: AndroidVersion = AndroidVersion().setApiLevel(33)

val fullAndroidRuntimeConfiguration: AndroidRuntimeConfiguration = AndroidRuntimeConfiguration()
  .setLocales(listOf(Locale().setId("testLocaleId").setRegion("testLocaleRegion").setName("testLocaleName")))
  .setOrientations(listOf(Orientation().setId("testOrientationId")))

val fullAndroidDeviceCatalog: AndroidDeviceCatalog = AndroidDeviceCatalog()
  .setModels(listOf(fullAndroidModel))
  .setVersions(listOf(fullAndroidVersion))
  .setRuntimeConfiguration(fullAndroidRuntimeConfiguration)

val testFtlDeviceOrientation = listOf("horizontal", "vertical", "default")
val testFtlDeviceLocale: HashMap<String, FtlDeviceCatalog.LocaleInfo> = hashMapOf(
  "lang2" to FtlDeviceCatalog.LocaleInfo("langName2", "region2"),
  "lang1" to FtlDeviceCatalog.LocaleInfo("langName1", "region1"),
  "lang3" to FtlDeviceCatalog.LocaleInfo("langName3", "region3"))
val testFtlDeviceApiLevels = (1..34).toList()

val fullFtlDeviceCatalog: () -> FtlDeviceCatalog = {
  val deviceCatalog = FtlDeviceCatalog()
  deviceCatalog.orientation.addAll(testFtlDeviceOrientation)
  deviceCatalog.locale.putAll(testFtlDeviceLocale)
  deviceCatalog.apiLevels.addAll(testFtlDeviceApiLevels)
  deviceCatalog.checkEmptyFields()
  deviceCatalog
}

val fullManagedVirtualDeviceCatalog: () -> ManagedVirtualDeviceCatalog = {
  val deviceCatalog = ManagedVirtualDeviceCatalog()
  deviceCatalog.apiLevels.add(ManagedVirtualDeviceCatalog.ApiVersionInfo())
  deviceCatalog.devices["testDevice"] = AndroidDeviceInfo()
  deviceCatalog.checkEmptyFields()
  deviceCatalog
}

val freshFtlDeviceCatalogState: () -> FtlDeviceCatalogState = {
  val calendar = Calendar.getInstance()
  calendar.add(Calendar.DATE, 1)
  FtlDeviceCatalogState(calendar.time, fullFtlDeviceCatalog())
}

val fullManagedVirtualDeviceCatalogState: () -> ManagedVirtualDeviceCatalogState = {
  val calendar = Calendar.getInstance()
  calendar.add(Calendar.DATE, 1)
  ManagedVirtualDeviceCatalogState(calendar.time, fullManagedVirtualDeviceCatalog())
}

fun matchFtlDeviceCatalog(ftlDeviceCatalog: FtlDeviceCatalog, androidDeviceCatalog: AndroidDeviceCatalog): Boolean {
  return androidDeviceCatalog.models.all { androidModel ->
    val deviceInfo = ftlDeviceCatalog.devices[androidModel.id]
    deviceInfo != null &&
    deviceInfo.deviceName == androidModel.name &&
    deviceInfo.formFactor == androidModel["formFactor"] &&
    deviceInfo.deviceForm == androidModel.form &&
    deviceInfo.brand == androidModel.brand &&
    deviceInfo.supportedApis.map { it.toString() } == androidModel.supportedVersionIds
  } &&
         androidDeviceCatalog.versions.map { it.apiLevel } == ftlDeviceCatalog.apiLevels &&
         androidDeviceCatalog.runtimeConfiguration.orientations.map { it.id } == ftlDeviceCatalog.orientation &&
         androidDeviceCatalog.runtimeConfiguration.locales.all { locale ->
           val localeInfo = ftlDeviceCatalog.locale[locale.id]
           localeInfo != null &&
           localeInfo.region == locale.region &&
           localeInfo.languageName == locale.name
         }
}

fun managedVirtualDeviceCatalogTestHelper(
  deviceManager: DeviceManager?,
  androidSdks: AndroidSdks?,
  callback: () -> Unit) {
  mockStatic<DeviceManager>().use {
    mockStatic<AndroidSdks>().use {
      whenever(AndroidSdks.getInstance()).thenReturn(androidSdks)
      mockStatic<StudioSdkUtil>().use {
        whenever(StudioSdkUtil.reloadRemoteSdkWithModalProgress()).thenAnswer {}
        whenever(DeviceManager.createInstance(any(), any(), any())).thenReturn(deviceManager)
        callback()
      }
    }
  }
}

val testMinAndTargetApiLevel = MinAndTargetApiLevel(targetSdk = 33, minSdk = 20)

fun lookupElementProviderTestHelper(devicePropertyName: DevicePropertyName, currentDeviceProperties: CurrentDeviceProperties,
                                    deviceCatalog: GmdDeviceCatalog, expectedResult: List<GmdCodeCompletionLookupElement>,
                                    lookupElementProvider: BaseLookupElementProvider) {
  val result = lookupElementProvider.generateDevicePropertyValueSuggestionList(devicePropertyName,
                                                                               currentDeviceProperties,
                                                                               testMinAndTargetApiLevel,
                                                                               deviceCatalog)
  val sortedResult = result.map { it as GmdCodeCompletionLookupElement }
    .sortedWith { element1, element2 ->
      element1.compareTo(element2)
    }
  assertEquals(expectedResult.size, sortedResult.size)
  sortedResult.zip(expectedResult).forEach { (element1, element2) ->
    assertEquals(element1.compareTo(element2), 0)
  }
}