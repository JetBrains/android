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
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.colorpicker.CommonButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.RowSorterEvent
import javax.swing.table.TableRowSorter

/**
 * A view that consists of a paginated [JBTable] and pagination controls.
 *
 * @param tableModel model to create the paginated table
 * @property table the underlying [JBTable]
 * @property component view component that wraps a table and the pagination controls.
 */
class PaginatedTableView(val tableModel: AbstractPaginatedTableModel) {
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

  init {
    table = JBTable(tableModel).apply {
      rowSorter = TableRowSorter(tableModel).apply {
        // By default, JTable only sorts the rows in view, which represents the current page of all our data.
        // So we need to listen to sort events and sort the entire data set instead.
        addRowSorterListener { event ->
          if (event.type == RowSorterEvent.Type.SORT_ORDER_CHANGED) {
            tableModel.sortData(event.source.sortKeys)
            allRowsChanged()
          }
        }
      }
    }
    component = JPanel(BorderLayout()).apply {
      add(buildPageControlPanel(), BorderLayout.NORTH)
      add(JBScrollPane(table), BorderLayout.CENTER)
    }
  }

  private fun updateButtonStates() {
    firstPageButton.isEnabled = !tableModel.isOnFirstPage
    lastPageButton.isEnabled = !tableModel.isOnLastPage
    prevPageButton.isEnabled = !tableModel.isOnFirstPage
    nextPageButton.isEnabled = !tableModel.isOnLastPage
  }

  private fun buildPageControlPanel(): JComponent {
    // Page navigation controls
    firstPageButton.apply {
      disabledIcon = IconLoader.getDisabledIcon(FIRST_PAGE_ICON)
      toolTipText = "Go to first page"
      addActionListener {
        tableModel.goToFirstPage()
        updateButtonStates()
      }
    }
    lastPageButton.apply {
      disabledIcon = IconLoader.getDisabledIcon(LAST_PAGE_ICON)
      toolTipText = "Go to last page"
      addActionListener {
        tableModel.goToLastPage()
        updateButtonStates()
      }
    }
    prevPageButton.apply {
      disabledIcon = IconLoader.getDisabledIcon(PREV_PAGE_ICON)
      toolTipText = "Go to previous page"
      addActionListener {
        tableModel.goToPrevPage()
        updateButtonStates()
      }
    }
    nextPageButton.apply {
      disabledIcon = IconLoader.getDisabledIcon(NEXT_PAGE_ICON)
      toolTipText = "Go to next page"
      addActionListener {
        tableModel.goToNextPage()
        updateButtonStates()
      }
    }
    val pagingControl = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
      add(firstPageButton)
      add(prevPageButton)
      add(nextPageButton)
      add(lastPageButton)
    }
    updateButtonStates()

    return pagingControl
  }

  private companion object {
    val FIRST_PAGE_ICON: Icon = StudioIcons.LayoutEditor.Motion.GO_TO_START
    val LAST_PAGE_ICON: Icon = StudioIcons.LayoutEditor.Motion.GO_TO_END
    val PREV_PAGE_ICON: Icon = StudioIcons.LayoutEditor.Motion.PREVIOUS_TICK
    val NEXT_PAGE_ICON: Icon = StudioIcons.LayoutEditor.Motion.NEXT_TICK
  }
}