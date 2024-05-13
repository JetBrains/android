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
package com.android.tools.idea.ui.resourcechooser.colorpicker2.internal

import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.ui.MaterialColors
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerBuilder
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerComponentProvider
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerModel
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.Color

class MaterialColorPaletteTest : HeavyPlatformTestCase() {

  fun testCreatePickerWithColorPalette() {
    val picker = ColorPickerBuilder().addCustomComponent(object : ColorPickerComponentProvider {
      override fun createComponent(colorPickerModel: ColorPickerModel) = MaterialColorPalette(colorPickerModel)
    }).build()
    assertEquals(1, picker.components.size)
    assertTrue(picker.getComponent(0) is MaterialColorPalette)
  }

  fun testChangeColorCategoryWillUpdateButtons() {
    val palette = MaterialColorPalette(ColorPickerModel())
    val comboBox = palette.getColorSetComboBox()

    for (category in MaterialColors.Category.values()) {
      comboBox.selectedItem = category
      for (index in palette.colorButtons.indices) {
        val b = palette.colorButtons[index]
        val colorName = MaterialColors.Color.values()[index]
        val expectedColor = MaterialColors[colorName, category]
        assertEquals(expectedColor, b.color)
      }
    }
  }

  fun testChangeSelectedColorCategoryWillUpdateDefaultCategory() {
    val palette1 = MaterialColorPalette(ColorPickerModel())
    val comboBox1 = palette1.getColorSetComboBox()
    comboBox1.selectedItem = MaterialColors.Category.MATERIAL_ACCENT_100

    val palette2 = MaterialColorPalette(ColorPickerModel())
    val comboBox2 = palette2.getColorSetComboBox()
    assertEquals(MaterialColors.Category.MATERIAL_ACCENT_100, comboBox2.selectedItem)
  }

  fun testClickButtonWillUpdateColorPickerModel() {
    val model = ColorPickerModel()
    val palette = MaterialColorPalette(model)
    val comboBox = palette.getColorSetComboBox()

    for (category in MaterialColors.Category.values()) {
      comboBox.selectedItem = category
      for (index in palette.colorButtons.indices) {
        val b = palette.colorButtons[index]
        b.doClick()
        val colorName = MaterialColors.Color.values()[index]
        val expectedColor = MaterialColors[colorName, category]
        assertEquals(expectedColor, model.color)
      }
    }
  }

  private operator fun MaterialColors.get(color: MaterialColors.Color, category: MaterialColors.Category): Color? =
    MaterialColors.getColor(color, category)

  /**
   * Get the [CommonComboBox] of [MaterialColorPalette] which helps to select the set of MaterialColor. (Material 50, 100, 200, etc.)
   */
  @Suppress("UNCHECKED_CAST")
  private fun MaterialColorPalette.getColorSetComboBox() = UIUtil.findComponentOfType(this, CommonComboBox::class.java)
    as CommonComboBox<MaterialColors.Category, CommonComboBoxModel<MaterialColors.Category>>
}
