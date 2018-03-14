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

class EnergyAxisFormatterTest {
  @Test
  fun testGlobalRangeIsZero() {
    assertThat(EnergyAxisFormatter.DEFAULT.getMajorInterval(0.0)).isEqualTo(0L)
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(0.0, 0.0, false)).isEqualTo("None")
  }

  @Test
  fun testGlobalRangeIsLow() {
    assertThat(EnergyAxisFormatter.DEFAULT.getMajorInterval(200.0)).isEqualTo(200L)
    assertThat(EnergyAxisFormatter.DEFAULT.getMajorInterval(100.0)).isEqualTo(100L)
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(200.0, 100.0, false)).isEqualTo("Light")
  }

  @Test
  fun testGlobalRangeIsMedium() {
    assertThat(EnergyAxisFormatter.DEFAULT.getMajorInterval(400.0)).isEqualTo(200L)
    assertThat(EnergyAxisFormatter.DEFAULT.getMajorInterval(300.0)).isEqualTo(200L)
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(300.0, 300.0, false)).isEqualTo("Medium")
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(300.0, 200.0, false)).isEqualTo("Light")
  }

  @Test
  fun testGlobalRangeIsHeavy() {
    assertThat(EnergyAxisFormatter.DEFAULT.getMajorInterval(600.0)).isEqualTo(200L)
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(600.0, 500.0, false)).isEqualTo("Heavy")
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(600.0, 300.0, false)).isEqualTo("Medium")
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(600.0, 100.0, false)).isEqualTo("Light")
  }

  @Test
  fun testGlobalRangeExceedsHeavyButStillCanDisplayLow() {
    assertThat(EnergyAxisFormatter.DEFAULT.getMajorInterval(700.0)).isEqualTo(200L)
    // The largest marker is at 600 and 700 do not repeat the "HEAVY" marker.
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(700.0, 700.0, false)).isEqualTo("")
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(700.0, 600.0, false)).isEqualTo("Heavy")
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(700.0, 300.0, false)).isEqualTo("Medium")
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(700.0, 200.0, false)).isEqualTo("Light")
  }

  @Test
  fun testGlobalRangeTooLargeToDisplayLow() {
    assertThat(EnergyAxisFormatter.DEFAULT.getMajorInterval(800.0)).isEqualTo(300L)
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(800.0, 800.0, false)).isEqualTo("")
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(800.0, 600.0, false)).isEqualTo("Heavy")
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(800.0, 300.0, false)).isEqualTo("Medium")
  }

  @Test
  fun testGlobalRangeTooLargeToDisplayMedium() {
    assertThat(EnergyAxisFormatter.DEFAULT.getMajorInterval(900.0)).isEqualTo(900L)
    assertThat(EnergyAxisFormatter.DEFAULT.getFormattedString(900.0, 900.0, false)).isEqualTo("Heavy")
  }
}
