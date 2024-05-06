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

/**
 * Data class representing WHS capabilities such as Heart Rate, Location. [labelKey] is the user
 * displayed label of the capability, [isOverrideable] means the value of the sensor can be
 * changed via adb commands, and [unitKey] specifies the unit of the sensor.
 */
data class WhsCapability(@PropertyKey(resourceBundle = BUNDLE_NAME) val labelKey: String,
                         @PropertyKey(resourceBundle = BUNDLE_NAME) val unitKey: String,
                         val isOverrideable: Boolean)

/**
 * Ordered list of all capabilities as displayed in the WHS panel.
 */
val WHS_CAPABILITIES = listOf(
  WhsCapability(
    "wear.whs.capability.heart.rate.label",
    "wear.whs.capability.heart.rate.unit",
    true,
  ),
  WhsCapability(
    "wear.whs.capability.location.label",
    "wear.whs.capability.unit.none",
    false,
  ),
  WhsCapability(
    "wear.whs.capability.steps.label",
    "wear.whs.capability.steps.unit",
    true,
  ),
  WhsCapability(
    "wear.whs.capability.distance.label",
    "wear.whs.capability.distance.unit",
    true,
  ),
  WhsCapability(
    "wear.whs.capability.speed.label",
    "wear.whs.capability.speed.unit",
    true,
  ),
  WhsCapability(
    "wear.whs.capability.duration.label",
    "wear.whs.capability.duration.unit",
    true,
  ),
  WhsCapability(
    "wear.whs.capability.elevation.gain.label",
    "wear.whs.capability.elevation.gain.unit",
    true,
  ),
  WhsCapability(
    "wear.whs.capability.total.calories.label",
    "wear.whs.capability.total.calories.unit",
    true,
  ),
  WhsCapability(
    "wear.whs.capability.absolute.elevation.label",
    "wear.whs.capability.unit.none",
    false,
  ),
)
