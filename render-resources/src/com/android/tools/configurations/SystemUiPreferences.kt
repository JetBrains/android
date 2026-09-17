/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.configurations

import com.android.tools.configurations.Configuration.ImageTransformationType
import com.android.tools.res.FrameworkOverlay
import java.awt.image.BufferedImage
import java.util.EnumMap
import java.util.function.Consumer

/** Encapsulates the specific System UI and display rendering settings for a Configuration. */
class SystemUiPreferences {
  var fontScale: Float = 1f
  var adaptiveShape: AdaptiveIconShape = AdaptiveIconShape.getDefaultShape()
  var useThemedIcon: Boolean = false
  var wallpaper: Wallpaper? = null
  private val imageTransformations = EnumMap<ImageTransformationType, Consumer<BufferedImage?>>(ImageTransformationType::class.java)
  var isGestureNav: Boolean = true
  var isEdgeToEdge: Boolean = true
  var cutoutOverlay: FrameworkOverlay = FrameworkOverlay.CUTOUT_NONE
  var deviceOverlay: FrameworkOverlay? = null

  fun setImageTransformation(type: ImageTransformationType, transformation: Consumer<BufferedImage?>?) {
    if (transformation == null) {
      imageTransformations.remove(type)
    } else {
      imageTransformations.put(type, transformation)
    }
  }

  val imageTransformation: Consumer<BufferedImage?>?
    get() {
      if (imageTransformations.isEmpty()) return null
      return Consumer { image: BufferedImage? -> imageTransformations.values.forEach { c -> c.accept(image) } }
    }

  fun copyFrom(other: SystemUiPreferences) {
    this.fontScale = other.fontScale
    this.adaptiveShape = other.adaptiveShape
    this.useThemedIcon = other.useThemedIcon
    this.wallpaper = other.wallpaper
    this.isGestureNav = other.isGestureNav
    this.isEdgeToEdge = other.isEdgeToEdge
    this.cutoutOverlay = other.cutoutOverlay
    this.deviceOverlay = other.deviceOverlay
    // Intentionally missing imageTransformations to preserve exact original backward-compatibility
  }
}
