/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.resources.Density
import com.android.resources.ScreenRatio
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import com.android.resources.TouchScreen
import com.android.sdklib.devices.Multitouch
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.ScreenType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Contains all methods needed to build a [Screen] instance.
 */
class AvdScreenData(private val deviceData: AvdDeviceData) {
  /**
   * Create a screen based on a reasonable set of defaults and user input.
   */
  fun createScreen(): Screen {
    val screenWidth = deviceData.screenResolutionWidth().get()
    val screenHeight = deviceData.screenResolutionHeight().get()
    val screenDiagonal = deviceData.diagonalScreenSize().get()
    var effectiveDiagonal = screenDiagonal
    if (deviceData.isScreenRound.get()) {
      // For round devices, compute the diagonal of the enclosing square.
      effectiveDiagonal *= sqrt(2.0)
    }
    var dpi = deviceData.screenDpi().get()
    if (dpi <= 0) {
      dpi = calculateDpi(screenWidth.toDouble(), screenHeight.toDouble(), screenDiagonal, deviceData.isScreenRound.get())
    }

    dpi = (dpi * 100).roundToInt() / 100.0

    return Screen().apply {
      multitouch = Multitouch.JAZZ_HANDS
      mechanism = TouchScreen.FINGER
      screenType = ScreenType.CAPACITIVE
      screenRound = if (deviceData.isScreenRound.get()) ScreenRound.ROUND else ScreenRound.NOTROUND
      diagonalLength = screenDiagonal
      size = ScreenSize.getScreenSize(effectiveDiagonal)
      xDimension = screenWidth
      yDimension = screenHeight
      foldedXOffset = deviceData.screenFoldedXOffset().get()
      foldedYOffset = deviceData.screenFoldedYOffset().get()
      foldedWidth = deviceData.screenFoldedWidth().get()
      foldedHeight = deviceData.screenFoldedHeight().get()
      ratio = getScreenRatio(screenWidth, screenHeight)
      ydpi = dpi
      xdpi = dpi
      pixelDensity = getScreenDensity(deviceData.deviceId().get(), deviceData.isTv.get(), dpi, screenHeight)
    }
  }

  companion object {
    fun calculateDpi(screenResolutionWidth: Double, screenResolutionHeight: Double, diagonalScreenSize: Double, isRound: Boolean): Double {
      val diagonalPixelResolution: Double = if (isRound) {
        // Round: The "diagonal" is the same as the diameter.
        // Use the width so we don't have to consider a possible chin.
        screenResolutionWidth
      }
      else {
        // Calculate diagonal resolution in pixels using the Pythagorean theorem: Dp = (pixelWidth^2 + pixelHeight^2)^1/2
        sqrt(screenResolutionWidth.pow(2.0) + screenResolutionHeight.pow(2.0))
      }
      // Calculate dots per inch: DPI = Dp / diagonalInchSize
      return diagonalPixelResolution / diagonalScreenSize
    }

    /**
     * Calculate the screen ratio. Beyond a 5:3 ratio is considered "long"
     */
    @JvmStatic
    fun getScreenRatio(width: Int, height: Int): ScreenRatio {
      val longSide = max(width, height)
      val shortSide = min(width, height)

      // Above a 5:3 ratio is "long"
      return if (longSide.toDouble() / shortSide >= 5.0 / 3) ScreenRatio.LONG else ScreenRatio.NOTLONG
    }

    /**
     * Calculate the density resource bucket (the "generalized density") for the device, given its dots-per-inch.
     */
    @JvmStatic
    fun getScreenDensity(deviceId: String?, isTv: Boolean, dpi: Double, screenHeight: Int): Density {
      if (isTv) {
        // The 'generalized density' of a TV is based on its vertical resolution
        return if (screenHeight <= 720) Density.TV else Density.XHIGH
      }
      // A hand-held device.
      // Check if it uses a "special" density
      val specialDensity = specialDeviceDensity(deviceId)
      if (specialDensity != null) {
        return specialDensity
      }
      // Not "special." Search for the density enum whose value is closest to the density of our device.
      return Density.values()
        .filter { it.isValidValueForDevice && it.isRecommended }
        .minBy { abs(it.dpiValue - dpi) } ?: Density.MEDIUM
    }

    /**
     * A small set of devices use "special" density enumerations.
     * Handle them explicitly.
     */
    private fun specialDeviceDensity(deviceId: String?): Density? = when (deviceId) {
      "Nexus 5X" -> Density.DPI_420
      "Nexus 6" -> Density.DPI_560
      "Nexus 6P" -> Density.DPI_560
      "pixel" -> Density.DPI_420
      "pixel_xl" -> Density.DPI_560
      "pixel 2" -> Density.DPI_420
      "pixel_2_xl" -> Density.DPI_560
      else -> null
    }
  }
}