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
import com.android.tools.idea.gradle.structure.model.PsVariable
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.Component
import java.awt.event.*
import java.util.*
import java.util.function.Consumer
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.tree.*

private const val NAME = 0
private const val UNRESOLVED_VALUE = 1
private const val RESOLVED_VALUE = 2

/**
 * Main table for the Variables view in the Project Structure Dialog
 */
class VariablesTable(private val project: Project, private val context: PsContext) :
  TreeTable(VariablesTableModel(DefaultMutableTreeNode())) {

  private val iconGap = JBUI.scale(2)
  private val editorInsets = JBUI.insets(1, 2)
  private val iconSize = JBUI.scale(16)

  init {
    setRowHeight(iconSize + editorInsets.top + editorInsets.bottom)
    setTreeCellRenderer(object : NodeRenderer() {
      override fun customizeCellRenderer(tree: JTree,
                                         value: Any?,
                                         selected: Boolean,
                                         expanded: Boolean,
                                         leaf: Boolean,
                                         row: Int,
                                         hasFocus: Boolean) {
        super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
        if (value is EmptyMapItemNode) {
          append("Insert new key", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
        val userObject = (value as DefaultMutableTreeNode).userObject
        if (userObject is NodeDescription) {
          icon = userObject.icon
          iconTextGap = iconGap
          ipad = editorInsets
        }
      }
    })
    isStriped = true
    fillTable()
    tableModel.setTree(tree)
  }

  private fun fillTable() {
    fun createRoot(variablesScope: PsVariablesScope): ModuleNode {
      val moduleVariables = variablesScope.getModuleVariables()
      val moduleRoot = ModuleNode(variablesScope)
      moduleVariables.map { VariableNode(it) }.sortedBy { it.variable.getName() }.forEach { moduleRoot.add(it) }
      return moduleRoot
    }

    val moduleNodes = mutableListOf<ModuleNode>()
    context.project.forEachModule(Consumer { module -> module.variables.let { variables -> moduleNodes.add(createRoot(variables)) } })
    moduleNodes.sortBy { it.variables.name }
    moduleNodes.add(0, createRoot(context.project.variables))

    moduleNodes.forEach { (tableModel.root as DefaultMutableTreeNode).add(it) }
    tree.expandRow(0)
    tree.expandRow(1)
    tree.isRootVisible = false
  }

  fun deleteSelectedVariables() {
    removeEditor()
    val selectedNodes = tree.getSelectedNodes(BaseVariableNode::class.java, null)
    for (node in selectedNodes) {
      node.variable.delete()
      val model = tableModel as DefaultTreeModel
      if (node is ListItemNode) {
        var sibling = node.nextSibling
        while (sibling is ListItemNode) {
          sibling.updateIndex(sibling.getIndex() - 1)
          model.nodeChanged(sibling)
          sibling = sibling.nextSibling
        }
      }
      model.removeNodeFromParent(node)
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

    val emptyNode = EmptyNode(last.variables, type)
    last.add(emptyNode)
    (tableModel as DefaultTreeModel).nodesWereInserted(last, IntArray(1) { last.getIndex(emptyNode) })
    tree.expandPath(TreePath(last.path))
    editCellAt(tree.getRowForPath(TreePath(emptyNode.path)), 0, NewVariableEvent(emptyNode))
  }

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
    val defaultRenderer = super.getCellRenderer(row, column)
    return TableCellRenderer { table, value, isSelected, _, rowIndex, columnIndex ->
      val nodeRendered = tree.getPathForRow(rowIndex).lastPathComponent as DefaultMutableTreeNode
      val component = defaultRenderer.getTableCellRendererComponent(table, value, isSelected, false, rowIndex, columnIndex)
      if (nodeRendered is EmptyListItemNode && column == UNRESOLVED_VALUE) {
        component.foreground = UIUtil.getInactiveTextColor()
        (component as JLabel).text = "Insert new value"
      } else {
        component.foreground = if (table.isRowSelected(rowIndex)) table.selectionForeground else table.foreground
      }
      component
    }
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

  override fun processKeyEvent(e: KeyEvent?) {
    if (e?.keyCode == KeyEvent.VK_ENTER && e.modifiers == 0) {
      val rows = selectedRows
      if (rows.size == 1) {
        val selectedRow = rows[0]
        if (isCellEditable(selectedRow, 0)) {
          editCellAt(selectedRow, 0)
          return
        }
        if (isCellEditable(selectedRow, 1)) {
          editCellAt(selectedRow, 1)
          return
        }
      }
    }
    super.processKeyEvent(e)
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
      if (e is NewVariableEvent) {
        textBox.setPlaceholder("Variable name")
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
        panel.add(
          Box.createHorizontalStrut(bounds.x - (tree.ui as BasicTreeUI).rightChildIndent + 1 - Math.ceil(icon.iconWidth / 2.0).toInt()))
        panel.add(iconLabel)
        panel.add(Box.createHorizontalStrut((tree.ui as BasicTreeUI).rightChildIndent - 1 - Math.floor(icon.iconWidth / 2.0).toInt()))
      }
      else {
        panel.add(Box.createHorizontalStrut(bounds.x))
      }
      val userObject = nodeBeingEdited.userObject
      if (userObject is NodeDescription) {
        val nodeIconLabel = JLabel(userObject.icon)
        nodeIconLabel.border = EmptyBorder(editorInsets.top, editorInsets.left, editorInsets.bottom, iconGap)
        panel.add(nodeIconLabel)
      }
      panel.add(textBox)
      panel.background = table.selectionBackground
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
      textBox.border = BorderFactory.createMatteBorder(editorInsets.top, editorInsets.left, editorInsets.bottom, editorInsets.right,
                                                       this@VariablesTable.selectionBackground)
    }

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component? {
      if (value !is String) {
        return null
      }
      val nodeBeingEdited = (table as VariablesTable).tree.getPathForRow(row).lastPathComponent
      if (nodeBeingEdited is BaseVariableNode) {
        textBox.setVariants(nodeBeingEdited.variable.scopePsVariables.getModuleVariables().map { it.getName() })
      }
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
        RESOLVED_VALUE -> "Actual value"
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
        NAME -> node is VariableNode || node is MapItemNode || node is EmptyNode || node is EmptyMapItemNode
        UNRESOLVED_VALUE -> {
          if (node is VariableNode) {
            val type = node.variable.valueType
            type != ValueType.MAP && type != ValueType.LIST
          }
          else {
            node is BaseVariableNode || node is EmptyListItemNode
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
        tableTree!!.expandPath(TreePath(variableNode.path))
        return
      }

      if (node is EmptyMapItemNode && column == NAME) {
        val variableNode = node.createVariableNode(aValue) ?: return
        val parent = node.parent as VariableNode
        val index = parent.getIndex(node)
        val treeModel = tableTree!!.model as DefaultTreeModel
        treeModel.removeNodeFromParent(node)
        treeModel.insertNodeInto(variableNode, parent, index)
        treeModel.insertNodeInto(EmptyMapItemNode(parent.variable), parent, index + 1)
        return
      }

      if (node is EmptyListItemNode && column == UNRESOLVED_VALUE) {
        val variableNode = node.createVariableNode(aValue)
        val parent = node.parent as VariableNode
        val index = parent.getIndex(node)
        val treeModel = tableTree!!.model as DefaultTreeModel
        treeModel.removeNodeFromParent(node)
        treeModel.insertNodeInto(variableNode, parent, index)
        treeModel.insertNodeInto(EmptyListItemNode(parent.variable), parent, index + 1)
        return
      }

      if (node !is BaseVariableNode) {
        return
      }

      if (column == NAME) {
        node.setName(aValue)
        nodeChanged(node)
      }
      else if (column == UNRESOLVED_VALUE) {
        node.setValue(aValue)
        nodeChanged(node)
      }
    }

    override fun setTree(tree: JTree?) {
      tableTree = tree
    }
  }

  class ModuleNode(val variables: PsVariablesScope) : DefaultMutableTreeNode(
    NodeDescription(variables.name, StudioIcons.Shell.Filetree.GRADLE_FILE))

  abstract class BaseVariableNode(val variable: PsVariable) : DefaultMutableTreeNode() {
    abstract fun getUnresolvedValue(expanded: Boolean): String
    abstract fun getResolvedValue(expanded: Boolean): String
    abstract fun setName(newName: String)
    fun setValue(newValue: String) = variable.setValue(newValue)
  }

  class EmptyNode(private val variablesScope: PsVariablesScope, val type: ValueType) : DefaultMutableTreeNode() {
    fun createVariableNode(name: String): BaseVariableNode {
      val property = variablesScope.container.findProperty(name)
      if (type == ValueType.LIST) {
        property.convertToEmptyList()
      }
      else if (type == ValueType.MAP) {
        property.convertToEmptyMap()
      }
      val variable = PsVariable(property, property.resolve(), variablesScope.model, variablesScope)
      return VariableNode(variable)
    }
  }

  class VariableNode(variable: PsVariable) : BaseVariableNode(variable) {
    init {
      when (variable.valueType) {
        GradlePropertyModel.ValueType.MAP -> {
          variable.getUnresolvedValue(MAP_TYPE)?.forEach {
            add(MapItemNode(it.key, PsVariable(it.value, it.value.resolve(), variable.model, variable.scopePsVariables)))
          }
          add(EmptyMapItemNode(variable))
          userObject = NodeDescription(variable.getName(), EmptyIcon.ICON_0)
        }
        GradlePropertyModel.ValueType.LIST -> {
          val list = variable.getUnresolvedValue(LIST_TYPE)
          if (list != null) {
            list.forEachIndexed { index, propertyModel ->
              add(ListItemNode(index, PsVariable(propertyModel, propertyModel.resolve(), variable.model, variable.scopePsVariables)))
            }
            add(EmptyListItemNode(variable))
          }
          userObject = NodeDescription(variable.getName(), EmptyIcon.ICON_0)
        }
        else -> {
          userObject = NodeDescription(variable.getName(), EmptyIcon.ICON_0)
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
      if (variable.getDependencies().isEmpty()) {
        return ""
      }
      if (variable.resolvedValueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(resolvedValue)
      }
      return resolvedValue
    }

    override fun setName(newName: String) {
      (userObject as NodeDescription).name = newName
      variable.setName(newName)
    }
  }

  class ListItemNode(index: Int, variable: PsVariable) : BaseVariableNode(variable) {
    init {
      userObject = index
    }

    override fun getUnresolvedValue(expanded: Boolean): String {
      val value = variable.getUnresolvedValue(STRING_TYPE) ?: ""
      if (variable.valueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(value)
      }
      return value
    }

    override fun getResolvedValue(expanded: Boolean): String {
      val resolvedValue = variable.getResolvedValue(STRING_TYPE) ?: ""
      if (variable.getDependencies().isEmpty()) {
        return ""
      }
      if (variable.resolvedValueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(resolvedValue)
      }
      return resolvedValue
    }

    override fun setName(newName: String) {
      throw UnsupportedOperationException("List item indices cannot be renamed")
    }

    fun updateIndex(newIndex: Int) {
      userObject = newIndex
    }

    fun getIndex() = userObject as Int
  }

  class EmptyListItemNode(private val containingList: PsVariable) : DefaultMutableTreeNode() {
    fun createVariableNode(value: String): ListItemNode {
      val newVariable = containingList.addListValue(value)
      return ListItemNode(parent.getIndex(this), newVariable)
    }
  }

  class MapItemNode(key: String, variable: PsVariable) : BaseVariableNode(variable) {
    init {
      userObject = key
    }

    override fun getUnresolvedValue(expanded: Boolean): String {
      val value = variable.getUnresolvedValue(STRING_TYPE) ?: ""
      if (variable.valueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(value)
      }
      return value
    }

    override fun getResolvedValue(expanded: Boolean): String {
      val resolvedValue = variable.getResolvedValue(STRING_TYPE) ?: ""
      if (variable.getDependencies().isEmpty()) {
        return ""
      }
      if (variable.resolvedValueType == ValueType.STRING) {
        return StringUtil.wrapWithDoubleQuote(resolvedValue)
      }
      return resolvedValue
    }

    override fun setName(newName: String) {
      userObject = newName
      variable.setName(newName)
    }
  }

  class EmptyMapItemNode(private val containingMap: PsVariable) : DefaultMutableTreeNode() {
    fun createVariableNode(key: String): MapItemNode? {
      val newVariable = containingMap.addMapValue(key) ?: return null
      return MapItemNode(key, newVariable)
    }
  }
}

class NodeDescription(var name: String, val icon: Icon) {
  override fun toString() = name
}

class NewVariableEvent(source: Any) : EventObject(source)
