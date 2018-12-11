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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.tools.idea.common.property2.api.InspectorBuilder
import com.android.tools.idea.common.property2.api.InspectorPanel
import com.android.tools.idea.common.property2.api.PropertiesTable
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.model.SelectedComponentModel
import com.android.tools.idea.uibuilder.property2.ui.SelectedComponentPanel

class SelectedComponentBuilder : InspectorBuilder<NelePropertyItem> {

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    val components = properties.first?.components ?: emptyList()
    if (components.isEmpty()) {
      return
    }
    val id = properties.getOrNull(ANDROID_URI, ATTR_ID)
    val qualifiedTagName = if (components.size == 1) components[0].tagName else ""
    val tagName = qualifiedTagName.substring(qualifiedTagName.lastIndexOf('.') + 1)
    val panel = SelectedComponentPanel(SelectedComponentModel(id, components, tagName))
    val lineModel = inspector.addComponent(panel, null)
    panel.lineModel = lineModel
  }
}
