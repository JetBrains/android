/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.selector

import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import javax.swing.table.TableColumn
import javax.swing.table.TableModel

internal class SelectMultipleDevicesDialogTable : JBTable() {
  init {
    val header = getTableHeader()
    header.setReorderingAllowed(false)
    header.setResizingAllowed(false)
    setDefaultEditor(Boolean::class.java, BooleanTableCellEditor())
    setRowHeight(JBUI.scale(40))
    setRowSelectionAllowed(false)
  }

  var selectedTargets: List<DeploymentTarget>
    get() = (dataModel as SelectMultipleDevicesDialogTableModel).selectedTargets
    set(selectedTargets) {
      (dataModel as SelectMultipleDevicesDialogTableModel).selectedTargets = selectedTargets
    }

  fun isSelected(viewRowIndex: Int): Boolean {
    val modelRowIndex = convertRowIndexToModel(viewRowIndex)
    return dataModel.getValueAt(
      modelRowIndex,
      SelectMultipleDevicesDialogTableModel.SELECTED_MODEL_COLUMN_INDEX,
    ) as Boolean
  }

  @VisibleForTesting
  fun setSelected(selected: Boolean, viewRowIndex: Int) {
    dataModel.setValueAt(
      selected,
      convertRowIndexToModel(viewRowIndex),
      SelectMultipleDevicesDialogTableModel.SELECTED_MODEL_COLUMN_INDEX,
    )
  }

  @get:VisibleForTesting
  val data: List<List<Any>>
    get() {
      val data: MutableList<List<Any>> = ArrayList(1 + getRowCount())
      val columnNames = (0 until columnCount).map { getColumnName(it) }
      data.add(columnNames)
      for (row in 0 until rowCount) {
        data.add(getRowAt(row))
      }
      return data
    }

  private fun getRowAt(rowIndex: Int): List<Any> =
    (0 until columnCount).map { columnIndex -> getValueAt(rowIndex, columnIndex) }

  override fun setModel(model: TableModel) {
    super.setModel(model)
    if (tableHeader == null) {
      return
    }
    if (columnCount == 0) {
      return
    }
    setSelectedAndIconColumnMaxWidthsToFit()
  }

  private fun setSelectedAndIconColumnMaxWidthsToFit() {
    setMaxWidthToFit(
      convertColumnIndexToView(SelectMultipleDevicesDialogTableModel.SELECTED_MODEL_COLUMN_INDEX)
    )
    setMaxWidthToFit(
      convertColumnIndexToView(SelectMultipleDevicesDialogTableModel.TYPE_MODEL_COLUMN_INDEX)
    )
  }

  private fun setMaxWidthToFit(viewColumnIndex: Int) {
    val maxPreferredWidth =
      (-1 until rowCount).maxOf { rowIndex -> getPreferredWidth(rowIndex, viewColumnIndex) }
    getColumnModel().getColumn(viewColumnIndex).setMaxWidth(maxPreferredWidth)
  }

  private fun getPreferredWidth(viewRowIndex: Int, viewColumnIndex: Int): Int {
    val component =
      if (viewRowIndex == -1) {
        val name = getColumnName(viewColumnIndex)
        getTableHeader()
          .defaultRenderer
          .getTableCellRendererComponent(this, name, false, false, -1, viewColumnIndex)
      } else {
        prepareRenderer(
          getCellRenderer(viewRowIndex, viewColumnIndex),
          viewRowIndex,
          viewColumnIndex,
        )
      }
    return component.preferredSize.width + JBUI.scale(8)
  }

  override fun createDefaultColumnsFromModel() {
    while (columnModel.columnCount != 0) {
      columnModel.removeColumn(columnModel.getColumn(0))
    }
    for (column in 0 until dataModel.columnCount) {
      if (notAllValuesEqualEmptyString(column)) {
        addColumn(TableColumn(column))
      }
    }
  }

  private fun notAllValuesEqualEmptyString(columnIndex: Int): Boolean =
    (0 until dataModel.rowCount).any { rowIndex ->
      dataModel.getValueAt(rowIndex, columnIndex) != ""
    }
}
