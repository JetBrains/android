/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wear.preview.animation

import com.android.tools.idea.preview.animation.AnimationUnit
import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import java.awt.Color

/**
 * Represents a color value as a unit in a Wear animation.
 *
 * Stores color components (red, green, blue, alpha) as integers (0-255) and provides methods for
 * conversion, string representation, and parsing.
 */
class ColorUnit(override val color: Color) :
  AnimationUnit.BaseUnit<Int>(color.red, color.green, color.blue, color.alpha),
  AnimationUnit.Color<Int, ColorUnit> {

  /**
   * Creates a [ColorUnit] from individual integer (0-255) RGBA values.
   *
   * @param r Red component (0-255)
   * @param g Green component (0-255)
   * @param b Blue component (0-255)
   * @param a Alpha component (0-255, defaults to 255 for opaque)
   */
  constructor(r: Int, g: Int, b: Int, a: Int = 255) : this(Color(r, g, b, a))

  companion object {
    /**
     * Creates a [ColorUnit] from a single integer representing the ARGB color value.
     *
     * @param argb The ARGB color value as an integer.
     */
    fun parseColorUnit(argb: Any?): ColorUnit {
      if (argb is Int) return ColorUnit(Color(argb, true))
      throw IllegalArgumentException("Error parsing color")
    }
  }

  /**
   * Returns a string representation of the specified color component.
   *
   * @param componentId The index of the component (0 for red, 1 for green, 2 for blue, 3 for
   *   alpha).
   * @return A string in the format "Component: Value" (e.g., "R: 255").
   */
  override fun toString(componentId: Int) =
    when (componentId) {
      0 -> "R: ${color.red}"
      1 -> "G: ${color.green}"
      2 -> "B: ${color.blue}"
      3 -> "A: ${color.alpha}"
      else -> "Invalid componentId"
    }

  /**
   * Attempts to parse a color unit from a string.
   *
   * This implementation expects a string in the format: "R: 123, G: 45, B: 67, A: 255"
   *
   * @param getValue A function to retrieve the string value for parsing.
   * @return A [ColorUnit] instance if parsing is successful, or null otherwise.
   */
  override fun parseUnit(getValue: (Int) -> String?): AnimationUnit.Unit<*>? {
    val values =
      getValue(0)?.split(",")?.mapNotNull { s ->
        s.trim().split(":").let { if (it.size == 2) it[1].trim().toIntOrNull() else null }
      }
    return if (values?.size == 4) {
      ColorUnit(values[0], values[1], values[2], values[3])
    } else {
      null
    }
  }

  override fun create(color: Color): ColorUnit {
    return parseColorUnit(color.rgb)
  }

  /** Returns the specified component as a double value (for use in calculations or plotting). */
  override fun componentAsDouble(componentId: Int): Double {
    return components[componentId].toDouble()
  }

  /**
   * Gets the title for a color picker used to edit this color unit.
   *
   * @return A localized string for "Color".
   */
  override fun getPickerTitle(): String = message("animation.inspector.picker.color")
}
