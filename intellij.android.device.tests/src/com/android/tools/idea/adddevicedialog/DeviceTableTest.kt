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

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.android.sdklib.deviceprovisioner.Resolution
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DeviceTableTest {
  @get:Rule val edtRule = EdtRule()
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

    // Only a single entry -> no filter shown.
    composeTestRule.onNode(googleFilter, useUnmergedTree = true).assertDoesNotExist()
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

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun columnSort() {
    val widths = listOf(1200, 400, 800, 20)
    val devices =
      widths.map {
        TestDevices.mediumPhone.copy(name = "Phone $it", resolution = Resolution(it, 2400))
      }

    composeTestRule.setContent {
      val source = TestDeviceSource()
      source.apply { devices.forEach { add(it) } }
      TestDeviceTable(source.profiles.value.value)
    }

    // Click the column header to sort by width, arrow down to the table
    composeTestRule.onNodeWithText("Width").performClick()
    composeTestRule.onRoot().performKeyInput { keyPress(Key.DirectionDown) }

    // Verify correct row order
    for (width in widths.sorted()) {
      composeTestRule.onNodeWithText("Phone $width").assertIsSelected()
      composeTestRule.onRoot().performKeyInput { keyPress(Key.DirectionDown) }
    }
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
    composeTestRule.onNodeWithText(TestDevices.remotePixel5.name).performClick()

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

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun keyboardScrolling() {
    val tableSelectionState = TableSelectionState<TestDevice>()
    val devices = (1..30).map { TestDevices.mediumPhone.copy(name = "Medium Phone $it") }.toList()

    composeTestRule.setContent {
      val filterState = TestDeviceFilterState()
      DeviceTable(
        devices,
        columns = testDeviceTableColumns,
        filterContent = { TestDeviceFilters(devices, filterState) },
        filterState = filterState,
        tableSelectionState = tableSelectionState,
        modifier = Modifier.size(400.dp, 100.dp),
      )
    }

    composeTestRule.onNodeWithText(devices[0].name).performClick()
    composeTestRule.onNodeWithText(devices[0].name).assertIsSelected()

    for (i in 1 until devices.size) {
      composeTestRule.onRoot().performKeyInput { keyPress(Key.DirectionDown) }
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText(devices[i].name).assertIsSelected()
    }
    for (i in devices.size - 2 downTo 0) {
      composeTestRule.onRoot().performKeyInput { keyPress(Key.DirectionUp) }
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText(devices[i].name).assertIsSelected()
    }
  }

  @Test
  fun keepSelectionVisible() {
    val devices = (0..20).map { TestDevices.mediumPhone.copy(name = "Phone ${'A' + it}") }.toList()

    composeTestRule.setContent {
      DeviceTable(
        devices,
        columns = testDeviceTableColumns,
        filterContent = {},
        modifier = Modifier.size(400.dp, 200.dp),
      )
    }

    composeTestRule.onNodeWithText("Phone A").performClick()
    composeTestRule.onNodeWithText("Phone A").assertIsSelected()
    composeTestRule.onNodeWithText("Phone S").assertDoesNotExist()

    composeTestRule.onNodeWithText("Name").performClick()
    composeTestRule.onNodeWithText("Phone A").assertIsSelected()

    composeTestRule.onNodeWithText("Name").performClick()
    composeTestRule.onNodeWithText("Phone A").assertIsSelected()

    composeTestRule.onNodeWithText("Name").performClick()
    composeTestRule.onNodeWithText("Phone A").assertIsSelected()
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun focus() {
    val tableSelectionState = TableSelectionState<TestDevice>()
    val devices = (1..30).map { TestDevices.mediumPhone.copy(name = "Medium Phone $it") }.toList()

    composeTestRule.setContent {
      val filterState = TestDeviceFilterState()
      DeviceTable(
        devices,
        columns = testDeviceTableColumns,
        filterContent = { /* no filters */ },
        filterState = filterState,
        tableSelectionState = tableSelectionState,
        modifier = Modifier.size(400.dp, 100.dp),
      )
    }

    // Focus starts on the search bar
    composeTestRule.onNode(hasSetTextAction()).assertIsFocused()

    // Next is details button
    composeTestRule.onRoot().performKeyInput { keyPress(Key.Tab) }
    composeTestRule.onNodeWithContentDescription("Details").assertIsFocused()

    // Next are the column headings
    composeTestRule.onRoot().performKeyInput { keyPress(Key.Tab) }
    composeTestRule.onNodeWithText("OEM").assertIsFocused()
    composeTestRule.onRoot().performKeyInput { keyPress(Key.Tab) }
    composeTestRule.onNodeWithText("Name").assertIsFocused()
    composeTestRule.onRoot().performKeyInput { keyPress(Key.Tab) }
    composeTestRule.onNodeWithText("API").assertIsFocused()
    composeTestRule.onRoot().performKeyInput { keyPress(Key.Tab) }
    composeTestRule.onNodeWithText("Width").assertIsFocused()
    composeTestRule.onRoot().performKeyInput { keyPress(Key.Tab) }
    composeTestRule.onNodeWithText("Height").assertIsFocused()
    composeTestRule.onRoot().performKeyInput { keyPress(Key.Tab) }
    composeTestRule.onNodeWithText("Density").assertIsFocused()

    // Next is the table body
    composeTestRule.onRoot().performKeyInput { keyPress(Key.Tab) }

    // Finally back to the search bar
    composeTestRule.onRoot().performKeyInput { keyPress(Key.Tab) }
    composeTestRule.onNode(hasSetTextAction()).assertIsFocused()
  }

  @Test
  fun formFactorOrder() {
    with(FormFactors) {
      assertThat(
          listOf(TV, TABLET, PHONE, "Shoe", AUTO, "Chrome", WEAR, DESKTOP)
            .sortedWith(FormFactor.comparator)
        )
        .containsExactlyElementsIn(formFactorOrder.plus("Chrome").plus("Shoe"))
        .inOrder()
    }
  }

  @Test
  fun apiLevelOrder() {
    val api24To34 = TestDevices.mediumPhone.copy(apiRange = Range.closed(24, 34))
    val api25To30 = TestDevices.mediumPhone.copy(apiRange = Range.closed(25, 30))
    val api24Plus = TestDevices.mediumPhone.copy(apiRange = Range.atLeast(24))
    val api26Plus = TestDevices.mediumPhone.copy(apiRange = Range.atLeast(26))
    val apiUpTo30 = TestDevices.mediumPhone.copy(apiRange = Range.atMost(30))

    val levels = listOf(api24To34, api25To30, api24Plus, api26Plus, apiUpTo30)

    assertThat(levels.sortedWith(DeviceTableColumns.apiRangeAscendingOrder))
      .containsExactly(apiUpTo30, api24To34, api24Plus, api25To30, api26Plus)
      .inOrder()

    assertThat(levels.sortedWith(DeviceTableColumns.apiRangeDescendingOrder))
      .containsExactly(api26Plus, api24Plus, api24To34, api25To30, apiUpTo30)
      .inOrder()
  }
}
