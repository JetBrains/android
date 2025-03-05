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

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunsInEdt
@RunWith(JUnit4::class)
class StorageGroupTest {
  @get:Rule val composeRule = createStudioComposeTestRule()
  @get:Rule val edtRule = EdtRule()

  @Test
  fun internalStorageIsValid() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, false, false) }

    // Act
    composeRule.onInternalStorageTextField().performTextReplacement("3")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onTooltips().assertCountEquals(0)
    assertThat(device.internalStorage).isEqualTo(StorageCapacity(3, StorageCapacity.Unit.GB))
  }

  @Test
  fun internalStorageIsEmpty() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, false, false) }

    // Act
    composeRule.onInternalStorageTextField().performTextReplacement("")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onNodeWithText("Specify an internal storage value").assertIsDisplayed()
    assertThat(device.internalStorage).isNull()
  }

  @Test
  fun internalStorageIsLessThanMinAndHasPlayStore() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, true, false) }

    // Act
    composeRule.onInternalStorageTextField().performTextReplacement("1")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule
      .onNodeWithText("Internal storage for Play Store devices must be at least 2G")
      .assertIsDisplayed()

    assertThat(device.internalStorage).isNull()
  }

  @Test
  fun internalStorageIsLessThanMin() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, false, false) }

    // Act
    composeRule.onInternalStorageTextField().performTextReplacement("1")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onNodeWithText("Internal storage must be at least 2G").assertIsDisplayed()
    assertThat(device.internalStorage).isNull()
  }

  @Test
  fun internalStorageIsOverflow() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, false, false) }

    // Act
    composeRule.onInternalStorageTextField().performTextReplacement("8589934592")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onInternalStorageTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onNodeWithText("Internal storage is too large").assertIsDisplayed()
    assertThat(device.internalStorage).isNull()
  }

  @Test
  fun expandedStorageFormFactorDoesNotEqualWearOS() {
    // Arrange
    val device = TestDevices.pixel9Pro()
    val state = StorageGroupState(device)

    // Act
    setContent { StorageGroup(device, state, false, true) }

    // Assert
    composeRule.onNodeWithText("Expanded storage").assertExists()
  }

  @Test
  fun expandedStorageFormFactorEqualsWearOS() {
    // Arrange
    var device = TestDevices.wearOSSmallRound()
    val state = StorageGroupState(device)

    // Act
    setContent { StorageGroup(device, state, false, true) }

    // Assert
    composeRule.onNodeWithText("Expanded storage").assertDoesNotExist()
  }

  @Test
  fun expandedStorage() {
    // Arrange
    var device = TestDevices.pixel9Pro()
    val state = StorageGroupState(device)

    // Act
    setContent { StorageGroup(device, state, false, false) }

    // Assert
    composeRule.onNodeWithText("Expanded storage").assertExists()
  }

  @Test
  fun onCustomRadioButtonClick() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, false, false) }

    // Act
    composeRule.onNodeWithText("Existing image").performClick()
    composeRule.onNodeWithText("Custom").performClick()
    composeRule.waitForIdle()

    // Assert
    assertThat(state.selectedRadioButton).isEqualTo(ExpandedStorageRadioButton.CUSTOM)
    assertThat(device.expandedStorage)
      .isEqualTo(Custom(StorageCapacity(512, StorageCapacity.Unit.MB)))
  }

  @Test
  fun onExistingImageRadioButtonClick() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, false, false) }

    // Act
    composeRule.onNodeWithText("Existing image").performClick()

    @OptIn(ExperimentalTestApi::class)
    composeRule.onNodeWithTag("ExistingImageField").performMouseInput { moveTo(center) }

    // Assert
    assertEquals(ExpandedStorageRadioButton.EXISTING_IMAGE, state.selectedRadioButton)
    composeRule.onNodeWithText("The specified image must be a valid file").assertIsDisplayed()
    assertThat(device.expandedStorage).isNull()
  }

  @Test
  fun onNoneRadioButtonClick() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, false, false) }

    // Act
    composeRule.onNodeWithText("None").performClick()
    composeRule.waitForIdle()

    // Assert
    assertEquals(ExpandedStorageRadioButton.NONE, state.selectedRadioButton)
    assertThat(device.expandedStorage).isEqualTo(None)
  }

  @Test
  fun customIsValid() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, false, false) }

    // Act
    composeRule.onCustomTextField().performTextReplacement("513")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onCustomTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onTooltips().assertCountEquals(0)
    assertThat(device.expandedStorage)
      .isEqualTo(Custom(StorageCapacity(513, StorageCapacity.Unit.MB)))
  }

  @Test
  fun customIsEmpty() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, false, false) }

    // Act
    composeRule.onCustomTextField().performTextReplacement("")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onCustomTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onNodeWithText("Specify an SD card size").assertIsDisplayed()
    assertThat(device.expandedStorage).isNull()
  }

  @Test
  fun customIsLessThanMinAndHasPlayStore() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, true, false) }

    // Act
    composeRule.onCustomTextField().performTextReplacement("99")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onCustomTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule
      .onNodeWithText("The SD card for Play Store devices must be at least 100M")
      .assertIsDisplayed()
    assertThat(device.expandedStorage).isNull()
  }

  @Test
  fun customIsLessThanMin() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, false, false) }

    // Act
    composeRule.onCustomTextField().performTextReplacement("9")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onCustomTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onNodeWithText("The SD card must be at least 10M").assertIsDisplayed()
    assertThat(device.expandedStorage).isNull()
  }

  @Test
  fun customIsOverflow() {
    // Arrange
    val device = TestDevices.pixel6()
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, false, false) }

    // Act
    composeRule.onCustomTextField().performTextReplacement("8796093022208")

    @OptIn(ExperimentalTestApi::class)
    composeRule.onCustomTextField().performMouseInput { moveTo(center) }

    // Assert
    composeRule.onNodeWithText("SD card size is too large").assertIsDisplayed()
    assertThat(device.expandedStorage).isNull()
  }

  @Test
  fun existingCustomExpandedStorageDoesntEqualState() {
    // Arrange
    val device =
      TestDevices.pixel6().apply {
        existingCustomExpandedStorage = Custom(StorageCapacity(512, StorageCapacity.Unit.MB))
      }
    val state = StorageGroupState(device)

    setContent { StorageGroup(device, state, false, false) }

    // Act
    composeRule.onCustomTextField().performTextReplacement("513")
    composeRule.waitForIdle()

    // Assert
    composeRule.onNodeWithText("Modifying storage size erases existing content").assertIsDisplayed()
  }

  private fun setContent(composable: @Composable () -> Unit) {
    composeRule.setContent { provideCompositionLocals { composable() } }
  }

  private companion object {
    private fun SemanticsNodeInteractionsProvider.onInternalStorageTextField() =
      onNodeWithTag("InternalStorageRow").onChildren().filterToOne(hasSetTextAction())

    private fun SemanticsNodeInteractionsProvider.onCustomTextField() =
      onNodeWithTag("CustomRow").onChildren().filterToOne(hasSetTextAction())
  }
}
