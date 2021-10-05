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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.Range
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.TableCellRenderer

object TableUtils {
  inline fun<reified C: Enum<C>> JTable.setColumnRenderers(renderer: (C) -> TableCellRenderer) =
    enumValues<C>().forEach {
      columnModel.getColumn(it.ordinal).cellRenderer = renderer(it)
    }

  /**
   * Given a table whose rows have notions of "start" and "end", update the range on each row's selection.
   */
  fun<R> JTable.changeRangeOnSelection(model: PaginatedTableModel<R>,
                                       getRange: () -> Range, getStart: (R) -> Double, getEnd: (R) -> Double) {
    assert(this.model === model) { "Supplied model must be table's model" }
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    selectionModel.addListSelectionListener {
      if (selectedRow >= 0) {
        val selectedModelIndex = convertRowIndexToModel(selectedRow) + model.pageIndex * model.pageSize
        val p1 = model.rows[selectedModelIndex]
        getRange().set(getStart(p1), getEnd(p1))
      }
    }
  }

}