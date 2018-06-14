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
package com.android.tools.adtui.ptable2.item

import com.android.tools.adtui.ptable2.PTableColumn
import com.android.tools.adtui.ptable2.PTableGroupItem
import com.android.tools.adtui.ptable2.PTableItem
import com.android.tools.adtui.ptable2.PTableModel
import com.android.tools.adtui.ptable2.impl.PTableModelImpl
import org.mockito.Mockito
import javax.swing.event.TableModelListener

fun createModel(vararg items: PTableItem): PTableModel {
  return PTableTestModel(*items)
}

fun addModelListener(model: PTableModelImpl): TableModelListener {
  val listener = Mockito.mock<TableModelListener>(TableModelListener::class.java)
  model.addTableModelListener(listener)
  return listener
}

class Item(override val name: String, override val value: String? = null ) : PTableItem

class Group(override val name: String, vararg childItems: PTableItem) : PTableGroupItem {
  override val value: String? = null
  override val children: List<PTableItem> = listOf(*childItems)
}

private class PTableTestModel(vararg items: PTableItem) : PTableModel {
  override val items: List<PTableItem> = listOf(*items)

  override fun isCellEditable(item: PTableItem, column: PTableColumn): Boolean {
    return column == PTableColumn.VALUE && item.name != "readonly"
  }
}
