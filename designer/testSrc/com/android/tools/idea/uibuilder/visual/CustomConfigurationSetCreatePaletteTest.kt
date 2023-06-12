/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual

import com.intellij.ui.ComponentUtil
import com.intellij.ui.components.JBTextField
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.swing.JButton

class CustomConfigurationSetCreatePaletteTest {

  @Test
  fun testSetNameCannotBeBlank() {
    val palette = CustomConfigurationSetCreatePalette { }

    val textField = ComponentUtil.findComponentsOfType(palette, JBTextField::class.java).single()
    val addButton = ComponentUtil.findComponentsOfType(palette, JButton::class.java).single()

    textField.text = ""
    assertFalse(addButton.isEnabled)

    textField.text = "Something"
    assertTrue(addButton.isEnabled)

    // Also test the blank text
    textField.text = "    "
    assertFalse(addButton.isEnabled)
  }
}
