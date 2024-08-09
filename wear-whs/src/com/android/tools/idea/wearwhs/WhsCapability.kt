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

import java.text.DecimalFormat
import kotlin.reflect.KClass
import org.jetbrains.annotations.PropertyKey

/** A value of a [type]. */
sealed class WhsDataValue(val type: WhsDataType) {
  abstract fun asText(): String

  /** Represents the case where a [type] has no value or has not been assigned one. */
  class NoValue(type: WhsDataType) : WhsDataValue(type) {
    override fun asText(): String = ""

    override fun equals(other: Any?): Boolean {
      return other is NoValue && type == other.type
    }

    override fun hashCode(): Int {
      return javaClass.hashCode()
    }
  }

  /** Subclass of [WhsDataValue] that represents a [type] containing a value. */
  sealed class Value<T>(type: WhsDataType, val value: T) : WhsDataValue(type) {
    override fun equals(other: Any?): Boolean = other is Value<*> && value == other.value

    override fun hashCode(): Int = value.hashCode()
  }

  class IntValue(type: WhsDataType, value: Int) : Value<Int>(type, value) {
    override fun asText(): String = value.toString()
  }

  class FloatValue(type: WhsDataType, value: Float) : Value<Float>(type, value) {
    private val asTextValue = DecimalFormat("0.##").format(value)

    override fun asText(): String = asTextValue
  }
}

enum class WhsDataType(val overrideDataType: KClass<*>) {
  DATA_TYPE_UNKNOWN(WhsDataValue.NoValue::class),
  STEPS(WhsDataValue.IntValue::class),
  DISTANCE(WhsDataValue.FloatValue::class),
  CALORIES(WhsDataValue.FloatValue::class),
  FLOORS(WhsDataValue.FloatValue::class),
  ELEVATION_GAIN(WhsDataValue.FloatValue::class),
  ELEVATION_LOSS(WhsDataValue.FloatValue::class),
  ABSOLUTE_ELEVATION(WhsDataValue.FloatValue::class),
  LOCATION(WhsDataValue.NoValue::class),
  HEART_RATE_BPM(WhsDataValue.FloatValue::class),
  SPEED(WhsDataValue.FloatValue::class),
  PACE(WhsDataValue.FloatValue::class),
  STEPS_PER_MINUTE(WhsDataValue.FloatValue::class);

  /** Returns a [WhsDataValue] with the [value] for the type of this [WhsDataValue]. */
  fun value(value: Number): WhsDataValue =
    when (overrideDataType) {
      WhsDataValue.NoValue::class -> WhsDataValue.NoValue(this)
      WhsDataValue.IntValue::class -> WhsDataValue.IntValue(this, value.toInt())
      WhsDataValue.FloatValue::class -> WhsDataValue.FloatValue(this, value.toFloat())
      else -> throw UnsupportedOperationException()
    }

  /** Returns a [WhsDataValue] with no value for this type. */
  fun noValue(): WhsDataValue = WhsDataValue.NoValue(this)

  /**
   * Returns a [WhsDataValue] with the [value] for the type of this [WhsDataValue]. This method
   * might throw [NumberFormatException] if the given [value] is not valid number for this type.
   */
  fun valueFromString(value: String): WhsDataValue =
    when (overrideDataType) {
      WhsDataValue.NoValue::class -> WhsDataValue.NoValue(this)
      WhsDataValue.IntValue::class -> WhsDataValue.IntValue(this, value.toInt())
      WhsDataValue.FloatValue::class -> WhsDataValue.FloatValue(this, value.toFloat())
      else -> throw UnsupportedOperationException()
    }
}

/**
 * Data class representing WHS capabilities such as Heart Rate, Location. [dataType] corresponds to
 * an enum for capabilities in WHS, [label] is the user displayed label of the capability,
 * [isOverrideable] means the value of the sensor can be changed via adb commands, and [unit]
 * specifies the unit of the sensor.
 */
