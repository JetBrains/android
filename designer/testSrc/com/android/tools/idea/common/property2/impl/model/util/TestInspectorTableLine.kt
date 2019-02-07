/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.common.property2.impl.model.util

import com.android.tools.adtui.ptable2.PTableItem
import com.android.tools.adtui.ptable2.PTableModel
import com.android.tools.idea.common.property2.api.TableLineModel

class TestTableLineModel(
  override val tableModel: PTableModel,
  override val isSearchable: Boolean
) : TestInspectorLineModel(TestLineType.TABLE), TableLineModel {

  override var selectedItem: PTableItem? = null

  override val itemCount = tableModel.items.size

  override var filter: String = ""

  override fun requestFocus(item: PTableItem) {
    selectedItem = item
  }

  override fun requestFocusInBestMatch() {
  }

  override fun stopEditing() {
    selectedItem = null
  }

  override fun refresh() {
    tableModel.refresh()
  }
}

