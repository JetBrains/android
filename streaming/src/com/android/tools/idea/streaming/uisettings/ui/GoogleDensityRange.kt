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
package com.android.tools.idea.streaming.uisettings.ui

import com.android.resources.Density
import java.awt.Dimension

// These values are taken from: frameworks/base/packages/SettingsLib/res/values/dimens.xml from the udc-dev branch
private const val DISPLAY_DENSITY_MAX_SCALE = 1.50f
private const val DISPLAY_DENSITY_MIN_SCALE = 0.85f
private const val DISPLAY_DENSITY_MIN_SCALE_INTERVAL = 0.09f

// Constant from: frameworks/base/packages/SettingsLib/src/com/android/settingslib/display/DisplayDensityUtils.java (udc-dev)
private const val MIN_DIMENSION_DP = 320

object GoogleDensityRange {

  /**
   * Compute the available display sizes as it is computed on Google devices.
   * Other OEM vendors may choose to display a different density range in the settings on the device.
   * The code based on: frameworks/base/packages/SettingsLib/src/com/android/settingslib/display/DisplayDensityUtils.java (udc-dev)
   */
  fun computeDensityRange(screenSize: Dimension, physicalDensity: Int): List<Int> {
    val minDimensionPx = minOf(screenSize.width, screenSize.height)
    val maxDensity = Density.MEDIUM.dpiValue * minDimensionPx / MIN_DIMENSION_DP
    val maxScaleDimension = DISPLAY_DENSITY_MAX_SCALE
    val maxScale = minOf(maxScaleDimension, maxDensity / physicalDensity.toFloat())
    val minScale = DISPLAY_DENSITY_MIN_SCALE
    val minScaleInterval = DISPLAY_DENSITY_MIN_SCALE_INTERVAL
    val numLarger = ((maxScale - 1) / minScaleInterval).toInt().coerceIn(0, 3)
    val numSmaller = ((1 - minScale) / minScaleInterval).toInt().coerceIn(0, 1)
    val values = mutableListOf<Int>()
    if (numSmaller > 0) {
      val interval = (1 - minScale) / numSmaller
      for (i in numSmaller downTo 1) {
        values.add((physicalDensity * (1 - i * interval)).toInt() and -2)
      }
    }
    values.add(physicalDensity)
    if (numLarger > 0) {
      val interval = (maxScale - 1) / numLarger
      for (i in 1..numLarger) {
        values.add((physicalDensity * (1 + i * interval)).toInt() and -2)
      }
    }
    return values
  }
}