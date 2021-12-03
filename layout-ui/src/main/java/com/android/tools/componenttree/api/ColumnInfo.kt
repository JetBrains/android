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

import javax.swing.JTable
import javax.swing.table.TableCellRenderer

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
  val renderer: TableCellRenderer

  /**
   * Compute the max width of the column based on all possible items.
   */
  fun computeWidth(table: JTable, data: Sequence<*>): Int

  /**
   * Invalidate the renderer after a look and feel (or size) change.
   */
  fun updateUI() {}
}
