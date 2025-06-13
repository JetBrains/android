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
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.ui.PropertyEditorValidator
import com.android.tools.idea.gradle.structure.configurables.ui.properties.ModelPropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.properties.PropertyCellEditor
import com.android.tools.idea.gradle.structure.configurables.ui.properties.renderAnyTo
import com.android.tools.idea.gradle.structure.configurables.ui.properties.toSelectedTextRenderer
import com.android.tools.idea.gradle.structure.configurables.ui.simplePropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.stringPropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.toRenderer
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.ShadowNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.ShadowedTreeNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.childNodes
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.initializeNode
import com.android.tools.idea.gradle.structure.configurables.ui.uiProperty
import com.android.tools.idea.gradle.structure.model.PsBuildScript
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsVariable
import com.android.tools.idea.gradle.structure.model.PsVariables
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.PsVersionCatalog
import com.android.tools.idea.gradle.structure.model.android.DISALLOWED_IN_NAME
import com.android.tools.idea.gradle.structure.model.android.DISALLOWED_MESSAGE
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.maybeLiteralValue
import com.android.tools.idea.structure.dialog.logUsagePsdAction
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBTextField
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.ui.treeStructure.treetable.TreeTableModelAdapter
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.Component
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.EventObject
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.SwingUtilities.invokeLater
import javax.swing.border.EmptyBorder
import javax.swing.event.ChangeEvent
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

private const val NAME = 0
private const val UNRESOLVED_VALUE = 1

/**
 * Main table for the Variables view in the Project Structure Dialog
 */
