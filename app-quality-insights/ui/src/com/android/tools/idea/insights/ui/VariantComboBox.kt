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
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.max
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.VisibleForTesting

private const val LOADING_COMBOBOX_MESSAGE = "Loading variants..."
private const val EMPTY_COMBOBOX_MESSAGE = "No variants available."
private const val FAILURE_COMBOBOX_MESSAGE = "Failed to load variants."
private const val OFFLINE_COMBOBOX_MESSAGE = "Not available offline."

class VariantComboBox(flow: Flow<AppInsightsState>, parentDisposable: Disposable) :
  CommonComboBox<Row, DefaultCommonComboBoxModel<Row>>(
    DefaultCommonComboBoxModel<Row>("All variants").apply { editable = false }
  ),
  Disposable {
  private var isDisabledIndex = false
  private var currentVariantSelection: Selection<IssueVariant>? = null
  private val scope = AndroidCoroutineScope(this, AndroidDispatchers.uiThread)

  override fun getMinimumPopupWidth(): Int {
    var popupWidth = 100
    for (i in 0 until model.size) {
      popupWidth = max(popupWidth, model.getElementAt(i).getRendererComponent().preferredSize.width)
    }
    return popupWidth
  }

  init {
    Disposer.register(parentDisposable, this)
    font = font.deriveFont(JBFont.BOLD).deriveFont(JBFont.ITALIC)
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
              prototypeDisplayValue = model.selectedItem as? Row
              currentVariantSelection = variants
              model.enabled = true
            }
          }
          is LoadingState.NetworkFailure -> {
            setDisableText(OFFLINE_COMBOBOX_MESSAGE)
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
    renderer =
      object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
          list: JList<*>,
          value: Any,
          index: Int,
          isSelected: Boolean,
          cellHasFocus: Boolean
        ): Component {
          // index == -1 means it's trying to render the title of the combo box
          if (index == -1 && value is VariantRow) {
            return JPanel(BorderLayout()).apply {
              add(
                JBLabel("Variant: ").apply { font = font.deriveFont(JBFont.ITALIC) },
                BorderLayout.WEST
              )
              add(
                JBLabel(value.name).apply { font = font.deriveFont(JBFont.BOLD or JBFont.ITALIC) },
                BorderLayout.CENTER
              )
              border = JBUI.Borders.empty()
              verticalTextPosition = SwingConstants.CENTER
            }
          }
          return when (value) {
            is Row -> {
              value.getRendererComponent()
            }
            else -> super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
          }
        }
      }
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
    val disabledRow = DisabledTextRow(message)
    model.addElement(disabledRow)
    prototypeDisplayValue = disabledRow
    model.enabled = false
  }

  override fun dispose() {
    model.removeAllElements()
  }
}

@VisibleForTesting
fun AppInsightsIssue.toVariantRow(size: Int) =
  VariantRow(
    "All${if (size > 1) " ($size variants)" else ""}",
    issueDetails.eventsCount,
    issueDetails.impactedDevicesCount,
    null
  )

@VisibleForTesting
fun IssueVariant.toVariantRow() =
  VariantRow(id.takeLast(4), eventsCount, impactedDevicesCount, this)
