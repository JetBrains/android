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
package com.android.tools.idea.compose.preview.pickers.properties

import com.android.resources.Density
import com.android.utils.HashCodes
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import kotlin.math.roundToInt

internal const val SPEC_PREFIX = "spec:"

internal const val DEFAULT_WIDTH = 1080
internal const val DEFAULT_HEIGHT = 1920
internal val DEFAULT_DENSITY = Density.XXHIGH
internal val DEFAULT_UNIT = DimUnit.px
internal val DEFAULT_SHAPE = Shape.Normal

private const val DENSITY_SUFFIX = "dpi"

/**
 * Defines some mutable hardware parameters of a Device. Can be encoded using [deviceSpec] and decoded using [DeviceConfig.toDeviceConfigOrDefault].
 *
 * @param dimUnit Determines the unit of the given [width] and [height]. Ie: For [DimUnit.px] they will be considered as pixels.
 * @param shape Shape of the device screen, may affect how the screen behaves, or it may add a cutout (like with wearables)
 */
internal class DeviceConfig(
  var width: Int = DEFAULT_WIDTH,
  var height: Int = DEFAULT_HEIGHT,
  dimUnit: DimUnit = DEFAULT_UNIT,
  var density: Int = DEFAULT_DENSITY.dpiValue,
  var shape: Shape = DEFAULT_SHAPE
) {
  /**
   * Defines the unit in which [width] and [height] should be considered. Modifying this property also changes [width] and [height].
   */
  var dimensionUnit: DimUnit = dimUnit
    set(newValue) {
      if (newValue != field) {
        field = newValue
        val baseDpi = Density.MEDIUM.dpiValue
        when (newValue) {
          DimUnit.px -> {
            width = (1.0f * width * density / baseDpi).roundToInt()
            height = (1.0f * height * density / baseDpi).roundToInt()
          }
          DimUnit.dp -> {
            width = (1.0f * baseDpi / density * width).roundToInt()
            height = (1.0f * baseDpi / density * height).roundToInt()
          }
        }
      }
    }

  var orientation: Orientation
    get() = if (height >= width) Orientation.portrait else Orientation.landscape
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

  /** Returns a string that defines the Device in the current state of [DeviceConfig] */
  fun deviceSpec(): String = "spec:${shape.name};$width;$height;${dimensionUnit.name};${density}$DENSITY_SUFFIX"

  override fun equals(other: Any?): Boolean {
    if (other !is DeviceConfig) {
      return false
    }
    return deviceSpec() == other.deviceSpec()
  }

  override fun hashCode(): Int {
    return HashCodes.mix(width, height, density, shape.hashCode(), dimensionUnit.hashCode())
  }

  companion object {

    /**
     * Returns a [DeviceConfig] from parsing the given string.
     *
     * For any step that might fail, a default value will be used.
     * So if all fails, returns an instance using all default values.
     */
    fun toDeviceConfigOrDefault(serialized: String?): DeviceConfig {
      if (serialized == null || !serialized.startsWith(SPEC_PREFIX)) return DeviceConfig()
      val configString = serialized.substringAfter(SPEC_PREFIX)
      val params = configString.split(';')
      if (params.size != 5) return DeviceConfig()

      val shape = try {
        Shape.valueOf(params[0])
      }
      catch (e: Exception) {
        DEFAULT_SHAPE
      }
      val width = params[1].toIntOrNull() ?: DEFAULT_WIDTH
      val height = params[2].toIntOrNull() ?: DEFAULT_HEIGHT
      val dimUnit = DimUnit.valueOfOrPx(params[3].toLowerCaseAsciiOnly())
      val dpi = params[4].substringBefore(DENSITY_SUFFIX).toIntOrNull() ?: DEFAULT_DENSITY.dpiValue
      return DeviceConfig(width = width, height = height, dimUnit = dimUnit, density = dpi, shape = shape)
    }
  }
}

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
internal enum class DimUnit {
  px,
  dp;

  companion object {
    fun valueOfOrPx(value: String) = try {
      valueOf(value)
    }
    catch (_: Exception) {
      px
    }
  }
}

internal enum class Orientation {
  portrait,
  landscape;

  companion object {
    fun valueOfOrPortrait(value: String) = try {
      valueOf(value)
    }
    catch (_: Exception) {
      portrait
    }
  }
}