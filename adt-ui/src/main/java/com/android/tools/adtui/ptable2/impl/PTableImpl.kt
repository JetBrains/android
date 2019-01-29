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
package com.android.tools.adtui.ptable2.impl

import com.android.tools.adtui.ptable2.PTable
import com.android.tools.adtui.ptable2.PTableCellEditorProvider
import com.android.tools.adtui.ptable2.PTableCellRendererProvider
import com.android.tools.adtui.ptable2.PTableColumn
import com.android.tools.adtui.ptable2.PTableGroupItem
import com.android.tools.adtui.ptable2.PTableItem
import com.android.tools.adtui.ptable2.PTableModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.EventObject
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.event.ChangeEvent
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter
import javax.swing.text.JTextComponent
import kotlin.properties.Delegates

/**
 * Implementation of a [PTable].
 *
 * The intention is to hide implementation details in this class, and only
 * expose a minimal API in [PTable].
 */
class PTableImpl(override val tableModel: PTableModel,
                 override val context: Any?,
                 private val rendererProvider: PTableCellRendererProvider,
                 private val editorProvider: PTableCellEditorProvider,
                 private val customToolTipHook: (MouseEvent) -> String? = { null })
  : JBTable(PTableModelImpl(tableModel)), PTable {

  private val nameRowSorter = TableRowSorter<TableModel>()
  private val nameRowFilter = NameRowFilter()
  private val tableCellRenderer = PTableCellRendererWrapper()
  private val tableCellEditor = PTableCellEditorWrapper()
  override val backgroundColor: Color
    get() = super.getBackground()
  override val foregroundColor: Color
    get() = super.getForeground()
  override val activeFont: Font
    get() = super.getFont()
  override val gridLineColor: Color
    get() = gridColor

  init {
    // The row heights should be identical, save time by only looking at the first rows
    super.setMaxItemsForSizeCalculation(5)

    super.setShowColumns(false)
    super.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN)
    super.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

    super.setShowGrid(false)
    super.setShowHorizontalLines(true)
    super.setIntercellSpacing(Dimension(0, JBUI.scale(1)))
    super.setGridColor(UIUtil.getSlightlyDarkerColor(background))

    super.setColumnSelectionAllowed(false)
    super.setCellSelectionEnabled(false)
    super.setRowSelectionAllowed(true)

    super.addMouseListener(MouseTableListener())
    super.addKeyListener(PTableKeyListener())

    super.resetDefaultFocusTraversalKeys()
    super.setFocusTraversalPolicyProvider(true)
    super.setFocusTraversalPolicy(PTableFocusTraversalPolicy())

    // We want expansion for the property names but not of the editors. This disables expansion for both columns.
    // TODO: Provide expansion of the left column only.
    super.setExpandableItemsEnabled(false)
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

  override fun startEditing(row: Int) {
    if (row < 0) {
      removeEditor()
    }
    else if (!startEditing(row, 0) {}) {
      startEditing(row, 1) {}
    }
  }

  override fun startNextEditor(): Boolean {
    val pos = TablePosition(0, 0, itemCount)
    if (isEditing) {
      pos.row = editingRow
      pos.column = editingColumn
      removeEditor()
      pos.next(true)
    }
    while (pos.wrapped == 0 && !tableModel.isCellEditable(item(pos.row), pos.tableColumn)) {
      pos.next(true)
    }
    if (pos.wrapped > 0) {
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
    // Customize keymaps. See https://docs.oracle.com/javase/tutorial/uiswing/misc/keybinding.html for info on how this works, but the
    // summary is that we set an input map mapping key bindings to a string, and an action map that maps those strings to specific actions.
    val actionMap = actionMap
    val focusedInputMap = getInputMap(JComponent.WHEN_FOCUSED)
    val ancestorInputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)

    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "smartEnter")
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))
    actionMap.put("smartEnter", MyEnterAction(false))

    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggleEditor")
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0))
    actionMap.put("toggleEditor", MyEnterAction(true))

    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0))
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0))
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "expandCurrentRight")
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0), "expandCurrentRight")
    actionMap.put("expandCurrentRight", MyExpandCurrentAction(true))

    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0))
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0))
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "collapseCurrentLeft")
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0), "collapseCurrentLeft")
    actionMap.put("collapseCurrentLeft", MyExpandCurrentAction(false))

    // Page Up & Page Down
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "pageUp")
    actionMap.put("pageUp", MyPageUpAction())
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "pageDown")
    actionMap.put("pageDown", MyPageDownAction())

    // Home and End key
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "home")
    actionMap.put("home", MyHomeAction())
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), "end")
    actionMap.put("end", MyEndAction())

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
    // b/37132037 Move focus back to table before hiding the editor
    val editor = editorComponent
    if (editor != null && IJSwingUtilities.hasFocus(editor)) {
      requestFocus()
    }

    // Now remove the editor
    repaintOtherCellInRow()
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
    return true
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
  private inner class MyEnterAction(private val toggleOnly: Boolean) : AbstractAction() {

    override fun actionPerformed(event: ActionEvent) {
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
  }

  /**
   * Expand/Collapse items after right/left key press
   */
  private inner class MyExpandCurrentAction(private val expand: Boolean) : AbstractAction() {

    override fun actionPerformed(event: ActionEvent) {
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
  }

  /**
   * Scroll the selected row when pressing Page Up key
   */
  private inner class MyPageUpAction : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
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
      selectRow(Math.max(0, selectedRow - movement))
    }
  }

  /**
   * Scroll the selected row when pressing Page Down key
   */
  private inner class MyPageDownAction : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
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
      selectRow(Math.min(selectedRow + movement, rowCount - 1))
    }
  }

  /**
   * Scroll the selected row when pressing Page Up key
   */
  private inner class MyHomeAction : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
      val selectedRow = selectedRow
      if (isEditing || selectedRow == -1) {
        return
      }

      selectRow(0)
    }
  }

  /**
   * Scroll the selected row when pressing Page Down key
   */
  private inner class MyEndAction : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
      val selectedRow = selectedRow
      if (isEditing || selectedRow == -1) {
        return
      }

      selectRow(rowCount - 1)
    }
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
      val table = this@PTableImpl
      val row = table.selectedRow
      if (table.isEditing || row == -1 || event.keyChar == '\t' || event.keyCode == KeyEvent.VK_ESCAPE) {
        return
      }
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
      if (!table.startEditing(row, 0, focusRequest)) {
        table.startEditing(row, 1, focusRequest)
      }
    }
  }

  // ========== Focus Traversal Policy =========================================

  /**
   * FocusTraversalPolicy for [PTableImpl].
   *
   * Setup focus traversal keys such that tab takes focus out of the table if the user is not editing.
   * When the user is editing the focus traversal keys will move to the next editable cell.
   */
  private inner class PTableFocusTraversalPolicy : LayoutFocusTraversalPolicy() {

    override fun getComponentAfter(aContainer: Container, aComponent: Component): Component? {
      val after = super.getComponentAfter(aContainer, aComponent)
      if (after != null && after != this@PTableImpl) {
        return after
      }
      return if (this@PTableImpl.isEditing) editNextEditableCell(true) else null
    }

    override fun getComponentBefore(aContainer: Container, aComponent: Component): Component? {
      val before = super.getComponentBefore(aContainer, aComponent)
      if (before != null && before != this@PTableImpl) {
        return before
      }
      return if (this@PTableImpl.isEditing) editNextEditableCell(false) else null
    }

    private fun editNextEditableCell(forwards: Boolean): Component? {
      val table = this@PTableImpl
      val rows = table.rowCount
      val pos = TablePosition(table.editingRow, table.editingColumn, rows)

      // Make sure we don't loop forever
      while (pos.rowIterations < rows) {
        pos.next(forwards)
        if (table.isCellEditable(pos.row, pos.column)) {
          table.setRowSelectionInterval(pos.row, pos.row)
          table.startEditing(pos.row, pos.column) {}
          return table.editorComponent
        }
      }
      // We can't find an editable cell.
      // Delegate focus out of the table.
      return null
    }

    override fun getFirstComponent(aContainer: Container): Component {
      return this@PTableImpl
    }

    override fun getLastComponent(aContainer: Container): Component {
      return this@PTableImpl
    }

    override fun getDefaultComponent(aContainer: Container): Component? {
      return null
    }
  }
}

