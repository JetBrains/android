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

import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.property.NlFlagsPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.model.HorizontalEditorPanelModel
import com.android.tools.idea.uibuilder.property.model.ToggleButtonPropertyEditorModel
import com.android.tools.idea.uibuilder.property.ui.HorizontalEditorPanel
import com.android.tools.idea.uibuilder.property.ui.ToggleButtonPropertyEditor
import com.android.tools.property.panel.api.*
import icons.StudioIcons.LayoutEditor.Properties.*
import javax.swing.Icon
import javax.swing.JComponent

/**
 * An [InspectorBuilder] for all widgets that are based on a TextView.
 *
 * The attributes for a TextView is shown only if all the [REQUIRED_PROPERTIES] are available. The
 * controls for [ATTR_TEXT_APPEARANCE] are displayed in a collapsible section.
 *
 * There are 2 attributes that may or may not be present: [ATTR_FONT_FAMILY] and
 * [ATTR_TEXT_ALIGNMENT] They are present if the minSdkVersion is high enough or if AppCompat is
 * used.
 */
class TextViewInspectorBuilder(private val editorProvider: EditorProvider<NlPropertyItem>) {

  fun attachToInspector(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
    getTitleLine: () -> InspectorLineModel,
  ) {
    if (!isApplicable(properties)) return

    val titleLine = getTitleLine()
    addEditor(inspector, properties[ANDROID_URI, ATTR_TEXT], titleLine)
    addEditor(
      inspector,
      properties.getOrNull(TOOLS_URI, ATTR_TEXT)
        ?: properties[ANDROID_URI, ATTR_TEXT].designProperty,
      titleLine,
    )
    addEditor(inspector, properties[ANDROID_URI, ATTR_CONTENT_DESCRIPTION], titleLine)

    val textAppearanceLabel =
      addEditor(inspector, properties[ANDROID_URI, ATTR_TEXT_APPEARANCE], titleLine)
    textAppearanceLabel.makeExpandable(initiallyExpanded = false)

    val fontFamily =
      properties.getOrNull(ANDROID_URI, ATTR_FONT_FAMILY)
        ?: properties.getOrNull(AUTO_URI, ATTR_FONT_FAMILY)
    if (fontFamily != null) {
      addEditor(inspector, fontFamily, textAppearanceLabel)
    }
    addEditor(inspector, properties[ANDROID_URI, ATTR_TYPEFACE], textAppearanceLabel)
    addEditor(inspector, properties[ANDROID_URI, ATTR_TEXT_SIZE], textAppearanceLabel)
    addEditor(inspector, properties[ANDROID_URI, ATTR_LINE_SPACING_EXTRA], textAppearanceLabel)
    addEditor(inspector, properties[ANDROID_URI, ATTR_TEXT_COLOR], textAppearanceLabel)
    addTextStyle(inspector, properties, textAppearanceLabel)
    addAlignment(inspector, properties, textAppearanceLabel)
  }

  private fun addEditor(
    inspector: InspectorPanel,
    property: NlPropertyItem,
    group: InspectorLineModel,
  ): InspectorLineModel {
    return inspector.addEditor(editorProvider.createEditor(property), group)
  }

  private fun addTextStyle(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
    group: InspectorLineModel,
  ) {
    val textStyle =
      properties.getOrNull(ANDROID_URI, ATTR_TEXT_STYLE) as? NlFlagsPropertyItem ?: return
    val allCaps = properties.getOrNull(ANDROID_URI, ATTR_TEXT_ALL_CAPS) ?: return
    val bold = textStyle.flag(TextStyle.VALUE_BOLD)
    val italic = textStyle.flag(TextStyle.VALUE_ITALIC)
    val model = HorizontalEditorPanelModel(textStyle)
    val panel = HorizontalEditorPanel(model)
    val line = inspector.addCustomEditor(model, panel, group)
    panel.add(createIconEditor(line, bold, "Bold", TEXT_STYLE_BOLD, "true", "false"))
    panel.add(createIconEditor(line, italic, "Italics", TEXT_STYLE_ITALIC, "true", "false"))
    panel.add(createIconEditor(line, allCaps, "All Caps", TEXT_STYLE_UPPERCASE, "true", "false"))
  }

  private fun addAlignment(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
    group: InspectorLineModel,
  ) {
    val alignment = properties.getOrNull(ANDROID_URI, ATTR_TEXT_ALIGNMENT) ?: return
    val model = HorizontalEditorPanelModel(alignment)
    val panel = HorizontalEditorPanel(model)
    val line = inspector.addCustomEditor(model, panel, group)
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Align Start of View",
        TEXT_ALIGN_LAYOUT_LEFT,
        TextAlignment.VIEW_START,
      )
    )
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Align Start of Text",
        TEXT_ALIGN_LEFT,
        TextAlignment.TEXT_START,
      )
    )
    panel.add(
      createIconEditor(line, alignment, "Align Center", TEXT_ALIGN_CENTER, TextAlignment.CENTER)
    )
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Align End of Text",
        TEXT_ALIGN_RIGHT,
        TextAlignment.TEXT_END,
      )
    )
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Align End of View",
        TEXT_ALIGN_LAYOUT_RIGHT,
        TextAlignment.VIEW_END,
      )
    )
  }

  private fun createIconEditor(
    line: InspectorLineModel,
    property: NlPropertyItem,
    description: String,
    icon: Icon,
    trueValue: String,
    falseValue: String = "",
  ): Pair<PropertyEditorModel, JComponent> {
    val model = ToggleButtonPropertyEditorModel(description, icon, trueValue, falseValue, property)
    val editor = ToggleButtonPropertyEditor(model)
    model.lineModel = line
    return model to editor
  }

  companion object {
    val REQUIRED_PROPERTIES =
      listOf(
        ATTR_TEXT,
        ATTR_CONTENT_DESCRIPTION,
        ATTR_TEXT_APPEARANCE,
        ATTR_TYPEFACE,
        ATTR_TEXT_SIZE,
        ATTR_LINE_SPACING_EXTRA,
        ATTR_TEXT_STYLE,
        ATTR_TEXT_ALL_CAPS,
        ATTR_TEXT_COLOR,
      )

    fun isApplicable(properties: PropertiesTable<NlPropertyItem>): Boolean {
      return properties.getByNamespace(ANDROID_URI).keys.containsAll(REQUIRED_PROPERTIES)
    }
  }
}
