/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.testutils.delayUntilCondition
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE_VARIANT
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.TEST_FILTERS
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class VariantComboBoxTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `selection of header row is disabled in combobox`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val flow = MutableSharedFlow<AppInsightsState>(1)
      val comboBox = VariantComboBox(flow, projectRule.testRootDisposable)

      flow.emit(
        AppInsightsState(
          connections = Selection.emptySelection(),
          filters = TEST_FILTERS,
          issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
          currentIssueVariants = LoadingState.Ready(Selection(null, listOf(ISSUE_VARIANT)))
        )
      )

      delayUntilCondition(200) { comboBox.model.selectedItem != null }

      val headerRow = comboBox.model.getElementAt(0)
      assertThat(headerRow).isSameAs(HeaderRow)
      assertThat(comboBox.selectedIndex).isEqualTo(1)

      // Verify selected item doesn't change when selecting a header row
      comboBox.selectedIndex = 0
      assertThat(comboBox.selectedIndex).isEqualTo(1)

      comboBox.selectedItem = headerRow
      assertThat(comboBox.selectedIndex).isEqualTo(1)
    }

  @Test
  fun `combo box shows disabled text when no variants are available`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val flow = MutableSharedFlow<AppInsightsState>(1)
      val comboBox = VariantComboBox(flow, projectRule.testRootDisposable)

      flow.emit(
        AppInsightsState(
          connections = Selection.emptySelection(),
          filters = TEST_FILTERS,
          issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
          currentIssueVariants = LoadingState.Ready(Selection.emptySelection())
        )
      )

      delayUntilCondition(200) {
        comboBox.selectedItem == DisabledTextRow("No variants available.")
      }
    }

  @Test
  fun `combo box shows disabled text when variants fail to load`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val flow = MutableSharedFlow<AppInsightsState>(1)
      val comboBox = VariantComboBox(flow, projectRule.testRootDisposable)

      flow.emit(
        AppInsightsState(
          connections = Selection.emptySelection(),
          filters = TEST_FILTERS,
          issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
          currentIssueVariants = LoadingState.UnknownFailure("failed variants call")
        )
      )

      delayUntilCondition(200) {
        comboBox.selectedItem == DisabledTextRow("Failed to load variants.")
      }
    }

  @Test
  fun `combo box shows offline text when AQI is offline`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val flow = MutableSharedFlow<AppInsightsState>(1)
      val comboBox = VariantComboBox(flow, projectRule.testRootDisposable)

      flow.emit(
        AppInsightsState(
          connections = Selection.emptySelection(),
          filters = TEST_FILTERS,
          issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
          currentIssueVariants = LoadingState.NetworkFailure("offline")
        )
      )

      delayUntilCondition(200) {
        comboBox.selectedItem == DisabledTextRow("Not available offline.")
      }
    }

  @Test
  fun `combo box shows loading text when in between requests`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val flow = MutableSharedFlow<AppInsightsState>(1)
      val comboBox = VariantComboBox(flow, projectRule.testRootDisposable)

      flow.emit(
        AppInsightsState(
          connections = Selection.emptySelection(),
          filters = TEST_FILTERS,
          issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
          currentIssueVariants = LoadingState.Loading
        )
      )

      delayUntilCondition(200) { comboBox.selectedItem == DisabledTextRow("Loading variants...") }
    }

  @Test
  fun `combo box shows selection of variants when they exist`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val flow = MutableSharedFlow<AppInsightsState>(1)
      val comboBox = VariantComboBox(flow, projectRule.testRootDisposable)

      val variant1 = ISSUE_VARIANT
      val variant2 = ISSUE_VARIANT.copy(id = "variant2")

      flow.emit(
        AppInsightsState(
          connections = Selection.emptySelection(),
          filters = TEST_FILTERS,
          issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
          currentIssueVariants = LoadingState.Ready(Selection(variant2, listOf(variant1, variant2)))
        )
      )

      delayUntilCondition(200) { comboBox.model.selectedItem != null }

      // Verify list of variants and selected variant.
      assertThat(comboBox.model.getElementAt(0)).isSameAs(HeaderRow)
      assertThat(comboBox.model.getElementAt(1)).isEqualTo(ISSUE1.toVariantRow(2))
      val firstVariantRow = comboBox.model.getElementAt(2) as VariantRow
      assertThat(firstVariantRow).isEqualTo(variant1.toVariantRow())
      assertThat(firstVariantRow.name).isEqualTo("ant1")
      val secondVariantRow = comboBox.model.getElementAt(3) as VariantRow
      assertThat(secondVariantRow).isEqualTo(variant2.toVariantRow())
      assertThat(secondVariantRow.name).isEqualTo("ant2")
    }

  @Test
  fun `combo box title is shortened`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val flow = MutableSharedFlow<AppInsightsState>(1)
      val comboBox = VariantComboBox(flow, projectRule.testRootDisposable)

      val variant1 = ISSUE_VARIANT
      val variant2 = ISSUE_VARIANT.copy(id = "variant2")

      flow.emit(
        AppInsightsState(
          connections = Selection.emptySelection(),
          filters = TEST_FILTERS,
          issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
          currentIssueVariants = LoadingState.Ready(Selection(null, listOf(variant1)))
        )
      )

      delayUntilCondition(200) { (comboBox.model.selectedItem as? VariantRow)?.name == "All" }

      flow.emit(
        AppInsightsState(
          connections = Selection.emptySelection(),
          filters = TEST_FILTERS,
          issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), Instant.now())),
          currentIssueVariants = LoadingState.Ready(Selection(null, listOf(variant1, variant2)))
        )
      )

      delayUntilCondition(200) {
        (comboBox.model.selectedItem as? VariantRow)?.name == "All (2 variants)"
      }
    }
}
