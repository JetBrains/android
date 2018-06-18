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
package com.android.tools.idea.uibuilder.property2.inspector

import com.android.tools.adtui.ptable2.PTableColumn
import com.android.tools.adtui.ptable2.PTableItem
import com.android.tools.adtui.ptable2.PTableModel
import com.android.tools.adtui.ptable2.PTableModelUpdateListener
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.uibuilder.property2.NeleNewPropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import org.jetbrains.android.formatter.AttributeComparator

class AdvancedInspectorBuilder(model: NelePropertiesModel, private val tableUIProvider: TableUIProvider)
  : InspectorBuilder<NelePropertyItem> {

  private val comparator = AttributeComparator<NelePropertyItem>({ it.name })
  private val newPropertyInstance = NeleNewPropertyItem(model, PropertiesTable.emptyTable())
  private var lastDeclaredTable: NeleTableModel? = null

  init {
    model.addListener(object: PropertiesModelListener<NelePropertyItem> {

      override fun propertiesGenerated(model: PropertiesModel<NelePropertyItem>) {
      }

      override fun propertyValuesChanged(model: PropertiesModel<NelePropertyItem>) {
        val table = lastDeclaredTable ?: return
        val declared = model.properties.values.filter { it.rawValue != null }.toMutableList()
        sort(declared)
        table.updateItems(declared)
      }
    })
  }

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    val declared = properties.values.filter { it.rawValue != null }.toMutableList()
    val newProperty = if (properties.isEmpty) null else newPropertyInstance
    newProperty?.properties = properties
    newProperty?.name = ""
    lastDeclaredTable = addTable(inspector, "Declared Attributes", declared, newProperty, searchable = false)

    val all = properties.values.toMutableList()
    addTable(inspector, "All Attributes", all, null, searchable = true)
  }

  private fun addTable(inspector: InspectorPanel,
                       title: String,
                       properties: MutableList<NelePropertyItem>,
                       newProperty: NelePropertyItem?,
                       searchable: Boolean): NeleTableModel {
    sort(properties)
    if (newProperty != null) {
      properties.add(newProperty)
    }
    val titleModel = inspector.addExpandableTitle(title, true)
    val tableModel = NeleTableModel(properties)
    if (tableModel.items.isNotEmpty()) {
      inspector.addTable(tableModel, searchable, tableUIProvider, titleModel)
    }
    return tableModel
  }

  private fun sort(properties: MutableList<NelePropertyItem>) {
    // TODO: Grouping
    properties.sortWith(comparator)
  }
}

private class NeleTableModel(override val items: MutableList<NelePropertyItem>) : PTableModel {

  private val listeners = mutableListOf<PTableModelUpdateListener>()

  override fun isCellEditable(item: PTableItem, column: PTableColumn): Boolean {
    return when (item) {
      is NeleNewPropertyItem -> column == PTableColumn.NAME || item.delegate != null
      else -> column == PTableColumn.VALUE
    }
  }

  override fun acceptMoveToNextEditor(item: PTableItem, column: PTableColumn): Boolean {
    // Accept any move to the next editor unless we know that that the current row
    // is going to fly away soon. That is the case if the user just entered a value
    // for a new property. The updateItems below will cause a better determination
    // of an appropriate next editor.
    return !(item is NeleNewPropertyItem && column == PTableColumn.VALUE && item.rawValue != null )
  }

  override fun addListener(listener: PTableModelUpdateListener) {
    listeners.add(listener)
  }

  fun updateItems(declared: MutableList<NelePropertyItem>) {
    val last = lastItem()
    if (last != null) {
      declared.add(last)
    }
    if (isSameItems(declared)) {
      return
    }
    items.clear()
    items.addAll(declared)
    listeners.forEach { it.itemsUpdated() }
  }

  private fun lastItem(): NeleNewPropertyItem? {
    if (items.isEmpty()) {
      return null
    }
    return items[items.size - 1] as? NeleNewPropertyItem
  }

  private fun isSameItems(declared: List<NelePropertyItem>): Boolean {
    // Provide size shortcut and then use AbstractList.equals method to check that all items are the same:
    return declared.size == items.size && declared == items
  }
}
