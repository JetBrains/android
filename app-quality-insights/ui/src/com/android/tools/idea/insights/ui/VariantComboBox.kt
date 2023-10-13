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
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.IssueVariant
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.intellij.openapi.Disposable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.VisibleForTesting

const val LOADING_COMBOBOX_MESSAGE = "Loading variants..."
const val EMPTY_COMBOBOX_MESSAGE = "No variants available."
const val FAILURE_COMBOBOX_MESSAGE = "Failed to load variants."

class VariantComboBox(flow: Flow<AppInsightsState>, parentDisposable: Disposable) :
  CommonComboBox<Row, DefaultCommonComboBoxModel<Row>>(
    DefaultCommonComboBoxModel<Row>("All variants").apply { editable = false }
  ) {
  private var isDisabledIndex = false
  private var currentVariantSelection: Selection<IssueVariant>? = null
  private val scope = AndroidCoroutineScope(parentDisposable, AndroidDispatchers.uiThread)

  init {
    flow
      .mapNotNull {
        (it.issues as? LoadingState.Ready)?.value?.value?.selected?.let { issue ->
          issue to it.currentIssueVariants
        }
      }
      .distinctUntilChanged()
      .onEach { (issue, variantsLoadingState) ->
        when (variantsLoadingState) {
          is LoadingState.Ready -> {
            val variants = variantsLoadingState.value
            if (variants?.items.isNullOrEmpty()) {
              setDisableText(EMPTY_COMBOBOX_MESSAGE)
            } else {
              val variantSize = variants!!.items.size
              if (currentVariantSelection?.items != variants.items) {
                model.removeAllElements()
                val allItem = issue.toVariantRow(variantSize)
                model.addElement(HeaderRow)
                model.addElement(allItem)
                model.addAll(variants.items.map { it.toVariantRow() })
                model.selectedItem = allItem
              }
              if (currentVariantSelection?.selected != variants.selected) {
                if (variants.selected == null) {
                  model.selectedItem = issue.toVariantRow(variantSize)
                } else {
                  model.selectedItem = variants.selected!!.toVariantRow()
                }
              }
              currentVariantSelection = variants
              model.enabled = true
            }
          }
          is LoadingState.Failure -> {
            setDisableText(FAILURE_COMBOBOX_MESSAGE)
          }
          is LoadingState.Loading -> {
            setDisableText(LOADING_COMBOBOX_MESSAGE)
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

  private fun setDisableText(message: String) {
    model.removeAllElements()
    model.addElement(DisabledTextRow(message))
    model.enabled = false
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
