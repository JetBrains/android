/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.util.IconUtil
import java.awt.Color
import java.awt.image.RGBImageFilter
import javax.swing.Icon

val WHITE = JBColor(Color.white, Color.white)

/**
 * Generator of icons where all colors are replaced with the given color.
 * The alpha value of the source icon is maintained. It is assumed that the alpha of the target color is 1.
 */
object ColoredIconGenerator {

  @JvmStatic fun generateWhiteIcon(icon: Icon): Icon {
    return generateColoredIcon(icon, WHITE)
  }

  @Deprecated(message = "Use generatedColorIcon(Icon, JBColor) instead",
              replaceWith = ReplaceWith(expression = "ColoredIconGenerator.generateColoredIcon(icon, JBColor(color, color))",
                                        imports = ["com.intellij.ui.JBColor"]))
  fun generateColoredIcon(icon: Icon, color: Color) = generateColoredIcon(icon, JBColor(color, color))

  fun generateColoredIcon(icon: Icon, color: JBColor): Icon = ColoredLazyIcon(icon, color)
}

private class ColoredLazyIcon(val icon: Icon, val color: JBColor) : IconLoader.LazyIcon() {
  private val cache = mutableMapOf<Int, Icon>()

  override fun compute() =
    cache.getOrPut(color.rgb) {
      IconUtil.filterIcon(icon, { object: RGBImageFilter() {
        override fun filterRGB(x: Int, y: Int, rgb: Int) = (rgb or 0xffffff) and color.rgb
      } }, null) }
}
