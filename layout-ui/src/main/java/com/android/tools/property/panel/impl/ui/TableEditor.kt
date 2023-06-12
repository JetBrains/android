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
package com.android.tools.property.panel.impl.ui

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.adtui.stdui.registerAnActionKey
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.impl.model.TableEditingRequest
import com.android.tools.property.panel.impl.model.TableLineModelImpl
import com.android.tools.property.panel.impl.model.TableRowEditListener
import com.android.tools.property.panel.impl.model.TextFieldPropertyEditorModel
import com.android.tools.property.panel.impl.support.HelpSupportBinding
import com.android.tools.property.ptable.ColumnFraction
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableCellEditorProvider
import com.android.tools.property.ptable.PTableCellRendererProvider
import com.android.tools.property.ptable.PTableColumn
import com.android.tools.property.ptable.PTableGroupItem
import com.android.tools.property.ptable.PTableItem
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.text.Matcher
import com.intellij.util.ui.JBUI
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.SwingUtilities

private const val DEFAULT_ROW_HEIGHT = 24
private const val MINIMUM_ROW_HEIGHT = 20

/**
 * A standard table control for editing multiple properties in a tabular form.
 */
class TableEditor(val lineModel: TableLineModelImpl,
                  rendererProvider: PTableCellRendererProvider,
                  editorProvider: PTableCellEditorProvider,
                  val actions: List<AnAction> = emptyList(),
                  nameColumnFraction: ColumnFraction = ColumnFraction()) {

  private val table = PTable.create(lineModel.tableModel, lineModel, rendererProvider, editorProvider, { getToolTipText(it) }, ::updateUI,
                                    nameColumnFraction)
  val component = table.component as JTable

  init {
    component.background = secondaryPanelBackground
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
      val index = component.selectedRow
      val item = if (index >= 0 && index < component.rowCount) component.getValueAt(index, 1) as? PTableItem else null
      lineModel.selectedItem = item
    }
    HelpSupportBinding.registerHelpKeyActions(component, { lineModel.selectedItem as? PropertyItem })

    // In the properties panel we do not want the table to handle it's own navigation.
    // Ignore the events and allow the scrollPane created in PropertiesPage to handle the events.
    component.registerActionKey({}, KeyStrokes.PAGE_UP, "pageUp", { false })
    component.registerActionKey({}, KeyStrokes.PAGE_DOWN, "pageDown", { false })

    // Register all single keystroke shortcuts.
    // Ignore the shortscuts with double keystrokes since we cannot add them to the InputMap of the table.
    // In Intellij there are special handling of these double keystrokes that happens before the normal swing
    // key stroke delegation. Here we do not want to take keystrokes away from the cell editors.
    actions.forEach { action ->
      action.shortcutSet.shortcuts
        .filterIsInstance<KeyboardShortcut>()
        .filter { it.secondKeyStroke == null }
        .forEach {
          component.registerAnActionKey({ action }, it.firstKeyStroke, action.templatePresentation.description)
        }
    }
  }

  private fun updateUI() {
    // The font height may change on a LaF change: recompute and set the row height.
    component.rowHeight = computeRowHeight()
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
      TableEditingRequest.SELECT -> selectItem(item)
      else -> {}
    }
  }

  private fun selectItem(item: PTableItem?) {
    if (item != null) {
      for (row in 0 until component.rowCount) {
        if (component.getValueAt(row, 0) == item) {
          component.setRowSelectionInterval(row, row)
          return
        }
      }
    }
    // If there is no row to select transfer the focus out of the table:
    component.transferFocusBackward()
  }

  private fun getToolTipText(event: MouseEvent): String? {
    val tableRow = component.rowAtPoint(event.point)
    val tableColumn = component.columnAtPoint(event.point)
    if (tableRow < 0 || tableColumn < 0) {
      return null
    }
    val item = component.getValueAt(tableRow, tableColumn)
    val renderer = component.getCellRenderer(tableRow, tableColumn)
    val cell = renderer.getTableCellRendererComponent(component, item, false, false, tableRow, tableColumn) ?: return null
    val rect = component.getCellRect(tableRow, tableColumn, true)
    cell.setBounds(0, 0, rect.width, rect.height)
    val control = SwingUtilities.getDeepestComponentAt(cell, event.x - rect.x, event.y - rect.y) as? JComponent
    return control?.getToolTipText(event)
  }

  private fun computeRowHeight(): Int {
    val property = lineModel.tableModel.items.find { it is PropertyItem } as? PropertyItem ?: return JBUI.scale(DEFAULT_ROW_HEIGHT)
    val textField = PropertyTextField(TextFieldPropertyEditorModel(property, true))
    return Integer.max(textField.preferredSize.height, JBUI.scale(MINIMUM_ROW_HEIGHT))
  }

  // TODO(b/123090421): Move this to TableLineModelImpl
  private fun findRowOf(itemToEdit: PTableItem?): Int {
    val count = table.itemCount
    for (i in 0 until count) {
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
    for (row in 0 until count) {
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
