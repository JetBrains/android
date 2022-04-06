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
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.DEFAULT_CHIN_SIZE_ZERO
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.DEFAULT_DPI
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.DEFAULT_HEIGHT_PX
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.DEFAULT_IS_ROUND
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.DEFAULT_SHAPE
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.DEFAULT_UNIT
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.DEFAULT_WIDTH_PX
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.OPERATOR
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.PARAMETER_CHIN_SIZE
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.PARAMETER_DPI
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.PARAMETER_HEIGHT
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.PARAMETER_IS_ROUND
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.PARAMETER_SHAPE
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.PARAMETER_UNIT
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.PARAMETER_WIDTH
import com.android.tools.idea.compose.preview.Preview.DeviceSpec.SEPARATOR
import com.android.tools.idea.compose.preview.pickers.properties.utils.DEVICE_BY_SPEC_PREFIX
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.enumValueOfOrNull
import com.android.utils.HashCodes
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import kotlin.math.roundToInt

private const val PARAM_LEGACY_SEPARATOR = ';'

private const val DENSITY_SUFFIX = "dpi"
private const val WIDTH_SUFFIX = "w"
private const val HEIGHT_SUFFIX = "h"

/**
 * Defines some hardware parameters of a Device. Can be encoded using [deviceSpec] and decoded using [DeviceConfig.toDeviceConfigOrNull].
 *
 * @param dimUnit Determines the unit of the given [width] and [height]. Ie: For [DimUnit.px] they will be considered as pixels.
 * @param shape Shape of the device screen, may affect how the screen behaves, or it may add a cutout (like with wearables).
 * @param chinSize For round devices only, defines the height of the flat surface on a screen, measured from the bottom.
 */
internal open class DeviceConfig(
  open val width: Int = DEFAULT_WIDTH_PX,
  open val height: Int = DEFAULT_HEIGHT_PX,
  open val dimUnit: DimUnit = DEFAULT_UNIT,
  open val dpi: Int = DEFAULT_DPI,
  open val shape: Shape = DEFAULT_SHAPE,
  open val chinSize: Int = DEFAULT_CHIN_SIZE_ZERO
) {
  open val orientation: Orientation
    get() = if (height >= width) Orientation.portrait else Orientation.landscape

  open val isRound: Boolean
    get() = shape == Shape.Round || shape == Shape.Chin

  /** Returns a string that defines the Device in the current state of [DeviceConfig] */
  fun deviceSpec(): String {
    val builder = StringBuilder(DEVICE_BY_SPEC_PREFIX)
    if (!StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.get()) {
      builder.appendParamValue(PARAMETER_SHAPE, shape.name)
      builder.appendSeparator()
      builder.appendParamValue(PARAMETER_WIDTH, width.toString())
      builder.appendSeparator()
      builder.appendParamValue(PARAMETER_HEIGHT, height.toString())
      builder.appendSeparator()
      builder.appendParamValue(PARAMETER_UNIT, dimUnit.name)
      builder.appendSeparator()
      builder.appendParamValue(PARAMETER_DPI, dpi.toString())
      return builder.toString()
    }
    else {
      // TODO(b/227255434): Support parent=<device_id>
      // TODO(b/227255434): Figure out what to do with Orientation parameter
      builder.appendParamValue(PARAMETER_WIDTH, width.toString() + dimUnit.name)
      builder.appendSeparator()
      builder.appendParamValue(PARAMETER_HEIGHT, height.toString() + dimUnit.name)
      if (dpi != DEFAULT_DPI) {
        builder.appendSeparator()
        builder.appendParamValue(PARAMETER_DPI, dpi.toString())
      }
      if (isRound) {
        builder.appendSeparator()
        builder.appendParamValue(PARAMETER_IS_ROUND, isRound.toString())
        if (chinSize != DEFAULT_CHIN_SIZE_ZERO) {
          // ChinSize is only applicable to round devices, see com.android.sdklib.devices.Screen#getChin
          builder.appendSeparator()
          builder.appendParamValue(PARAMETER_CHIN_SIZE, chinSize.toString() + dimUnit.name)
        }
      }
      return builder.toString()
    }
  }

  override fun equals(other: Any?): Boolean {
    if (other !is DeviceConfig) {
      return false
    }
    return deviceSpec() == other.deviceSpec()
  }

  override fun hashCode(): Int {
    return HashCodes.mix(width, height, dpi, shape.hashCode(), dimUnit.hashCode(), chinSize, isRound.hashCode())
  }

  companion object {
    fun toMutableDeviceConfigOrNull(serialized: String?): MutableDeviceConfig? {
      return toDeviceConfigOrNull(serialized)?.toMutableConfig()
    }

    fun toDeviceConfigOrNull(serialized: String?): DeviceConfig? {
      if (serialized == null || !serialized.startsWith(DEVICE_BY_SPEC_PREFIX)) return null
      val configString = serialized.substringAfter(DEVICE_BY_SPEC_PREFIX)
      val paramsMap = configString.split(SEPARATOR).filter {
        it.length >= 3 && it.contains(OPERATOR)
      }.associate { paramString ->
        Pair(paramString.substringBefore(OPERATOR).trim(), paramString.substringAfter(OPERATOR).trim())
      }

      if (!paramsMap.containsKey(PARAMETER_SHAPE) && StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.get()) {
        return parseDeviceSpecLanguage(paramsMap)
      }

      if (paramsMap.size != 5) {
        // Try to parse with old method, otherwise, continue
        legacyParseToDeviceConfig(serialized)?.let { return it }
      }

      val shape = enumValueOfOrNull<Shape>(paramsMap.getOrDefault(PARAMETER_SHAPE, "")) ?: return null
      val width = paramsMap.getOrDefault(PARAMETER_WIDTH, "").toIntOrNull() ?: return null
      val height = paramsMap.getOrDefault(PARAMETER_HEIGHT, "").toIntOrNull() ?: return null
      val dimUnit = enumValueOfOrNull<DimUnit>(paramsMap.getOrDefault(PARAMETER_UNIT, "").toLowerCaseAsciiOnly()) ?: return null
      val dpi = paramsMap.getOrDefault(PARAMETER_DPI, "").toIntOrNull() ?: return null
      return DeviceConfig(width = width, height = height, dimUnit = dimUnit, dpi = dpi, shape = shape)
    }

    private fun parseDeviceSpecLanguage(params: Map<String, String>): DeviceConfig? {
      // Width & height are required
      val width = parseAndroidNumberOrNull(params[PARAMETER_WIDTH]) ?: return null
      val height = parseAndroidNumberOrNull(params[PARAMETER_HEIGHT]) ?: return null
      val chinSize = parseAndroidNumberOrNull(params[PARAMETER_CHIN_SIZE]) // Chin size is optional
      if (width.unit != height.unit) {
        // We currently require the units of all dimensions to match
        return null
      }
      else if (params[PARAMETER_CHIN_SIZE] != null && (chinSize == null || chinSize.unit != width.unit)) {
        // If chinSize is present, but parsing failed (chinSize == null) or it doesn't match the width & height unit
        return null
      }

      val dimUnit = width.unit
      val dpi = if (params[PARAMETER_DPI] != null) {
        // Only return null if the parsing itself failed
        params[PARAMETER_DPI]?.toIntOrNull() ?: return null
      }
      else {
        // Default value for optional parameter
        DEFAULT_DPI
      }
      val isRound = if (params[PARAMETER_IS_ROUND] != null) {
        // Only return null if the parsing itself failed
        params[PARAMETER_IS_ROUND]?.toBooleanStrictOrNull() ?: return null
      }
      else {
        // Default value for optional parameter
        DEFAULT_IS_ROUND
      }
      val chinSizeValue = if (params[PARAMETER_CHIN_SIZE] != null) {
        // Only return null if the parsing itself failed
        chinSize?.value?.roundToInt() ?: return null
      }
      else {
        // Default value for optional parameter
        DEFAULT_CHIN_SIZE_ZERO
      }
      return DeviceConfig(
        width = width.value.roundToInt(),
        height = height.value.roundToInt(),
        dimUnit = dimUnit,
        dpi = dpi,
        shape = if (isRound || chinSizeValue > 0) Shape.Round else Shape.Normal,
        chinSize = chinSizeValue
      )
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
  initialWidth: Int = DEFAULT_WIDTH_PX,
  initialHeight: Int = DEFAULT_HEIGHT_PX,
  initialDimUnit: DimUnit = DEFAULT_UNIT,
  initialDpi: Int = DEFAULT_DPI,
  initialShape: Shape = DEFAULT_SHAPE,
  initialChinSize: Int = DEFAULT_CHIN_SIZE_ZERO
) : DeviceConfig(initialWidth, initialHeight, initialDimUnit, initialDpi, initialShape) {
  override var width: Int = initialWidth
  override var height: Int = initialHeight
  override var chinSize: Int = initialChinSize
  override var dpi: Int = initialDpi
  override var shape: Shape = initialShape

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
        chinSize = (chinSize * dpiFactor).roundToInt()
      }
    }

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
    initialDpi = this.dpi,
    initialChinSize = this.chinSize
  )

