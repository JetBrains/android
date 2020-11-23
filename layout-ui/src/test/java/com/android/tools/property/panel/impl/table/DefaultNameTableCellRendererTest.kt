/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.android.tools.property.panel.impl.ui.PropertyTooltip
import com.android.tools.property.ptable2.PTable
import com.android.tools.property.ptable2.PTableColumn
import com.android.tools.property.ptable2.item.PTableTestModel
import com.android.tools.property.testing.ApplicationRule
import com.android.tools.property.testing.IconTester
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.util.IconLoader
import icons.StudioIcons
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.SwingUtilities

class DefaultNameTableCellRendererTest {

  @JvmField @Rule
  val appRule = ApplicationRule()

  var manager: IdeTooltipManager? = null

  @Before
  fun setUp() {
    manager = mock(IdeTooltipManager::class.java)
    appRule.testApplication.registerService(IdeTooltipManager::class.java, manager!!)
  }

  @After
  fun tearDown() {
    manager = null
  }

  @Test
  fun testGetToolTipText() {
    val renderer = DefaultNameTableCellRenderer()
    val table = createTable() as PTable
    val jTable = table.component as JTable
    val item = table.item(1)
    val component = renderer.getEditorComponent(table, item, PTableColumn.NAME, 0, false, false, false)
    val rect = jTable.getCellRect(0, 0, true)
    component.setBounds(0, 0, rect.width, rect.height)
    component.doLayout()
    val event = MouseEvent(table.component, 0, 0L, 0, rect.width / 2, rect.height / 2, 1, false)
    val control = SwingUtilities.getDeepestComponentAt(component, event.x - rect.x, event.y - rect.y) as? JComponent
    control!!.getToolTipText(event)

    val captor = ArgumentCaptor.forClass(PropertyTooltip::class.java)
    verify(manager!!).setCustomTooltip(any(JComponent::class.java), captor.capture())
    val tip = captor.value.tip
    assertThat(tip.text).isEqualTo("<html>Help on id</html>")
  }

  @Test
  fun testNamespaceIcon() {
    IconLoader.activate()
    val renderer = DefaultNameTableCellRenderer()
    val table = createTable() as PTable
    val item = table.item(1)
    val unselected = renderer.getEditorComponent(table, item, PTableColumn.NAME, 0, false, false, false) as DefaultNameComponent
    assertThat(unselected.icon).isSameAs(StudioIcons.LayoutEditor.Properties.TOOLS_ATTRIBUTE)

    val selected = renderer.getEditorComponent(table, item, PTableColumn.NAME, 0, true, true, false) as DefaultNameComponent
    assertThat(IconTester.hasOnlyWhiteColors(selected.icon!!)).isTrue()
  }

  private fun createTable(): JTable {
    val property1 = FakePropertyItem(ANDROID_URI, ATTR_ID, "@id/text1")
    val property2 = FakePropertyItem(TOOLS_URI, ATTR_TEXT, "Hello")
    val model = PTableTestModel(property1, property2)
    property1.tooltipForName = "Help on id"
    property1.tooltipForValue = "Help on id value"
    property2.tooltipForName = "Help on text"
    property2.tooltipForValue = "Help on text value"
    return PTable.create(model) as JTable
  }
}