private class NameRowFilter : RowFilter<TableModel, Int>() {
  private val comparator = SpeedSearchComparator(false)
  var pattern = ""

  override fun include(entry: RowFilter.Entry<out TableModel, out Int>): Boolean {
    val item = entry.getValue(0) as PTableItem
    if (isMatch(item.name)) {
      return true
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

private class TablePosition(var row: Int, var column: Int, val rows: Int) {

  val tableColumn: PTableColumn
    get() = PTableColumn.fromColumn(column)

  var rowIterations: Int = 0
    private set

  // Use [wrapped] to count how many times we wrapped from end to start or vice versa
  var wrapped: Int = 0
    private set

  fun next(forward: Boolean) {
    if (forward) {
      forwards()
    }
    else {
      backwards()
    }
  }

  private fun forwards() {
    when (column) {
      0 -> { column = 1 }
      1 -> { column = 0; nextRow() }
    }
  }

  private fun backwards() {
    when (column) {
      0 -> { column = 1; previousRow() }
      1 -> { column = 0 }
    }
  }

  private fun nextRow() {
    row++
    rowIterations++
    column = 0
    if (row >= rows) {
      row = 0
      wrapped++
    }
  }

  private fun previousRow() {
    row--
    rowIterations++
    column = 1
    if (row < 0) {
      wrapped++
      row = rows - 1
    }
  }
}
