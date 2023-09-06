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
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.tools.idea.insights.ui

import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE_VARIANT
import com.android.tools.idea.insights.Selection
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class VariantComboBoxTest {

  @get:Rule val disposableRule = DisposableRule()

  @Test
  fun `selection of header row is disabled in combobox`() = runTest {
    val scope = createChildScope()
    val flow = MutableSharedFlow<VariantComboBoxState>(1)
    val comboBox = VariantComboBox(scope, flow)

    flow.emit(PopulatedComboBoxState(ISSUE1, Selection(null, listOf(ISSUE_VARIANT))))

    advanceUntilIdle()

    val headerRow = comboBox.model.getElementAt(0)
    assertThat(headerRow).isSameAs(HeaderRow)
    assertThat(comboBox.selectedIndex).isEqualTo(1)

    // Verify selected item doesn't change when selecting a header row
    comboBox.selectedIndex = 0
    assertThat(comboBox.selectedIndex).isEqualTo(1)

    comboBox.selectedItem = headerRow
    assertThat(comboBox.selectedIndex).isEqualTo(1)

    scope.cancel()
  }

  @Test
  fun `combo box shows disabled text when no variants are available`() = runTest {
    val scope = createChildScope()
    val flow = MutableSharedFlow<VariantComboBoxState>(1)
    val comboBox = VariantComboBox(scope, flow)

    flow.emit(DisabledComboBoxState.empty)

    advanceUntilIdle()

    assertThat(comboBox.selectedItem).isEqualTo(DisabledTextRow("No variants available."))

    scope.cancel()
  }

  @Test
  fun `combo box shows disabled text when variants fail to load`() = runTest {
    val scope = createChildScope()
    val flow = MutableSharedFlow<VariantComboBoxState>(1)
    val comboBox = VariantComboBox(scope, flow)

    flow.emit(DisabledComboBoxState.failure)

    advanceUntilIdle()

    assertThat(comboBox.selectedItem).isEqualTo(DisabledTextRow("Failed to load variants."))

    scope.cancel()
  }

  @Test
  fun `combo box shows loading text when in between requests`() = runTest {
    val scope = createChildScope()
    val flow = MutableSharedFlow<VariantComboBoxState>(1)
    val comboBox = VariantComboBox(scope, flow)

    flow.emit(DisabledComboBoxState.loading)

    advanceUntilIdle()

    assertThat(comboBox.selectedItem).isEqualTo(DisabledTextRow("Loading variants..."))

    scope.cancel()
  }

  @Test
  fun `combo box shows selection of variants when they exist`() = runTest {
    val scope = createChildScope()
    val flow = MutableSharedFlow<VariantComboBoxState>(1)
    val comboBox = VariantComboBox(scope, flow)

    val variant1 = ISSUE_VARIANT
    val variant2 = ISSUE_VARIANT.copy(id = "variant2")

    flow.emit(PopulatedComboBoxState(ISSUE1, Selection(variant2, listOf(variant1, variant2))))

    advanceUntilIdle()

    // Verify list of variants and selected variant.
    assertThat(comboBox.model.getElementAt(0)).isSameAs(HeaderRow)
    assertThat(comboBox.model.getElementAt(1)).isEqualTo(ISSUE1.toVariantRow(2))
    assertThat(comboBox.model.getElementAt(2)).isEqualTo(variant1.toVariantRow())
    assertThat(comboBox.model.getElementAt(3)).isEqualTo(variant2.toVariantRow())
    assertThat(comboBox.selectedIndex).isEqualTo(3)

    scope.cancel()
  }
}
