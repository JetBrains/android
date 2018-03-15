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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.table.TableCellEditor
import javax.swing.tree.*

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
        moduleVariables.map({ VariableNode(it) }).sortedBy { it.variable.getName() }.forEach { moduleRoot.add(it) }
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

  fun addVariable(type: ValueType) {
    val selectedNodes = tree.getSelectedNodes(DefaultMutableTreeNode::class.java, null)
    if (selectedNodes.isEmpty()) {
      return
    }
    var last = selectedNodes.last()
    while (last !is ModuleNode) {
      last = last.parent as DefaultMutableTreeNode
    }

    val emptyNode = EmptyNode(last.module, type)
    last.add(emptyNode)
    (tableModel as DefaultTreeModel).nodesWereInserted(last, IntArray(1) { last.getIndex(emptyNode) })
    tree.expandPath(TreePath(last.path))
    editCellAt(tree.getRowForPath(TreePath(emptyNode.path)), 0)
  }

  override fun getCellEditor(row: Int, column: Int): TableCellEditor {
    if (column == NAME) {
      return NameCellEditor(row)
    }
    if (column == UNRESOLVED_VALUE) {
      return VariableCellEditor()
    }
    return super.getCellEditor(row, column)
  }

  /**
   * Table cell editor that reproduces the layout of a tree element
   */
  inner class NameCellEditor(private val row: Int) : AbstractTableCellEditor() {
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

    override fun isCellEditable(e: EventObject?): Boolean {
      // Do not trigger editing when clicking left of the text, so that editing does not interfere with tree expansion
      val bounds = tree.getRowBounds(row)
      if (e is MouseEvent && e.x < bounds.x) {
        return false
      }
      return super.isCellEditable(e)
    }

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
      // Reproduce the tree element layout (see BasicTreeUI)
      val panel = JPanel()
      panel.layout = BoxLayout(panel, BoxLayout.LINE_AXIS)
      val nodeBeingEdited = (table as VariablesTable).tree.getPathForRow(row).lastPathComponent as DefaultMutableTreeNode
      val bounds = tree.getRowBounds(row)
      if (!nodeBeingEdited.isLeaf) {
        val icon = UIUtil.getTreeNodeIcon(tree.isExpanded(row), isSelected, tree.hasFocus())
        val iconLabel = JLabel(icon)
        val extraHeight = bounds.height - icon.iconHeight
        iconLabel.border = EmptyBorder(Math.ceil(extraHeight / 2.0).toInt(), 0, Math.floor(extraHeight / 2.0).toInt(), 0)
        panel.add(iconLabel, BorderLayout.LINE_START)
        panel.add(Box.createHorizontalStrut(bounds.x - (tree.ui as BasicTreeUI).rightChildIndent + 1 - Math.ceil(icon.iconWidth / 2.0).toInt()))
        panel.add(iconLabel)
        panel.add(Box.createHorizontalStrut((tree.ui as BasicTreeUI).rightChildIndent - 1 - Math.floor(icon.iconWidth / 2.0).toInt()))
      } else {
        panel.add(Box.createHorizontalStrut(bounds.x))
      }
      panel.add(textBox)
      panel.background = table.background
      textBox.text = value as String
      return panel
    }

    override fun getCellEditorValue(): Any? = textBox.text
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
      val nodeBeingEdited = (table as VariablesTable).tree.getPathForRow(row).lastPathComponent
      if (nodeBeingEdited is BaseVariableNode) {
        textBox.setVariants(nodeBeingEdited.variable.module.variables.getModuleVariables().map { it.getName() })
        textBox.text = value
        return textBox
      }
      return null
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
      if (node is ModuleNode && column == NAME) {
        return node.toString()
      }

      if (node !is BaseVariableNode) {
        return ""
      }

      val isExpanded = tableTree?.isExpanded(TreePath(node.path)) == true
      return when (column) {
        NAME -> node.toString()
        UNRESOLVED_VALUE -> node.getUnresolvedValue(isExpanded)
        RESOLVED_VALUE -> node.getResolvedValue(isExpanded)
        else -> ""
      }
    }

    override fun isCellEditable(node: Any?, column: Int): Boolean {
      return when (column) {
        NAME -> node is VariableNode || node is MapItemNode || node is EmptyNode
        UNRESOLVED_VALUE -> {
          if (node is VariableNode) {
            val type = node.variable.valueType
            type != ValueType.MAP && type != ValueType.LIST
          } else {
            node is BaseVariableNode
          }
        }
        else -> false
      }
    }

    override fun setValueAt(aValue: Any?, node: Any?, column: Int) {
      if (aValue !is String || aValue == getValueAt(node, column)) {
        return
      }

      if (node is EmptyNode && column == NAME) {
        val variableNode = node.createVariableNode(aValue)
        val parent = node.parent as MutableTreeNode
        val index = parent.getIndex(node)
        val treeModel = tableTree!!.model as DefaultTreeModel
        treeModel.removeNodeFromParent(node)
        treeModel.insertNodeInto(variableNode, parent, index)
        return
      }

      if (node !is BaseVariableNode) {
        return
      }

      if (column == NAME) {
        node.setName(aValue)
        nodeChanged(node)
      } else if (column == UNRESOLVED_VALUE) {
        node.setValue(aValue)
        nodeChanged(node)
      }
    }

    override fun setTree(tree: JTree?) {
      tableTree = tree
    }
  }

  class ModuleNode(val module: PsModule) : DefaultMutableTreeNode(module.name)

  abstract class BaseVariableNode(name: String, val variable: PsVariable) : DefaultMutableTreeNode(name) {
    abstract fun getUnresolvedValue(expanded: Boolean): String
    abstract fun getResolvedValue(expanded: Boolean): String
    abstract fun setName(newName: String)
    fun setValue(newValue: String) = variable.setValue(newValue)
  }

  class EmptyNode(val module: PsModule, val type: ValueType) : DefaultMutableTreeNode() {
    fun createVariableNode(name: String): BaseVariableNode {
      val property = module.parsedModel!!.ext().findProperty(name)
      if (type == ValueType.LIST) {
        property.convertToEmptyList()
      } else if (type == ValueType.MAP) {
        property.convertToEmptyMap()
      }
      val variable = PsVariable(property, module)
      return VariableNode(variable)
    }
  }

  class VariableNode(variable: PsVariable) : BaseVariableNode(variable.getName(), variable) {
    init {
      when (variable.valueType) {
        GradlePropertyModel.ValueType.MAP -> {
          variable.getUnresolvedValue(MAP_TYPE)?.forEach { add(MapItemNode(it.key, PsVariable(it.value, variable.module))) }
        }
        GradlePropertyModel.ValueType.LIST -> {
          variable.getUnresolvedValue(LIST_TYPE)
            ?.forEachIndexed { index, propertyModel -> add(ListItemNode(index, PsVariable(propertyModel, variable.module))) }
        }
        else -> {
        }
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

    override fun setName(newName: String) {
      setUserObject(newName)
      variable.setName(newName)
    }
  }

  class ListItemNode(val index: Int, variable: PsVariable) : BaseVariableNode(index.toString(), variable) {
    override fun getUnresolvedValue(expanded: Boolean): String {
      val value = variable.getUnresolvedValue(STRING_TYPE) ?: ""
      if (variable.valueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(value)
      }
      return value
    }

    override fun getResolvedValue(expanded: Boolean): String {
      val resolvedValue = variable.getResolvedValue(STRING_TYPE) ?: ""
      if (variable.valueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(resolvedValue)
      }
      return resolvedValue
    }

    override fun setName(newName: String) {
      throw UnsupportedOperationException("List item indices cannot be renamed")
    }
  }

  class MapItemNode(val key: String, variable: PsVariable) : BaseVariableNode(key, variable) {
    override fun getUnresolvedValue(expanded: Boolean): String {
      val value = variable.getUnresolvedValue(STRING_TYPE) ?: ""
      if (variable.valueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(value)
      }
      return value
    }

    override fun getResolvedValue(expanded: Boolean): String {
      val resolvedValue = variable.getResolvedValue(STRING_TYPE) ?: ""
      if (variable.valueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(resolvedValue)
      }
      return resolvedValue
    }

    override fun setName(newName: String) {
      setUserObject(newName)
      variable.setName(newName)
    }
  }
}
