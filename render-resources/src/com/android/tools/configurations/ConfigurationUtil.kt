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
import com.android.sdklib.AndroidCoordinate
import com.android.sdklib.AndroidDpCoordinate
import kotlin.math.roundToInt

/**
 * Represents the width and height of a device in pixels or dps.
 */
data class DeviceSize(val width: Int, val height: Int)

/**
 * Calculates the width and height based on the given x and y dimensions and the screen orientation.
 */
private fun calculateDimensions(
  x: Int,
  y: Int,
  mScreenOrientation: ScreenOrientation?,
): DeviceSize {
  // Determine if the desired orientation needs a swap.
  val shouldSwapDimensions = (x > y) != (mScreenOrientation == ScreenOrientation.LANDSCAPE)

  return if (shouldSwapDimensions) {
    DeviceSize(y, x)
  } else {
    DeviceSize(x, y)
  }
}

/** Calculates the current width and height in PX for the given Configuration. */
@AndroidCoordinate
fun Configuration.deviceSizePx(): DeviceSize {
  val deviceState = deviceState ?: return DeviceSize(0, 0)
  val orientation = deviceState.orientation
  val x = deviceState.hardware.screen.xDimension
  val y = deviceState.hardware.screen.yDimension
  return calculateDimensions(x, y, orientation)
}

/** Calculates the current width and height in DP for the given Configuration. */
@AndroidDpCoordinate
fun Configuration.deviceSizeDp(): DeviceSize {
  val deviceState = deviceState ?: return DeviceSize(0, 0)
  val orientation = deviceState.orientation
  val x =
    ConversionUtil.pxToDp(
      deviceState.hardware.screen.xDimension,
      dpi(),
    )
  val y =
    ConversionUtil.pxToDp(
      deviceState.hardware.screen.yDimension,
      dpi(),
    )
  return calculateDimensions(x, y, orientation)
}

/** Returns the [Configuration]'s density dpi. */
fun Configuration.dpi(): Int = deviceState?.hardware?.screen?.pixelDensity?.dpiValue ?: 0

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
