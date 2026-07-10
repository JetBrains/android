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
package com.android.tools.rendering

import com.android.ide.common.rendering.api.RenderSizeProvider
import java.awt.Dimension
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ConstrainedRenderSizeProvider(private val maxImageSize: Long, private val qualityProvider: () -> Float) : RenderSizeProvider {
  override fun getTargetSize(width: Int, height: Int): Dimension {
    val wantedImageSize: Long = maxOf(1, width.toLong() * height.toLong())
    // First adjust dimensions according to quality multiplier. Each dimension is scaled using
    // `sqrt(quality)`, so that  the whole image is scaled with `quality` as a consequence.
    val quality = qualityProvider().toDouble().coerceIn(1e-6, 1.0)
    val qualityDimensionScale: Double = sqrt(quality)

    // Also use maxImageSize as absolute upper bound
    val maxSizeDimensionScale: Double = sqrt(maxImageSize.toDouble() / wantedImageSize)

    // Use the tighter bound (quality vs max size)
    val dimensionScale: Double = min(qualityDimensionScale, maxSizeDimensionScale)
    val downscaleWidth: Double = width * dimensionScale
    val downscaleHeight: Double = height * dimensionScale

    // Make sure both dimensions are positive integers
    val w = max(1, downscaleWidth.toInt())
    val h = max(1, downscaleHeight.toInt())
    return Dimension(w, h)
  }
}
