/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.IconManager
import com.intellij.ui.JBColor
import icons.StudioIcons
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.FileInputStream
import javax.swing.Icon

private const val LIGHT_COLOR = 0xF1F2F3
private const val DARK_COLOR = 0x010203

@RunsInEdt
class ColoredIconGeneratorTest {
  @get:Rule
  val edtRule = EdtRule()

  private var wasDarkMode = false // Restore dark mode after test runs

  @Before
  fun setUp() {
    wasDarkMode = !JBColor.isBright()
    DataVisualizationColors.doInitialize(FileInputStream(TestResources.getFile(javaClass, "/palette/data-colors.json")))
    IconManager.activate(null)
    IconLoader.activate()
  }

  @After
  fun tearDown() {
    // Reset dark mode.
    JBColor.setDark(wasDarkMode)
    IconLoader.setUseDarkIcons(wasDarkMode)
    IconManager.deactivate()
    IconLoader.deactivate()
    IconLoader.clearCacheInTests()
  }

  @Test
  fun testColoredIcon() {
    for (origIcon in listOf(StudioIcons.Common.ERROR, StudioIcons.Common.PROPERTY_UNBOUND_FOCUS_LARGE, StudioIcons.Cursors.GRAB)) {
      val coloredIcon = ColoredIconGenerator.generateColoredIcon(origIcon, JBColor(LIGHT_COLOR, DARK_COLOR))

      JBColor.setDark(false)
      IconLoader.setUseDarkIcons(false)

      val origImg = getImage(origIcon)
      val coloredImg = getImage(coloredIcon)

      compareImages(origImg, coloredImg, LIGHT_COLOR)

      JBColor.setDark(true)
      IconLoader.setUseDarkIcons(true)
      val origImgDark = getImage(origIcon)
      val coloredImgDark = getImage(coloredIcon)

      compareImages(origImgDark, coloredImgDark, DARK_COLOR)
    }
  }

  @Suppress("UndesirableClassUsage")
  private fun getImage(icon: Icon): BufferedImage {
    val img = BufferedImage(icon.iconWidth, icon.iconHeight, TYPE_INT_ARGB)
    val graphics = img.graphics as Graphics2D
    graphics.composite = AlphaComposite.Src

    icon.paintIcon(null, graphics, 0, 0)
    return img
  }

  private fun compareImages(origImg: BufferedImage, coloredImg: BufferedImage, color: Int) {
    assertThat(origImg.width).isEqualTo(coloredImg.width)
    assertThat(origImg.height).isEqualTo(coloredImg.height)

    for ((origValue, coloredValue) in origImg.getRGB(0, 0, origImg.width, origImg.height, null, 0, origImg.width).zip(
      coloredImg.getRGB(0, 0, origImg.width, origImg.height, null, 0, origImg.width))) {
      if (coloredValue != 0 || origValue != 0) {
        assertThat(coloredValue and 0xffffff).isEqualTo(color and 0xffffff)
        assertThat(coloredValue shr 24).isEqualTo(origValue shr 24)
      }
    }
  }
}