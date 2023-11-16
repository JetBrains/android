/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.facet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class AndroidSourceTypeTest {

  @Test
  fun compareTo() {

    val expectedOrder: List<AndroidSourceType> =
      BUILT_IN_TYPES + listOf(
        AndroidSourceType.Custom("a"),
        AndroidSourceType.Custom("z"),
      )

    assertThat(expectedOrder.reversed().sorted()).containsExactlyElementsIn(expectedOrder).inOrder()
  }

  @Test
  fun equalsAndHashcode() {
    val examples: List<AndroidSourceType> =
      BUILT_IN_TYPES + listOf(
        AndroidSourceType.Custom("a"),
        AndroidSourceType.Custom("z"),
      )
    for (i in examples.indices) {
      assertThat(examples[i]).isEqualTo(examples[i])
      for (j in 0 until i) {
        assertThat(examples[i]).isNotEqualTo(examples[j])
      }
    }
    val set = examples.toSet()
    assertThat(set).hasSize(examples.size)
  }
}