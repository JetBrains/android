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
package com.android.tools.profilers

import com.android.tools.profilers.StringFormattingUtils.formatLongValueWithCommas
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StringFormattingUtilsTest {
  @Test
  fun testFormatLongValueWithNoCommasExpected() {
    // A value with 3 digits should not have a commas
    val value = 999L

    val formattedValueWithCommas = formatLongValueWithCommas(value)
    assertThat(formattedValueWithCommas).isEqualTo("999")
  }

  @Test
  fun testFormatLongValueWithSingleCommaExpected() {
    // A value with 4 digits should have a comma
    val value = 9999L

    val formattedValueWithCommas = formatLongValueWithCommas(value)
    assertThat(formattedValueWithCommas).isEqualTo("9,999")
  }

  @Test
  fun testFormatLongValueWithMultipleCommasExpected() {
    // A value with 10 digits should have 3 commas
    val value = 9999999999L

    val formattedValueWithCommas = formatLongValueWithCommas(value)
    assertThat(formattedValueWithCommas).isEqualTo("9,999,999,999")
  }
}