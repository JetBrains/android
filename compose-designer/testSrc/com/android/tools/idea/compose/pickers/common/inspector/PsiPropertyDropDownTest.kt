/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.common.inspector

import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.idea.compose.pickers.common.enumsupport.PsiEnumValueCellRenderer
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.impl.model.util.FakeComboBoxUI
import com.android.tools.property.panel.impl.model.util.FakeEnumSupport
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

internal class PsiPropertyDropDownTest {

  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  @get:Rule val edtRule = EdtRule()

  @Test
  fun testEnterInPopup() {
    val property = FakePsiProperty("prop", "")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val dropDown = createDropDown(property, enumSupport)
    val ui = createFakeUiForComboBoxWrapper(dropDown)
    getWrappedComboBox(dropDown).showPopup()
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertEquals("invisible", property.value)
    assertFalse(isPopupVisible(dropDown))
  }

  @Test
  fun testNavigationInPopup() {
    val property = FakePsiProperty("prop", "")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val dropDown = createDropDown(property, enumSupport)
    val ui = createFakeUiForComboBoxWrapper(dropDown)
    val wrappedComboBox = getWrappedComboBox(dropDown)

    with(ui.keyboard) {
      setFocus(wrappedComboBox)
      wrappedComboBox.showPopup()
      pressAndRelease(KeyEvent.VK_DOWN)
      pressAndRelease(KeyEvent.VK_TAB)
    }
    assertEquals("", property.value)
    assertFalse(isPopupVisible(dropDown))

    with(ui.keyboard) {
      setFocus(wrappedComboBox)
      wrappedComboBox.showPopup()
      pressAndRelease(KeyEvent.VK_DOWN)
      press(FakeKeyboard.Key.SHIFT)
      pressAndRelease(FakeKeyboard.Key.TAB)
      release(FakeKeyboard.Key.SHIFT)
    }
    assertEquals("", property.value)
    assertFalse(isPopupVisible(dropDown))
  }

  @Test
  fun testEscapeInPopup() {
    val property = FakePsiProperty("prop", "")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val dropDown = createDropDown(property, enumSupport)
    val ui = createFakeUiForComboBoxWrapper(dropDown)
    val wrappedComboBox = getWrappedComboBox(dropDown)
    val keyConsumer = wrapDropDownInKeyboardConsumer(dropDown)

    with(ui.keyboard) {
      wrappedComboBox.showPopup()
      pressAndRelease(FakeKeyboard.Key.ESC)
    }
    assertFalse(isPopupVisible(dropDown))
    assertEquals(0, keyConsumer.keyCount)
    ui.keyboard.pressAndRelease(FakeKeyboard.Key.ESC)
    assertEquals(1, keyConsumer.keyCount)
  }

  @Test
  fun updateFromModel() {
    val property = FakePsiProperty("prop", "existing", "default")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")

    val model = PsiDropDownModel(property, enumSupport)
    val dropDown = createDropDown(model)
    val ui = createFakeUiForComboBoxWrapper(dropDown)
    val wrappedComboBox = getWrappedComboBox(dropDown)

    assertEquals("existing", wrappedComboBox.selectedEnumValue?.value)
    assertEquals(0, wrappedComboBox.selectedIndex)

    property.value = null
    model.refresh()

    assertNull(wrappedComboBox.selectedEnumValue!!.value)
    assertEquals(0, wrappedComboBox.selectedIndex)

    property.value = "invisible"
    model.refresh()

    assertEquals("invisible", wrappedComboBox.selectedEnumValue?.value)
    assertEquals(0, wrappedComboBox.selectedIndex)

    wrappedComboBox.showPopup()
    ui.keyboard.pressAndRelease(FakeKeyboard.Key.ESC)

    // Index is updated once the full list is loaded
    assertEquals("invisible", wrappedComboBox.selectedItem?.toString())
    assertEquals(1, wrappedComboBox.selectedIndex)
  }

  private fun createFakeUiForComboBoxWrapper(comboBox: PsiPropertyDropDown): FakeUi {
    val wrapper = getWrappedComboBox(comboBox)
    val ui = FakeUi(wrapper)
    ui.keyboard.setFocus(wrapper)
    return ui
  }

  private fun getWrappedComboBox(comboBox: PsiPropertyDropDown): CommonComboBox<EnumValue, *> {
    @Suppress("UNCHECKED_CAST") return comboBox.components.single() as CommonComboBox<EnumValue, *>
  }

  private val CommonComboBox<EnumValue, *>.selectedEnumValue
    get() = selectedItem as? EnumValue

  private fun isPopupVisible(dropDown: PsiPropertyDropDown): Boolean =
    getWrappedComboBox(dropDown).isPopupVisible

  private fun createDropDown(model: PsiDropDownModel): PsiPropertyDropDown {
    val dropdown = PsiPropertyDropDown(model, true, PsiEnumValueCellRenderer())
    val wrapped = getWrappedComboBox(dropdown)
    wrapped.setUI(FakeComboBoxUI())
    return dropdown
  }

  private fun createDropDown(
    property: PsiPropertyItem,
    enumSupport: EnumSupport
  ): PsiPropertyDropDown {
    val model = PsiDropDownModel(property, enumSupport)
    return createDropDown(model)
  }

  private fun wrapDropDownInKeyboardConsumer(dropDown: PsiPropertyDropDown): MyKeyboardConsumer =
    MyKeyboardConsumer().apply { add(dropDown) }

  /** Container that may consume ESCAPE keyboard events. */
  private class MyKeyboardConsumer : JPanel() {
    private var _keyCount = 0

    val keyCount: Int
      get() = _keyCount

    init {
      registerActionKey(
        { _keyCount++ },
        KeyStrokes.ESCAPE,
        "escape",
        condition = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      )
    }
  }
}
