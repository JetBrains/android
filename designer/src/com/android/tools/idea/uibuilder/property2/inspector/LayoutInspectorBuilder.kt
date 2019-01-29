/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_PARENT_TAG
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VIEW_MERGE
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property2.api.EditorProvider
import com.android.tools.idea.common.property2.api.InspectorBuilder
import com.android.tools.idea.common.property2.api.InspectorPanel
import com.android.tools.idea.common.property2.api.PropertiesTable
import com.android.tools.idea.uibuilder.api.CustomPanel
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.intellij.openapi.project.Project
import javax.swing.JPanel

/**
 * An [InspectorBuilder] for all procured layout attributes.
 *
 * Usually we want to show [ATTR_LAYOUT_WIDTH] and [ATTR_LAYOUT_HEIGHT] first,
 * then the custom panel if applicable, followed by the layout attributes
 * defined in the [ViewHandler] of the layout ViewGroup.
 */
class LayoutInspectorBuilder(project: Project, private val editorProvider: EditorProvider<NelePropertyItem>) : InspectorBuilder<NelePropertyItem> {
  private val viewHandlerManager = ViewHandlerManager.get(project)
  private val cachedCustomPanels = mutableMapOf<String, CustomPanel>()

  override fun resetCache() {
    cachedCustomPanels.clear()
  }

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    val attributes = getLayoutAttributes(properties)
    val custom = setupCustomPanel(properties)
    if (!isApplicable(attributes, custom, properties)) return

    val titleModel = inspector.addExpandableTitle("layout")

    if (custom != null) {
      inspector.addComponent(custom, titleModel)
    }

    for (propertyName in attributes) {
      // TODO: Handle other namespaces
      val property = properties.getOrNull(ANDROID_URI, propertyName)
      if (property != null) {
        inspector.addEditor(editorProvider.createEditor(property), titleModel)
      }
    }
  }

  private fun isApplicable(attributes: List<String>, custom: JPanel?, properties: PropertiesTable<NelePropertyItem>): Boolean {
    if (custom != null) return true
    return attributes.any { properties.getOrNull(ANDROID_URI, it) != null }
  }

  private fun getLayoutAttributes(properties: PropertiesTable<NelePropertyItem>): List<String> {
    val attributes = mutableListOf(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT)
    val parent = getParentComponent(properties) ?: return attributes
    val handler = viewHandlerManager.getHandler(parent) ?: return attributes
    attributes.addAll(handler.layoutInspectorProperties)
    return attributes
  }

  private fun getParentComponent(properties: PropertiesTable<NelePropertyItem>): NlComponent? {
    val property = properties.first ?: return null
    val firstParent = property.components.firstOrNull()?.parent ?: return null
    return if (property.components.all { it.parent?.tagName == firstParent.tagName }) firstParent else null
  }

  private fun setupCustomPanel(properties: PropertiesTable<NelePropertyItem>): JPanel? {
    val parent = getParentComponent(properties) ?: return null
    val customPanelKey = getCustomPanelKey(parent)
    val panel = cachedCustomPanels[customPanelKey] ?: createCustomPanel(parent, customPanelKey)
    if (panel == DummyCustomPanel.INSTANCE) return null

    val property = properties.first ?: return null
    val component = property.components.singleOrNull() ?: return null
    panel.useComponent(component)
    return panel.panel
  }

  private fun getCustomPanelKey(parent: NlComponent): String {
    val tagName = parent.tagName
    return if (tagName != VIEW_MERGE) tagName else parent.getAttribute(TOOLS_URI, ATTR_PARENT_TAG) ?: tagName
  }

  private fun createCustomPanel(parent: NlComponent, customPanelKey: String): CustomPanel {
    val handler = viewHandlerManager.getHandler(parent)
    val panel = handler?.layoutCustomPanel ?: DummyCustomPanel.INSTANCE
    cachedCustomPanels[customPanelKey] = panel
    return panel
  }
}
