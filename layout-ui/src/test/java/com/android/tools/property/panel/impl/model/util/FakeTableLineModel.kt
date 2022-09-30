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
package com.android.tools.property.panel.impl.model.util

import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.ptable.PTableItem
import com.android.tools.property.ptable.PTableModel
import com.android.tools.property.panel.api.TableLineModel
import com.android.tools.property.panel.api.TableUIProvider
import com.google.common.truth.Truth
import kotlin.properties.Delegates

class FakeTableLineModel(
  override val tableModel: PTableModel,
  val tableUI: TableUIProvider,
  override val isSearchable: Boolean
) : FakeInspectorLineModel(FakeLineType.TABLE), TableLineModel {

  override var selectedItem: PTableItem? = null

  override val itemCount = tableModel.items.size

  override var filter by Delegates.observable("") { _, _, _ -> fireValueChanged() }

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

  fun checkItemCount(rows: Int) {
    Truth.assertThat(rows).isEqualTo(tableModel.items.size)
  }

  fun checkItem(row: Int, namespace: String, attribute: String) {
    Truth.assertThat(row).isLessThan(tableModel.items.size)
    val item = tableModel.items[row] as PropertyItem
    Truth.assertThat(item.namespace).isEqualTo(namespace)
    Truth.assertThat(item.name).isEqualTo(attribute)
  }
}

