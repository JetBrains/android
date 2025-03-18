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
package com.android.tools.idea.appinspection.inspectors.network.view.rules

import com.android.tools.adtui.table.ConfigColumnTableAspect
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleVariable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.TableUtil
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

/** A dialog for managing Rule Variables. */
class RuleVariablesDialog(
  private val project: Project,
  private val variables: MutableList<RuleVariable>,
  private val rules: List<RuleData>,
  private val onRulesUpdated: (RuleData) -> Unit,
) : DialogWrapper(project) {

  private val dialogState = RuleVariablesDialogStateComponent.getInstance().state
  private val newVariables = variables.mapTo(mutableListOf()) { it.copy() }
  private val tableModel = RuleVariableTableModel(newVariables)
  private val table = JBTable(tableModel)

  init {
    init()
    title = "Rule Variables"
    pack()
  }

  override fun createCenterPanel(): JComponent {

    val decorator =
      ToolbarDecorator.createDecorator(table)
        .setAddAction { addRow() }
        .setRemoveAction { deleteRow() }
    decorator.setPreferredSize(
      JBUI.size(Dimension(dialogState.dialogWidth, dialogState.dialogHeight))
    )

    ConfigColumnTableAspect.apply(project, table, dialogState.columns)
    val panel = decorator.createPanel()
    panel.addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
          val dimension = e.component.size.unscale()
          dialogState.dialogWidth = dimension.width
          dialogState.dialogHeight = dimension.height
        }
      }
    )
    return panel
  }

  public override fun doValidate(): ValidationInfo? {
    val dups =
      tableModel.items.map { it.name }.groupBy { it }.filter { it.value.size > 1 }.map { it.key }
    if (dups.isEmpty()) {
      return null
    }
    return ValidationInfo("Duplicate variable names: ${dups.joinToString { it }}")
  }

  override fun doOKAction() {
    super.doOKAction()
    val originalVariables = variables.map { it.copy() }
    variables.clear()
    variables.addAll(newVariables)
    rules.forEach { ruleData ->
      val originalProto = ruleData.toProto(originalVariables)
      val newProto = ruleData.toProto(newVariables)
      if (originalProto.toByteString() != newProto.toByteString()) {
        onRulesUpdated(ruleData)
      }
    }
  }

  private fun addRow() {
    tableModel.addRow(RuleVariable(getUniqueName(), ""))
  }

  private fun deleteRow() {
    TableUtil.doRemoveSelectedItems(table, tableModel, null)
  }

  private fun getUniqueName(): String {
    val names = tableModel.items.mapTo(hashSetOf()) { it.name }
    return UniqueNameGenerator.generateUniqueName(
      /* defaultName = */ "NEW-VARIABLE",
      /* prefix = */ "",
      /* suffix = */ "",
      /* beforeNumber = */ "-",
      /* afterNumber = */ "",
    )
    /* validator = */ {
      !names.contains(it)
    }
  }

  private class RuleVariableTableModel(items: MutableList<RuleVariable>) :
    ListTableModel<RuleVariable>(arrayOf(nameColumn, valueColumn), items) {

    override fun setValueAt(aValue: Any, rowIndex: Int, columnIndex: Int) {
      val variable = items[rowIndex]
      when (columnIndex) {
        0 -> variable.name = aValue.toString()
        1 -> variable.value = aValue.toString()
      }
    }
  }

  private class VariableColumnInfo(name: String, private val getValue: RuleVariable.() -> String) :
    ColumnInfo<RuleVariable, String>(name) {
    override fun valueOf(item: RuleVariable) = item.getValue()

    override fun isCellEditable(item: RuleVariable) = true
  }

  companion object {
    private val nameColumn = VariableColumnInfo("Name") { name }
    private val valueColumn = VariableColumnInfo("Value") { value }

    val columnConfig =
      listOf(
        ConfigColumnTableAspect.ColumnInfo(nameColumn.name, 0.2),
        ConfigColumnTableAspect.ColumnInfo(valueColumn.name, 0.8),
      )
  }
}

private fun Dimension.unscale(): Dimension {
  val scale = JBUI.scale(1)
  return Dimension(width / scale, height / scale)
}
