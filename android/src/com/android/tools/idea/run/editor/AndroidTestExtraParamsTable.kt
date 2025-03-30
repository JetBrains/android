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
package com.android.tools.idea.run.editor

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.AnActionButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import java.awt.Component
import java.awt.Font
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * List table view for editing [AndroidTestExtraParam]s.
 *
 * @param showAddAndDeleteElementButton show add/delete element button if true
 * @param showRevertElementButton show revert element button (reset value to its original value) if true
 */
class AndroidTestExtraParamsTable(
  private val showAddAndDeleteElementButton: Boolean,
  private val showRevertElementButton: Boolean) : ListTableWithButtons<AndroidTestExtraParam>() {

  override fun createListModel() = ListTableModel<AndroidTestExtraParam>(NameColumnInfo(), ValueColumnInfo())

  override fun createElement() = AndroidTestExtraParam()

  override fun isEmpty(element: AndroidTestExtraParam) = element.NAME.isBlank()

  override fun cloneElement(variable: AndroidTestExtraParam) = variable.copy()

  override fun canDeleteElement(selection: AndroidTestExtraParam) = selection.ORIGINAL_VALUE_SOURCE == AndroidTestExtraParamSource.NONE

  override fun createAddAction() = if (showAddAndDeleteElementButton) super.createAddAction() else null

  override fun createRemoveAction() = if (showAddAndDeleteElementButton) super.createRemoveAction() else null

  override fun createExtraToolbarActions(): Array<AnActionButton> {
    return (if (showRevertElementButton) {
      val revertAction = object : AnActionButton(ActionsBundle.message("action.ChangesView.Revert.text"),
                                                 AllIcons.Actions.Rollback) {

        override fun getActionUpdateThread() = ActionUpdateThread.BGT

        override fun actionPerformed(e: AnActionEvent) {
          stopEditing()
          selection.forEach { selectedParam ->
            if (selectedParam.ORIGINAL_VALUE_SOURCE != AndroidTestExtraParamSource.NONE) {
              selectedParam.VALUE = selectedParam.ORIGINAL_VALUE
            }
          }
          setModified()
        }

        override fun isEnabled(): Boolean {
          return selection.any { selectedParam ->
            selectedParam.ORIGINAL_VALUE_SOURCE != AndroidTestExtraParamSource.NONE
            && selectedParam.VALUE != selectedParam.ORIGINAL_VALUE
          }
        }
      }
      arrayOf(revertAction)
    }
    else {
      emptyArray<AnActionButton>()
    })
  }

  /**
   * Represents a name column of [AndroidTestExtraParamsTable].
   *
   * The param name is unmodifiable if the param has the original value source and those unmodifiable cell will be
   * rendered differently (grayed out) from the regular cells.
   */
  private class NameColumnInfo : ElementsColumnInfoBase<AndroidTestExtraParam>("Name") {
    override fun valueOf(item: AndroidTestExtraParam) = item.NAME
    override fun getDescription(element: AndroidTestExtraParam) = null
    override fun isCellEditable(item: AndroidTestExtraParam) = item.ORIGINAL_VALUE_SOURCE == AndroidTestExtraParamSource.NONE
    override fun setValue(item: AndroidTestExtraParam, value: String) {
      item.NAME = value
    }

    override fun getCustomizedRenderer(item: AndroidTestExtraParam, renderer: TableCellRenderer): TableCellRenderer {
      return if (isCellEditable(item)) renderer else NonEditableTableCellRenderer
    }
  }

  /**
   * Represents a value column of [AndroidTestExtraParamsTable].
   *
   * The value is modifiable regardless of its original value source. When you modify value which has the original value,
   * the new value will be rendered differently to standout.
   */
  private class ValueColumnInfo : ElementsColumnInfoBase<AndroidTestExtraParam>("Value") {
    override fun valueOf(item: AndroidTestExtraParam) = item.VALUE
    override fun getDescription(element: AndroidTestExtraParam) = null
    override fun isCellEditable(item: AndroidTestExtraParam) = true
    override fun setValue(item: AndroidTestExtraParam, value: String) {
      item.VALUE = value
    }

    override fun getCustomizedRenderer(item: AndroidTestExtraParam, renderer: TableCellRenderer): TableCellRenderer {
      return if (item.ORIGINAL_VALUE_SOURCE == AndroidTestExtraParamSource.NONE
                 || item.VALUE == item.ORIGINAL_VALUE) renderer
      else ModifiedTableCellRenderer
    }
  }
}

/**
 * A custom table cell renderer for non-editable cells.
 */
private object NonEditableTableCellRenderer : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable,
                                             value: Any,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    component.isEnabled = table.isEnabled && (hasFocus || isSelected)
    return component
  }
}

/**
 * A custom table cell renderer for modified cells.
 */
private object ModifiedTableCellRenderer : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable,
                                             value: Any,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    component.font = component.font.deriveFont(Font.BOLD)
    if (!hasFocus && !isSelected) {
      component.foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
    }
    return component
  }
}