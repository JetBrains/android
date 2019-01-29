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
import com.android.tools.idea.uibuilder.property2.NeleFlagPropertyItem
import com.android.tools.idea.uibuilder.property2.NeleNewPropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.StudioIcons
import org.jetbrains.android.formatter.AttributeComparator

class AdvancedInspectorBuilder(model: NelePropertiesModel, private val tableUIProvider: TableUIProvider)
  : InspectorBuilder<NelePropertyItem> {

  private val comparator = AttributeComparator<NelePropertyItem> { it.name }
  private val newPropertyInstance = NeleNewPropertyItem(model, PropertiesTable.emptyTable())
  private var lastDeclaredTable: TableLineModel? = null
  private var lastNewDelegate: NelePropertyItem? = null

  init {
    model.addListener(object: PropertiesModelListener<NelePropertyItem> {

      override fun propertiesGenerated(model: PropertiesModel<NelePropertyItem>) {
      }

      override fun propertyValuesChanged(model: PropertiesModel<NelePropertyItem>) {
        val table = lastDeclaredTable ?: return
        val tableModel = table.tableModel as? NeleTableModel ?: return
        val declared = model.properties.values.filter { it.rawValue != null }.toMutableList()
        sort(declared)
        val newProperty = tableModel.lastItem()
        if (newProperty != null) {
          declared.add(newProperty)
          if (newProperty.rawValue != null) {
            // This property should appear else where in the declared table.
            // Make this new property item ready for adding another attribute.
            newProperty.name = ""
          }
        }
        if (!tableModel.isSameItems(declared) || lastNewDelegate != newProperty?.delegate) {
          val selected = table.selectedItem
          table.stopEditing()
          tableModel.updateItems(declared)
          if (selected != null) {
            table.requestFocus(selected)
          }
          lastNewDelegate = newProperty?.delegate
        }
      }
    })
  }

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    lastDeclaredTable = null
    lastNewDelegate = null
    if (properties.isEmpty) {
      return
    }
    val declared = properties.values.filter { it.rawValue != null }.toMutableList()
    newPropertyInstance.properties = properties
    newPropertyInstance.name = ""
    val declaredTableModel = NeleTableModel(declared)
    val addNewRow = AddNewRowAction(declaredTableModel, newPropertyInstance)
    val deleteRowAction = DeleteRowAction(declaredTableModel)
    lastDeclaredTable = addTable(inspector, "Declared Attributes", declaredTableModel, addNewRow, deleteRowAction, searchable = false)
    addNewRow.lineModel = lastDeclaredTable
    deleteRowAction.lineModel = lastDeclaredTable

    val all = properties.values.toMutableList()
    val allTableModel = NeleTableModel(all)
    addTable(inspector, "All Attributes", allTableModel, searchable = true)
  }

  private fun addTable(inspector: InspectorPanel,
                       title: String,
                       tableModel: NeleTableModel,
                       vararg actions: AnAction,
                       searchable: Boolean): TableLineModel {
    sort(tableModel.items)
    val titleModel = inspector.addExpandableTitle(title, true, *actions)
    return inspector.addTable(tableModel, searchable, tableUIProvider, titleModel)
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

  fun updateItems(newItems: MutableList<NelePropertyItem>) {
    items.clear()
    items.addAll(newItems)
    listeners.toTypedArray().forEach { it.itemsUpdated() }
  }

  /**
   * Return the last property item if it is a NeleNewPropertyItem.
   *
   * Skip flag items if the property is currently expanded in the table.
   */
  fun lastItem(): NeleNewPropertyItem? {
    if (items.isEmpty()) {
      return null
    }
    var last = items[items.size - 1]
    if (last is NeleFlagPropertyItem) {
      val flags = last.flags.children.size
      if (items.size - flags >= 1) {
        last = items[items.size - flags - 1]
      }
    }
    return last as? NeleNewPropertyItem
  }

  fun isSameItems(newItems: List<NelePropertyItem>): Boolean {
    // Provide size shortcut and then use AbstractList.equals method to check that all items are the same:
    return newItems.size == items.size && newItems == items
  }
}

private class AddNewRowAction(val tableModel: NeleTableModel,
                              val newProperty: NeleNewPropertyItem): AnAction(null, "Add Property", StudioIcons.Common.ADD) {

  var lineModel: TableLineModel? = null

  override fun actionPerformed(event: AnActionEvent) {
    val model = lineModel ?: return
    val last = tableModel.lastItem()
    if (last == null) {
      newProperty.name = ""
      val newItems = mutableListOf<NelePropertyItem>()
      newItems.addAll(tableModel.items)
      newItems.add(newProperty)
      tableModel.updateItems(newItems)
      model.requestFocus(newProperty)
    }
    else {
      model.requestFocus(last)
    }
  }
}

private class DeleteRowAction(private val tableModel: NeleTableModel)
  : AnAction(null, "Remove Selected Property", StudioIcons.Common.REMOVE) {

  var lineModel: TableLineModel? = null

  override fun actionPerformed(event: AnActionEvent) {
    val model = lineModel ?: return
    val selected = model.selectedItem as? NelePropertyItem ?: return
    val index = tableModel.items.indexOf(selected)
    if (index < 0) {
      return
    }
    var nextItem: NelePropertyItem? = null
    val last = tableModel.lastItem()
    model.stopEditing()
    if (selected == last) {
      // First determine the next item to select:
      if (index > 0) {
        nextItem = tableModel.items[index - 1]
      }

      // Then remove the add new property line
      val newItems = mutableListOf<NelePropertyItem>()
      newItems.addAll(tableModel.items.subList(0, tableModel.items.lastIndex))
      tableModel.updateItems(newItems)
    }
    else {
      // First determine the next item to select:
      if (index < tableModel.items.lastIndex) {
        nextItem = tableModel.items[index + 1]
      }
      else if (index > 0) {
        nextItem = tableModel.items[index - 1]
      }

      // Then delete the attribute by removing the value for the property.
      selected.value = null

      // Then remove the add new property line
      val newItems = mutableListOf<NelePropertyItem>()
      newItems.addAll(tableModel.items.subList(0, index))
      newItems.addAll(tableModel.items.subList(index + 1, tableModel.items.size))
      tableModel.updateItems(newItems)
    }
    if (nextItem != null) {
      model.requestFocus(nextItem)
    }
  }
}
