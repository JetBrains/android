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

import com.android.tools.idea.uibuilder.property.NelePropertiesModel
import com.android.tools.idea.uibuilder.property.NelePropertyItem
import com.android.tools.idea.uibuilder.property.inspector.groups.ConstraintGroup
import com.android.tools.idea.uibuilder.property.inspector.groups.MarginGroup
import com.android.tools.idea.uibuilder.property.inspector.groups.PaddingGroup
import com.android.tools.idea.uibuilder.property.inspector.groups.ThemeGroup
import com.android.tools.idea.uibuilder.property.support.NeleControlTypeProvider
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.FilteredPTableModel
import com.android.tools.property.panel.api.FilteredPTableModel.PTableModelFactory.alphabeticalSortOrder
import com.android.tools.property.panel.api.GroupSpec
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.api.TableUIProvider

class AllAttributesInspectorBuilder(
  private val model: NelePropertiesModel,
  controlTypeProvider: NeleControlTypeProvider,
  editorProvider: EditorProvider<NelePropertyItem>
) : InspectorBuilder<NelePropertyItem> {

  private val allTableUIProvider = TableUIProvider.create(
    NelePropertyItem::class.java, controlTypeProvider, editorProvider)

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    if (properties.isEmpty || !InspectorSection.ALL.visible) {
      return
    }

    val allTableModel = FilteredPTableModel.create(model, { true }, {}, alphabeticalSortOrder, createGroups(properties))
    val titleModel = inspector.addExpandableTitle(InspectorSection.ALL.title, false)
    inspector.addTable(allTableModel, true, allTableUIProvider, emptyList(), titleModel)
  }

  private fun createGroups(properties: PropertiesTable<NelePropertyItem>): List<GroupSpec<NelePropertyItem>> {
    val groups = mutableListOf<GroupSpec<NelePropertyItem>>()
    groups.add(PaddingGroup(properties))
    groups.add(MarginGroup(properties))
    groups.add(ConstraintGroup(properties))
    groups.add(ThemeGroup(model.facet, properties))
    return groups
  }
}
