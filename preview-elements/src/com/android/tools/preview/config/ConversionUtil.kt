/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.preview.config

import com.android.resources.Density
import kotlin.math.roundToInt

/**
 * Utility object for converting between density-independent pixels (dp) and pixels (px) based on
 * screen density.
 */
object ConversionUtil {
  /** Calculates the pixel value for the given dp value and density. */
  fun dpToPx(dp: Int, density: Int): Int {
    return (1.0f * dp * density / Density.DEFAULT_DENSITY).roundToInt()
  }

  /** Calculates the dp value for the given pixel value and density. */
  fun pxToDp(px: Int, density: Int): Int {
    return (1.0f * px * Density.DEFAULT_DENSITY / density).roundToInt()
  }
}
