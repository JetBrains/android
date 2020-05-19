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
package com.android.tools.idea.uibuilder.property2.ui

import com.android.SdkConstants
import com.android.tools.adtui.common.lines3d
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.android.tools.property.panel.api.PropertiesTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.border.EmptyBorder

/**
 * Custom panel to support direct editing of transforms (rotation, etc.)
 */
class TransformsPanel(properties: PropertiesTable<NelePropertyItem>) : JPanel(BorderLayout()) {

  private val PANEL_WIDTH = 200
  private val PANEL_HEIGHT = PANEL_WIDTH + 80

  private var processingChange: Boolean = false

  private val propertyRotationX = properties.getOrNull(SdkConstants.ANDROID_URI, "rotationX")
  private val propertyRotationY = properties.getOrNull(SdkConstants.ANDROID_URI, "rotationY")
  private val propertyRotationZ = properties.getOrNull(SdkConstants.ANDROID_URI, "rotation")

  private val virtualButton = VirtualWidget()
  val rotationX = JSlider(-360, 360, 0)
  val rotationY = JSlider(-360, 360, 0)
  val rotationZ = JSlider(-360, 360, 0)

  init {
    val panelSize = JBUI.size(PANEL_WIDTH, PANEL_HEIGHT)
    preferredSize = panelSize

    val control = JPanel()

    control.layout = BoxLayout(control, BoxLayout.PAGE_AXIS)
    control.border = EmptyBorder(10, 10, 10, 10)

    add(virtualButton)
    add(control, BorderLayout.SOUTH)

    val rotationXLabel = JLabel("  X")
    val rotationYLabel = JLabel("  Y")
    val rotationZLabel = JLabel("  Z")
    val rotationXValue = JLabel("0")
    val rotationYValue = JLabel("0")
    val rotationZValue = JLabel("0")

    var mouseClickX = object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 2) {
          rotationX.value = 0
        }
      }
    }
    var mouseClickY = object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 2) {
          rotationY.value = 0
        }
      }
    }
    var mouseClickZ = object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 2) {
          rotationZ.value = 0
        }
      }
    }

    rotationX.addMouseListener(mouseClickX)
    rotationY.addMouseListener(mouseClickY)
    rotationZ.addMouseListener(mouseClickZ)
    rotationXValue.addMouseListener(mouseClickX)
    rotationYValue.addMouseListener(mouseClickY)
    rotationZValue.addMouseListener(mouseClickZ)

    var columnSize = Dimension(40, 20)
    rotationXLabel.preferredSize = columnSize
    rotationYLabel.preferredSize = columnSize
    rotationZLabel.preferredSize = columnSize
    rotationXValue.preferredSize = columnSize
    rotationYValue.preferredSize = columnSize
    rotationZValue.preferredSize = columnSize

    val controlRotationX = JPanel()
    controlRotationX.layout = BoxLayout(controlRotationX, BoxLayout.LINE_AXIS)
    controlRotationX.add(rotationXLabel)
    controlRotationX.add(rotationX)
    controlRotationX.add(rotationXValue)

    val controlRotationY = JPanel()
    controlRotationY.layout = BoxLayout(controlRotationY, BoxLayout.LINE_AXIS)
    controlRotationY.add(rotationYLabel)
    controlRotationY.add(rotationY)
    controlRotationY.add(rotationYValue)

    val controlRotationZ = JPanel()
    controlRotationZ.layout = BoxLayout(controlRotationZ, BoxLayout.LINE_AXIS)
    controlRotationZ.add(rotationZLabel)
    controlRotationZ.add(rotationZ)
    controlRotationZ.add(rotationZValue)

    var controlLabel = JPanel()
    controlLabel.layout = BoxLayout(controlLabel, BoxLayout.LINE_AXIS)
    controlLabel.add(JLabel("Rotation"))
    controlLabel.add(Box.createHorizontalGlue())

    control.add(controlLabel)
    control.add(controlRotationX)
    control.add(controlRotationY)
    control.add(controlRotationZ)

    control.background = secondaryPanelBackground
    controlLabel.background = secondaryPanelBackground
    rotationX.background = secondaryPanelBackground
    rotationY.background = secondaryPanelBackground
    rotationZ.background = secondaryPanelBackground
    controlRotationX.background = secondaryPanelBackground
    controlRotationY.background = secondaryPanelBackground
    controlRotationZ.background = secondaryPanelBackground
    virtualButton.background = secondaryPanelBackground
    virtualButton.foreground = lines3d

    rotationX.addChangeListener {
      processingChange = true
      var value = rotationX.value.toString()
      rotationXValue.text = value
      virtualButton.setRotateX(rotationX.value.toDouble())
      if (!rotationX.valueIsAdjusting) {
        propertyRotationX?.value = value
      }
      processingChange = false
    }
    rotationY.addChangeListener {
      processingChange = true
      var value = rotationY.value.toString()
      rotationYValue.text = value
      virtualButton.setRotateY(rotationY.value.toDouble())
      if (!rotationY.valueIsAdjusting) {
        propertyRotationY?.value = value
      }
      processingChange = false
    }
    rotationZ.addChangeListener {
      processingChange = true
      var value = rotationZ.value.toString()
      rotationZValue.text = value
      virtualButton.setRotate(rotationZ.value.toDouble())
      if (!rotationZ.valueIsAdjusting) {
        propertyRotationZ?.value = value
      }
      processingChange = false
    }

    var listener = object: PropertiesModelListener<NelePropertyItem> {
      override fun propertiesGenerated(model: PropertiesModel<NelePropertyItem>) {
        updateFromValues()
      }
      override fun propertyValuesChanged(model: PropertiesModel<NelePropertyItem>) {
        updateFromValues()
      }
    }

    propertyRotationX?.model?.addListener(listener)
    propertyRotationY?.model?.addListener(listener)
    propertyRotationZ?.model?.addListener(listener)

    updateFromValues()
  }

  private fun updateFromValues() {
    if (processingChange) {
      return
    }
    propertyRotationX?.value?.toDouble()?.let {
      virtualButton.setRotateX(it)
      rotationX.value = it.toInt()
    }
    propertyRotationY?.value?.toDouble()?.let {
      virtualButton.setRotateY(it)
      rotationY.value = it.toInt()
    }
    propertyRotationZ?.value?.toDouble()?.let {
      rotationZ.value = it.toInt()
      virtualButton.setRotate(it)
    }
  }

}