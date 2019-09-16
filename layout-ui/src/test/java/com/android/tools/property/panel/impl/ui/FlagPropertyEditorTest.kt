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
package com.android.tools.property.panel.impl.ui

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_INPUT_TYPE
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.FlagsPropertyItem
import com.android.tools.property.panel.api.NewPropertyItem
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.impl.model.TableLineModelImpl
import com.android.tools.property.panel.impl.model.util.FakeFlagsPropertyItem
import com.android.tools.property.panel.impl.support.SimpleControlTypeProvider
import com.android.tools.property.panel.impl.table.EditorPanel
import com.android.tools.property.panel.impl.table.PTableCellEditorProviderImpl
import com.android.tools.property.ptable2.DefaultPTableCellRendererProvider
import com.android.tools.property.ptable2.PTableColumn
import com.android.tools.property.ptable2.PTableItem
import com.android.tools.property.ptable2.PTableModel
import com.android.tools.property.ptable2.impl.PTableImpl
import com.android.tools.property.testing.PropertyAppRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import javax.swing.JScrollPane

class FlagPropertyEditorTest {

  @JvmField @Rule
  val appRule = PropertyAppRule()

  @Before
  fun setUp() {
    `when`(ActionManager.getInstance().getAction(IdeActions.ACTION_CLEAR_TEXT)).thenReturn(SomeAction("ClearText"))
    `when`(DataManager.getInstance().getDataContext(ArgumentMatchers.any())).thenReturn(mock(DataContext::class.java))
  }

  @Test
  fun testRestoreComponentIsTableWhenEditingFlagPropertyInTable() {
    val table = createTableWithFlagEditors()
    val flagEditor = getEditorFromTable(table, 1)
    assertThat(flagEditor.tableParent).isEqualTo(table.component)
  }

  @Test
  fun testShortCheckBoxListIsNotScrollable() {
    val table = createTableWithFlagEditors()
    val flagEditor = getEditorFromTable(table, 1)
    val panel = FlagPropertyPanel(flagEditor.editorModel, flagEditor.tableParent!!, 400)
    val scrollPane = panel.getComponent(2) as JScrollPane
    assertThat(scrollPane.isPreferredSizeSet).isFalse()
  }

  @Test
  fun testLongCheckBoxListIsScrollable() {
    val table = createTableWithFlagEditors()
    val flagEditor = getEditorFromTable(table, 2)
    val panel = FlagPropertyPanel(flagEditor.editorModel, flagEditor.tableParent!!, 400)
    val scrollPane = panel.getComponent(2) as JScrollPane
    assertThat(scrollPane.isPreferredSizeSet).isTrue()
    assertThat(scrollPane.preferredSize.height).isLessThan(400)
  }

  @Test
  fun testPanelWillRestoreEditingInTable() {
    val table = createTableWithFlagEditors()
    val flagEditor = getEditorFromTable(table, 1)
    val panel = FlagPropertyPanel(flagEditor.editorModel, flagEditor.tableParent!!, 400)
    val swingTable = table.component as PTableImpl
    swingTable.removeEditor()
    assertThat(swingTable.editingRow).isEqualTo(-1)
    panel.hideBalloonAndRestoreFocusOnEditor()
    assertThat(swingTable.editingRow).isEqualTo(1)
  }

  private fun createTableWithFlagEditors(): TableEditor {
    val flag1 = FakeFlagsPropertyItem(ANDROID_URI, ATTR_INPUT_TYPE, listOf("text", "date", "datetime"), listOf(1, 6, 2))
    val flag2 = FakeFlagsPropertyItem(ANDROID_URI, "autoLink", listOf("none", "web", "email", "phone", "all"), listOf(0, 1, 2, 4, 7))
    val flag3 = FakeFlagsPropertyItem(
      ANDROID_URI,
      "long",
      listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen"),
      listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14))
    val tableModel = PTableTestModel(flag1, flag2, flag3)
    val lineModel = TableLineModelImpl(tableModel, true)
    val enumSupportProvider = object : EnumSupportProvider<PropertyItem> {
      override fun invoke(property: PropertyItem): EnumSupport? {
        return null
      }
    }
    val controlTypeProvider = SimpleControlTypeProvider<PropertyItem>(ControlType.FLAG_EDITOR)
    val nameControlTypeProvider = SimpleControlTypeProvider<NewPropertyItem>(ControlType.TEXT_EDITOR)
    val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)
    val cellEditorProvider = PTableCellEditorProviderImpl(
      NewPropertyItem::class.java, nameControlTypeProvider, EditorProvider.createForNames(),
      FlagsPropertyItem::class.java, controlTypeProvider, editorProvider)
    return TableEditor(lineModel, DefaultPTableCellRendererProvider(), cellEditorProvider)
  }

  private fun getEditorFromTable(table: TableEditor, row: Int): FlagPropertyEditor {
    val swingTable = table.component as PTableImpl
    while (swingTable.editingRow < row) {
      assertThat(swingTable.startNextEditor()).isTrue()
    }
    val editorPanel = swingTable.editorComponent as EditorPanel
    return editorPanel.editor as FlagPropertyEditor
  }
}

private class PTableTestModel(vararg items: PTableItem) : PTableModel {
  override var editedItem: PTableItem? = null
  override val items = mutableListOf(*items)

  override fun isCellEditable(item: PTableItem, column: PTableColumn): Boolean {
    return column == PTableColumn.VALUE
  }

  override fun addItem(item: PTableItem): PTableItem {
    items.add(item)
    return item
  }

  override fun removeItem(item: PTableItem) {
    items.remove(item)
  }
}

private class SomeAction internal constructor(title: String) : AnAction(title) {
  override fun actionPerformed(e: AnActionEvent) {}
}
