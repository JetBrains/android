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

import com.android.tools.adtui.ptable2.PTableModel
import com.android.tools.idea.common.property2.api.FilteredPTableModel
import com.android.tools.idea.common.property2.api.InspectorBuilder
import com.android.tools.idea.common.property2.api.InspectorPanel
import com.android.tools.idea.common.property2.api.PropertiesTable
import com.android.tools.idea.common.property2.api.TableLineModel
import com.android.tools.idea.common.property2.api.TableUIProvider
import com.android.tools.idea.uibuilder.property2.NeleNewPropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.StudioIcons

private const val DECLARED_TITLE = "Declared Attributes"
private const val ALL_ATTRIBUTES_TITLE = "All Attributes"
private const val ADD_PROPERTY_ACTION_TITLE = "Add Property"
private const val DELETE_ROW_ACTION_TITLE = "Remove Selected Property"

class AdvancedInspectorBuilder(private val model: NelePropertiesModel, private val tableUIProvider: TableUIProvider)
  : InspectorBuilder<NelePropertyItem> {

  private val newPropertyInstance = NeleNewPropertyItem(model, PropertiesTable.emptyTable())

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    if (properties.isEmpty) {
      return
    }
    newPropertyInstance.properties = properties
    newPropertyInstance.name = ""
    val declaredTableModel = FilteredPTableModel.create(model, { item -> item.rawValue != null })
    val addNewRow = AddNewRowAction(declaredTableModel, newPropertyInstance)
    val deleteRowAction = DeleteRowAction(declaredTableModel)
    val tableLineModel = addTable(inspector, DECLARED_TITLE, declaredTableModel, addNewRow, deleteRowAction, searchable = false)
    addNewRow.lineModel = tableLineModel
    deleteRowAction.lineModel = tableLineModel

    val allTableModel = FilteredPTableModel.create(model, { true })
    addTable(inspector, ALL_ATTRIBUTES_TITLE, allTableModel, searchable = true)
  }

  private fun addTable(inspector: InspectorPanel,
                       title: String,
                       tableModel: PTableModel,
                       vararg actions: AnAction,
                       searchable: Boolean): TableLineModel {
    val titleModel = inspector.addExpandableTitle(title, true, *actions)
    return inspector.addTable(tableModel, searchable, tableUIProvider, titleModel)
  }
}


private class AddNewRowAction(val tableModel: FilteredPTableModel<NelePropertyItem>,
                              val newProperty: NeleNewPropertyItem): AnAction(null, ADD_PROPERTY_ACTION_TITLE, StudioIcons.Common.ADD) {

  var lineModel: TableLineModel? = null

  override fun actionPerformed(event: AnActionEvent) {
    val model = lineModel ?: return
    val nextItem = tableModel.addNewItem(newProperty)
    model.requestFocus(nextItem)
  }
}

private class DeleteRowAction(private val tableModel: FilteredPTableModel<NelePropertyItem>)
  : AnAction(null, DELETE_ROW_ACTION_TITLE, StudioIcons.Common.REMOVE) {

  var lineModel: TableLineModel? = null

  override fun actionPerformed(event: AnActionEvent) {
    val selected = lineModel?.selectedItem as? NelePropertyItem ?: return
    tableModel.deleteItem(selected)
  }
}
