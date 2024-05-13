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

import com.intellij.testFramework.HeavyPlatformTestCase
import org.mockito.Mockito
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke

class ColorPickerBuilderTest : HeavyPlatformTestCase() {

  fun testCreateEmptyPickerShouldThrowTheException() {
    assertThrows(IllegalStateException::class.java) { ColorPickerBuilder().build() }
  }

  fun testCreatePickerWithSaturationBrightnessComponent() {
    val picker = ColorPickerBuilder().addSaturationBrightnessComponent().build()
    assertEquals(1, picker.components.size)
    // Picker wrappers the SaturationBrightnessComponent for supporting Contrast mode.
    assertTrue((picker.getComponent(0) as JPanel).getComponent(0) is SaturationBrightnessComponent)
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
    assertThrows(IllegalStateException::class.java) {
      ColorPickerBuilder().addOperationPanel(null, null).build()
    }
  }

  fun testAddKeyAction() {
    val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
    val action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) = Unit
    }

    val colorPicker = ColorPickerBuilder()
      .addSeparator()
      .addKeyAction(keyStroke, action)
      .build()

    assertEquals(1, colorPicker.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).size())
    val actionId = colorPicker.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(keyStroke)
    assertNotNull(actionId)
    assertEquals(1, colorPicker.actionMap.size())
    assertEquals(action, colorPicker.actionMap.get(actionId))
  }

  fun testFocusTraversal() {
    val colorPicker = ColorPickerBuilder()
      .addColorValuePanel()
      .addSeparator()
      .addColorValuePanel().withFocus()
      .build()

    val secondColorValuePanel = colorPicker.getComponent(2) as ColorValuePanel
    assertEquals(secondColorValuePanel, colorPicker.focusTraversalPolicy.getDefaultComponent(colorPicker))
  }

  fun testEscapeTriggersCancelOperations() {
    val cancelTask = Mockito.mock(Runnable::class.java)

    val colorPicker = ColorPickerBuilder()
      .addOperationPanel({ _ -> Unit }, { _ -> cancelTask.run() })
      .build()

    val key = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, true)
    val action = colorPicker.getActionForKeyStroke(key)!!
    val actionEvent = ActionEvent(colorPicker, 0, key.keyChar.toString(), key.modifiers)

    action.actionPerformed(actionEvent)
    Mockito.verify(cancelTask, Mockito.times(1)).run()
  }
}

private class EmptyColorPickerPanel : JComponent()

private class EmptyColorPickerComponentProvider : ColorPickerComponentProvider {
  override fun createComponent(colorPickerModel: ColorPickerModel) = EmptyColorPickerPanel()
}
