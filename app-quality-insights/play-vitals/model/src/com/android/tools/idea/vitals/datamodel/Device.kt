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

import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.DeviceType
import com.google.play.developer.reporting.DeviceModelSummary

fun Device.Companion.fromProto(proto: DeviceModelSummary): Device {
  val brand = proto.deviceId.buildBrand
  val model = proto.deviceId.buildDevice
  return Device(
    manufacturer = brand, // e.g. xiaomi
    model = model, // e.g. cereus
    displayName = createFullDisplayName(brand, model, proto.marketingName),
  )
}

fun Device.Companion.fromDimensions(dimensions: List<Dimension>): Device {
  var deviceModel = ""
  var displayName = ""
  var manufacturer = ""
  var deviceType = ""

  dimensions.map {
    when (it.type) {
      DimensionType.DEVICE_BRAND -> {
        manufacturer = (it.value as DimensionValue.StringValue).value
      }
      DimensionType.DEVICE_MODEL -> {
        deviceModel = extractDeviceModel((it.value as DimensionValue.StringValue).value)
        displayName = extractMarketingName(manufacturer, deviceModel, it.displayValue)
      }
      DimensionType.DEVICE_TYPE -> {
        deviceType = it.displayValue
      }
      else -> Unit
    }
  }

  return Device(
    manufacturer = manufacturer,
    model = deviceModel,
    displayName = displayName,
    deviceType = DeviceType(deviceType),
  )
}

private fun extractDeviceModel(value: String) = value.split('/').getOrElse(1) { value }

private fun extractMarketingName(manufacturer: String, model: String, value: String): String {
  val regex = Regex("^$manufacturer $model \\((.*)\\)$")
  val result = regex.matchEntire(value)
  return result?.groupValues?.getOrNull(1) ?: value
}

/**
 * Constructs the full display name of a device using the manufacturer and marketing name.
 *
 * For cases where the marketing name's first word is the manufacturer name, we omit the
 * manufacturer in the final output and use only the marketing name.
 */
private fun createFullDisplayName(manufacturer: String, model: String, value: String): String {
  val marketingName = extractMarketingName(manufacturer, model, value)
  return if (marketingName.startsWith(manufacturer, true)) {
    marketingName
  } else {
    "$manufacturer $marketingName"
  }
}