class VariablesTable private constructor(
  private val project: Project,
  private val context: PsContext,
  private val psProject: PsProject,
  private val variablesTreeModel: VariablesTableModel,
  private val validationResultsKeeper: ValidationResultsKeeper,
  private val parentDisposable: Disposable
) :
  TreeTable(variablesTreeModel) {

  constructor (project: Project, context: PsContext, psProject: PsProject, parentDisposable: Disposable, validationResultsKeeper: ValidationResultsKeeper) :
    this(project, context, psProject, createTreeModel(ProjectShadowNode(psProject), parentDisposable), validationResultsKeeper, parentDisposable)

  private val iconGap = JBUI.scale(2)
  private val editorInsets = JBUI.insets(1, 2)
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

    setTreeCellRenderer(OutlineNodeRenderer(object : NodeRenderer() {
      override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
      ) {
        super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
        val emptyName = when {
          value is EmptyNamedNode -> value.emptyName
          value is EmptyValueNode && editingRow == row -> value.emptyName
          else -> null
        }
        if (emptyName != null) {
          append(
            emptyName,
            if (selected) SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES
            else SimpleTextAttributes.GRAYED_ATTRIBUTES
          )
        }
        val userObject = (value as VariablesTableNode).userObject
        if (userObject is NodeDescription) {
          icon = userObject.icon
          iconTextGap = iconGap
          ipad = editorInsets
        }
        else {
          icon = EmptyIcon.ICON_16
        }
      }
    }))

    isStriped = true
    tree.isRootVisible = false
    tree.expandRow(0)
    tree.expandRow(1)
    tableModel.setTree(tree)
    setRowSelectionAllowed(false)
    columnSelectionAllowed = false
    setCellSelectionEnabled(true)
    setRowHeight(calculateMinRowHeight())
    selectionModel.setSelectionInterval(0, 0)

    updateValidationStatus()
  }

  override fun adapt(treeTableModel: TreeTableModel): TreeTableModelAdapter =
    object : TreeTableModelAdapter(treeTableModel, tree, this) {
      override fun fireTableDataChanged() {
        // have to restore table selection since AbstractDataModel.fireTableDataChanged() clears all selection
        val selectedColumn = this@VariablesTable.selectedColumn
        val selectedRow = this@VariablesTable.selectedRow
        super.fireTableDataChanged()
        if (selectedRow != -1) {
          selectionModel.setSelectionInterval(selectedRow, selectedRow)
        }
        if (selectedColumns.size != -1) {
          columnModel.selectionModel.setSelectionInterval(selectedColumn, selectedColumn)
        }
      }
    }

  private inline fun <reified T: VariablesTableNode> getSelectedNodes() =
    selectedRows
      .map { tree.getPathForRow(it)?.lastPathComponent as? T }
      .filterNotNull()

  fun deleteSelectedVariables() {
    fun BaseVariableNode.moduleName():String {
      val name = this.variable.parent.name
      return when (this.variable.parent) {
        is PsProject -> "project '$name'"
        is PsBuildScript -> "the build script of project '$name'"
        is PsModule -> "module '$name'"
        is PsVersionCatalog -> "version catalog '$name'"
        else -> ""
      }
    }

    removeEditor()
    val variableNodes = getSelectedNodes<BaseVariableNode>()
    val message = when {
      variableNodes.isEmpty() -> return
      variableNodes.size == 1 -> variableNodes[0].let { node ->
        when (node) {
          is VariableNode -> "Remove variable '${variableNodes[0].variable.name}' from ${node.moduleName()}?"
          is CatalogVariableNode -> "Remove version '${variableNodes[0].variable.name}' from ${node.moduleName()}?"
          is ListItemNode -> "Remove list item ${node.index} from '${node.variable.parent.name}'?"
          is MapItemNode -> "Remove map entry '${node.key}' from '${node.variable.parent.name}'?"
          else -> return
        }
      }
      else -> "Remove ${variableNodes.size} items from project '${psProject.name}'?"
    }
    if (Messages.showYesNoDialog(
        project,
        message,
        if (variableNodes.size > 1) "Remove Variables" else "Remove Variable",
        Messages.getQuestionIcon()
      ) == Messages.YES) {
      variableNodes.map { it.variable }.forEach { it.delete() }
      project.logUsagePsdAction(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_VARIABLES_REMOVE)
      updateValidationStatus()
    }
  }

  fun runToolbarAddAction(currentPosition: RelativePoint) {
    createAddVariableStrategy().executeToolbarAddVariable(currentPosition)
  }

  fun ContainerNode.findEmptyVariableNode() = children()?.toList()?.last() as? EmptyTopLevelNode

  fun createAddVariableStrategy(currentNode: EmptyTopLevelNode? = null): AddVariableStrategy =
    if (findParentContainer(currentNode) is VersionCatalogNode)
      AddVersionCatalogVariableStrategy()
    else AddModuleVariableStrategy()

  abstract inner class AddVariableStrategy {
    abstract fun executeToolbarAddVariable(currentPosition: RelativePoint)
    abstract fun prepareAddVariableEditor(editor: TableCellEditor?, row: Int, column: Int): Component?
    abstract fun addVariable(type: ValueType)

    protected fun addVariableInternal(type: ValueType, clickedOnRow: Int? = null, startEditing: Boolean = true) {
      val maybeEmptyNode: EmptyTopLevelNode? = clickedOnRow?.let { getEmptyNodeByRow(clickedOnRow) }
      val moduleNode = findParentContainer(maybeEmptyNode) ?: return
      val emptyNode: EmptyTopLevelNode = maybeEmptyNode ?: moduleNode.findEmptyVariableNode() ?: return

      emptyNode.setValueType(type)

      tree.expandPath(TreePath(moduleNode.path))
      val emptyNodePath = TreePath(emptyNode.path)
      val emptyNodeRow = tree.getRowForPath(emptyNodePath)

      if (editingRow != emptyNodeRow || editingColumn != 0) {
        getCellEditor()?.stopCellEditing()
        scrollRectToVisible(tree.getPathBounds(emptyNodePath))
        val selectedRow = tree.getRowForPath(emptyNodePath)
        selectionModel.setSelectionInterval(selectedRow, selectedRow)
        if (startEditing) editCellAt(emptyNodeRow, 0, NewVariableEvent(emptyNode))
        project.logUsagePsdAction(
          when (type) {
            ValueType.LIST -> AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_VARIABLES_ADD_LIST
            ValueType.MAP -> AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_VARIABLES_ADD_MAP
            else -> AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_VARIABLES_ADD_SIMPLE
          })
      }
    }
  }

  inner class AddVersionCatalogVariableStrategy : AddVariableStrategy() {
    override fun executeToolbarAddVariable(currentPosition: RelativePoint) {
      addVariableInternal(ValueType.STRING)
    }

    override fun prepareAddVariableEditor(editor: TableCellEditor?, row: Int, column: Int): Component {
      addVariableInternal(ValueType.STRING, row, startEditing = false)
      maybeScheduleNameRepaint(row, column)
      return super@VariablesTable.prepareEditor(editor, row, column)
    }

    override fun addVariable(type: ValueType) {
      // this method is used for tests as there is no such case on real UI
      addVariableInternal(ValueType.STRING)
    }
  }

  inner class AddModuleVariableStrategy : AddVariableStrategy() {
    override fun executeToolbarAddVariable(currentPosition: RelativePoint) {
      val popup = createChooseVariableTypePopup()
      popup.show(currentPosition)
    }

    override fun prepareAddVariableEditor(editor: TableCellEditor?, row: Int, column: Int): Component? {
      invokeLater {
        createChooseVariableTypePopup()
          .show(
            RelativePoint(this@VariablesTable, getCellRect(row, column, true).let { Point(it.x, it.y + it.height) }))
      }
      return null
    }

    override fun addVariable(type: ValueType) {
      addVariableInternal(type)
    }
  }

  private val coloredComponent = SimpleColoredComponent()

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer =
    TableCellRenderer { table, value, isSelected, _, rowIndex, columnIndex ->

      fun getDefaultComponent() =
        super
          .getCellRenderer(row, column)
          .getTableCellRendererComponent(table, value, isSelected, false, rowIndex, columnIndex)

      fun getNodeRendered() = tree.getPathForRow(rowIndex).lastPathComponent as VariablesTableNode

      when {
        column == UNRESOLVED_VALUE && getNodeRendered() is EmptyValueNode ->
          (getDefaultComponent() as JLabel).apply {
            text = (getNodeRendered() as EmptyValueNode).emptyValue
            foreground =
              if (isSelected) SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES.fgColor
              else NamedColorUtil.getInactiveTextColor()
          }

        column == NAME -> getDefaultComponent()

        else -> {
          coloredComponent.clear()
          if (!tree.isExpanded(rowIndex)) {
            val textRenderer = coloredComponent.toRenderer().toSelectedTextRenderer(isSelected)
            (value as? ParsedValue<Any>)?.renderAnyTo(textRenderer, mapOf())
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
        val selectedColumn = this.selectedColumn
        if (isCellEditable(selectedRow, selectedColumn)) {
          editCellAt(selectedRow, selectedColumn)
        }
        else {
          nextCell(e, selectedRow, selectedColumn)
        }
        return
      }
    }
    super.processKeyEvent(e)
  }

  private fun findParentContainer(emptyNode: EmptyTopLevelNode? = null): ContainerNode? {
    var last: VariablesTableNode? = emptyNode ?: getSelectedNodes<VariablesTableNode>().takeUnless { it.isEmpty() }?.last()

    while (last != null && last !is ContainerNode) {
      last = last.parent as ContainerNode
    }
    return last as? ContainerNode
  }

  override fun prepareEditor(editor: TableCellEditor?, row: Int, column: Int): Component? {
    if (column == NAME) {
      val node = getEmptyNodeByRow(row)
      if (node != null && !node.editingInProgress) {
        return createAddVariableStrategy(node).prepareAddVariableEditor(editor, row, column)
      }
    }
    maybeScheduleNameRepaint(row, column)
    return super.prepareEditor(editor, row, column)
  }

  private fun getEmptyNodeByRow(row: Int): EmptyTopLevelNode? =
    tree.getPathForRow(row).lastPathComponent as? EmptyTopLevelNode

  override fun editingCanceled(e: ChangeEvent?) {
    val rowBeingEdited = editingRow
    val columnBeingEdited = editingColumn
    super.editingCanceled(e)
    val nodeBeingEdited = tree.getPathForRow(rowBeingEdited)?.lastPathComponent
    if (nodeBeingEdited is EmptyTopLevelNode) {
      nodeBeingEdited.editingInProgress = false
    }
    maybeScheduleNameRepaint(rowBeingEdited, columnBeingEdited)
  }

  override fun editingStopped(e: ChangeEvent?) {
    val rowBeingEdited = editingRow
    val columnBeingEdited = editingColumn
    super.editingStopped(e)
    val nodeBeingEdited = tree.getPathForRow(rowBeingEdited)?.lastPathComponent
    if (nodeBeingEdited is EmptyTopLevelNode) {
      nodeBeingEdited.editingInProgress = false
    }
    maybeScheduleNameRepaint(rowBeingEdited, columnBeingEdited)
    updateValidationStatus()
  }

  private fun maybeScheduleNameRepaint(row: Int, column: Int) {
    if (column == UNRESOLVED_VALUE) {
      (tree.getPathForRow(row)?.lastPathComponent as? TreeNode)?.let { treeNode ->
        if (treeNode is EmptyValueNode) {
          invokeLater { variablesTreeModel.nodeChanged(treeNode) }
        }
      }
    }
  }

  /**
   * Table cell editor that reproduces the layout of a tree element
   */
  inner class NameCellEditor(private val row: Int) : AbstractTableCellEditor() {
    private val textBox = JBTextField()

    init {
      addTabKeySupportTo(textBox)
      textBox.componentPopupMenu = null
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
      panel.background = table.getSelectionBackground()
      textBox.text = value as String

      panel.addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          val focusManager = IdeFocusManager.findInstanceByComponent(panel)
          focusManager.requestFocus(textBox, true)
        }
      })

      val validatorFunction = when (val node = tree.getPathForRow(row).lastPathComponent) {
        is VariablesBaseNode -> node.validateNameOnEditing(textBox.text)
        else -> null
      }
      val validator = ComponentValidator(parentDisposable).withValidator { ->
         val message = validatorFunction?.invoke(textBox.text)
         message?.let { ValidationInfo(it, textBox) }
      }.installOn(textBox)

      textBox.document?.addDocumentListener(
        object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            ComponentValidator.getInstance(textBox).ifPresent { v: ComponentValidator -> v.revalidate() }
          }
        })

      if(textBox.text != "") validator.revalidate()

      return panel
    }

    override fun getCellEditorValue(): Any? = textBox.text
  }

  private fun calculateMinRowHeight() = JBUI.scale(24)

  inner class VariableCellEditor : PropertyCellEditor<Any>() {
    override fun initEditorFor(row: Int): ModelPropertyEditor<Any> {
      val node = tree.getPathForRow(row).lastPathComponent
      val variable = when (node) {
        is BaseVariableNode -> node.variable
        is EmptyValueNode -> node.createVariable(ParsedValue.NotSet)
        else -> throw IllegalStateException()
      }
      val uiProperty = if ((node as DefaultMutableTreeNode).parent is VersionCatalogNode)
        uiProperty(PsVariable.Descriptors.variableStringValue, ::stringPropertyEditor, psdUsageLogFieldId = null)
      else
        uiProperty(PsVariable.Descriptors.variableValue, ::simplePropertyEditor, psdUsageLogFieldId = null)

      val enterHandlingProxyCellEditor =
        object : TableCellEditor by this {
          override fun stopCellEditing(): Boolean {
            val editingRow = editingRow
            val editingColumn = editingColumn
            return this@VariableCellEditor.stopCellEditing()
              .also { invokeLater { nextCell(ActionEvent(this, ActionEvent.ACTION_PERFORMED, null), editingRow, editingColumn) } }
          }
        }

      val validator = when (val node = tree.getPathForRow(row).lastPathComponent) {
        is VariablesBaseNode -> when (node.parent) {
          is VersionCatalogNode -> node.validateStringValue()
          else -> null
        }

        else -> null
      }?.let { PropertyEditorValidator(parentDisposable, it) }

      val editor = uiProperty.createEditor(context, psProject, null, variable, enterHandlingProxyCellEditor, validator)

      addTabKeySupportTo(editor.component)
      return editor
    }

    override fun Annotated<ParsedValue<Any>>.toModelValue(): Any = this

    override fun valueEdited() {
      project.logUsagePsdAction(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_VARIABLES_MODIFY_VALUE)
    }
  }

  private fun selectCell(row: Int, column: Int) {
    this.selectionModel.setSelectionInterval(row, row)
    this.columnModel.selectionModel.setSelectionInterval(column, column)
  }

  private fun nextCell(e: ActionEvent) {
    nextCell(e, editingRow, editingColumn)
  }

  private fun nextCell(e: EventObject, row: Int, col: Int) {
    val editPosition = row to col
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
        invokeLater { editCellAt(row, column, null) }
      }
  }

  private fun prevCell(e: ActionEvent) {
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

  private fun addTabKeySupportTo(editor: JComponent) {
    editor.registerKeyboardAction(::nextCell, KeyStroke.getKeyStroke("TAB"), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    editor.registerKeyboardAction(::prevCell, KeyStroke.getKeyStroke("shift pressed TAB"), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    editor.registerKeyboardAction(::nextCell, KeyStroke.getKeyStroke("ENTER"), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
  }

  fun addVariableAvailable(): Boolean = getSelectedNodes<VariablesTableNode>().isNotEmpty()
  fun removeVariableAvailable(): Boolean = getSelectedNodes<BaseVariableNode>().isNotEmpty()

  class VariablesTableModel internal constructor(
    private val project: PsProject,
    internal val root: ContainerNode
  ) : DefaultTreeModel(root), TreeTableModel {
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
        NAME -> (node is VariablesTableNode && node.isNameEditable())
        UNRESOLVED_VALUE -> {
          if (node is VariableNode) {
            val literalValue = node.variable.value.maybeLiteralValue
            literalValue !is Map<*, *> && literalValue !is List<*>
          }
          else {
            node is BaseVariableNode || node is EmptyValueNode
          }
        }
        else -> false
      }

    override fun setValueAt(aValue: Any?, node: Any?, column: Int) {
      if (getValueAt(node, column) == ((aValue as? Annotated<*>)?.value ?: aValue)) return

      when (aValue) {
        is String -> when {
          node is EmptyVariableNode && column == NAME -> {
            val parentNode = node.parent as? ShadowedTreeNode
            val variable = node.createVariable(aValue)
            val newNode =
              parentNode
                ?.childNodes
                ?.find { (it.shadowNode as? VariableShadowNode)?.variable === variable }
                ?.let { newNode ->
                  tableTree?.expandPath(TreePath(getPathToRoot(newNode)))
                }
          }
          node is EmptyNamedNode && column == NAME -> node.createVariable(aValue)
          node is BaseVariableNode && column == NAME -> {
            node.setName(aValue)
            nodeChanged(node)
            project.ideProject.logUsagePsdAction(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_VARIABLES_RENAME)
          }

        }
        is Annotated<*> -> when {
          node is EmptyValueNode && column == UNRESOLVED_VALUE && aValue.value is ParsedValue.Set<Any> ->
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

  private fun updateValidationStatus(){
    validationResultsKeeper.updateValidationResult(hasValidationErrors())
  }

  private fun hasValidationErrors(): Boolean {
    fun isInvalid(node: VariablesTableNode): Boolean {
      if(!node.hasValidName()) return true
      return node.children().toList().filterIsInstance<VariablesTableNode>().any { isInvalid(it) }
    }

    return isInvalid(variablesTreeModel.root)
  }
}

fun VariablesTable.createChooseVariableTypePopup(): ListPopup {
  val actions = listOf(
    VariablesConfigurable.AddAction("1. Simple value", ValueType.STRING),
    VariablesConfigurable.AddAction("2. List", ValueType.LIST),
    VariablesConfigurable.AddAction("3. Map", ValueType.MAP)
  )
  val icons = listOf<Icon>(EmptyIcon.ICON_0, EmptyIcon.ICON_0, EmptyIcon.ICON_0)
  return JBPopupFactory
    .getInstance()
    .createListPopup(
      object : BaseListPopupStep<VariablesConfigurable.AddAction>(null, actions, icons) {
        override fun onChosen(selectedValue: VariablesConfigurable.AddAction?, finalChoice: Boolean): PopupStep<*>? =
          doFinalStep { selectedValue?.type?.let { createAddVariableStrategy().addVariable(it) } }
      })
}

// Node for all new and existing variables
abstract class VariablesBaseNode(
  shadowNode: ShadowNode,
  private val variablesScope: PsVariablesScope?
) : VariablesTableNode(shadowNode) {
  override fun hasValidName(): Boolean = true
  override fun isNameEditable() = true
  open fun validateName(): (String) -> String? = { name ->
    when {
      name.isEmpty() -> "Variable name cannot be empty."
      name.indexOf(" ") != -1 -> "Variable name cannot have whitespaces."
      name.indexOf(".") != -1 -> "Variable name cannot have dot."
      DISALLOWED_IN_NAME.indexIn(name) >= 0 -> "Build type name cannot contain any of $DISALLOWED_MESSAGE: '$name'"
      else -> null
    }
  }

  // validateName() plus duplication verification
  open fun validateNameOnEditing(currentName: String): (String) -> String? = { name ->
    validateName().invoke(name) ?: validateNameDuplicateOnEditing(currentName, name)
  }

  fun validateStringValue(): (String) -> String? = { name ->
    when {
      name.isEmpty() -> "Variable value cannot be empty."
      else -> null
    }
  }

  fun validateNameDuplicateOnEditing(currentName: String, editedName: String): String? = when {
    variablesScope?.filter { it.name != currentName }?.any { it.name == editedName } ?: false -> "Duplicate variable name: '$editedName'"
    else -> null
  }

}

abstract class VariablesTableNode(override val shadowNode: ShadowNode): DefaultMutableTreeNode(null), ShadowedTreeNode {
  override fun dispose() = Unit
  abstract fun isNameEditable(): Boolean
  abstract fun hasValidName(): Boolean
}

open class ContainerNode(shadowNode: ShadowNode): VariablesTableNode(shadowNode) {
  override fun hasValidName(): Boolean = true
  override fun isNameEditable(): Boolean = false
}

class ModuleNode(znode: ShadowNode, variables: PsVariablesScope) : ContainerNode(znode) {
  init {
    userObject = NodeDescription(variables.name, variables.model.icon ?: StudioIcons.Shell.Filetree.GRADLE_FILE)
  }
}

class VersionCatalogNode(znode: ShadowNode, variables: PsVariablesScope) : ContainerNode(znode) {
  init {
    userObject = NodeDescription(variables.name, variables.model.icon ?: StudioIcons.Shell.Filetree.GRADLE_FILE)
  }
}

// Class for all existing variables
abstract class BaseVariableNode(znode: ShadowNode, val variable: PsVariable) : VariablesBaseNode(znode, variable.scopePsVariables) {
  abstract fun getUnresolvedValue(expanded: Boolean): ParsedValue<Any>
  abstract fun setName(newName: String)

  fun setValue(newValue: ParsedValue<Any>) {
    variable.value = newValue
  }
}

abstract class EmptyTopLevelNode(
  znode: ShadowNode,
  variablesScope: PsVariablesScope
): VariablesBaseNode(znode, variablesScope) {
  var type: ValueType? = null
  fun setValueType(valueType: ValueType) {
    type = valueType
    editingInProgress = true
    setIconFor(type)
  }
  var editingInProgress: Boolean = false
    set(value) {
      field = value
      if(!value) setIconFor(null)
    }

  abstract fun setIconFor(value: ValueType?)
}

open class EmptyVariableNode(
  znode: ShadowNode,
  private val variablesScope: PsVariablesScope
) : EmptyTopLevelNode(znode, variablesScope), EmptyNamedNode {

  override val emptyName = "+New variable"
  override fun createVariable(key: String): PsVariable =
    when (type) {
      ValueType.LIST -> variablesScope.addNewListVariable(key)
      ValueType.MAP -> variablesScope.addNewMapVariable(key)
      else -> variablesScope.addNewVariable(key)
    }
      .also {
        editingInProgress = false
      }

  override fun setIconFor(value: ValueType?) {
    userObject = NodeDescription("", when (value) {
      ValueType.MAP -> AllIcons.Json.Object
      ValueType.LIST -> AllIcons.Json.Array
      null -> EmptyIcon.ICON_16
      else -> StudioIcons.Misc.GRADLE_VARIABLE
    })
  }
}

class CatalogEmptyVariableNode(
  znode: ShadowNode,
  private val variablesScope: PsVariablesScope
) : EmptyTopLevelNode(znode, variablesScope), EmptyNamedNode {

  override fun setIconFor(value: ValueType?) {
    userObject = NodeDescription("", when (value) {
      null -> EmptyIcon.ICON_16
      else -> StudioIcons.Misc.GRADLE_VARIABLE
    })
  }

  override val emptyName = "+New catalog version"

  override fun validateName(): (String) -> String? = validateCatalogNameFunction()

  override fun validateNameOnEditing(currentName: String): (String) -> String? = { name ->
    validateName().invoke(name) ?: validateNameDuplicateOnEditing(currentName, name)
  }

  override fun createVariable(key: String): PsVariable = variablesScope.addNewVariable(key).also {
    userObject = NodeDescription("", EmptyIcon.ICON_16)
    type = null
  }
}

fun validateCatalogNameFunction(): (String) -> String? = { name ->
  val catalogNamePattern = "[a-z]([a-zA-Z0-9_-])+"
  if (!name.matches(catalogNamePattern.toRegex()))
    "Variable name must match the following regular expression: $catalogNamePattern"
  else
    null
}

class VariableNode(znode: ShadowNode, variable: PsVariable) : BaseVariableNode(znode, variable) {
  init {
    userObject = NodeDescription(variable.name, when {
      variable.isMap -> AllIcons.Json.Object
      variable.isList -> AllIcons.Json.Array
      else -> StudioIcons.Misc.GRADLE_VARIABLE
    })
  }

  override fun hasValidName(): Boolean {
    val name = (userObject as? NodeDescription)?.name ?: return true
    return validateName().invoke(name) == null
  }

  override fun getUnresolvedValue(expanded: Boolean): ParsedValue<Any> = variable.value

  override fun setName(newName: String) {
    (userObject as NodeDescription).name = newName
    variable.setName(newName)
  }
}

class CatalogVariableNode(znode: ShadowNode, variable: PsVariable) : BaseVariableNode(znode, variable) {
  init {
    userObject = NodeDescription(variable.name, StudioIcons.Misc.GRADLE_VARIABLE)
  }

  override fun hasValidName(): Boolean =
    validateName().invoke(variable.name) == null

  override fun getUnresolvedValue(expanded: Boolean): ParsedValue<Any> = variable.value

  override fun setName(newName: String) {
    (userObject as NodeDescription).name = newName
    variable.setName(newName)
  }

  override fun validateName(): (String) -> String? = validateCatalogNameFunction()

  override fun validateNameOnEditing(currentName: String): (String) -> String? = { name ->
    validateName().invoke(name) ?: validateNameDuplicateOnEditing(currentName, name)
  }
}

class ListItemNode(znode: ShadowNode, val index: Int, variable: PsVariable) : BaseVariableNode(znode, variable) {
  init {
    userObject = index
  }
  override fun getUnresolvedValue(expanded: Boolean): ParsedValue<Any> = variable.value

  override fun setName(newName: String) {
    throw UnsupportedOperationException("List item indices cannot be renamed")
  }
  override fun isNameEditable() = false

  override fun hasValidName(): Boolean = true
}

class EmptyListItemNode(
  znode: ShadowNode,
  private val containingList: PsVariable
) : VariablesBaseNode(znode, containingList.scopePsVariables), EmptyValueNode {
  override fun isNameEditable() = false
  override fun hasValidName(): Boolean = true

  override val emptyName get() = this.parent.getIndex(this).toString()
  override val emptyValue = "+New value"
  override fun createVariable(value: ParsedValue<Any>): PsVariable = containingList.addListValue(value)
}

class MapItemNode(znode: ShadowNode, val key: String, variable: PsVariable) : BaseVariableNode(znode, variable) {
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

  override fun hasValidName(): Boolean = validateName().invoke(key) == null
}

interface EmptyNamedNode {
  val emptyName: String
  fun createVariable(key: String): PsVariable?
}

interface EmptyValueNode {
  val emptyName: String
  val emptyValue: String
  fun createVariable(value: ParsedValue<Any>): PsVariable
}

class EmptyMapItemNode(
  znode: ShadowNode,
  private val containingMap: PsVariable
) : VariablesBaseNode(znode, containingMap.scopePsVariables), EmptyNamedNode {
  override val emptyName = "+New entry"
  override fun isNameEditable() = true

  override fun createVariable(key: String) = containingMap.addMapValue(key)
}

class NodeDescription(var name: String, val icon: Icon) {
  override fun toString() = name
}

class NewVariableEvent(source: Any) : EventObject(source)

/**
 * Creates a tree model from a tree of shadow nodes which is auto-updated on changes made to the shadow tree.
 */
internal fun createTreeModel(root: ProjectShadowNode, parentDisposable: Disposable): VariablesTable.VariablesTableModel =
  VariablesTable.VariablesTableModel(root.project, ContainerNode(root)
    .also { Disposer.register(parentDisposable, it) })
    .also { it.initializeNode(it.root, from = root) }

internal data class ProjectShadowNode(val project: PsProject) : ShadowNode {
  override fun getChildrenModels(): Collection<ShadowNode> =
    listOf(RootModuleShadowNode(project.buildScriptVariables)) +
    getVersionCatalogNodes() +
    listOf(RootModuleShadowNode(project.variables)) +
    project.modules.filter { it.isDeclared }.sortedBy { it.gradlePath }.map { ModuleShadowNode(it) }

  private fun getVersionCatalogNodes(): List<VersionCatalogShadowNode> =
    project.versionCatalogs.sortedBy { it.name }.map { VersionCatalogShadowNode(it) }

  override fun createNode(): ContainerNode = ContainerNode(this)
  override fun onChange(disposable: Disposable, listener: () -> Unit) {
    project.modules.onChange(disposable, listener)
    project.versionCatalogs.onChange(disposable, listener)
  }
}

internal data class RootModuleShadowNode(val scope: PsVariables) : ShadowNode {
  override fun getChildrenModels(): Collection<ShadowNode> =
    scope.map { VariableShadowNode(it) } + VariableEmptyShadowNode(scope)

  override fun createNode(): ModuleNode = ModuleNode(this, scope)
  override fun onChange(disposable: Disposable, listener: () -> Unit) = scope.onChange(disposable, listener)
}

internal data class ModuleShadowNode(val module: PsModule) : ShadowNode {
  override fun getChildrenModels(): Collection<ShadowNode> =
    module.variables.map { VariableShadowNode(it) } + VariableEmptyShadowNode(module.variables)
  override fun createNode(): ModuleNode = ModuleNode(this, module.variables)
  override fun onChange(disposable: Disposable, listener: () -> Unit) = module.variables.onChange(disposable, listener)
}

internal data class VersionCatalogShadowNode(val versionCatalog: PsVersionCatalog) : ShadowNode {
  override fun getChildrenModels(): Collection<ShadowNode> =
    versionCatalog.variables.map { CatalogVariableShadowNode(it) } + CatalogVariableEmptyShadowNode(versionCatalog.variables)
  override fun createNode(): VersionCatalogNode = VersionCatalogNode(this, versionCatalog.variables)
  override fun onChange(disposable: Disposable, listener: () -> Unit) = versionCatalog.variables.onChange(disposable, listener)
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

open class VariableShadowNode(open val variable: PsVariable) : ShadowNode {
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

internal class CatalogVariableShadowNode(override val variable: PsVariable) : VariableShadowNode(variable) {
  override fun createNode(): VariablesBaseNode = CatalogVariableNode(this, variable)
}

internal data class VariableEmptyShadowNode(val variablesScope: PsVariablesScope) : ShadowNode {
  override fun createNode(): VariablesBaseNode = EmptyVariableNode(this, variablesScope)
}

internal class CatalogVariableEmptyShadowNode(private val variablesScope: PsVariablesScope) : ShadowNode {
  override fun createNode(): VariablesBaseNode = CatalogEmptyVariableNode(this, variablesScope)
}