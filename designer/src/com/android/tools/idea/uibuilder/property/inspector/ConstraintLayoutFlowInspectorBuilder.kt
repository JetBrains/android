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

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.model.isOrHasSuperclass
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.model.HorizontalEditorPanelModel
import com.android.tools.idea.uibuilder.property.model.ToggleButtonPropertyEditorModel
import com.android.tools.idea.uibuilder.property.ui.HorizontalEditorPanel
import com.android.tools.idea.uibuilder.property.ui.ToggleButtonPropertyEditor
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.api.PropertyEditorModel
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder

/** Provides a custom set of editors for ConstraintLayout's flow in the Property Panel. */
class ConstraintLayoutFlowInspectorBuilder(
  private val editorProvider: EditorProvider<NlPropertyItem>
) {

  fun attachToInspector(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
    getTitleLine: () -> InspectorLineModel,
  ) {
    if (!isApplicable(properties)) return

    var titleLine = getTitleLine()
    addEditor(
      inspector,
      properties[SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION],
      titleLine,
    )
    addEditor(
      inspector,
      properties[SdkConstants.AUTO_URI, SdkConstants.ATTR_FLOW_WRAP_MODE],
      titleLine,
    )
    addEditor(
      inspector,
      properties[SdkConstants.AUTO_URI, SdkConstants.ATTR_FLOW_MAX_ELEMENTS_WRAP],
      titleLine,
    )
    inspector.addComponent(MySeparator(), titleLine)
    addSubtitle(inspector, "Horizontal", titleLine)
    addHorizontalAlignment(inspector, properties, titleLine)
    addEditor(
      inspector,
      properties[SdkConstants.AUTO_URI, SdkConstants.ATTR_FLOW_HORIZONTAL_GAP],
      titleLine,
    )
    addEditor(
      inspector,
      properties[SdkConstants.AUTO_URI, SdkConstants.ATTR_FLOW_HORIZONTAL_BIAS],
      titleLine,
    )
    addHorizontalStyle(inspector, properties, titleLine)
    inspector.addComponent(MySeparator(), titleLine)
    addSubtitle(inspector, "Vertical", titleLine)
    addVerticalAlignment(inspector, properties, titleLine)
    addEditor(
      inspector,
      properties[SdkConstants.AUTO_URI, SdkConstants.ATTR_FLOW_VERTICAL_GAP],
      titleLine,
    )
    addEditor(
      inspector,
      properties[SdkConstants.AUTO_URI, SdkConstants.ATTR_FLOW_VERTICAL_BIAS],
      titleLine,
    )
    addVerticalStyle(inspector, properties, titleLine)
    inspector.addComponent(MySeparator(), titleLine)
  }

  private fun addSubtitle(inspector: InspectorPanel, s: String, titleLine: InspectorLineModel) {
    var component = JLabel(s)
    component.border = EmptyBorder(8, 8, 8, 8)
    inspector.addComponent(component, titleLine)
  }

  private fun addHorizontalAlignment(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
    group: InspectorLineModel,
  ) {
    val alignment =
      properties.getOrNull(SdkConstants.AUTO_URI, SdkConstants.ATTR_FLOW_HORIZONTAL_ALIGN) ?: return
    val model = HorizontalEditorPanelModel(alignment)
    val panel = HorizontalEditorPanel(model)
    val line = inspector.addCustomEditor(model, panel, group)
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Align Start",
        StudioIcons.LayoutEditor.Toolbar.LEFT_ALIGNED,
        SdkConstants.FlowAlignment.START,
      )
    )
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Align Start",
        StudioIcons.LayoutEditor.Toolbar.HORIZONTAL_CENTER_ALIGNED,
        SdkConstants.FlowAlignment.CENTER,
      )
    )
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Align End",
        StudioIcons.LayoutEditor.Toolbar.RIGHT_ALIGNED,
        SdkConstants.FlowAlignment.END,
      )
    )
  }

  private fun addVerticalAlignment(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
    group: InspectorLineModel,
  ) {
    val alignment =
      properties.getOrNull(SdkConstants.AUTO_URI, SdkConstants.ATTR_FLOW_VERTICAL_ALIGN) ?: return
    val model = HorizontalEditorPanelModel(alignment)
    val panel = HorizontalEditorPanel(model)
    val line = inspector.addCustomEditor(model, panel, group)
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Align Top",
        StudioIcons.LayoutEditor.Toolbar.TOP_ALIGNED,
        SdkConstants.FlowAlignment.TOP,
      )
    )
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Align Center",
        StudioIcons.LayoutEditor.Toolbar.VERTICAL_CENTER_ALIGNED,
        SdkConstants.FlowAlignment.CENTER,
      )
    )
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Align Bottom",
        StudioIcons.LayoutEditor.Toolbar.BOTTOM_ALIGNED,
        SdkConstants.FlowAlignment.BOTTOM,
      )
    )
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Align Baseline",
        StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED,
        SdkConstants.FlowAlignment.BASELINE,
      )
    )
  }

  private fun addHorizontalStyle(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
    group: InspectorLineModel,
  ) {
    val alignment =
      properties.getOrNull(SdkConstants.AUTO_URI, SdkConstants.ATTR_FLOW_HORIZONTAL_STYLE) ?: return
    val model = HorizontalEditorPanelModel(alignment)
    val panel = HorizontalEditorPanel(model)
    val line = inspector.addCustomEditor(model, panel, group)
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Spread",
        StudioIcons.LayoutEditor.Properties.SPREAD_HORIZONTAL,
        SdkConstants.FlowStyle.SPREAD,
      )
    )
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Spread Inside",
        StudioIcons.LayoutEditor.Properties.SPREAD_INSIDE_HORIZONTAL,
        SdkConstants.FlowStyle.SPREAD_INSIDE,
      )
    )
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Packed",
        StudioIcons.LayoutEditor.Properties.PACKED_HORIZONTAL,
        SdkConstants.FlowStyle.PACKED,
      )
    )
  }

  private fun addVerticalStyle(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
    group: InspectorLineModel,
  ) {
    val alignment =
      properties.getOrNull(SdkConstants.AUTO_URI, SdkConstants.ATTR_FLOW_VERTICAL_STYLE) ?: return
    val model = HorizontalEditorPanelModel(alignment)
    val panel = HorizontalEditorPanel(model)
    val line = inspector.addCustomEditor(model, panel, group)
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Spread",
        StudioIcons.LayoutEditor.Properties.SPREAD_VERTICAL,
        SdkConstants.FlowStyle.SPREAD,
      )
    )
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Spread Inside",
        StudioIcons.LayoutEditor.Properties.SPREAD_INSIDE_VERTICAL,
        SdkConstants.FlowStyle.SPREAD_INSIDE,
      )
    )
    panel.add(
      createIconEditor(
        line,
        alignment,
        "Packed",
        StudioIcons.LayoutEditor.Properties.PACKED_VERTICAL,
        SdkConstants.FlowStyle.PACKED,
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

  private fun addEditor(
    inspector: InspectorPanel,
    property: NlPropertyItem,
    group: InspectorLineModel,
  ): InspectorLineModel {
    return inspector.addEditor(editorProvider.createEditor(property), group)
  }

  private class MySeparator internal constructor() : AdtSecondaryPanel(BorderLayout()) {
    override fun updateUI() {
      super.updateUI()
      border = JBUI.Borders.empty(4)
    }

    init {
      add(JSeparator(SwingConstants.HORIZONTAL), BorderLayout.CENTER)
    }
  }

  companion object {
    fun isApplicable(properties: PropertiesTable<NlPropertyItem>): Boolean {
      var components: List<NlComponent>? = properties.first?.components ?: return false
      if (components!!.isEmpty()) return false
      var component: NlComponent? = components?.get(0)
      return component!!.isOrHasSuperclass(AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_FLOW)
    }
  }
}
