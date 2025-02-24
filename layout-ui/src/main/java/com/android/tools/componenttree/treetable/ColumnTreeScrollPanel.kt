/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.adtui.RangeScrollBarUI
import com.android.tools.adtui.TabularLayout
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.treeStructure.treetable.TreeTableTree
import java.awt.Dimension
import javax.swing.BoundedRangeModel
import javax.swing.JPanel
import javax.swing.JTable

/**
 * JPanel responsible for creating and managing the horizontal scroll bar for the component tree.
 */
class ColumnTreeScrollPanel(private val tree: TreeTableTree, private val table: TreeTableImpl) :
  JPanel(TabularLayout("Fit-,*")) {

  private val scrollbar = ColumnTreeScrollBar(table)

  init {
    add(scrollbar, TabularLayout.Constraint(0, 0))
    add(JPanel(), TabularLayout.Constraint(0, 1))
  }

  fun getModel(): BoundedRangeModel = scrollbar.model

  fun getUnitIncrement() = scrollbar.getUnitIncrement()

  fun updateScrollBar(maxValue: Int) {
    // Min is always 0
    val max = maxValue
    var value = tree.treeOffset
    val extent = getTreeColumnWidth(table)
    // Adjust the values so always value+extent <= max which avoids having space to show
    // the tree but still having part of the tree outside the view.
    if (value + extent > max) {
      value = max - extent
    }
    scrollbar.model.maximum = max
    scrollbar.model.value = value
    scrollbar.model.extent = extent
    scrollbar.revalidate()
    isVisible = extent < max
  }

  private fun getTreeColumnWidth(table: JTable?): Int {
    if (table == null || table.columnModel.columnCount == 0) {
      return 0
    }
    return table.columnModel.getColumn(0).width
  }

  private inner class ColumnTreeScrollBar(private val table: JTable) : JBScrollBar(HORIZONTAL) {

    init {
      updateUI()
      // Set the Horizontal scroll increment unit to 10 for a smoother scrolling.
      unitIncrement = 10
    }

    override fun updateUI() {
      setUI(RangeScrollBarUI())
    }

    override fun getMinimumSize(): Dimension {
      val dim = super.getMinimumSize()
      return Dimension(getTreeColumnWidth(this@ColumnTreeScrollBar.table), dim.height)
    }

    private fun getTreeColumnWidth(table: JTable): Int {
      return if (table.getColumnModel().columnCount == 0) {
        0
      } else {
        table.getColumnModel().getColumn(0).getWidth()
      }
    }
  }
}
