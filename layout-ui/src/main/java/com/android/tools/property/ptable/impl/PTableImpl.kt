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
package com.android.tools.property.ptable.impl

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.property.ptable.ColumnFraction
import com.android.tools.property.ptable.ColumnFractionChangeHandler
import com.android.tools.property.ptable.DefaultPTableCellEditorProvider
import com.android.tools.property.ptable.DefaultPTableCellRendererProvider
import com.android.tools.property.ptable.KEY_IS_VISUALLY_RESTRICTED
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableCellEditorProvider
import com.android.tools.property.ptable.PTableCellRendererProvider
import com.android.tools.property.ptable.PTableColumn
import com.android.tools.property.ptable.PTableGroupItem
import com.android.tools.property.ptable.PTableGroupModification
import com.android.tools.property.ptable.PTableItem
import com.android.tools.property.ptable.PTableModel
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableActions
import com.intellij.ui.TableCell
import com.intellij.ui.TableExpandableItemsHandler
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.preferredHeight
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.util.EventObject
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JViewport
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.TableModelEvent
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter
import javax.swing.text.JTextComponent
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.properties.Delegates

const val EXPANSION_RIGHT_PADDING = 4
private const val COLUMN_COUNT = 2

/**
 * Implementation of a [PTable].
 *
 * The intention is to hide implementation details in this class, and only expose a minimal API in
 * [PTable]. This class is open for testing purposes only.
 */
