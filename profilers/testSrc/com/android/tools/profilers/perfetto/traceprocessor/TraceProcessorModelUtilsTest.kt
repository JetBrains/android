/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.perfetto.traceprocessor

import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorModelUtils.findValueNearKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TraceProcessorModelUtilsTest {
  @Test
  fun testFindValueNearKeyWithTolerance() {
    val hashmap = linkedMapOf(
      1L to 10,
      5L to 50,
      10L to 100,
      15L to 150,
      20L to 200
    )

    // Test when target key is within tolerance
    assertThat(findValueNearKey(hashmap, 9L, 2L)).isEqualTo(100)
    assertThat(findValueNearKey(hashmap, 7L, 2L)).isEqualTo(50)

    // Test when target key matches exactly
    assertThat(findValueNearKey(hashmap, 5L, 1L)).isEqualTo(50)

    // Test when target key is outside tolerance
    assertThat(findValueNearKey(hashmap, 7L, 1L)).isNull()
  }

  @Test
  fun testFindValueNearKeyWithEmptyMap() {
    val emptyMap = linkedMapOf<Long, Int>()
    assertThat(findValueNearKey(emptyMap, 5L, 1L)).isNull()
  }
}