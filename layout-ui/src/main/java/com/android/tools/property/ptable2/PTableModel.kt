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
package com.android.tools.property.ptable2

/**
 * A model for a [PTable].
 */
interface PTableModel {

  /**
   * The items in the table.
   */
  val items: List<PTableItem>

  /**
   * The item currently being edited.
   */
  var editedItem: PTableItem?

  /**
   * Returns true if an item [PTableColumn.NAME] or an item [PTableColumn.VALUE] is editable.
   */
  fun isCellEditable(item: PTableItem, column: PTableColumn): Boolean = false

  /**
   * Returns true if it is acceptable to use the default implementation for
   * moving to the next editor after being done editing the [column] of the specified [item].
   * A return value of false will leave no active editors unless this is handled by other means.
   */
  fun acceptMoveToNextEditor(item: PTableItem, column: PTableColumn): Boolean = true

  /**
   * Add an update listener.
   *
   * A model should notify all its listeners if the [items] have changed.
   */
  fun addListener(listener: PTableModelUpdateListener) {}

  /**
   * Add an item to this model.
   *
   * A model should notify all its listener if the [items] have changed.
   * The added item is returned. If a similar item already exists a model
   * may return that instance instead.
   */
  fun addItem(item: PTableItem): PTableItem

  /**
   * Remove an item from this model.
   *
   * A model should notify all its listener if the [items] have changed.
   */
  fun removeItem(item: PTableItem)

  /**
   * Refresh the table contents after a property value change.
   */
  fun refresh() {}
}

/**
 * Listener interface for model changes.
 */
interface PTableModelUpdateListener {
  /**
   * Notifies a listener that the items in the model were changed.
   *
   * The [modelChanged] parameter indicates if the items in the model were changed.
   * If the items were not changed, then a repaint of the table is requested.
   * After the update [nextEditedItem] should be edited if anything was being edited
   * before the update.
   */
  fun itemsUpdated(modelChanged: Boolean, nextEditedItem: PTableItem?)
}
