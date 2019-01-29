/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.idea.observable.constraints;
import org.junit.Test;

import com.android.resources.*;
import com.android.tools.idea.avdmanager.AvdScreenData;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link com.android.tools.idea.avdmanager.AvdScreenData}
 */
public class AvdScreenDataTest {

  @Test
  public void testGetScreenDensity() throws Exception {

    // Recommended densities
    // From https://developer.android.com/guide/practices/screens_support.html
    assertThat( AvdScreenData.getScreenDensity(null, false,  120.0, 1080) ).isEqualTo(Density.LOW);
    assertThat( AvdScreenData.getScreenDensity(null, false,  160.0, 1080) ).isEqualTo(Density.MEDIUM);
    assertThat( AvdScreenData.getScreenDensity(null, false,  240.0, 1080) ).isEqualTo(Density.HIGH);
    assertThat( AvdScreenData.getScreenDensity(null, false,  320.0, 1080) ).isEqualTo(Density.XHIGH);
    assertThat( AvdScreenData.getScreenDensity(null, false,  480.0, 1080) ).isEqualTo(Density.XXHIGH);
    assertThat( AvdScreenData.getScreenDensity(null, false,  640.0, 1080) ).isEqualTo(Density.XXXHIGH);

    assertThat( AvdScreenData.getScreenDensity(null, false, 2048.0, 1080) ).isEqualTo(Density.XXXHIGH); // The maximum (for now)

    // Non-recommended densities
    // From https://developer.android.com/reference/android/util/DisplayMetrics.html#DENSITY_280
    assertThat( AvdScreenData.getScreenDensity(null, false, 279.5, 720) ).isEqualTo(Density.HIGH);    // Not DPI_280
    assertThat( AvdScreenData.getScreenDensity(null, false, 360.0, 720) ).isEqualTo(Density.XHIGH);   // Not DPI_360
    assertThat( AvdScreenData.getScreenDensity(null, false, 399.5, 720) ).isEqualTo(Density.XHIGH);   // Not DPI_400
    assertThat( AvdScreenData.getScreenDensity(null, false, 420.0, 720) ).isEqualTo(Density.XXHIGH);  // Not DPI_420
    assertThat( AvdScreenData.getScreenDensity(null, false, 560.5, 720) ).isEqualTo(Density.XXXHIGH); // Not DPI_560

    // Special densities for special devices
    assertThat( AvdScreenData.getScreenDensity("pixel", false, 0.0, 0) ).isEqualTo(Density.DPI_420);
    assertThat( AvdScreenData.getScreenDensity("pixel 2", false, 0.0, 0) ).isEqualTo(Density.DPI_420);
    assertThat( AvdScreenData.getScreenDensity("pixel_xl", false, 0.0, 0) ).isEqualTo(Density.DPI_560);
    assertThat( AvdScreenData.getScreenDensity("pixel_2_xl", false, 0.0, 0) ).isEqualTo(Density.DPI_560);
    assertThat( AvdScreenData.getScreenDensity("Nexus 5X", false, 0.0, 0) ).isEqualTo(Density.DPI_420);
    assertThat( AvdScreenData.getScreenDensity("Nexus 6", false, 0.0, 0) ).isEqualTo(Density.DPI_560);
    assertThat( AvdScreenData.getScreenDensity("Nexus 6P", false, 0.0, 0) ).isEqualTo(Density.DPI_560);

    // TV densities
    // From https://developer.android.com/reference/android/util/DisplayMetrics.html#DENSITY_TV
    assertThat( AvdScreenData.getScreenDensity(null, true, 480.0,  720) ).isEqualTo(Density.TV);
    assertThat( AvdScreenData.getScreenDensity(null, true, 480.0, 1080) ).isEqualTo(Density.XHIGH);
    assertThat( AvdScreenData.getScreenDensity(null, true, 480.0, 2048) ).isEqualTo(Density.XHIGH); // May change in the future
  }

  // Helper function
  static private Double pythag(Double ww, Double hh) {
    return Math.sqrt(ww * ww + hh * hh);
  }

  @Test
  public void testCalculateDpi() throws Exception {
    final Double TOLERANCE = 0.01;

    Double dpi;

    dpi = AvdScreenData.calculateDpi(1080.0, 1920.0, 5.5, false);
    assertThat(dpi).isWithin(TOLERANCE).of(pythag(1080.0, 1920.0) / 5.5);

    dpi = AvdScreenData.calculateDpi(1920.0, 1080.0, 5.5, false);
    assertThat(dpi).isWithin(TOLERANCE).of(pythag(1080.0, 1920.0) / 5.5);

    dpi = AvdScreenData.calculateDpi(400.0, 400.0, 1.5, false);
    assertThat(dpi).isWithin(TOLERANCE).of(pythag(400.0, 400.0) / 1.5);

    dpi = AvdScreenData.calculateDpi(400.0, 400.0, 1.5, true);
    assertThat(dpi).isWithin(TOLERANCE).of(400.0 / 1.5);
  }
}
