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

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.requestFocus
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class DeviceTableTest {
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  @Test
  fun toggleGoogleOem() {
    composeTestRule.setContent {
      val source = TestDeviceSource()
      source.apply { TestDevices.allTestDevices.forEach { add(it) } }
      TestDeviceTable(source.profiles.value.value)
    }

    composeTestRule.onNodeWithText("Pixel 5", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Pixel Fold", useUnmergedTree = true).assertIsDisplayed()

    composeTestRule.onNode(hasText("Google") and hasAnySibling(hasText("OEM"))).performClick()

    composeTestRule.onNodeWithText("Pixel 5", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Pixel Fold", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun addNewDevices() {
    val source = TestDeviceSource()
    source.add(TestDevices.pixelFold)

    composeTestRule.setContent {
      val profiles by source.profiles.collectAsState()
      TestDeviceTable(profiles.value)
    }

    val googleFilter = hasText("Google") and hasAnyAncestor(hasTestTag("DeviceFilters"))
    val genericFilter = hasText("Generic") and hasAnyAncestor(hasTestTag("DeviceFilters"))

    composeTestRule.onNode(googleFilter, useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNode(genericFilter, useUnmergedTree = true).assertDoesNotExist()

    source.add(TestDevices.mediumPhone)

    composeTestRule.onNode(googleFilter, useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNode(genericFilter, useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun textSearch() {
    composeTestRule.setContent {
      val source = TestDeviceSource()
      source.apply { TestDevices.allTestDevices.forEach { add(it) } }
      TestDeviceTable(source.profiles.value.value)
    }

    composeTestRule.onNode(hasSetTextAction()).performTextReplacement("gal")

    composeTestRule.onNodeWithText("Pixel 5", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Pixel Fold", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Galaxy S22", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun formFactorFilter() {
    composeTestRule.setContent {
      val source = TestDeviceSource()
      source.apply { TestDevices.allTestDevices.forEach { add(it) } }
      TestDeviceTable(source.profiles.value.value)
    }

    composeTestRule.onNodeWithText(TestDevices.pixelFold.name).assertIsDisplayed()
    composeTestRule.onNodeWithText(TestDevices.automotive.name).assertDoesNotExist()

    composeTestRule
      .onNode(hasText("Phone") and hasAnySibling(hasText("Form Factor")))
      .performClick()
    composeTestRule.onNodeWithText("Automotive").performClick()

    composeTestRule.onNodeWithText(TestDevices.pixelFold.name).assertDoesNotExist()
    composeTestRule.onNodeWithText(TestDevices.automotive.name).assertIsDisplayed()
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun rightClick() {
    val clicks = mutableListOf<DeviceProfile>()

    composeTestRule.setContent {
      val source = TestDeviceSource()
      source.apply { TestDevices.allTestDevices.forEach { add(it) } }
      val filterState = TestDeviceFilterState()
      DeviceTable(
        source.profiles.value.value,
        columns = testDeviceTableColumns,
        filterContent = { TestDeviceFilters(source.profiles.value.value, filterState) },
        filterState = filterState,
        onRowSecondaryClick = { device, offset -> clicks.add(device) },
      )
    }

    composeTestRule.onNodeWithText(TestDevices.pixelFold.name).assertIsDisplayed()
    composeTestRule.onNodeWithText(TestDevices.pixelFold.name).performMouseInput {
      click(button = MouseButton.Secondary)
    }

    assertThat(clicks).containsExactly(TestDevices.pixelFold)
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun keyboard() {
    val tableSelectionState = TableSelectionState<TestDevice>()
    val devices = listOf(TestDevices.mediumPhone, TestDevices.remotePixel5, TestDevices.pixelFold)

    composeTestRule.setContent {
      val filterState = TestDeviceFilterState()
      DeviceTable(
        devices,
        columns = testDeviceTableColumns,
        filterContent = { TestDeviceFilters(devices, filterState) },
        filterState = filterState,
        tableSelectionState = tableSelectionState,
      )
    }

    tableSelectionState.selection = TestDevices.allTestDevices[0]

    // Select the Pixel 5.
    composeTestRule.onNodeWithText(TestDevices.remotePixel5.name).requestFocus().performKeyInput {
      keyPress(Key.Spacebar)
    }

    assertThat(tableSelectionState.selection).isEqualTo(TestDevices.remotePixel5)
    composeTestRule.onNodeWithText(TestDevices.remotePixel5.name).assertIsSelected()

    // Arrow down to Pixel Fold.
    composeTestRule.onRoot().performKeyInput { keyPress(Key.DirectionDown) }

    assertThat(tableSelectionState.selection).isEqualTo(TestDevices.pixelFold)
    composeTestRule.onNodeWithText(TestDevices.pixelFold.name).assertIsSelected()

    // Pixel Fold is the last device; down again should stay there.
    composeTestRule.onRoot().performKeyInput { keyPress(Key.DirectionDown) }

    assertThat(tableSelectionState.selection).isEqualTo(TestDevices.pixelFold)
    composeTestRule.onNodeWithText(TestDevices.pixelFold.name).assertIsSelected()

    // Arrow up to Medium Phone.
    composeTestRule.onRoot().performKeyInput {
      keyPress(Key.DirectionUp)
      keyPress(Key.DirectionUp)
      keyPress(Key.DirectionUp)
    }

    assertThat(tableSelectionState.selection).isEqualTo(TestDevices.mediumPhone)
    composeTestRule.onNodeWithText(TestDevices.mediumPhone.name).assertIsSelected()
  }
}

private fun KeyInjectionScope.keyPress(key: Key) {
  keyDown(key)
  keyUp(key)
}
