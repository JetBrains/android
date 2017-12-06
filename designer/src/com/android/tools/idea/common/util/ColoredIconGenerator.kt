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
package com.android.tools.idea.common.util

import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * Generator of icons where all colors are replaced with the given color.
 * The alpha value of each color is maintained.
 */
object ColoredIconGenerator {

  fun generateWhiteIcon(icon: Icon): Icon {
    return generateColoredIcon(icon, Color.WHITE.rgb)
  }

  fun generateColoredIcon(icon: Icon, color: Int): Icon {
    //noinspection UndesirableClassUsage
    val image = generateColoredImage(icon, color)
    return ImageIcon(image)
  }

  fun generateColoredImage(icon: Icon, color: Int): BufferedImage {
    val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val g2 = image.graphics
    icon.paintIcon(null, g2, 0, 0)
    g2.dispose()
    for (i in 0 until image.width) {
      for (j in 0 until image.height) {
        image.setRGB(i, j, (image.getRGB(i, j) or 0xffffff) and color)
      }
    }
    return image
  }
}
