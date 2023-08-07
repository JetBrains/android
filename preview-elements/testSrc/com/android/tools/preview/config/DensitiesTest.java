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

import static com.google.common.truth.Truth.assertThat;

import com.android.resources.Density;
import org.junit.Test;

public class DensitiesTest {

  @Test
  public void testGetScreenDensity() {

    assertThat(Densities.getScreenDensity(false, 120.0, 1080) ).isEqualTo(Density.LOW);
    assertThat(Densities.getScreenDensity(false,  160.0, 1080) ).isEqualTo(Density.MEDIUM);
    assertThat(Densities.getScreenDensity(false,  240.0, 1080) ).isEqualTo(Density.HIGH);
    assertThat(Densities.getScreenDensity(false,  320.0, 1080) ).isEqualTo(Density.XHIGH);
    assertThat(Densities.getScreenDensity(false,  480.0, 1080) ).isEqualTo(Density.XXHIGH);
    assertThat(Densities.getScreenDensity(false,  640.0, 1080) ).isEqualTo(Density.XXXHIGH);

    assertThat(Densities.getScreenDensity(false, 2048.0, 1080) ).isEqualTo(Density.XXXHIGH); // The maximum (for now)

    assertThat(Densities.getScreenDensity(false, 279.5, 720) ).isEqualTo(Density.HIGH);
    assertThat(Densities.getScreenDensity(false, 360.0, 720) ).isEqualTo(Density.XHIGH);
    assertThat(Densities.getScreenDensity(false, 399.5, 720) ).isEqualTo(Density.XHIGH);
    assertThat(Densities.getScreenDensity(false, 420.0, 720) ).isEqualTo(Density.XXHIGH);
    assertThat(Densities.getScreenDensity(false, 560.5, 720) ).isEqualTo(Density.XXXHIGH);

    // TV densities
    // From https://developer.android.com/reference/android/util/DisplayMetrics.html#DENSITY_TV
    assertThat(Densities.getScreenDensity(true, 480.0,  720) ).isEqualTo(Density.TV);
    assertThat(Densities.getScreenDensity(true, 480.0, 1080) ).isEqualTo(Density.XHIGH);
    assertThat(Densities.getScreenDensity(true, 480.0, 2048) ).isEqualTo(Density.XHIGH); // May change in the future
  }
}
