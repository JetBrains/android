/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.adtui.categorytable

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.event.DelegateMouseEventHandler
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.Executor
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.InputMap
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.Scrollable
import javax.swing.SizeRequirements
import javax.swing.SortOrder
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * A JTable-like UI component that displays tabular data, but also allows grouping rows into
 * hierarchical categories, and showing / hiding those categories like a JTree.
 *
 * Unlike JTable, child components are created for each cell and added to the widget hierarchy, so
 * event handlers work normally. This also means that mouse events captured by a cell widget will
 * not be received by the [CategoryTable]; cell widgets may [forward] the mouse event to the table
 * if they want the table to handle the event as well (for row selection).
 *
 * The value type of the table, T, should be an immutable data class. Its components will be read
 * frequently via its [Attribute] classes; the values should not change. Instead, [removeRow] and
 * [addRow] can be used to replace rows, or, if a [primaryKey] is provided, [updateRow] can be used
 * to update a row "in-place".
 */
@UiThread
class CategoryTable<T : Any>(
  val columns: ColumnList<T>,
  val primaryKey: (T) -> Any = { it },
  private val coroutineDispatcher: CoroutineDispatcher = defaultCoroutineDispatcher,
  colors: Colors = defaultColors,
  private val rowDataProvider: ValueRowDataProvider<T> = NullValueRowDataProvider,
  val emptyStatePanel: JComponent? = null,
) : JBPanel<CategoryTable<T>>(), Scrollable {

  /** The listener called when a category row is clicked. */
  var categoryRowMouseClickListener: CategoryRowMouseClickListener<T> =
    DefaultCategoryRowMouseClickListener()

  /** The listener called when a column header is clicked. */
  var categoryTableHeaderClickListener: CategoryTableHeaderClickListener<T> =
    DefaultCategoryTableHeaderClickListener()

  internal val header =
    CategoryTableHeader(columns, { columnSorters.firstOrNull() }, ::mouseClickedOnHeader).also {
      it.reorderingAllowed = false
      it.resizingAllowed = false
    }

  /**
   * The values in the table, in display order (considering grouping and sorting). Maintained by
   * [groupAndSortValues].
   */
  var values: List<T> = emptyList()
    private set

  /**
   * All [CategoryRowComponent] and [ValueRowComponent] components in the table, in display order.
   * Includes both visible and invisible rows.
   */
  internal var rowComponents: List<RowComponent<T>> = emptyList()
    private set

  /** CategoryRowComponents indexed by their CategoryList. */
  private var categoryRows = mutableMapOf<CategoryList<T>, CategoryRowComponent<T>>()

  /** ValueRowComponents indexed by their primary key. These are reused when the value changes. */
  private val valueRows = mutableMapOf<Any, ValueRowComponent<T>>()

  private val hiddenRows = mutableSetOf<Any>()

  /** The columns we are grouping by, in order. */
  @get:AnyThread
  @Volatile
  var groupByAttributes = persistentListOf<Attribute<T, *>>()
    private set

  /** We sort groups of leaf rows in the table by these sorters, in order. */
  @get:AnyThread
  @Volatile
  var columnSorters = persistentListOf<ColumnSortOrder<T>>()
    private set(value) {
      field = value
      groupAndSortValues()
      updateComponents()
      header.repaint()
    }

  @get:AnyThread
  @Volatile
  var collapsedNodes = persistentSetOf<CategoryList<T>>()
    private set

  val selection = CategoryTableSingleSelection(this)

  /** The number of pixels to indent the rows following each category header. */
  var categoryIndent = 24

  private val scaledCategoryIndent
    get() = JBUI.scale(categoryIndent)

  private val actions =
    listOf(
      TableActions.SELECT_NEXT_ROW.makeAction { selection.selectNextRow() },
      TableActions.SELECT_PREVIOUS_ROW.makeAction { selection.selectPreviousRow() },
      TableActions.TOGGLE_EXPAND_COLLAPSE.makeAction { selection.toggleSelectedRowCollapsed() },
      TableActions.EXPAND.makeAction { selection.expandSelectedRow() },
      TableActions.COLLAPSE.makeAction { selection.collapseSelectedRow() },
    )

  private var scope = createComponentScope()

  private val tablePresentationManager = TablePresentationManager()
  private var selectedPresentation =
    TablePresentation(colors.selectedForeground, colors.selectedBackground, true)
  private var unselectedPresentation =
    TablePresentation(colors.unselectedForeground, colors.unselectedBackground, false)

  init {
    emptyStatePanel?.let { add(it) }

    unselectedPresentation.applyColors(this)

    header.columnModel.addColumnModelListener(
      object : TableColumnModelListener {
        override fun columnAdded(e: TableColumnModelEvent?) {}

        override fun columnRemoved(e: TableColumnModelEvent?) {}

        override fun columnMoved(e: TableColumnModelEvent?) {
          invalidateRows()
        }

        override fun columnMarginChanged(e: ChangeEvent?) {
          invalidateRows()
        }

        override fun columnSelectionChanged(e: ListSelectionEvent?) {}
      }
    )

    for (action in actions) {
      actionMap.put(action.getValue(Action.NAME), action)
    }

    setInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, CATEGORY_TABLE_INPUT_MAP)

    addMouseListener(
      object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          mousePressedOnRow(e)
        }
      }
    )
  }

  private fun createComponentScope(): CoroutineScope {
    return CoroutineScope(SupervisorJob() + coroutineDispatcher)
  }

  override fun addNotify() {
    super.addNotify()

    if (!scope.coroutineContext.job.isActive) {
      scope = createComponentScope()
    }
    scope.launch {
      // TODO: Make this efficient
      selection.asFlow().collect { selectedKeys -> updateTablePresentation(selectedKeys) }
    }
  }

  override fun removeNotify() {
    super.removeNotify()
    scope.cancel()
  }

  private fun updateTablePresentation(selectedKeys: Set<RowKey<T>>) {
    for (c in rowComponents) {
      tablePresentationManager.applyPresentation(
        c,
        when {
          selectedKeys.contains(c.rowKey) -> selectedPresentation
          else -> unselectedPresentation
        },
      )
    }
  }

  fun addToScrollPane(scrollPane: JScrollPane) {
    scrollPane.setColumnHeaderView(header)
    scrollPane.setViewportView(this)
    // This is required for the JBScrollPane to allocate space for the vertical scrollbar.
    // See "vsbRequiresSpace" in JBScrollPane.Layout.layoutContainer.
    scrollPane.verticalScrollBar.isOpaque = true
  }

  private fun mouseClickedOnHeader(e: MouseEvent) {
    val columnIndex = header.columnAtPoint(e.point)
    if (columnIndex != -1) {
      val column = columns[header.viewIndexToModelIndex(columnIndex)]
      categoryTableHeaderClickListener.columnHeaderClicked(e, this, column)
    }
  }

  internal fun indexOf(key: RowKey<T>): Int = rowComponents.indexOfFirst { it.rowKey == key }

  private fun mousePressedOnRow(e: MouseEvent) {
    if (SwingUtilities.isLeftMouseButton(e)) {
      rowComponents
        .firstOrNull { it.isVisible && e.y < it.y + it.height }
        ?.let { clickedRow ->
          clickedRow.requestFocusInWindow()
          selection.selectRow(clickedRow.rowKey)
        }
    }
  }

  fun setSortOrder(columnSortOrder: List<ColumnSortOrder<T>>) {
    columnSorters = columnSortOrder.toPersistentList()
  }

  fun <C> toggleSortOrder(attribute: Attribute<T, C>) {
    if (attribute.sorter != null) {
      val currentSortOrders = columnSorters
      val newSortOrder =
        when (currentSortOrders.find { it.attribute == attribute }?.sortOrder) {
          SortOrder.ASCENDING -> SortOrder.DESCENDING
          else -> SortOrder.ASCENDING
        }
      // Move the toggled column to the front, followed by the rest.
      columnSorters =
        persistentListOf(ColumnSortOrder(attribute, newSortOrder)) +
          currentSortOrders.filter { it.attribute != attribute }
    }
  }

  fun <C> addGrouping(column: Column<T, C, *>) {
    addGrouping(column.attribute)
  }

  fun <C> addGrouping(attribute: Attribute<T, C>) {
    columns
      .find { it.attribute == attribute && !it.visibleWhenGrouped }
      ?.let { header.removeColumn(attribute) }

    groupByAttributes += attribute
    groupAndSortValues()
    updateComponents()
  }

  fun <C> removeGrouping(column: Column<T, C, *>) {
    removeGrouping(column.attribute)
  }

  fun <C> removeGrouping(attribute: Attribute<T, C>) {
    groupByAttributes -= attribute
    groupAndSortValues()
    updateComponents()

    columns
      .find { it.attribute == attribute && !it.visibleWhenGrouped }
      ?.let { header.restoreColumn(attribute) }
  }

  /**
   * Adds the given row to the table. If a row already exists with the same primary key, it is
   * updated to the new value. This may result in addition or deletion of category nodes.
   *
   * @param beforeKey adds the element before the element with this primary key; if null, adds to
   *   the end. A stable sort is performed after the element is added. Thus, if the new element is
   *   equal in sort order to the given key, it will remain in the same position after the sort.
   * @return true if a new row was added
   */
  fun addOrUpdateRow(rowValue: T, beforeKey: Any? = null): Boolean {
    val key = primaryKey(rowValue)
    val add = !valueRows.contains(key)
    if (add) {
      valueRows[key] =
        ValueRowComponent(rowDataProvider, header, columns, rowValue, key).also {
          addRowComponent(it)
        }
      updateValues { it.withInsertedItemBefore(beforeKey, rowValue) }
    } else {
      updateValues { currentValues ->
        // Replace the value with the same primary key with the given value.
        currentValues.map {
          when {
            primaryKey(it) == key -> rowValue
            else -> it
          }
        }
      }
    }
    updateComponents()
    return add
  }

  private fun List<T>.withInsertedItemBefore(beforeKey: Any?, valueToInsert: T): List<T> {
    val newValues = ArrayList<T>(size + 1)
    for (v in this) {
      if (primaryKey(v) == beforeKey) {
        newValues.add(valueToInsert)
      }
      newValues.add(v)
    }
    if (newValues.size <= size) {
      // Didn't find the key; add it to the end
      newValues.add(valueToInsert)
    }
    return newValues
  }

  private fun addRowComponent(rowComponent: RowComponent<T>) {
    add(rowComponent)
    // We need to receive mouse events on children to handle row selection.
    DelegateMouseEventHandler.delegateTo(this).installListenerOn(rowComponent)
    tablePresentationManager.applyPresentation(rowComponent, unselectedPresentation)
  }

  /** Removes the row (i.e. the row with the same primary key as the given value) from the table. */
  fun removeRow(rowValue: T) {
    removeRowByKey(primaryKey(rowValue))
  }

  /** Removes the row with the given primary key from the table. */
  fun removeRowByKey(key: Any) {
    updateValues { it.filterNot { primaryKey(it) == key } }
    valueRows.remove(key)?.let { remove(it) }
    updateComponents()
  }

  /** Unconditionally updates the categorized values by re-grouping and sorting them. */
  private fun groupAndSortValues() {
    values = groupAndSort(values, groupByAttributes, columnSorters)
  }

  private fun updateValues(updater: (List<T>) -> List<T>) {
    val oldValues = values
    val newValues = updater(oldValues)
    if (newValues != oldValues) {
      values = newValues
      groupAndSortValues()
    }
  }

  /**
   * Update [rowComponents] based on [values]: we have our values and their categorizations, and we
   * need to create or reuse appropriate [CategoryRowComponent] components. Appropriate
   * [ValueRowComponent] components should already exist. We also need to update visibility based on
   * [collapsedNodes].
   */
  private fun updateComponents() {
    val oldCategoryRows = categoryRows
    val newCategoryRows = mutableMapOf<CategoryList<T>, CategoryRowComponent<T>>()
    val newRowComponents = mutableListOf<RowComponent<T>>()

    var categoryList = emptyList<Category<T, *>>()
    // The number of collapsed parents of the current row; if non-zero, hide the current row.
    var collapsedParentCount = 0
    for (value in values) {
      // Remove categories that no longer apply.
      while (categoryList.isNotEmpty() && !categoryList.matches(value)) {
        if (collapsedNodes.contains(categoryList)) {
          collapsedParentCount--
        }
        categoryList = categoryList.subList(0, categoryList.size - 1)
      }

      // Add new categories until we have fully categorized the current value.
      while (categoryList.size < groupByAttributes.size) {

        val newCategory = groupByAttributes[categoryList.size].withValue(value)

        categoryList = categoryList + newCategory
        val categoryRow = oldCategoryRows.remove(categoryList) ?: addCategoryRow(categoryList)
        newCategoryRows[categoryList] = categoryRow
        newRowComponents.add(categoryRow)
        categoryRow.isVisible = collapsedParentCount == 0

        val isCollapsed = collapsedNodes.contains(categoryList)
        categoryRow.isExpanded = !isCollapsed
        if (isCollapsed) {
          collapsedParentCount++
        }
      }

      // Update the ValueRow with the new values.
      valueRows[primaryKey(value)]?.apply {
        this.value = value
        isVisible = !hiddenRows.contains(primaryKey(value)) && collapsedParentCount == 0
        newRowComponents.add(this)
      }
    }

    // Remove obsolete child components
    oldCategoryRows.values.forEach { remove(it) }
    categoryRows = newCategoryRows
    rowComponents = newRowComponents

    emptyStatePanel?.isVisible = rowComponents.isEmpty()

    revalidate()
    repaint()
  }

  private fun addCategoryRow(categoryList: CategoryList<T>) =
    CategoryRowComponent(categoryList).also {
      addRowComponent(it)
      it.addMouseListener(
        object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            categoryRowMouseClickListener.categoryRowClicked(e, this@CategoryTable, categoryList)
          }
        }
      )
    }

  fun setCollapsed(path: CategoryList<T>, collapsed: Boolean) {
    updateCollapsedNodes { if (collapsed) add(path) else remove(path) }
  }

  fun toggleCollapsed(path: CategoryList<T>) {
    updateCollapsedNodes {
      when {
        contains(path) -> remove(path)
        else -> add(path)
      }
    }
  }

  private fun updateCollapsedNodes(update: MutableSet<CategoryList<T>>.() -> Unit) {
    collapsedNodes = collapsedNodes.mutate(update)
    categoryRows.values.forEach { it.isExpanded = !collapsedNodes.contains(it.path) }
    updateComponents()
  }

  private fun CategoryList<T>.matches(value: T) = all { it.matches(value) }

  private fun invalidateRows() {
    rowComponents.forEach { it.invalidate() }
    revalidate()
  }

  fun isRowVisibleByKey(key: Any) = valueRows[key]?.isVisible ?: false

  fun setRowVisibleByKey(key: Any, visible: Boolean) {
    when {
      visible -> hiddenRows.remove(key)
      else -> hiddenRows.add(key)
    }
    val row = valueRows[key] ?: return
    row.isVisible = visible && collapsedNodes.none { it.matches(row.value) }
  }

  private fun Column.SizeConstraint.toSizeRequirements() = SizeRequirements(min, preferred, max, 0f)

  override fun getPreferredSize(): Dimension =
    Dimension(header.preferredSize.width, rowComponents.sumOf { it.preferredSize.height })

  // Just documenting that these are never called:
  override fun getMinimumSize(): Dimension =
    throw UnsupportedOperationException("Not needed in scroll panes")

  override fun getMaximumSize(): Dimension =
    throw UnsupportedOperationException("Not needed in scroll panes")

  /**
   * Rather than implementing the whole LayoutManager interface, we perform the layout in doLayout.
   * This avoids a lot of unnecessary boilerplate from LayoutManager, and is the approach used by
   * JTable.
   */
  override fun doLayout() {
    emptyStatePanel?.bounds = bounds
    updateHeaderColumnWidths()
    doTableLayout()
  }

  /** Recomputes column widths based on their width constraints. */
  private fun updateHeaderColumnWidths() {
    val sizeRequirements =
      header.columnModel.columnList
        .map { columns[it.modelIndex].widthConstraint.toSizeRequirements() }
        .toTypedArray()
    val offsets = IntArray(sizeRequirements.size)
    val spans = IntArray(sizeRequirements.size)

    // Subtract the indent from the available width to distribute to the columns, then allocate it
    // to the first column.
    val totalIndent = scaledCategoryIndent * groupByAttributes.size
    val valueWidth = width - totalIndent
    SizeRequirements.calculateTiledPositions(valueWidth, null, sizeRequirements, offsets, spans)

    for (i in spans.indices) {
      header.columnModel.getColumn(i).width =
        when (i) {
          0 -> spans[i] + totalIndent
          else -> spans[i]
        }
    }
  }

  private fun doTableLayout(): Int {
    var y = 0
    var indent = 0
    for (row in rowComponents) {
      if (row.isVisible) {
        when (row) {
          is CategoryRowComponent<*> -> {
            row.indent = (row.path.size - 1) * scaledCategoryIndent
            // Set the indent for any following ValueRows
            indent = row.path.size * scaledCategoryIndent
          }
          is ValueRowComponent<*> -> {
            row.indent = indent
          }
        }
        row.setBounds(0, y, parent.width, row.preferredSize.height)
        y += row.height
      }
    }
    return y
  }

  override fun getPreferredScrollableViewportSize() = preferredSize

  // TODO: refine this
  override fun getScrollableUnitIncrement(
    visibleRect: Rectangle?,
    orientation: Int,
    direction: Int,
  ) = JBUI.scale(16)

  // TODO: refine this
  override fun getScrollableBlockIncrement(
    visibleRect: Rectangle?,
    orientation: Int,
    direction: Int,
  ) = JBUI.scale(48)

  override fun getScrollableTracksViewportWidth() = true

  // If we're showing the emptyStatePanel, make it fill the viewport.
  override fun getScrollableTracksViewportHeight() = rowComponents.isEmpty()

  companion object {
    private val defaultCoroutineDispatcher =
      Executor { block -> SwingUtilities.invokeLater(block) }.asCoroutineDispatcher()

    private enum class TableActions {
      SELECT_NEXT_ROW,
      SELECT_PREVIOUS_ROW,
      TOGGLE_EXPAND_COLLAPSE,
      EXPAND,
      COLLAPSE,
      ;

      fun makeAction(action: (ActionEvent) -> Unit) =
        object : AbstractAction(this.name) {
          override fun actionPerformed(e: ActionEvent) {
            action(e)
          }
        }
    }

    private fun InputMap.put(keyStroke: String, action: TableActions) {
      put(KeyStroke.getKeyStroke(keyStroke), action.name)
    }

    private val CATEGORY_TABLE_INPUT_MAP =
      InputMap().apply {
        put("DOWN", TableActions.SELECT_NEXT_ROW)
        put("KP_DOWN", TableActions.SELECT_NEXT_ROW)
        put("UP", TableActions.SELECT_PREVIOUS_ROW)
        put("KP_UP", TableActions.SELECT_PREVIOUS_ROW)
        put("SPACE", TableActions.TOGGLE_EXPAND_COLLAPSE)
        put("ENTER", TableActions.TOGGLE_EXPAND_COLLAPSE)
        put("LEFT", TableActions.COLLAPSE)
        put("KP_LEFT", TableActions.COLLAPSE)
        put("RIGHT", TableActions.EXPAND)
        put("KP_RIGHT", TableActions.EXPAND)
      }

    val defaultColors =
      Colors(
        selectedForeground = JBUI.CurrentTheme.Table.foreground(true, true),
        selectedBackground = JBUI.CurrentTheme.Table.background(true, true),
        unselectedForeground = JBUI.CurrentTheme.Table.foreground(false, true),
        unselectedBackground = JBUI.CurrentTheme.Table.background(false, true),
      )
  }

  class Colors(
    val selectedForeground: Color,
    val selectedBackground: Color,
    val unselectedForeground: Color,
    val unselectedBackground: Color,
  )
}

