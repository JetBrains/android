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

import com.android.tools.adtui.util.GraphicsUtil
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.UIUtil

import java.awt.*
import kotlin.math.max
import kotlin.math.min

private val DEFAULT_SLIDER_BACKGROUND = Color.WHITE

class AlphaSliderComponent : SliderComponent<Int>(0) {

  /**
   * Used to set the color on slider
   */
  var sliderBackgroundColor: Color = DEFAULT_SLIDER_BACKGROUND

  override fun knobPositionToValue(knobPosition: Int): Int {
    return if (sliderWidth > 0) Math.round(knobPosition * 255f / sliderWidth) else 0
  }

  override fun valueToKnobPosition(value: Int): Int = Math.round(value * sliderWidth / 255f)

  override fun slideLeft(size: Int) {
    value = max(0, value - size)
  }

  override fun slideRight(size: Int) {
    value = min(value + size, 255)
  }

  override fun paintSlider(g2d: Graphics2D) {
    val transparent = ColorUtil.toAlpha(Color.WHITE, 0)

    val clip = Rectangle(leftPadding, topPadding, width - leftPadding - rightPadding, height - topPadding - bottomPadding)
    GraphicsUtil.paintCheckeredBackground(g2d, Color.LIGHT_GRAY, Color.GRAY, clip, 6)

    val sliderBackgroundWithoutAlpha = Color(sliderBackgroundColor.rgb and 0x00FFFFFF)
    g2d.paint = UIUtil.getGradientPaint(0f, 0f, transparent, width.toFloat(), 0f, sliderBackgroundWithoutAlpha)
    g2d.fillRect(clip.x, clip.y, clip.width, clip.height)
  }
}
