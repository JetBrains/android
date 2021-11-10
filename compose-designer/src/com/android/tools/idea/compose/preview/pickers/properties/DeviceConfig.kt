/*
 * Copyright (C) 2021 The Android Open Source Project
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
@file:Suppress("EnumEntryName")

package com.android.tools.idea.compose.preview.pickers.properties

import com.android.resources.Density
import com.android.tools.idea.compose.preview.pickers.properties.utils.DEVICE_BY_SPEC_PREFIX
import com.android.tools.idea.compose.preview.pickers.tracking.PickerTrackableValue
import com.android.tools.idea.compose.preview.util.enumValueOfOrNull
import com.android.utils.HashCodes
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import kotlin.math.roundToInt

internal const val DEFAULT_WIDTH = 1080
internal const val DEFAULT_HEIGHT = 1920
internal val DEFAULT_DENSITY = Density.XXHIGH
internal val DEFAULT_UNIT = DimUnit.px
internal val DEFAULT_SHAPE = Shape.Normal

private const val PARAM_SHAPE = "shape"
private const val PARAM_WIDTH = "width"
private const val PARAM_HEIGHT = "height"
private const val PARAM_UNIT = "unit"
private const val PARAM_DENSITY = "dpi"

private const val PARAM_VALUE_OPERATOR = '='
private const val PARAM_SEPARATOR = ','
private const val PARAM_LEGACY_SEPARATOR = ';'

private const val DENSITY_SUFFIX = "dpi"
private const val WIDTH_SUFFIX = "w"
private const val HEIGHT_SUFFIX = "h"

/**
 * Defines some hardware parameters of a Device. Can be encoded using [deviceSpec] and decoded using [DeviceConfig.toDeviceConfigOrNull].
 *
 * @param dimUnit Determines the unit of the given [width] and [height]. Ie: For [DimUnit.px] they will be considered as pixels.
 * @param shape Shape of the device screen, may affect how the screen behaves, or it may add a cutout (like with wearables)
 */
internal open class DeviceConfig(
  open val width: Int = DEFAULT_WIDTH,
  open val height: Int = DEFAULT_HEIGHT,
  open val dimUnit: DimUnit = DEFAULT_UNIT,
  open val dpi: Int = DEFAULT_DENSITY.dpiValue,
  open val shape: Shape = DEFAULT_SHAPE
) {
  open val orientation: Orientation
    get() = if (height >= width) Orientation.portrait else Orientation.landscape

  /** Returns a string that defines the Device in the current state of [DeviceConfig] */
  fun deviceSpec(): String {
    val builder = StringBuilder(DEVICE_BY_SPEC_PREFIX)
    builder.appendParamValue(PARAM_SHAPE, shape.name)
    builder.appendSeparator()
    builder.appendParamValue(PARAM_WIDTH, width.toString())
    builder.appendSeparator()
    builder.appendParamValue(PARAM_HEIGHT, height.toString())
    builder.appendSeparator()
    builder.appendParamValue(PARAM_UNIT, dimUnit.name)
    builder.appendSeparator()
    builder.appendParamValue(PARAM_DENSITY, dpi.toString())
    return builder.toString()
  }

  override fun equals(other: Any?): Boolean {
    if (other !is DeviceConfig) {
      return false
    }
    return deviceSpec() == other.deviceSpec()
  }

  override fun hashCode(): Int {
    return HashCodes.mix(width, height, dpi, shape.hashCode(), dimUnit.hashCode())
  }

  companion object {
    fun toDeviceConfigOrNull(serialized: String?): DeviceConfig? {
      if (serialized == null || !serialized.startsWith(DEVICE_BY_SPEC_PREFIX)) return null
      val configString = serialized.substringAfter(DEVICE_BY_SPEC_PREFIX)
      val paramsMap = configString.split(PARAM_SEPARATOR).filter {
        it.length >= 3 && it.contains(PARAM_VALUE_OPERATOR)
      }.associate { paramString ->
        Pair(paramString.substringBefore(PARAM_VALUE_OPERATOR).trim(), paramString.substringAfter(PARAM_VALUE_OPERATOR).trim())
      }

      if (paramsMap.size != 5) {
        // Try to parse with old method, otherwise, continue
        legacyParseToDeviceConfig(serialized)?.let { return it }
      }

      val shape = enumValueOfOrNull<Shape>(paramsMap.getOrDefault(PARAM_SHAPE, "")) ?: return null
      val width = paramsMap.getOrDefault(PARAM_WIDTH, "").toIntOrNull() ?: return null
      val height = paramsMap.getOrDefault(PARAM_HEIGHT, "").toIntOrNull() ?: return null
      val dimUnit = enumValueOfOrNull<DimUnit>(paramsMap.getOrDefault(PARAM_UNIT, "").toLowerCaseAsciiOnly()) ?: return null
      val dpi = paramsMap.getOrDefault(PARAM_DENSITY, "").toIntOrNull() ?: return null
      return DeviceConfig(width = width, height = height, dimUnit = dimUnit, dpi = dpi, shape = shape)
    }

    fun toMutableDeviceConfigOrNull(serialized: String?): MutableDeviceConfig? {
      return toDeviceConfigOrNull(serialized)?.toMutableConfig()
    }

    private fun legacyParseToDeviceConfig(serialized: String?): DeviceConfig? {
      if (serialized == null || !serialized.startsWith(DEVICE_BY_SPEC_PREFIX)) return null
      val configString = serialized.substringAfter(DEVICE_BY_SPEC_PREFIX)
      val params = configString.split(PARAM_LEGACY_SEPARATOR)
      if (params.size != 5) return null

      val shape = enumValueOfOrNull<Shape>(params[0]) ?: return null
      val width = params[1].substringBefore(WIDTH_SUFFIX).toIntOrNull() ?: return null
      val height = params[2].substringBefore(HEIGHT_SUFFIX).toIntOrNull() ?: return null
      val dimUnit = enumValueOfOrNull<DimUnit>(params[3].toLowerCaseAsciiOnly()) ?: return null
      val dpi = params[4].substringBefore(DENSITY_SUFFIX).toIntOrNull() ?: return null
      return DeviceConfig(width = width, height = height, dimUnit = dimUnit, dpi = dpi, shape = shape)
    }
  }
}

