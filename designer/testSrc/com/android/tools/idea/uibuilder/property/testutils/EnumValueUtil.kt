/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.testutils

import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.HeaderEnumValue
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs

object EnumValueUtil {
  /**
   * Checks a section of [EnumValue]s.
   *
   * @param values the list of [EnumValue] to check
   * @param startIndex check the section starting at this index
   * @param expectedHeader the expected header of this section. Must be in the first value.
   * @param expectedCount the expected count in this section. A negative value indicates a minimum
   *   count.
   * @param expectedValues the expected values (may be a subset of the complete section)
   * @param expectedDisplayValues the expected display values (may be a subset of the complete
   *   section)
   */
  fun checkSection(
    values: List<EnumValue>,
    startIndex: Int,
    expectedHeader: String,
    expectedCount: Int,
    expectedValues: List<String>,
    expectedDisplayValues: List<String>,
  ): Int {
    assertThat(startIndex).isAtLeast(0)
    assertThat(startIndex).isAtMost(values.lastIndex)
    var nextSectionIndex = values.size
    for (index in startIndex..values.lastIndex) {
      val enum = values[index]
      val header = enum as? HeaderEnumValue
      if (index == startIndex) {
        assertThat(header).isNotNull()
        assertThat(header!!.header).isEqualTo(expectedHeader)
        continue
      } else if (header != null) {
        nextSectionIndex = index
        break
      }
      val valueIndex = index - startIndex - 1
      if (expectedValues.size > valueIndex) {
        assertThat(enum.value).isEqualTo(expectedValues[valueIndex])
      }
      if (expectedDisplayValues.size > valueIndex) {
        assertThat(enum.display).isEqualTo(expectedDisplayValues[valueIndex])
      }
    }
    if (expectedCount >= 0) {
      assertThat(nextSectionIndex - startIndex)
        .named("Expected Style Count")
        .isEqualTo(expectedCount)
    } else {
      assertThat(nextSectionIndex - startIndex)
        .named("Expected Style Count")
        .isAtLeast(abs(expectedCount))
    }
    return if (nextSectionIndex < values.size) nextSectionIndex else -1
  }
}
