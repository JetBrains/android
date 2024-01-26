/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.ui.screenshot

import com.android.tools.idea.ui.AndroidAdbUiBundle.message
import com.android.utils.HashCodes
import java.awt.Color
import java.util.Objects

/** Describes additional decorations applied to a screenshot image. */
class ScreenshotDecorationOption
    private constructor(private val clipAction: String?, val background: Color? = null, val framingOption: FramingOption? = null) {

  constructor(framingOption: FramingOption) : this(null, null, framingOption)

  override fun toString(): String {
    return framingOption?.displayName ?: clipAction.toString()
  }

  override fun hashCode(): Int {
    return HashCodes.mix(Objects.hashCode(clipAction), Objects.hashCode(framingOption))
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    other as ScreenshotDecorationOption
    return clipAction == other.clipAction && framingOption == other.framingOption
  }

  @Suppress("UseJBColor") // Use real black color (JBColor.BLACK is grey in dark modes).
  companion object {
    @JvmField val RECTANGULAR = ScreenshotDecorationOption(message("screenshot.decoration.rectangular"), Color.BLACK)
    @JvmField val DISPLAY_SHAPE_CLIP = ScreenshotDecorationOption(message("screenshot.decoration.display.shape"))
    @JvmField val PLAY_COMPATIBLE = ScreenshotDecorationOption(message("screenshot.decoration.play-compatible"), Color.BLACK)
  }
}
