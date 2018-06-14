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
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.uibuilder.property2.NeleNewPropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import org.jetbrains.android.formatter.AttributeComparator

class AdvancedInspectorBuilder(model: NelePropertiesModel, private val tableUIProvider: TableUIProvider)
  : InspectorBuilder<NelePropertyItem> {

  private val comparator = AttributeComparator<NelePropertyItem>({ it.name })
  private val newPropertyInstance = NeleNewPropertyItem(model, PropertiesTable.emptyTable())

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    val declared = properties.values.filter { it.rawValue != null }.toMutableList()
    val newProperty = if (properties.isEmpty) null else newPropertyInstance
    newProperty?.properties = properties
    newProperty?.name = ""
    addTable(inspector, "Declared Attributes", declared, newProperty, searchable = false)

    val all = properties.values.toMutableList()
    addTable(inspector, "All Attributes", all, null, searchable = true)
  }

  private fun addTable(inspector: InspectorPanel,
                       title: String,
                       properties: MutableList<NelePropertyItem>,
                       newProperty: NelePropertyItem?,
                       searchable: Boolean) {
    sort(properties)
    if (newProperty != null) {
      properties.add(newProperty)
    }
    val titleModel = inspector.addExpandableTitle(title, true)
    val tableModel = NeleTableModel(properties)
    if (tableModel.items.isNotEmpty()) {
      inspector.addTable(tableModel, searchable, tableUIProvider, titleModel)
    }
  }

  private fun sort(properties: MutableList<NelePropertyItem>) {
    // TODO: Grouping
    properties.sortWith(comparator)
  }
}

private class NeleTableModel(override val items: List<NelePropertyItem>) : PTableModel {

  override fun isCellEditable(item: PTableItem, column: PTableColumn): Boolean {
    return when (item) {
      is NeleNewPropertyItem -> column == PTableColumn.NAME || item.delegate != null
      else -> column == PTableColumn.VALUE
    }
  }
}
