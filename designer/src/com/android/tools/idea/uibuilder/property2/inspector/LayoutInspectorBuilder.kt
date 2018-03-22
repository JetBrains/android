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

import com.android.SdkConstants.*
import com.android.tools.idea.common.property2.api.*
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

    inspector.addSeparator()
    val titleModel = inspector.addExpandableTitle("layout")
    if (custom != null) {
      val customLine = inspector.addComponent(custom)
      titleModel.addChild(customLine)
    }

    for (propertyName in attributes) {
      // TODO: Handle other namespaces
      val property = properties.getOrNull(ANDROID_URI, propertyName)
      if (property != null) {
        val line = inspector.addEditor(editorProvider(property))
        titleModel.addChild(line)
      }
    }
  }

  private fun isApplicable(attributes: List<String>, custom: JPanel?, properties: PropertiesTable<NelePropertyItem>): Boolean {
    if (custom != null) return true
    return attributes.any { properties.getOrNull(ANDROID_URI, it) != null }
  }

  private fun getLayoutAttributes(properties: PropertiesTable<NelePropertyItem>): List<String> {
    val attributes = mutableListOf(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT)
    val parentTagName = getParentTagName(properties) ?: return attributes
    val handler = viewHandlerManager.getHandler(parentTagName) ?: return attributes
    attributes.addAll(handler.layoutInspectorProperties)
    return attributes
  }

  private fun getParentTagName(properties: PropertiesTable<NelePropertyItem>): String? {
    val property = properties.first ?: return null
    val parentTagName = property.components.firstOrNull()?.parent?.tagName ?: return null
    return if (property.components.all { it.parent?.tagName == parentTagName }) parentTagName else null
  }

  private fun setupCustomPanel(properties: PropertiesTable<NelePropertyItem>): JPanel? {
    val parentTagName = getParentTagName(properties) ?: return null
    val panel = cachedCustomPanels[parentTagName] ?: createCustomPanel(parentTagName)
    if (panel == DummyCustomPanel.INSTANCE) return null

    val property = properties.first ?: return null
    val component = property.components.singleOrNull() ?: return null
    panel.useComponent(component)
    return panel.panel
  }

  private fun createCustomPanel(parentTagName: String): CustomPanel {
    val handler = viewHandlerManager.getHandler(parentTagName)
    val panel = handler?.layoutCustomPanel ?: DummyCustomPanel.INSTANCE
    cachedCustomPanels[parentTagName] = panel
    return panel
  }
}
