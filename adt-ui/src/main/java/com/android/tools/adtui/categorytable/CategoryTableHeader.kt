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

import com.intellij.ide.ui.laf.darcula.DarculaTableHeaderUI
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.SortOrder
import javax.swing.UIManager
import javax.swing.plaf.TableHeaderUI
import javax.swing.plaf.basic.BasicTableHeaderUI
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.JTableHeader
import javax.swing.table.TableColumn

/** A JTableHeader adapted to integrate with CategoryTable. */
class CategoryTableHeader(
  private val model: ColumnList<*>,
  private val primarySortSupplier: () -> ColumnSortOrder<*>?,
  private val mouseClickHandler: (MouseEvent) -> Unit
) : JTableHeader() {
  init {
    defaultRenderer = CategoryTableCellHeaderRenderer()

    for (i in 0 until model.size) {
      columnModel.addColumn(
        TableColumn(i).also {
          it.identifier = model[i].attribute
          it.headerValue = model[i].name
        }
      )
    }
  }

  /**
   * Immutable list of TableColumns corresponding to the model.columns. The headerColumnModel's
   * columns are removed when we group; we keep a copy here so we can ungroup.
   */
  val tableColumns = columnModel.columnList.toList()

  fun removeColumn(attribute: Attribute<*, *>) {
    columnModel.columnList.find { it.identifier == attribute }?.let { columnModel.removeColumn(it) }
  }

  fun restoreColumn(attribute: Attribute<*, *>) {
    // We want to put the column back where it was, essentially. However, the user could have
    // moved around the columns in the meantime (if reordering is enabled), which makes it hard to
    // define where it should go.
    tableColumns
      .find { it.identifier == attribute }
      ?.let {
        val originalIndex = it.modelIndex
        val lastIndex = columnModel.columnCount
        val firstGreaterIndex =
          columnModel.columnList.indexOfFirst { it.modelIndex > originalIndex }

        columnModel.addColumn(it)
        if (firstGreaterIndex >= 0) {
          columnModel.moveColumn(lastIndex, firstGreaterIndex)
        }
      }
  }

  fun viewIndexToModelIndex(viewIndex: Int) = columnModel.getColumn(viewIndex).modelIndex

  override fun setUI(ui: TableHeaderUI?) {
    super.setUI(
      when (ui) {
        // We have to use a lambda here rather than passing mouseClickHandler directly, because
        // setUI is called from the JTableHeader constructor, before our constructor has initialized
        // the fields of this class.
        is DarculaTableHeaderUI -> DarculaCategoryTableHeaderUI { e -> mouseClickHandler(e) }
        is BasicTableHeaderUI -> BasicCategoryTableHeaderUI { e -> mouseClickHandler(e) }
        else -> ui
      }
    )
  }

  inner class CategoryTableCellHeaderRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
      table: JTable?,
      value: Any?,
      isSelected: Boolean,
      hasFocus: Boolean,
      row: Int,
      columnIndex: Int
    ): Component {
      text = value?.toString() ?: ""

      val column = model[viewIndexToModelIndex(columnIndex)]
      val primarySort = primarySortSupplier()
      val sortOrder =
        primarySort?.takeIf { it.attribute == column.attribute }?.sortOrder ?: SortOrder.UNSORTED
      icon = sortIcons[sortOrder]
      return this
    }
  }

  companion object {
    private val sortIcons =
      mapOf(
        SortOrder.ASCENDING to UIManager.get("Table.ascendingSortIcon", null) as Icon,
        SortOrder.DESCENDING to UIManager.get("Table.descendingSortIcon", null) as Icon,
        SortOrder.UNSORTED to UIManager.get("Table.naturalSortIcon", null) as? Icon
      )
  }
}

/**
 * Wrapper around DarculaTableHeaderUI to enable sorting by clicking on column headers.
 *
 * BasicTableHeaderUI.MouseInputHandler invokes JTable methods to implement sorting; however, since
 * we are not a JTable, it doesn't do anything.
 */
class DarculaCategoryTableHeaderUI(val mouseClickedHandler: (MouseEvent) -> Unit) :
  DarculaTableHeaderUI() {
  inner class MouseInputHandler : BasicTableHeaderUI.MouseInputHandler() {
    override fun mouseClicked(e: MouseEvent) {
      mouseClickedHandler(e)
    }
  }

  override fun createMouseInputListener() = MouseInputHandler()
}

class BasicCategoryTableHeaderUI(val mouseClickedHandler: (MouseEvent) -> Unit) :
  BasicTableHeaderUI() {
  inner class MouseInputHandler : BasicTableHeaderUI.MouseInputHandler() {
    override fun mouseClicked(e: MouseEvent) {
      mouseClickedHandler(e)
    }
  }

  override fun createMouseInputListener() = MouseInputHandler()
}
