/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.displayList.columns

import com.android.tools.idea.deviceManager.avdmanager.actions.PhysicalDeviceUiAction
import com.android.tools.idea.deviceManager.displayList.NamedDevice
import com.android.tools.idea.deviceManager.displayList.PhysicalDeviceActionPanel
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.ColumnInfo
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class PhysicalDeviceActionsColumnInfo(
  name: String,
  private val width: Int = -1,
  private val deviceProvider: PhysicalDeviceUiAction.PhysicalDeviceProvider
): ColumnInfo<NamedDevice, NamedDevice>(name) {
  override fun valueOf(item: NamedDevice?) = item

  override fun getRenderer(device: NamedDevice)  = getComponent(device)

  // TODO(qumeric): override comporator for sorting, see AvdACtionsColumnInfo

  // TODO(qumeric): do we need cache here? See AvdActionsColumnInfo
  fun getComponent(device: NamedDevice?) = ActionRenderer(2, device, deviceProvider)

  override fun getEditor(device: NamedDevice?): TableCellEditor = getComponent(device)

  override fun isCellEditable(device: NamedDevice?): Boolean = true

  override fun getWidth(table: JTable): Int = width

  class ActionRenderer(
    private var numVisibleActions: Int, device: NamedDevice?, deviceProvider: PhysicalDeviceUiAction.PhysicalDeviceProvider
  ) : AbstractTableCellEditor(), TableCellRenderer {
    // FIXME(qumeric)
    val component = PhysicalDeviceActionPanel(deviceProvider, numVisibleActions)

    private fun getComponent(table: JTable, row: Int, column: Int) = component.apply {
      if (table.selectedRow == row) {
        background = table.selectionBackground
        foreground = table.selectionForeground
        setHighlighted(true)
      }
      else {
        background = table.background
        foreground = table.foreground
        setHighlighted(false)
      }
      setFocused(table.selectedRow == row && table.selectedColumn == column)
    }

    //fun cycleFocus(backward: Boolean): Boolean = component.cycleFocus(backward)

    override fun getTableCellRendererComponent(
      table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component = getComponent(table, row, column)

    override fun getTableCellEditorComponent(
      table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int
    ): Component = getComponent(table, row, column)

    override fun getCellEditorValue(): Any? = null
  }
}
