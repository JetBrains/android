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

import com.android.testutils.TestResources
import com.android.tools.adtui.common.ColorPaletteManager.ColorPalette
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.intellij.ui.JBColor
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.FileInputStream
import java.io.InputStreamReader

@RunWith(Parameterized::class)
class ColorPaletteManagerTest(private val isDarkMode: Boolean) {
  companion object {
    /**
     * JBColor equality only checks dark variant if the current theme is Darcular, so we need to programmatically set dark mode.
     */
    @JvmStatic
    @Parameterized.Parameters
    fun data() = listOf(false, true)
  }

  private var wasDarkMode = false // Restore dark mode after test runs

  private val colorPaletteManager = ColorPaletteManager(Gson().fromJson(
    InputStreamReader(FileInputStream(TestResources.getFile(javaClass, "/palette/data-colors.json"))),
    Array<ColorPalette>::class.java))

  @Before
  fun setUp() {
    wasDarkMode = !JBColor.isBright()
    JBColor.setDark(isDarkMode)
  }

  @After
  fun tearDown() {
    // Reset dark mode.
    JBColor.setDark(wasDarkMode)
  }

  /**
   * This test loads the production palette and if passes validates that the current data-colors.json is in a valid format.
   * It is meant to catch errors during presubmit when updating the palette json.
   */
  @Test
  fun validateFileFormat() {
    assumeFalse(isDarkMode)
    assertThat(colorPaletteManager.backgroundPalette).isNotEmpty()
    assertThat(colorPaletteManager.foregroundPalette).isNotEmpty()
  }

  @Test
  fun validateFileHasSameNumberVariantsForEachColor() {
    assumeFalse(isDarkMode)
    colorPaletteManager.backgroundPalette.values.forEach {
      assertThat(it.size).isEqualTo(colorPaletteManager.numberOfTonesPerColor)
    }
    colorPaletteManager.foregroundPalette.values.forEach {
      assertThat(it.size).isEqualTo(colorPaletteManager.numberOfTonesPerColor)
    }
  }

  @Test
  fun requestingBackgroundColorsByIndexIsDeterministic() {
    val initialColor = colorPaletteManager.getBackgroundColor(0)
    val nextColor = colorPaletteManager.getBackgroundColor(1)
    assertThat(initialColor).isNotEqualTo(nextColor)
    assertThat(initialColor).isEqualTo(colorPaletteManager.getBackgroundColor(0))
  }

  @Test
  fun requestingForegroundColorsByIndexIsDeterministic() {
    val initialColor = colorPaletteManager.getForegroundColor(0)
    val nextColor = colorPaletteManager.getForegroundColor(1)
    assertThat(initialColor).isNotEqualTo(nextColor)
    assertThat(initialColor).isEqualTo(colorPaletteManager.getForegroundColor(0))
  }

  @Test
  fun backgroundColorsByIndexHandlesOutOfBounds() {
    val palette = colorPaletteManager.backgroundPalette
    val numberOfColors = palette.size
    val numberOfTones = colorPaletteManager.numberOfTonesPerColor
    assertThat(colorPaletteManager.getBackgroundColor(-1)).isEqualTo(
      colorPaletteManager.getBackgroundColor(1))
    assertThat(colorPaletteManager.getBackgroundColor(numberOfColors * numberOfTones)).isEqualTo(
      colorPaletteManager.getBackgroundColor(0))
  }

  @Test
  fun foregroundColorsByIndexHandlesOutOfBounds() {
    val palette = colorPaletteManager.foregroundPalette
    val numberOfColors = palette.size
    val numberOfTones = colorPaletteManager.numberOfTonesPerColor
    assertThat(colorPaletteManager.getForegroundColor(-1)).isEqualTo(
      colorPaletteManager.getForegroundColor(1))
    assertThat(colorPaletteManager.getForegroundColor(numberOfColors * numberOfTones)).isEqualTo(
      colorPaletteManager.getForegroundColor(0))
  }

  @Test
  fun getBackgroundColorByName() {
    assertThat(colorPaletteManager.getBackgroundColor("Gray", 0)).isEqualTo(
      colorPaletteManager.getBackgroundColor(0, 0, false))
  }

  @Test
  fun getFocusColor() {
    val color = colorPaletteManager.getBackgroundColor(0)
    assertThat(colorPaletteManager.getFocusColor(color)).isNotEqualTo(color)
  }

  @Test
  fun getFocusedColors() {
    val color = colorPaletteManager.getBackgroundColor(0, false)
    val focusedColor = colorPaletteManager.getBackgroundColor(0, true)
    assertThat(focusedColor).isNotEqualTo(color)

    val gray = colorPaletteManager.getBackgroundColor("Gray", false)
    val focusedGray = colorPaletteManager.getBackgroundColor("Gray", true)
    assertThat(gray).isNotEqualTo(focusedGray)
  }

  @Test
  fun testGrayscaleColorConversion() {
    val color = JBColor(0x545454, 0xCACACA)
    val grayscaleColor = colorPaletteManager.toGrayscale(color)
    assertThat(grayscaleColor.red).isEqualTo(grayscaleColor.green)
    assertThat(grayscaleColor.green).isEqualTo(grayscaleColor.blue)
  }
}