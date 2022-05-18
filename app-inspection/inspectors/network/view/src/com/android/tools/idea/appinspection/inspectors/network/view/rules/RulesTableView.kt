/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view.rules

import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorClient
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleDataListener
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RulesTableModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import studio.network.inspection.NetworkInspectorProtocol
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class RulesTableView(
  private val client: NetworkInspectorClient,
  private val scope: CoroutineScope,
  model: NetworkInspectorModel
) {
  val component: JComponent

  val tableModel = RulesTableModel()
  val table = TableView(tableModel)
  private var orderedRules = listOf<Int>()

  init {
    val decorator = ToolbarDecorator.createDecorator(table).setAddAction {
      tableModel.addRow(createRuleDataWithListener())
      val selectedRow = tableModel.rowCount - 1
      table.selectionModel.setSelectionInterval(selectedRow, selectedRow)
    }.setRemoveAction {
      val index = table.selectedRow
      if (index < 0) {
        return@setRemoveAction
      }
      val ruleData = table.selectedObject ?: return@setRemoveAction
      tableModel.removeRow(table.convertRowIndexToModel(index))
      scope.launch {
        if (ruleData.isActive) {
          client.interceptResponse(NetworkInspectorProtocol.InterceptCommand.newBuilder().apply {
            interceptRuleRemovedBuilder.apply {
              ruleId = ruleData.id
            }
          }.build())
        }
      }
    }
    component = createDecoratedTable(table, decorator)
    table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    table.selectionModel.addListSelectionListener {
      val row = table.selectedObject ?: return@addListSelectionListener
      model.setSelectedRule(row)
    }
    tableModel.addTableModelListener {
      reorderRules()
    }
  }

  private fun createRuleDataWithListener(): RuleData {
    val id = RuleData.newId()
    return RuleData(id, "New Rule", true, object : RuleDataListener {
      override fun onRuleDataChanged(ruleData: RuleData) {
        if (ruleData.isActive) {
          scope.launch {
            client.interceptResponse(NetworkInspectorProtocol.InterceptCommand.newBuilder().apply {
              interceptRuleAddedBuilder.apply {
                ruleId = id
                rule = ruleData.toProto()
              }
            }.build())
          }
        }
      }

      override fun onRuleNameChanged(ruleData: RuleData) {
        val index = tableModel.indexOf(ruleData)
        if (index != -1) {
          tableModel.fireTableCellUpdated(index, 1)
        }
      }

      override fun onRuleIsActiveChanged(ruleData: RuleData) {
        scope.launch {
          client.interceptResponse(NetworkInspectorProtocol.InterceptCommand.newBuilder().apply {
            if (ruleData.isActive) {
              interceptRuleAddedBuilder.apply {
                ruleId = ruleData.id
                rule = ruleData.toProto()
              }
            }
            else {
              interceptRuleRemovedBuilder.apply {
                ruleId = ruleData.id
              }
            }
          }.build())
        }
      }
    })
  }

  private fun reorderRules() {
    val newOrderedRules = tableModel.items.filter { it.isActive }.map { it.id }
    if (newOrderedRules != orderedRules) {
      orderedRules = newOrderedRules
      scope.launch {
        client.interceptResponse(NetworkInspectorProtocol.InterceptCommand.newBuilder().apply {
          reorderInterceptRulesBuilder.addAllRuleId(orderedRules)
        }.build())
      }
    }
  }
}
