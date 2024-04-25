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
package com.android.tools.idea.preview

import com.android.tools.idea.flags.StudioFlags

/**
 * Default implementation of [RenderQualityPolicy] . A [screenScalingFactorProvider] is needed for
 * the [scaleVisibilityThreshold] to work correctly. Also note that it needs to be a provider and
 * not a fixed value for the policy to properly work and get dynamically adjusted when the user has
 * different screens with different scale factors.
 */
class DefaultRenderQualityPolicy(val screenScalingFactorProvider: () -> Double) :
  RenderQualityPolicy {
  companion object {
    /** When the scale is lower than this value, then all previews are treated as not visible. */
    val scaleVisibilityThreshold: Float
      get() =
        (StudioFlags.PREVIEW_RENDER_QUALITY_VISIBILITY_THRESHOLD.get() / 100f)
          .coerceAtLeast(0f)
          .coerceAtMost(1f)

    const val lowestQuality: Float = 0.001f
  }

  /**
   * When false, then the target quality of all the previews configured following this policy
   * instance will be [lowestQuality].
   *
   * This is expected to be used for considering the lifecycle of the components where this policy
   * is used, to make sure that deactivated previews, that could be reactivated later, get
   * temporarily rendered in low quality.
   */
  private var active: Boolean = true
  override val acceptedErrorMargin = .05f // 5% error margin
  override val debounceTimeMillis: Long
    get() = StudioFlags.PREVIEW_RENDER_QUALITY_DEBOUNCE_TIME.get().coerceAtLeast(1)

  override fun getTargetQuality(scale: Double, isVisible: Boolean): Float {
    if (!active || !isVisible || scale * screenScalingFactorProvider() < scaleVisibilityThreshold) {
      return lowestQuality
    }
    return scale.toFloat().coerceAtMost(getDefaultPreviewQuality()).coerceAtLeast(lowestQuality)
  }

  fun activate() {
    active = true
  }

  fun deactivate() {
    active = false
  }
}
