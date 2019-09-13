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

import com.android.tools.idea.uibuilder.property2.NeleNewPropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.support.NeleTwoStateBooleanControlTypeProvider
import com.android.tools.idea.uibuilder.property2.ui.EmptyTablePanel
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.FilteredPTableModel
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.api.TableLineModel
import com.android.tools.property.panel.api.TableUIProvider
import com.android.tools.property.panel.impl.support.SimpleControlTypeProvider
import com.android.tools.property.ptable2.PTableItem
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.StudioIcons
import org.jetbrains.android.formatter.AttributeComparator

private const val ADD_PROPERTY_ACTION_TITLE = "Add Attribute"
private const val DELETE_ROW_ACTION_TITLE = "Remove Selected Attribute"

/**
 * Comparator that is sorting [PTableItem] in Android sorting order.
 * This implies layout attributes first and layout_width before layout_height.
 */
val androidSortOrder: Comparator<PTableItem> = AttributeComparator { it.name }

class DeclaredAttributesInspectorBuilder(
  private val model: NelePropertiesModel,
  enumSupportProvider: EnumSupportProvider<NelePropertyItem>
) : InspectorBuilder<NelePropertyItem> {

  private val newPropertyInstance = NeleNewPropertyItem(model, PropertiesTable.emptyTable(), { it.rawValue == null }, {})
  private val nameControlTypeProvider = SimpleControlTypeProvider<NeleNewPropertyItem>(ControlType.TEXT_EDITOR)
  private val nameEditorProvider = EditorProvider.createForNames<NeleNewPropertyItem>()
  private val controlTypeProvider = NeleTwoStateBooleanControlTypeProvider(enumSupportProvider)
  private val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)
  private val tableUIProvider = TableUIProvider.create(
    NeleNewPropertyItem::class.java, nameControlTypeProvider, nameEditorProvider,
    NelePropertyItem::class.java, controlTypeProvider, editorProvider)

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    if (properties.isEmpty || !InspectorSection.DECLARED.visible) {
      return
    }
    newPropertyInstance.properties = properties
    newPropertyInstance.name = ""
    val declaredTableModel = FilteredPTableModel.create(model, { item -> item.rawValue != null }, androidSortOrder)
    val addNewRow = AddNewRowAction(declaredTableModel, newPropertyInstance)
    val deleteRowAction = DeleteRowAction(declaredTableModel)
    val actions = listOf(addNewRow, deleteRowAction)
    val titleModel = inspector.addExpandableTitle(InspectorSection.DECLARED.title, false, actions)
    val tableLineModel = inspector.addTable(declaredTableModel, false, tableUIProvider, titleModel)
    inspector.addComponent(EmptyTablePanel(addNewRow, tableLineModel), titleModel)
    addNewRow.titleModel = titleModel
    addNewRow.lineModel = tableLineModel
    deleteRowAction.titleModel = titleModel
    deleteRowAction.lineModel = tableLineModel
  }

  private class AddNewRowAction(
    val tableModel: FilteredPTableModel<NelePropertyItem>,
    val newProperty: NeleNewPropertyItem
  ) : AnAction(null, ADD_PROPERTY_ACTION_TITLE, StudioIcons.Common.ADD) {

    var titleModel: InspectorLineModel? = null
    var lineModel: TableLineModel? = null

    override fun actionPerformed(event: AnActionEvent) {
      titleModel?.expanded = true
      val model = lineModel ?: return
      val nextItem = tableModel.addNewItem(newProperty)
      model.requestFocus(nextItem)
    }
  }

  private class DeleteRowAction(
    private val tableModel: FilteredPTableModel<NelePropertyItem>
  ) : AnAction(null, DELETE_ROW_ACTION_TITLE, StudioIcons.Common.REMOVE) {

    var titleModel: InspectorLineModel? = null
    var lineModel: TableLineModel? = null

    override fun actionPerformed(event: AnActionEvent) {
      titleModel?.expanded = true
      val selected = lineModel?.selectedItem as? NelePropertyItem ?: return
      tableModel.deleteItem(selected)
    }
  }
}
