/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.configurations


import com.android.tools.configurations.ConversionUtil.dpToPx
import com.android.tools.configurations.ConversionUtil.pxToDp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConversionUtilsTest {

  @Test
  fun `dpToPx should convert dp to pixels correctly`() {
    // Test case 1: Standard conversion
    assertThat(dpToPx(dp = 10, density = 160)).isEqualTo(10)

    // Test case 2: Higher density
    assertThat(dpToPx(dp = 10, density = 320)).isEqualTo(20)

    // Test case 3: Lower density
    assertThat(dpToPx(dp = 20, density = 80)).isEqualTo(10)

    // Test case 4: Zero dp
    assertThat(dpToPx(dp = 0, density = 160)).isEqualTo(0)

    // Test case 5: Fractional result (rounding)
    assertThat(dpToPx(dp = 10, density = 240)).isEqualTo(15) // 10 * 240 / 160 = 15.0
    assertThat(dpToPx(dp = 7, density = 200))
      .isEqualTo(9) // 7 * 200 / 160 = 8.75f -> roundToInt() = 9
  }

  @Test
  fun `pxToDp should convert pixels to dp correctly`() {
    // Test case 1: Standard conversion
    assertThat(pxToDp(px = 10, density = 160)).isEqualTo(10)

    // Test case 2: Higher density (meaning fewer dp for same pixels)
    assertThat(pxToDp(px = 20, density = 320)).isEqualTo(10)

    // Test case 3: Lower density (meaning more dp for same pixels)
    assertThat(pxToDp(px = 10, density = 80)).isEqualTo(20)

    // Test case 4: Zero px
    assertThat(pxToDp(px = 0, density = 160)).isEqualTo(0)

    // Test case 5: Fractional result (rounding)
    assertThat(pxToDp(px = 15, density = 240))
      .isEqualTo(10) // 15 * 160 / 240 = 10.0f -> roundToInt() = 10
    assertThat(pxToDp(px = 9, density = 200))
      .isEqualTo(7) // 9 * 160 / 200 = 7.2f -> roundToInt() = 7
  }

  @Test
  fun `dpToPx and pxToDp should be inverse with rounding`() {
    val originalDp = 17
    val density = 240 // A density that might cause rounding

    val px = dpToPx(originalDp, density)
    val convertedDp = pxToDp(px, density)

    // With rounding:
    // dpToPx(17, 240) = (17 * 240 / 160f) = 25.5f.roundToInt() = 26
    // pxToDp(26, 240) = (26 * 160 / 240f) = 17.333f.roundToInt() = 17
    assertThat(px).isEqualTo(26)
    assertThat(convertedDp).isEqualTo(originalDp)

    val originalDp2 = 16
    val px2 = dpToPx(originalDp2, density) // (16 * 240 / 160f) = 24f.roundToInt() = 24
    val convertedDp2 = pxToDp(px2, density) // (24 * 160 / 240f) = 16f.roundToInt() = 16
    assertThat(px2).isEqualTo(24)
    assertThat(convertedDp2).isEqualTo(originalDp2)

    // Test case 3: Different density, potentially causing rounding
    val originalDp3 = 23
    val density3 = 120 // ldpi
    // dpToPx(23, 120) = (23 * 120 / 160f) = 17.25f.roundToInt() = 17
    // pxToDp(17, 120) = (17 * 160 / 120f) = 22.666f.roundToInt() = 23
    val px3 = dpToPx(originalDp3, density3)
    val convertedDp3 = pxToDp(px3, density3)
    assertThat(px3).isEqualTo(17)
    assertThat(convertedDp3).isEqualTo(originalDp3)

    // Test case 4: Another density, different rounding
    val originalDp4 = 55
    val density4 = 480 // xxxhdpi
    // dpToPx(55, 480) = (55 * 480 / 160f) = 165f.roundToInt() = 165
    // pxToDp(165, 480) = (165 * 160 / 480f) = 55f.roundToInt() = 55
    val px4 = dpToPx(originalDp4, density4)
    val convertedDp4 = pxToDp(px4, density4)
    assertThat(px4).isEqualTo(165)
    assertThat(convertedDp4).isEqualTo(originalDp4)

    // Test case 5: Zero dp
    assertThat(pxToDp(dpToPx(0, 320), 320)).isEqualTo(0)
  }

  @Test
  fun `pxToDp and dpToPx should be inverse with rounding`() {
    // Test case 1: Standard density
    val originalPx1 = 100
    val density1 = 160 // mdpi
    // pxToDp(100, 160) = (100 * 160 / 160f) = 100f.roundToInt() = 100
    // dpToPx(100, 160) = (100 * 160 / 160f) = 100f.roundToInt() = 100
    val dp1 = pxToDp(originalPx1, density1)
    val convertedPx1 = dpToPx(dp1, density1)
    assertThat(dp1).isEqualTo(100)
    assertThat(convertedPx1).isEqualTo(originalPx1)

    // Test case 2: Higher density, potential rounding
    val originalPx2 = 150
    val density2 = 320 // xhdpi
    // pxToDp(150, 320) = (150 * 160 / 320f) = 75f.roundToInt() = 75
    // dpToPx(75, 320) = (75 * 320 / 160f) = 150f.roundToInt() = 150
    val dp2 = pxToDp(originalPx2, density2)
    val convertedPx2 = dpToPx(dp2, density2)
    assertThat(dp2).isEqualTo(75)
    assertThat(convertedPx2).isEqualTo(originalPx2)

    // Test case 3: Lower density, different rounding
    val originalPx3 = 25
    val density3 = 120 // ldpi
    // pxToDp(25, 120) = (25 * 160 / 120f) = 33.333f.roundToInt() = 33
    // dpToPx(33, 120) = (33 * 120 / 160f) = 24.75f.roundToInt() = 25
    val dp3 = pxToDp(originalPx3, density3)
    val convertedPx3 = dpToPx(dp3, density3)
    assertThat(dp3).isEqualTo(33)
    assertThat(convertedPx3).isEqualTo(originalPx3)
  }
}
