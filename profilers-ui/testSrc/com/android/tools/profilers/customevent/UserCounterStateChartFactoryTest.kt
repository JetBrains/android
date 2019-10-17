/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.customevent

import com.android.tools.profilers.ProfilerColors
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Test that the UserCounterStateChartFactory returns the correct color for the corresponding number of user counters occurring.
 */
class UserCounterStateChartFactoryTest {

  private val colorProvider = UserCounterStateChartFactory.getDurationStateColorProvider()

  @Test
  fun testNoneColor() {
    assertThat(colorProvider.getColor(true, 0)).isEqualTo(ProfilerColors.USER_COUNTER_EVENT_NONE)
  }

  @Test
  fun testLightColor() {
    assertThat(colorProvider.getColor(true, 1)).isEqualTo(ProfilerColors.USER_COUNTER_EVENT_LIGHT)
    assertThat(colorProvider.getColor(true, 3)).isEqualTo(ProfilerColors.USER_COUNTER_EVENT_LIGHT)
  }

  @Test
  fun testMediumColor() {
    assertThat(colorProvider.getColor(true, 4)).isEqualTo(ProfilerColors.USER_COUNTER_EVENT_MED)
    assertThat(colorProvider.getColor(true, 6)).isEqualTo(ProfilerColors.USER_COUNTER_EVENT_MED)
  }

  @Test
  fun testDarkColor() {
    assertThat(colorProvider.getColor(true, 7)).isEqualTo(ProfilerColors.USER_COUNTER_EVENT_DARK)
    assertThat(colorProvider.getColor(true, 12)).isEqualTo(ProfilerColors.USER_COUNTER_EVENT_DARK)
    assertThat(colorProvider.getColor(true, 50)).isEqualTo(ProfilerColors.USER_COUNTER_EVENT_DARK)
  }
}

