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
package com.android.tools.idea.common.scene.draw

import com.google.common.cache.CacheBuilder
import java.awt.Font
import java.awt.geom.AffineTransform
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

object FontCache {
  val SCALE_ADJUST = .88f // a factor to scale fonts from android to Java2d
  val DEFAULT_NAME = "Helvetica"
  val DEFAULT_STYLE = Font.PLAIN
  val FORMATTER = DecimalFormat("###.00")

  private val ourFontCache = CacheBuilder.newBuilder()
      .maximumSize(100)
      .expireAfterAccess(10, TimeUnit.SECONDS)
      .build<String, Font>()

  fun getFont(size: Int, scale: Float): Font {
    return getFont(size, scale, DEFAULT_NAME)
  }

  fun getFont(size: Int, scale: Float, name: String): Font {
    val key = FORMATTER.format(scale * size) + " " + name

    // Convert to swing size font
    return ourFontCache.get(key) {
      Font(name, DEFAULT_STYLE, size)
          .deriveFont(AffineTransform.getScaleInstance((scale * SCALE_ADJUST).toDouble(), (scale * SCALE_ADJUST).toDouble()))
    }
  }
}
