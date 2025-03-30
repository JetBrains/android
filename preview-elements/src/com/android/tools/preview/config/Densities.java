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
package com.android.tools.preview.config;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Densities {
  private static Density[] ourCommonDensities;

  /**
   * Calculate the density resource bucket (the "generalized density")
   * for the device, given its dots-per-inch
   */
  @NonNull
  public static Density getScreenDensity(boolean isTv, double dpi, int screenHeight) {
    return lookupDensity(isTv, dpi, screenHeight, Density.values());
  }

  /**
   * Compute the closest commonly used screen density
   * for the device, given its dots-per-inch.
   * Please so not use this method when the actual device density is known,
   * in that case just use Density.create(dpi).
   */
  @NonNull
  public static Density getCommonScreenDensity(boolean isTv, double dpi, int screenHeight) {
    return lookupDensity(isTv, dpi, screenHeight, getCommonDensities());
  }

  private static Density lookupDensity(boolean isTv, double dpi, int screenHeight, @NonNull Density[] densities) {
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
}
