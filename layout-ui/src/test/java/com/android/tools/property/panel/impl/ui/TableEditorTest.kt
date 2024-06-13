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
package com.android.tools.property.panel.impl.ui

import com.android.tools.property.panel.impl.model.TableLineModelImpl
import com.android.tools.property.panel.impl.model.util.FakePTableModel
import com.android.tools.property.panel.impl.model.util.TestGroupItem
import com.android.tools.property.panel.impl.model.util.TestTableItem
import com.android.tools.property.ptable.DefaultPTableCellEditor
import com.android.tools.property.ptable.DefaultPTableCellRendererProvider
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableCellEditor
import com.android.tools.property.ptable.PTableCellEditorProvider
import com.android.tools.property.ptable.PTableColumn
import com.android.tools.property.ptable.PTableGroupItem
import com.android.tools.property.ptable.PTableItem
import com.android.tools.property.ptable.impl.PTableModelImpl
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.ClassRule
import org.junit.Test
import javax.swing.JPanel

class TableEditorTest {

  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  @Test
  fun testRequestFocusInBestMatch() {
    val editorProvider =
      object : PTableCellEditorProvider {
        val editor =
          object : DefaultPTableCellEditor() {
            override val editorComponent = JPanel()
          }

        override fun invoke(
          table: PTable,
          property: PTableItem,
          column: PTableColumn,
        ): PTableCellEditor {
          return editor
        }
      }
    val group1: PTableGroupItem =
      TestGroupItem("border", mapOf("left" to "4", "right" to "4", "top" to "8", "bottom" to "8"))
    val group2: PTableGroupItem = TestGroupItem("group2", mapOf("size" to "4dp", "tone" to "C"))
    val tableModel =
      FakePTableModel(
        true,
        mapOf("color" to "blue", "topText" to "Hello", "container" to "id2"),
        listOf(group1, group2),
      )
    val model = TableLineModelImpl(tableModel, true)
    val editor = TableEditor(model, DefaultPTableCellRendererProvider(), editorProvider)
    model.filter = "top"
    model.requestFocusInBestMatch()

    assertThat(editor.component.isEditing).isTrue()
    assertThat(editor.component.editingRow).isEqualTo(2)
    assertThat(editor.component.getValueAt(2, 1)).isEqualTo(TestTableItem("top", "8"))
  }

  @Test
  fun testSelectionOfExpandedItems() {
    val editorProvider =
      object : PTableCellEditorProvider {
        val editor =
          object : DefaultPTableCellEditor() {
            override val editorComponent = JPanel()
          }

        override fun invoke(
          table: PTable,
          property: PTableItem,
          column: PTableColumn,
        ): PTableCellEditor {
          return editor
        }
      }
    val group1: PTableGroupItem = TestGroupItem("group1", mapOf("size" to "4dp", "tone" to "C"))
    val group2: PTableGroupItem =
      TestGroupItem("border", mapOf("left" to "4", "right" to "4", "top" to "8", "bottom" to "8"))
    val tableModel =
      FakePTableModel(
        false,
        mapOf("color" to "blue", "topText" to "Hello", "container" to "id2"),
        listOf(group1, group2),
      )
    val lineModel = TableLineModelImpl(tableModel, true)
    val editor = TableEditor(lineModel, DefaultPTableCellRendererProvider(), editorProvider)
    val model = editor.component.model as PTableModelImpl
    model.expand(4)
    editor.component.selectionModel.setSelectionInterval(6, 6)
    assertThat(lineModel.selectedItem!!.name).isEqualTo("right")
    editor.component.selectionModel.clearSelection()
    assertThat(lineModel.selectedItem).isNull()
  }
}
