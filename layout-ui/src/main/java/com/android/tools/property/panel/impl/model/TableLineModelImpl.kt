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

import com.android.tools.property.ptable2.PTableItem
import com.android.tools.property.ptable2.PTableModel
import com.android.tools.property.panel.api.TableLineModel
import kotlin.properties.Delegates

/**
 * A model for table embedded in the properties panel.
 */
class TableLineModelImpl(override val tableModel: PTableModel,
                         override val isSearchable: Boolean) : GenericInspectorLineModel(), TableLineModel {

  override var filter by Delegates.observable("") { _, _, _ -> fireValueChanged()}

  override val focusable: Boolean
    get() = true

  /** Updated by UI implementation */
  override var selectedItem: PTableItem? = null

  /** Updated by UI implementation */
  override var itemCount = 0

  override fun requestFocus() {
    val item = tableModel.items.firstOrNull() ?: return
    fireEditRequest(TableEditingRequest.SPECIFIED_ITEM, item)
  }

  override fun requestFocus(item: PTableItem) {
    fireEditRequest(TableEditingRequest.SPECIFIED_ITEM, item)
  }

  override fun requestFocusInBestMatch() {
    fireEditRequest(TableEditingRequest.BEST_MATCH)
  }

  override fun stopEditing() {
    fireEditRequest(TableEditingRequest.STOP_EDITING)
  }

  override fun removeItem(item: PTableItem) {
    val next = nextSelectedItem(item)
    tableModel.removeItem(item)
    fireEditRequest(TableEditingRequest.SELECT, next)
  }

  override fun refresh() {
    tableModel.refresh()
  }

  private fun nextSelectedItem(item: PTableItem): PTableItem? {
    val index: Int = tableModel.items.indexOf(item)
    val next: Int = when {
      index + 1 < tableModel.items.size -> index + 1
      index > 0 -> index - 1
      else -> return null
    }
    return tableModel.items[next]
  }

  private fun fireEditRequest(request: TableEditingRequest, item: PTableItem? = null) {
    listeners.toTypedArray().filterIsInstance<TableRowEditListener>().forEach { it.editRequest(request, item) }
  }
}
