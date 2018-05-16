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
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import kotlin.streams.toList
import org.jetbrains.android.formatter.AttributeComparator

class AdvancedInspectorBuilder(private val tableUIProvider: TableUIProvider) : InspectorBuilder<NelePropertyItem> {

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    val declared = properties.values.stream().filter { it.rawValue != null }.toList()
    addTable(inspector, "Declared Attributes", declared, searchable = false)

    val all = properties.values.toList()
    addTable(inspector, "All Attributes", all, searchable = true)
  }

  private fun addTable(inspector: InspectorPanel, title: String, properties: List<NelePropertyItem>, searchable: Boolean) {
    val titleModel = inspector.addExpandableTitle(title, true)
    val tableModel = NeleTableModel(properties)
    val lineModel = inspector.addTable(tableModel, searchable, tableUIProvider)
    titleModel.addChild(lineModel)
  }
}

private class NeleTableModel(override val items: List<NelePropertyItem>) : PTableModel {
  init {
    // TODO: Grouping
    if (items is MutableList<NelePropertyItem>) {
      val comparator = AttributeComparator<NelePropertyItem>({ it.name })
      items.sortWith(comparator)
    }
  }

  override fun isCellEditable(item: PTableItem, column: PTableColumn): Boolean {
    return column == PTableColumn.VALUE
  }
}
