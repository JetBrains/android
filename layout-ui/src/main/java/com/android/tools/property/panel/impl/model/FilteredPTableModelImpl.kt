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
package com.android.tools.property.panel.impl.model

import com.android.tools.property.panel.api.FilteredPTableModel
import com.android.tools.property.panel.api.FlagPropertyItem
import com.android.tools.property.panel.api.GroupSpec
import com.android.tools.property.panel.api.NewPropertyItem
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.ptable.PTableColumn
import com.android.tools.property.ptable.PTableGroupItem
import com.android.tools.property.ptable.PTableItem
import com.android.tools.property.ptable.PTableModel
import com.android.tools.property.ptable.PTableModelUpdateListener

/**
 * Implementation of [FilteredPTableModel].
 *
 * A [PTableModel] implementation created for handling variable length tables in the property
 * editor.
 *
 * The items are populated from the properties in [model] that satisfies [itemFilter]. If
 * [keepNewAfterFlyAway] is true then the [refresh] method will keep a [NewPropertyItem] at the end
 * of the table when the previous new item was entered such that it appears in this table.
 */
class FilteredPTableModelImpl<P : PropertyItem>(
  private val valueType: Class<P>,
  private val model: PropertiesModel<P>,
  private val itemFilter: (P) -> Boolean,
  private val insertOperation: ((String, String) -> P?)?,
  private val deleteOperation: ((P) -> Unit)?,
  private val itemComparator: Comparator<PTableItem>,
  private val groups: List<GroupSpec<P>>,
  private val keepNewAfterFlyAway: Boolean,
  private val allowEditing: Boolean,
  private val valueEditable: (P) -> Boolean,
  private val hasCustomCursor: (P) -> Boolean,
) : FilteredPTableModel<P>, PTableModel {

  private val listeners = mutableListOf<PTableModelUpdateListener>()

  /** The items in this table model */
  override val items = mutableListOf<PTableItem>()

  /**
   * The item that is currently being edited in the table. The table implementation must provide
   * this value.
   */
  override var editedItem: PTableItem? = null

  init {
    groupAndSort(findParticipatingItems(), items)
  }

  override fun supportsInsertableItems() = insertOperation != null

  override fun supportsRemovableItems() = deleteOperation != null

  override fun addNewItem(item: P): P {
    if (items.contains(item)) {
      return item
    }
    val newItems = ArrayList(items)
    if (item is NewPropertyItem) {
      newItems.add(item)
      editedItem = item
    } else {
      val index =
        newItems.indexOfFirst { it is NewPropertyItem || itemComparator.compare(it, item) >= 0 }
      if (index < 0) {
        newItems.add(item)
      } else {
        newItems.add(index, item)
      }
    }
    updateItems(newItems, lastItem(items))
    return item
  }

  override fun addItem(name: String, value: String): PTableItem? {
    val insert = insertOperation ?: return null
    val item = insert(name, value) ?: return null
    return addItem(item)
  }

  override fun addItem(item: PTableItem): PTableItem {
    @Suppress("UNCHECKED_CAST") return addNewItem(item as P)
  }

  override fun removeItem(item: PTableItem) {
    val delete = deleteOperation ?: return
    val newItems = ArrayList(items)
    if (!newItems.remove(item)) {
      return
    }
    @Suppress("UNCHECKED_CAST") delete(item as P)
    updateItems(newItems, null)
  }

  @Suppress("UNCHECKED_CAST")
  override fun isCellEditable(item: PTableItem, column: PTableColumn): Boolean =
    allowEditing &&
      when (item) {
        is NewPropertyItem -> column == PTableColumn.NAME || item.delegate != null
        is PTableGroupItem -> true
        else -> column == PTableColumn.VALUE && valueEditable(item as P)
      }

  @Suppress("UNCHECKED_CAST")
  override fun hasCustomCursor(item: PTableItem, column: PTableColumn): Boolean =
    (column == PTableColumn.VALUE) && valueType.isInstance(item) && hasCustomCursor(item as P)

  override fun acceptMoveToNextEditor(item: PTableItem, column: PTableColumn): Boolean {
    // Accept any move to the next editor unless we know that that the current row
    // is going to fly away soon. That is the case if the user just entered a value
    // for a new property.
    return !(item is NewPropertyItem && column == PTableColumn.VALUE && item.delegate != null)
  }

  /**
   * Refresh the items in the table.
   *
   * This [refresh] method should be called when a change to the property values in the model is
   * known to have changed. Since a property value change may affect which items should appear in
   * the table, we recompute the wanted items and ask the table to update.
   *
   * There is special logic in this method for handling a [NewPropertyItem] at the end of the table.
   */
  override fun refresh() {
    val last = lastItem(items)
    val newItems = mutableListOf<PTableItem>()
    groupAndSort(findParticipatingItems(), newItems)
    if (last != null) {
      val existing = model.properties.getOrNull(last.namespace, last.name)
      if (existing == null || !itemFilter(existing)) {
        newItems.add(last)
      } else if (keepNewAfterFlyAway) {
        last.name = ""
        newItems.add(last)
      }
    }
    updateItems(newItems, last)
  }

  /**
   * Update the items in the table.
   *
   * If the existing [items] are the same as the [newItems] this is a noop. Otherwise replace the
   * content of [items] and notify the table.
   *
   * If [newItem] is specified then a [NewPropertyItem] was found at the end of the table before
   * this change. Use this for computing which item the table should be editing after this
   * operation.
   */
  private fun updateItems(newItems: List<PTableItem>, newItem: PTableItem?): PTableItem? {
    var nextItemToEdit = editedItem
    val modelChanged = items != newItems
    if (modelChanged) {
      nextItemToEdit = findNextItemToEdit(newItems, newItem)
      items.clear()
      items.addAll(newItems)
    }
    listeners.toTypedArray().forEach { it.itemsUpdated(modelChanged, nextItemToEdit) }
    return nextItemToEdit
  }

  override fun addListener(listener: PTableModelUpdateListener) {
    listeners.add(listener)
  }

  /**
   * Return the last property item if it is a NeleNewPropertyItem.
   *
   * Skip flag items if the property is currently expanded in the table.
   */
  private fun lastItem(itemList: List<PTableItem>): NewPropertyItem? {
    var last = itemList.lastOrNull() ?: return null
    if (last is FlagPropertyItem) {
      val flags = last.flags.children.size
      if (itemList.size - flags >= 1) {
        last = itemList[itemList.size - flags - 1]
      }
    }
    return last as? NewPropertyItem
  }

  private fun findParticipatingItems(): List<P> {
    return model.properties.values.filter(itemFilter)
  }

  /**
   * During a refresh: compute the next item to edit
   *
   * Note that the current edited item may have flown away to another place in the table.
   */
  private fun findNextItemToEdit(newItems: List<PTableItem>, newItem: PTableItem?): PTableItem? {
    val itemToFind = editedItem ?: return null // Nothing was being edited, continue without editing
    if (itemToFind == newItem && newItems.lastOrNull() == newItem) {
      // The new property at the end of the model was being edited.
      // Continue to edit the new value.
      return newItem
    }
    val index = newItems.indexOf(itemToFind)
    if (index >= 0) {
      // The value being edited is still in the new items.
      // Continue to edit this property.
      return itemToFind
    }
    if (itemToFind is FlagPropertyItem) {
      // The value being edited is a flag item (one of may sub items of a FlagsPropertyItem).
      // If the FlagsPropertyItem is in the new items continue editing the flag item.
      val flags: Any = itemToFind.flags
      if (items.contains(flags)) {
        return itemToFind
      }
    }
    // The value doesn't appear to be around anymore.
    // Attempt to locate the next item in the current list of items.
    val prevIndex = items.indexOf(itemToFind)
    if (prevIndex < 0) {
      return null
    }
    if (items.size > prevIndex + 1) {
      val nextItem = items[prevIndex + 1]
      if (newItems.contains(nextItem)) {
        return nextItem
      }
    }
    return null
  }

  private fun group(list: List<P>, output: MutableList<PTableItem>) {
    if (groups.isEmpty()) {
      output.addAll(list)
      return
    }

    val temp1 = mutableListOf<P>()
    val temp2 = mutableListOf<P>()
    var input = list
    var temp = temp1

    for (group in groups) {
      val groupItem = TableGroupItem(group)
      for (item in input) {
        if (group.itemFilter(item)) {
          groupItem.children.add(item)
        } else {
          temp.add(item)
        }
      }
      if (groupItem.children.isNotEmpty()) {
        groupItem.children.sortWith(group.comparator)
        output.add(groupItem)
        input = temp
        temp = if (input == temp1) temp2 else temp1
      }
      temp.clear()
    }
    output.addAll(input)
  }

  private fun sort(list: MutableList<PTableItem>) {
    val lastItems = removeLastItems(list)
    list.sortWith(itemComparator)
    list.addAll(lastItems)
  }

  private fun removeLastItems(list: MutableList<PTableItem>): List<PTableItem> {
    val last = list.lastOrNull() ?: return emptyList()
    val lastNewItem = lastItem(list) ?: return emptyList()
    if (last == lastNewItem) {
      list.removeAt(list.size - 1)
      return listOf(last)
    }
    val index = list.lastIndexOf(lastNewItem)
    val toRemove = list.subList(index, list.size)
    val lastItems = toRemove.toList()
    toRemove.clear()
    return lastItems
  }

  private fun groupAndSort(list: List<P>, output: MutableList<PTableItem>) {
    group(list, output)
    sort(output)
  }
}
