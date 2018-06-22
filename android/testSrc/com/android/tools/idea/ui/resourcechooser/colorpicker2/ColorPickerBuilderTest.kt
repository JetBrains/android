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

import com.intellij.testFramework.IdeaTestCase
import java.awt.Color
import javax.swing.JComponent

class ColorPickerBuilderTest : IdeaTestCase() {

  fun testCreateEmptyPickerShouldThrowTheException() {
    assertThrows<IllegalStateException>(IllegalStateException::class.java) { ColorPickerBuilder().build() }
  }

  fun testCreatePickerWithSaturationBrightnessComponent() {
    val picker = ColorPickerBuilder().addSaturationBrightnessComponent().build()
    assertEquals(1, picker.components.size)
    assertTrue(picker.getComponent(0) is SaturationBrightnessComponent)
  }

  fun testCreatePickerWithColorAdjustPanel() {
    val picker = ColorPickerBuilder().addColorAdjustPanel().build()
    assertEquals(1, picker.components.size)
    assertTrue(picker.getComponent(0) is ColorAdjustPanel)
  }

  fun testCreatePickerWithColorValuePanel() {
    val picker = ColorPickerBuilder().addColorValuePanel().build()
    assertEquals(1, picker.components.size)
    assertTrue(picker.getComponent(0) is ColorValuePanel)
  }

  fun testCreateCustomPanelPickerWithColorPalette() {
    val picker = ColorPickerBuilder().addCustomComponent(EmptyColorPickerComponentProvider()).build()
    assertEquals(1, picker.components.size)
    assertTrue(picker.getComponent(0) is EmptyColorPickerPanel)
  }

  fun testCreatePickerWithOperationPanel() {
    val ok = { _: Color -> Unit }
    val cancel = { _: Color -> Unit }
    val picker = ColorPickerBuilder().addOperationPanel(ok, cancel).build()
    assertEquals(1, picker.components.size)
    assertTrue(picker.getComponent(0) is OperationPanel)
  }

  fun testCreatePickerWithNoOperationWillThrowException() {
    assertThrows<IllegalStateException>(IllegalStateException::class.java) {
      ColorPickerBuilder().addOperationPanel(null, null).build()
    }
  }
}

private class EmptyColorPickerPanel : JComponent()

private class EmptyColorPickerComponentProvider : ColorPickerComponentProvider {
  override fun createComponent(colorPickerModel: ColorPickerModel) = EmptyColorPickerPanel()
}
