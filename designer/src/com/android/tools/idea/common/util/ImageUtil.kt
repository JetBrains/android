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

import com.intellij.util.ui.UIUtil
import java.awt.Image
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * Utility function to convert from an Icon to an Image
 */
fun iconToImage(icon: Icon): Image {
  return if (icon is ImageIcon) {
    icon.image
  }
  else {
    val w = icon.iconWidth
    val h = icon.iconHeight
    val image = UIUtil.createImage(w, h, BufferedImage.TYPE_4BYTE_ABGR)
    val g = image.createGraphics()
    icon.paintIcon(null, g, 0, 0)
    g.dispose()
    image
  }
}
