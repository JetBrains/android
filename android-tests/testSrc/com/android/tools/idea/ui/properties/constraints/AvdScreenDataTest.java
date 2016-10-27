/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.idea.ui.properties.constraints;
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
    assertThat( AvdScreenData.getScreenDensity(false,  120.0, 1080) ).isEqualTo(Density.LOW);
    assertThat( AvdScreenData.getScreenDensity(false,  160.0, 1080) ).isEqualTo(Density.MEDIUM);
    assertThat( AvdScreenData.getScreenDensity(false,  240.0, 1080) ).isEqualTo(Density.HIGH);
    assertThat( AvdScreenData.getScreenDensity(false,  320.0, 1080) ).isEqualTo(Density.XHIGH);
    assertThat( AvdScreenData.getScreenDensity(false,  480.0, 1080) ).isEqualTo(Density.XXHIGH);
    assertThat( AvdScreenData.getScreenDensity(false,  640.0, 1080) ).isEqualTo(Density.XXXHIGH);

    assertThat( AvdScreenData.getScreenDensity(false, 2048.0, 1080) ).isEqualTo(Density.XXXHIGH); // The maximum (for now)

    // Non-recommended densities
    // From https://developer.android.com/reference/android/util/DisplayMetrics.html#DENSITY_280
    assertThat( AvdScreenData.getScreenDensity(false, 279.5, 720) ).isEqualTo(Density.HIGH);    // Not DPI_280
    assertThat( AvdScreenData.getScreenDensity(false, 360.0, 720) ).isEqualTo(Density.XHIGH);   // Not DPI_360
    assertThat( AvdScreenData.getScreenDensity(false, 399.5, 720) ).isEqualTo(Density.XHIGH);   // Not DPI_400
    assertThat( AvdScreenData.getScreenDensity(false, 420.0, 720) ).isEqualTo(Density.XXHIGH);  // Not DPI_420
    assertThat( AvdScreenData.getScreenDensity(false, 560.5, 720) ).isEqualTo(Density.XXXHIGH); // Not DPI_560

    // TV densities
    // From https://developer.android.com/reference/android/util/DisplayMetrics.html#DENSITY_TV
    assertThat( AvdScreenData.getScreenDensity(true, 480.0,  720) ).isEqualTo(Density.TV);
    assertThat( AvdScreenData.getScreenDensity(true, 480.0, 1080) ).isEqualTo(Density.XHIGH);
    assertThat( AvdScreenData.getScreenDensity(true, 480.0, 2048) ).isEqualTo(Density.XHIGH); // May change in the future
  }
}
