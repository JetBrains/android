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

import com.android.tools.property.panel.impl.model.FilteredPTableModelImpl
import com.android.tools.property.ptable.PTableItem
import com.android.tools.property.ptable.PTableModel

/**
 * A [PTableModel] for variable length property tables.
 *
 * There are methods for adding and removing items from the table.
 */
interface FilteredPTableModel<P : PropertyItem> : PTableModel {

  /**
   * Adds a [PropertyItem] to the table model.
   *
   * The added item will be placed in its natural order among the existing items. If the added item
   * implements [NewPropertyItem] it will be placed at the bottom the table. Adding an already
   * existing item is a noop.
   *
   * The returned item is the current item after the operation.
   */
  fun addNewItem(item: P): P

  companion object PTableModelFactory {

    /** Comparator that is sorting [PTableItem] in alphabetical sorting order. */
    val alphabeticalSortOrder: Comparator<PTableItem> = Comparator.comparing(PTableItem::name)
  }
}

/**
 * Create an implementation of [FilteredPTableModel].
 *
 * The [model] specifies where the items are retrieved from. Only the items satisfying the
 * [itemFilter] are included in the table.
 *
 * The [refresh] method will repopulate the table with items from the available properties from the
 * [model] applying the [itemFilter]. The [deleteOperation] is applied when [removeItem] is called.
 * If the model includes an item implementing [NewPropertyItem] that item will be excluded if a
 * corresponding matching item is found in the [model] except if [keepNewAfterFlyAway] is true, then
 * the item will be included at the end of the table after setting its name to null i.e. the new
 * item line will be ready for the user to add another item to the table. Use [allowEditing] to turn
 * off editing completely in all cells. Use [valueEditable] to turn off editing for some values.
 * Group item and items implementing [NewPropertyItem] will be editable regardless. The [groups]
 * specifies which item are grouped under a specified group name. The items are sorted using
 * [itemComparator].
 */
inline fun <reified P : PropertyItem> FilteredPTableModel(
  model: PropertiesModel<P>,
  noinline itemFilter: (P) -> Boolean,
  noinline insertOperation: ((String, String) -> P?)? = null,
  noinline deleteOperation: ((P) -> Unit)? = null,
  itemComparator: Comparator<PTableItem> = FilteredPTableModel.alphabeticalSortOrder,
  groups: List<GroupSpec<P>> = emptyList(),
  keepNewAfterFlyAway: Boolean = true,
  allowEditing: Boolean = true,
  noinline valueEditable: (P) -> Boolean = { true },
  noinline hasCustomCursor: (P) -> Boolean = { false },
): FilteredPTableModel<P> {
  return FilteredPTableModelImpl(
    P::class.java,
    model,
    itemFilter,
    insertOperation,
    deleteOperation,
    itemComparator,
    groups,
    keepNewAfterFlyAway,
    allowEditing,
    valueEditable,
    hasCustomCursor,
  )
}
