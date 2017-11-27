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

import com.android.tools.adtui.imagediff.ImageDiffUtil.assertImagesSimilar
import com.android.tools.adtui.model.stdui.DefaultCommonTextFieldModel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.KeyboardFocusManager

private const val MAX_PERCENT_DIFFERENT = 5.0f

@RunWith(JUnit4::class)
class CommonTextFieldTest {
  private val model = DefaultCommonTextFieldModel("")
  private val field = CommonTextField(model)
  private val focusManager = mock(KeyboardFocusManager::class.java)

  @Before
  fun setUp() {
    field.font = UIUtil.getFontWithFallback("Ariel", 0, 12)
  }

  @After
  fun cleanUp() {
    KeyboardFocusManager.setCurrentKeyboardFocusManager(null)
    JBUI.setUserScaleFactor(1.0f)
  }

  @Test
  fun regularTextField() {
    checkLook("regularTextField.png")
  }

  @Test
  fun focusedTextField() {
    `when`(focusManager.focusOwner).thenReturn(field)
    KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)
    checkLook("focusedTextField.png")
  }

  @Test
  fun disabledTextField() {
    model.enabled = false
    checkLook("disabledTextField.png")
  }

  @Test
  fun placeHolderTextField() {
    model.value = ""
    model.placeHolderValue = "-"
    checkLook("placeHolderTextField.png")
  }

  @Test
  fun errorTextField() {
    model.value = "Error"
    checkLook("errorTextField.png")
  }

  @Test
  fun regularTextFieldHiRes() {
    JBUI.setUserScaleFactor(1.75f)
    checkLook("regularTextFieldHiRes.png")
  }

  @Test
  fun focusedTextFieldHiRes() {
    JBUI.setUserScaleFactor(1.75f)
    `when`(focusManager.focusOwner).thenReturn(field)
    KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)
    checkLook("focusedTextFieldHiRes.png")
  }

  @Test
  fun disabledTextFieldHiRes() {
    JBUI.setUserScaleFactor(1.75f)
    model.enabled = false
    checkLook("disabledTextFieldHiRes.png")
  }

  @Test
  fun placeHolderTextFieldHiRes() {
    JBUI.setUserScaleFactor(1.75f)
    model.value = ""
    model.placeHolderValue = "-"
    checkLook("placeHolderTextFieldHiRes.png")
  }

  @Test
  fun errorTextFieldHiRes() {
    model.value = "Error"
    JBUI.setUserScaleFactor(1.75f)
    checkLook("errorTextFieldHiRes.png")
  }

  private fun checkLook(imageName: String) {
    setFieldSize()
    assertImagesSimilar("stdui/textfield/" + imageName, field, 7.0, MAX_PERCENT_DIFFERENT)
  }

  private fun setFieldSize() {
    val size = field.preferredSize
    size.width = Math.max(size.width, 120)
    field.size = size
  }
}
