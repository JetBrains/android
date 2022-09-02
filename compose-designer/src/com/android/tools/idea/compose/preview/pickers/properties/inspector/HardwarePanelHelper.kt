/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.pickers.properties.inspector

import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_CHIN_SIZE
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DENSITY
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DIMENSIONS
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DIM_UNIT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_HEIGHT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_IS_ROUND
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_ORIENTATION
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_WIDTH
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyItem
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.panel.impl.ui.InspectorLayoutManager
import com.android.tools.property.panel.impl.ui.Placement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/** Object to get a class-named Logger. */
private object HardwarePanelHelper

private val LOG: Logger
  get() = Logger.getInstance(HardwarePanelHelper.javaClass)

/**
 * Generates the UI for the Hardware section in the @Preview picker. See
 * [PreviewPropertiesInspectorBuilder].
 */
internal fun addHardwareView(
  inspector: InspectorPanel,
  properties: Map<String, PsiPropertyItem>,
  editorProvider: EditorProvider<PsiPropertyItem>
) {
  val panelBuilder = HardwarePanelBuilder()
  val editors = mutableListOf<PropertyEditorModel>()

  /**
   * Adds a new line on the [HardwarePanelBuilder] for the given [propertyName] with its
   * corresponding editor.
   */
  fun addSinglePropertyLine(propertyName: String) {
    val property = properties[propertyName]
    if (property == null) {
      LOG.warn("No property of name: $propertyName")
      return
    }
    panelBuilder.addLine(propertyName, editorProvider.createEditor(property, editors))
  }

  addSinglePropertyLine(PARAMETER_HARDWARE_DEVICE)

  // The Dimensions parameter actually uses 3 other parameters: width, height, dimensionUnit.
  panelBuilder.addLine(
    PARAMETER_HARDWARE_DIMENSIONS,
    createDimensionLine(properties, editorProvider, editors)
  )

  addSinglePropertyLine(PARAMETER_HARDWARE_DENSITY)

  addSinglePropertyLine(PARAMETER_HARDWARE_ORIENTATION)

  if (StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.get()) {
    addSinglePropertyLine(PARAMETER_HARDWARE_IS_ROUND)

    addSinglePropertyLine(PARAMETER_HARDWARE_CHIN_SIZE)
  }

  inspector.addComponent(panelBuilder.build()).addValueChangedListener {
    // Refresh the added editors on value changes
    editors.forEach { it.refresh() }
  }
}

private fun createDimensionLine(
  properties: Map<String, PsiPropertyItem>,
  editorProvider: EditorProvider<PsiPropertyItem>,
  editors: MutableList<PropertyEditorModel>
): JPanel {
  /** The added [component] will shrink horizontally to fit its content */
  fun JPanel.addShrink(component: Component, gbc: GridBagConstraints) {
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.weightx = 0.0
    add(component, gbc)
  }

  /**
   * The added [component] will expand horizontally proportionally to other components added with
   * this method.
   */
  fun JPanel.addExpand(component: Component, gbc: GridBagConstraints) {
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.weightx = 1.0
    add(component, gbc)
  }

  val dimensionLine =
    JPanel(GridBagLayout()).apply {
      isOpaque = false
      val widthProperty = properties[PARAMETER_HARDWARE_WIDTH]!!
      val heightProperty = properties[PARAMETER_HARDWARE_HEIGHT]!!
      val unitProperty = properties[PARAMETER_HARDWARE_DIM_UNIT]!!
      val gbc = GridBagConstraints()
      gbc.gridwidth = 4
      addExpand(editorProvider.createEditor(widthProperty, editors), gbc)

      addShrink(JLabel("x"), gbc)

      addExpand(editorProvider.createEditor(heightProperty, editors), gbc)

      addShrink(
        editorProvider.createEditor(unitProperty, editors).also { component ->
          component.preferredSize = Dimension(JBUI.scale(52), preferredSize.height)
          component.minimumSize = Dimension(JBUI.scale(52), minimumSize.height)
        },
        gbc
      )
    }
  return dimensionLine
}

private fun EditorProvider<PsiPropertyItem>.createEditor(
  property: PsiPropertyItem,
  existing: MutableList<PropertyEditorModel>
): JComponent {
  val editorPair = createEditor(property)
  existing.add(editorPair.first)
  // Set the preferred size, to avoid layout managers from changing it, which may cause popups close
  // unexpectedly
  editorPair.second.preferredSize = editorPair.second.preferredSize
  return editorPair.second
}

private class HardwarePanelBuilder {
  private val panel = JPanel(InspectorLayoutManager()).apply { isOpaque = false }

  fun addLine(name: String, component: JComponent) {
    val label = JLabel(name)
    label.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    label.border = JBUI.Borders.emptyLeft(8)
    panel.add(label, Placement.LEFT)
    panel.add(component, Placement.RIGHT)
  }

  fun build() = panel
}
