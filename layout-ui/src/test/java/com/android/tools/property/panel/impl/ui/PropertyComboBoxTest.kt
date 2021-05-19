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
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.impl.model.ComboBoxPropertyEditorModel
import com.android.tools.property.panel.impl.model.util.FakeAction
import com.android.tools.property.panel.impl.model.util.FakeEnumSupport
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.android.tools.property.testing.PropertyAppRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBList
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.plaf.basic.ComboPopup

class PropertyComboBoxTest {

  @get:Rule
  val appRule = PropertyAppRule()

  @get:Rule
  val edtRule = EdtRule()

  @Test
  fun testEnter() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport, true)
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
    val comboBox = createComboBox(property, enumSupport, true)
    comboBox.editor.text = "gone"
    val ui = createFakeUiForComboBoxEditor(comboBox)
    ui.keyboard.pressAndRelease(KeyEvent.VK_ESCAPE)
    assertThat(property.value).isEqualTo("visible")
    assertThat(isPopupVisible(comboBox)).isFalse()
  }

  @Test
  fun testSpace() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport, true)
    val ui = createFakeUiForComboBoxEditor(comboBox)
    ui.keyboard.type(KeyEvent.VK_SPACE)
    assertThat(property.value).isEqualTo("visible")
    assertThat(comboBox.editor.text).isEqualTo(" ")
  }

  @Test
  fun testEnterInPopup() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport, true)
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
    val comboBox = createComboBox(property, enumSupport, false)
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
    val comboBox = createComboBox(property, enumSupport, true)
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
    val comboBox = createComboBox(property, enumSupport, true)
    val ui = createFakeUiForComboBoxEditor(comboBox)
    getWrappedComboBox(comboBox).showPopup()
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    ui.keyboard.pressAndRelease(KeyEvent.VK_ESCAPE)
    assertThat(property.value).isEqualTo("visible")
    assertThat(isPopupVisible(comboBox)).isFalse()
  }

  @Test
  fun testSpaceInPopupEditor() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    val enumSupport = FakeEnumSupport("visible", "invisible", "gone")
    val comboBox = createComboBox(property, enumSupport, true)
    val ui = createFakeUiForComboBoxEditor(comboBox)
    getWrappedComboBox(comboBox).showPopup()
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    ui.keyboard.type(KeyEvent.VK_SPACE)
    assertThat(property.value).isEqualTo("invisible")
    assertThat(isPopupVisible(comboBox)).isFalse()
  }

  private fun createFakeUiForComboBoxEditor(comboBox: PropertyComboBox): FakeUi {
    val editor = comboBox.editor
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
    return (editor.parent ?: comboBox.components.single()) as CommonComboBox<EnumValue, *>
  }

  private fun isPopupVisible(comboBox: PropertyComboBox): Boolean =
    getWrappedComboBox(comboBox).isPopupVisible()

  private fun createComboBox(property: PropertyItem, enumSupport: EnumSupport, editable: Boolean): PropertyComboBox {
    val model = ComboBoxPropertyEditorModel(property, enumSupport, editable)
    val comboBox = PropertyComboBox(model, true)
    val wrapped = getWrappedComboBox(comboBox)
    wrapped.setUI(FakeComboBoxUI())
    return comboBox
  }

  private class FakeComboBoxUI : DarculaComboBoxUI() {
    override fun installUI(component: JComponent) {
      @Suppress("UNCHECKED_CAST")
      comboBox = component as JComboBox<Any>
      popup = MyComboPopup(comboBox)
      installListeners()
      installComponents()
      installKeyboardActions()
    }

    override fun setPopupVisible(comboBox: JComboBox<*>?, visible: Boolean) {
      if (visible) {
        popup.show()
      }
      else {
        popup.hide()
      }
    }

    override fun isPopupVisible(comboBox: JComboBox<*>?): Boolean = popup.isVisible
  }

  private class MyComboPopup(private val comboBox: JComboBox<Any>): ComboPopup {
    private var visible = false
    private val list = JBList(comboBox.model)
    private val mouseListener = object : MouseAdapter() {}
    private val keyListener = object : KeyAdapter() {}

    override fun getMouseListener(): MouseListener = mouseListener

    override fun getMouseMotionListener(): MouseMotionListener = mouseListener

    override fun getKeyListener(): KeyListener = keyListener

    override fun hide() {
      comboBox.firePopupMenuWillBecomeInvisible()
      visible = false
    }

    override fun show() {
      comboBox.firePopupMenuWillBecomeVisible()
      setListSelection(comboBox.selectedIndex)
      visible = true
    }

    private fun setListSelection(selectedIndex: Int) {
      if (selectedIndex == -1) {
        list.clearSelection()
      }
      else {
        list.selectedIndex = selectedIndex
        list.ensureIndexIsVisible(selectedIndex)
      }
    }

    override fun isVisible(): Boolean = visible

    override fun getList(): JList<Any> = list

    override fun uninstallingUI() {}
  }
}