open class PTableImpl(
  override val tableModel: PTableModel,
  override val context: Any? = null,
  private val rendererProvider: PTableCellRendererProvider = DefaultPTableCellRendererProvider(),
  private val editorProvider: PTableCellEditorProvider = DefaultPTableCellEditorProvider(),
  private val customToolTipHook: (MouseEvent) -> String? = { null },
  private val updatingUI: () -> Unit = {},
  private val nameColumnFraction: ColumnFraction = ColumnFraction(),
) : PFormTableImpl(PTableModelImpl(tableModel)), PTable {
  private val nameRowSorter = TableRowSorter<TableModel>()
  private val nameRowFilter = NameRowFilter(model)
  private val tableCellRenderer = PTableCellRendererWrapper()
  private val tableCellEditor = PTableCellEditorWrapper()
  private val resizeHandler =
    ColumnFractionChangeHandler(
      nameColumnFraction,
      { 0 },
      { width },
      { columnModel.getColumn(0).minWidth },
      ::onResizeModeChange,
    )
  private var lastLeftFractionValue = nameColumnFraction.value
  private var initialized = false
  override val backgroundColor: Color
    get() = super.getBackground()

  override val foregroundColor: Color
    get() = super.getForeground()

  override val activeFont: Font
    get() = super.getFont()

  override val gridLineColor: Color
    get() = gridColor

  override var wrap = false

  override var previousTable: PTable? = null

  override var nextTable: PTable? = null

  init {
    setShowColumns(false)
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

    setShowGrid(false)
    setShowHorizontalLines(true)
    intercellSpacing = Dimension(0, JBUIScale.scale(1))
    setGridColor(JBColor(Gray._224, Gray._44))

    columnSelectionAllowed = false
    setCellSelectionEnabled(false)
    setRowSelectionAllowed(true)

    addMouseListener(MouseTableListener())
    addKeyListener(PTableKeyListener())
    addMouseListener(resizeHandler)
    addMouseMotionListener(resizeHandler)

    // We want expansion for the property names but not of the editors. This disables expansion for
    // both columns.
    // TODO: Provide expansion of the left column only.
    setExpandableItemsEnabled(false)

    transferHandler = PTableTransferHandler()

    getColumnModel().getColumn(0).resizable = false
    getColumnModel().getColumn(1).resizable = false

    installHoverListener()

    initialized = true
  }

  private fun onResizeModeChange(newResizeMode: Boolean) {}

  override fun isValid(): Boolean {
    // Make sure we cause a layout when the leftFraction has changed.
    // This method is called during initialization where the leftFraction property is still null, so
    // guard with "initialized".
    return super.isValid() && (!initialized || lastLeftFractionValue == nameColumnFraction.value)
  }

  override fun doLayout() {
    lastLeftFractionValue = nameColumnFraction.value
    val nameColumn = getColumnModel().getColumn(0)
    val valueColumn = getColumnModel().getColumn(1)
    nameColumn.width = maxOf((width * lastLeftFractionValue).roundToInt(), 0)
    valueColumn.width = maxOf(width - nameColumn.width, 0)
  }

  override val component: JComponent
    get() = this

  override val itemCount: Int
    get() = rowCount

  override var filter: String by
    Delegates.observable("") { _, oldValue, newValue -> filterChanged(oldValue, newValue) }

  override fun item(row: Int): PTableItem {
    return super.getValueAt(row, 0) as PTableItem
  }

  override fun depth(item: PTableItem): Int {
    return model.depth(item)
  }

  /** Return true if the group is current open/expanded for showing child items. */
  override fun isExpanded(item: PTableGroupItem): Boolean {
    return model.isExpanded(item)
  }

  /**
   * Return true if the [column] of [item] is currently expanded to show the full value that doesn't
   * normally fit in the cell.
   */
  override fun isExpandedRendererItem(item: PTableItem, column: PTableColumn): Boolean {
    val cell = expandableItemsHandler.expandedItems.singleOrNull() ?: return false
    if (column.ordinal != cell.column) {
      return false
    }
    val value = getValueAt(cell.row, cell.column)
    return value === item
  }

  /**
   * Return true if a popup is currently showing for a value that doesn't normally fit in the cell.
   */
  override fun isExpandedRendererPopupShowing(): Boolean {
    return (expandableItemsHandler as? TableExpandableItemsHandler)?.isShowing ?: false
  }

  override fun toggle(item: PTableGroupItem) {
    val index = model.toggle(item)
    if (index >= 0) {
      val row = convertRowIndexToView(index)
      scrollCellIntoView(row, 0)
    }
  }

  override fun updateRowHeight(
    item: PTableItem,
    column: PTableColumn,
    cellEditor: JComponent,
    scrollIntoView: Boolean,
  ) {
    // The Border insets height for a DarculaTextBorder is different if
    // DarculaUIUtil.isTableCellEditor returns false.
    // Temporary add the editor as a child to the table to get the correct preferred height.
    val isAttached = DarculaUIUtil.isTableCellEditor(cellEditor)
    if (!isAttached) {
      add(cellEditor)
    }
    val height =
      try {
        cellEditor.preferredHeight
      } finally {
        if (!isAttached) {
          remove(cellEditor)
        }
      }

    val index = model.indexOf(item)
    if (index >= 0) {
      val row = convertRowIndexToView(index)
      if (getRowHeight(row) != height) {
        setRowHeight(row, height)
      }
      if (scrollIntoView) {
        scrollCellIntoView(row, column.ordinal)
      }
    }
  }

  override fun createExpandableItemsHandler(): ExpandableItemsHandler<TableCell> {
    return PTableExpandableItemsHandler(this)
  }

  fun isExpandedItem(row: Int, column: Int): Boolean {
    return expandableItemsHandler.expandedItems.find { it.row == row && it.column == column } !=
      null
  }

  override fun startEditing(row: Int) {
    if (row < 0) {
      removeEditor()
    } else if (!startEditing(row, 0) {}) {
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
    while (
      exists && !tableModel.isCellEditable(item(pos.row), PTableColumn.fromColumn(pos.column))
    ) {
      exists = pos.next(true)
    }
    if (!exists) {
      // User navigated to the next editor after the last row of this table:
      return false
    }
    startEditing(pos.row, pos.column)
    return true
  }

  override fun updateGroupItems(group: PTableGroupItem, modification: PTableGroupModification) {
    model.updateGroupItems(group, modification)
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
    if (initialized) { // This method is called but JTable.init
      updatingUI()
      rendererProvider.updateUI()
      editorProvider.updateUI()
    }
  }

  /**
   * The [TableModel] notification to update the table content.
   *
   * The editor must be removed before updating the content, otherwise the editor may show up at the
   * wrong row after the update.
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
    val wasEditing = isEditing
    val wasEditingColumn = editingColumn
    var editing: PTableItem? = null
    if (wasEditing) {
      editing = item(editingRow)
      removeEditor()
    }
    super.tableChanged(event)
    if (editing != null) {
      val row = model.indexOf(editing)
      val newEditingRow = if (row >= 0) convertRowIndexToView(row) else -1
      startEditing(newEditingRow, wasEditingColumn)
    }
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
      } else {
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
    } else {
      nameRowFilter.pattern = newValue
      nameRowSorter.rowFilter = nameRowFilter
      nameRowSorter.model = model
      rowSorter = nameRowSorter
    }
  }

  private fun customizeKeyMaps() {

    // Override the builtin table actions
    actionMap.put(TableActions.Left.ID, MyKeyAction { modifyGroup(expand = false) })
    actionMap.put(TableActions.Right.ID, MyKeyAction { modifyGroup(expand = true) })
    actionMap.put(TableActions.ShiftLeft.ID, MyKeyAction { modifyGroup(expand = false) })
    actionMap.put(TableActions.ShiftRight.ID, MyKeyAction { modifyGroup(expand = true) })
    actionMap.put(TableActions.Up.ID, MyKeyAction { nextRow(moveUp = true) })
    actionMap.put(TableActions.Down.ID, MyKeyAction { nextRow(moveUp = false) })
    actionMap.put(TableActions.ShiftUp.ID, MyKeyAction { nextRow(moveUp = true) })
    actionMap.put(TableActions.ShiftDown.ID, MyKeyAction { nextRow(moveUp = false) })
    actionMap.put(TableActions.PageUp.ID, MyKeyAction { nextPage(moveUp = true) })
    actionMap.put(TableActions.PageDown.ID, MyKeyAction { nextPage(moveUp = false) })
    actionMap.put(TableActions.ShiftPageUp.ID, MyKeyAction { nextPage(moveUp = true) })
    actionMap.put(TableActions.ShiftPageDown.ID, MyKeyAction { nextPage(moveUp = false) })
    actionMap.put(TableActions.CtrlHome.ID, MyKeyAction { moveToAbsoluteFirst() })
    actionMap.put(TableActions.CtrlEnd.ID, MyKeyAction { moveToAbsoluteEnd() })
    actionMap.put(TableActions.CtrlShiftHome.ID, MyKeyAction { moveToAbsoluteFirst() })
    actionMap.put(TableActions.CtrlShiftEnd.ID, MyKeyAction { moveToAbsoluteEnd() })

    // Setup additional actions for the table
    registerKey(KeyStrokes.ENTER) { smartEnter(toggleOnly = false) }
    registerKey(KeyStrokes.SPACE) { smartEnter(toggleOnly = true) }
    registerKey(KeyStrokes.NUM_LEFT) { modifyGroup(expand = false) }
    registerKey(KeyStrokes.NUM_RIGHT) { modifyGroup(expand = true) }
    registerKey(KeyStrokes.HOME) { moveToFirstRow() }
    registerKey(KeyStrokes.END) { moveToLastRow() }

    // Disable auto start editing from JTable
    putClientProperty("JTable.autoStartsEdit", java.lang.Boolean.FALSE)
  }

  private fun registerKey(key: KeyStroke, action: () -> Unit) {
    val name = key.toString().replace(" ", "-")
    registerActionKey(action, key, name, { true }, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
  }

  private fun toggleTreeNode(row: Int) {
    val index = convertRowIndexToModel(row)
    model.toggle(index)
    scrollCellIntoView(row, 0)
  }

  private fun selectRow(row: Int) {
    changeSelection(row, selectedColumn.coerceIn(0, 1), false, false)
  }

  private fun selectColumn(column: Int) {
    getColumnModel().selectionModel.setSelectionInterval(column, column)
  }

  private fun toggleAndSelect(row: Int) {
    toggleTreeNode(row)
    selectRow(row)
  }

  @Suppress("SameParameterValue")
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
      requestFocus()
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
    if (resizeHandler.resizeMode || !super.editCellAt(row, column, event)) {
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

  private class MyKeyAction(private val action: () -> Unit) : AbstractAction() {
    override fun actionPerformed(event: ActionEvent) {
      action()
    }
  }

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
      model.isGroupItem(item) && !tableModel.isCellEditable(item(row), PTableColumn.NAME) ->
        toggleAndSelect(row)
      toggleOnly -> quickEdit(row, 1)
      else -> {
        if (!startEditing(row, 0) {}) {
          startEditing(row, 1) {}
        }
      }
    }
  }

  /** Expand/Collapse items after right/left key press */
  private fun modifyGroup(expand: Boolean) {
    if (editingColumn == 1) {
      // If the value column is being edited: we should not expand/collapse the table row.
      // Then the editor will disappear unexpectedly.
      return
    }
    val row = selectedRow
    if (row == -1) {
      return
    }

    val index = convertRowIndexToModel(row)
    if (expand) {
      model.expand(index)
    } else {
      model.collapse(index)
    }
    selectRow(row)
  }

  /** Scroll the selected row up/down. */
  private fun nextRow(moveUp: Boolean) {
    val selectedRow = selectedRow
    if (isEditing) {
      removeEditor()
      requestFocus()
    }
    when {
      moveUp && selectedRow == 0 && previousNonEmptyTable != null ->
        previousNonEmptyTable!!.moveToLastRow()
      !moveUp && selectedRow == rowCount - 1 && nextNonEmptyTable != null ->
        nextNonEmptyTable!!.moveToFirstRow()
      moveUp -> selectRow(max(0, selectedRow - 1))
      else -> selectRow(min(selectedRow + 1, rowCount - 1))
    }
  }

  /** Scroll the selected row up/down. */
  private fun nextPage(moveUp: Boolean) {
    if (isEditing) {
      removeEditor()
      requestFocus()
    }

    // PTable is expected to be inside a JScrollPane: use the height of the viewport.
    // If no viewport is found fall back to the visible height of the table.
    val viewport = parent.parent as? JViewport
    val visibleHeight = viewport?.height ?: visibleRect.getHeight().toInt()
    if (visibleHeight <= 0) {
      return
    }
    val selectedRow = selectedRow.coerceIn(0, rowCount - 1)
    if (moveUp) {
      moveToOffset(forward = false, offsetOfRow(selectedRow) - visibleHeight)
    } else {
      moveToOffset(forward = true, offsetOfRow(selectedRow) + visibleHeight)
    }
  }

  private val previousNonEmptyTable: PTableImpl?
    get() {
      var prev = previousTable as? PTableImpl ?: return null
      while (prev.rowCount == 0 || !prev.isShowing) {
        prev = prev.previousTable as? PTableImpl ?: return null
      }
      return prev
    }

  private val nextNonEmptyTable: PTableImpl?
    get() {
      var next = nextTable as? PTableImpl ?: return null
      while (next.rowCount == 0 || !next.isShowing) {
        next = next.nextTable as? PTableImpl ?: return null
      }
      return next
    }

  private fun offsetOfRow(row: Int): Int {
    val rect = getCellRect(row, 0, false)
    return y + rect.y + rect.height / 2
  }

  private fun moveToOffset(forward: Boolean, offset: Int) {
    when {
      !forward && offset < y && previousNonEmptyTable != null ->
        previousNonEmptyTable?.moveToOffset(false, offset)
      forward && offset > y + height && nextNonEmptyTable != null ->
        nextNonEmptyTable?.moveToOffset(true, offset)
      else -> {
        val rowHeight = getRowHeight()
        val range = 0 until rowCount
        var estimatedRowIndex1 = ((offset - y) / rowHeight).coerceIn(range)
        var estimatedRowIndex2 = estimatedRowIndex1
        var diff1 = offsetOfRow(estimatedRowIndex1) - offset
        var diff2 = diff1
        while (
          diff1.sign == diff2.sign && diff2 != 0 && range.contains(estimatedRowIndex2 - diff2.sign)
        ) {
          estimatedRowIndex1 = estimatedRowIndex2
          diff1 = diff2
          estimatedRowIndex2 -= diff2.sign
          diff2 = offsetOfRow(estimatedRowIndex2) - offset
        }
        val rowIndex =
          if (diff1.absoluteValue < diff2.absoluteValue) estimatedRowIndex1 else estimatedRowIndex2
        if (!hasFocus()) {
          requestFocus()
        }
        selectRow(rowIndex)
      }
    }
  }

  private fun moveToFirstRow() {
    if (isEditing) {
      removeEditor()
      requestFocus()
    }
    if (!hasFocus()) {
      requestFocus()
    }
    selectRow(0)
  }

  private fun moveToLastRow() {
    if (isEditing) {
      removeEditor()
      requestFocus()
    }
    if (!hasFocus()) {
      requestFocus()
    }
    selectRow(rowCount - 1)
  }

  private fun moveToAbsoluteFirst() {
    var firstTable = this
    var previous = previousNonEmptyTable
    while (previous != null) {
      firstTable = previous
      previous = previous.previousNonEmptyTable
    }
    firstTable.moveToFirstRow()
  }

  private fun moveToAbsoluteEnd() {
    var lastTable = this
    var next = nextNonEmptyTable
    while (next != null) {
      lastTable = next
      next = next.nextNonEmptyTable
    }
    lastTable.moveToLastRow()
  }

  // TODO: Change this from MouseMoveListener to TableHoverListener when the latter is a stable API
  private fun installHoverListener() {
    addMouseMotionListener(
      object : MouseMotionAdapter() {
        override fun mouseMoved(event: MouseEvent) {
          val point = event.point
          val row = rowAtPoint(point)
          val column = PTableColumn.fromColumn(columnAtPoint(point))
          val renderer =
            if (
              !(row == editingRow &&
                column.ordinal == editingColumn) && // this cell is not being edited
                tableModel.hasCustomCursor(item(row), column)
            )
              getRenderer(row, column)
            else null

          // Replace the cursor of the table to the cursor of the component in the renderer the
          // mouse event points to.
          cursor =
            if (resizeHandler.resizeMode) Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)
            else
              renderer?.let {
                val rect = getCellRect(row, column.ordinal, true)
                val component =
                  SwingUtilities.getDeepestComponentAt(renderer, event.x - rect.x, event.y - rect.y)
                // The property panel is using text editors to display text.
                // Ignore the I-beam from those components.
                if (component is JTextComponent) null else component?.cursor
              } ?: Cursor.getDefaultCursor()
        }
      }
    )
  }

  /** Lookup the renderer for the given [row] and [column]. */
  private fun getRenderer(row: Int, column: PTableColumn): Component =
    prepareRenderer(getCellRenderer(row, column.ordinal), row, column.ordinal).also { renderer ->
      if (renderer.preferredSize.height > rowHeight) {
        // Variable height renders needs to be resized
        TreeWalker(renderer).descendantStream().forEach(Component::doLayout)
      }
    }

  // ========== Group Expansion on Mouse Click =================================

  /** MouseListener */
  private inner class MouseTableListener : MouseAdapter() {

    /** Handle expansion/collapse after clicking on the expand icon in the name column. */
    override fun mousePressed(event: MouseEvent) {
      val row = rowAtPoint(event.point)
      if (row == -1) {
        return
      }
      val column = columnAtPoint(event.point)
      if (column != 0) {
        return
      }

      // Ignore a toggle if the name cell is editable. Allow the TableUI to start editing instead:
      if (tableModel.isCellEditable(item(row), PTableColumn.NAME)) {
        val editor = editorComponent ?: return
        val newEvent = SwingUtilities.convertMouseEvent(this@PTableImpl, event, editor)
        editor.dispatchEvent(newEvent)
        return
      }

      val rectLeftColumn = getCellRect(row, convertColumnIndexToView(0), false)
      rectLeftColumn.width = UIUtil.getTreeExpandedIcon().iconWidth
      if (rectLeftColumn.contains(event.x, event.y)) {
        toggleTreeNode(row)
        event.consume()
      }
    }
  }

  // ========== KeyListener ====================================================

  /** PTableKeyListener is our own implementation of "JTable.autoStartsEdit" */
  private inner class PTableKeyListener : KeyAdapter() {

    override fun keyTyped(event: KeyEvent) {
      val row = selectedRow
      val type = Character.getType(event.keyChar).toByte()
      if (
        isEditing ||
          row == -1 ||
          type == Character.CONTROL ||
          type == Character.OTHER_SYMBOL ||
          type == Character.SPACE_SEPARATOR
      ) {
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
              val keyEvent =
                KeyEvent(
                  textEditor,
                  event.id,
                  event.`when`,
                  event.modifiers,
                  event.keyCode,
                  event.keyChar,
                )
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

/** A custom [TableExpandableItemsHandler] for a properties table. */
@VisibleForTesting
class PTableExpandableItemsHandler(table: PTableImpl) : TableExpandableItemsHandler(table) {
  private var expandedCell: TableCell? = null

  @TestOnly // Get access to a protected method:
  fun computeCellRendererAndBounds(key: TableCell): Pair<Component, Rectangle>? {
    return getCellRendererAndBounds(key)
  }

  /**
   * Return the currently expanded items.
   *
   * The super class will return nothing if the popup is not shown. Some controls may be rendered
   * differently when "expanded" to see the entire value. Such an "expended" renderer may fit in the
   * table cell i.e. no popup will be shown. We still need to know that the item is "expanded"
   * versus showing in its normal form.
   *
   * Override this method to provide this functionality.
   */
  override fun getExpandedItems(): Collection<TableCell> {
    return if (expandedCell == null) emptyList() else setOf(expandedCell!!)
  }

  /**
   * Find the [TableCell] over the [point] for the parent [TableExpandableItemsHandler].
   *
   * The parent handler may decide not to display a popup for several reasons.
   *
   * We may be using a different renderer for the expanded value (to hide buttons that doesn't make
   * sense). That could mean the expanded renderer fits in the table cell. Save the expandedCell in
   * this class and let the parent handler handle the popup.
   *
   * When the expanded cell changes: invalidate the affected cells such that we can repaint them
   * with the proper renderer.
   */
  override fun getCellKeyForPoint(point: Point): TableCell? {
    val cell = computeRestrictedCellAtPoint(point)
    if (expandedCell != cell) {
      cell?.invalidate()
      expandedCell?.invalidate()
    }
    expandedCell = cell
    return cell
  }

  private fun TableCell.invalidate() =
    myComponent.repaint(myComponent.getCellRect(row, column, true))

  /**
   * Compute the [TableCell] at [point] that has a cell renderer where the value is restricted due
   * to limited space in the cell. Find the component under the mouse and check if the component is
   * visually restricted. In that way a ComboBox can reject expansions when hovering over the drop
   * down button, but accept expansions when hovering over the text part of the ComboBox.
   */
  private fun computeRestrictedCellAtPoint(point: Point): TableCell? {
    val cell = super.getCellKeyForPoint(point) ?: return null
    val value = myComponent.getValueAt(cell.row, cell.column)
    val renderer = myComponent.getCellRenderer(cell.row, cell.column)
    val component =
      renderer.getTableCellRendererComponent(
        myComponent,
        value,
        false,
        false,
        cell.row,
        cell.column,
      )
    val bounds = myComponent.getCellRect(cell.row, cell.column, true)
    val componentUnderMouse =
      SwingUtilities.getDeepestComponentAt(component, point.x - bounds.x, point.y - bounds.y)
        ?: return null
    val isVisuallyRestricted = ClientProperty.get(componentUnderMouse, KEY_IS_VISUALLY_RESTRICTED)
    if (cell == expandedCell && isVisuallyRestricted != null) {
      // Since an expanded cell will return false to "isVisuallyRestricted", we will maintain
      // expanded cells when the mouse is over a component that has a visually restricted callback.
      // This will avoid flickering in the expanded cells: b/281650931
      return expandedCell
    }
    return cell.takeIf { isVisuallyRestricted != null && isVisuallyRestricted() }
  }

  /**
   * Return a little extra space on the right, such that expanded text has a little empty space on
   * the right.
   */
  override fun getCellRendererAndBounds(key: TableCell): Pair<Component, Rectangle>? {
    val rendererAndBounds = super.getCellRendererAndBounds(key) ?: return null
    rendererAndBounds.second.width += JBUIScale.scale(EXPANSION_RIGHT_PADDING)
    return rendererAndBounds
  }

  /**
   * Intellij has disabled [TableExpandableItemsHandler] is the table is not in a
   * [javax.swing.JScrollPane]. Our property table has a scroll pane around the parent JPanel of the
   * table, and we still want to support table expansion.
   */
  override fun isEnabled(): Boolean {
    return true
  }
}
