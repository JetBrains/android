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

import com.android.tools.idea.avdmanager.ui.AvdScreenData;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link AvdScreenData}
 */
public class AvdScreenDataTest {

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
