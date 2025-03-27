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
package com.android.tools.idea.avd

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.avd.StorageCapacityFieldState.Overflow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StorageCapacityFieldTest {
  @get:Rule val rule = createStudioComposeTestRule()
  private val state = StorageCapacityFieldState(CAPACITY)

  @Before
  fun setContent() {
    rule.setContent { StorageCapacityField(state, null) }
  }

  @Test
  fun regexDoesntMatchText() {
    // Act
    rule.onNodeWithTag("StorageCapacityFieldTextField").performTextReplacement("a")

    // Assert
    assertEquals(CAPACITY, state.valid().storageCapacity)
  }

  @Test
  fun textIsEmpty() {
    // Act
    rule.onNodeWithTag("StorageCapacityFieldTextField").performTextReplacement("")

    // Assert
    assertEquals(StorageCapacityFieldState.Empty, state.result())
  }

  @Test
  fun replaceValueWithMaxValuePlus1() {
    // Act
    rule
      .onNodeWithTag("StorageCapacityFieldTextField")
      .performTextReplacement("9223372036854775808")

    // Assert
    assertEquals(Overflow, state.result())
  }

  @Test
  fun capacityCantBeExpressedInBytes() {
    // Act
    rule.onNodeWithTag("StorageCapacityFieldTextField").performTextReplacement("8796093022208")

    // Assert
    assertEquals(Overflow, state.result())
  }

  private companion object {
    private val CAPACITY = StorageCapacity(2_048, StorageCapacity.Unit.MB)
  }
}
