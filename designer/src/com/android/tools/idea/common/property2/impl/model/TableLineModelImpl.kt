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
package com.android.tools.idea.common.property2.impl.model

import com.android.tools.adtui.ptable2.PTableItem
import com.android.tools.adtui.ptable2.PTableModel
import com.android.tools.idea.common.property2.api.TableLineModel
import kotlin.properties.Delegates

/**
 * A model for table embedded in the properties panel.
 */
class TableLineModelImpl(override val tableModel: PTableModel,
                         override val isSearchable: Boolean) : GenericInspectorLineModel(), TableLineModel {

  override var filter by Delegates.observable("") { _, _, _ -> fireValueChanged()}

  override val focusable: Boolean
    get() = true

  override var selectedItem: PTableItem? = null

  /** Flag for the UI to update the edited row */
  var updateEditing = false
    private set

  /** Indicates to the UI which row to edit (-1 means stop editing) when updateEditing is true */
  var rowToEdit = -1
    private set

  override fun requestFocus() {
    editingRequest(tableModel.items.firstOrNull())
  }

  override fun requestFocus(item: PTableItem) {
    editingRequest(item)
  }

  override fun stopEditing() {
    editingRequest(null)
  }

  private fun editingRequest(item: PTableItem?) {
    updateEditing = true
    rowToEdit = if (item != null) tableModel.items.indexOf(item) else -1
    fireValueChanged()
    updateEditing = false
    rowToEdit = -1
  }
}
