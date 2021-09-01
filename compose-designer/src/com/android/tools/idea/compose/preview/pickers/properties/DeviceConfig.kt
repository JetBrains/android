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
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import kotlin.math.roundToInt

/**
 * Defines some mutable hardware parameters of a Device. Can be encoded using [deviceSpec] and decoded using [DeviceConfig.parse].
 *
 * @param dimUnit Determines the unit of the given [width] and [height]. Ie: For [DimUnit.px] they will be considered as pixels.
 * @param shape Shape of the device screen, may affect how the screen behaves, or it may add a cutout (like with wearables)
 */
internal class DeviceConfig(
  var width: Int = 1080,
  var height: Int = 1920,
  dimUnit: DimUnit = DimUnit.px,
  var density: Int = 480,
  var orientation: Orientation = Orientation.portrait,
  var shape: Shape = Shape.Normal
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

  /** Returns a string that defines the Device in the current state of [DeviceConfig] */
  fun deviceSpec(): String =
    "spec:${shape.name};$width;$height;${dimensionUnit.name};${density}dpi;${orientation.name}"

  companion object {
    fun parse(serialized: String?): DeviceConfig {
      val configString = serialized?.substringAfter(':') ?: return DeviceConfig()
      val params = configString.split(';')
      if (params.size != 6) return DeviceConfig()

      val shape = try {
        Shape.valueOf(params[0])
      }
      catch (e: Exception) {
        Shape.Normal
      }
      val width = params[1].toIntOrNull() ?: return DeviceConfig()
      val height = params[2].toIntOrNull() ?: return DeviceConfig()
      val isPx = DimUnit.valueOfOrPx(params[3].toLowerCaseAsciiOnly())
      val dpi = params[4].substringBefore("dpi").toIntOrNull() ?: 480
      val orientation = Orientation.valueOfOrPortrait(params[5].toLowerCaseAsciiOnly())
      return DeviceConfig(
        width = width,
        height = height,
        dimUnit = isPx,
        density = dpi,
        orientation = orientation,
        shape = shape
      )
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
    } catch(_: Exception) {
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