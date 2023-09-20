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
package com.android.tools.idea.vitals.datamodel

data class Dimension(val type: DimensionType, val value: DimensionValue, val displayValue: String) {
  companion object {
    fun fromProto(proto: com.google.play.developer.reporting.DimensionValue): Dimension {
      val dimensionType = proto.dimension.toEnumDimensionType()

      // TODO: it's a bit too much to do the following, but I'm not sure if just
      //  querying "value label" is good enough.
      val dimensionValue =
        when {
          proto.hasInt64Value() -> DimensionValue.LongValue(proto.int64Value)
          proto.hasStringValue() -> DimensionValue.StringValue(proto.stringValue)
          else -> throw IllegalStateException("$proto is neither or long nor string type.")
        }

      return Dimension(
        type = dimensionType,
        value = dimensionValue,
        displayValue = proto.valueLabel
      )
    }
  }
}

enum class DimensionType(val value: String) {
  // **Supported dimensions:**
  //
  //  * `apiLevel`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     the API level of Android that was running on the user's device.
  API_LEVEL("apiLevel"),

  //  * `versionCode`
  //     ([int64][google.play.developer.reporting.{$api_version}.DimensionValue.int64_value]):
  //     version of the app that was running on the user's device.
  VERSION_CODE("versionCode"),

  //  * `deviceModel`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     unique identifier of the user's device model.
  DEVICE_MODEL("deviceModel"),

  //  * `deviceBrand`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     unique identifier of the user's device brand.
  DEVICE_BRAND("deviceBrand"),

  //  * `deviceType`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     identifier of the device's form factor, e.g., PHONE.
  DEVICE_TYPE("deviceType"),

  //  * `reportType`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     the type of error. The value should correspond to one of the possible
  //     values in
  //     [ErrorType][google.play.developer.reporting.{$api_version}.ErrorType].
  REPORT_TYPE("reportType"),

  //  * `issueId`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     the id an error was assigned to. The value should correspond to the
  //     `{issue}` component of the [issue
  //     name][google.play.developer.reporting.{$api_version}.ErrorIssue.name].
  ISSUE_ID("issueId"),

  //  * `deviceRamBucket`
  //     ([int64][google.play.developer.reporting.{$api_version}.DimensionValue.int64_value]):
  //     RAM of the device, in MB, in buckets (3GB, 4GB, etc.).
  DEVICE_RAM_BUCKET("deviceRamBucket"),

  //  *  `deviceSocMake`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     Make of the device's primary system-on-chip, e.g., Samsung.
  //     [Reference](https://developer.android.com/reference/android/os/Build#SOC_MANUFACTURER)
  DEVICE_SOC_MAKE("deviceSocMake"),

  //  *  `deviceSocModel`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     Model of the device's primary system-on-chip, e.g., "Exynos 2100".
  //     [Reference](https://developer.android.com/reference/android/os/Build#SOC_MODEL)
  DEVICE_SOC_MODEL("deviceSocModel"),

  //  *  `deviceCpuMake`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     Make of the device's CPU, e.g., Qualcomm.
  DEVICE_CPU_MAKE("deviceCpuMake"),

  //  *  `deviceCpuModel`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     Model of the device's CPU, e.g., "Kryo 240".
  DEVICE_CPU_MODEL("deviceCpuModel"),

  //  *  `deviceGpuMake`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     Make of the device's GPU, e.g., ARM.
  DEVICE_GPU_MAKE("deviceGpuMake"),

  //  *  `deviceGpuModel`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     Model of the device's GPU, e.g., Mali.
  DEVICE_GPU_MODEL("deviceGpuModel"),

  //  *  `deviceGpuVersion`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     Version of the device's GPU, e.g., T750.
  DEVICE_GPU_VERSION("deviceGpuVersion"),

  //  *  `deviceVulkanVersion`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     Vulkan version of the device, e.g., "4198400".
  DEVICE_VULKAN_VERSION("deviceVulkanVersion"),

  //  *  `deviceGlEsVersion`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     OpenGL ES version of the device, e.g., "196610".
  DEVICE_GL_ES_VERSION("deviceGlEsVersion"),

  //  *  `deviceScreenSize`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     Screen size of the device, e.g., NORMAL, LARGE.
  DEVICE_SCREEN_SIZE("deviceScreenSize"),

  //  *  `deviceScreenDpi`
  //     ([string][google.play.developer.reporting.{$api_version}.DimensionValue.string_value]):
  //     Screen density of the device, e.g., mdpi, hdpi.
  DEVICE_SCREEN_DPI("deviceScreenDpi")
}

fun String.toEnumDimensionType(): DimensionType {
  return DimensionType.values().firstOrNull { it.value == this }
    ?: throw IllegalStateException("$this is not of a recognizable dimension type.")
}

sealed class DimensionValue {
  data class StringValue(val value: String) : DimensionValue()

  data class LongValue(val value: Long) : DimensionValue()
}
