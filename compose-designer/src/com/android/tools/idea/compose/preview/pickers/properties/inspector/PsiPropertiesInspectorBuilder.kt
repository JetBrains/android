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

import com.android.tools.idea.compose.preview.PARAMETER_GROUP
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DIM_UNIT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DENSITY
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_HEIGHT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_ORIENTATION
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_WIDTH
import com.android.tools.idea.compose.preview.PARAMETER_HEIGHT_DP
import com.android.tools.idea.compose.preview.PARAMETER_NAME
import com.android.tools.idea.compose.preview.PARAMETER_WIDTH_DP
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyItem
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator

internal class PsiPropertiesInspectorBuilder(
  private val editorProvider: EditorProvider<PsiPropertyItem>
) : InspectorBuilder<PsiPropertyItem> {
  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<PsiPropertyItem>) {
    val allProps = properties.values.associateBy { it.name }.toMutableMap()
    val previewProperties = mutableListOf<PsiPropertyItem>().apply {
      allProps.remove(PARAMETER_NAME)?.let { add(it) }
      allProps.remove(PARAMETER_GROUP)?.let { add(it) }
    }

    allProps.remove(PARAMETER_WIDTH_DP)
    allProps.remove(PARAMETER_HEIGHT_DP)

    val deviceProperties = mutableMapOf<String, PsiPropertyItem>().apply {
      allProps.remove(PARAMETER_HARDWARE_DEVICE)?.let { put(PARAMETER_HARDWARE_DEVICE, it) }
      allProps.remove(PARAMETER_HARDWARE_ORIENTATION)?.let { put(PARAMETER_HARDWARE_ORIENTATION, it) }
      allProps.remove(PARAMETER_HARDWARE_DENSITY)?.let { put(PARAMETER_HARDWARE_DENSITY, it) }
      allProps.remove(PARAMETER_HARDWARE_WIDTH)?.let { put(PARAMETER_HARDWARE_WIDTH, it) }
      allProps.remove(PARAMETER_HARDWARE_HEIGHT)?.let { put(PARAMETER_HARDWARE_HEIGHT, it) }
      allProps.remove(PARAMETER_HARDWARE_DIM_UNIT)?.let { put(PARAMETER_HARDWARE_DIM_UNIT, it) }
    }
    val remainingProperties = allProps.values

    // Main preview parameters
    previewProperties.forEach {
      inspector.addEditor(editorProvider.createEditor(it))
    }

    // Hardware parameters
    inspector.addSectionLabel("Hardware")
    addHardwareView(inspector, deviceProperties, editorProvider)

    // Display parameters
    inspector.addSectionLabel("Display")
    remainingProperties.forEach {
      inspector.addEditor(editorProvider.createEditor(it))
    }
  }
}

private fun InspectorPanel.addSectionLabel(display: String) {
  val separatorPanel = JPanel(GridBagLayout()).apply {
    val gbc = GridBagConstraints().apply {
      gridwidth = GridBagConstraints.REMAINDER
      fill = GridBagConstraints.HORIZONTAL
      weightx = 1.0
    }
    isOpaque = false
    add(JSeparator(), gbc)
  }
  val labelPanel = JPanel().apply {
    layout = BorderLayout()
    isOpaque = false
    val label = JLabel(display)
    label.border = JBUI.Borders.empty(8)
    label.font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL).deriveFont(Font.BOLD)
    add(label, BorderLayout.WEST)
    add(separatorPanel)
  }
  addComponent(labelPanel)
}