/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.truth.Truth
import org.junit.Test

class MemoryAxisFormatterTest {

  @Test
  fun `memory formatting using one decimal place`() {
    val formatter = MemoryAxisFormatter(1, 5, 5)
    fun check(x: Double, s: String) =
      Truth.assertThat(formatter.getFormattedString(x, x, true)).isEqualTo(s)
    check(25.1 * 1024 * 1024, "25.1 MB")
    check(5.23 * 1024 * 1024, "5.2 MB")
    check(9.97 * 1024 * 1024 * 1024, "10 GB")
    check(8.29 * 1024, "8.3 KB")
    // floating point precision causes round down
    check(2.25, "2.2 B")
  }

  @Test
  fun `memory formatting different sizes`() {
    val formatter = MemoryAxisFormatter(1, 5, 5)
    Truth.assertThat(formatter.getFormattedString(5.0, 5.0, true)).isEqualTo("5 B")
    Truth.assertThat(formatter.getFormattedString(5.0 * 1024, 5.0 * 1024, true)).isEqualTo("5 KB")
    Truth.assertThat(formatter.getFormattedString(5.0 * 1024 * 1024, 5.0 * 1024 * 1024, true)).isEqualTo("5 MB")
    Truth.assertThat(formatter.getFormattedString(5.0 * 1024 * 1024 * 1024, 5.0 * 1024 * 1024 * 1024, true)).isEqualTo("5 GB")
  }

  @Test
  fun `memory formatting displays correct unit within range of value`() {
    val formatter = MemoryAxisFormatter(1, 5, 5);

    // 1023.9 is less than the threshold of 1024 to move B -> KB
    Truth.assertThat(formatter.getFormattedString(1023.9, 1023.9, true)).isEqualTo("1023.9 B");

    // 1024^1 B is the lower bound for KB
    Truth.assertThat(formatter.getFormattedString(1024.0, 1024.0,true)).isEqualTo("1 KB");

    // Stays at KB, not MB unit, despite 1024 multiplier threshold met due to rounding of 1023.99...
    Truth.assertThat(formatter.getFormattedString(1048575.9, 1048575.9, true)).isEqualTo("1024 KB");

    // 1024^2 B is the lower bound for MB
    Truth.assertThat(formatter.getFormattedString(1048576.0, 1048576.0, true)).isEqualTo("1 MB");

    // Stays at MB, not GB unit, despite 1024 multiplier threshold met due to rounding of 1048575.99...
    Truth.assertThat(formatter.getFormattedString(1073741823.9, 1073741823.9, true)).isEqualTo("1024 MB");

    // 1024^3 B is now the lower bound for GB
    Truth.assertThat(formatter.getFormattedString(1073741824.0, 1073741824.0, true)).isEqualTo("1 GB");
  }
}