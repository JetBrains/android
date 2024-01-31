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
package com.android.tools.idea.adddevicedialog

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import com.android.testutils.MockitoKt
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito

@RunWith(JUnit4::class)
class StorageCapacityFieldTest {
  @get:Rule val rule = createComposeRule()

  @Ignore("kotlinx-coroutines-* Version 1.8.0-RC2 is required")
  @Test
  fun replaceValueWithMaxValuePlus1() {
    // Arrange
    val onValueChange = MockitoKt.mock<(StorageCapacity) -> Unit>()

    rule.setContent {
      IntUiTheme {
        StorageCapacityField(StorageCapacity(2_048, StorageCapacity.Unit.MB), onValueChange)
      }
    }

    // Act
    rule
      .onNodeWithTag("StorageCapacityFieldTextField")
      .performTextReplacement("9223372036854775808")

    // Assert
    Mockito.verifyNoInteractions(onValueChange)
  }
}
