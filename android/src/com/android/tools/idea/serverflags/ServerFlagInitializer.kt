/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.serverflags

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.CommonMetricsData
import com.android.tools.idea.analytics.currentIdeBrand
import com.android.tools.idea.serverflags.protos.Brand
import com.android.tools.idea.serverflags.protos.OSType
import com.android.tools.idea.serverflags.protos.ServerFlag
import com.android.tools.idea.serverflags.protos.ServerFlagData
import com.google.common.hash.Hashing
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Path
import kotlin.math.abs

private const val ENABLED_OVERRIDE_KEY = "studio.server.flags.enabled.override"

/**
 * ServerFlagInitializer initializes the ServerFlagService.instance field.
 * It will try to download the protobuf file for the current version of Android Studio
 * from the specified URL. If it succeeds it will save the file to a local path, then initialize the service.
 * If the download fails, it will use the last successful download to initialize the service
 */

data class ServerFlagInitializationData(val configurationVersion: Long, val flags: Map<String, ServerFlag>)
class ServerFlagInitializer {
  companion object {
    @JvmStatic
    fun initializeService(): ServerFlagInitializationData {
      val experiments = System.getProperty(ENABLED_OVERRIDE_KEY)?.split(',') ?: emptyList()
      val data = initializeService(localCacheDirectory, flagsVersion, CommonMetricsData.osName, currentIdeBrand(), experiments)

      val logger = Logger.getInstance(ServerFlagInitializer::class.java)
      val string = data.flags.keys.toList().joinToString()
      logger.info("Enabled server flags: $string")

      return data
    }

    /**
     * Initialize the server flag service
     * @param localCacheDirectory: The local directory to store the most recent download.
     * @param version: The current version of Android Studio. This is used to construct the full paths from the first two parameters.
     * @param enabled: An optional set of experiment names to be enabled. If empty, the percentEnabled field will determine whether
     * a given flag is enabled.
     */
    @JvmStatic
    fun initializeService(localCacheDirectory: Path,
                          version: String,
                          osName: String,
                          ideBrand: AndroidStudioEvent.IdeBrand,
                          enabled: Collection<String>): ServerFlagInitializationData {
      val localFilePath = buildLocalFilePath(localCacheDirectory, version)
      val serverFlagList = unmarshalFlagList(localFilePath.toFile())
      val configurationVersion = serverFlagList?.configurationVersion ?: -1
      val list = serverFlagList?.serverFlagsList ?: emptyList()
      val osType = getOsType(osName)
      val brand = getBrand(ideBrand)

      val filter = if (enabled.isEmpty()) {
        { flag: ServerFlagData -> flag.isEnabled(osType, brand) }
      }
      else {
        { flag: ServerFlagData -> enabled.contains(flag.name) }
      }

      val flags = list.filter(filter)

      val map = flags.associate { it.name to it.serverFlag }
      return ServerFlagInitializationData(configurationVersion, map)
    }
  }
}

private fun ServerFlagData.isEnabled(osType: OSType, brand: Brand): Boolean {
  if (!this.isOSEnabled(osType) || !this.isBrandEnabled(brand)) {
    return false
  }

  val key = AnalyticsSettings.userId + this.name
  val hash = Hashing.farmHashFingerprint64().hashString(key, Charsets.UTF_8)
  return (abs(hash.asLong()) % 100).toInt() < this.serverFlag.percentEnabled
}

private fun ServerFlagData.isOSEnabled(osType: OSType): Boolean {
  return (this.serverFlag.osTypeCount == 0 || this.serverFlag.osTypeList.contains(osType))
}

private fun ServerFlagData.isBrandEnabled(brand: Brand): Boolean {
  return (this.serverFlag.brandCount == 0 || this.serverFlag.brandList.contains(brand))
}

private fun getOsType(osName: String): OSType {
  if (osName.startsWith(CommonMetricsData.OS_NAME_FREE_BSD)) {
    return OSType.OS_TYPE_FREE_BSD
  }

  return when (osName) {
    CommonMetricsData.OS_NAME_LINUX -> OSType.OS_TYPE_LINUX
    CommonMetricsData.OS_NAME_CHROMIUM -> OSType.OS_TYPE_CHROMIUM
    CommonMetricsData.OS_NAME_WINDOWS -> OSType.OS_TYPE_WIN
    CommonMetricsData.OS_NAME_MAC -> OSType.OS_TYPE_MAC
    else -> OSType.OS_TYPE_UNKNOWN
  }
}

private fun getBrand(brand: AndroidStudioEvent.IdeBrand) : Brand {
  return when (brand) {
    AndroidStudioEvent.IdeBrand.ANDROID_STUDIO -> Brand.BRAND_ANDROID_STUDIO
    AndroidStudioEvent.IdeBrand.ANDROID_STUDIO_WITH_BLAZE -> Brand.BRAND_ANDROID_STUDIO_WITH_BLAZE
    else -> Brand.BRAND_UNKNOWN
  }
}

