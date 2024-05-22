/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.adtui

import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.ui.MaterialColors
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.colorpicker.ColorButton
import com.intellij.ui.colorpicker.ColorPickerComponentProvider
import com.intellij.ui.colorpicker.ColorPickerModel
import com.intellij.ui.colorpicker.HORIZONTAL_MARGIN_TO_PICKER_BORDER
import com.intellij.ui.colorpicker.PICKER_BACKGROUND_COLOR
import com.intellij.ui.colorpicker.PICKER_PREFERRED_WIDTH
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import java.awt.GridLayout
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JPanel

private const val COLOR_BUTTON_ROW = 2
private const val COLOR_BUTTON_COLUMN = 8

private const val PANEL_HEIGHT = 120

class MaterialColorPalette(private val pickerModel: ColorPickerModel) : JPanel() {

  @get:TestOnly
  val colorButtons = Array(COLOR_BUTTON_ROW * COLOR_BUTTON_COLUMN) {
    ColorButton().apply {
      background = PICKER_BACKGROUND_COLOR
      addActionListener { pickerModel.setColor(color, this@MaterialColorPalette) }
    }
  }

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    border = JBUI.Borders.empty(5, HORIZONTAL_MARGIN_TO_PICKER_BORDER, 10, HORIZONTAL_MARGIN_TO_PICKER_BORDER)
    preferredSize = JBUI.size(PICKER_PREFERRED_WIDTH, PANEL_HEIGHT)
    background = PICKER_BACKGROUND_COLOR

    val comboBoxPanel = JPanel(GridLayout(1, 1)).apply {
      preferredSize = JBUI.size(PICKER_PREFERRED_WIDTH, 35)
      border = JBUI.Borders.empty(0, 2, 8, 2)
      background = PICKER_BACKGROUND_COLOR
    }
    val boxModel = MyComboBoxModel(MaterialColors.Category.values())
    val box = CommonComboBox(boxModel)
    box.addActionListener { setColorSet(boxModel.selectedItem as MaterialColors.Category) }
    comboBoxPanel.add(box)
    add(comboBoxPanel)

    // Add buttons for built-in colors
    val colorButtonPanel = JPanel(GridLayout(COLOR_BUTTON_ROW, COLOR_BUTTON_COLUMN)).apply {
      border = JBUI.Borders.empty()
      background = PICKER_BACKGROUND_COLOR
      colorButtons.forEach { add(it) }
    }
    add(colorButtonPanel)

    val category = loadLastUsedColorCategory()
    box.selectedItem = category
    setColorSet(category)
  }

  private fun setColorSet(category: MaterialColors.Category) {
    val colorSet = MaterialColors.getColorSet(category)
    saveLastUsedColorCategory(category)
    MaterialColors.Color.values()
      .filter { it.ordinal < colorButtons.size }
      .forEach { colorButtons[it.ordinal].color = colorSet[it]!! }
    repaint()
  }
}

private class MyComboBoxModel(colorCategories: Array<MaterialColors.Category>)
  : DefaultComboBoxModel<MaterialColors.Category>(), CommonComboBoxModel<MaterialColors.Category> {

  init {
    colorCategories.forEach { addElement(it) }
  }

  override var value = (selectedItem as MaterialColors.Category).displayName

  override var text = (selectedItem as MaterialColors.Category).displayName

  override var editable = false
    private set

  override fun addListener(listener: ValueChangedListener) = Unit

  override fun removeListener(listener: ValueChangedListener) = Unit
}

private const val COLOR_PICKER_CATEGORY_PROPERTY = "colorPickerCategoryProperty"
private val DEFAULT_COLOR_CATEGORY = MaterialColors.Category.MATERIAL_500

private fun loadLastUsedColorCategory(): MaterialColors.Category {
  val modeName = PropertiesComponent.getInstance().getValue(
    COLOR_PICKER_CATEGORY_PROPERTY, DEFAULT_COLOR_CATEGORY.name)
  return try {
    MaterialColors.Category.valueOf(modeName)
  }
  catch (e: IllegalArgumentException) {
    // If the code reach here, that means some of unexpected category is saved as user's preference.
    // In this case, return the default category instead.
    Logger.getInstance(MaterialColorPalette::class.java)
      .warn("The color category $modeName is not recognized, use default category $DEFAULT_COLOR_CATEGORY instead")
    DEFAULT_COLOR_CATEGORY
  }
}

private fun saveLastUsedColorCategory(category: MaterialColors.Category)
  = PropertiesComponent.getInstance().setValue(
  COLOR_PICKER_CATEGORY_PROPERTY, category.name)

object MaterialColorPaletteProvider : ColorPickerComponentProvider {
  override fun createComponent(colorPickerModel: ColorPickerModel) = MaterialColorPalette(colorPickerModel)
}
