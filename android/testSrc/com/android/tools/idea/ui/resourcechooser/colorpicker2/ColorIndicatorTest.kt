/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser.colorpicker2

import com.intellij.util.ui.UIUtil
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

class ColorIndicatorTest {

  @Test
  fun testSetColor() {
    val indicator = ColorIndicator()
    indicator.setSize(10, 10)
    indicator.background = Color.WHITE

    val bf = UIUtil.createImage(10, 10, BufferedImage.TYPE_INT_ARGB)
    indicator.paint(bf.graphics)
    assertEquals(DEFAULT_PICKER_COLOR, Color(bf.getRGB(5, 5)))

    indicator.color = Color.YELLOW
    indicator.paint(bf.graphics)
    assertEquals(Color.YELLOW, Color(bf.getRGB(5, 5)))

    indicator.color = Color.WHITE
    indicator.paint(bf.graphics)
    assertEquals(Color.WHITE, Color(bf.getRGB(5, 5)))
  }

  @Test
  fun testSetColorWithAlpha() {
    val indicator = ColorIndicator()
    indicator.setSize(10, 10)
    indicator.background = Color.WHITE

    val bf = UIUtil.createImage(10, 10, BufferedImage.TYPE_INT_ARGB)

    indicator.color = Color(0x40, 0x80, 0xA0, 0xC0)
    indicator.paint(bf.graphics)
    assertEquals(Color(0x40, 0x80, 0x9F), Color(bf.getRGB(5, 5)))

    indicator.color = Color(0x80, 0x40, 0xA0, 0xC0)
    indicator.paint(bf.graphics)
    assertEquals(Color(0x73, 0x4D, 0x9F), Color(bf.getRGB(5, 5)))

    indicator.color = Color(0x80, 0xA0, 0x40, 0xC0)
    indicator.paint(bf.graphics)
    assertEquals(Color(0x7D, 0x8C, 0x56), Color(bf.getRGB(5, 5)))
  }
}
