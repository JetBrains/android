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
import java.awt.Color
import java.awt.image.RGBImageFilter
import java.util.function.Supplier
import javax.swing.Icon

val WHITE = JBColor(Color.white, Color.white)

/**
 * Generator of icons where the colors are modified from given source icons.
 */
object ColoredIconGenerator {

  /**
   * Generate an icon where all the colors are replaced with [WHITE].
   *
   * This method is meant to be used in renderer of selected content where the background is dark blue.
   */
  @JvmStatic fun generateWhiteIcon(icon: Icon): Icon {
    return generateColoredIcon(icon, WHITE)
  }

  /**
   * Generate an icon where all the colors are replaced with the given [color].
   *
   * The alpha value of the source icon is maintained. It is assumed that the alpha of the target [color] is 1.
   * Note: that the actual instance of [color] may be an [JBColor] and we may end up with 3 different icons in the cache created.
   */
  fun generateColoredIcon(icon: Icon, color: Color): Icon = IconLoader.createLazy(object : Supplier<Icon> {
    private val cache = mutableMapOf<Int, Icon>()

    override fun get(): Icon =
      cache.getOrPut(color.rgb) {
        IconLoader.filterIcon(icon) {
          object : RGBImageFilter() {
            override fun filterRGB(x: Int, y: Int, rgb: Int) = (rgb or 0xffffff) and color.rgb
          }
        }
      }
  })

  /**
   * Generate an icon where all the alpha values are decreased thus giving a more faint version of the specified [icon].
   */
  fun generateDeEmphasizedIcon(icon: Icon): Icon = IconLoader.createLazy {
    IconLoader.filterIcon(icon) {
      object : RGBImageFilter() {
        @Suppress("UseJBColor")
        override fun filterRGB(x: Int, y: Int, rgb: Int): Int = Color(rgb, true).deEmphasize().rgb
      }
    }
  }

  /**
   * Return a [Color] where the alpha value is decreased to make a more faint version of the given [Color].
   */
  @Suppress("UseJBColor")
  fun Color.deEmphasize(): Color = Color(red, green, blue, (alpha + 1) / 2)
}
