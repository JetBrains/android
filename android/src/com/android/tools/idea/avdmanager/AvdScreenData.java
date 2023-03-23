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

import com.android.resources.Density;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenRound;
import com.android.resources.ScreenSize;
import com.android.resources.TouchScreen;
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

  public static double calculateDpi(double screenResolutionWidth, double screenResolutionHeight,
                                    double diagonalScreenSize, boolean isRound) {
    double diagonalPixelResolution;
    if (isRound) {
      // Round: The "diagonal" is the same as the diameter.
      // Use the width so we don't have to consider a possible chin.
      diagonalPixelResolution = screenResolutionWidth;
    } else {
      // Calculate diagonal resolution in pixels using the Pythagorean theorem: Dp = (pixelWidth^2 + pixelHeight^2)^1/2
      diagonalPixelResolution = Math.sqrt(Math.pow(screenResolutionWidth, 2) + Math.pow(screenResolutionHeight, 2));
    }
    // Calculate dots per inch: DPI = Dp / diagonalInchSize
    return diagonalPixelResolution / diagonalScreenSize;
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
   * Calculate the density resource bucket (the "generalized density")
   * for the device, given its dots-per-inch
   */
  @NotNull
  public static Density getScreenDensity(boolean isTv, double dpi, int screenHeight) {

    if (isTv) {
      // The 'generalized density' of a TV is based on its
      // vertical resolution
      return (screenHeight <= 720) ? Density.TV : Density.XHIGH;
    }
    // A hand-held device.
    // Search for the density enum whose value is closest to the density of our device.
    Density bucket = Density.MEDIUM;
    double minDifference = Double.MAX_VALUE;
    for (Density bucketDensity : Density.values()) {
      if (!bucketDensity.isValidValueForDevice() || bucketDensity == Density.TV) {
        continue;
      }
      double difference = Math.abs(bucketDensity.getDpiValue() - dpi);
      if (difference < minDifference) {
        minDifference = difference;
        bucket = bucketDensity;
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

    int screenWidth  = myDeviceData.screenResolutionWidth().get();
    int screenHeight = myDeviceData.screenResolutionHeight().get();
    double screenDiagonal = myDeviceData.diagonalScreenSize().get();
    double effectiveDiagonal = screenDiagonal;
    if (myDeviceData.isScreenRound().get()) {
      // For round devices, compute the diagonal of
      // the enclosing square.
      effectiveDiagonal *= Math.sqrt(2.0);
    }

    screen.setDiagonalLength(screenDiagonal);
    screen.setSize(ScreenSize.getScreenSize(effectiveDiagonal));
    screen.setXDimension(screenWidth);
    screen.setYDimension(screenHeight);
    screen.setFoldedXOffset(myDeviceData.screenFoldedXOffset().get());
    screen.setFoldedYOffset(myDeviceData.screenFoldedYOffset().get());
    screen.setFoldedWidth(myDeviceData.screenFoldedWidth().get());
    screen.setFoldedHeight(myDeviceData.screenFoldedHeight().get());
    screen.setFoldedXOffset2(myDeviceData.screenFoldedXOffset2().get());
    screen.setFoldedYOffset2(myDeviceData.screenFoldedYOffset2().get());
    screen.setFoldedWidth2(myDeviceData.screenFoldedWidth2().get());
    screen.setFoldedHeight2(myDeviceData.screenFoldedHeight2().get());
    screen.setFoldedXOffset3(myDeviceData.screenFoldedXOffset3().get());
    screen.setFoldedYOffset3(myDeviceData.screenFoldedYOffset3().get());
    screen.setFoldedWidth3(myDeviceData.screenFoldedWidth3().get());
    screen.setFoldedHeight3(myDeviceData.screenFoldedHeight3().get());


    screen.setRatio(getScreenRatio(screenWidth, screenHeight));

    double dpi = myDeviceData.screenDpi().get();
    if (dpi <= 0) {
      dpi = calculateDpi(screenWidth, screenHeight, screenDiagonal, myDeviceData.isScreenRound().get());
    }

    dpi = Math.round(dpi * 100) / 100.0;
    screen.setYdpi(dpi);
    screen.setXdpi(dpi);

    screen.setPixelDensity(myDeviceData.density().get());
    return screen;
  }
}