interface CategoryRowMouseClickListener<T : Any> {
  fun categoryRowClicked(e: MouseEvent, table: CategoryTable<T>, path: CategoryList<T>)
}

/** Toggles expanding / collapsing the category when clicked. */
open class DefaultCategoryRowMouseClickListener<T : Any> : CategoryRowMouseClickListener<T> {
  override fun categoryRowClicked(e: MouseEvent, table: CategoryTable<T>, path: CategoryList<T>) {
    if (SwingUtilities.isLeftMouseButton(e)) {
      table.toggleCollapsed(path)
    }
  }
}

interface CategoryTableHeaderClickListener<T : Any> {
  fun columnHeaderClicked(e: MouseEvent, table: CategoryTable<T>, column: Column<T, *, *>)
}

/** Sorts the column when clicked. */
open class DefaultCategoryTableHeaderClickListener<T : Any> : CategoryTableHeaderClickListener<T> {
  override fun columnHeaderClicked(
    e: MouseEvent,
    table: CategoryTable<T>,
    column: Column<T, *, *>,
  ) {
    if (SwingUtilities.isLeftMouseButton(e)) {
      table.toggleSortOrder(column.attribute)
    }
  }
}

/**
 * Sorts a list of values: first, by each of the grouping attributes, then finally by the sorting
 * attributes. This puts all values of the same group together.
 */
internal fun <T> groupAndSort(
  values: List<T>,
  groupByAttributes: List<Attribute<T, *>>,
  attributeSorters: List<ColumnSortOrder<T>>,
): List<T> =
  (groupByAttributes.map { attribute ->
      val sortOrder =
        attributeSorters.find { it.attribute == attribute }?.sortOrder ?: SortOrder.ASCENDING
      checkNotNull(attribute.valueSorter(sortOrder)) { "Groupable attributes must be sortable" }
    } + attributeSorters.mapNotNull { it.attribute.valueSorter(it.sortOrder) })
    .reduceOrNull { a, b -> a.then(b) }
    ?.let { values.sortedWith(it) } ?: values
