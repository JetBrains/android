/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.common

import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.JBColor
import java.awt.Color
import kotlin.math.abs

/**
 * Provides colors from a palette provided by the client.
 *
 * See documentation of [ColorPaletteManager.getBackgroundColor] for details.
 */
class ColorPaletteManager(pallets: Array<ColorPalette>) {
  data class ColorPalette(val name: String, val light: List<ColorDefinition>, val dark: List<ColorDefinition>)
  data class ColorDefinition(val foreground: String, val background: String)

  @VisibleForTesting
  val foregroundPalette: LinkedHashMap<String, List<JBColor>> = pallets.associateByTo(LinkedHashMap(), ColorPalette::name) {
    loadColors(it, ColorDefinition::foreground)
  }

  @VisibleForTesting
  val backgroundPalette: LinkedHashMap<String, List<JBColor>> = pallets.associateByTo(LinkedHashMap(), ColorPalette::name) {
    loadColors(it, ColorDefinition::background)
  }

  // We ensure all colors have the same number of tones when we loadColors().
  @VisibleForTesting
  val numberOfTonesPerColor = pallets.first().light.size

  /**
   * Returns a color that is mapped to an index. Index values that are outside the total number of colors are wrapped.
   * The map of colors can be thought of as a table of colors by tones. Each row is a new color each column is a new tone.  We start at 0,0
   * and enumerate the table row first until we run out of colors. At this point we increment our column and repeat.
   * Example:
   * [COLORA_TONE0, COLORA_TONE1]
   * [COLORB_TONE0, COLORB_TONE1]
   *
   * Index 0 maps to COLORA_TONE0
   * Index 1 maps to COLORB_TONE0
   * Index 2 maps to COLORA_TONE1
   * Index 3 maps to COLORB_TONE1.
   */
  fun getBackgroundColor(index: Int): JBColor = getBackgroundColor(index, false)

  fun getBackgroundColor(index: Int, isFocused: Boolean): JBColor = getBackgroundColor(index, 0, isFocused)

  fun getBackgroundColor(index: Int, toneIndex: Int, isFocused: Boolean): JBColor =
    getColorFromPalette(index, toneIndex, isFocused, backgroundPalette)

  fun getBackgroundColor(name: String, toneIndex: Int): JBColor = getBackgroundColor(name, toneIndex, false)

  fun getBackgroundColor(name: String, isFocused: Boolean): JBColor = getBackgroundColor(name, 0, isFocused)

  fun getBackgroundColor(name: String, toneIndex: Int, isFocused: Boolean): JBColor =
    getColorFromPalette(name, toneIndex, isFocused, backgroundPalette)

  /**
   * Returns a new color that represents the focused state.
   */
  fun getFocusColor(color: JBColor): JBColor = JBColor(color.brighter(), color.darker())

  /**
   * Returns the matching font color for the data color with the same index.
   */
  fun getForegroundColor(index: Int): JBColor = getColorFromPalette(index, 0, false, foregroundPalette)

  /**
   * Returns the matching font color for the data color with the same name.
   */
  fun getForegroundColor(name: String): JBColor = getColorFromPalette(name, 0, false, foregroundPalette)

  /**
   * Returns the computed grayscale version of the passed in color
   */
  fun toGrayscale(color: Color): Color {
    val avg = (0.3f * color.red / 255.0f) + (0.59f * color.blue / 255.0f) + (0.11f * color.green / 255.0f)
    return Color(avg, avg, avg)
  }

  private fun getColorFromPalette(index: Int, toneIndex: Int, isFocused: Boolean, palette: Map<String, List<JBColor>>): JBColor {
    val boundIndex = abs(index + numberOfTonesPerColor * toneIndex) % (palette.size * numberOfTonesPerColor)
    val colorIndex = boundIndex % palette.size
    val boundToneIndex = (boundIndex / palette.size) % numberOfTonesPerColor
    val color = palette.values.elementAt(colorIndex)[boundToneIndex]
    return if (isFocused) getFocusColor(color) else color
  }

  private fun getColorFromPalette(name: String, toneIndex: Int, isFocused: Boolean, palette: Map<String, List<JBColor>>): JBColor {
    assert(palette.containsKey(name)) { "Palette does not contain specified key: ${name}." }
    val color = palette[name]!![toneIndex]
    return if (isFocused) getFocusColor(color) else color
  }

  private fun loadColors(it: ColorPalette, getColor: (ColorDefinition) -> String): List<JBColor> {
    assert(it.light.size == it.dark.size) {
      "Expected light (${it.light.size}), and dark (${it.dark.size}) palette to have same number of colors"
    }
    return it.light.mapIndexed { index, color ->
      JBColor(Integer.parseInt(getColor(color), 16), Integer.parseInt(getColor(it.dark[index]), 16))
    }
  }
}