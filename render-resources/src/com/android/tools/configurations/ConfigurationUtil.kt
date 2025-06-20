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
package com.android.tools.configurations

import com.android.resources.Density
import com.android.resources.ScreenOrientation
import kotlin.math.roundToInt


/**
 * Calculates the width and height based on the given x and y dimensions and the screen orientation.
 */
private fun calculateDimensions(
  x: Int,
  y: Int,
  mScreenOrientation: ScreenOrientation?,
): Pair<Int, Int> {
  // Determine if the desired orientation needs a swap.
  val shouldSwapDimensions = (x > y) != (mScreenOrientation == ScreenOrientation.LANDSCAPE)

  return if (shouldSwapDimensions) {
    Pair(y, x)
  } else {
    Pair(x, y)
  }
}

/** Calculates the current width and height in PX for the given Configuration. */
fun Configuration.deviceSizePx(): Pair<Int, Int> {
  val deviceState = deviceState ?: return Pair(0, 0)
  val orientation = deviceState.orientation
  val x = deviceState.hardware.screen.xDimension
  val y = deviceState.hardware.screen.yDimension
  return calculateDimensions(x, y, orientation)
}

/** Calculates the current width and height in DP for the given Configuration. */
fun Configuration.deviceSizeDp(): Pair<Int, Int> {
  val deviceState = deviceState ?: return Pair(0, 0)
  val orientation = deviceState.orientation
  val x =
    ConversionUtil.pxToDp(
      deviceState.hardware.screen.xDimension,
      deviceState.hardware.screen.pixelDensity.dpiValue,
    )
  val y =
    ConversionUtil.pxToDp(
      deviceState.hardware.screen.yDimension,
      deviceState.hardware.screen.pixelDensity.dpiValue,
    )
  return calculateDimensions(x, y, orientation)
}


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
