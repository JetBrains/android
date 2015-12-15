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
package com.android.tools.idea.avdmanager;

import com.android.resources.*;
import com.android.sdklib.devices.Multitouch;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.ScreenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Contains all methods needed to build a {@link Screen} instance.
 */
public final class AvdScreenData {
  private AvdDeviceData myDeviceData;

  public AvdScreenData(AvdDeviceData deviceData) {
    myDeviceData = deviceData;
  }

  public static double calculateDpi(double screenResolutionWidth, double screenResolutionHeight, double diagonalScreenSize) {
    // Calculate diagonal resolution in pixels using the Pythagorean theorem: Dp = (pixelWidth^2 + pixelHeight^2)^1/2
    double diagonalPixelResolution = Math.sqrt(Math.pow(screenResolutionWidth, 2) + Math.pow(screenResolutionHeight, 2));
    // Calculate dos per inch: DPI = Dp / diagonalInchSize
    return diagonalPixelResolution / diagonalScreenSize;
  }

  /**
   * Get the resource bucket value that corresponds to the given size in inches.
   *
   * @param diagonalSize Diagonal Screen size in inches.
   *                     If null, a default diagonal size is used
   */
  @NotNull
  public static ScreenSize getScreenSize(@Nullable Double diagonalSize) {
    if (diagonalSize == null) {
      return ScreenSize.NORMAL;
    }

    /**
     * Density-independent pixel (dp) : The density-independent pixel is
     * equivalent to one physical pixel on a 160 dpi screen,
     * which is the baseline density assumed by the system for a
     * "medium" density screen.
     *
     * Taken from http://developer.android.com/guide/practices/screens_support.html
     */
    double diagonalDp = 160.0 * diagonalSize;

    // Set the Screen Size
    if (diagonalDp >= 1200) {
      return ScreenSize.XLARGE;
    }
    else if (diagonalDp >= 800) {
      return ScreenSize.LARGE;
    }
    else if (diagonalDp >= 568) {
      return ScreenSize.NORMAL;
    }
    else {
      return ScreenSize.SMALL;
    }
  }

  /**
   * Calculate the screen ratio. Beyond a 5:3 ratio is considered "long"
   */
  @NotNull
  public static ScreenRatio getScreenRatio(int width, int height) {
    int longSide = Math.max(width, height);
    int shortSide = Math.min(width, height);

    // Above a 5:3 ratio is "long"
    if (((double)longSide) / shortSide >= 5.0 / 3) {
      return ScreenRatio.LONG;
    }
    else {
      return ScreenRatio.NOTLONG;
    }
  }

  /**
   * Calculate the density resource bucket for the given dots-per-inch
   */
  @NotNull
  public static Density getScreenDensity(double dpi) {
    double minDifference = Double.MAX_VALUE;
    Density bucket = Density.MEDIUM;
    for (Density d : Density.values()) {
      if (!d.isValidValueForDevice()) {
        continue;
      }
      // Search for the density enum whose value is closest to the density of our device.
      double difference = Math.abs(d.getDpiValue() - dpi);
      if (difference < minDifference) {
        minDifference = Math.abs(d.getDpiValue() - dpi);
        bucket = d;
      }
    }
    return bucket;
  }

  /**
   * Create a screen based on a reasonable set of defaults and user input.
   */
  @NotNull
  public Screen createScreen() {
    Screen screen = new Screen();
    screen.setMultitouch(Multitouch.JAZZ_HANDS);
    screen.setMechanism(TouchScreen.FINGER);
    screen.setScreenType(ScreenType.CAPACITIVE);

    screen.setScreenRound((myDeviceData.isScreenRound().get()) ? ScreenRound.ROUND : ScreenRound.NOTROUND);

    screen.setDiagonalLength(myDeviceData.diagonalScreenSize().get());
    screen.setSize(getScreenSize(myDeviceData.diagonalScreenSize().get()));

    screen.setXDimension(myDeviceData.screenResolutionWidth().get());
    screen.setYDimension(myDeviceData.screenResolutionHeight().get());

    screen.setRatio(getScreenRatio(myDeviceData.screenResolutionWidth().get(), myDeviceData.screenResolutionHeight().get()));

    Double dpi = myDeviceData.screenDpi().get();
    if (dpi <= 0) {
      dpi = calculateDpi(myDeviceData.screenResolutionWidth().get(), myDeviceData.screenResolutionHeight().get(),
                         myDeviceData.diagonalScreenSize().get());
    }

    dpi = Math.round(dpi * 100) / 100.0;
    screen.setYdpi(dpi);
    screen.setXdpi(dpi);

    if (myDeviceData.isTv().get()) {
      // TVs can have varied densities, including much lower than the normal range.
      // Set the density explicitly in that case.
      screen.setPixelDensity(Density.TV);
    }
    else {
      screen.setPixelDensity(getScreenDensity(dpi));
    }

    return screen;
  }
}
