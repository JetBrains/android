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

import com.android.tools.adtui.ptable2.*
import com.android.tools.adtui.ptable2.impl.PTableModelImpl
import org.mockito.Mockito
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.TableModelListener

fun createModel(vararg items: PTableItem): PTableTestModel {
  return PTableTestModel(*items)
}

fun addModelListener(model: PTableModelImpl): TableModelListener {
  val listener = Mockito.mock<TableModelListener>(TableModelListener::class.java)
  model.addTableModelListener(listener)
  return listener
}

open class Item(override val name: String, override val value: String? = null ) : PTableItem {
  override fun hashCode(): Int {
    return name.hashCode()
  }

  override fun equals(other: Any?): Boolean {
    return name == (other as? Item)?.name
  }
}

class Group(name: String, vararg childItems: PTableItem) : Item(name, null), PTableGroupItem {
  override val value: String? = null
  override val children: List<PTableItem> = listOf(*childItems)
}

class PTableTestModel(vararg items: PTableItem) : PTableModel {
  private val listeners = mutableListOf<PTableModelUpdateListener>()
  override val items = mutableListOf(*items)

  override fun isCellEditable(item: PTableItem, column: PTableColumn): Boolean =
    when (column) {
      PTableColumn.NAME -> item.name == "new"
      PTableColumn.VALUE -> item.name != "readonly"
    }

  fun updateTo(vararg newItems: PTableItem) {
    items.clear()
    items.addAll(listOf(*newItems))
    listeners.forEach { it.itemsUpdated() }
  }

  override fun addListener(listener: PTableModelUpdateListener) {
    listeners.add(listener)
  }
}

class DummyPTableCellEditor : PTableCellEditor {

  override val editorComponent = JPanel()

  override val value: String?
    get() = null

  override val isBooleanEditor: Boolean
    get() = false

  override fun toggleValue() {}

  override fun requestFocus() {}

  override fun cancelEditing() {}

  override fun close(oldTable: PTable) {}
}

class DummyPTableCellEditorProvider : PTableCellEditorProvider {
  val editor = DummyPTableCellEditor()

  override fun invoke(table: PTable, property: PTableItem, column: PTableColumn): PTableCellEditor {
    return editor
  }
}

