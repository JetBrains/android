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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType
import com.android.tools.idea.gradle.structure.configurables.ui.properties.renderAnyTo
import com.android.tools.idea.gradle.structure.configurables.ui.toRenderer
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.*
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsVariable
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.helpers.parseAny
import com.android.tools.idea.gradle.structure.model.meta.*
import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableUtil
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
import javax.swing.*
import javax.swing.SwingUtilities.invokeLater
import javax.swing.border.EmptyBorder
import javax.swing.event.ChangeEvent
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.TreePath

private const val NAME = 0
private const val UNRESOLVED_VALUE = 1

/**
 * Main table for the Variables view in the Project Structure Dialog
 */
class VariablesTable(private val project: Project, private val psProject: PsProject, private val parentDisposable: Disposable) :
  TreeTable(createTreeModel(ProjectShadowNode(psProject), parentDisposable)) {

  private val iconGap = JBUI.scale(2)
  private val editorInsets = JBUI.insets(1, 2)
  private val iconSize = JBUI.scale(16)

  init {
    setProcessCursorKeys(false)

    // We replace the automatically synced selection model from [TreeTable] with a simpler one but such that honors column selection.
    fun updateTreeSelection() {
      if (selectedColumn <= 0) {  // It is -1 when a tree node is being expanded/collapsed.
        tree.selectionModel.selectionPath = tree.getPathForRow(selectedRow)
      }
      else {
        tree.selectionModel.clearSelection()
      }
    }
    this.columnModel.selectionModel.addListSelectionListener { updateTreeSelection() }
    this.selectionModel.addListSelectionListener { updateTreeSelection() }
    tree.selectionModel = DefaultTreeSelectionModel()

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
        val userObject = (value as VariablesBaseNode).userObject
        if (userObject is NodeDescription) {
          icon = userObject.icon
          iconTextGap = iconGap
          ipad = editorInsets
        } else {
          icon = EmptyIcon.ICON_16
        }
      }
    })

    isStriped = true
    tree.isRootVisible = false
    tree.expandRow(0)
    tree.expandRow(1)
    tableModel.setTree(tree)
    setRowSelectionAllowed(false)
    columnSelectionAllowed = false
    setCellSelectionEnabled(true)
  }

  fun deleteSelectedVariables() {
    removeEditor()
    tree.getSelectedNodes(BaseVariableNode::class.java, null).map { it.variable }.forEach { it.delete() }
  }

  fun addVariable(type: ValueType) {
    getCellEditor()?.stopCellEditing()
    val selectedNodes = tree.getSelectedNodes(VariablesBaseNode::class.java, null)
    if (selectedNodes.isEmpty()) {
      return
    }
    val moduleNode = let {
      var last = selectedNodes.last()
      while (last !is ModuleNode) {
        last = last.parent as VariablesBaseNode
      }
      last as ModuleNode
    }
    val emptyNode = EmptyVariableNode(moduleNode.variables, type)
    moduleNode.add(emptyNode)
    (tableModel as DefaultTreeModel).nodesWereInserted(moduleNode, IntArray(1) { moduleNode.getIndex(emptyNode) })
    tree.expandPath(TreePath(moduleNode.path))
    val emptyNodePath = TreePath(emptyNode.path)
    scrollRectToVisible(tree.getPathBounds(emptyNodePath))
    editCellAt(tree.getRowForPath(emptyNodePath), 0, NewVariableEvent(emptyNode))
  }

  private val coloredComponent = SimpleColoredComponent()

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer =
    TableCellRenderer { table, value, isSelected, _, rowIndex, columnIndex ->

      fun getDefaultComponent() =
        super.getCellRenderer(row, column).getTableCellRendererComponent(table, value, isSelected, false, rowIndex, columnIndex)

      fun getNodeRendered() = tree.getPathForRow(rowIndex).lastPathComponent as VariablesBaseNode

      when {
        column == UNRESOLVED_VALUE && getNodeRendered() is EmptyListItemNode ->
          (getDefaultComponent() as JLabel).apply { text = "Insert new value"; foreground = UIUtil.getInactiveTextColor() }

        column == NAME -> getDefaultComponent()

        else -> {
          coloredComponent.clear()
          if (!tree.isExpanded(rowIndex)) {
            (value as? ParsedValue<Any>)?.renderAnyTo(coloredComponent.toRenderer(), mapOf())
          }
          val rowSelected = table.isRowSelected(rowIndex)
          coloredComponent.foreground = if (rowSelected) table.selectionForeground else table.foreground
          coloredComponent.background = if (rowSelected) table.selectionBackground else table.background
          coloredComponent
        }
      }
    }

  override fun getCellEditor(row: Int, column: Int): TableCellEditor =
    when (column) {
      NAME -> NameCellEditor(row)
      UNRESOLVED_VALUE -> VariableCellEditor()
      else -> super.getCellEditor(row, column)
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
    val isCollapseKey = e?.keyCode == KeyEvent.VK_LEFT || e?.keyCode == KeyEvent.VK_MINUS
    val isExpandKey = e?.keyCode == KeyEvent.VK_RIGHT || e?.keyCode == KeyEvent.VK_PLUS
    fun currentNodeCanExpand() = (tree.selectionPath.lastPathComponent as? VariablesBaseNode)?.isLeaf == false
    val toBeExpandedOrCollapsed =
      !isEditing && selectedColumn == 0 &&
      (isCollapseKey && tree.isExpanded(selectedRow) ||
       (isExpandKey && !tree.isExpanded(selectedRow) && currentNodeCanExpand()))
    setProcessCursorKeys(toBeExpandedOrCollapsed)
    super.processKeyEvent(e)
    if (toBeExpandedOrCollapsed) {
      columnModel.selectionModel.setSelectionInterval(0, 0)
    }
  }

  override fun editingStopped(e: ChangeEvent?) {
    val rowBeingEdited = editingRow
    super.editingStopped(e)
    val nodeBeingEdited = tree.getPathForRow(rowBeingEdited)?.lastPathComponent
    if (nodeBeingEdited is EmptyVariableNode) {
      (tableModel as DefaultTreeModel).removeNodeFromParent(nodeBeingEdited)
    }
  }

  override fun editingCanceled(e: ChangeEvent?) {
    val rowBeingEdited = editingRow
    super.editingCanceled(e)
    val nodeBeingEdited = tree.getPathForRow(rowBeingEdited)?.lastPathComponent
    if (nodeBeingEdited is EmptyVariableNode) {
      (tableModel as DefaultTreeModel).removeNodeFromParent(nodeBeingEdited)
    }
  }

  /**
   * Table cell editor that reproduces the layout of a tree element
   */
  inner class NameCellEditor(private val row: Int) : AbstractTableCellEditor() {
    private val textBox = VariableAwareTextBox(project)

    init {
      addTabKeySupportTo(textBox)
      textBox.addTextListener(ActionListener { stopCellEditing() })
      textBox.addFocusListener(object : FocusAdapter() {
        override fun focusLost(e: FocusEvent?) {
          stopCellEditing()
          super.focusLost(e)
        }
      })
      textBox.border = BorderFactory.createMatteBorder(editorInsets.top, editorInsets.left, editorInsets.bottom, editorInsets.right,
                                                       this@VariablesTable.selectionBackground)
      textBox.componentPopupMenu = null
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
      val nodeBeingEdited = (table as VariablesTable).tree.getPathForRow(row).lastPathComponent as VariablesBaseNode
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

      panel.addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          val focusManager = IdeFocusManager.findInstanceByComponent(panel)
          focusManager.requestFocus(textBox, true)
        }
      })
      return panel
    }

    override fun getCellEditorValue(): Any? = textBox.text
  }

  inner class VariableCellEditor : AbstractTableCellEditor() {
    private val textBox = VariableAwareTextBox(project)

    init {
      addTabKeySupportTo(textBox)
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
      if (value !is ParsedValue<*>) {
        return null
      }
      val nodeBeingEdited = (table as VariablesTable).tree.getPathForRow(row).lastPathComponent
      if (nodeBeingEdited is BaseVariableNode) {
        textBox.setVariants(nodeBeingEdited.variable.scopePsVariables.map { it.name })
      }
      textBox.text = value.getText { toString() }
      return textBox
    }

    override fun getCellEditorValue(): Any? = VariablePropertyContextStub.parseEditorText(textBox.text)
  }

  private fun addTabKeySupportTo(editor: JComponent) {
    fun selectCell(row: Int, column: Int) {
      this.selectionModel.setSelectionInterval(row, row)
      this.columnModel.selectionModel.setSelectionInterval(column, column)
    }

    fun nextCell(e: ActionEvent) {
      if (isEditing) {
        val editPosition = editingRow to editingColumn
        TableUtil.stopEditing(this)
        generateSequence(editPosition) {
          tree.expandRow(it.first)
          (if (it.second >= 1) it.first + 1 to 0 else it.first to it.second + 1)
        }
          .drop(1)
          .takeWhile { it.first < tree.rowCount }
          .firstOrNull { model.isCellEditable(it.first, it.second) }
          ?.let { (row, column) ->
            selectionModel.setSelectionInterval(row, row)
            scrollRectToVisible(this.getCellRect(row, column, true))
            selectCell(row, column)
            invokeLater { editCellAt(row, column, e) }
          }
      }
    }

    fun prevCell(e: ActionEvent) {
      if (isEditing) {
        val editPosition = editingRow to editingColumn
        TableUtil.stopEditing(this)
        generateSequence(editPosition) {
          var (nextRow, nextColumn) = if (it.second <= 0) it.first - 1 to 1 else it.first to it.second - 1
          var totalRows = tree.rowCount
          while (!tree.isExpanded(nextRow)) {
            tree.expandRow(nextRow)
            if (totalRows == tree.rowCount) {
              break
            }
            else {
              nextRow += tree.rowCount - totalRows
            }
            totalRows = tree.rowCount
          }
          nextRow to nextColumn
        }
          .drop(1)
          .takeWhile { it.first >= 0 }
          .firstOrNull { model.isCellEditable(it.first, it.second) }
          ?.let { (row, column) ->
            selectionModel.setSelectionInterval(row, row)
            scrollRectToVisible(this.getCellRect(row, column, true))
            selectCell(row, column)
            invokeLater { editCellAt(row, column, e) }
          }
      }
    }
    editor.registerKeyboardAction(::nextCell, KeyStroke.getKeyStroke("TAB"), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    editor.registerKeyboardAction(::prevCell, KeyStroke.getKeyStroke("shift pressed TAB"), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
  }

  class VariablesTableModel internal constructor(internal val root: VariablesBaseNode) : DefaultTreeModel(root), TreeTableModel {
    private var tableTree: JTree? = null

    override fun getColumnCount(): Int = 2

    override fun getColumnName(column: Int): String =
      when (column) {
        NAME -> "Name"
        UNRESOLVED_VALUE -> "Value"
        else -> ""
      }

    override fun getColumnClass(column: Int): Class<*> =
      when (column) {
        NAME -> TreeTableModel::class.java
        else -> String::class.java
      }

    override fun getValueAt(node: Any?, column: Int): Any? =
      when (column) {
        NAME -> node.toString()
        UNRESOLVED_VALUE -> (node as? BaseVariableNode)?.getUnresolvedValue(tableTree?.isExpanded(TreePath(node.path)) == true)
                            ?: ParsedValue.NotSet
        else -> ""
      }

    override fun isCellEditable(node: Any?, column: Int): Boolean =
      when (column) {
        NAME -> node is VariableNode || node is MapItemNode || node is EmptyVariableNode || node is EmptyMapItemNode
        UNRESOLVED_VALUE -> {
          if (node is VariableNode) {
            val literalValue = node.variable.value.maybeLiteralValue
            literalValue !is Map<*, *> && literalValue !is List<*>
          }
          else {
            node is BaseVariableNode || node is EmptyListItemNode
          }
        }
        else -> false
      }

    override fun setValueAt(aValue: Any?, node: Any?, column: Int) {
      if (getValueAt(node, column) == (aValue as? Annotated<*>)?.value ?: aValue) return

      when (aValue) {
        is String -> when {
          node is EmptyVariableNode && column == NAME -> {
            val parentNode = node.parent as? ShadowedTreeNode
            val variable = node.createVariable(aValue)
            if (parentNode != null) {
              tableTree?.expandPath(
                TreePath(
                  getPathToRoot(
                    parentNode
                      .childNodes
                      .find { (it.shadowNode as? VariableShadowNode)?.variable === variable }
                  )))
            }
          }
          node is EmptyMapItemNode && column == NAME -> node.createVariable(aValue)
          node is BaseVariableNode && column == NAME -> {
            node.setName(aValue)
            nodeChanged(node)
          }

        }
        is Annotated<*> -> when {
          node is EmptyListItemNode && column == UNRESOLVED_VALUE && aValue.value is ParsedValue.Set<Any> ->
            node.createVariable(aValue.value)
          node !is BaseVariableNode -> Unit
          column == UNRESOLVED_VALUE && aValue.value is ParsedValue<Any> -> {
            node.setValue(aValue.value)
            nodeChanged(node)
          }
        }
      }
    }

    override fun setTree(tree: JTree?) {
      tableTree = tree
    }
  }
}

open class VariablesBaseNode(override val shadowNode: ShadowNode) : DefaultMutableTreeNode(null), ShadowedTreeNode {
  override fun dispose() = Unit
}

class ModuleNode(znode: ShadowNode, val variables: PsVariablesScope) : VariablesBaseNode(znode) {
  init {
    userObject = NodeDescription(variables.name, variables.model.icon ?: StudioIcons.Shell.Filetree.GRADLE_FILE)
  }
}

abstract class BaseVariableNode(znode: ShadowNode, val variable: PsVariable) : VariablesBaseNode(znode) {
  abstract fun getUnresolvedValue(expanded: Boolean): ParsedValue<Any>
  abstract fun setName(newName: String)

  fun setValue(newValue: ParsedValue<Any>) {
    variable.value = newValue
  }
}

class EmptyVariableNode(private val variablesScope: PsVariablesScope, val type: ValueType) : VariablesBaseNode(FakeShadowNode) {
  fun createVariable(name: String): PsVariable =
    when (type) {
      ValueType.LIST -> variablesScope.addNewListVariable(name)
      ValueType.MAP -> variablesScope.addNewMapVariable(name)
      else -> variablesScope.addNewVariable(name)
  }
}

class VariableNode(znode: ShadowNode, variable: PsVariable) : BaseVariableNode(znode, variable) {
  init {
    userObject = NodeDescription(variable.name, when {
      variable.isMap -> AllIcons.Json.Object
      variable.isList -> AllIcons.Json.Array
      else -> AllIcons.Nodes.C_plocal
    })
  }
  
  override fun getUnresolvedValue(expanded: Boolean): ParsedValue<Any> = variable.value

  override fun setName(newName: String) {
    (userObject as NodeDescription).name = newName
    variable.setName(newName)
  }
}

class ListItemNode(znode: ShadowNode, index: Int, variable: PsVariable) : BaseVariableNode(znode, variable) {
  init {
    userObject = index
  }

  override fun getUnresolvedValue(expanded: Boolean): ParsedValue<Any> = variable.value

  override fun setName(newName: String) {
    throw UnsupportedOperationException("List item indices cannot be renamed")
  }
}

class EmptyListItemNode(znode: ShadowNode, private val containingList: PsVariable) : VariablesBaseNode(znode) {
  fun createVariable(value: ParsedValue<Any>): PsVariable = containingList.addListValue(value)
}

class MapItemNode(znode: ShadowNode, key: String, variable: PsVariable) : BaseVariableNode(znode, variable) {
  init {
    userObject = key
  }

  override fun getUnresolvedValue(expanded: Boolean): ParsedValue<Any> {
    return variable.value
  }

  override fun setName(newName: String) {
    userObject = newName
    variable.setName(newName)
  }
}

class EmptyMapItemNode(znode: ShadowNode, private val containingMap: PsVariable) : VariablesBaseNode(znode) {
  fun createVariable(key: String) = containingMap.addMapValue(key)
}

class NodeDescription(var name: String, val icon: Icon) {
  override fun toString() = name
}

class NewVariableEvent(source: Any) : EventObject(source)

/**
 * Creates a tree model from a tree of shadow nodes which is auto-updated on changes made to the shadow tree.
 */
internal fun createTreeModel(root: ShadowNode, parentDisposable: Disposable): VariablesTable.VariablesTableModel =
  VariablesTable.VariablesTableModel(VariablesBaseNode(root).also { Disposer.register(parentDisposable, it) })
    .also { it.initializeNode(it.root, from = root) }

internal data class ProjectShadowNode(val project: PsProject) : ShadowNode {
  override fun getChildrenModels(): Collection<ShadowNode> =
    listOf(RootModuleShadowNode(project)) + project.modules.sortedBy { it.name }.map { ModuleShadowNode(it) }

  override fun createNode(): VariablesBaseNode = VariablesBaseNode(this)
  override fun onChange(disposable: Disposable, listener: () -> Unit) = project.modules.onChange(disposable, listener)
}

internal data class RootModuleShadowNode(val project: PsProject) : ShadowNode {
  override fun getChildrenModels(): Collection<ShadowNode> = project.variables.map { VariableShadowNode(it) }
  override fun createNode(): VariablesBaseNode = ModuleNode(this, project.variables)
  override fun onChange(disposable: Disposable, listener: () -> Unit) = project.variables.onChange(disposable, listener)
}

internal data class ModuleShadowNode(val module: PsModule) : ShadowNode {
  override fun getChildrenModels(): Collection<ShadowNode> = module.variables.map { VariableShadowNode(it) }
  override fun createNode(): VariablesBaseNode = ModuleNode(this, module.variables)
  override fun onChange(disposable: Disposable, listener: () -> Unit) = module.variables.onChange(disposable, listener)
}

internal data class MapEntryShadowNode(val variable: PsVariable, val key: String) : ShadowNode {
  override fun createNode(): VariablesBaseNode = MapItemNode(this, key, variable.mapEntries.findElement(key)!!)
}

internal data class MapEmptyEntryShadowNode(val variable: PsVariable) : ShadowNode {
  override fun createNode(): VariablesBaseNode = EmptyMapItemNode(this, variable)
}

internal data class ListItemShadowNode(val variable: PsVariable, val index: Int) : ShadowNode {
  override fun createNode(): VariablesBaseNode = ListItemNode(this, index, variable.listItems.findElement(index)!!)
}

internal data class ListEmptyItemShadowNode(val variable: PsVariable, val index: Int) : ShadowNode {
  override fun createNode(): VariablesBaseNode = EmptyListItemNode(this, variable)
}

internal data class VariableShadowNode(val variable: PsVariable) : ShadowNode {
  override fun getChildrenModels(): Collection<ShadowNode> {
    return when {
      variable.isMap ->
        variable.mapEntries.entries.map { MapEntryShadowNode(variable, it.key) } +
        MapEmptyEntryShadowNode(variable)
      variable.isList ->
        variable.listItems.entries.map { ListItemShadowNode(variable, it.key) } +
        ListEmptyItemShadowNode(variable, variable.listItems.size)
      else -> listOf()
    }
  }

  override fun createNode(): VariablesBaseNode = VariableNode(this, variable)
  override fun onChange(disposable: Disposable, listener: () -> Unit) = when {
    // For now, variables cannot get converted between meta types.
    variable.isMap -> variable.mapEntries.onChange(disposable, listener)
    variable.isList -> variable.listItems.onChange(disposable, listener)
    else -> Unit
  }
}

private object VariablePropertyContextStub: PropertyContextStub<Any>() {
  override fun parse(value: String) = parseAny(value)
}
