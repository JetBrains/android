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
import com.android.SdkConstants.ATTR_TEXT
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.property.panel.api.EditorContext
import com.android.tools.property.panel.impl.model.TextFieldWithLeftButtonEditorModel
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.ClassRule
import org.junit.Test
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTextField

class PropertyTextFieldWithLeftButtonTest {

  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  @Test
  fun testDoubleClickOnTextFieldIsHandledByContainer() {
    val item = FakePropertyItem(ANDROID_URI, ATTR_TEXT)
    val model = TextFieldWithLeftButtonEditorModel(item, true)
    val container = PropertyTextFieldWithLeftButton(model, EditorContext.STAND_ALONE_EDITOR)

    // The editor should have a JTextField and not a PropertyLabel:
    assertThat(container.components.single { it is JTextField }).isNotNull()
    assertThat(container.components.singleOrNull { it is PropertyLabel }).isNull()

    var toggleCount = 0
    container.addMouseListener(
      object : MouseAdapter() {
        override fun mouseClicked(event: MouseEvent) {
          if (event.clickCount > 1) {
            toggleCount++
          }
        }
      }
    )
    container.size = Dimension(500, 200)
    container.doLayout()
    val textField = (container.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
    // The editor should have a JTextField and not a PropertyLabel:
    assertThat(textField).isInstanceOf(JTextField::class.java)
    val ui = FakeUi(textField)
    ui.mouse.doubleClick(400, 100)
    assertThat(toggleCount).isEqualTo(1)
    ui.mouse.doubleClick(400, 100)
    assertThat(toggleCount).isEqualTo(2)
  }

  @Test
  fun testRendererUsesLabel() {
    val item = FakePropertyItem(ANDROID_URI, ATTR_TEXT)
    val model = TextFieldWithLeftButtonEditorModel(item, true)
    val container = PropertyTextFieldWithLeftButton(model, EditorContext.TABLE_RENDERER)
    container.size = Dimension(500, 200)
    container.doLayout()

    // The renderer should have a PropertyLabel and not a JTextField:
    assertThat(container.components.single { it is PropertyLabel }).isNotNull()
    assertThat(container.components.singleOrNull { it is JTextField }).isNull()
  }
}
