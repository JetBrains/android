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
package com.android.tools.profilers

import com.android.tools.adtui.common.ColorPaletteManager
import com.android.tools.adtui.common.ColorPaletteManager.ColorPalette
import com.google.gson.Gson
import java.awt.Color
import java.io.InputStreamReader

/**
 * The data-visualization-palette.json file is
 * [auto generated](https://source.cloud.google.com/google.com:adux-source/studio-palettes/+/master:client/app/index.js;l=176)
 * and should be updated when the design team creates new colors.
 */
private const val PALETTE_JSON_FILENAME = "/palette/data-visualization-palette.json"

/**
 * A singleton container for a [ColorPaletteManager] that provides colors for data visualization.
 */
object DataVisualizationColors {
  /**
   * This color name refers to an entry in the [PALETTE_JSON_FILENAME] file loaded below
   */
  const val BACKGROUND_DATA_COLOR_NAME = "Gray"

  @JvmField
  val DEFAULT_LIGHT_TEXT_COLOR: Color = Color.WHITE

  @JvmField
  val DEFAULT_DARK_TEXT_COLOR: Color = Color.BLACK

  @JvmStatic
  val paletteManager: ColorPaletteManager by lazy {
    javaClass.getResourceAsStream(PALETTE_JSON_FILENAME)?.let {
      ColorPaletteManager(Gson().fromJson(InputStreamReader(it), Array<ColorPalette>::class.java))
    } ?: throw IllegalStateException("Resource not found")
  }
}
