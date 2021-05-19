/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.adtui

import com.android.tools.adtui.model.AbstractPaginatedTableModel
import com.android.tools.adtui.stdui.CommonButton
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.RowSorterEvent
import javax.swing.table.TableRowSorter

/**
 * A view that consists of a paginated [JBTable] and pagination controls.
 *
 * @param pageSizeValues page size values to pre-populate in the dropdown. When empty, the dropdown will be hidden.
 * @property tableModel model to create the paginated table. When its initial page size is set to one of the pre-populated values, it will
 *                      be pre-selected in the dropdown.
 * @property table the underlying [JBTable]
 * @property component view component that wraps a table and the pagination controls.
 */
class PaginatedTableView(val tableModel: AbstractPaginatedTableModel, pageSizeValues: Array<Int> = emptyArray()) {
  val table: JBTable
  val component: JComponent

  @VisibleForTesting
  val firstPageButton = CommonButton(FIRST_PAGE_ICON)

  @VisibleForTesting
  val lastPageButton = CommonButton(LAST_PAGE_ICON)

  @VisibleForTesting
  val prevPageButton = CommonButton(PREV_PAGE_ICON)

  @VisibleForTesting
  val nextPageButton = CommonButton(NEXT_PAGE_ICON)

  @VisibleForTesting
  val pageInfoLabel = JLabel()

  @VisibleForTesting
  val pageSizeComboBox = ComboBox(pageSizeValues)

  init {
    table = JBTable(tableModel).apply {
      rowSorter = TableRowSorter(tableModel).apply {
        // By default, JTable only sorts the rows in view, which represents the current page of all our data.
        // So we need to listen to sort events and sort the entire data set instead.
        addRowSorterListener { event ->
          if (event.type == RowSorterEvent.Type.SORT_ORDER_CHANGED) {
            clearSelection()
            tableModel.sortData(event.source.sortKeys)
            allRowsChanged()
          }
        }
      }
    }
    tableModel.addTableModelListener { updateToolbar() }
    component = JPanel(BorderLayout()).apply {
      add(buildToolbar(), BorderLayout.NORTH)
      add(JBScrollPane(table), BorderLayout.CENTER)
    }
  }

  private fun updateToolbar() {
    // Labels
    val firstRowIndex = tableModel.pageIndex * tableModel.pageSize + 1
    val lastRowIndex = firstRowIndex + tableModel.rowCount - 1
    pageInfoLabel.text = "${firstRowIndex} - ${lastRowIndex} of ${tableModel.getDataSize()}"

    // Buttons
    firstPageButton.isEnabled = !tableModel.isOnFirstPage
    lastPageButton.isEnabled = !tableModel.isOnLastPage
    prevPageButton.isEnabled = !tableModel.isOnFirstPage
    nextPageButton.isEnabled = !tableModel.isOnLastPage
  }

  private fun buildToolbar(): JComponent {
    // Page navigation controls
    firstPageButton.apply {
      disabledIcon = IconLoader.getDisabledIcon(FIRST_PAGE_ICON)
      toolTipText = "Go to first page"
      addActionListener {
        tableModel.goToFirstPage()
      }
    }
    lastPageButton.apply {
      disabledIcon = IconLoader.getDisabledIcon(LAST_PAGE_ICON)
      toolTipText = "Go to last page"
      addActionListener {
        tableModel.goToLastPage()
      }
    }
    prevPageButton.apply {
      disabledIcon = IconLoader.getDisabledIcon(PREV_PAGE_ICON)
      toolTipText = "Go to previous page"
      addActionListener {
        tableModel.goToPrevPage()
      }
    }
    nextPageButton.apply {
      disabledIcon = IconLoader.getDisabledIcon(NEXT_PAGE_ICON)
      toolTipText = "Go to next page"
      addActionListener {
        tableModel.goToNextPage()
      }
    }
    pageSizeComboBox.apply {
      isVisible = itemCount > 0
      selectedItem = tableModel.pageSize
      addActionListener {
        tableModel.updatePageSize(item)
      }
    }
    val toolbar = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.emptyLeft(8)
      add(pageInfoLabel, BorderLayout.LINE_START)
      add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(firstPageButton)
        add(prevPageButton)
        add(pageSizeComboBox)
        add(nextPageButton)
        add(lastPageButton)
      }, BorderLayout.LINE_END)
    }
    updateToolbar()

    return toolbar
  }

  private companion object {
    private val FIRST_PAGE_ICON: Icon = StudioIcons.LayoutEditor.Motion.GO_TO_START
    private val LAST_PAGE_ICON: Icon = StudioIcons.LayoutEditor.Motion.GO_TO_END
    private val PREV_PAGE_ICON: Icon = StudioIcons.LayoutEditor.Motion.PREVIOUS_TICK
    private val NEXT_PAGE_ICON: Icon = StudioIcons.LayoutEditor.Motion.NEXT_TICK
  }
}