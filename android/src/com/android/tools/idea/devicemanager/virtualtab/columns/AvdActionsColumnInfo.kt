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
package com.android.tools.idea.devicemanager.virtualtab.columns

import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.avdmanager.AvdActionPanel
import com.android.tools.idea.avdmanager.AvdActionPanel.AvdRefreshProvider
import com.android.tools.idea.devicemanager.Tables
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
  private val projectOpen: Boolean,
  private val refreshProvider: AvdRefreshProvider
) : ColumnInfo<AvdInfo, AvdInfo>(name) {
  private val numVisibleActions = if (projectOpen) 3 else 2
  private val width = JBUI.scale(45) * numVisibleActions + JBUI.scale(75)

  /**
   * This cell renders an action panel for both the editor component and the display component
   */
  private val ourActionPanelRendererEditor = hashMapOf<AvdInfo?, ActionRenderer?>()
  override fun valueOf(avdInfo: AvdInfo): AvdInfo = avdInfo

  override fun getRenderer(avdInfo: AvdInfo): TableCellRenderer = getComponent(avdInfo)

  fun getComponent(avdInfo: AvdInfo?): ActionRenderer {
    var renderer = ourActionPanelRendererEditor[avdInfo]
    if (renderer == null) {
      renderer = ActionRenderer(numVisibleActions, avdInfo, projectOpen, refreshProvider)
      ourActionPanelRendererEditor[avdInfo] = renderer
    }
    return renderer
  }

  override fun getEditor(avdInfo: AvdInfo?): TableCellEditor = getComponent(avdInfo)

  override fun isCellEditable(avdInfo: AvdInfo): Boolean = true

  override fun getWidth(table: JTable): Int = width

  class ActionRenderer(private var numVisibleActions: Int,
                       info: AvdInfo?,
                       projectOpen: Boolean,
                       refreshProvider: AvdRefreshProvider) : AbstractTableCellEditor(), TableCellRenderer {
    val component = AvdActionPanel(refreshProvider, info!!, true, projectOpen, this.numVisibleActions)

    override fun getTableCellEditorComponent(table: JTable,
                                             value: Any,
                                             selected: Boolean,
                                             viewRowIndex: Int,
                                             viewColumnIndex: Int) = getTableCellComponent(table, selected)

    override fun getTableCellRendererComponent(table: JTable,
                                               value: Any,
                                               selected: Boolean,
                                               focused: Boolean,
                                               viewRowIndex: Int,
                                               viewColumnIndex: Int): Component {
      getTableCellComponent(table, selected)
      component.setFocused(false)

      return component
    }

    private fun getTableCellComponent(table: JTable, selected: Boolean): Component {
      component.background = Tables.getBackground(table, selected)
      component.foreground = Tables.getForeground(table, selected)
      component.setHighlighted(selected)

      return component
    }

    override fun getCellEditorValue(): Any? = null
  }
}
