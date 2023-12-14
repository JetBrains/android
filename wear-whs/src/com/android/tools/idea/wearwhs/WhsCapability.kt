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
package com.android.tools.idea.wearwhs

import org.jetbrains.annotations.PropertyKey

enum class WhsDataType() {
  DATA_TYPE_UNKNOWN,
  STEPS,
  DISTANCE,
  TOTAL_CALORIES,
  ELEVATION_GAIN,
  ABSOLUTE_ELEVATION,
  LOCATION,
  HEART_RATE_BPM,
  SPEED,
}

/**
 * Data class representing WHS capabilities such as Heart Rate, Location. [key] is the
 * key for this data type in WHS, [label] is the user displayed label of the capability,
 * [isOverrideable] means the value of the sensor can be changed via adb commands, and
 * [unit] specifies the unit of the sensor.
 */
data class WhsCapability(val key: WhsDataType,
                         @PropertyKey(resourceBundle = BUNDLE_NAME) val label: String,
                         @PropertyKey(resourceBundle = BUNDLE_NAME) val unit: String,
                         val isOverrideable: Boolean,
                         val isStandardCapability: Boolean)

/**
 * Ordered list of all capabilities as displayed in the WHS panel.
 */
val WHS_CAPABILITIES = listOf(
  WhsCapability(
    WhsDataType.HEART_RATE_BPM,
    "wear.whs.capability.heart.rate.label",
    "wear.whs.capability.heart.rate.unit",
    isOverrideable = true,
    isStandardCapability = true,
  ),
  WhsCapability(
    WhsDataType.LOCATION,
    "wear.whs.capability.location.label",
    "wear.whs.capability.unit.none",
    isOverrideable = false,
    isStandardCapability = true,
  ),
  WhsCapability(
    WhsDataType.STEPS,
    "wear.whs.capability.steps.label",
    "wear.whs.capability.steps.unit",
    isOverrideable = true,
    isStandardCapability = true,
  ),
  WhsCapability(
    WhsDataType.DISTANCE,
    "wear.whs.capability.distance.label",
    "wear.whs.capability.distance.unit",
    isOverrideable = true,
    isStandardCapability = true,
  ),
  WhsCapability(
    WhsDataType.SPEED,
    "wear.whs.capability.speed.label",
    "wear.whs.capability.speed.unit",
    isOverrideable = true,
    isStandardCapability = true,
  ),
  WhsCapability(
    WhsDataType.ELEVATION_GAIN,
    "wear.whs.capability.elevation.gain.label",
    "wear.whs.capability.elevation.gain.unit",
    isOverrideable = true,
    isStandardCapability = false,
  ),
  WhsCapability(
    WhsDataType.TOTAL_CALORIES,
    "wear.whs.capability.total.calories.label",
    "wear.whs.capability.total.calories.unit",
    isOverrideable = true,
    isStandardCapability = false,
  ),
  WhsCapability(
    WhsDataType.ABSOLUTE_ELEVATION,
    "wear.whs.capability.absolute.elevation.label",
    "wear.whs.capability.unit.none",
    isOverrideable = false,
    isStandardCapability = false,
  ),
)
