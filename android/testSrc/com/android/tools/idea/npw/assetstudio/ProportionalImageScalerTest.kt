/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ProportionalImageScalerTest {
  private fun assertScaleFactorsMatch(maxAllowedHeight: Int, heightToScaleFactor: Map<Int, Double>) {
    val minHeight = heightToScaleFactor.keys.min()!!
    val maxHeight = heightToScaleFactor.keys.max()!!

    for ((height, expectedScaleFacor) in heightToScaleFactor) {
      val scaleFactor = determineScaleFactor(height, minHeight, maxHeight, maxAllowedHeight)
      assertThat(scaleFactor).isEqualTo(expectedScaleFacor)
    }
  }

  @Test
  fun determineScaleFactor_varyingHeights() {
    assertScaleFactorsMatch(
      maxAllowedHeight = 5,
      heightToScaleFactor = mapOf(
        2 to 1.0,
        4 to 0.75,
        6 to 2 / 3.0,
        8 to 5 / 8.0
      )
    )
  }

  @Test
  fun determineScaleFactor_minHeightGreaterThanAllowed() {
    assertScaleFactorsMatch(
      maxAllowedHeight = 1,
      heightToScaleFactor = mapOf(
        2 to 0.125,
        4 to 0.125,
        6 to 0.125,
        8 to 0.125
      )
    )
  }

  @Test
  fun determineScaleFactor_minHeightEqualsAllowed() {
    assertScaleFactorsMatch(
      maxAllowedHeight = 2,
      heightToScaleFactor = mapOf(
        2 to 0.25,
        4 to 0.25,
        6 to 0.25,
        8 to 0.25
      )
    )
  }

  @Test
  fun determineScaleFactor_onlyOneHeight() {
    assertScaleFactorsMatch(
      maxAllowedHeight = 5,
      heightToScaleFactor = mapOf(10 to 0.5)
    )
  }
}