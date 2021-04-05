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

import com.android.tools.idea.uibuilder.property.NeleNewPropertyItem
import com.android.tools.idea.uibuilder.property.NelePropertiesModel
import com.android.tools.idea.uibuilder.property.NelePropertyItem
import com.android.tools.idea.uibuilder.property.support.NeleTwoStateBooleanControlTypeProvider
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
import com.android.tools.property.ptable2.PTableItem
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions.ACTION_DELETE
import icons.StudioIcons
import org.jetbrains.android.formatter.AttributeComparator

private const val ADD_PROPERTY_ACTION_TITLE = "Add attribute"
private const val DELETE_ROW_ACTION_TITLE = "Remove selected attribute"

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
    val declaredTableModel = FilteredPTableModel.create(model, { it.rawValue != null }, { it.value = null }, androidSortOrder)
    val addNewRow = AddNewRowAction(newPropertyInstance)
    val deleteRowAction = DeleteRowAction()
    val actions = listOf(addNewRow, deleteRowAction)
    val titleModel = inspector.addExpandableTitle(InspectorSection.DECLARED.title, false, actions)
    val tableLineModel = inspector.addTable(declaredTableModel, false, tableUIProvider, actions, titleModel)
    inspector.addComponent(EmptyTablePanel(addNewRow, tableLineModel), titleModel)
    addNewRow.titleModel = titleModel
    addNewRow.lineModel = tableLineModel
    deleteRowAction.titleModel = titleModel
    deleteRowAction.lineModel = tableLineModel
  }

  private class AddNewRowAction(
    val newProperty: NeleNewPropertyItem
  ) : AnAction(null, ADD_PROPERTY_ACTION_TITLE, StudioIcons.Common.ADD) {

    var titleModel: InspectorLineModel? = null
    var lineModel: TableLineModel? = null

    override fun actionPerformed(event: AnActionEvent) {
      titleModel?.expanded = true
      val model = lineModel ?: return
      val nextItem = model.addItem(newProperty)
      model.requestFocus(nextItem)
    }
  }

  private class DeleteRowAction: AnAction(null, DELETE_ROW_ACTION_TITLE, StudioIcons.Common.REMOVE) {
    var titleModel: InspectorLineModel? = null
    var lineModel: TableLineModel? = null

    init {
      val manager = ActionManager.getInstance()
      shortcutSet = manager.getAction(ACTION_DELETE).shortcutSet
    }

    override fun update(event: AnActionEvent) {
      val enabled = lineModel?.tableModel?.items?.isNotEmpty() ?: false
      event.presentation.isEnabled = enabled

      // Hack: the FocusableActionButton will update when the state of the template presentation is updated:
      templatePresentation.isEnabled = enabled
    }

    override fun actionPerformed(event: AnActionEvent) {
      titleModel?.expanded = true
      val model = lineModel ?: return
      val selected = (model.selectedItem ?: model.tableModel.items.firstOrNull()) ?: return
      model.removeItem(selected)
    }
  }
}