private data class AndroidDimension(
  val value: Float,
  val unit: DimUnit
) {
  // TODO(b/227255434): Refactor DeviceConfig to internally use this class

  /**
   * Returns an [AndroidDimension] with the value calculated for [newUnit] with the given [dpi].
   */
  fun transformTo(newUnit: DimUnit, dpi: Int): AndroidDimension {
    if (newUnit == this.unit) return this
    val baseDpi = Density.MEDIUM.dpiValue
    val dpiFactor = when (newUnit) {
      DimUnit.px -> 1.0f * dpi / baseDpi
      DimUnit.dp -> 1.0f * baseDpi / dpi
    }
    return AndroidDimension(value = value * dpiFactor, unit = newUnit)
  }
}

private fun parseAndroidNumberOrNull(text: String?): AndroidDimension? {
  if (text == null) return null
  val unit = text.takeLast(2)
  val dimUnit = enumValueOfOrNull<DimUnit>(unit) ?: return null
  val value = text.dropLast(2).toFloatOrNull() ?: return null
  return AndroidDimension(value = value, unit = dimUnit)
}

private fun StringBuilder.appendParamValue(parameterName: String, value: String): StringBuilder =
  append("$parameterName$OPERATOR$value")

private fun StringBuilder.appendSeparator(): StringBuilder =
  append(SEPARATOR)

/**
 * The visual shape of the Device, usually applied as cutout.
 */
internal enum class Shape {
  Normal,

  @Deprecated("Redundant for DeviceConfig, set 'shape=Normal'")
  Square,
  Round,

  @Deprecated("Redundant for DeviceConfig, set 'shape=Round' and a value for 'chinSize'")
  Chin,
}

/**
 * Unit for the Device's width and height.
 */
internal enum class DimUnit(val trackableValue: PreviewPickerValue) {
  px(PreviewPickerValue.UNIT_PIXELS),
  dp(PreviewPickerValue.UNIT_DP)
}

internal enum class Orientation(val trackableValue: PreviewPickerValue) {
  portrait(PreviewPickerValue.ORIENTATION_PORTRAIT),
  landscape(PreviewPickerValue.ORIENTATION_LANDSCAPE)
}