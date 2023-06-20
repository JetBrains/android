/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Color
import kotlin.test.assertFailsWith

/** Tests functions in ColorUtils.kt. */
@RunWith(JUnit4::class)
class ColorUtilsTest {
  @Test
  fun interpolate() {
    assertThat(interpolate(Color.BLACK, Color.WHITE, 0.25)).isEqualTo(Color.DARK_GRAY)
    assertThat(interpolate(Color.BLACK, Color.WHITE, 0.5)).isEqualTo(Color.GRAY)
    assertThat(interpolate(Color.BLACK, Color.WHITE, 0.753)).isEqualTo(Color.LIGHT_GRAY)
    assertThat(interpolate(Color.RED, Color.YELLOW, 0.785)).isEqualTo(Color.ORANGE)
    assertThat(interpolate(Color.RED, Color.WHITE, 0.685)).isEqualTo(Color.PINK)
    assertThat(interpolate(Color.MAGENTA, Color.CYAN, 0.5)).isEqualTo(
      interpolate(Color.BLUE, Color.WHITE, 0.5))
  }

  @Test
  fun interpolate_withAlpha() {
    val c1 = Color(0, 0, 0, 100)
    val c2 = Color(10, 70, 30, 20)
    assertThat(interpolate(c1, c2, 0.5)).isEqualTo(Color(5, 35, 15, 60))
  }

  @Test
  fun interpolate_multipleColors() {
    val gradient = listOf(Color.BLACK, Color.WHITE, Color.RED)
    assertThat(interpolate(gradient, -1.0)).isEqualTo(Color.BLACK)
    assertThat(interpolate(gradient, 5.0)).isEqualTo(Color.RED)
    assertThat(interpolate(gradient, 0.25)).isEqualTo(Color.GRAY)
    assertThat(interpolate(gradient, 0.6575)).isEqualTo(Color.PINK)
  }

  @Test
  fun interpolate_singletonList() {
    assertThat(interpolate(listOf(Color.ORANGE), 0.5)).isEqualTo(Color.ORANGE)
  }

  @Test
  fun interpolate_emptyList_throws() {
    assertFailsWith<IllegalArgumentException> { interpolate(listOf(), 0.5) }
  }
}