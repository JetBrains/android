/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.componenttree.treetable

import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.componenttree.api.BadgeItem
import com.android.tools.componenttree.api.ColumnInfo
import com.android.tools.componenttree.api.ContextPopupHandler
import com.android.tools.componenttree.api.DoubleClickHandler
import com.android.tools.componenttree.api.TableVisibility
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.invokeLater
import com.intellij.ui.DisabledTraversalPolicy
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.ui.treeStructure.treetable.TreeTableModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Component
import java.awt.Graphics
import java.awt.Point
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.event.MouseEvent
import java.lang.Integer.max
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.TransferHandler
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.table.JTableHeader
import javax.swing.table.TableCellRenderer
import javax.swing.tree.ExpandVetoException
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

private const val HOVER_CELL = "component.tree.hover.cell"
internal class Cell(val row: Int, val column: Int) {
  fun equalTo(otherRow: Int, otherColumn: Int) = otherRow == row && otherColumn == column
}

internal var JTable.hoverCell: Cell?
  get() = getClientProperty(HOVER_CELL) as? Cell
  set(value) { putClientProperty(HOVER_CELL, value)}

class TreeTableImpl(
  model: TreeTableModelImpl,
  private val contextPopup: ContextPopupHandler,
  private val doubleClick: DoubleClickHandler,
  private val painter: (() -> Control.Painter?)?,
  private val installKeyboardActions: (JComponent) -> Unit,
  treeSelectionMode: Int,
  autoScroll: Boolean,
  installTreeSearch: Boolean,
  treeHeaderRenderer: TableCellRenderer?
) : TreeTable(model), TableVisibility {
  private val extraColumns: List<ColumnInfo>
  private var initialized = false
  private var dropTargetHandler: TreeTableDropTargetHandler? = null
  private val hiddenColumns = mutableSetOf<Int>()
  val treeTableSelectionModel = TreeTableSelectionModelImpl(this)
  private val emptyComponent = JLabel()
  private val emptyTreeCellRenderer = TableCellRenderer { _, _, _, _, _, _ -> emptyComponent }
  private var initialHeaderVisibility = false

  init {
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = DisabledTraversalPolicy()
    resetDefaultFocusTraversalKeys()
    tree.cellRenderer = TreeCellRendererImpl(this)
    tree.addTreeWillExpandListener(ExpansionListener())
    tree.selectionModel.selectionMode = treeSelectionMode
    selectionModel.selectionMode = treeSelectionMode.toTableSelectionMode()
    setExpandableItemsEnabled(true)
    extraColumns = model.columns
    columnModel.getColumn(0).headerRenderer = treeHeaderRenderer
    initExtraColumns()
    model.addTreeModelListener(DataUpdateHandler(treeTableSelectionModel))
    MouseHandler().let {
      addMouseListener(it)
      addMouseMotionListener(it)
    }

    if (autoScroll) {
      treeTableSelectionModel.addAutoScrollListener {
        invokeLater {
          selectionModel.selectedIndices.singleOrNull()?.let { scrollRectToVisible(getCellRect(it, 0, true)) }
        }
      }
    }
    if (installTreeSearch) {
      TreeSpeedSearch(tree, false) { model.toSearchString(it.lastPathComponent) }
    }
    initialized = true
    updateUI()
  }

  private fun initExtraColumns() {
    for (index in extraColumns.indices) {
      val columnInfo = extraColumns[index]
      val dataWidth = columnInfo.width.takeIf { it > 0 }
                      ?: columnInfo.computeWidth(this, tableModel.allNodes).takeIf { it > 0 }
                      ?: JBUIScale.scale(10)
      val component = columnInfo.headerRenderer?.getTableCellRendererComponent(this, null, false, false, 0, index + 1)
      val width = max(dataWidth, component?.preferredSize?.width ?: 0)
      setColumnWidth(index + 1, width, columnInfo.headerRenderer)
    }
  }

  private fun setColumnWidth(columnIndex: Int, wantedWidth: Int, wantedHeaderRenderer: TableCellRenderer?) {
    val width = if (hiddenColumns.contains(columnIndex)) 0 else wantedWidth
    columnModel.getColumn(columnIndex).apply {
      maxWidth = width
      minWidth = width
      maxWidth = width // set maxWidth twice, since implementation of setMaxWidth depends on the value of minWidth and vice versa
      preferredWidth = width
      headerRenderer = wantedHeaderRenderer
    }
  }

  override fun setHeaderVisibility(visible: Boolean) {
    val columnHeader = (parent?.parent as? JScrollPane)?.columnHeader
    initialHeaderVisibility = visible
    if (columnHeader != null) {
      // If the columnHeader isn't created yet, delay the setting until we are notified through addNotify.
      columnHeader.isVisible = visible
    }
  }

  override fun addNotify() {
    super.addNotify()
    setHeaderVisibility(initialHeaderVisibility)
  }

  override fun setColumnVisibility(columnIndex: Int, visible: Boolean) {
    val changed = if (visible) hiddenColumns.remove(columnIndex) else hiddenColumns.add(columnIndex)
    if (changed) {
      initExtraColumns()
    }
  }

  fun enableDnD() {
    dragEnabled = true
    val treeTransferHandler = TreeTableTransferHandler()
    transferHandler = treeTransferHandler
    dropTargetHandler = TreeTableDropTargetHandler(this) { treeTransferHandler.draggedItem }
    dropTarget = DropTarget(this, dropTargetHandler)
  }

  override fun getTableModel(): TreeTableModelImpl {
    return super.getTableModel() as TreeTableModelImpl
  }

  override fun createDefaultTableHeader(): JTableHeader {
    return TreeTableHeader(this)
  }

  override fun updateUI() {
    super.updateUI()
    if (initialized) {
      tableModel.clearRendererCache()
      installKeyboardActions(this)
      registerActionKey(::toggleTree, KeyStrokes.ENTER, "enter")
      registerActionKey(::toggleTree, KeyStrokes.SPACE, "space")
      extraColumns.forEach { it.updateUI() }
      initExtraColumns()
      dropTargetHandler?.updateUI()
    }
  }

  private fun toggleTree() {
    val row = selectedRow.takeIf { it >= 0 } ?: return
    if (tree.isExpanded(row)) {
      tree.collapseRow(row)
    }
    else {
      tree.expandRow(row)
    }
  }

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer = when (column) {
    0 -> super.getCellRenderer(row, column)
    else -> extraColumns[column - 1].renderer ?: emptyTreeCellRenderer
  }

  override fun adapt(treeTableModel: TreeTableModel): TreeTableModelAdapter =
    object : TreeTableModelAdapter(treeTableModel, tree, this) {
      override fun fireTableDataChanged() {
        // Note: This is called when a tree node is expanded/collapsed.
        // Delay the table update to avoid paint problems during tree node expansions and closures.
        // The problem seem to be caused by this being called from the selection update of the table.
        invokeLater { treeTableSelectionModel.update { super.fireTableDataChanged() } }
      }
    }

  override fun paintComponent(g: Graphics) {
    tree.putClientProperty(Control.Painter.KEY, painter?.invoke())
    super.paintComponent(g)
    dropTargetHandler?.paintDropTargetPosition(g)
    paintColumnDividers(g)
  }

  fun paintColumnDividers(g: Graphics) {
    val color = g.color
    g.color = JBColor.border()
    var x = width
    for (index in extraColumns.indices.reversed()) {
      val columnInfo = extraColumns[index]
      x -= columnModel.getColumn(1 + index).maxWidth
      if (!hiddenColumns.contains(index) && columnInfo.leftDivider) {
        g.drawLine(x, 0, x, height)
      }
    }
    g.color = color
  }

  /**
   * Compute the max render width which is the width of the tree minus indents.
   */
  fun computeMaxRenderWidth(nodeDepth: Int): Int =
    tree.width - tree.insets.right - computeLeftOffset(nodeDepth)

  /**
   * Return the depth of a given pixel distance from the left edge of the table tree.
   */
  fun findDepthFromOffset(x: Int): Int {
    val ourUi = tree.ui as BasicTreeUI
    val childIndent = ourUi.leftChildIndent + ourUi.rightChildIndent
    return maxOf(0, (x - tree.insets.left) / childIndent)
  }

  /**
   * Compute the left offset of a row with the specified [nodeDepth] in the tree.
   *
   * Note: This code is based on the internals of the UI for the tree e.g. the method [BasicTreeUI.getRowX].
   */
  @VisibleForTesting
  fun computeLeftOffset(nodeDepth: Int): Int {
    val ourUi = tree.ui as BasicTreeUI
    return tree.insets.left + (ourUi.leftChildIndent + ourUi.rightChildIndent) * (nodeDepth - 1)
  }

  private fun alwaysExpanded(path: TreePath): Boolean {
    // An invisible root or a root without root handles should always be expanded
    val parentPath = path.parentPath ?: return !tree.isRootVisible || !tree.showsRootHandles

    // The children of an invisible root that are shown without root handles should always be expanded
    return parentPath.parentPath == null && !tree.isRootVisible && !tree.showsRootHandles
  }

  private val selectedItem: Any?
    get() {
      val selectedRow = selectedRow
      if (selectedRow < 0) {
        return null
      }
      return getValueAt(selectedRow, 0)
    }

  private fun Int.toTableSelectionMode() = when(this) {
    TreeSelectionModel.SINGLE_TREE_SELECTION -> ListSelectionModel.SINGLE_SELECTION
    TreeSelectionModel.CONTIGUOUS_TREE_SELECTION -> ListSelectionModel.SINGLE_INTERVAL_SELECTION
    TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION -> ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    else -> ListSelectionModel.SINGLE_SELECTION
  }

  private inner class DataUpdateHandler(private val selectionModel: TreeTableSelectionModelImpl): TreeTableModelImplAdapter() {
    override fun treeChanged(event: TreeModelEvent) {
      initExtraColumns()
      selectionModel.keepSelectionDuring {
        val expanded = TreeUtil.collectExpandedPaths(tree)
        tableModel.fireTreeStructureChange(event)
        TreeUtil.restoreExpandedPaths(tree, expanded)
      }
      (transferHandler as? TreeTableTransferHandler)?.resetDraggedItem()
      dropTargetHandler?.reset()
      if (!tree.isRootVisible || !tree.showsRootHandles) {
        tableModel.root?.let { root ->
          val paths = mutableListOf(TreePath(root))
          tableModel.children(root).mapTo(paths) { TreePath(arrayOf(root, it)) }
          paths.filter { alwaysExpanded(it) }.forEach { tree.expandPath(it) }
        }
      }
    }

    override fun columnDataChanged() {
      initExtraColumns()
      repaint()
    }
  }

  private inner class ExpansionListener : TreeWillExpandListener {
    override fun treeWillExpand(event: TreeExpansionEvent) {}

    override fun treeWillCollapse(event: TreeExpansionEvent) {
      if (alwaysExpanded(event.path)) {
        throw ExpandVetoException(event)
      }
    }
  }

  private inner class MouseHandler : PopupHandler() {
    override fun invokePopup(comp: Component, x: Int, y: Int) {
      val cell = position(x, y) ?: return
      val item = getValueAt(cell.row, cell.column)
      when {
        cell.column == 0 -> contextPopup(this@TreeTableImpl, x, y)
        else -> extraColumns[cell.column - 1].showPopup(item, this@TreeTableImpl, x, y)
      }
    }

    override fun mouseClicked(event: MouseEvent) {
      if (event.button == MouseEvent.BUTTON1 && !event.isPopupTrigger && !event.isShiftDown && !event.isControlDown && !event.isMetaDown) {
        val cell = position(event.x, event.y) ?: return
        val item = getValueAt(cell.row, cell.column)
        when {
          cell.column == 0 && event.clickCount == 2 -> doubleClick()
          cell.column > 0 && event.clickCount == 1 -> {
            val bounds = getCellRect(cell.row, cell.column, true)
            extraColumns[cell.column - 1].performAction(item, this@TreeTableImpl, bounds)
          }
        }
      }
    }

    override fun mouseMoved(event: MouseEvent) {
      val cell = position(event.x, event.y)
      val oldHoverCell = hoverCell
      if (cell != oldHoverCell) {
        hoverCell = cell
        repaintBadge(oldHoverCell)
        repaintBadge(cell)
      }
    }

    override fun mouseExited(event: MouseEvent) {
      repaintBadge(hoverCell)
      hoverCell = null
    }

    private fun position(x: Int, y: Int): Cell? {
      val point = Point(x, y)
      val row = rowAtPoint(point)
      val column = columnAtPoint(point)
      return if (row >= 0 && column >= 0) Cell(row, column) else null
    }

    private fun repaintBadge(cell: Cell?) {
      val column = cell?.column ?: return
      if (column >= 1) {
        val badge = extraColumns[column - 1] as? BadgeItem ?: return
        val item = getValueAt(cell.row, column) ?: return
        if (badge.getHoverIcon(item) != null) {
          repaint(getCellRect(cell.row, column, true))
        }
      }
    }
  }

  private inner class TreeTableTransferHandler : TransferHandler() {
    var draggedItem: Any? = null
      private set

    fun resetDraggedItem() {
      draggedItem = null
    }

    override fun getSourceActions(component: JComponent): Int = DnDConstants.ACTION_COPY_OR_MOVE

    override fun createTransferable(component: JComponent): Transferable? {
      val item = selectedItem ?: return null
      val transferable = tableModel.createTransferable(item) ?: return null
      dragImage = tableModel.createDragImage(item)
      draggedItem = item
      return transferable
    }

    override fun exportDone(source: JComponent, data: Transferable, action: Int) {
      if (action == DnDConstants.ACTION_MOVE) {
        draggedItem?.let { tableModel.delete(it) }
      }
      draggedItem = null
    }
  }
}
