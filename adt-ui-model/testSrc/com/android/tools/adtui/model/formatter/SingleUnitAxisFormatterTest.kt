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
package com.android.tools.adtui.model.formatter

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SingleUnitAxisFormatterTest {
  @Test
  fun numbersAreFormattedProperlyWithoutCommas() {
    val formatter = SingleUnitAxisFormatter(1, 5, 1, "mm", false)
    var singleUnitString = formatter.getFormattedString(100.0, 100.0, true)
    assertThat(singleUnitString).matches("100 mm")
    singleUnitString = formatter.getFormattedString(1000.0, 1000.0, false)
    assertThat(singleUnitString).matches("1000")
    singleUnitString = formatter.getFormattedString(10000.0, 10000.0, true)
    assertThat(singleUnitString).matches("10000 mm")
    singleUnitString = formatter.getFormattedString(100000.0, 100000.0, false)
    assertThat(singleUnitString).matches("100000")
    singleUnitString = formatter.getFormattedString(1000000.0, 1000000.0, false)
    assertThat(singleUnitString).matches("1000000")
  }

  @Test
  fun numbersAreFormattedProperlyWithCommas() {
    val formatter = SingleUnitAxisFormatter(1, 5, 1, "mm", true)
    var singleUnitString = formatter.getFormattedString(100.0, 100.0, true)
    assertThat(singleUnitString).matches("100 mm")
    singleUnitString = formatter.getFormattedString(1000.0, 1000.0, false)
    assertThat(singleUnitString).matches("1,000")
    singleUnitString = formatter.getFormattedString(10000.0, 10000.0, true)
    assertThat(singleUnitString).matches("10,000 mm")
    singleUnitString = formatter.getFormattedString(100000.0, 100000.0, false)
    assertThat(singleUnitString).matches("100,000")
    singleUnitString = formatter.getFormattedString(1000000.0, 1000000.0, false)
    assertThat(singleUnitString).matches("1,000,000")
  }
}