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

import com.android.tools.idea.insights.OperatingSystemInfo
import com.google.play.developer.reporting.OsVersion

fun OperatingSystemInfo.Companion.fromProto(proto: OsVersion): OperatingSystemInfo {
  return OperatingSystemInfo(
    displayVersion = proto.apiLevel.toString(),
    displayName = proto.apiLevel.toString(),
  )
}

fun OperatingSystemInfo.Companion.fromDimensions(dimensions: List<Dimension>): OperatingSystemInfo {
  return dimensions
    .filter { it.type == DimensionType.API_LEVEL }
    .map {
      // E.g.
      // "stringValue": "31",
      // "valueLabel": "Android 12"
      val os = (it.value as DimensionValue.StringValue).value
      val displayName = it.displayValue

      OperatingSystemInfo(displayVersion = os, displayName = displayName)
    }
    .single()
}
