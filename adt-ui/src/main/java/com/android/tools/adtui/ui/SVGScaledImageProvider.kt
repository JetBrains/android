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
package com.android.tools.adtui.ui

import com.android.annotations.concurrency.WorkerThread
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.scale.ScaleContext
import java.awt.Image
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * A [ScaledImageProvider] for SVG resources. Aspect ratio is preserved when scaling.
 */
class SVGScaledImageProvider(private val cachedIcon: CachedImageIcon, private val image: Image?) : ScaledImageProvider {
  override val initialImage: Image?
    get() = image

  @Throws(java.io.IOException::class)
  @WorkerThread
  override fun createScaledImage(ctx: ScaleContext, width: Double, height: Double): Image {
    // Preserve an aspect ratio
    val size = width.coerceAtMost(height).toFloat() // min(width, height)
    val unscaledSize = cachedIcon.iconWidth.coerceAtMost(cachedIcon.iconHeight)

    // Load SVG file, with HiDPI support from [ctx]
    return (cachedIcon.scale(size / unscaledSize) as ImageIcon).image
  }

  companion object {
    @JvmStatic
    fun create(icon: Icon): SVGScaledImageProvider {
      if (icon is CachedImageIcon) {
        return create(icon)
      }
      throw IllegalArgumentException("Icon should be an instance of CachedImageIcon")
    }

    fun create(cachedIcon: CachedImageIcon): SVGScaledImageProvider {
      return SVGScaledImageProvider(cachedIcon, cachedIcon.getRealImage())
    }
  }
}
