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
package com.android.tools.adtui.ptable2.impl

import com.android.tools.adtui.ptable2.*
import com.android.tools.adtui.ptable2.item.Group
import com.android.tools.adtui.ptable2.item.Item
import com.android.tools.adtui.ptable2.item.createModel
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.awt.event.ActionEvent
import javax.swing.JPanel
import javax.swing.event.ChangeEvent

class PTableTest {
  private var model: PTableModel? = null
  private var table: PTableImpl? = null
  private var editorProvider: SimplePTableCellEditorProvider? = null

  @Before
  fun setUp() {
    editorProvider = SimplePTableCellEditorProvider()
    model = createModel(Item("weight"), Item("size"), Item("readonly"), Item("visible"), Group("weiss", Item("siphon"), Item("extra")), Item("new"))
    table = PTableImpl(model!!, null, DefaultPTableCellRendererProvider(), editorProvider!!)
  }

  @After
  fun tearDown() {
    model = null
    table = null
    editorProvider = null
  }

  @Test
  fun testFilterWithNoMatch() {
    table!!.filter = "xyz"
    assertThat(table!!.rowCount).isEqualTo(0)
  }

  @Test
  fun testFilterWithPartialMatch() {
    table!!.filter = "iz"
    assertThat(table!!.rowCount).isEqualTo(1)
    assertThat(table!!.item(0).name).isEqualTo("size")
  }

  @Test
  fun testFilterWithGroupMatch() {
    table!!.filter = "we"
    assertThat(table!!.rowCount).isEqualTo(2)
    assertThat(table!!.item(0).name).isEqualTo("weight")
    assertThat(table!!.item(1).name).isEqualTo("weiss")
  }

  @Test
  fun testFilterWithGroupChildMatch() {
    table!!.filter = "si"
    assertThat(table!!.rowCount).isEqualTo(3)
    assertThat(table!!.item(0).name).isEqualTo("size")
    assertThat(table!!.item(1).name).isEqualTo("visible")
    assertThat(table!!.item(2).name).isEqualTo("weiss")
  }

  @Test
  fun testFilterWithExpandedGroupChildMatch() {
    table!!.filter = "si"
    table!!.model.expand(4)
    assertThat(table!!.rowCount).isEqualTo(4)
    assertThat(table!!.item(0).name).isEqualTo("size")
    assertThat(table!!.item(1).name).isEqualTo("visible")
    assertThat(table!!.item(2).name).isEqualTo("weiss")
    assertThat(table!!.item(3).name).isEqualTo("siphon")
  }

  @Test
  fun testHomeNavigation() {
    table!!.model.expand(3)
    table!!.setRowSelectionInterval(4, 4)
    table!!.setColumnSelectionInterval(1, 1)
    dispatchAction("home")
    assertThat(table!!.selectedRow).isEqualTo(0)
  }

  @Test
  fun testEndNavigation() {
    table!!.model.expand(4)
    table!!.setRowSelectionInterval(0, 0)
    dispatchAction("end")
    assertThat(table!!.selectedRow).isEqualTo(7)
  }

  @Test
  fun testExpandAction() {
    table!!.setRowSelectionInterval(4, 4)
    table!!.setColumnSelectionInterval(1, 1)
    dispatchAction("expandCurrentRight")
    assertThat(table!!.rowCount).isEqualTo(8)
  }

  @Test
  fun testCollapseAction() {
    table!!.model.expand(4)
    table!!.setRowSelectionInterval(4, 4)
    dispatchAction("collapseCurrentLeft")
    assertThat(table!!.rowCount).isEqualTo(6)
  }

  @Test
  fun enterExpandsClosedGroup() {
    table!!.setRowSelectionInterval(4, 4)
    table!!.setColumnSelectionInterval(1, 1)
    dispatchAction("toggleEditor")
    assertThat(table!!.rowCount).isEqualTo(8)
  }

  @Test
  fun enterCollapsesOpenGroup() {
    table!!.model.expand(4)
    table!!.setRowSelectionInterval(4, 4)
    dispatchAction("toggleEditor")
    assertThat(table!!.rowCount).isEqualTo(6)
  }

  @Test
  fun toggleBooleanValue() {
    table!!.setRowSelectionInterval(3, 3)
    table!!.setColumnSelectionInterval(1, 1)
    dispatchAction("toggleEditor")
    assertThat(editorProvider!!.editor.toggleCount).isEqualTo(1)
    assertThat(table!!.editingRow).isEqualTo(3)
    assertThat(table!!.editingColumn).isEqualTo(1)
  }

  @Test
  fun toggleNonBooleanValueIsNoop() {
    table!!.setRowSelectionInterval(0, 0)
    table!!.setColumnSelectionInterval(1, 1)
    dispatchAction("toggleEditor")
    assertThat(table!!.editingRow).isEqualTo(-1)
    assertThat(table!!.editingColumn).isEqualTo(-1)
  }

