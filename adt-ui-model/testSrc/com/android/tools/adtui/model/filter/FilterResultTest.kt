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
package com.android.tools.adtui.model.filter

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FilterResultTest {
  @Test
  fun combine() {
    val identity = FilterResult()
    val result1 = FilterResult(1, 2, false)
    val result2 = FilterResult(3, 4, true)

    assertThat(result1.combine(identity)).isEqualTo(result1)
    assertThat(result2.combine(identity)).isEqualTo(result2)
    assertThat(result1.combine(result2)).isEqualTo(FilterResult(4, 6, true))
  }
}