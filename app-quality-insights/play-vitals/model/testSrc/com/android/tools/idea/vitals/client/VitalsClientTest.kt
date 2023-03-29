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
package com.android.tools.idea.vitals.client

import com.android.tools.idea.insights.WithCount
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VitalsClientTest {
  @Test
  fun checkAggregationUtils() {
    val list =
      listOf(
        Pair("a", 1L),
        Pair("b", 0L),
        Pair("c", 2L),
        Pair("a", 1L),
        Pair("b", 0L),
        Pair("c", 100L),
        Pair("d", 0L)
      )

    val aggregated = list.aggregateToWithCount()
    assertThat(aggregated)
      .containsExactlyElementsIn(
        listOf(WithCount(2L, "a"), WithCount(0L, "b"), WithCount(102L, "c"), WithCount(0L, "d"))
      )
  }
}
