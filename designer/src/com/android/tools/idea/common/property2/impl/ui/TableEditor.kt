/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.property2.impl.ui

import com.android.tools.adtui.ptable2.PTable
import com.android.tools.adtui.ptable2.PTableCellEditorProvider
import com.android.tools.adtui.ptable2.PTableCellRendererProvider
import com.android.tools.adtui.ptable2.PTableColumn
import com.android.tools.adtui.ptable2.PTableGroupItem
import com.android.tools.adtui.ptable2.PTableItem
import com.android.tools.idea.common.property2.api.PropertyItem
import com.android.tools.idea.common.property2.impl.model.TableEditingRequest
import com.android.tools.idea.common.property2.impl.model.TableLineModelImpl
import com.android.tools.idea.common.property2.impl.model.TableRowEditListener
import com.android.tools.idea.common.property2.impl.model.TextFieldPropertyEditorModel
import com.android.tools.idea.common.property2.impl.support.HelpSupportBinding
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.text.Matcher
import com.intellij.util.ui.JBUI
import java.awt.event.MouseEvent
import javax.swing.JTable

private const val DEFAULT_ROW_HEIGHT = 24
private const val MINIMUM_ROW_HEIGHT = 20

/**
 * A standard table control for editing multiple properties in a tabular form.
 */
class TableEditor(val lineModel: TableLineModelImpl,
                  rendererProvider: PTableCellRendererProvider,
                  editorProvider: PTableCellEditorProvider) {

  private val table = PTable.create(lineModel.tableModel, lineModel, rendererProvider, editorProvider) { getToolTipText(it) }
  val component = table.component as JTable

  init {
    component.rowHeight = computeRowHeight()
    lineModel.addValueChangedListener(object : TableRowEditListener {
      override fun valueChanged() {
        handleValueChanged()
      }

      override fun editRequest(type: TableEditingRequest, item: PTableItem?) {
        handleEditRequest(type, item)
      }
    })
    component.selectionModel.addListSelectionListener {
      val model = lineModel.tableModel
      val index = component.selectedRow
      val item = if (index >= 0 && index < model.items.size) model.items[index] else null
      lineModel.selectedItem = item
    }
    HelpSupportBinding.registerHelpKeyActions(component, { lineModel.selectedItem as? PropertyItem })
  }

  private fun handleValueChanged() {
    component.isVisible = lineModel.visible
    table.filter = lineModel.filter
    lineModel.itemCount = table.itemCount
  }

  private fun handleEditRequest(request: TableEditingRequest, item: PTableItem?) {
    handleValueChanged()
    when (request) {
      TableEditingRequest.SPECIFIED_ITEM -> table.startEditing(findRowOf(item))
      TableEditingRequest.STOP_EDITING -> table.startEditing(-1)
      TableEditingRequest.BEST_MATCH -> table.startEditing(findRowOfBestMatch())
      else -> {}
    }
  }

  private fun getToolTipText(event: MouseEvent): String? {
    val tableRow = component.rowAtPoint(event.point)
    val tableColumn = component.columnAtPoint(event.point)
    val index = component.convertRowIndexToModel(tableRow)
    val column = PTableColumn.fromColumn(tableColumn)
    val property = component.model.getValueAt(index, tableColumn) as? PropertyItem
    return PropertyTooltip.setToolTip(component, event, property, column == PTableColumn.VALUE, property?.value.orEmpty())
  }

  private fun computeRowHeight(): Int {
    val property = lineModel.tableModel.items.find { it is PropertyItem } as? PropertyItem ?: return JBUI.scale(DEFAULT_ROW_HEIGHT)
    val textField = PropertyTextField(TextFieldPropertyEditorModel(property, true))
    return Integer.max(textField.preferredSize.height, JBUI.scale(MINIMUM_ROW_HEIGHT))
  }

  // TODO(b/123090421): Move this to TableLineModelImpl
  private fun findRowOf(itemToEdit: PTableItem?): Int {
    val count = table.itemCount
    for (i in 0..(count-1)) {
      val item = table.item(i)
      if (item == itemToEdit) {
        return i
      }
    }
    return -1
  }

  // TODO(b/123090421): Move this to TableLineModelImpl
  // TODO(b/123092243): Allow this to find items that are currently in a closed group item.
  private fun findRowOfBestMatch(): Int {
    if (lineModel.filter.isEmpty()) {
      return -1
    }
    val matcher = NameUtil.buildMatcher("*${lineModel.filter}").build()
    val count = table.itemCount
    var best: PTableItem? = null
    var bestRow = -1
    for (row in 0..(count-1)) {
      val item = table.item(row)
      if (isMatch(matcher, item) && isBetter(item, best)) {
        best = item
        bestRow = row
      }
    }
    return bestRow
  }

  private fun isMatch(matcher: Matcher, item: PTableItem): Boolean =
    when (item) {
      is PTableGroupItem -> matcher.matches(item.name)
      else -> true
    }

  private fun isBetter(item: PTableItem, best: PTableItem?): Boolean =
    when {
      !lineModel.tableModel.isCellEditable(item, PTableColumn.VALUE) -> false
      best == null -> true
      else -> item.name.length < best.name.length
    }
}