  @Test
  fun smartEnterStartsEditing() {
    table!!.setRowSelectionInterval(0, 0)
    table!!.setColumnSelectionInterval(1, 1)
    dispatchAction("smartEnter")
    assertThat(table!!.editingRow).isEqualTo(0)
    assertThat(table!!.editingColumn).isEqualTo(1)
  }

  @Test
  fun smartEnterDoesNotToggleBooleanValue() {
    table!!.setRowSelectionInterval(3, 3)
    table!!.setColumnSelectionInterval(1, 1)
    dispatchAction("smartEnter")
    assertThat(editorProvider!!.editor.toggleCount).isEqualTo(0)
    assertThat(table!!.editingRow).isEqualTo(3)
    assertThat(table!!.editingColumn).isEqualTo(1)
  }

  @Test
  fun startNextEditor() {
    table!!.setRowSelectionInterval(0, 0)
    table!!.setColumnSelectionInterval(1, 1)
    dispatchAction("smartEnter")
    assertThat(table!!.editingRow).isEqualTo(0)
    assertThat(table!!.startNextEditor()).isTrue()
    assertThat(table!!.editingRow).isEqualTo(1)
    assertThat(table!!.editingColumn).isEqualTo(1)
  }

  @Test
  fun startNextEditorSkipsNonEditableRows() {
    table!!.setRowSelectionInterval(1, 1)
    table!!.setColumnSelectionInterval(1, 1)
    dispatchAction("smartEnter")
    assertThat(table!!.editingRow).isEqualTo(1)
    assertThat(table!!.startNextEditor()).isTrue()
    assertThat(table!!.editingRow).isEqualTo(3)
    assertThat(table!!.editingColumn).isEqualTo(1)
  }

  @Test
  fun startNextEditorWhenAtEndOfTable() {
    table!!.model.expand(4)
    table!!.setRowSelectionInterval(7, 7)
    table!!.setColumnSelectionInterval(1, 1)
    dispatchAction("smartEnter")
    assertThat(table!!.editingRow).isEqualTo(7)
    assertThat(table!!.startNextEditor()).isFalse()
    assertThat(table!!.editingRow).isEqualTo(-1)
    assertThat(table!!.editingColumn).isEqualTo(-1)
  }

  @Test
  fun startNextEditorWhenNextRowAllowsNameEditing() {
    table!!.model.expand(4)
    table!!.setRowSelectionInterval(6, 6)
    table!!.setColumnSelectionInterval(1, 1)
    dispatchAction("smartEnter")
    assertThat(table!!.editingRow).isEqualTo(6)
    assertThat(table!!.startNextEditor()).isTrue()
    assertThat(table!!.editingRow).isEqualTo(7)
    assertThat(table!!.editingColumn).isEqualTo(0)
  }

  @Test
  fun startNextEditorWhenCurrentRowAllowsNameEditing() {
    table!!.model.expand(4)
    table!!.setRowSelectionInterval(7, 7)
    table!!.setColumnSelectionInterval(0, 0)
    dispatchAction("smartEnter")
    assertThat(table!!.editingRow).isEqualTo(7)
    assertThat(table!!.startNextEditor()).isTrue()
    assertThat(table!!.editingRow).isEqualTo(7)
    assertThat(table!!.editingColumn).isEqualTo(1)
  }

  @Test
  fun editingCanceledShouldNotCancelCellEditing() {
    // editingCancelled should give the editor a change to
    // decide the proper action. The table should not stop
    // cell editing as the default JTable does.
    table!!.model.expand(4)
    table!!.setRowSelectionInterval(6, 6)
    table!!.setColumnSelectionInterval(1, 1)
    dispatchAction("smartEnter")
    assertThat(table!!.editingRow).isEqualTo(6)
    table!!.editingCanceled(ChangeEvent(table!!))
    assertThat(table!!.editingRow).isEqualTo(6)
    assertThat(table!!.editingColumn).isEqualTo(1)
    assertThat(editorProvider!!.editor.cancelCount).isEqualTo(1)
  }

  private fun dispatchAction(action: String) {
    table!!.actionMap[action].actionPerformed(ActionEvent(table, 0, action))
  }
}

private class SimplePTableCellEditor : PTableCellEditor {
  var property: PTableItem? = null
  var column: PTableColumn? = null
  var toggleCount = 0
  var cancelCount = 0

  override val editorComponent = JPanel()

  override val value: String?
    get() = null

  override val isBooleanEditor: Boolean
    get() = property?.name == "visible"

  override fun toggleValue() {
    toggleCount++
  }

  override fun requestFocus() {}

  override fun cancelEditing() {
    cancelCount++
  }

  override fun close(oldTable: PTable) {}
}

private class SimplePTableCellEditorProvider : PTableCellEditorProvider {
  val editor = SimplePTableCellEditor()

  override fun invoke(table: PTable, property: PTableItem, column: PTableColumn): PTableCellEditor {
    editor.property = property
    editor.column = column
    return editor
  }
}

