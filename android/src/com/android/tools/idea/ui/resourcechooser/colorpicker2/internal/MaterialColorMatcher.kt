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
package com.android.tools.idea.ui.resourcechooser.colorpicker2.internal

import com.android.tools.idea.editors.theme.MaterialColorUtils
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerComponentProvider
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerListener
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerModel
import com.android.tools.idea.ui.resourcechooser.colorpicker2.HORIZONTAL_MARGIN_TO_PICKER_BORDER
import com.android.tools.idea.ui.resourcechooser.colorpicker2.PICKER_BACKGROUND_COLOR
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** Panel that suggests a material color based on the current picked color from [ColorPickerModel]. */
class MaterialColorMatcher(private val colorPickerModel: ColorPickerModel): JPanel(FlowLayout(FlowLayout.LEFT)), ColorPickerListener {
  private val colorButton: ColorButton
  private val label = JBLabel("Closest material color: ")

  init {
    background = PICKER_BACKGROUND_COLOR
    border = JBUI.Borders.empty(5, HORIZONTAL_MARGIN_TO_PICKER_BORDER, 10, HORIZONTAL_MARGIN_TO_PICKER_BORDER)
    val initialClosest = MaterialColorUtils.getClosestMaterialColor(colorPickerModel.color)
    colorButton = ColorButton(initialClosest)
    colorButton.addActionListener { event ->
      // Update model when suggested color is clicked.
      colorPickerModel.setColor(colorButton.color, this@MaterialColorMatcher)
    }
    colorPickerModel.addListener(this@MaterialColorMatcher)
    add(label)
    add(colorButton)
  }

  /** Update closest material color when the model changes. */
  override fun colorChanged(color: Color?, source: Any?) {
    if (source != null && source == this@MaterialColorMatcher) {
      return
    }
    color?.let {
      colorButton.color = MaterialColorUtils.getClosestMaterialColor(color)
      colorButton.repaint()
    }
  }
}

object MaterialColorMatcherProvider : ColorPickerComponentProvider {
  override fun createComponent(colorPickerModel: ColorPickerModel): JComponent {
    return MaterialColorMatcher(colorPickerModel)
  }
}