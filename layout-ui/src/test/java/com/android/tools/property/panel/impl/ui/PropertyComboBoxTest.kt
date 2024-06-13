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
package com.android.tools.property.panel.impl.ui

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.property.panel.api.EditorContext
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.impl.model.ComboBoxPropertyEditorModel
import com.android.tools.property.panel.impl.model.util.FakeAction
import com.android.tools.property.panel.impl.model.util.FakeComboBoxUI
import com.android.tools.property.panel.impl.model.util.FakeEnumSupport
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.test.assertEquals

class PropertyComboBoxTest {

  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  @get:Rule val edtRule = EdtRule()

  @Test
  fun testInitialTextWithEmptyValue() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport, editable = false)

    assertEquals("", comboBox.editor.text)
  }

  @Test
  fun testPreferredSizeCoversEditorControl() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport)
    val size = comboBox.preferredSize
    val editorSize = comboBox.editor.preferredSize
    assertThat(editorSize.width).isAtMost(size.width)
    assertThat(editorSize.height).isAtMost(size.height)
  }

  @Test
  fun testEnter() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport)
    comboBox.editor.text = "gone"
    val ui = createFakeUiForComboBoxEditor(comboBox)
    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(property.value).isEqualTo("gone")
    assertThat(isPopupVisible(comboBox)).isFalse()
  }

  @Test
  fun testEscape() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport)
    val keyboardConsumer = wrapComboBoxInKeyboardConsumer(comboBox)
    comboBox.editor.text = "gone"
    val ui = createFakeUiForComboBoxEditor(comboBox)
    ui.keyboard.pressAndRelease(KeyEvent.VK_ESCAPE)
    assertThat(comboBox.editor.text).isEqualTo("visible")
    assertThat(property.value).isEqualTo("visible")
    assertThat(isPopupVisible(comboBox)).isFalse()
    assertThat(keyboardConsumer.keyCount).isEqualTo(0)

    // ComboBox should not be able to consume Escape key again
    ui.keyboard.pressAndRelease(KeyEvent.VK_ESCAPE)
    assertThat(keyboardConsumer.keyCount).isEqualTo(1)
  }

  @RunsInEdt
  @Test
  fun testEditorCanScroll() {
    val editor = createComboBoxEditorAndScrollRight(EditorContext.TABLE_EDITOR)
    assertThat(editor.scrollOffset).isGreaterThan(0)
  }

  @RunsInEdt
  @Test
  fun testRendererCannotScroll() {
    val editor = createComboBoxEditorAndScrollRight(EditorContext.TABLE_RENDERER)
    assertThat(editor.scrollOffset).isEqualTo(0)
  }

  private fun createComboBoxEditorAndScrollRight(context: EditorContext): JTextField {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport, context)
    val editor = comboBox.editor
    createFakeUiForComboBoxEditor(comboBox, Dimension(20, 20))
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    editor.scrollRectToVisible(Rectangle(800, 0, 50, 20))
    return editor
  }

  @Test
  fun testSpace() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport)
    val ui = createFakeUiForComboBoxEditor(comboBox)
    ui.keyboard.type(KeyEvent.VK_SPACE)
    assertThat(property.value).isEqualTo("visible")
    assertThat(comboBox.editor.text).isEqualTo(" ")
  }

  @Test
  fun testEnterInPopup() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport)
    val ui = createFakeUiForComboBoxEditor(comboBox)
    getWrappedComboBox(comboBox).showPopup()
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(property.value).isEqualTo("invisible")
    assertThat(isPopupVisible(comboBox)).isFalse()
  }

  @Test
  fun testEnterInPopupOfDropDown() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport, editable = false)
    val ui = createFakeUiForComboBoxWrapper(comboBox)
    getWrappedComboBox(comboBox).showPopup()
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(property.value).isEqualTo("invisible")
    assertThat(isPopupVisible(comboBox)).isFalse()
  }

  @RunsInEdt
  @Test
  fun testEnterInPopupOnAction() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    val action = FakeAction("testAction")
    val enumSupport = FakeEnumSupport("visible", "invisible", action = action)
    val comboBox = createComboBox(property, enumSupport)
    val ui = createFakeUiForComboBoxEditor(comboBox)
    getWrappedComboBox(comboBox).showPopup()
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_DOWN)
    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)

    // The action is executed delayed on the UI event queue:
    UIUtil.dispatchAllInvocationEvents()
    assertThat(action.actionPerformedCount).isEqualTo(1)
    assertThat(property.value).isEqualTo("visible")
    assertThat(isPopupVisible(comboBox)).isFalse()
  }

  @Test
  fun testEscapeInPopup() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport)
    val keyboardConsumer = wrapComboBoxInKeyboardConsumer(comboBox)
    comboBox.editor.text = "gone"
    val ui = createFakeUiForComboBoxEditor(comboBox)
    getWrappedComboBox(comboBox).showPopup()
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    ui.keyboard.pressAndRelease(KeyEvent.VK_ESCAPE)
    assertThat(comboBox.editor.text).isEqualTo("visible")
    assertThat(property.value).isEqualTo("visible")
    assertThat(isPopupVisible(comboBox)).isFalse()
    assertThat(keyboardConsumer.keyCount).isEqualTo(0)
    ui.keyboard.pressAndRelease(KeyEvent.VK_ESCAPE)
    assertThat(keyboardConsumer.keyCount).isEqualTo(1)
  }

  @Test
  fun testSpaceInPopupEditor() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport)
    val ui = createFakeUiForComboBoxEditor(comboBox)
    getWrappedComboBox(comboBox).showPopup()
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    ui.keyboard.type(KeyEvent.VK_SPACE)
    assertThat(property.value).isEqualTo("invisible")
    assertThat(isPopupVisible(comboBox)).isFalse()
  }

  private fun createFakeUiForComboBoxEditor(
    comboBox: PropertyComboBox,
    size: Dimension = Dimension(200, 20),
  ): FakeUi {
    val editor = comboBox.editor
    editor.size = size
    val ui = FakeUi(editor)
    ui.keyboard.setFocus(editor)
    editor.selectAll()
    return ui
  }

  private fun createFakeUiForComboBoxWrapper(comboBox: PropertyComboBox): FakeUi {
    val wrapper = getWrappedComboBox(comboBox)
    val ui = FakeUi(wrapper)
    ui.keyboard.setFocus(wrapper)
    return ui
  }

  private fun getWrappedComboBox(comboBox: PropertyComboBox): CommonComboBox<EnumValue, *> {
    val editor = comboBox.editor
    @Suppress("UNCHECKED_CAST")
    return (editor.parent ?: comboBox.components.single { it.isVisible })
      as CommonComboBox<EnumValue, *>
  }

  private fun isPopupVisible(comboBox: PropertyComboBox): Boolean =
    getWrappedComboBox(comboBox).isPopupVisible()

  private fun createComboBox(
    property: PropertyItem,
    enumSupport: EnumSupport,
    context: EditorContext = EditorContext.TABLE_EDITOR,
    editable: Boolean = true,
  ): PropertyComboBox {
    val model = ComboBoxPropertyEditorModel(property, enumSupport, editable)
    val comboBox = PropertyComboBox(model, context)
    val wrapped = getWrappedComboBox(comboBox)
    wrapped.setUI(FakeComboBoxUI())
    return comboBox
  }

  private fun wrapComboBoxInKeyboardConsumer(comboBox: PropertyComboBox): MyKeyboardConsumer =
    MyKeyboardConsumer().apply { add(comboBox) }

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
        condition = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
      )
    }
  }
}
