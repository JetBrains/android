/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.FilteredPTableModel
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.api.TableUIProvider
import com.android.tools.property.ptable.PTableItem

class InspectorTableBuilder(
  private val title: String,
  private val filter: (InspectorPropertyItem) -> Boolean,
  private val model: InspectorPropertiesModel,
  enumSupportProvider: EnumSupportProvider<InspectorPropertyItem>,
  controlTypeProvider: ControlTypeProvider<InspectorPropertyItem>,
  private val itemComparator: Comparator<PTableItem> = FilteredPTableModel.alphabeticalSortOrder,
  private val searchable: Boolean = false,
) : InspectorBuilder<InspectorPropertyItem> {

  private val editorProvider =
    ResolutionStackEditorProvider(model, enumSupportProvider, controlTypeProvider)
  private val uiProvider = TableUIProvider(controlTypeProvider, editorProvider)

  override fun attachToInspector(
    inspector: InspectorPanel,
    properties: PropertiesTable<InspectorPropertyItem>,
  ) {
    val tableModel =
      FilteredPTableModel(
        model,
        filter,
        itemComparator = itemComparator,
        valueEditable = {
          editorProvider.isValueClickable(it)
        }, // links must be editable for accessibility
        hasCustomCursor = {
          editorProvider.isValueClickable(it)
        }, // enables cursor changes for items not being edited
      )
    if (tableModel.items.isEmpty()) {
      return
    }
    val titleModel = inspector.addExpandableTitle(title, true)
    inspector.addTable(tableModel, searchable, uiProvider, emptyList(), titleModel)
  }
}
