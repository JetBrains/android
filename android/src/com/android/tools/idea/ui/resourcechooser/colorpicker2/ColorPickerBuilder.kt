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
package com.android.tools.idea.ui.resourcechooser.colorpicker2

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import javax.swing.*

val PICKER_BACKGROUND_COLOR = JBColor(Color(252, 252, 252), Color(64, 64, 64))
val PICKER_TEXT_COLOR = Color(186, 186, 186)

const val PICKER_PREFERRED_WIDTH = 300
const val HORIZONTAL_MARGIN_TO_PICKER_BORDER = 14

private val PICKER_BORDER = JBUI.Borders.empty()

private const val SEPARATOR_HEIGHT = 5

/**
 * Builder class to help to create customized picker components depends on the requirement.
 */
class ColorPickerBuilder {

  private val componentsToBuild = mutableListOf<JComponent>()
  private val model = ColorPickerModel()
  private var originalColor: Color? = null

  fun setOriginalColor(originalColor: Color?) = apply { this.originalColor = originalColor }

  fun addSaturationBrightnessComponent() = apply { componentsToBuild.add(SaturationBrightnessComponent(model)) }

  @JvmOverloads
  fun addColorAdjustPanel(colorPipetteProvider: ColorPipetteProvider = GraphicalColorPipetteProvider()) = apply {
    componentsToBuild.add(ColorAdjustPanel(model, colorPipetteProvider))
  }

  fun addColorValuePanel() = apply { componentsToBuild.add(ColorValuePanel(model)) }

  /**
   * If both [okOperation] and [cancelOperation] are null, [IllegalArgumentException] will be raised.
   */
  fun addOperationPanel(okOperation: ((Color) -> Unit)?, cancelOperation: ((Color) -> Unit)?) = apply {
    componentsToBuild.add(OperationPanel(model, okOperation, cancelOperation))
  }

  fun addCustomComponent(provider: ColorPickerComponentProvider) = apply { componentsToBuild.add(provider.createComponent(model)) }

  fun addSeparator() = apply {
    val separator = JSeparator(JSeparator.HORIZONTAL)
    separator.border = JBUI.Borders.empty()
    separator.preferredSize = JBUI.size(PICKER_PREFERRED_WIDTH, SEPARATOR_HEIGHT)
    componentsToBuild.add(separator)
  }

  fun build(): JPanel {
    if (componentsToBuild.isEmpty()) {
      throw IllegalStateException("The Color Picker should have at least one picking component.")
    }

    val width: Int = componentsToBuild.map { it.preferredSize.width }.max()!!
    val height = componentsToBuild.map { it.preferredSize.height }.sum()

    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = PICKER_BORDER
    panel.preferredSize = Dimension(width, height)
    panel.background = PICKER_BACKGROUND_COLOR

    for (component in componentsToBuild) {
      panel.add(component)
    }

    val c = originalColor
    if (c != null) {
      model.setColor(c, null)
    }

    panel.repaint()
    return panel
  }
}
