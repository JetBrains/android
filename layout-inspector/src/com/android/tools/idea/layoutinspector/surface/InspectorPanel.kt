/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.surface

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.sampledata.chromeSampleData
import com.android.tools.idea.layoutinspector.sampledata.videosSampleData
import com.android.tools.idea.layoutinspector.sampledata.youtubeSampleData
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * Main (center) panel of the layout inspector
 */
class InspectorPanel : JPanel(BorderLayout()) {
  private val showBordersCheckBox = JBCheckBox("Show borders")
  private val sampleDataSelector = ComboBox<Pair<String, InspectorModel>>(
    arrayOf("Chrome" to chromeSampleData, "Videos" to videosSampleData, "Youtube" to youtubeSampleData))

  init {
    val deviceViewPanel = DeviceViewPanel()
    showBordersCheckBox.addActionListener {
      deviceViewPanel.showBorders = showBordersCheckBox.isSelected
      repaint()
    }
    sampleDataSelector.addActionListener {
      deviceViewPanel.data = sampleDataSelector.getItemAt(sampleDataSelector.selectedIndex).second
      repaint()
    }
    showBordersCheckBox.preferredSize = Dimension(50, 20)
    showBordersCheckBox.size = Dimension(50, 20)
    showBordersCheckBox.minimumSize = Dimension(50, 20)
    val renderer = JLabel()
    sampleDataSelector.renderer = ListCellRenderer<Pair<String, InspectorModel>> { _, value, _, _, _ ->
      renderer.apply { text = value.first }
    }
    val topPanel = JPanel(BorderLayout())
    topPanel.add(sampleDataSelector, BorderLayout.WEST)
    topPanel.add(showBordersCheckBox, BorderLayout.CENTER)
    add(topPanel, BorderLayout.NORTH)
    add(deviceViewPanel, BorderLayout.CENTER)
  }
}

