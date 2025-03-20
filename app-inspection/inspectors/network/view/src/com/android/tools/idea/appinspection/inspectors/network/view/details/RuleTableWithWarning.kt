/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker.InterceptionCriteria
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData.TransformationRuleData
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NetworkInspectorBundle
import com.android.tools.idea.appinspection.inspectors.network.view.rules.registerTabKeyAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.TableUtil
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import icons.StudioIcons
import javax.swing.JComponent
import javax.swing.JPanel

internal class RuleTableWithWarning(
  private val model: ListTableModel<TransformationRuleData>,
  name: String,
  private val usageTracker: NetworkInspectorTracker,
  private val validate: (ListTableModel<TransformationRuleData>) -> String?,
) : JPanel(TabularLayout("*,Fit", "*")) {
  private val table = createTable(model, name)
  private val warningLabel =
    JBLabel(StudioIcons.Common.WARNING).apply {
      this.name = "${name}WarningLabel"
      isVisible = false
      border = JBUI.Borders.emptyLeft(5)
    }

  init {
    add(table, TabularLayout.Constraint(0, 0))
    add(warningLabel, TabularLayout.Constraint(0, 1))
    validateContent()
  }

  fun validateContent(): Boolean {
    val warning = validate(model)
    val isValid = warning == null
    warningLabel.isVisible = !isValid
    warningLabel.toolTipText = warning
    return isValid
  }

  private fun createTable(model: ListTableModel<TransformationRuleData>, name: String): JComponent {
    val table = TableView(model)
    table.registerTabKeyAction { table.transferFocus() }
    table.name = name
    val decorator = ToolbarDecorator.createDecorator(table)

    val trackAction: (TransformationRuleData) -> Unit = { newItem ->
      val component =
        when (newItem) {
          is RuleData.HeaderAddedRuleData -> InterceptionCriteria.ADD_HEADER
          is RuleData.HeaderReplacedRuleData -> InterceptionCriteria.FIND_REPLACE_HEADER
          is RuleData.BodyReplacedRuleData -> InterceptionCriteria.REPLACE_BODY
          is RuleData.BodyModifiedRuleData -> InterceptionCriteria.FIND_REPLACE_BODY
          else -> null
        }
      component?.let { usageTracker.trackRuleUpdated(it) }
    }

    val addRowAction: (TransformationRuleData) -> Unit = { newItem ->
      model.addRow(newItem)
      val index = table.convertRowIndexToView(model.rowCount - 1)
      table.selectionModel.setSelectionInterval(index, index)
      trackAction(newItem)
      validateContent()
    }
    decorator.setAddAction {
      when (model) {
        is RuleData.HeaderRulesTableModel -> HeaderRuleDialog(null, addRowAction).show()
        is RuleData.BodyRulesTableModel -> BodyRuleDialog(null, addRowAction).show()
      }
    }
    decorator.setRemoveAction {
      val message =
        when (model) {
          is RuleData.HeaderRulesTableModel -> NetworkInspectorBundle.message("confirmation.header")
          else -> NetworkInspectorBundle.message("confirmation.body")
        }
      val isConfirmed =
        MessageDialogBuilder.okCancel(NetworkInspectorBundle.message("confirmation.title"), message)
          .ask(table)
      if (isConfirmed) {
        if (TableUtil.doRemoveSelectedItems(table, model, null)) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
            IdeFocusManager.getGlobalInstance().requestFocus(table, true)
          }
          TableUtil.updateScroller(table)
        }
      }
    }

    val replaceRowAction: (TransformationRuleData) -> Unit = { newItem ->
      val selectedItem = table.selectedObject
      val replaceIndex = model.items.indexOf(selectedItem)
      if (replaceIndex != -1) {
        model.items = model.items.map { if (it == selectedItem) newItem else it }
        model.fireTableRowsUpdated(replaceIndex, replaceIndex)
        val tableIndex = table.convertRowIndexToView(replaceIndex)
        table.selectionModel.setSelectionInterval(tableIndex, tableIndex)
        trackAction(newItem)
        validateContent()
      }
    }
    decorator.setEditAction {
      val selectedItem = table.selectedObject
      when (model) {
        is RuleData.HeaderRulesTableModel -> HeaderRuleDialog(selectedItem, replaceRowAction).show()
        is RuleData.BodyRulesTableModel -> BodyRuleDialog(selectedItem, replaceRowAction).show()
      }
    }

    val decoratedTableView = decorator.createPanel()
    val infoLabel =
      JBLabel(AllIcons.General.Information).apply {
        border = JBUI.Borders.emptyRight(8)
        isEnabled = false
        toolTipText = "Order of rules indicate execution order."
      }
    decorator.actionsPanel.setToolbarLabel(infoLabel, ActionToolbarPosition.RIGHT)
    ActionToolbarUtil.makeToolbarNavigable(decorator.actionsPanel.toolbar)
    return decoratedTableView
  }
}
