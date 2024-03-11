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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.tools.idea.uibuilder.property.NlNewPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.support.NlTwoStateBooleanControlTypeProvider
import com.android.tools.idea.uibuilder.property.ui.EmptyTablePanel
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
import com.android.tools.property.ptable.PTableItem
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions.ACTION_DELETE
import com.intellij.openapi.application.invokeLater
import org.jetbrains.android.formatter.AttributeComparator

private const val ADD_PROPERTY_ACTION_TITLE = "Add attribute"
private const val DELETE_ROW_ACTION_TITLE = "Remove selected attribute"

/**
 * Comparator that is sorting [PTableItem] in Android sorting order. This implies layout attributes
 * first and layout_width before layout_height.
 */
val androidSortOrder: Comparator<PTableItem> = AttributeComparator { it.name }

class DeclaredAttributesInspectorBuilder(
  private val model: NlPropertiesModel,
  enumSupportProvider: EnumSupportProvider<NlPropertyItem>,
) : InspectorBuilder<NlPropertyItem> {

  private val newPropertyInstance =
    NlNewPropertyItem(model, PropertiesTable.emptyTable(), { it.rawValue == null }, {})
  private val nameControlTypeProvider =
    SimpleControlTypeProvider<NlNewPropertyItem>(ControlType.TEXT_EDITOR)
  private val nameEditorProvider = EditorProvider.createForNames<NlNewPropertyItem>()
  private val controlTypeProvider = NlTwoStateBooleanControlTypeProvider(enumSupportProvider)
  private val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)
  private val tableUIProvider =
    TableUIProvider(
      nameControlTypeProvider,
      nameEditorProvider,
      controlTypeProvider,
      editorProvider,
    )
  private val insertOp = ::insertNewItem

  override fun attachToInspector(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
  ) {
    if (properties.isEmpty || !InspectorSection.DECLARED.visible) {
      return
    }
    newPropertyInstance.properties = properties
    newPropertyInstance.name = ""
    val declaredTableModel =
      FilteredPTableModel(
        model,
        { it.rawValue != null },
        insertOp,
        { it.value = null },
        androidSortOrder,
      )
    val addNewRow = AddNewRowAction(newPropertyInstance)
    val deleteRowAction = DeleteRowAction()
    val actions = listOf(addNewRow, deleteRowAction)
    val titleModel = inspector.addExpandableTitle(InspectorSection.DECLARED.title, false, actions)
    val tableLineModel =
      inspector.addTable(declaredTableModel, false, tableUIProvider, actions, titleModel)
    inspector.addComponent(EmptyTablePanel(addNewRow, tableLineModel), titleModel)
    addNewRow.titleModel = titleModel
    addNewRow.lineModel = tableLineModel
    deleteRowAction.titleModel = titleModel
    deleteRowAction.lineModel = tableLineModel
  }

  private fun insertNewItem(name: String, value: String): NlPropertyItem? {
    newPropertyInstance.name = name
    if (newPropertyInstance.delegate == null) {
      return null
    }
    newPropertyInstance.value = value
    return newPropertyInstance
  }

  private class AddNewRowAction(val newProperty: NlNewPropertyItem) :
    AnAction(ADD_PROPERTY_ACTION_TITLE, ADD_PROPERTY_ACTION_TITLE, AllIcons.General.Add) {

    var titleModel: InspectorLineModel? = null
    var lineModel: TableLineModel? = null

    override fun actionPerformed(event: AnActionEvent) {
      titleModel?.expanded = true
      val model = lineModel ?: return
      val nextItem = model.addItem(newProperty)
      model.requestFocus(nextItem)
    }
  }

  private class DeleteRowAction :
    AnAction(DELETE_ROW_ACTION_TITLE, DELETE_ROW_ACTION_TITLE, AllIcons.General.Remove) {
    var titleModel: InspectorLineModel? = null
    var lineModel: TableLineModel? = null

    init {
      val manager = ActionManager.getInstance()
      shortcutSet = manager.getAction(ACTION_DELETE).shortcutSet
    }

    // Running on edt because of the panel data model access
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
      val enabled = lineModel?.tableModel?.items?.isNotEmpty() ?: false
      event.presentation.isEnabled = enabled
    }

    override fun actionPerformed(event: AnActionEvent) {
      titleModel?.expanded = true
      val model = lineModel ?: return
      val selected = (model.selectedItem ?: model.tableModel.items.firstOrNull()) ?: return
      // Stop editing the table before removing the selected item.
      // This will give the cell editor a chance to commit any pending changes before we delete
      // the value. If we don't the cell editor will commit after we delete the value.
      model.stopEditing()
      // The cell editor may commit the changes late (as a result of a focus loss).
      // Wait for it by posting the action on the event queue after the focus loss.
      invokeLater { model.removeItem(selected) }
    }
  }
}
