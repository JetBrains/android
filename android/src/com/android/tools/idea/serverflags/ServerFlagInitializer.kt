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
import com.android.tools.idea.serverflags.protos.Brand
import com.android.tools.idea.serverflags.protos.FlagValue
import com.android.tools.idea.serverflags.protos.OSType
import com.android.tools.idea.serverflags.protos.ServerFlag
import com.android.tools.idea.serverflags.protos.ServerFlagData
import com.android.utils.associateNotNull
import com.google.common.hash.Hashing
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Path
import kotlin.math.abs

/**
 * ServerFlagInitializer initializes the ServerFlagService.instance field. It will try to download
 * the protobuf file for the current version of Android Studio from the specified URL. If it
 * succeeds it will save the file to a local path, then initialize the service. If the download
 * fails, it will use the last successful download to initialize the service
 */
data class ServerFlagInitializationData(
  val configurationVersion: Long,
  val flags: Map<String, ServerFlagValueData>,
)

data class ServerFlagValueData(val index: Int, val value: FlagValue)

class ServerFlagInitializer {

  companion object {
    /**
     * Initialize the server flag service
     *
     * @param localCacheDirectory The local directory to store the most recent download.
     * @param version The current version of Android Studio. This is used to construct the full
     *   paths from the first two parameters.
     * @param overriddenFlags A map of flags and the index of value. For flags that have a single
     *   value, the value of the entry is ignored.
     * @param useMultiValueFlag if set to true, ServerFlagInitializer will start supporting multi
     *   value flags.
     * @param hashOverride mainly used for testing - overrides the default hashing function.
     */
    fun initializeService(
      localCacheDirectory: Path,
      version: String,
      osName: String,
      ideBrand: AndroidStudioEvent.IdeBrand,
      overriddenFlags: Map<String, Int>,
      useMultiValueFlag: Boolean,
      hashOverride: (String) -> Int = ::hash,
    ): ServerFlagInitializationData {
      val localFilePath = buildLocalFilePath(localCacheDirectory, version)
      val serverFlagList = unmarshalFlagList(localFilePath.toFile())
      val configurationVersion = serverFlagList?.configurationVersion ?: -1
      val list =
        serverFlagList?.serverFlagsList
          ?: return ServerFlagInitializationData(configurationVersion, emptyMap())
      val osType = getOsType(osName)
      val brand = getBrand(ideBrand)
      val values =
        if (overriddenFlags.isEmpty()) {
          list
            .filter { it.isEnabled(osType, brand) }
            .associateNotNull { serverFlagData ->
              serverFlagData.getEnabledValue(useMultiValueFlag, hashOverride)?.let { flagValue ->
                serverFlagData.name to flagValue
              }
            }
        } else {
          list.getOverriddenFlags(overriddenFlags, useMultiValueFlag)
        }

      val logger = Logger.getInstance(ServerFlagInitializer::class.java)
      val string = values.keys.toList().joinToString()
      logger.info("Enabled server flags: $string")

      return ServerFlagInitializationData(configurationVersion, values)
    }
  }
}

private fun List<ServerFlagData>.getOverriddenFlags(
  overriddenFlags: Map<String, Int>,
  useMultiValueFlag: Boolean,
) =
  filter { overriddenFlags.containsKey(it.name) }
    .associateNotNull {
      if (useMultiValueFlag) {
        val flagValueIndex = overriddenFlags[it.name]!!
        if (it.hasMultiValueServerFlag()) {
          val flagValue =
            try {
              it.multiValueServerFlag.flagValuesList[flagValueIndex]
            } catch (_: IndexOutOfBoundsException) {
              Logger.getInstance("ServerFlagInitializer")
                .warn("Index $flagValueIndex is out of bounds for flag ${it.name}")
              return@associateNotNull null
            }
          it.name to ServerFlagValueData(flagValueIndex, flagValue)
        } else {
          Logger.getInstance("ServerFlagInitializer")
            .warn("Expected MultiValueServerFlag to be set for overridden flag ${it.name}")
          null
        }
      } else {
        if (it.hasServerFlag()) {
          it.name to ServerFlagValueData(0, it.serverFlag.toSingleFlagValue())
        } else {
          Logger.getInstance("ServerFlagInitializer")
            .warn("Expected ServerFlag to be set for overridden flag ${it.name}")
          null
        }
      }
    }

