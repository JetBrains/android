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
import com.google.gson.Gson
import com.intellij.ui.JBColor
import java.awt.Color
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.abs

private data class ColorDefinition(val foreground: String, val background: String)
private data class ColorPalette(val name: String, val light: List<ColorDefinition>, val dark: List<ColorDefinition>)

/**
 * Palette file is visible for testing.
 */
object DataVisualizationColors {
  const val PRIMARY_DATA_COLOR = "Vivid Blue"
  const val BACKGROUND_DATA_COLOR = "Gray"

  // To make text readable against data viz colors we create text colors from these predefined values independent of the current theme.
  @JvmField
  val DEFAULT_LIGHT_TEXT_COLOR: Color = Color.WHITE

  @JvmField
  val DEFAULT_DARK_TEXT_COLOR: Color = Color.BLACK

  @VisibleForTesting
  val backgroundPalette = LinkedHashMap<String, List<JBColor>>()

  @VisibleForTesting
  val foregroundPalette = LinkedHashMap<String, List<JBColor>>()

  @VisibleForTesting
  var numberOfTonesPerColor = 0
  private var isInitialized = false

  /**
   * Initialize colors with a palette file if not already initialized. Tests that use custom palette file should use [doInitialize] instead.
   */
  private fun initialize(paletteStream: InputStream = javaClass.getResourceAsStream("/palette/data-visualization-palette.json")
                                                      ?: throw IllegalArgumentException("Palette not found")) {
    if (isInitialized) {
      return
    }
    doInitialize(paletteStream)
  }

  /**
   * May be used by tests to always overwrite the existing palette regardless of initialization status.
   */
  @VisibleForTesting
  fun doInitialize(paletteStream: InputStream) {
    loadColorPalette(paletteStream)
    numberOfTonesPerColor = backgroundPalette.values.first().size
    isInitialized = true
  }

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
  fun getColor(index: Int): JBColor = getColor(index, false)

  fun getColor(index: Int, isFocused: Boolean): JBColor = getColor(index, 0, isFocused)

  fun getColor(index: Int, toneIndex: Int, isFocused: Boolean): JBColor =
    getColorFromPalette(index, toneIndex, isFocused, backgroundPalette)

  fun getColor(name: String, toneIndex: Int): JBColor = getColor(name, toneIndex, false)

  fun getColor(name: String, isFocused: Boolean): JBColor = getColor(name, 0, isFocused)

  fun getColor(name: String, toneIndex: Int, isFocused: Boolean): JBColor = getColorFromPalette(name, toneIndex, isFocused,
                                                                                                backgroundPalette)

  /**
   * Returns a new color that represents the focused state.
   */
  fun getFocusColor(color: JBColor): JBColor = JBColor(color.brighter(), color.darker())

  /**
   * Returns the matching font color for the data color with the same index.
   */
  fun getFontColor(index: Int): JBColor = getColorFromPalette(index, 0, false, foregroundPalette)

  /**
   * Returns the matching font color for the data color with the same name.
   */
  fun getFontColor(name: String): JBColor = getColorFromPalette(name, 0, false, foregroundPalette)

  /**
   * Returns the computed grayscale version of the passed in color
   */
  fun toGrayscale(color: Color): Color {
    val avg = (0.3f * color.red / 255.0f) + (0.59f * color.blue / 255.0f) + (0.11f * color.green / 255.0f)
    return Color(avg, avg, avg)
  }

  /**
   * Returns grayscale
   */

  private fun getColorFromPalette(index: Int, toneIndex: Int, isFocused: Boolean, palette: Map<String, List<JBColor>>): JBColor {
    if (!isInitialized) {
      initialize()
    }
    val boundIndex = abs(index + numberOfTonesPerColor * toneIndex) % (palette.size * numberOfTonesPerColor)
    val colorIndex = boundIndex % palette.size
    val boundToneIndex = (boundIndex / palette.size) % numberOfTonesPerColor
    val color = palette.values.elementAt(colorIndex)[boundToneIndex]
    return if (isFocused) getFocusColor(color) else color
  }

  private fun getColorFromPalette(name: String, toneIndex: Int, isFocused: Boolean, palette: Map<String, List<JBColor>>): JBColor {
    if (!isInitialized) {
      initialize()
    }
    assert(palette.containsKey(name)) { "Palette does not contain specified key: ${name}." }
    val color = palette[name]!![toneIndex]
    return if (isFocused) getFocusColor(color) else color
  }

  /**
   * Helper function to parse and load the color palette json. The format of the json is expected to contain light, and dark mode colors
   * along with an array of tones for each color. The array of tones is expected to be a fixed length for all colors defined in the palette.
   *
   * TODO(albert): Add a link to the location of the auto generator
   * Note: The data-visualization-palette.json is an
   * [auto generated file](https://source.cloud.google.com/google.com:adux-source/studio-palettes/+/master:client/app/index.js;l=176)
   * and should be updated when the design team creates new colors.
   */
  private fun loadColorPalette(paletteStream: InputStream) {
    val gsonParser = Gson()
    backgroundPalette.clear()
    foregroundPalette.clear()

    val colors = gsonParser.fromJson(InputStreamReader(paletteStream), Array<ColorPalette>::class.java)
    colors.forEach {
      // Data colors
      assert(it.light.size == it.dark.size) {
        "Expected light (${it.light.size}), and dark (${it.dark.size}) palette to have same number of colors"
      }
      val backgroundColors = mutableListOf<JBColor>()
      val foregroundColors = mutableListOf<JBColor>()
      it.light.forEachIndexed { idx, color ->
        backgroundColors.add(JBColor(Integer.parseInt(color.background, 16), Integer.parseInt(it.dark[idx].background, 16)))
        foregroundColors.add(JBColor(Integer.parseInt(color.foreground, 16), Integer.parseInt(it.dark[idx].foreground, 16)))
      }
      foregroundPalette[it.name] = foregroundColors
      backgroundPalette[it.name] = backgroundColors
    }
  }
}