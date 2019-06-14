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
package com.android.tools.property.ptable2.impl

import com.android.tools.property.ptable2.DefaultPTableCellRendererProvider
import com.android.tools.property.ptable2.PTable
import com.android.tools.property.ptable2.PTableCellEditor
import com.android.tools.property.ptable2.PTableCellEditorProvider
import com.android.tools.property.ptable2.PTableColumn
import com.android.tools.property.ptable2.PTableGroupItem
import com.android.tools.property.ptable2.PTableItem
import com.android.tools.property.ptable2.item.Group
import com.android.tools.property.ptable2.item.Item
import com.android.tools.property.ptable2.item.PTableTestModel
import com.android.tools.property.ptable2.item.createModel
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.property.testing.ApplicationRule
import com.android.tools.property.testing.RunWithTestFocusManager
import com.android.tools.property.testing.SwingFocusRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.components.JBLabel
import icons.StudioIcons
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.AWTEvent
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.UIManager
import javax.swing.event.ChangeEvent
import javax.swing.event.TableModelEvent

private const val TEXT_CELL_EDITOR = "TextCellEditor"
private const val ICON_CELL_EDITOR = "IconCellEditor"
private const val FIRST_FIELD_EDITOR = "First Editor"
private const val LAST_FIELD_EDITOR = "Last Editor"
private const val TABLE_NAME = "Table"

class PTableImplTest {
  private var model: PTableTestModel? = null
  private var table: PTableImpl? = null
  private var editorProvider: SimplePTableCellEditorProvider? = null

  @JvmField @Rule
  val appRule = ApplicationRule()

  @JvmField @Rule
  val focusRule = SwingFocusRule(appRule)

  @Before
  fun setUp() {
    // This test created some JTextField components. Do not start timers for the caret blink rate in tests:
    UIManager.put("TextField.caretBlinkRate", 0)

    editorProvider = SimplePTableCellEditorProvider()
    model = createModel(Item("weight"), Item("size"), Item("readonly"), Item("visible"), Group("weiss", Item("siphon"), Item("extra")),
                        Item("new"))
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
  fun testFilterWithParentMatch() {
    table!!.filter = "eis"
    assertThat(table!!.rowCount).isEqualTo(1)
    assertThat(table!!.item(0).name).isEqualTo("weiss")
  }

  @Test
  fun testFilterWithExpandedParentMatch() {
    table!!.filter = "eis"
    table!!.model.expand(4)
    assertThat(table!!.rowCount).isEqualTo(3)
    assertThat(table!!.item(0).name).isEqualTo("weiss")
    assertThat(table!!.item(1).name).isEqualTo("siphon")
    assertThat(table!!.item(2).name).isEqualTo("extra")
  }

  @Test
  fun testHomeNavigation() {
    table!!.model.expand(3)
    table!!.setRowSelectionInterval(4, 4)
    dispatchAction(KeyStrokes.HOME)
    assertThat(table!!.selectedRow).isEqualTo(0)
  }

  @Test
  fun testEndNavigation() {
    table!!.model.expand(4)
    table!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.END)
    assertThat(table!!.selectedRow).isEqualTo(7)
  }

  @Test
  fun testExpandAction() {
    table!!.setRowSelectionInterval(4, 4)
    dispatchAction(KeyStrokes.RIGHT)
    assertThat(table!!.rowCount).isEqualTo(8)
  }

  @Test
  fun testExpandActionWithNumericKeyboard() {
    table!!.setRowSelectionInterval(4, 4)
    dispatchAction(KeyStrokes.NUM_RIGHT)
    assertThat(table!!.rowCount).isEqualTo(8)
  }

  @Test
  fun testCollapseAction() {
    table!!.model.expand(4)
    table!!.setRowSelectionInterval(4, 4)
    dispatchAction(KeyStrokes.LEFT)
    assertThat(table!!.rowCount).isEqualTo(6)
  }

  @Test
  fun testCollapseActionWithNumericKeyboard() {
    table!!.model.expand(4)
    table!!.setRowSelectionInterval(4, 4)
    dispatchAction(KeyStrokes.NUM_LEFT)
    assertThat(table!!.rowCount).isEqualTo(6)
  }

