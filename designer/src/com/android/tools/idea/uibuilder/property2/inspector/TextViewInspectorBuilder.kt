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
import com.android.tools.idea.uibuilder.property2.NeleFlagsPropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.model.ToggleButtonPropertyEditorModel
import com.android.tools.idea.uibuilder.property2.model.HorizontalEditorPanelModel
import com.android.tools.idea.uibuilder.property2.ui.HorizontalEditorPanel
import com.android.tools.idea.uibuilder.property2.ui.ToggleButtonPropertyEditor
import icons.StudioIcons.LayoutEditor.Properties.*
import javax.swing.Icon
import javax.swing.JComponent

/**
 * An [InspectorBuilder] for all widgets that are based on a TextView.
 *
 * The attributes for a TextView is shown only if all the [REQUIRED_PROPERTIES]
 * are available. The controls for [ATTR_TEXT_APPEARANCE] are displayed in a
 * collapsible section.
 *
 * There are 2 attributes that may or may not be present:
 *     [ATTR_FONT_FAMILY] and [ATTR_TEXT_ALIGNMENT]
 * They are present if the minSdkVersion is high enough or if AppCompat is used.
 */
class TextViewInspectorBuilder(private val editorProvider: EditorProvider<NelePropertyItem>, private val formModel: FormModel) :
    InspectorBuilder<NelePropertyItem> {

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
    addComponent(inspector, properties[ANDROID_URI, ATTR_TEXT_COLOR], textAppearanceLabel)
    addTextStyle(inspector, properties, textAppearanceLabel)
    addAlignment(inspector, properties, textAppearanceLabel)
  }

  private fun addComponent(inspector: InspectorPanel, property: NelePropertyItem, group: InspectorLineModel): InspectorLineModel {
    val line = inspector.addComponent(editorProvider.provide(property))
    group.addChild(line)
    return line
  }

  private fun addTextStyle(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>, group: InspectorLineModel) {
    val textStyle = properties.getOrNull(ANDROID_URI, ATTR_TEXT_STYLE) as? NeleFlagsPropertyItem ?: return
    val allCaps = properties.getOrNull(ANDROID_URI, ATTR_TEXT_ALL_CAPS) ?: return
    val bold = textStyle.flag(TextStyle.VALUE_BOLD)
    val italic = textStyle.flag(TextStyle.VALUE_ITALIC)
    val model = HorizontalEditorPanelModel(textStyle, formModel)
    val panel = HorizontalEditorPanel(model)
    val line = inspector.addComponent(model, panel)
    panel.add(createIconEditor(line, bold, "Bold", TEXT_STYLE_BOLD, "true", "false"))
    panel.add(createIconEditor(line, italic, "Italics", TEXT_STYLE_ITALIC, "true", "false"))
    panel.add(createIconEditor(line, allCaps, "All Caps", TEXT_STYLE_UPPERCASE, "true", "false"))
    group.addChild(line)
  }

  private fun addAlignment(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>, group: InspectorLineModel) {
    val alignment = properties.getOrNull(ANDROID_URI, ATTR_TEXT_ALIGNMENT) ?: return
    val model = HorizontalEditorPanelModel(alignment, formModel)
    val panel = HorizontalEditorPanel(model)
    val line = inspector.addComponent(model, panel)
    panel.add(createIconEditor(line, alignment, "Align Start of View", TEXT_ALIGN_LAYOUT_LEFT, TextAlignment.VIEW_START))
    panel.add(createIconEditor(line, alignment, "Align Start of Text", TEXT_ALIGN_LEFT, TextAlignment.TEXT_START))
    panel.add(createIconEditor(line, alignment, "Align Center", TEXT_ALIGN_CENTER, TextAlignment.CENTER))
    panel.add(createIconEditor(line, alignment, "Align End of Text", TEXT_ALIGN_RIGHT, TextAlignment.TEXT_END))
    panel.add(createIconEditor(line, alignment, "Align End of View", TEXT_ALIGN_LAYOUT_RIGHT, TextAlignment.VIEW_END))
    group.addChild(line)
  }

  private fun createIconEditor(
    line: InspectorLineModel,
    property: PropertyItem,
    description: String,
    icon: Icon,
    trueValue: String,
    falseValue: String = ""
  ): Pair<PropertyEditorModel, JComponent> {
    val model = ToggleButtonPropertyEditorModel(description, icon, trueValue, falseValue, property, formModel)
    val editor = ToggleButtonPropertyEditor(model)
    model.line = line
    return model to editor
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
