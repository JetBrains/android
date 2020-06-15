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
package com.android.tools.idea.common.surface

import com.android.tools.idea.uibuilder.surface.TRIMMED_TAIL
import com.android.tools.idea.uibuilder.surface.createTrimmedText
import junit.framework.Assert.assertEquals
import org.junit.Test

// The assigned withs here are just for easy-to-test purpose.
private val TEST_CHAR_MEASURER: (Char) -> Int = {
  when (it) {
    'a', 'A' -> 20
    'b', 'B' -> 30
    'c', 'C' -> 50
    else -> 10 // including "."
  }
}

private val TEST_STRING_MEASURER: (String) -> Int = {
  when (it) {
    TRIMMED_TAIL -> 30
    else -> it.sumBy { char -> TEST_CHAR_MEASURER(char) }
  }
}

class ModelNameLayerTest {

  @Test
  fun testCreateTextWithoutTrimming() {
    run {
      val availableWidth = 100
      val label = "ABC"
      val expected = "ABC"

      val actual = createTrimmedText(label, availableWidth, TEST_STRING_MEASURER)
      assertEquals(expected, actual)
    }
    run {
      val availableWidth = 100
      val label = "AaAa"
      val expected = "AaAa"

      val actual = createTrimmedText(label, availableWidth, TEST_STRING_MEASURER)
      assertEquals(expected, actual)
    }
    run {
      val availableWidth = 150
      val label = "BbBb"
      val expected = "BbBb"

      val actual = createTrimmedText(label, availableWidth, TEST_STRING_MEASURER)
      assertEquals(expected, actual)
    }
    run {
      val availableWidth = 200
      val label = "CcCc"
      val expected = "CcCc"

      val actual = createTrimmedText(label, availableWidth, TEST_STRING_MEASURER)
      assertEquals(expected, actual)
    }
  }

  @Test
  fun testCreateTextWithTrimming() {
    run {
      val availableWidth = 100
      val label = "ABCabc"
      val expected = "AB..."

      val actual = createTrimmedText(label,availableWidth, TEST_STRING_MEASURER)
      assertEquals(expected, actual)
    }
    run {
      val availableWidth = 100
      val label = "AaAaAaAa"
      val expected = "AaA..."

      val actual = createTrimmedText(label,availableWidth, TEST_STRING_MEASURER)
      assertEquals(expected, actual)
    }
    run {
      val availableWidth = 150
      val label = "BbBbBb"
      val expected = "BbBb..."

      val actual = createTrimmedText(label, availableWidth, TEST_STRING_MEASURER)
      assertEquals(expected, actual)
    }
    run {
      val availableWidth = 200
      val label = "CcCcCc"
      val expected = "CcC..."

      val actual = createTrimmedText(label, availableWidth, TEST_STRING_MEASURER)
      assertEquals(expected, actual)
    }
  }
}
