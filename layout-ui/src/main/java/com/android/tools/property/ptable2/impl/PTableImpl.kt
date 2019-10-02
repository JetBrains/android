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
package com.android.tools.property.ptable2.impl

import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.property.ptable2.PTable
import com.android.tools.property.ptable2.PTableCellEditorProvider
import com.android.tools.property.ptable2.PTableCellRendererProvider
import com.android.tools.property.ptable2.PTableColumn
import com.android.tools.property.ptable2.PTableGroupItem
import com.android.tools.property.ptable2.PTableItem
import com.android.tools.property.ptable2.PTableModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableCell
import com.intellij.ui.TableExpandableItemsHandler
import com.intellij.ui.TableUtil
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.EventObject
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.event.ChangeEvent
import javax.swing.event.TableModelEvent
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter
import javax.swing.text.JTextComponent
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.Delegates

private const val LEFT_FRACTION = 0.40
private const val MAX_LABEL_WIDTH = 240
private const val EXPANSION_RIGHT_PADDING = 4
private const val NOOP = "noop"
private const val COLUMN_COUNT = 2

/**
 * Implementation of a [PTable].
 *
 * The intention is to hide implementation details in this class, and only
 * expose a minimal API in [PTable].
 */
class PTableImpl(
  override val tableModel: PTableModel,
  override val context: Any?,
  private val rendererProvider: PTableCellRendererProvider,
  private val editorProvider: PTableCellEditorProvider,
  private val customToolTipHook: (MouseEvent) -> String? = { null },
  private val updatingUI: () -> Unit = {}
) : PFormTableImpl(PTableModelImpl(tableModel)), PTable {

  private val nameRowSorter = TableRowSorter<TableModel>()
  private val nameRowFilter = NameRowFilter(model)
  private val tableCellRenderer = PTableCellRendererWrapper()
  private val tableCellEditor = PTableCellEditorWrapper()
  private val expandableNameHandler = PTableExpandableItemsHandler(this)
  private var initialized = false
  private var scaledMaxLabelWidth = JBUI.scale(MAX_LABEL_WIDTH)
  override val backgroundColor: Color
    get() = super.getBackground()
  override val foregroundColor: Color
    get() = super.getForeground()
  override val activeFont: Font
    get() = super.getFont()
  override val gridLineColor: Color
    get() = gridColor
  override var wrap = false
  var isPaintingTable = false
    private set

  init {
    super.setShowColumns(false)
    super.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

    super.setShowGrid(false)
    super.setShowHorizontalLines(true)
    super.setIntercellSpacing(Dimension(0, JBUI.scale(1)))
    super.setGridColor(JBColor(Gray._224, Gray._44))

    super.setColumnSelectionAllowed(false)
    super.setCellSelectionEnabled(false)
    super.setRowSelectionAllowed(true)

    super.addMouseListener(MouseTableListener())
    super.addKeyListener(PTableKeyListener())

    // We want expansion for the property names but not of the editors. This disables expansion for both columns.
    // TODO: Provide expansion of the left column only.
    super.setExpandableItemsEnabled(false)

    getColumnModel().getColumn(0).resizable = false
    getColumnModel().getColumn(1).resizable = false

    initialized = true
  }

  override fun doLayout() {
    val nameColumn = getColumnModel().getColumn(0)
    val valueColumn = getColumnModel().getColumn(1)
    nameColumn.width = min((width * LEFT_FRACTION).roundToInt(), scaledMaxLabelWidth)
    valueColumn.width = width - nameColumn.width
  }

  override fun paint(g: Graphics) {
    isPaintingTable = true
    try {
      super.paint(g)
    }
    finally {
      isPaintingTable = false
    }
  }

  override val component: JComponent
    get() = this

  override val itemCount: Int
    get() = rowCount

  override var filter: String by Delegates.observable("") { _, oldValue, newValue -> filterChanged(oldValue, newValue) }

  override fun item(row: Int): PTableItem {
    return super.getValueAt(row, 0) as PTableItem
  }

  override fun isExpanded(item: PTableGroupItem): Boolean {
    return model.isExpanded(item)
  }

  override fun getExpandableItemsHandler(): ExpandableItemsHandler<TableCell> {
    return expandableNameHandler
  }

  fun isExpandedItem(row: Int, column: Int): Boolean {
    return expandableNameHandler.expandedItems.find { it.row == row && it.column == column } != null
  }

  override fun startEditing(row: Int) {
    if (row < 0) {
      removeEditor()
    }
    else if (!startEditing(row, 0) {}) {
      startEditing(row, 1) {}
    }
  }

  override fun startNextEditor(): Boolean {
    val pos = PTablePosition(0, 0, itemCount, COLUMN_COUNT)
    var exists = true
    if (isEditing) {
      pos.row = editingRow
      pos.column = editingColumn
      removeEditor()
      exists = pos.next(true)
    }
    while (exists && !tableModel.isCellEditable(item(pos.row), PTableColumn.fromColumn(pos.column))) {
      exists = pos.next(true)
    }
    if (!exists) {
      // User navigated to the next editor after the last row of this table:
      return false
    }
    startEditing(pos.row, pos.column)
    return true
  }

  private fun startEditing(row: Int, column: Int) {
    selectRow(row)
    selectColumn(column)
    startEditing(row, column) {}
  }

  override fun getModel(): PTableModelImpl {
    return super.getModel() as PTableModelImpl
  }

  override fun updateUI() {
    super.updateUI()
    customizeKeyMaps()
    if (initialized) {  // This method is called but JTable.init
      scaledMaxLabelWidth = JBUI.scale(MAX_LABEL_WIDTH)
      updatingUI()
      rendererProvider.updateUI()
      editorProvider.updateUI()
    }
  }

  /**
   * The [TableModel] notification to update the table content.
   *
   * The editor must be removed before updating the content,
   * otherwise the editor may show up at the wrong row after the update.
   *
   * Also add logic to continue editing after the update.
   */
  override fun tableChanged(event: TableModelEvent) {
    when (event) {
      is PTableModelRepaintEvent -> refresh()
      is PTableModelEvent -> tableChangedWithNextEditedRow(event, event.nextEditedRow)
      else -> tableChangedWithoutNextEditedRow(event)
    }
  }

  private fun refresh() {
    if (isEditing) {
      tableCellEditor.editor.refresh()
    }
    repaint()
  }

  private fun tableChangedWithoutNextEditedRow(event: TableModelEvent) {
    if (isEditing) {
      removeEditor()
    }
    super.tableChanged(event)
  }

  private fun tableChangedWithNextEditedRow(event: TableModelEvent, nextEditedRow: Int) {
    val wasEditing = isEditing
    if (wasEditing) {
      removeEditor()
    }
    super.tableChanged(event)

    if (wasEditing) {
      val newEditingRow: Int
      val newEditingColumn: Int
      if (nextEditedRow >= 0 && model.rowCount > nextEditedRow) {
        newEditingRow = convertRowIndexToView(nextEditedRow)
        newEditingColumn = 0
      }
      else {
        newEditingRow = -1
        newEditingColumn = -1
      }
      if (newEditingRow >= 0) {
        startEditing(newEditingRow, newEditingColumn)
        if (!isEditing && newEditingColumn == 0) {
          startEditing(newEditingRow, 1)
        }
      }
    }
  }

  // Bug: 221565
  // Without this line it is impossible to get focus to a combo box editor.
  // The code in JBTable will move the focus to the JPanel that includes
  // the combo box, the resource button, and the design button.
  override fun surrendersFocusOnKeyStroke(): Boolean {
    return false
  }

  // Override the JTable.prepareEditor
  // Return the same value but do NOT call component.setNextFocusableComponent.
  // This allows us to TAB from the last editor to the first editor in the table.
  // Without this a TAB from the last editor would cause the table to get the focus.
  override fun prepareEditor(editor: TableCellEditor, row: Int, column: Int): Component? {
    val value = getValueAt(row, column)
    val isSelected = isCellSelected(row, column)
    return editor.getTableCellEditorComponent(this, value, isSelected, row, column)
  }

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
    tableCellRenderer.renderer = rendererProvider(this, item(row), PTableColumn.fromColumn(column))
    return tableCellRenderer
  }

  override fun getCellEditor(row: Int, column: Int): PTableCellEditorWrapper {
    tableCellEditor.editor = editorProvider(this, item(row), PTableColumn.fromColumn(column))
    return tableCellEditor
  }

  override fun getToolTipText(event: MouseEvent): String? {
    return customToolTipHook(event)
  }

  private fun filterChanged(oldValue: String, newValue: String) {
    if (oldValue == newValue) {
      return
    }
    if (newValue.isEmpty()) {
      rowSorter = null
    }
    else {
      nameRowFilter.pattern = newValue
      nameRowSorter.rowFilter = nameRowFilter
      nameRowSorter.model = model
      rowSorter = nameRowSorter
    }
  }

  private fun customizeKeyMaps() {

    // Disable the builtin actions from the TableUI by always returning false for isEnabled.
    // This will make sure the event is bubbled up to the parent component.
    registerActionKey({}, KeyStrokes.ENTER, NOOP, { false }, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    registerActionKey({}, KeyStrokes.SPACE, NOOP, { false }, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    registerActionKey({}, KeyStrokes.LEFT, NOOP, { false }, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    registerActionKey({}, KeyStrokes.NUM_LEFT, NOOP, { false }, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    registerActionKey({}, KeyStrokes.RIGHT, NOOP, { false }, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    registerActionKey({}, KeyStrokes.NUM_RIGHT, NOOP, { false }, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    registerActionKey({}, KeyStrokes.PAGE_UP, NOOP, { false }, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    registerActionKey({}, KeyStrokes.PAGE_DOWN, NOOP, { false }, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)

    // Setup the actions for when the table has focus i.e. we are not editing a property
    registerActionKey({ smartEnter(toggleOnly = false) }, KeyStrokes.ENTER, "smartEnter")
    registerActionKey({ smartEnter(toggleOnly = true) }, KeyStrokes.SPACE, "toggleEditor")
    registerActionKey({ modifyGroup(expand = false) }, KeyStrokes.LEFT, "collapse")
    registerActionKey({ modifyGroup(expand = false) }, KeyStrokes.NUM_LEFT, "collapse")
    registerActionKey({ modifyGroup(expand = true) }, KeyStrokes.RIGHT, "expand")
    registerActionKey({ modifyGroup(expand = true) }, KeyStrokes.NUM_RIGHT, "expand")
    registerActionKey({ nextPage(moveUp = true) }, KeyStrokes.PAGE_UP, "pageUp")
    registerActionKey({ nextPage(moveUp = false) }, KeyStrokes.PAGE_DOWN, "pageDown")
    registerActionKey({ moveToFirstRow() }, KeyStrokes.HOME, "firstRow")
    registerActionKey({ moveToLastRow() }, KeyStrokes.END, "lastRow")
    registerActionKey({ moveToFirstRow() }, KeyStrokes.CMD_HOME, "firstRow")
    registerActionKey({ moveToLastRow() }, KeyStrokes.CMD_END, "lastRow")

    // Disable auto start editing from JTable
    putClientProperty("JTable.autoStartsEdit", java.lang.Boolean.FALSE)
  }

  private fun toggleTreeNode(row: Int) {
    val index = convertRowIndexToModel(row)
    model.toggle(index)
  }

  private fun selectRow(row: Int) {
    getSelectionModel().setSelectionInterval(row, row)
    TableUtil.scrollSelectionToVisible(this)
  }

  private fun selectColumn(column: Int) {
    getColumnModel().selectionModel.setSelectionInterval(column, column)
  }

  private fun toggleAndSelect(row: Int) {
    toggleTreeNode(row)
    selectRow(row)
  }

  private fun quickEdit(row: Int, column: Int) {
    val editor = getCellEditor(row, column)

    // only perform edit if we know the editor is capable of a quick toggle action.
    // We know that boolean editors switch their state and finish editing right away
    if (editor.isBooleanEditor) {
      startEditing(row, column) { editor.toggleValue() }
    }
  }

  // Start editing a table cell.
  // Return true if an editor was successfully created, false if the cell was not editable.
  private fun startEditing(row: Int, column: Int, afterActivation: () -> Unit?): Boolean {
    val editor = getCellEditor(row, 0)
    if (!editCellAt(row, column)) {
      return false
    }

    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
      editor.requestFocus()
      afterActivation()
    }
    return true
  }

  override fun editingCanceled(event: ChangeEvent?) {
    // This method is called from the IDEEventQueue.EditingCanceller
    // an event preprocessing utility. For a ComboBox with an open
    // popup we simply want to close the popup.
    // Therefore: give the editor a change to handle this event
    // before stopping the cell editor.
    //
    // Do not remove the editor i.e. do not call the super method.
    if (tableCellEditor.editor.cancelEditing()) {
      super.editingCanceled(event)
    }
  }

  override fun removeEditor() {
    tableModel.editedItem = null
    repaintOtherCellInRow()

    // Now remove the editor
    super.removeEditor()

    // Give the cell editor a change to reset it's state
    tableCellEditor.editor.close(this)
  }

  override fun editCellAt(row: Int, column: Int, event: EventObject?): Boolean {
    if (!super.editCellAt(row, column, event)) {
      return false
    }
    repaintOtherCellInRow()
    selectRow(row)
    tableModel.editedItem = item(row)
    return true
  }

  // The implementation in JTable stops editing and performs auto resizing.
  // When a row is added in a PTableImpl we often want to edit the new row.
  // The default implementation will not allow that.
  // Also auto resizing is not used in PTableImpl so no functionality should is lost.
  override fun columnMarginChanged(event: ChangeEvent) {
    resizeAndRepaint()
  }

  // This method fixes a problem where the other cell was not repainted after
  // focus was removed from a PTable with a cell being edited.
  private fun repaintOtherCellInRow() {
    if (editingRow < 0 || editingColumn < 0) {
      return
    }
    val otherColumn = (editingColumn + 1) % 2
    val cellRect = getCellRect(editingRow, otherColumn, false)
    repaint(cellRect)
  }

  // ========== Keyboard Actions ===============================================

  /**
   * Expand/Collapse if it is a group property, start editing otherwise.
   *
   * If [toggleOnly] then don't launch a full editor, just perform a quick toggle.
   */
  private fun smartEnter(toggleOnly: Boolean) {
    val row = selectedRow
    if (isEditing || row == -1) {
      return
    }

    val item = item(row)
    when {
      model.isGroupItem(item) -> toggleAndSelect(row)
      toggleOnly -> quickEdit(row, 1)
      else -> {
        if (!startEditing(row, 0) {}) {
          startEditing(row, 1) {}
        }
      }
    }
  }

  /**
   * Expand/Collapse items after right/left key press
   */
  private fun modifyGroup(expand: Boolean) {
    val row = selectedRow
    if (isEditing || row == -1) {
      return
    }

    val index = convertRowIndexToModel(row)
    if (expand) {
      model.expand(index)
    }
    else {
      model.collapse(index)
    }
    selectRow(row)
  }

  /**
   * Scroll the selected row up/down.
   */
  private fun nextPage(moveUp: Boolean) {
    val selectedRow = selectedRow
    if (isEditing || selectedRow == -1) {
      return
    }

    // PTable may be in a scrollable component, so we need to use visible height instead of getHeight()
    val visibleHeight = visibleRect.getHeight().toInt()
    val rowHeight = getRowHeight()
    if (visibleHeight <= 0 || rowHeight <= 0) {
      return
    }
    val movement = visibleHeight / rowHeight
    if (moveUp) {
      selectRow(Math.max(0, selectedRow - movement))
    }
    else {
      selectRow(Math.min(selectedRow + movement, rowCount - 1))
    }
  }

  private fun moveToFirstRow() {
    val selectedRow = selectedRow
    if (isEditing || selectedRow == -1) {
      return
    }

    selectRow(0)
  }

  private fun moveToLastRow() {
    val selectedRow = selectedRow
    if (isEditing || selectedRow == -1) {
      return
    }

    selectRow(rowCount - 1)
  }

  // ========== Group Expansion on Mouse Click =================================

  /**
   * MouseListener
   */
  private inner class MouseTableListener : MouseAdapter() {

    override fun mousePressed(event: MouseEvent) {
      val row = rowAtPoint(event.point)
      if (row == -1) {
        return
      }

      val rectLeftColumn = getCellRect(row, convertColumnIndexToView(0), false)
      if (rectLeftColumn.contains(event.x, event.y)) {
        toggleTreeNode(row)
      }
    }
  }

  // ========== KeyListener ====================================================

  /**
   * PTableKeyListener is our own implementation of "JTable.autoStartsEdit"
   */
  private inner class PTableKeyListener : KeyAdapter() {

    override fun keyTyped(event: KeyEvent) {
      val row = selectedRow
      if (isEditing || row == -1 || event.keyChar == '\t' || event.keyCode == KeyEvent.VK_ESCAPE) {
        return
      }
      autoStartEditingAndForwardKeyEventToEditor(row, event)
    }

    override fun keyPressed(event: KeyEvent) {
      val row = selectedRow
      if (isEditing || row == -1 || event.keyCode != KeyEvent.VK_DOWN || !event.isAltDown) {
        return
      }
      autoStartEditingAndForwardKeyEventToEditor(row, event)
    }

    private fun autoStartEditingAndForwardKeyEventToEditor(row: Int, event: KeyEvent) {
      val focusRequest = {
        ApplicationManager.getApplication()?.invokeLater {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
            val textEditor = IdeFocusManager.findInstance().focusOwner
            if (textEditor is JTextComponent) {
              val keyEvent = KeyEvent(textEditor, event.id, event.`when`, event.modifiers, event.keyCode, event.keyChar)
              textEditor.dispatchEvent(keyEvent)
            }
          }
        }
      }
      val table = this@PTableImpl
      if (!table.startEditing(row, 0, focusRequest)) {
        table.startEditing(row, 1, focusRequest)
      }
    }
  }
}

private class NameRowFilter(private val model: PTableModelImpl) : RowFilter<TableModel, Int>() {
  private val comparator = SpeedSearchComparator(false)
  var pattern = ""

  override fun include(entry: Entry<out TableModel, out Int>): Boolean {
    val item = entry.getValue(0) as PTableItem
    if (isMatch(item.name)) {
      return true
    }
    var parent = model.parentOf(item)
    while (parent != null) {
      if (isMatch(parent.name)) {
        return true
      }
      parent = model.parentOf(parent)
    }
    if (item !is PTableGroupItem) {
      return false
    }
    for (child in item.children) {
      if (isMatch(child.name)) {
        return true
      }
    }
    return false
  }

  private fun isMatch(text: String): Boolean {
    return comparator.matchingFragments(pattern, text) != null
  }
}

private class PTableExpandableItemsHandler(table: PTableImpl) : TableExpandableItemsHandler(table) {
  override fun getCellRendererAndBounds(key: TableCell): Pair<Component, Rectangle>? {
    if (key.column != 0) {
      return null
    }
    val rendererAndBounds = super.getCellRendererAndBounds(key) ?: return null
    rendererAndBounds.second.width += JBUI.scale(EXPANSION_RIGHT_PADDING)
    return rendererAndBounds
  }
}
