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
import com.android.tools.idea.naveditor.model.isActivity
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_ACTION
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DATA
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DATA_PATTERN

private val propertyNames = arrayOf(ATTR_ACTION, ATTR_DATA, ATTR_DATA_PATTERN)

class ActivityInspectorBuilder(private val editorProvider: EditorProvider<NlPropertyItem>) : InspectorBuilder<NlPropertyItem> {
  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NlPropertyItem>) {
    if (properties.first?.components?.singleOrNull()?.isActivity != true) {
      return
    }

    val titleModel = inspector.addExpandableTitle("Activity")

    for (propertyName in propertyNames) {
      val property = properties.getOrNull(ResourceNamespace.TODO().xmlNamespaceUri, propertyName) ?: continue
      inspector.addEditor(editorProvider.createEditor(property), titleModel)
    }
  }
}