  @Test
  fun enterExpandsClosedGroup() {
    table!!.setRowSelectionInterval(4, 4)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table!!.rowCount).isEqualTo(8)
  }

  @Test
  fun enterCollapsesOpenGroup() {
    table!!.model.expand(4)
    table!!.setRowSelectionInterval(4, 4)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table!!.rowCount).isEqualTo(6)
  }

  @Test
  fun toggleBooleanValue() {
    table!!.setRowSelectionInterval(3, 3)
    dispatchAction(KeyStrokes.SPACE)
    assertThat(editorProvider!!.editor.toggleCount).isEqualTo(1)
    assertThat(table!!.editingRow).isEqualTo(3)
    assertThat(table!!.editingColumn).isEqualTo(1)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[3])
  }

  @Test
  fun toggleNonBooleanValueIsNoop() {
    table!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.SPACE)
    assertThat(table!!.editingRow).isEqualTo(-1)
    assertThat(table!!.editingColumn).isEqualTo(-1)
    assertThat(model!!.editedItem).isNull()
  }

  @Test
  fun smartEnterStartsEditingNameColumnFirst() {
    table!!.setRowSelectionInterval(5, 5)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table!!.editingRow).isEqualTo(5)
    assertThat(table!!.editingColumn).isEqualTo(0)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[5])
  }

  @Test
  fun smartEnterStartsEditingValueIfNameIsNotEditable() {
    table!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table!!.editingRow).isEqualTo(0)
    assertThat(table!!.editingColumn).isEqualTo(1)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[0])
  }

  @Test
  fun smartEnterDoesNotToggleBooleanValue() {
    table!!.setRowSelectionInterval(3, 3)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(editorProvider!!.editor.toggleCount).isEqualTo(0)
    assertThat(table!!.editingRow).isEqualTo(3)
    assertThat(table!!.editingColumn).isEqualTo(1)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[3])
  }

  @Test
  fun startNextEditor() {
    table!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table!!.editingRow).isEqualTo(0)
    assertThat(table!!.editingColumn).isEqualTo(1)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[0])
    assertThat(table!!.startNextEditor()).isTrue()
    assertThat(table!!.editingRow).isEqualTo(1)
    assertThat(table!!.editingColumn).isEqualTo(1)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[1])
  }

  @Test
  fun startNextEditorSkipsNonEditableRows() {
    table!!.setRowSelectionInterval(1, 1)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table!!.editingRow).isEqualTo(1)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[1])
    assertThat(table!!.startNextEditor()).isTrue()
    assertThat(table!!.editingRow).isEqualTo(3)
    assertThat(table!!.editingColumn).isEqualTo(1)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[3])
  }

  @Test
  fun startNextEditorWhenAtEndOfTable() {
    table!!.model.expand(4)
    table!!.setRowSelectionInterval(7, 7)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table!!.editingRow).isEqualTo(7)
    assertThat(table!!.editingColumn).isEqualTo(0)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[5])
    assertThat(table!!.startNextEditor()).isFalse()
    assertThat(table!!.editingRow).isEqualTo(-1)
    assertThat(table!!.editingColumn).isEqualTo(-1)
    assertThat(model!!.editedItem).isNull()
  }

  @Test
  fun startNextEditorWhenNextRowAllowsNameEditing() {
    table!!.model.expand(4)
    table!!.setRowSelectionInterval(6, 6)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table!!.editingRow).isEqualTo(6)
    assertThat(model!!.editedItem).isEqualTo((model!!.items[4] as PTableGroupItem).children[1])
    assertThat(table!!.startNextEditor()).isTrue()
    assertThat(table!!.editingRow).isEqualTo(7)
    assertThat(table!!.editingColumn).isEqualTo(0)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[5])
  }

  @Test
  fun editingCanceled() {
    table!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table!!.editingRow).isEqualTo(0)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[0])
    table!!.editingCanceled(ChangeEvent(table!!))
    assertThat(table!!.editingRow).isEqualTo(-1)
    assertThat(editorProvider!!.editor.cancelCount).isEqualTo(1)
    assertThat(model!!.editedItem).isNull()
  }

  @Test
  fun editingCanceledShouldNotCancelCellEditingWhenEditorDeclines() {
    // editingCancelled should give the editor a change to
    // decide the proper action. The editor for "size" is
    // setup to decline cell editing cancellation.
    // Check that this works.
    table!!.setRowSelectionInterval(1, 1)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table!!.editingRow).isEqualTo(1)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[1])
    table!!.editingCanceled(ChangeEvent(table!!))
    assertThat(table!!.editingRow).isEqualTo(1)
    assertThat(table!!.editingColumn).isEqualTo(1)
    assertThat(editorProvider!!.editor.cancelCount).isEqualTo(1)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[1])
  }

  @Test
  fun typingStartsEditingNameIfNameIsEditable() {
    table!!.setRowSelectionInterval(5, 5)
    val event = KeyEvent(table, KeyEvent.KEY_TYPED, 0, 0, 0, 's')
    imitateFocusManagerIsDispatching(event)
    table!!.dispatchEvent(event)
    assertThat(table!!.editingRow).isEqualTo(5)
    assertThat(table!!.editingColumn).isEqualTo(0)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[5])
  }

  @Test
  fun typingIsNoopIfNeitherNameNorValueIsEditable() {
    table!!.setRowSelectionInterval(2, 2)
    val event = KeyEvent(table, KeyEvent.KEY_TYPED, 0, 0, 0, 's')
    imitateFocusManagerIsDispatching(event)
    table!!.dispatchEvent(event)
    assertThat(table!!.editingRow).isEqualTo(-1)
    assertThat(model!!.editedItem).isNull()
  }

  @Test
  fun typingStartsEditingValue() {
    table!!.setRowSelectionInterval(0, 0)
    val event = KeyEvent(table, KeyEvent.KEY_TYPED, 0, 0, 0, 's')
    imitateFocusManagerIsDispatching(event)
    table!!.dispatchEvent(event)
    assertThat(table!!.editingRow).isEqualTo(0)
    assertThat(table!!.editingColumn).isEqualTo(1)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[0])
  }

  @Test
  fun tableChangedWithEditingChangeSpec() {
    table!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table!!.editingRow).isEqualTo(0)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[0])

    table!!.tableChanged(PTableModelEvent(table!!.model, 3))
    assertThat(table!!.editingRow).isEqualTo(3)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[3])
  }

  @Test
  fun tableChangedWithEditingButWithoutChangeSpec() {
    table!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table!!.editingRow).isEqualTo(0)
    assertThat(model!!.editedItem).isEqualTo(model!!.items[0])

    table!!.tableChanged(TableModelEvent(table!!.model))
    assertThat(table!!.editingRow).isEqualTo(-1)
  }

  @Test
  fun tableChangedWithoutEditing() {
    assertThat(model!!.editedItem).isNull()

    table!!.tableChanged(PTableModelEvent(table!!.model, 3))

    // Since no row was being edited before the update, make sure no row is being edited after the update:
    assertThat(table!!.editingRow).isEqualTo(-1)
    assertThat(model!!.editedItem).isNull()
  }

  @Test
  fun testSelectionRemainsAfterRepaintEvent() {
    table!!.selectionModel.setSelectionInterval(2, 2)
    assertThat(table!!.selectedRow).isEqualTo(2)

    table!!.tableChanged(PTableModelRepaintEvent(table!!.model))
    assertThat(table!!.selectedRow).isEqualTo(2)
  }

  @Test
  fun testEditorIsRefreshedDuringRepaintEvent() {
    table!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table!!.editingRow).isEqualTo(0)

    table!!.tableChanged(PTableModelRepaintEvent(table!!.model))
    assertThat(table!!.editingRow).isEqualTo(0)
    assertThat(editorProvider!!.editor.refreshCount).isEqualTo(1)
  }

  @Test
  fun testDeleteLastLineWhenEditing() {
    table!!.setRowSelectionInterval(5, 5)
    dispatchAction(KeyStrokes.ENTER)

    model!!.updateTo(true, Item("weight"), Item("size"), Item("readonly"), Item("visible"), Group("weiss", Item("siphon"), Item("extra")))

    assertThat(table!!.selectedRow).isEqualTo(-1)
    assertThat(table!!.isEditing).isFalse()
  }

  @RunWithTestFocusManager
  @Test
  fun testNavigateForwardsIntoTable() {
    val panel = createPanel()
    panel.components[0].transferFocus()
    assertThat(table!!.editingRow).isEqualTo(0)
    assertThat(table!!.editingColumn).isEqualTo(1)
    assertThat(focusRule.focusOwner?.name).isEqualTo(TEXT_CELL_EDITOR)
  }

  @RunWithTestFocusManager
  @Test
  fun testNavigateForwardsThroughTable() {
    val panel = createPanel()
    panel.components[0].requestFocusInWindow()
    for (row in 0..5) {
      var column = 1
      if (row == 2) {
        // Make sure readonly rows are skipped
        continue
      }
      else if (row == 5) {
        // A row with an editable name should edit column 0
        column = 0
      }
      val name = "value in row $row"
      focusRule.focusOwner?.transferFocus()
      assertThat(table!!.editingRow).named(name).isEqualTo(row)
      assertThat(table!!.editingColumn).named(name).isEqualTo(column)
      assertThat(focusRule.focusOwner?.name).named(name).isEqualTo(TEXT_CELL_EDITOR)
      focusRule.focusOwner?.transferFocus()
      assertThat(table!!.editingRow).named(name).isEqualTo(row)
      assertThat(table!!.editingColumn).named(name).isEqualTo(column)
      assertThat(focusRule.focusOwner?.name).named(name).isEqualTo(ICON_CELL_EDITOR)
    }
    focusRule.focusOwner?.transferFocus()
    assertThat(table!!.isEditing).isFalse()
    assertThat(focusRule.focusOwner?.name).isEqualTo(LAST_FIELD_EDITOR)
  }

  @RunWithTestFocusManager
  @Test
  fun testNavigateBackwardsThroughTable() {
    val panel = createPanel()
    panel.components[2].requestFocusInWindow()
    for (row in 5 downTo 0) {
      var column = 1
      if (row == 2) {
        // Make sure readonly rows are skipped
        continue
      }
      else if (row == 5) {
        // A row with an editable name should edit column 0
        column = 0
      }
      val name = "value in row $row"
      focusRule.focusOwner?.transferFocusBackward()
      assertThat(table!!.editingRow).named(name).isEqualTo(row)
      assertThat(table!!.editingColumn).named(name).isEqualTo(column)
      assertThat(focusRule.focusOwner?.name).named(name).isEqualTo(ICON_CELL_EDITOR)
      focusRule.focusOwner?.transferFocusBackward()
      assertThat(table!!.editingRow).named(name).isEqualTo(row)
      assertThat(table!!.editingColumn).named(name).isEqualTo(column)
      assertThat(focusRule.focusOwner?.name).named(name).isEqualTo(TEXT_CELL_EDITOR)
    }
    focusRule.focusOwner?.transferFocusBackward()
    assertThat(table!!.isEditing).isFalse()
    assertThat(focusRule.focusOwner?.name).isEqualTo(FIRST_FIELD_EDITOR)
  }

  @RunWithTestFocusManager
  @Test
  fun testNavigateBackwardsIntoTable() {
    val panel = createPanel()
    panel.components[2].transferFocusBackward()
    assertThat(table!!.editingRow).isEqualTo(5)
    assertThat(table!!.editingColumn).isEqualTo(0)
    assertThat(focusRule.focusOwner?.name).isEqualTo(ICON_CELL_EDITOR)
  }

  private fun createPanel(): JPanel {
    val panel = JPanel()
    panel.isFocusCycleRoot = true
    panel.focusTraversalPolicy = LayoutFocusTraversalPolicy()
    panel.add(JTextField())
    panel.add(table)
    panel.add(JTextField())
    panel.components[0].name = FIRST_FIELD_EDITOR
    panel.components[1].name = TABLE_NAME
    panel.components[2].name = LAST_FIELD_EDITOR
    focusRule.setRootPeer(panel)
    return panel
  }

  private fun dispatchAction(key: KeyStroke) {
    val action = table!!.getActionForKeyStroke(key)
    action.actionPerformed(ActionEvent(table, 0, action.toString()))
  }

  private fun imitateFocusManagerIsDispatching(event: KeyEvent) {
    val field = AWTEvent::class.java.getDeclaredField("focusManagerIsDispatching")
    field.isAccessible = true
    field.set(event, true)
  }

  private inner class SimplePTableCellEditor : PTableCellEditor {
    var property: PTableItem? = null
    var column: PTableColumn? = null
    var toggleCount = 0
    var cancelCount = 0
    var refreshCount = 0

    override val editorComponent = JPanel()
    val textEditor = JTextField()
    val icon = JBLabel()

    override val value: String?
      get() = null

    override val isBooleanEditor: Boolean
      get() = property?.name == "visible"

    init {
      icon.icon = StudioIcons.LayoutEditor.Properties.TOGGLE_PROPERTIES
      icon.isFocusable = true
      icon.name = ICON_CELL_EDITOR
      textEditor.name = TEXT_CELL_EDITOR
      editorComponent.add(textEditor)
      editorComponent.add(icon)
      focusRule.setPeer(editorComponent)
    }

    override fun toggleValue() {
      toggleCount++
    }

    override fun requestFocus() {}

    override fun cancelEditing(): Boolean {
      cancelCount++
      return property?.name != "size"
    }

    override fun close(oldTable: PTable) {}

    override fun refresh() {
      refreshCount++
    }
  }

  private inner class SimplePTableCellEditorProvider : PTableCellEditorProvider {
    val editor = SimplePTableCellEditor()

    override fun invoke(table: PTable, property: PTableItem, column: PTableColumn): PTableCellEditor {
      editor.property = property
      editor.column = column
      return editor
    }
  }
}
