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

import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.IssueVariant
import com.android.tools.idea.insights.Selection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.VisibleForTesting

/** Represents the different states the variant combobox could have. */
sealed interface VariantComboBoxState

data class DisabledComboBoxState(val message: String) : VariantComboBoxState {
  companion object {
    val loading = DisabledComboBoxState("Loading variants...")
    val empty = DisabledComboBoxState("No variants available.")
    val failure = DisabledComboBoxState("Failed to load variants.")
  }
}

data class PopulatedComboBoxState(
  val issue: AppInsightsIssue,
  val variants: Selection<IssueVariant>
) : VariantComboBoxState

class VariantComboBox(scope: CoroutineScope, flow: Flow<VariantComboBoxState>) :
  CommonComboBox<Row, DefaultCommonComboBoxModel<Row>>(
    DefaultCommonComboBoxModel<Row>("All variants").apply { editable = false }
  ) {
  private var isDisabledIndex = false
  private var currentVariantSelection: Selection<IssueVariant>? = null

  init {
    flow
      .distinctUntilChanged()
      .onEach { state ->
        when (state) {
          is DisabledComboBoxState -> {
            model.removeAllElements()
            model.addElement(DisabledTextRow(state.message))
            model.enabled = false
          }
          is PopulatedComboBoxState -> {
            val variantSize = state.variants.items.size
            if (currentVariantSelection?.items != state.variants.items) {
              model.removeAllElements()
              val allItem = state.issue.toVariantRow(variantSize)
              model.addElement(HeaderRow)
              model.addElement(allItem)
              model.addAll(state.variants.items.map { it.toVariantRow() })
              model.selectedItem = allItem
            }
            if (currentVariantSelection?.selected != state.variants.selected) {
              if (state.variants.selected == null) {
                model.selectedItem = state.issue.toVariantRow(variantSize)
              } else {
                model.selectedItem = state.variants.selected!!.toVariantRow()
              }
            }
            currentVariantSelection = state.variants
            model.enabled = true
          }
        }
      }
      .launchIn(scope)
  }

  // Disable selection on header row
  override fun setPopupVisible(visible: Boolean) {
    if (!visible && isDisabledIndex) {
      isDisabledIndex = false
    } else {
      super.setPopupVisible(visible)
    }
  }

  override fun setSelectedIndex(anIndex: Int) {
    if (getItemAt(anIndex) is HeaderRow) {
      isDisabledIndex = true
    } else {
      super.setSelectedIndex(anIndex)
    }
  }

  override fun setSelectedItem(anObject: Any?) {
    if (anObject is HeaderRow) {
      isDisabledIndex = true
    } else {
      super.setSelectedItem(anObject)
    }
  }
}

@VisibleForTesting
fun AppInsightsIssue.toVariantRow(size: Int) =
  VariantRow(
    "All ($size variant${if (size > 1) "s" else ""})",
    issueDetails.eventsCount,
    issueDetails.impactedDevicesCount,
    null
  )

@VisibleForTesting
fun IssueVariant.toVariantRow() =
  VariantRow("Variant ${id.take(4)}", eventsCount, impactedDevicesCount, this)
