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

import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.swing.JList
import javax.swing.plaf.basic.BasicComboBoxUI

@RunWith(JUnit4::class)
class CommonComboBoxTest {
  private val model = DefaultCommonComboBoxModel("t", listOf("one", "two", "three", "four", "five", "six", "t"))
  private val comboBox = CommonComboBox(model)

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

  @Test
  fun testErrorStateIsSetAndResetOnComboBox() {
    model.value = "Error"
    assertThat(comboBox.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(ERROR_VALUE)
    model.value = "Fixed"
    assertThat(comboBox.getClientProperty(OUTLINE_PROPERTY)).isNull()
  }
}
