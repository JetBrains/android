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
package com.android.tools.property.panel.api

import com.android.tools.property.ptable2.PTableItem
import com.android.tools.property.ptable2.PTableModel

/**
 * The model of a table in an inspector.
 */
interface TableLineModel: InspectorLineModel {

  /**
   * The table model for the table embedded in this inspector line.
   */
  val tableModel: PTableModel

  /**
   * The item currently selected in the table.
   */
  val selectedItem: PTableItem?

  /**
   * Returns the visible item count taking filtering into account.
   */
  val itemCount: Int

  /**
   * Request focus in a specified item.
   */
  fun requestFocus(item: PTableItem)

  /**
   * Request focus in item with best filter match.
   */
  fun requestFocusInBestMatch()

  /**
   * Add an item to the table.
   *
   * The added item will be placed in its natural order among the
   * existing items. If the added item implements [NewPropertyItem]
   * it will be placed at the bottom the table.
   * Adding an already existing item is a noop and will return the
   * instance of the existing item in the table.
   *
   * The returned item is the current item after the operation.
   */
  fun addItem(item: PTableItem): PTableItem {
    return tableModel.addItem(item)
  }

  /**
   * Remove the currently selected item and select the next item in the table.
   */
  fun removeItem(item: PTableItem) {
    tableModel.removeItem(item)
  }

  /**
   * Stop editing the current cell.
   */
  fun stopEditing()
}
