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
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableColumn
import com.android.tools.property.ptable.item.PTableTestModel
import com.android.tools.property.testing.IconTester
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.ApplicationRule
import com.intellij.ui.NewUI
import icons.StudioIcons
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.SwingUtilities
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private const val TOOLTIP_PROPERTY = "JComponent.helpTooltip"

class DefaultNameTableCellRendererTest {
  @get:Rule val rules = RuleChain.outerRule(ApplicationRule()).around(IconLoaderRule())

  @Test
  fun testGetToolTipText() {
    val renderer = DefaultNameTableCellRenderer()
    val table = createTable() as PTable
    val jTable = table.component as JTable
    val item = table.item(1)
    val component =
      renderer.getEditorComponent(table, item, PTableColumn.NAME, 0, false, false, false)
    val rect = jTable.getCellRect(0, 0, true)
    component.setBounds(0, 0, rect.width, rect.height)
    component.doLayout()
    val event = MouseEvent(table.component, 0, 0L, 0, rect.width / 2, rect.height / 2, 1, false)
    val control =
      SwingUtilities.getDeepestComponentAt(component, event.x - rect.x, event.y - rect.y)
        as? JComponent
    control!!.getToolTipText(event)
    val installed = jTable.getClientProperty(TOOLTIP_PROPERTY) as HelpTooltip
    assertThat(
        installed.javaClass.getDeclaredField("title").also { it.isAccessible = true }.get(installed)
      )
      .isNull()
    assertThat(
        installed.javaClass
          .getDeclaredField("description")
          .also { it.isAccessible = true }
          .get(installed)
      )
      .isEqualTo("Help on id")

    // Cleanup by removing the tooltip:
    HelpTooltip.dispose(jTable)
  }

  @Test
  fun testNamespaceIcon() {
    IconLoader.activate()
    val renderer = DefaultNameTableCellRenderer()
    val table = createTable() as PTable
    val item = table.item(1)
    val unselected =
      renderer.getEditorComponent(table, item, PTableColumn.NAME, 0, false, false, false)
        as DefaultNameComponent
    assertThat(unselected.icon).isSameAs(StudioIcons.LayoutEditor.Properties.TOOLS_ATTRIBUTE)

    val selected =
      renderer.getEditorComponent(table, item, PTableColumn.NAME, 0, true, true, false)
        as DefaultNameComponent
    assertThat(IconTester.hasOnlyWhiteColors(selected.icon!!)).isEqualTo(!NewUI.isEnabled())
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
