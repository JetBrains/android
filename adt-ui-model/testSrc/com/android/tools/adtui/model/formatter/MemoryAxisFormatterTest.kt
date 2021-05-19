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
  fun `memory formatting keeps 1 digit if the value is at least one next-smaller-unit`() {
    val formatter = MemoryAxisFormatter(1, 5, 5)
    fun check(x: Double, s: String) =
      Truth.assertThat(formatter.getFormattedString(1024*1024*1024.0, x, true)).isEqualTo(s)
    check(800 * 1024 * 1024.0, "0.8 GB")
    check(25.1 * 1024 * 1024.0, "0.02 GB")
    check(110 * 1024 * 1024.0, "0.1 GB")
    check(5.23 * 1024 * 1024.0, "0.005 GB")
    check(258 * 1024 * 1024.0, "0.3 GB")
    check(479 * 1024 * 1024.0, "0.5 GB")
    check(65 * 1024 * 1024.0, "0.06 GB")
  }

  @Test
  fun `memory formatting different sizes`() {
    val formatter = MemoryAxisFormatter(1, 5, 5)
    Truth.assertThat(formatter.getFormattedString(100.0, 5.0, true)).isEqualTo("5 B")
    Truth.assertThat(formatter.getFormattedString(2048.0, 5.0 * 1024, true)).isEqualTo("5 KB")
  }
}