private fun ServerFlagData.isEnabled(osType: OSType, brand: Brand): Boolean {
  return isOSEnabled(osType) && isBrandEnabled(brand)
}

/**
 * Returns the enabled flag value for a particular server flag. Null if none match.
 *
 * This is compatible with the old way of declaring server flag value directly in the ServerFlag
 * proto message (as opposed to FlagValue).
 */
private fun ServerFlagData.getEnabledValue(
  useMultiValueFlag: Boolean,
  hashFunction: (String) -> Int,
): ServerFlagValueData? {
  val key = AnalyticsSettings.userId + name
  val hash = hashFunction(key)

  if (useMultiValueFlag) {
    if (!hasMultiValueServerFlag()) {
      Logger.getInstance("ServerFlagInitializer")
        .warn("Server flag $name does not have MultiValueServerFlag field set.")
      return null
    }
    if (!areAllValuesTheSameType(multiValueServerFlag.flagValuesList)) {
      Logger.getInstance("ServerFlagInitializer")
        .warn("Server flag $name have flag values of different types.")
      return null
    }
    var acc = 0
    for (indexedValue in multiValueServerFlag.flagValuesList.withIndex()) {
      if (acc <= hash && hash < acc + indexedValue.value.percentEnabled) {
        return ServerFlagValueData(indexedValue.index, indexedValue.value)
      }
      acc += indexedValue.value.percentEnabled
    }
    return null
  } else {
    if (!hasServerFlag()) {
      Logger.getInstance("ServerFlagInitializer")
        .warn("Server flag $name does not have ServerFlag field set.")
      return null
    }
    if (hash < serverFlag.percentEnabled) {
      return ServerFlagValueData(0, serverFlag.toSingleFlagValue())
    }
    return null
  }
}

private fun areAllValuesTheSameType(flags: List<FlagValue>): Boolean {
  val firstValue = flags.firstOrNull() ?: return true
  return flags.all { it.valuesCase == firstValue.valuesCase }
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

private fun getBrand(brand: AndroidStudioEvent.IdeBrand): Brand {
  return when (brand) {
    AndroidStudioEvent.IdeBrand.ANDROID_STUDIO -> Brand.BRAND_ANDROID_STUDIO
    AndroidStudioEvent.IdeBrand.ANDROID_STUDIO_WITH_BLAZE -> Brand.BRAND_ANDROID_STUDIO_WITH_BLAZE
    else -> Brand.BRAND_UNKNOWN
  }
}

private fun hash(key: String): Int {
  val hash = Hashing.farmHashFingerprint64().hashString(key, Charsets.UTF_8)
  return (abs(hash.asLong()) % 100).toInt()
}

/** Converts a [ServerFlag] instance to [FlagValue]. */
fun ServerFlag.toSingleFlagValue(): FlagValue =
  FlagValue.newBuilder()
    .apply {
      percentEnabled = this@toSingleFlagValue.percentEnabled
      when (this@toSingleFlagValue.valuesCase) {
        ServerFlag.ValuesCase.INT_VALUE -> intValue = this@toSingleFlagValue.intValue
        ServerFlag.ValuesCase.FLOAT_VALUE -> floatValue = this@toSingleFlagValue.floatValue
        ServerFlag.ValuesCase.STRING_VALUE -> stringValue = this@toSingleFlagValue.stringValue
        ServerFlag.ValuesCase.BOOLEAN_VALUE -> booleanValue = this@toSingleFlagValue.booleanValue
        ServerFlag.ValuesCase.PROTO_VALUE -> protoValue = this@toSingleFlagValue.protoValue
        else -> {
          Logger.getInstance(this::class.java)
            .warn("Unexpected server flag value type: ${this@toSingleFlagValue.valuesCase}")
        }
      }
    }
    .build()
