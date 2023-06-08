/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.modes.essentials.EssentialsMode
import com.android.tools.idea.preview.RenderQualityPolicy

object ComposePreviewRenderQualityPolicy : RenderQualityPolicy {
  override val acceptedErrorMargin = .05f // 5% error margin
  override val debounceTimeMillis = 100L

  /** When the scale is lower than this value, then all previews are treated as not visible. */
  val scaleVisibilityThreshold: Float = 0.2f
  val lowestQuality: Float = 0.001f

  override fun getTargetQuality(scale: Double, isVisible: Boolean): Float {
    if (!isVisible || scale < scaleVisibilityThreshold) {
      return lowestQuality
    }
    return scale.toFloat().coerceAtMost(getDefaultPreviewQuality()).coerceAtLeast(lowestQuality)
  }
}

internal fun getDefaultPreviewQuality() = if (EssentialsMode.isEnabled()) 0.75f else 0.95f