data class WhsCapability(
  val dataType: WhsDataType,
  @PropertyKey(resourceBundle = BUNDLE_NAME) val label: String,
  @PropertyKey(resourceBundle = BUNDLE_NAME) val unit: String,
  val isOverrideable: Boolean,
  val isStandardCapability: Boolean,
)

val heartRateBpmCapability =
  WhsCapability(
    WhsDataType.HEART_RATE_BPM,
    "wear.whs.capability.heart.rate.label",
    "wear.whs.capability.heart.rate.unit",
    isOverrideable = true,
    isStandardCapability = true,
  )

val locationCapability =
  WhsCapability(
    WhsDataType.LOCATION,
    "wear.whs.capability.location.label",
    "wear.whs.capability.unit.none",
    isOverrideable = false,
    isStandardCapability = true,
  )

val stepsCapability =
  WhsCapability(
    WhsDataType.STEPS,
    "wear.whs.capability.steps.label",
    "wear.whs.capability.steps.unit",
    isOverrideable = true,
    isStandardCapability = true,
  )

val distanceCapability =
  WhsCapability(
    WhsDataType.DISTANCE,
    "wear.whs.capability.distance.label",
    "wear.whs.capability.distance.unit",
    isOverrideable = true,
    isStandardCapability = true,
  )

val speedCapability =
  WhsCapability(
    WhsDataType.SPEED,
    "wear.whs.capability.speed.label",
    "wear.whs.capability.speed.unit",
    isOverrideable = true,
    isStandardCapability = true,
  )

val elevationGainCapability =
  WhsCapability(
    WhsDataType.ELEVATION_GAIN,
    "wear.whs.capability.elevation.gain.label",
    "wear.whs.capability.elevation.gain.unit",
    isOverrideable = true,
    isStandardCapability = false,
  )

val elevationLossCapability =
  WhsCapability(
    WhsDataType.ELEVATION_LOSS,
    "wear.whs.capability.elevation.loss.label",
    "wear.whs.capability.elevation.loss.unit",
    isOverrideable = true,
    isStandardCapability = false,
  )

val caloriesCapability =
  WhsCapability(
    WhsDataType.CALORIES,
    "wear.whs.capability.total.calories.label",
    "wear.whs.capability.total.calories.unit",
    isOverrideable = true,
    isStandardCapability = false,
  )

val absoluteElevationCapability =
  WhsCapability(
    WhsDataType.ABSOLUTE_ELEVATION,
    "wear.whs.capability.absolute.elevation.label",
    "wear.whs.capability.absolute.elevation.unit",
    isOverrideable = true,
    isStandardCapability = false,
  )

val paceCapability =
  WhsCapability(
    WhsDataType.PACE,
    "wear.whs.capability.pace.label",
    "wear.whs.capability.pace.unit",
    isOverrideable = true,
    isStandardCapability = false,
  )

val floorsCapability =
  WhsCapability(
    WhsDataType.FLOORS,
    "wear.whs.capability.floors.label",
    "wear.whs.capability.floors.unit",
    isOverrideable = true,
    isStandardCapability = false,
  )

val stepsPerMinuteCapability =
  WhsCapability(
    WhsDataType.STEPS_PER_MINUTE,
    "wear.whs.capability.steps.per.minute.label",
    "wear.whs.capability.steps.per.minute.unit",
    isOverrideable = true,
    isStandardCapability = true,
  )

/** Ordered list of all capabilities as displayed in the WHS panel. */
val WHS_CAPABILITIES =
  listOf(
    heartRateBpmCapability,
    locationCapability,
    stepsCapability,
    distanceCapability,
    speedCapability,
    elevationGainCapability,
    elevationLossCapability,
    caloriesCapability,
    absoluteElevationCapability,
    paceCapability,
    floorsCapability,
    stepsPerMinuteCapability,
  )
