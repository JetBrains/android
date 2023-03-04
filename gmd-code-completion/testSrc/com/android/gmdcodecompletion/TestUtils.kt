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

import com.android.gmdcodecompletion.ftl.FtlDeviceCatalog
import com.android.gmdcodecompletion.ftl.FtlDeviceCatalogState
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalog
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalogState
import com.android.sdklib.devices.DeviceManager
import com.android.testutils.MockitoKt
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioSdkUtil
import com.google.api.services.testing.model.AndroidDeviceCatalog
import com.google.api.services.testing.model.AndroidModel
import com.google.api.services.testing.model.AndroidRuntimeConfiguration
import com.google.api.services.testing.model.AndroidVersion
import com.google.api.services.testing.model.Locale
import com.google.api.services.testing.model.Orientation
import java.util.Calendar

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

val freshFtlDeviceCatalog: () -> FtlDeviceCatalog = {
  FtlDeviceCatalog().parseAndroidDeviceCatalog(fullAndroidDeviceCatalog)
}

val freshFtlDeviceCatalogState: () -> FtlDeviceCatalogState = {
  val calendar = Calendar.getInstance()
  calendar.add(Calendar.DATE, 1)
  FtlDeviceCatalogState(calendar.time, freshFtlDeviceCatalog())
}

val freshManagedVirtualDeviceCatalogState: () -> ManagedVirtualDeviceCatalogState = {
  val calendar = Calendar.getInstance()
  calendar.add(Calendar.DATE, 1)
  ManagedVirtualDeviceCatalogState(calendar.time, ManagedVirtualDeviceCatalog().syncDeviceCatalog())
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
  MockitoKt.mockStatic<DeviceManager>().use {
    MockitoKt.mockStatic<AndroidSdks>().use {
      MockitoKt.whenever(AndroidSdks.getInstance()).thenReturn(androidSdks)
      MockitoKt.mockStatic<StudioSdkUtil>().use {
        MockitoKt.whenever(StudioSdkUtil.reloadRemoteSdkWithModalProgress()).thenAnswer{}
        MockitoKt.whenever(DeviceManager.createInstance(MockitoKt.any(), MockitoKt.any(), MockitoKt.any())).thenReturn(deviceManager)
        callback()
      }
    }
  }
}