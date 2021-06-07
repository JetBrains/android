/*
 * Copyright (C) 2021 The Android Open Source Project
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

/**
 * Adds a Semantics section to the attributes panel.
 *
 * It has 2 sub sections: "Merged semantics" and "Declared semantics"
 */
class SemanticsBuilder(
  private val title: String,
  private val model: InspectorPropertiesModel,
  enumSupportProvider: EnumSupportProvider<InspectorPropertyItem>,
  controlTypeProvider: ControlTypeProvider<InspectorPropertyItem>
) : InspectorBuilder<InspectorPropertyItem> {

  private val editorProvider = ResolutionStackEditorProvider(model, enumSupportProvider, controlTypeProvider)

  // TODO: Check if a reified type parameter can be used instead of the explicit class
  private val uiProvider = TableUIProvider.create(InspectorPropertyItem::class.java, controlTypeProvider, editorProvider)

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<InspectorPropertyItem>) {
    val mergedTableModel = FilteredPTableModel.create(model, { it.section == PropertySection.MERGED }, {}, valueEditable = { false })
    val unmergedTableModel = FilteredPTableModel.create(model, { it.section == PropertySection.UNMERGED }, {}, valueEditable = { false })
    if (mergedTableModel.items.isEmpty() && unmergedTableModel.items.isEmpty()) {
      return
    }
    val titleModel = inspector.addExpandableTitle(title, false)
    if (mergedTableModel.items.isNotEmpty()) {
      val subTitle = inspector.addSubTitle("Merged semantics", initiallyExpanded = false, titleModel)
      inspector.addTable(mergedTableModel, searchable = false, uiProvider, parent = subTitle)
    }
    if (unmergedTableModel.items.isNotEmpty()) {
      val subTitle = inspector.addSubTitle("Declared semantics", initiallyExpanded = false, titleModel)
      inspector.addTable(unmergedTableModel, searchable = false, uiProvider, parent = subTitle)
    }
  }
}
