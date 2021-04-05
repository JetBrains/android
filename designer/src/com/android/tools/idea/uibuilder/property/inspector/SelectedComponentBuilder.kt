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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.TAG_DEEP_LINK
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.api.SelectedComponentModel
import com.android.tools.property.panel.api.SelectedComponentPanel
import com.intellij.util.text.nullize
import icons.StudioIcons
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ARGUMENT
import javax.swing.Icon

private const val UNNAMED_COMPONENT = "<unnamed>"
private const val MULTIPLE_COMPONENTS = "<multiple>"

class SelectedComponentBuilder : InspectorBuilder<NlPropertyItem> {
  private val hiddenTags = setOf(TAG_DEEP_LINK, TAG_ARGUMENT)

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NlPropertyItem>) {
    val components = properties.first?.components ?: emptyList()
    if (components.isEmpty()) {
      return
    }

    if (components.all { hiddenTags.contains(it.tagName) }) {
      return
    }

    val idProperty = properties.getOrNull(ANDROID_URI, ATTR_ID)
    val idValue: String
    val iconValue: Icon?
    val qualifiedTagName: String
    if (components.size == 1) {
      idValue = idProperty?.value.nullize() ?: UNNAMED_COMPONENT
      iconValue = components[0].mixin?.icon ?: StudioIcons.LayoutEditor.Palette.VIEW
      qualifiedTagName = components[0].tagName
    }
    else {
      idValue = MULTIPLE_COMPONENTS
      // TODO: Get another icon for multiple components
      iconValue = StudioIcons.LayoutEditor.Palette.VIEW_SWITCHER
      qualifiedTagName = ""
    }
    val tagName = qualifiedTagName.substring(qualifiedTagName.lastIndexOf('.') + 1)
    val model = object : SelectedComponentModel {
      override val id = idValue
      override val icon = iconValue
      override val description = tagName
    }
    val panel = SelectedComponentPanel(model)
    inspector.addComponent(panel, null)
  }
}
