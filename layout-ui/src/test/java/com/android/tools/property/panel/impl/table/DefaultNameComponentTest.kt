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
package com.android.tools.property.panel.impl.table

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.property.panel.api.TableSupport
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableItem
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.Dimension
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultNameComponentTest {

  @Test
  fun testDoubleClick() {
    var toggleCount = 0
    val tableSupport =
      object : TableSupport {
        override fun toggleGroup() {
          toggleCount++
        }
      }
    val component = DefaultNameComponent(tableSupport)
    component.size = Dimension(500, 200)
    val ui = FakeUi(component)
    ui.mouse.doubleClick(400, 100)
    assertThat(toggleCount).isEqualTo(1)
    ui.mouse.doubleClick(400, 90)
    assertThat(toggleCount).isEqualTo(2)
  }

  @Test
  fun testSetUpItemHtmlDisabling() {
    val component = DefaultNameComponent()
    val table = mock<PTable>()
    val item = mock<PTableItem>()
    whenever(item.name).thenReturn("<html>evil</html>")

    // When not expanded, html should be disabled
    component.setUpItem(table, item, depth = 0, isSelected = false, hasFocus = false, isExpanded = false)
    val layout = component.layout as BorderLayout
    val label = layout.getLayoutComponent(BorderLayout.CENTER) as JBLabel
    assertThat(label.getClientProperty("html.disable")).isEqualTo(true)
    assertThat(label.text).isEqualTo("<html>evil</html>")

    // When expanded, html should be enabled (null or false, actually null to use default)
    component.setUpItem(table, item, depth = 0, isSelected = false, hasFocus = false, isExpanded = true)
    assertThat(label.getClientProperty("html.disable")).isNull()
    assertThat(label.text).isEqualTo("<html><nobr>&lt;html&gt;evil&lt;/html&gt;</nobr></html>")
  }
}