/**
 * Mutable equivalent of [DeviceConfig].
 *
 * Note that modifying [MutableDeviceConfig.dimUnit] or [MutableDeviceConfig.orientation] will also change the width and height values.
 */
internal class MutableDeviceConfig(
  initialWidth: Int = DEFAULT_WIDTH,
  initialHeight: Int = DEFAULT_HEIGHT,
  initialDimUnit: DimUnit = DEFAULT_UNIT,
  initialDpi: Int = DEFAULT_DENSITY.dpiValue,
  initialShape: Shape = DEFAULT_SHAPE
) : DeviceConfig(initialWidth, initialHeight, initialDimUnit, initialDpi, initialShape) {
  override var width: Int = initialWidth
  override var height: Int = initialHeight

  /**
   * Defines the unit in which [width] and [height] should be considered. Modifying this property also changes [width] and [height].
   */
  override var dimUnit: DimUnit = initialDimUnit
    set(newValue) {
      if (newValue != field) {
        field = newValue
        val baseDpi = Density.MEDIUM.dpiValue
        val dpiFactor = when (newValue) {
          DimUnit.px -> 1.0f * dpi / baseDpi
          DimUnit.dp -> 1.0f * baseDpi / dpi
        }
        // TODO(b/197021783): Do a more precise operation, or support floating point for width/height
        width = (width * dpiFactor).roundToInt()
        height = (height * dpiFactor).roundToInt()
      }
    }
  override var dpi: Int = initialDpi
  override var shape: Shape = initialShape

  /**
   * When changed, swaps the [width] and [height] values appropriately.
   */
  override var orientation: Orientation
    get() = super.orientation
    set(newValue) {
      when (newValue) {
        Orientation.portrait -> {
          if (height < width) {
            val temp = height
            height = width
            width = temp
          }
        }
        Orientation.landscape -> {
          if (width < height) {
            val temp = width
            width = height
            height = temp
          }
        }
      }
    }
}

/**
 * Returns an immutable copy of this [MutableDeviceConfig] instance.
 */
internal fun MutableDeviceConfig.toImmutableConfig(): DeviceConfig =
  DeviceConfig(
    shape = this.shape,
    width = this.width,
    height = this.height,
    dimUnit = this.dimUnit,
    dpi = this.dpi
  )

/**
 * Returns a mutable copy of this [DeviceConfig] instance.
 */
internal fun DeviceConfig.toMutableConfig(): MutableDeviceConfig =
  MutableDeviceConfig(
    initialShape = this.shape,
    initialWidth = this.width,
    initialHeight = this.height,
    initialDimUnit = this.dimUnit,
    initialDpi = this.dpi
  )

private fun StringBuilder.appendParamValue(parameterName: String, value: String): StringBuilder =
  append("$parameterName$PARAM_VALUE_OPERATOR$value")

private fun StringBuilder.appendSeparator(): StringBuilder =
  append(PARAM_SEPARATOR)

/**
 * The visual shape of the Device, usually applied as cutout.
 */
internal enum class Shape(val display: String) {
  Normal(""),
  Square("Square"),
  Round("Round"),
  Chin("Round Chin"),
}

/**
 * Unit for the Device's width and height.
 */
internal enum class DimUnit(val trackableValue: PickerTrackableValue) {
  px(PickerTrackableValue.UNIT_PIXELS),
  dp(PickerTrackableValue.UNIT_DP)
}

internal enum class Orientation(val trackableValue: PickerTrackableValue) {
  portrait(PickerTrackableValue.ORIENTATION_PORTRAIT),
  landscape(PickerTrackableValue.ORIENTATION_LANDSCAPE)
}