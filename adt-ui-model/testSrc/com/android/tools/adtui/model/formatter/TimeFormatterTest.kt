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
package com.android.tools.adtui.model.formatter

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

class TimeFormatterTest {

  @Test
  fun testFullClockString() {
    val timestamp1 = TimeUnit.HOURS.toMicros(1) + TimeUnit.MINUTES.toMicros(2) +
                     TimeUnit.SECONDS.toMicros(11) + TimeUnit.MILLISECONDS.toMicros(25);
    assertThat(TimeFormatter.getFullClockString(timestamp1)).isEqualTo("01:02:11.025")
    val timestamp2 = timestamp1 - TimeUnit.HOURS.toMicros(1)
    assertThat(TimeFormatter.getFullClockString(timestamp2)).isEqualTo("00:02:11.025")
    val timestamp3 = timestamp2 - TimeUnit.MINUTES.toMicros(2)
    assertThat(TimeFormatter.getFullClockString(timestamp3)).isEqualTo("00:00:11.025")
    val timestamp4 = timestamp3 - TimeUnit.SECONDS.toMicros(11)
    assertThat(TimeFormatter.getFullClockString(timestamp4)).isEqualTo("00:00:00.025")
    val timestamp5 = 35L
    assertThat(TimeFormatter.getFullClockString(timestamp5)).isEqualTo("00:00:00.000")
    val timestamp6 = 0L
    assertThat(TimeFormatter.getFullClockString(timestamp6)).isEqualTo("00:00:00.000")
  }

  @Test
  fun testSemiSimplifiedClockString() {
    val timestamp1 = TimeUnit.HOURS.toMicros(1) + TimeUnit.MINUTES.toMicros(2) +
                     TimeUnit.SECONDS.toMicros(11) + TimeUnit.MILLISECONDS.toMicros(25);
    assertThat(TimeFormatter.getSemiSimplifiedClockString(timestamp1)).isEqualTo("01:02:11.025")
    val timestamp2 = timestamp1 - TimeUnit.HOURS.toMicros(1)
    assertThat(TimeFormatter.getSemiSimplifiedClockString(timestamp2)).isEqualTo("00:02:11.025")
    val timestamp3 = timestamp2 - TimeUnit.MINUTES.toMicros(2)
    assertThat(TimeFormatter.getSemiSimplifiedClockString(timestamp3)).isEqualTo("00:11.025")
    val timestamp4 = timestamp3 - TimeUnit.SECONDS.toMicros(11)
    assertThat(TimeFormatter.getSemiSimplifiedClockString(timestamp4)).isEqualTo("00:00.025")
  }

  @Test
  fun testSimplifiedClockString() {
    val timestamp1 = TimeUnit.HOURS.toMicros(1) + TimeUnit.MINUTES.toMicros(2) +
                     TimeUnit.SECONDS.toMicros(11) + TimeUnit.MILLISECONDS.toMicros(25);
    assertThat(TimeFormatter.getSimplifiedClockString(timestamp1)).isEqualTo("01:02:11.025")
    val timestamp2 = timestamp1 - TimeUnit.HOURS.toMicros(1)
    assertThat(TimeFormatter.getSimplifiedClockString(timestamp2)).isEqualTo("02:11.025")
    val timestamp3 = timestamp2 - TimeUnit.MINUTES.toMicros(2)
    assertThat(TimeFormatter.getSimplifiedClockString(timestamp3)).isEqualTo("11.025")
    val timestamp4 = timestamp3 - TimeUnit.SECONDS.toMicros(11)
    assertThat(TimeFormatter.getSimplifiedClockString(timestamp4)).isEqualTo("00.025")
  }

  @Test
  fun testSingleUnitDurationString() {
    assertThat(
      TimeFormatter.getSingleUnitDurationString(((TimeUnit.HOURS.toMicros(1) * 1.354).toLong())))
      .isEqualTo("1.35 h")
    assertThat(
      TimeFormatter.getSingleUnitDurationString(((TimeUnit.MINUTES.toMicros(1) * 1.254).toLong())))
      .isEqualTo("1.25 m")
    assertThat(
      TimeFormatter.getSingleUnitDurationString(((TimeUnit.SECONDS.toMicros(1) * 1.1).toLong())))
      .isEqualTo("1.1 s")
    assertThat(TimeFormatter.getSingleUnitDurationString((TimeUnit.MILLISECONDS.toMicros(1) * 200)))
      .isEqualTo("200 ms")
  }

  @Test
  fun testMultiUnitDurationString() {
    assertThat(TimeFormatter.getMultiUnitDurationString(TimeUnit.HOURS.toMicros(1)))
      .isEqualTo("1 hr")
    assertThat(TimeFormatter.getMultiUnitDurationString(TimeUnit.HOURS.toMicros(3)))
      .isEqualTo("3 hrs")
    assertThat(TimeFormatter.getMultiUnitDurationString(TimeUnit.MINUTES.toMicros(15)))
      .isEqualTo("15 min")
    assertThat(TimeFormatter.getMultiUnitDurationString(TimeUnit.SECONDS.toMicros(20)))
      .isEqualTo("20 sec")
    assertThat(TimeFormatter.getMultiUnitDurationString(
      TimeUnit.HOURS.toMicros(1) + TimeUnit.MINUTES.toMicros(20) + TimeUnit.SECONDS.toMicros(24)))
      .isEqualTo("1 hr 20 min 24 sec")
    assertThat(TimeFormatter.getMultiUnitDurationString(0))
      .isEqualTo("0 sec")
  }
}