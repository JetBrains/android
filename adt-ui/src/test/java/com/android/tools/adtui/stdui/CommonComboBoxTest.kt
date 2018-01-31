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
package com.android.tools.adtui.stdui

import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.google.common.truth.Truth.assertThat
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import java.awt.Dimension
import java.awt.Font
import java.awt.KeyboardFocusManager
import javax.swing.JList
import javax.swing.JTextField
import javax.swing.plaf.basic.BasicComboBoxUI

private const val MAX_PERCENT_DIFFERENT = 5.0f

// TODO: Add test for NonSelectableItem

@RunWith(JUnit4::class)
class CommonComboBoxTest {
  private val model = DefaultCommonComboBoxModel("two", listOf("one", "two", "three", "four", "five", "six"))
  private val comboBox = CommonComboBox(model)
  private val focusManager = Mockito.mock(KeyboardFocusManager::class.java)

  @Before
  fun setUp() {
    comboBox.font = UIUtil.getFontWithFallback("Ariel", Font.BOLD, 12)
  }

  @After
  fun cleanUp() {
    KeyboardFocusManager.setCurrentKeyboardFocusManager(null)
    JBUI.setUserScaleFactor(1.0f)
  }

  @Test
  fun regularComboBox() {
    model.selectedItem = "two"
    checkLook("regularComboBox.png")
  }

  @Test
  fun focusedComboBox() {
    model.selectedItem = "two"
    Mockito.`when`(focusManager.focusOwner).thenReturn(comboBox)
    KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)
    checkLook("focusedComboBox.png")
  }

  @Test
  fun disabledComboBox() {
    model.selectedItem = "two"
    model.enabled = false
    checkLook("disabledComboBox.png")
  }

  @Test
  fun placeHolderComboBox() {
    model.placeHolderValue = "Holder"
    (comboBox.editor.editorComponent as JTextField).text = ""
    checkLook("placeHolderComboBox.png")
  }

  @Test
  fun errorComboBox() {
    (comboBox.editor.editorComponent as JTextField).text = "Error"
    checkLook("errorComboBox.png")
  }

  @Test
  fun regularComboBoxHiRes() {
    model.selectedItem = "two"
    JBUI.setUserScaleFactor(1.75f)
    checkLook("regularComboBoxHiRes.png")
  }

  @Test
  fun focusedComboBoxHiRes() {
    model.selectedItem = "two"
    JBUI.setUserScaleFactor(1.75f)
    Mockito.`when`(focusManager.focusOwner).thenReturn(comboBox)
    KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)
    checkLook("focusedComboBoxHiRes.png")
  }

  @Test
  fun disabledComboBoxHiRes() {
    model.selectedItem = "two"
    JBUI.setUserScaleFactor(1.75f)
    model.enabled = false
    checkLook("disabledComboBoxHiRes.png")
  }

  @Test
  fun placeHolderComboBoxHiRes() {
    JBUI.setUserScaleFactor(1.75f)
    model.placeHolderValue = "Holder"
    (comboBox.editor.editorComponent as JTextField).text = ""
    checkLook("placeHolderComboBoxHiRes.png")
  }

  @Test
  fun errorComboBoxHiRes() {
    JBUI.setUserScaleFactor(1.75f)
    (comboBox.editor.editorComponent as JTextField).text = "Error"
    checkLook("errorComboBoxHiRes.png")
  }

  private fun checkLook(imageName: String) {
    setFieldSize()
    ImageDiffUtil.assertImagesSimilar("stdui/combobox/" + imageName, comboBox, 7.0, MAX_PERCENT_DIFFERENT)
  }

  private fun setFieldSize() {
    comboBox.size = Dimension(JBUI.scale(120), JBUI.scale(24))
    comboBox.doLayout()
  }

  @Test
  fun valueChangesArePropagatedToEditor() {
    model.value = "Moonlight"
    assertThat(comboBox.editor.item).isEqualTo("Moonlight")
  }

  @Test
  fun selectionChangesArePropagatedToEditorAndList() {
    model.selectedItem = "six"
    assertThat(comboBox.editor.item).isEqualTo("six")
    assertThat(getList().selectedValuesList).containsExactly("six")
  }

  private fun getList(): JList<*> {
    val field = BasicComboBoxUI::class.java.getDeclaredField("listBox")
    field.isAccessible = true
    return field!!.get(comboBox.ui) as JList<*>
  }
}
