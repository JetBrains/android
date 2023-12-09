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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.Dimension

class GoogleDensityRangeTest {
  @Test
  fun testPixel8Pro() {
    assertThat(GoogleDensityRange.computeDensityRange(Dimension(1344, 2992), 480)).containsExactly(408, 480, 544, 608, 672)
  }

  @Test
  fun testPixel6Pro() {
    assertThat(GoogleDensityRange.computeDensityRange(Dimension(1440, 3120), 560)).containsExactly(476, 560, 612, 666, 720)
  }

  @Test
  fun testPixel3() {
    assertThat(GoogleDensityRange.computeDensityRange(Dimension(1080, 2160), 440)).containsExactly(374, 440, 490, 540)
  }
}
