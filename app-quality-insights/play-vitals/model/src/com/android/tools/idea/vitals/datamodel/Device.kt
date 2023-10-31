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
  return Device(
    manufacturer = proto.deviceId.buildBrand, // e.g. xiaomi
    model = proto.deviceId.buildDevice, // e.g. cereus
    displayName =
      proto
        .marketingName // TODO: it seems I don't see any value here, not sure if it's still WIP on
    // the server side.
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
        deviceModel = (it.value as DimensionValue.StringValue).value
        displayName = it.displayValue
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
    deviceType = DeviceType(deviceType)
  )
}
