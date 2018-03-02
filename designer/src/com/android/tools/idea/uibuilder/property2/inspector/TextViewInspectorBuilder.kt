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

import com.android.SdkConstants.*
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.uibuilder.property2.NelePropertyItem

/**
 * An [InspectorBuilder] for all widgets that are based on a TextView.
 *
 * The attributes for a TextView is shown only if all the [REQUIRED_PROPERTIES]
 * are available. The controls for an [ATTR_TEXT_APPEARANCE] are displayed in a
 * collapsible section.
 *
 * There are 2 attributes that may or may not be present:
 *     [ATTR_FONT_FAMILY] and [ATTR_TEXT_ALIGNMENT]
 * They are present if the minSdkVersion is high enough or if AppCompat is used.
 */
class TextViewInspectorBuilder(private val editorProvider: EditorProvider<NelePropertyItem>) : InspectorBuilder<NelePropertyItem> {

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    if (!isApplicable(properties)) return

    inspector.addSeparator()
    val textViewLabel = inspector.addExpandableTitle("TextView")
    addComponent(inspector, properties[ANDROID_URI, ATTR_TEXT], textViewLabel)
    addComponent(inspector, getDesignProperty(properties, ATTR_TEXT), textViewLabel)
    addComponent(inspector, properties[ANDROID_URI, ATTR_CONTENT_DESCRIPTION], textViewLabel)

    val textAppearanceLabel = addComponent(inspector, properties[ANDROID_URI, ATTR_TEXT_APPEARANCE], textViewLabel)
    textAppearanceLabel.makeExpandable(initiallyExpanded = false)

    val fontFamily = getOptionalProperty(properties, ATTR_FONT_FAMILY)
    if (fontFamily != null) {
      addComponent(inspector, fontFamily, textAppearanceLabel)
    }
    addComponent(inspector, properties[ANDROID_URI, ATTR_TYPEFACE], textAppearanceLabel)
    addComponent(inspector, properties[ANDROID_URI, ATTR_TEXT_SIZE], textAppearanceLabel)
    addComponent(inspector, properties[ANDROID_URI, ATTR_LINE_SPACING_EXTRA], textAppearanceLabel)
    // TODO: TextStyle & Alignment panels
    addComponent(inspector, properties[ANDROID_URI, ATTR_TEXT_COLOR], textAppearanceLabel)
  }

  private fun addComponent(inspector: InspectorPanel, property: NelePropertyItem, group: InspectorLineModel): InspectorLineModel {
    val line = inspector.addComponent(editorProvider.provide(property))
    group.addChild(line)
    return line
  }

  private fun isApplicable(properties: PropertiesTable<NelePropertyItem>): Boolean {
    return properties.getByNamespace(ANDROID_URI).keys.containsAll(REQUIRED_PROPERTIES)
  }

  private fun getDesignProperty(properties: PropertiesTable<NelePropertyItem>, attribute: String): NelePropertyItem {
    return properties.getOrNull(TOOLS_URI, attribute) ?: properties[ANDROID_URI, attribute].designProperty
  }

  private fun getOptionalProperty(properties: PropertiesTable<NelePropertyItem>, attribute: String): NelePropertyItem? {
    return properties.getOrNull(ANDROID_URI, attribute) ?: properties.getOrNull(AUTO_URI, attribute) ?: return null
  }

  companion object {
    val REQUIRED_PROPERTIES = listOf(
      ATTR_TEXT,
      ATTR_CONTENT_DESCRIPTION,
      ATTR_TEXT_APPEARANCE,
      ATTR_TYPEFACE,
      ATTR_TEXT_SIZE,
      ATTR_LINE_SPACING_EXTRA,
      ATTR_TEXT_STYLE,
      ATTR_TEXT_ALL_CAPS,
      ATTR_TEXT_COLOR)
  }
}
