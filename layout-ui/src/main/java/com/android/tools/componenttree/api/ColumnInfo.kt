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
package com.android.tools.componenttree.api

import java.awt.Color
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * The default border for each column is 2 scaled pixels on left and right side of the column data.
 */
internal val DEFAULT_INSETS = Insets(0, 2, 0, 2)

/**
 * An extra column in the component tree.
 *
 * The column will appear to the right of the tree outline and to the left of the badges.
 */
interface ColumnInfo {
  /**
   * The [name] of the column.
   *
   * Reserved for use in the table header.
   */
  val name: String

  /**
   * The constant [width] of this column.
   *
   * If the column has dynamic width return this should return -1.
   */
  val width: Int

  /**
   * The column renderer.
   */
  val renderer: TableCellRenderer?

  /**
   * The header renderer.
   */
  val headerRenderer: TableCellRenderer?
    get() = null

  /**
   * Return the tooltip text for the column of the specified [item].
   */
  fun getTooltipText(item: Any): String? = null

  /**
   * Display a divider on the left.
   */
  val leftDivider: Boolean
    get() = false

  /**
   * Foreground text color.
   */
  val foreground: Color?
    get() = null

  /**
   * Border sizes.
   */
  val insets: Insets
    get() = DEFAULT_INSETS

  /**
   * Perform this action when a cell in this column is clicked on.
   * @param item the item that the cell belongs to
   * @param component the Swing component presenting the icon
   * @param bounds the bounds of the cell relative to [component]
   */
  fun performAction(item: Any, component: JComponent, bounds: Rectangle) {}

  /**
   * Show an (optional) popup after a right click on a cell in this column.
   */
  fun showPopup(item: Any, component: JComponent, x: Int, y: Int) {}

  /**
   * Compute the max width of the column based on all possible items.
   */
  fun computeWidth(table: JTable, data: Sequence<*>): Int = width

  /**
   * Invalidate the renderer after a look and feel (or size) change.
   */
  fun updateUI() {}
}
