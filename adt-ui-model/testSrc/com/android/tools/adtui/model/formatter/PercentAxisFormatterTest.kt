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

class PercentAxisFormatterTest {
  @Test
  fun numbersAreFormattedProperly() {
    val formatter = PercentAxisFormatter(1,10)
    var percent = formatter.getFormattedString(100.0, 25.0, true)
    assertThat(percent).matches("25%")
    percent = formatter.getFormattedString(10000.0, 7500.0, false)
    assertThat(percent).matches("75")
    percent = formatter.getFormattedString(100000.0, 33333.0, true)
    assertThat(percent).matches("33.33%")
    percent = formatter.getFormattedString(100000.0, -33333.0, false)
    assertThat(percent).matches("-33.33")
    percent = formatter.getFormattedString(100000.0, 0.0, false)
    assertThat(percent).matches("0")
  }

}