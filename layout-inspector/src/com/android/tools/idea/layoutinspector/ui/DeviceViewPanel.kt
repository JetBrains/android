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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.InspectorView
import com.android.tools.idea.layoutinspector.sampledata.chromeSampleData
import com.android.tools.idea.layoutinspector.sampledata.videosSampleData
import com.android.tools.idea.layoutinspector.sampledata.youtubeSampleData
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import kotlin.math.cos
import kotlin.math.sin

private const val LAYER_SPACING = 150

/**
 * Panel that shows the device screen in the layout inspector.
 */
class DeviceViewPanel(private val layoutInspector: LayoutInspector) : JPanel(BorderLayout()) {
  private val showBordersCheckBox = JBCheckBox("Show borders")
  private val sampleDataSelector = ComboBox<Pair<String, InspectorModel>>(
    arrayOf("Chrome" to chromeSampleData, "Videos" to videosSampleData, "Youtube" to youtubeSampleData))

  private val HQ_RENDERING_HINTS = mapOf(
    RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
    RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
    RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BILINEAR,
    RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE
  )

  private var angle = 0.0

  init {
    layoutInspector.modelChangeListeners.add(this::modelChanged)
    layoutInspector.layoutInspectorModel.selectionListeners.add(this::selectionChanged)

    addMouseWheelListener { e ->
      angle += e.preciseWheelRotation * 0.01
      repaint()
    }

    showBordersCheckBox.addActionListener {
      repaint()
    }
    sampleDataSelector.addActionListener {
      layoutInspector.layoutInspectorModel = sampleDataSelector.getItemAt(sampleDataSelector.selectedIndex).second
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
    add(object: JPanel() {
      override fun paint(g: Graphics) {
        (g as? Graphics2D)?.setRenderingHints(HQ_RENDERING_HINTS)
        g.color = Color.LIGHT_GRAY
        g.fillRect(0, 0, width, height)
        (g as? Graphics2D)?.translate(20, 0)
        (g as? Graphics2D)?.scale(.5, .5)
        draw(layoutInspector.layoutInspectorModel.root, g, 0)
      }
    }, BorderLayout.CENTER)
  }

  private fun draw(view: InspectorView, g: Graphics, depth: Int) {
    val g2 = g.create() as Graphics2D
    val bufferedImage = view.image

    g2.translate((sin(angle) * depth * LAYER_SPACING).toInt(), 0)
    g2.scale(cos(angle), 1.0)
    if (bufferedImage != null) {
      g2.drawImage(bufferedImage, view.x, view.y, null)
    }
    if (showBordersCheckBox.isSelected) {
      if (view == layoutInspector.layoutInspectorModel.selection) {
        g2.color = Color.RED
        g2.stroke = BasicStroke(3f)
      }
      else {
        g2.color = Color.BLUE
        g2.stroke = BasicStroke(1f)
      }
      g2.drawRect(view.x, view.y, view.width, view.height)
      g2.color = Color.BLACK
      g2.font = g2.font.deriveFont(20f)
      g2.drawString(view.type, view.x + 5, view.y + 25)
    }
    view.children.forEach { draw(it, g, depth + 1) }
  }

  private fun modelChanged(old: InspectorModel, new: InspectorModel) {
    old.selectionListeners.remove(this::selectionChanged)
    new.selectionListeners.add(this::selectionChanged)
    repaint()
  }

  private fun selectionChanged(old: InspectorView?, new: InspectorView?) {
    repaint()
  }
}
