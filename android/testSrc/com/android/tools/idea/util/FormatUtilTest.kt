/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.util

import kotlin.test.assertEquals
import org.junit.Test

class FormatUtilTest {
  @Test
  fun testFormatElementListString() {
    val oneTemplate = "The following SDK component was not installed: %s"
    val twoOrThreeTemplate = "The following SDK components were not installed: %1\$s and %2\$s"
    val moreThanThreeTemplate = "The following SDK components were not installed: %1\$s and %2\$s more"

    // 0 elements
    assertEquals("<validation error>", formatElementListString(emptyList(), oneTemplate, twoOrThreeTemplate, moreThanThreeTemplate))

    // 1 element
    assertEquals(
      "The following SDK component was not installed: A",
      formatElementListString(listOf("A"), oneTemplate, twoOrThreeTemplate, moreThanThreeTemplate),
    )

    // 2 elements
    assertEquals(
      "The following SDK components were not installed: A and B",
      formatElementListString(listOf("A", "B"), oneTemplate, twoOrThreeTemplate, moreThanThreeTemplate),
    )

    // 3 elements
    assertEquals(
      "The following SDK components were not installed: A, B and C",
      formatElementListString(listOf("A", "B", "C"), oneTemplate, twoOrThreeTemplate, moreThanThreeTemplate),
    )

    // 4 elements
    assertEquals(
      "The following SDK components were not installed: A, B and 2 more",
      formatElementListString(listOf("A", "B", "C", "D"), oneTemplate, twoOrThreeTemplate, moreThanThreeTemplate),
    )

    // 5 elements
    assertEquals(
      "The following SDK components were not installed: A, B and 3 more",
      formatElementListString(listOf("A", "B", "C", "D", "E"), oneTemplate, twoOrThreeTemplate, moreThanThreeTemplate),
    )
  }
}
