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
package com.android.tools.idea.common.property2.impl.ui

import com.android.tools.adtui.ptable2.DefaultPTableCellRendererProvider
import com.android.tools.adtui.ptable2.PTableColumn
import com.android.tools.adtui.ptable2.PTableItem
import com.android.tools.adtui.ptable2.PTableModel
import com.android.tools.adtui.ptable2.impl.PTableImpl
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.common.property2.impl.model.TableLineModel
import com.android.tools.idea.common.property2.impl.model.util.PropertyModelTestUtil.makeFlagsProperty
import com.android.tools.idea.common.property2.impl.table.EditorValuePanel
import com.android.tools.idea.common.property2.impl.table.PTableCellEditorProviderImpl
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.swing.JScrollPane

class FlagPropertyEditorTest {

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
    val flag1 = makeFlagsProperty("inputType", listOf("text", "date", "datetime"), listOf(1, 6, 2))
    val flag2 = makeFlagsProperty("autoLink", listOf("none", "web", "email", "phone", "all"), listOf(0, 1, 2, 4, 7))
    val flag3 = makeFlagsProperty(
      "long",
      listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen"),
      listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14))
    val tableModel = PTableTestModel(flag1, flag2, flag3)
    val lineModel = TableLineModel(tableModel, true)
    val enumSupportProvider = object : EnumSupportProvider<PropertyItem> {
      override fun invoke(property: PropertyItem): EnumSupport? {
        return null
      }
    }
    val controlTypeProvider = object : ControlTypeProvider<PropertyItem> {
      override fun invoke(property: PropertyItem): ControlType {
        return ControlType.FLAG_EDITOR
      }
    }
    val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)
    val cellEditorProvider = PTableCellEditorProviderImpl(FlagsPropertyItem::class.java, controlTypeProvider, editorProvider)
    return TableEditor(lineModel, DefaultPTableCellRendererProvider(), cellEditorProvider)
  }

  private fun getEditorFromTable(table: TableEditor, row: Int): FlagPropertyEditor {
    val swingTable = table.component as PTableImpl
    while (swingTable.editingRow < row) {
      assertThat(swingTable.startNextEditor()).isTrue()
    }
    val editorPanel = swingTable.editorComponent as EditorValuePanel
    return editorPanel.editor as FlagPropertyEditor
  }
}

private class PTableTestModel(vararg items: PTableItem) : PTableModel {
  override val items: List<PTableItem> = listOf(*items)

  override fun isCellEditable(item: PTableItem, column: PTableColumn): Boolean {
    return column == PTableColumn.VALUE
  }
}
