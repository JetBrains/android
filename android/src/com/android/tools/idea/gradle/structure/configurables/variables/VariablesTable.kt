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
package com.android.tools.idea.gradle.structure.configurables.variables

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.*
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsVariable
import com.google.common.base.Joiner
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.AbstractTableCellEditor
import java.awt.Component
import java.awt.event.ActionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.table.TableCellEditor
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

private const val NAME = 0
private const val UNRESOLVED_VALUE = 1
private const val RESOLVED_VALUE = 2

/**
 * Main table for the Variables view in the Project Structure Dialog
 */
class VariablesTable(private val project: Project, private val context: PsContext) :
    TreeTable(VariablesTableModel(DefaultMutableTreeNode())) {

  init {
    fillTable()
    tableModel.setTree(tree)
  }

  private fun fillTable() {
    val moduleNodes = mutableListOf<ModuleNode>()
    context.project.forEachModule { module ->
      val moduleVariables = module.variables.getModuleVariables()
      if (!moduleVariables.isEmpty()) {
        val moduleRoot = ModuleNode(module)
        moduleNodes.add(moduleRoot)
        moduleVariables.map( { VariableNode(it) } ).sortedBy { it.variable.name }.forEach { moduleRoot.add(it) }
      }
    }
    moduleNodes.sortedBy { it.module.name }.forEach { (tableModel.root as DefaultMutableTreeNode).add(it) }
    tree.expandRow(0)
    tree.isRootVisible = false
  }

  fun deleteSelectedVariables() {
    removeEditor()
    val selectedNodes = tree.getSelectedNodes(VariableNode::class.java, null)
    for (node in selectedNodes) {
      node.variable.delete()
      (tableModel as DefaultTreeModel).removeNodeFromParent(node)
    }
  }

  override fun getCellEditor(row: Int, column: Int): TableCellEditor {
    if (column == UNRESOLVED_VALUE) {
      return VariableCellEditor()
    }
    return super.getCellEditor(row, column)
  }

  inner class VariableCellEditor : AbstractTableCellEditor() {
    private val textBox = VariableAwareTextBox(project)

    init {
      textBox.addTextListener(ActionListener { stopCellEditing() })
      textBox.addFocusListener(object : FocusAdapter() {
        override fun focusLost(e: FocusEvent?) {
          stopCellEditing()
          super.focusLost(e)
        }
      })
    }

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component? {
      if (value !is String) {
        return null
      }
      val nodeBeingEdited = (table as VariablesTable).tree.getPathForRow(row).lastPathComponent as VariableNode
      textBox.setVariants(nodeBeingEdited.variable.module.variables.getModuleVariables().map { it.name })
      textBox.text = value
      return textBox
    }

    override fun getCellEditorValue(): Any = textBox.text
  }

  class VariablesTableModel(root: TreeNode) : DefaultTreeModel(root), TreeTableModel {
    private var tableTree: JTree? = null

    override fun getColumnCount(): Int = 3

    override fun getColumnName(column: Int): String {
      return when (column) {
        NAME -> "Name"
        UNRESOLVED_VALUE -> "Value"
        RESOLVED_VALUE -> "Resolved value"
        else -> ""
      }
    }

    override fun getColumnClass(column: Int): Class<*> {
      if (column == NAME) {
        return TreeTableModel::class.java
      }
      return String::class.java
    }

    override fun getValueAt(node: Any?, column: Int): Any? {
      if (node !is BaseNode) {
        return ""
      }

      val isExpanded = tableTree?.isExpanded(TreePath(node.path)) == true
      return when (column) {
        NAME -> node.name
        UNRESOLVED_VALUE -> node.getUnresolvedValue(isExpanded)
        RESOLVED_VALUE -> node.getResolvedValue(isExpanded)
        else -> ""
      }
    }

    override fun isCellEditable(node: Any?, column: Int): Boolean {
      if (column == RESOLVED_VALUE || node !is VariableNode) {
        return false
      }
      if (column == NAME) {
        return true
      }
      val type = node.variable.valueType
      return type != ValueType.MAP && type != ValueType.LIST
    }

    override fun setValueAt(aValue: Any?, node: Any?, column: Int) {
      if (aValue == null || node !is VariableNode) {
        return
      }
      if (column == UNRESOLVED_VALUE) {
        node.variable.setValue(aValue)
        nodeChanged(node)
      }
    }

    override fun setTree(tree: JTree?) {
      tableTree = tree
    }
  }

  abstract class BaseNode(val name: String) : DefaultMutableTreeNode(name) {
    abstract fun getUnresolvedValue(expanded: Boolean): String
    abstract fun getResolvedValue(expanded: Boolean): String
  }

  inner class VariableNode(val variable: PsVariable) : BaseNode(variable.name) {
    init {
      when (variable.valueType) {
        GradlePropertyModel.ValueType.MAP -> {
          variable.getUnresolvedValue(MAP_TYPE)?.forEach { add(MapItemNode(it)) }
        }
        GradlePropertyModel.ValueType.LIST -> {
          variable.getUnresolvedValue(LIST_TYPE)?.forEachIndexed { index, propertyModel -> add(ListItemNode(index, propertyModel)) }
        }
        else -> {}
      }
    }

    override fun getUnresolvedValue(expanded: Boolean): String {
      if (expanded) {
        return ""
      }
      val type = variable.valueType
      return when (type) {
        GradlePropertyModel.ValueType.MAP -> {
          val unresolvedMapValue = variable.getUnresolvedValue(MAP_TYPE) ?: return ""
          unresolvedMapValue.entries.joinToString(prefix = "[", postfix = "]")
        }
        GradlePropertyModel.ValueType.LIST -> {
          val unresolvedListValue = variable.getUnresolvedValue(LIST_TYPE) ?: return ""
          unresolvedListValue.joinToString(prefix = "[", postfix = "]")
        }
        GradlePropertyModel.ValueType.STRING -> {
          val unresolvedValue = variable.getUnresolvedValue(STRING_TYPE) ?: return ""
          StringUtil.wrapWithDoubleQuote(unresolvedValue)
        }
        else -> variable.getUnresolvedValue(STRING_TYPE) ?: ""
      }
    }

    override fun getResolvedValue(expanded: Boolean): String {
      if (expanded) {
        return ""
      }
      val resolvedValue = variable.getResolvedValue(STRING_TYPE) ?: return ""
      if (variable.valueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(resolvedValue)
      }
      return resolvedValue
    }
  }

  class ModuleNode(val module: PsModule) : BaseNode(module.name) {
    override fun getUnresolvedValue(expanded: Boolean) = ""

    override fun getResolvedValue(expanded: Boolean) = ""
  }

  class ListItemNode(val index: Int, private val propertyModel: GradlePropertyModel) : BaseNode(index.toString()) {
    override fun getUnresolvedValue(expanded: Boolean): String {
      val value = propertyModel.getRawValue(STRING_TYPE) ?: ""
      if (propertyModel.valueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(value)
      }
      return value
    }

    override fun getResolvedValue(expanded: Boolean): String {
      val resolvedValue = propertyModel.getValue(STRING_TYPE) ?: ""
      if (propertyModel.valueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(resolvedValue)
      }
      return resolvedValue
    }
  }

  class MapItemNode(private val mapValue: Map.Entry<String, GradlePropertyModel>) : BaseNode(mapValue.key) {
    override fun getUnresolvedValue(expanded: Boolean): String {
      val propertyModel = mapValue.value
      val value = propertyModel.getRawValue(STRING_TYPE) ?: ""
      if (propertyModel.valueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(value)
      }
      return value
    }

    override fun getResolvedValue(expanded: Boolean): String {
      val propertyModel = mapValue.value
      val resolvedValue = propertyModel.getValue(STRING_TYPE) ?: ""
      if (propertyModel.valueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(resolvedValue)
      }
      return resolvedValue
    }
  }
}
