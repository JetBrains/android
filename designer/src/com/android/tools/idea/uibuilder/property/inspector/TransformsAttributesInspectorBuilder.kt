/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.idea.uibuilder.property.NlNewPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.support.NlTwoStateBooleanControlTypeProvider
import com.android.tools.idea.uibuilder.property.ui.TransformsPanel
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable

/** Builder for a panel for transform properties in the layout editor */
class TransformsAttributesInspectorBuilder(
  private val model: NlPropertiesModel,
  enumSupportProvider: EnumSupportProvider<NlPropertyItem>,
) : InspectorBuilder<NlPropertyItem> {

  private val newPropertyInstance =
    NlNewPropertyItem(model, PropertiesTable.emptyTable(), { it.rawValue == null }, {})
  private val controlTypeProvider = NlTwoStateBooleanControlTypeProvider(enumSupportProvider)
  private val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)

  override fun attachToInspector(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
  ) {
    if (properties.isEmpty || !InspectorSection.TRANSFORMS.visible) {
      return
    }
    newPropertyInstance.properties = properties
    newPropertyInstance.name = ""

    val attributes =
      mutableListOf(
        "rotation",
        "rotationX",
        "rotationY",
        "scaleX",
        "scaleY",
        "translationX",
        "translationY",
        "translationZ",
        "alpha",
      )

    val titleModel = inspector.addExpandableTitle(InspectorSection.TRANSFORMS.title)

    inspector.addComponent(TransformsPanel(model, properties), titleModel)

    for (attributeName in attributes) {
      val property = properties.getOrNull(SdkConstants.ANDROID_URI, attributeName)
      if (property != null) {
        inspector.addEditor(editorProvider.createEditor(property), titleModel)
      }
    }
  }
}
