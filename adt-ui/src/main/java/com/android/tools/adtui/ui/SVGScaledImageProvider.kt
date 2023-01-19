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
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.SVGLoader
import java.awt.Image
import java.net.URL
import javax.swing.Icon

/**
 * A [ScaledImageProvider] for SVG resources. Aspect ratio is preserved when scaling.
 */
class SVGScaledImageProvider(private val url: URL, private val image: Image?) : ScaledImageProvider {
  override val initialImage: Image?
    get() = image

  @Throws(java.io.IOException::class)
  @WorkerThread
  override fun createScaledImage(ctx: ScaleContext, width: Double, height: Double): Image {
    // Preserve aspect ratio
    val size = width.coerceAtMost(height) // min(width, height)

    // Load SVG file, with HiDPI support from [ctx]
    return SVGLoader.load(url, url.openStream(), ctx, size, size)
  }

  companion object {
    @JvmStatic
    fun create(icon: Icon): SVGScaledImageProvider {
      if (icon is IconLoader.CachedImageIcon) {
        return create(icon)
      }
      throw IllegalArgumentException("Icon should be an instance of CachedImageIcon. Got "+icon.javaClass.simpleName)
    }

    @JvmStatic
    fun create(cachedIcon: IconLoader.CachedImageIcon): SVGScaledImageProvider {
      val url = cachedIcon.url
      if (url != null) {
        return SVGScaledImageProvider(url, cachedIcon.realIcon.image)
      }
      throw IllegalArgumentException("CachedImageIcon should have a valid URL")
    }
  }
}
