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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Contains all methods needed to build a {@link Screen} instance.
 */
public final class AvdScreenData {
  private AvdDeviceData myDeviceData;
  private static Density[] ourCommonDensities;

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
   * Calculate the density resource bucket (the "generalized density")
   * for the device, given its dots-per-inch
   */
  @NotNull
  public static Density getScreenDensity(boolean isTv, double dpi, int screenHeight) {
    return lookupDensity(isTv, dpi, screenHeight, Density.values());
  }

  /**
   * Compute the closest commonly used screen density
   * for the device, given its dots-per-inch.
   * Please so not use this method when the actual device density is known,
   * in that case just use Density.create(dpi).
   */
  @NotNull
  public static Density getCommonScreenDensity(boolean isTv, double dpi, int screenHeight) {
    return lookupDensity(isTv, dpi, screenHeight, getCommonDensities());
  }

  private static Density lookupDensity(boolean isTv, double dpi, int screenHeight, @NotNull Density[] densities) {
    if (isTv) {
      // The 'generalized density' of a TV is based on its
      // vertical resolution
      return (screenHeight <= 720) ? Density.TV : Density.XHIGH;
    }
    // A hand-held device.
    // Search for the density enum whose value is closest to the density of our device.
    Density bucket = Density.MEDIUM;
    double minDifference = Double.MAX_VALUE;
    for (Density bucketDensity : densities) {
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

  private static Density[] getCommonDensities() {
    if (ourCommonDensities == null) {
      List<Density> densities = new ArrayList<>();
      Collections.addAll(densities, Density.values());
      densities.add(Density.create(560));
      densities.add(Density.create(440));
      densities.add(Density.create(420));
      densities.add(Density.create(360));
      densities.add(Density.create(340));
      densities.add(Density.create(300));
      densities.add(Density.create(280));
      densities.add(Density.create(260));
      densities.add(Density.create(220));
      densities.add(Density.create(200));
      densities.add(Density.create(180));
      densities.add(Density.create(140));
      densities.sort(Density::compareTo);
      ourCommonDensities = densities.toArray(new Density[0]);
    }
    return ourCommonDensities;
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


    screen.setRatio(ScreenRatio.create(screenWidth, screenHeight));

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
