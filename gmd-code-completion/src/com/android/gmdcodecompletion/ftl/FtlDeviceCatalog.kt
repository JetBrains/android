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
package com.android.gmdcodecompletion.ftl

import com.android.gmdcodecompletion.AndroidDeviceInfo
import com.android.gmdcodecompletion.GmdDeviceCatalog
import com.google.api.services.testing.model.AndroidDeviceCatalog
import com.google.gct.testing.launcher.CloudAuthenticator

/** This class fetches and stores information from FTL android device catalog */
class FtlDeviceCatalog : GmdDeviceCatalog() {
  // Map of <device id, per Android device information>
  val devices: HashMap<String, AndroidDeviceInfo> = HashMap()
  val apiLevels: ArrayList<Int> = ArrayList()
  val orientation: ArrayList<String> = ArrayList()
  val locale: HashMap<String, LocaleInfo> = HashMap()

  // Stores additional information for device locale
  data class LocaleInfo(
    val languageName: String,
    val region: String,
  )

  override fun checkEmptyFields(): FtlDeviceCatalog{
    this.isEmptyCatalog = this.devices.isEmpty() &&
                          this.apiLevels.isEmpty() &&
                          this.orientation.isEmpty() &&
                          this.locale.isEmpty()
    return this
  }

  override fun syncDeviceCatalog(): FtlDeviceCatalog {
    val deviceCatalog = CloudAuthenticator.getInstance()?.androidDeviceCatalog ?: return this
    parseAndroidDeviceCatalog(deviceCatalog)
    return this
  }

  // parse AndroidDeviceCatalog and store necessary data in current FtlDeviceCatalog object
  fun parseAndroidDeviceCatalog(deviceCatalog: AndroidDeviceCatalog): FtlDeviceCatalog {
    if (deviceCatalog.isEmpty()) return this
    deviceCatalog.models?.forEach { androidModel ->
      val versionIds = androidModel.supportedVersionIds ?: emptyList()

      if (versionIds.isNotEmpty() && androidModel.id != null) {
        this.devices[androidModel.id] = AndroidDeviceInfo(
          deviceName = androidModel.name ?: "",
          supportedApis = versionIds.mapNotNull { it.toIntOrNull() },
          brand = androidModel.brand ?: "",
          formFactor = androidModel["formFactor"]?.toString() ?: "",
          deviceForm = androidModel.form ?: "",
        )
      }
    }
    this.apiLevels.addAll(deviceCatalog.versions?.mapNotNull { it.apiLevel } ?: emptyList())
    this.orientation.addAll(deviceCatalog.runtimeConfiguration?.orientations?.mapNotNull { it.id } ?: emptyList())
    deviceCatalog.runtimeConfiguration?.locales?.mapNotNull { locale ->
      if (locale.id != null && locale.id != "") {
        this.locale[locale.id] = LocaleInfo(languageName = locale.name ?: "", region = locale.region ?: "")
      }
    }
    checkEmptyFields()
    return this
  }
}