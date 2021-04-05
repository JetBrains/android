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
package com.android.tools.idea.naveditor.property.inspector

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.uibuilder.property.NelePropertyItem
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_UP_TO
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_UP_TO_INCLUSIVE
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_SINGLE_TOP

class ActionInspectorBuilder(private val editorProvider: EditorProvider<NelePropertyItem>) : InspectorBuilder<NelePropertyItem> {
  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    if (properties.first?.components?.singleOrNull()?.isAction != true) {
      return
    }

    addPopBehaviorProperties(inspector, properties)
    addLaunchOptionProperties(inspector, properties)
  }

  private fun addPopBehaviorProperties(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    addActionProperties(inspector, properties, "Pop Behavior", ATTR_POP_UP_TO, ATTR_POP_UP_TO_INCLUSIVE)
  }

  private fun addLaunchOptionProperties(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    addActionProperties(inspector, properties, "Launch Options", ATTR_SINGLE_TOP)
  }

  private fun addActionProperties(inspector: InspectorPanel,
                                  properties: PropertiesTable<NelePropertyItem>,
                                  title: String,
                                  vararg propertyNames: String) {

    val titleModel = inspector.addExpandableTitle(title)

    for (propertyName in propertyNames) {
      val property = properties.getOrNull(ResourceNamespace.TODO().xmlNamespaceUri, propertyName) ?: continue
      inspector.addEditor(editorProvider.createEditor(property), titleModel)
    }
  }
}