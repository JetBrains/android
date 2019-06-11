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

import com.android.tools.idea.ui.MaterialColors
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerBuilder
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerComponentProvider
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerModel
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.AndroidTestCase
import java.awt.Color

class MaterialColorMatcherTest : AndroidTestCase() {

  fun testCreatePickerWithColorMatcher() {
    val picker = ColorPickerBuilder().addCustomComponent(object : ColorPickerComponentProvider {
      override fun createComponent(colorPickerModel: ColorPickerModel) = MaterialColorMatcher(colorPickerModel)
    }).build()
    assertEquals(1, picker.components.size)
    assertTrue(picker.getComponent(0) is MaterialColorMatcher)
  }

  fun testCreateColorMatcherInitialValue() {
    var initialColor = MaterialColors.AMBER_100
    // Slightly change color.
    initialColor = Color(initialColor.red, initialColor.green,initialColor.blue + 10)
    val colorMatcher = MaterialColorMatcher(ColorPickerModel(initialColor))

    assertEquals(MaterialColors.AMBER_100, colorMatcher.getColorButton().color)
  }

  fun testColorMatcherButtonUpdatesModel() {
    val model = ColorPickerModel()
    val colorMatcher = MaterialColorMatcher(model)
    val colorButton = colorMatcher.getColorButton()
    colorButton.color = MaterialColors.CYAN_100
    colorButton.doClick()
    assertEquals(MaterialColors.CYAN_100, model.color)
  }

  fun testColorMatcherButtonUpdatedFromModel() {
    val model = ColorPickerModel()
    val colorMatcher = MaterialColorMatcher(model)
    val colorButton = colorMatcher.getColorButton()

    model.setColor(MaterialColors.RED_100, null)
    assertEquals(MaterialColors.RED_100, colorButton.color)


    model.setPickingColor(MaterialColors.BLUE_100, null)
    // No change when picking color.
    assertEquals(MaterialColors.RED_100, colorButton.color)
  }


  private fun MaterialColorMatcher.getColorButton() : ColorButton {
    return UIUtil.findComponentOfType(this, ColorButton::class.java)!!
  }
}