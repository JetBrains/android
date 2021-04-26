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
package com.android.tools.idea.devicemanager.displayList.columns

import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.avdmanager.AvdActionPanel
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/**
 * Custom table cell renderer that renders an action panel for a given AVD entry
 *
 */
class AvdActionsColumnInfo(
  name: String,
  private val numVisibleActions: Int = -1,
  private val refreshProvider: AvdActionPanel.AvdRefreshProvider
) : ColumnInfo<AvdInfo, AvdInfo>(name) {
  private val width: Int = if (numVisibleActions == -1) -1 else JBUI.scale(45) * numVisibleActions + JBUI.scale(75)

  /**
   * This cell renders an action panel for both the editor component and the display component
   */
  private val ourActionPanelRendererEditor = hashMapOf<AvdInfo?, ActionRenderer?>()
  override fun valueOf(avdInfo: AvdInfo): AvdInfo = avdInfo

  /**
   * We override the comparator here so that we can sort by healthy vs not healthy AVDs
   */
  override fun getComparator(): java.util.Comparator<AvdInfo> = Comparator { o1, o2 -> o1.status.compareTo(o2.status) }

  override fun getRenderer(avdInfo: AvdInfo): TableCellRenderer = getComponent(avdInfo)

  fun getComponent(avdInfo: AvdInfo?): ActionRenderer {
    var renderer = ourActionPanelRendererEditor[avdInfo]
    if (renderer == null) {
      renderer = ActionRenderer(numVisibleActions, avdInfo, refreshProvider)
      ourActionPanelRendererEditor[avdInfo] = renderer
    }
    return renderer
  }

  override fun getEditor(avdInfo: AvdInfo?): TableCellEditor = getComponent(avdInfo)

  override fun isCellEditable(avdInfo: AvdInfo): Boolean = true

  override fun getWidth(table: JTable): Int = width

  fun cycleFocus(info: AvdInfo?, backward: Boolean): Boolean = getComponent(info).cycleFocus(backward)

  class ActionRenderer(
    private var numVisibleActions: Int, info: AvdInfo?, refreshProvider: AvdActionPanel.AvdRefreshProvider
  ) : AbstractTableCellEditor(), TableCellRenderer {
    val component: AvdActionPanel = AvdActionPanel((info)!!, this.numVisibleActions, refreshProvider)

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

    fun cycleFocus(backward: Boolean): Boolean = component.cycleFocus(backward)

    override fun getTableCellRendererComponent(
      table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component = getComponent(table, row, column)

    override fun getTableCellEditorComponent(
      table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int
    ): Component = getComponent(table, row, column)

    override fun getCellEditorValue(): Any? = null
  }
}
