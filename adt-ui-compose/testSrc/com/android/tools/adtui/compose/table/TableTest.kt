/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.adtui.compose.table

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.keyPress
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class TableTest {
  @get:Rule val edtRule = EdtRule()
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun columnSort() {
    val weights = listOf(12, 4, 8, 20)
    val cats = weights.map { Cat(name = "Cat $it", weight = it) }

    composeTestRule.setContent { Table(columns = columns, cats, { it }) }

    // Click the column header to sort by weight, arrow down to the table
    composeTestRule.onNodeWithText("Weight").performClick()
    composeTestRule.onRoot().performKeyInput { keyPress(Key.DirectionDown) }

    // Verify correct row order
    for (weight in weights.sorted()) {
      composeTestRule.onNodeWithText("Cat $weight").assertIsSelected()
      composeTestRule.onRoot().performKeyInput { keyPress(Key.DirectionDown) }
    }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun rightClick() {
    val clicks = mutableListOf<Cat>()

    composeTestRule.setContent {
      Table(
        columns = listOf(name, weight),
        cats,
        { it },
        onRowSecondaryClick = { cat, offset -> clicks.add(cat) },
      )
    }

    composeTestRule.onNodeWithText("Hobbes").assertIsDisplayed()
    composeTestRule.onNodeWithText("Hobbes").performMouseInput {
      click(button = MouseButton.Secondary)
    }

    assertThat(clicks).containsExactly(hobbes)
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun keyboard() {
    val tableSelectionState = TableSelectionState<Cat>()
    val devices = listOf(scooter, benson, bella)

    composeTestRule.setContent {
      Table(
        columns = columns,
        devices,
        tableSelectionState = tableSelectionState,
        rowId = { it },
        modifier = Modifier.size(400.dp, 100.dp),
      )
    }

    // Select Benson.
    composeTestRule.onNodeWithText(benson.name).performClick()

    assertThat(tableSelectionState.selection).isEqualTo(benson)
    composeTestRule.onNodeWithText(benson.name).assertIsSelected()

    // Arrow down to Bella.
    composeTestRule.onRoot().performKeyInput { keyPress(Key.DirectionDown) }

    assertThat(tableSelectionState.selection).isEqualTo(bella)
    composeTestRule.onNodeWithText(bella.name).assertIsSelected()

    // Bella is the last cat; down again should stay there.
    composeTestRule.onRoot().performKeyInput { keyPress(Key.DirectionDown) }

    assertThat(tableSelectionState.selection).isEqualTo(bella)
    composeTestRule.onNodeWithText(bella.name).assertIsSelected()

    // Arrow up to Scooter.
    composeTestRule.onRoot().performKeyInput {
      keyPress(Key.DirectionUp)
      keyPress(Key.DirectionUp)
      keyPress(Key.DirectionUp)
    }

    assertThat(tableSelectionState.selection).isEqualTo(scooter)
    composeTestRule.onNodeWithText(scooter.name).assertIsSelected()
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun keyboardScrolling() {
    val tableSelectionState = TableSelectionState<Cat>()
    val cats = (1..30).map { cats[0].copy(name = "Cat $it") }.toList()

    composeTestRule.setContent {
      Table(
        columns = columns,
        cats,
        tableSelectionState = tableSelectionState,
        rowId = { it },
        modifier = Modifier.size(400.dp, 100.dp),
      )
    }

    composeTestRule.onNodeWithText(cats[0].name).performClick()
    composeTestRule.onNodeWithText(cats[0].name).assertIsSelected()

    for (i in 1 until cats.size) {
      composeTestRule.onRoot().performKeyInput { keyPress(Key.DirectionDown) }
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText(cats[i].name).assertIsSelected()
    }
    for (i in cats.size - 2 downTo 0) {
      composeTestRule.onRoot().performKeyInput { keyPress(Key.DirectionUp) }
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText(cats[i].name).assertIsSelected()
    }
  }

  @Test
  fun keepSelectionVisible() {
    val cats = (0..20).map { benson.copy(name = "Cat ${'A' + it}") }.toList()

    composeTestRule.setContent {
      Table(columns = columns, cats, rowId = { it }, modifier = Modifier.size(400.dp, 100.dp))
    }

    composeTestRule.onNodeWithText("Cat A").performClick()
    composeTestRule.onNodeWithText("Cat A").assertIsSelected()
    composeTestRule.onNodeWithText("Cat S").assertDoesNotExist()

    composeTestRule.onNodeWithText("Name").performClick()
    composeTestRule.onNodeWithText("Cat A").assertIsSelected()

    composeTestRule.onNodeWithText("Name").performClick()
    composeTestRule.onNodeWithText("Cat A").assertIsSelected()

    composeTestRule.onNodeWithText("Name").performClick()
    composeTestRule.onNodeWithText("Cat A").assertIsSelected()
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun focus() {
    val tableSelectionState = TableSelectionState<Cat>()
    val cats = (1..30).map { benson.copy(name = "Cat $it") }.toList()

    val focusRequester = FocusRequester()
    composeTestRule.setContent {
      Table(
        columns = columns,
        cats,
        tableSelectionState = tableSelectionState,
        rowId = { it },
        modifier = Modifier.size(400.dp, 100.dp).focusRequester(focusRequester),
      )
    }
    focusRequester.requestFocus()

    // Focus starts on the name column
    composeTestRule.onNodeWithText("Name").assertIsFocused()

    // Switch to the next column
    composeTestRule.onRoot().performKeyInput { keyPress(Key.Tab) }
    composeTestRule.onNodeWithText("Weight").assertIsFocused()

    // Next is the table body
    composeTestRule.onRoot().performKeyInput { keyPress(Key.Tab) }

    // Finally back to the first column
    composeTestRule.onRoot().performKeyInput { keyPress(Key.Tab) }
    composeTestRule.onNodeWithText("Name").assertIsFocused()
  }

  private data class Cat(val name: String, val weight: Int)

  private val benson = Cat("Benson", 18)
  private val bella = Cat("Bella", 10)
  private val hobbes = Cat("Hobbes", weight = 12)
  private val scooter = Cat("Scooter", weight = 8)
  private val cats = listOf(benson, bella, hobbes, scooter)

  private val name =
    TableTextColumn<Cat>(
      "Name",
      TableColumnWidth.Weighted(2f),
      attribute = { it.name },
      maxLines = 2,
    )
  private val weight =
    DefaultSortableTableColumn<Cat, Int>(
      "Weight",
      width = TableColumnWidth.ToFit("20", extraPadding = 8.dp),
      attribute = { it.weight },
    )

  private val columns = listOf(name, weight)
}
