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

import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorClient
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData.Companion.getLatestId
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData.Companion.newId
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleDataListener
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RulesPersistentStateComponent
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RulesTableModel
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NetworkInspectorBundle
import com.intellij.execution.RunManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.TableUtil
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.event.TableModelEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import studio.network.inspection.NetworkInspectorProtocol

class RulesTableView(
  project: Project,
  private val client: NetworkInspectorClient,
  private val scope: CoroutineScope,
  private val model: NetworkInspectorModel,
  private val usageTracker: NetworkInspectorTracker,
) {

  private val persistentStateComponent: RulesPersistentStateComponent = project.service()
  val component: JComponent
  val tableModel: RulesTableModel =
    RulesTableModel(persistentStateComponent.state.rulesList).also { scope.initPersistentRules() }
  val table = TableView(tableModel)

  init {
    val decorator =
      ToolbarDecorator.createDecorator(table)
        .setAddAction {
          val ruleData = createRuleDataWithListener(newId())
          addRule(ruleData)
        }
        .setRemoveAction {
          val isConfirmed =
            MessageDialogBuilder.okCancel(
                NetworkInspectorBundle.message("confirmation.title"),
                NetworkInspectorBundle.message("confirmation.rule"),
              )
              .ask(table)
          if (!isConfirmed) return@setRemoveAction
          val index = table.selectedRow
          if (index < 0) {
            return@setRemoveAction
          }
          val ruleData = table.selectedObject ?: return@setRemoveAction
          if (TableUtil.doRemoveSelectedItems(table, tableModel, null)) {
            IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
              IdeFocusManager.getGlobalInstance().requestFocus(table, true)
            }
            TableUtil.updateScroller(table)
            model.setSelectedRule(null)
            scope.launch {
              client.interceptResponse(
                NetworkInspectorProtocol.InterceptCommand.newBuilder()
                  .apply { interceptRuleRemovedBuilder.apply { ruleId = ruleData.id } }
                  .build()
              )
            }
          }
        }
        .addExtraAction(CloneRuleAction())
    component = decorator.createPanel()
    table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    table.selectionModel.addListSelectionListener {
      if (it.valueIsAdjusting) {
        return@addListSelectionListener // Only handle listener on last event, not intermediate
        // events
      }
      val row = table.selectedObject ?: return@addListSelectionListener
      model.setSelectedRule(row)
    }
    tableModel.addTableModelListener { event ->
      // The event is generated as a result of a move up or down action when the
      // following conditions are true.
      if (event.type == TableModelEvent.UPDATE && event.lastRow != event.firstRow) {
        reorderRules()
      }
    }
    table.addMouseListener(
      object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          val row = table.rowAtPoint(e.point)
          if (row != -1) {
            model.detailContent = NetworkInspectorModel.DetailContent.RULE
          }
        }
      }
    )
    ActionToolbarUtil.makeToolbarNavigable(decorator.actionsPanel.toolbar)
    table.registerTabKeyAction { table.transferFocus() }
    table.registerEnterKeyAction {
      if (table.selectedRow != -1) {
        model.detailContent = NetworkInspectorModel.DetailContent.RULE
      }
    }
  }

  private fun addRule(ruleData: RuleData) {
    tableModel.addRow(ruleData)
    val selectedRow = tableModel.rowCount - 1
    table.selectionModel.setSelectionInterval(selectedRow, selectedRow)
    model.detailContent = NetworkInspectorModel.DetailContent.RULE
    scope.launch {
      client.interceptResponse(
        NetworkInspectorProtocol.InterceptCommand.newBuilder()
          .apply {
            interceptRuleAddedBuilder.apply {
              ruleId = ruleData.id
              rule = ruleData.toProto()
            }
          }
          .build()
      )
    }
    usageTracker.trackRuleCreated()
  }

  private fun createNewRuleDataListener() =
    object : RuleDataListener {
      override fun onRuleDataChanged(ruleData: RuleData) {
        if (ruleData.isActive) {
          scope.launch {
            client.interceptResponse(
              NetworkInspectorProtocol.InterceptCommand.newBuilder()
                .apply {
                  interceptRuleUpdatedBuilder.apply {
                    ruleId = ruleData.id
                    rule = ruleData.toProto()
                  }
                }
                .build()
            )
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
          client.interceptResponse(
            NetworkInspectorProtocol.InterceptCommand.newBuilder()
              .apply {
                interceptRuleUpdatedBuilder.apply {
                  ruleId = ruleData.id
                  rule = ruleData.toProto()
                }
              }
              .build()
          )
        }
      }
    }

  private fun createRuleDataWithListener(id: Int): RuleData {
    val name = RunManager.suggestUniqueName("New Rule", table.items.map { it.name })
    return RuleData(id, name, true).apply { ruleDataListener = createNewRuleDataListener() }
  }

  private fun reorderRules() {
    scope.launch {
      client.interceptResponse(
        NetworkInspectorProtocol.InterceptCommand.newBuilder()
          .apply { reorderInterceptRulesBuilder.addAllRuleId(tableModel.items.map { it.id }) }
          .build()
      )
    }
  }

  private fun CoroutineScope.initPersistentRules() = launch {
    persistentStateComponent.state.rulesList.forEach { ruleData ->
      ruleData.ruleDataListener = createNewRuleDataListener()
      client.interceptResponse(
        NetworkInspectorProtocol.InterceptCommand.newBuilder()
          .apply {
            interceptRuleAddedBuilder.apply {
              ruleId = ruleData.id
              rule = ruleData.toProto()
            }
          }
          .build()
      )

      // Increment the ID to the current ID so that new rule ID don't conflict with existing ones.
      while (getLatestId() < ruleData.id) {
        newId()
      }
    }
  }

  private inner class CloneRuleAction :
    DumbAwareAction("Clone", "Clone rule", AllIcons.Actions.Copy) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = table.selectedObject != null
    }

    override fun actionPerformed(e: AnActionEvent) {
      val oldRule = table.selectedObject ?: return
      val ruleData = createRuleDataWithListener(newId())
      ruleData.copyFrom(oldRule)
      ruleData.name = RunManager.suggestUniqueName(ruleData.name, table.items.map { it.name })
      addRule(ruleData)
    }
  }
}
