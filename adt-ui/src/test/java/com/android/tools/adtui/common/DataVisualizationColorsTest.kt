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
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.JBColor
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.awt.Color
import java.io.FileInputStream

@RunWith(Parameterized::class)
class DataVisualizationColorsTest(private val isDarkMode: Boolean) {
  companion object {
    /**
     * JBColor equality only checks dark variant if the current theme is Darcular, so we need to programmatically set dark mode.
     */
    @JvmStatic
    @Parameterized.Parameters
    fun data() = listOf(false, true)
  }

  private var wasDarkMode = false // Restore dark mode after test runs

  @Before
  fun setUp() {
    wasDarkMode = !JBColor.isBright()
    JBColor.setDark(isDarkMode)
    DataVisualizationColors.initialize(FileInputStream(TestResources.getFile(javaClass, "/palette/data-colors.json")))
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
    assertThat(DataVisualizationColors.dataPalette).isNotEmpty()
    assertThat(DataVisualizationColors.fontPalette).isNotEmpty()
  }

  @Test
  fun validateFileHasSameNumberVariantsForEachColor() {
    assumeFalse(isDarkMode)
    DataVisualizationColors.dataPalette.values.forEach {
      assertThat(it.size).isEqualTo(DataVisualizationColors.numberOfTonesPerColor)
    }
    DataVisualizationColors.fontPalette.values.forEach {
      assertThat(it.size).isEqualTo(DataVisualizationColors.numberOfTonesPerColor)
    }
  }

  @Test
  fun requestingColorsByIndexIsDeterministic() {
    val initialColor = DataVisualizationColors.getColor(0)
    val nextColor = DataVisualizationColors.getColor(1)
    assertThat(initialColor).isNotEqualTo(nextColor)
    assertThat(initialColor).isEqualTo(DataVisualizationColors.getColor(0))
  }

  @Test
  fun colorsByIndexHandlesOutOfBounds() {
    val palette = DataVisualizationColors.dataPalette
    val numberOfColors = palette.size
    val numberOfTones = DataVisualizationColors.numberOfTonesPerColor
    assertThat(DataVisualizationColors.getColor(-1)).isEqualTo(
      DataVisualizationColors.getColor(1))
    assertThat(DataVisualizationColors.getColor(numberOfColors * numberOfTones)).isEqualTo(
      DataVisualizationColors.getColor(0))
  }

  @Test
  fun getColorByName() {
    assertThat(DataVisualizationColors.getColor("Gray", 0)).isEqualTo(
      DataVisualizationColors.getColor(0, 0, false))
  }

  @Test
  fun getFocusColor() {
    val color = DataVisualizationColors.getColor(0)
    assertThat(DataVisualizationColors.getFocusColor(color)).isNotEqualTo(color)
  }

  @Test
  fun getFocusedColors() {
    val color = DataVisualizationColors.getColor(0, false)
    val focusedColor = DataVisualizationColors.getColor(0, true)
    assertThat(focusedColor).isNotEqualTo(color)

    val gray = DataVisualizationColors.getColor("Gray", false)
    val focusedGray = DataVisualizationColors.getColor("Gray", true)
    assertThat(gray).isNotEqualTo(focusedGray)
  }

  @Test
  fun getFontColors() {
    assertThat(DataVisualizationColors.getFontColor(0)).isEqualTo(JBColor(Color.BLACK, Color.BLACK))
    assertThat(DataVisualizationColors.getFontColor("Light Blue")).isEqualTo(JBColor(Color.WHITE, Color.WHITE))
  }
}