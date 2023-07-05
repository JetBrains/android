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
package com.android.tools.idea.uibuilder.property.ui

import com.android.SdkConstants
import com.android.tools.adtui.common.lines3d
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.android.tools.property.panel.api.PropertiesTable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.runReadAction
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.border.EmptyBorder
import org.jetbrains.annotations.VisibleForTesting

/** Custom panel to support direct editing of transforms (rotation, etc.) */
class TransformsPanel(
  private val model: NlPropertiesModel,
  properties: PropertiesTable<NlPropertyItem>
) : JPanel(BorderLayout()) {

  private val PANEL_WIDTH = 200
  private val PANEL_HEIGHT = PANEL_WIDTH + 80

  private var processingChange: Boolean = false
  private var processingModelUpdate: Boolean = false

  private val propertyRotationX = properties.getOrNull(SdkConstants.ANDROID_URI, "rotationX")
  private val propertyRotationY = properties.getOrNull(SdkConstants.ANDROID_URI, "rotationY")
  private val propertyRotationZ = properties.getOrNull(SdkConstants.ANDROID_URI, "rotation")

  private val virtualButton = VirtualWidget()
  val rotationX = JSlider(-360, 360, 0)
  val rotationY = JSlider(-360, 360, 0)
  val rotationZ = JSlider(-360, 360, 0)

  private var listLabels = ArrayList<JLabel>()
  private var listValueLabels = ArrayList<JLabel>()

  private val modelListener =
    object : PropertiesModelListener<NlPropertyItem> {
      override fun propertyValuesChanged(model: PropertiesModel<NlPropertyItem>) {
        updateFromValues()
      }
    }

  init {
    val panelSize = JBUI.size(PANEL_WIDTH, PANEL_HEIGHT)
    preferredSize = panelSize

    val control = JPanel()

    control.layout = BoxLayout(control, BoxLayout.PAGE_AXIS)
    control.border = EmptyBorder(10, 10, 10, 10)

    add(virtualButton)
    add(control, BorderLayout.SOUTH)

    val rotationXLabel = JLabel("x")
    val rotationYLabel = JLabel("y")
    val rotationZLabel = JLabel("z")
    val rotationXValue = JLabel("0")
    val rotationYValue = JLabel("0")
    val rotationZValue = JLabel("0")

    listLabels.add(rotationXLabel)
    listLabels.add(rotationYLabel)
    listLabels.add(rotationZLabel)
    listValueLabels.add(rotationXValue)
    listValueLabels.add(rotationYValue)
    listValueLabels.add(rotationZValue)

    val mouseClickX =
      object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (e.clickCount == 2) {
            rotationX.value = 0
          }
        }
      }
    val mouseClickY =
      object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (e.clickCount == 2) {
            rotationY.value = 0
          }
        }
      }
    val mouseClickZ =
      object : MouseAdapter() {
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

    val controlLabel = JPanel()
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
      val value = rotationX.value.toString()
      rotationXValue.text = value
      virtualButton.setRotateX(rotationX.value.toDouble())
      if (!rotationX.valueIsAdjusting && !processingModelUpdate) {
        writeValue(propertyRotationX, value)
      }
      processingChange = false
    }
    rotationY.addChangeListener {
      processingChange = true
      val value = rotationY.value.toString()
      rotationYValue.text = value
      virtualButton.setRotateY(rotationY.value.toDouble())
      if (!rotationY.valueIsAdjusting && !processingModelUpdate) {
        writeValue(propertyRotationY, value)
      }
      processingChange = false
    }
    rotationZ.addChangeListener {
      processingChange = true
      val value = rotationZ.value.toString()
      rotationZValue.text = value
      virtualButton.setRotate(rotationZ.value.toDouble())
      if (!rotationZ.valueIsAdjusting && !processingModelUpdate) {
        writeValue(propertyRotationZ, value)
      }
      processingChange = false
    }

    updateFromValues()
    updateUI()
  }

  override fun updateUI() {
    super.updateUI()
    if (listLabels != null) {
      val font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
      val indent = EmptyBorder(0, JBUI.scale(20), 0, 0)
      for (label in listLabels) {
        label.font = font
        label.border = indent
      }
      val placeholder = JLabel()
      placeholder.font = font
      placeholder.text = "XXXX"
      val columnSize = placeholder.preferredSize
      for (label in listValueLabels) {
        label.font = font
        label.preferredSize = columnSize
      }
    }
  }

  override fun addNotify() {
    super.addNotify()
    model.addListener(modelListener)
  }

  override fun removeNotify() {
    super.removeNotify()
    model.removeListener(modelListener)
  }

  private fun writeValue(property: NlPropertyItem?, value: String) {
    if (property == null) {
      return
    }
    val component = property.componentName
    var propertyValue: String? = value
    if (propertyValue == "0") {
      propertyValue = null // set to null as it's the default value
    }
    TransactionGuard.submitTransaction(
      property.model,
      Runnable {
        NlWriteCommandActionUtil.run(
          property.components,
          "Set $component.${property.name} to $propertyValue"
        ) {
          property.value = propertyValue
        }
      }
    )
  }

  private fun updateFromValues() {
    if (processingChange) {
      return
    }
    val application = ApplicationManager.getApplication()
    if (application.isReadAccessAllowed) {
      updateFromProperty()
    } else {
      runReadAction { updateFromProperty() }
    }
  }

  private fun updateFromProperty() {
    valueOf(propertyRotationX)?.let {
      processingModelUpdate = true
      virtualButton.setRotateX(it)
      rotationX.value = it.toInt()
      processingModelUpdate = false
    }
    valueOf(propertyRotationY)?.let {
      processingModelUpdate = true
      virtualButton.setRotateY(it)
      rotationY.value = it.toInt()
      processingModelUpdate = false
    }
    valueOf(propertyRotationZ)?.let {
      processingModelUpdate = true
      rotationZ.value = it.toInt()
      virtualButton.setRotate(it)
      processingModelUpdate = false
    }
  }

  @VisibleForTesting
  fun valueOf(property: NlPropertyItem?): Double? {
    val stringValue = property?.value?.ifEmpty { "0" } ?: "0"
    return try {
      stringValue.toDouble()
    } catch (ex: NumberFormatException) {
      0.0
    }
  }
}
