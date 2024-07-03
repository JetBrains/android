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
package com.android.tools.property.ptable.impl

import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.laf.HeadlessTableUI
import com.android.tools.property.ptable.ColumnFraction
import com.android.tools.property.ptable.DefaultPTableCellRenderer
import com.android.tools.property.ptable.KEY_IS_VISUALLY_RESTRICTED
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableCellEditor
import com.android.tools.property.ptable.PTableCellEditorProvider
import com.android.tools.property.ptable.PTableCellRenderer
import com.android.tools.property.ptable.PTableCellRendererProvider
import com.android.tools.property.ptable.PTableColumn
import com.android.tools.property.ptable.PTableGroupItem
import com.android.tools.property.ptable.PTableItem
import com.android.tools.property.ptable.item.Group
import com.android.tools.property.ptable.item.Item
import com.android.tools.property.ptable.item.PTableTestModel
import com.android.tools.property.ptable.item.createModel
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.PassThroughIdeFocusManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.ClientProperty
import com.intellij.ui.TableCell
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.height
import com.intellij.ui.util.width
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.TransferHandler
import javax.swing.UIManager
import javax.swing.event.ChangeEvent
import javax.swing.event.TableModelEvent

private const val TEXT_CELL_EDITOR = "TextCellEditor"
private const val ICON_CELL_EDITOR = "IconCellEditor"
private const val FIRST_FIELD_EDITOR = "First Editor"
private const val LAST_FIELD_EDITOR = "Last Editor"
private const val TABLE_NAME = "Table"

@RunsInEdt
class PTableImplTest {
  private var model1: PTableTestModel? = null
  private var table1: PTableImpl? = null
  private var model2: PTableTestModel? = null
  private var table2: PTableImpl? = null
  private var model3: PTableTestModel? = null
  private var table3: PTableImpl? = null
  private var scrollPane: JBScrollPane? = null
  private var nameColumnFraction: ColumnFraction? = null
  private var rendererProvider: SimplePTableCellRendererProvider? = null
  private var editorProvider: SimplePTableCellEditorProvider? = null

  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  @get:Rule val disposableRule = DisposableRule()

  @get:Rule val iconLoader = IconLoaderRule()

  @get:Rule val edtRule = EdtRule()

  @Before
  fun setUp() {
    // This test created some JTextField components. Do not start timers for the caret blink rate in
    // tests:
    UIManager.put("TextField.caretBlinkRate", 0)

    nameColumnFraction = ColumnFraction(initialValue = 0.5f, resizeSupported = true)
    rendererProvider = SimplePTableCellRendererProvider()
    editorProvider = SimplePTableCellEditorProvider()
    model1 =
      createModel(
        Item("weight"),
        Item("size"),
        Item("readonly"),
        Item("visible", "true"),
        Group("weiss", Item("siphon"), Item("extra"), Group("flower", Item("rose"))),
        Item("new"),
      )
    table1 =
      PTableImpl(
        model1!!,
        null,
        rendererProvider!!,
        editorProvider!!,
        nameColumnFraction = nameColumnFraction!!,
      )
    model2 =
      createModel(Item("minWidth"), Item("maxWidth"), Item("minHeight", "9dp"), Item("maxHeight"))
    table2 =
      PTableImpl(
        model2!!,
        null,
        rendererProvider!!,
        editorProvider!!,
        nameColumnFraction = nameColumnFraction!!,
      )
    model3 =
      createModel(
        Item("paddingLeft"),
        Item("paddingRight"),
        Item("elevation"),
        Item("enabled", "true"),
        Item("gravity"),
      )
    table3 =
      PTableImpl(
        model3!!,
        null,
        rendererProvider!!,
        editorProvider!!,
        nameColumnFraction = nameColumnFraction!!,
      )
    table1!!.nextTable = table2
    table2!!.nextTable = table3
    table3!!.previousTable = table2
    table2!!.previousTable = table1
    table1!!.rowHeight = 20
    table2!!.rowHeight = 20
    table3!!.rowHeight = 20
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
    panel.isOpaque = false
    panel.add(table1)
    panel.add(table2)
    panel.add(table3)
    scrollPane = JBScrollPane(panel)
    val app = ApplicationManager.getApplication()
    app.replaceService(
      IdeFocusManager::class.java,
      PassThroughIdeFocusManager.getInstance(),
      disposableRule.disposable,
    )
  }

  @After
  fun tearDown() {
    model1 = null
    table1 = null
    editorProvider = null
  }

  @Test
  fun testFilterWithNoMatch() {
    table1!!.filter = "xyz"
    assertThat(table1!!.rowCount).isEqualTo(0)
  }

  @Test
  fun testFilterWithPartialMatch() {
    table1!!.filter = "iz"
    assertThat(table1!!.rowCount).isEqualTo(1)
    assertThat(table1!!.item(0).name).isEqualTo("size")
  }

  @Test
  fun testFilterWithGroupMatch() {
    table1!!.filter = "we"
    assertThat(table1!!.rowCount).isEqualTo(2)
    assertThat(table1!!.item(0).name).isEqualTo("weight")
    assertThat(table1!!.item(1).name).isEqualTo("weiss")
  }

  @Test
  fun testFilterWithGroupChildMatch() {
    table1!!.filter = "si"
    assertThat(table1!!.rowCount).isEqualTo(3)
    assertThat(table1!!.item(0).name).isEqualTo("size")
    assertThat(table1!!.item(1).name).isEqualTo("visible")
    assertThat(table1!!.item(2).name).isEqualTo("weiss")
  }

  @Test
  fun testFilterWithExpandedGroupChildMatch() {
    table1!!.filter = "si"
    table1!!.model.expand(4)
    assertThat(table1!!.rowCount).isEqualTo(4)
    assertThat(table1!!.item(0).name).isEqualTo("size")
    assertThat(table1!!.item(1).name).isEqualTo("visible")
    assertThat(table1!!.item(2).name).isEqualTo("weiss")
    assertThat(table1!!.item(3).name).isEqualTo("siphon")
  }

  @Test
  fun testFilterWithParentMatch() {
    table1!!.filter = "eis"
    assertThat(table1!!.rowCount).isEqualTo(1)
    assertThat(table1!!.item(0).name).isEqualTo("weiss")
  }

  @Test
  fun testFilterWithExpandedParentMatch() {
    table1!!.filter = "eis"
    table1!!.model.expand(4)
    assertThat(table1!!.rowCount).isEqualTo(4)
    assertThat(table1!!.item(0).name).isEqualTo("weiss")
    assertThat(table1!!.item(1).name).isEqualTo("siphon")
    assertThat(table1!!.item(2).name).isEqualTo("extra")
    assertThat(table1!!.item(3).name).isEqualTo("flower")
  }

  @Test
  fun testHomeNavigation() {
    table1!!.model.expand(3)
    table1!!.setRowSelectionInterval(4, 4)
    dispatchAction(KeyStrokes.HOME)
    assertThat(table1!!.selectedRow).isEqualTo(0)
  }

  @Test
  fun testEndNavigation() {
    table1!!.model.expand(4)
    table1!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.END)
    assertThat(table1!!.selectedRow).isEqualTo(8)
  }

  @Test
  fun testExpandAction() {
    table1!!.setRowSelectionInterval(4, 4)
    dispatchAction(KeyStrokes.RIGHT)
    assertThat(table1!!.rowCount).isEqualTo(9)
  }

  @Test
  fun testExpandActionWithNumericKeyboard() {
    table1!!.setRowSelectionInterval(4, 4)
    dispatchAction(KeyStrokes.NUM_RIGHT)
    assertThat(table1!!.rowCount).isEqualTo(9)
  }

  @Test
  fun testCollapseAction() {
    table1!!.model.expand(4)
    table1!!.setRowSelectionInterval(4, 4)
    assertThat(table1!!.rowCount).isEqualTo(9)
    dispatchAction(KeyStrokes.LEFT)
    assertThat(table1!!.rowCount).isEqualTo(6)
  }

  @Test
  fun testCollapseActionWithActiveValueEditor() {
    table1!!.model.expand(4)
    table1!!.setRowSelectionInterval(4, 4)
    assertThat(table1!!.rowCount).isEqualTo(9)
    table1!!.editingRow = 4
    table1!!.editingColumn = 1
    dispatchAction(KeyStrokes.LEFT)
    assertThat(table1!!.rowCount).isEqualTo(9)
  }

  @Test
  fun testCollapseActionWithNumericKeyboard() {
    table1!!.model.expand(4)
    table1!!.setRowSelectionInterval(4, 4)
    dispatchAction(KeyStrokes.NUM_LEFT)
    assertThat(table1!!.rowCount).isEqualTo(6)
  }

  @Test
  fun enterExpandsClosedGroup() {
    table1!!.setRowSelectionInterval(4, 4)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table1!!.rowCount).isEqualTo(9)
  }

  @Test
  fun enterCollapsesOpenGroup() {
    table1!!.model.expand(4)
    table1!!.setRowSelectionInterval(4, 4)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table1!!.rowCount).isEqualTo(6)
  }

  @Test
  fun toggleBooleanValue() {
    table1!!.setRowSelectionInterval(3, 3)
    dispatchAction(KeyStrokes.SPACE)
    assertThat(editorProvider!!.editor.toggleCount).isEqualTo(1)
    assertThat(table1!!.editingRow).isEqualTo(3)
    assertThat(table1!!.editingColumn).isEqualTo(1)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[3])
  }

  @Test
  fun toggleNonBooleanValueIsNoop() {
    table1!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.SPACE)
    assertThat(table1!!.editingRow).isEqualTo(-1)
    assertThat(table1!!.editingColumn).isEqualTo(-1)
    assertThat(model1!!.editedItem).isNull()
  }

  @Test
  fun smartEnterStartsEditingNameColumnFirst() {
    table1!!.setRowSelectionInterval(5, 5)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table1!!.editingRow).isEqualTo(5)
    assertThat(table1!!.editingColumn).isEqualTo(0)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[5])
  }

  @Test
  fun smartEnterStartsEditingValueIfNameIsNotEditable() {
    table1!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table1!!.editingRow).isEqualTo(0)
    assertThat(table1!!.editingColumn).isEqualTo(1)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[0])
  }

  @Test
  fun smartEnterDoesNotToggleBooleanValue() {
    table1!!.setRowSelectionInterval(3, 3)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(editorProvider!!.editor.toggleCount).isEqualTo(0)
    assertThat(table1!!.editingRow).isEqualTo(3)
    assertThat(table1!!.editingColumn).isEqualTo(1)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[3])
  }

  @Test
  fun startNextEditor() {
    table1!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table1!!.editingRow).isEqualTo(0)
    assertThat(table1!!.editingColumn).isEqualTo(1)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[0])
    assertThat(table1!!.startNextEditor()).isTrue()
    assertThat(table1!!.editingRow).isEqualTo(1)
    assertThat(table1!!.editingColumn).isEqualTo(1)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[1])
  }

  @Test
  fun startNextEditorSkipsNonEditableRows() {
    table1!!.setRowSelectionInterval(1, 1)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table1!!.editingRow).isEqualTo(1)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[1])
    assertThat(table1!!.startNextEditor()).isTrue()
    assertThat(table1!!.editingRow).isEqualTo(3)
    assertThat(table1!!.editingColumn).isEqualTo(1)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[3])
  }

  @Test
  fun startNextEditorWhenAtEndOfTable() {
    table1!!.model.expand(4)
    table1!!.setRowSelectionInterval(8, 8)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table1!!.editingRow).isEqualTo(8)
    assertThat(table1!!.editingColumn).isEqualTo(0)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[5])
    assertThat(table1!!.startNextEditor()).isFalse()
    assertThat(table1!!.editingRow).isEqualTo(-1)
    assertThat(table1!!.editingColumn).isEqualTo(-1)
    assertThat(model1!!.editedItem).isNull()
  }

  @Test
  fun startNextEditorWhenNextRowAllowsNameEditing() {
    table1!!.model.expand(4)
    table1!!.setRowSelectionInterval(6, 6)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table1!!.editingRow).isEqualTo(6)
    assertThat(model1!!.editedItem).isEqualTo((model1!!.items[4] as PTableGroupItem).children[1])
    assertThat(table1!!.startNextEditor()).isTrue()
    assertThat(table1!!.startNextEditor()).isTrue()
    assertThat(table1!!.editingRow).isEqualTo(8)
    assertThat(table1!!.item(8).name).isEqualTo("new")
    assertThat(table1!!.editingColumn).isEqualTo(0)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[5])
  }

  @Test
  fun editingCanceled() {
    table1!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table1!!.editingRow).isEqualTo(0)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[0])
    table1!!.editingCanceled(ChangeEvent(table1!!))
    assertThat(table1!!.editingRow).isEqualTo(-1)
    assertThat(editorProvider!!.editor.cancelCount).isEqualTo(1)
    assertThat(model1!!.editedItem).isNull()
  }

  @Test
  fun editingCanceledShouldNotCancelCellEditingWhenEditorDeclines() {
    // editingCancelled should give the editor a change to
    // decide the proper action. The editor for "size" is
    // setup to decline cell editing cancellation.
    // Check that this works.
    table1!!.setRowSelectionInterval(1, 1)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table1!!.editingRow).isEqualTo(1)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[1])
    table1!!.editingCanceled(ChangeEvent(table1!!))
    assertThat(table1!!.editingRow).isEqualTo(1)
    assertThat(table1!!.editingColumn).isEqualTo(1)
    assertThat(editorProvider!!.editor.cancelCount).isEqualTo(1)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[1])
  }

  @Test
  fun typingStartsEditingNameIfNameIsEditable() {
    table1!!.setRowSelectionInterval(5, 5)
    val event = KeyEvent(table1, KeyEvent.KEY_TYPED, 0, 0, 0, 's')
    imitateFocusManagerIsDispatching(event)
    table1!!.dispatchEvent(event)
    assertThat(table1!!.editingRow).isEqualTo(5)
    assertThat(table1!!.editingColumn).isEqualTo(0)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[5])
  }

  @Test
  fun typingSpaceDoesNotStartEditing() {
    table1!!.setRowSelectionInterval(5, 5)
    val event = KeyEvent(table1, KeyEvent.KEY_TYPED, 0, 0, 0, ' ')
    imitateFocusManagerIsDispatching(event)
    table1!!.dispatchEvent(event)
    assertThat(table1!!.editingRow).isEqualTo(-1)
    assertThat(model1!!.editedItem).isNull()
  }

  @Test
  fun typingIsNoopIfNeitherNameNorValueIsEditable() {
    table1!!.setRowSelectionInterval(2, 2)
    val event = KeyEvent(table1, KeyEvent.KEY_TYPED, 0, 0, 0, 's')
    imitateFocusManagerIsDispatching(event)
    table1!!.dispatchEvent(event)
    assertThat(table1!!.editingRow).isEqualTo(-1)
    assertThat(model1!!.editedItem).isNull()
  }

  @Test
  fun typingStartsEditingValue() {
    table1!!.setRowSelectionInterval(0, 0)
    val event = KeyEvent(table1, KeyEvent.KEY_TYPED, 0, 0, 0, 's')
    imitateFocusManagerIsDispatching(event)
    table1!!.dispatchEvent(event)
    assertThat(table1!!.editingRow).isEqualTo(0)
    assertThat(table1!!.editingColumn).isEqualTo(1)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[0])
  }

  @Test
  fun resizeRowHeight() {
    table1!!.setRowSelectionInterval(5, 5)
    val item = table1!!.item(5)
    val event = KeyEvent(table1, KeyEvent.KEY_TYPED, 0, 0, 0, 's')
    imitateFocusManagerIsDispatching(event)
    table1!!.dispatchEvent(event)
    assertThat(table1!!.editingRow).isEqualTo(5)
    val editor = table1!!.editorComponent as SimpleEditorComponent
    table1!!.updateRowHeight(item, PTableColumn.VALUE, editor, false)
    assertThat(table1!!.getRowHeight(5)).isEqualTo(404)

    table1!!.removeEditor()
    table1!!.updateRowHeight(item, PTableColumn.VALUE, editor, false)
    assertThat(table1!!.getRowHeight(5)).isEqualTo(404)
  }

  @Test
  fun tableChangedWithEditingChangeSpec() {
    table1!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table1!!.editingRow).isEqualTo(0)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[0])

    table1!!.tableChanged(PTableModelEvent(table1!!.model, 3))
    assertThat(table1!!.editingRow).isEqualTo(3)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[3])
  }

  @Test
  fun tableChangedWithEditingButWithoutChangeSpec() {
    table1!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table1!!.editingRow).isEqualTo(0)
    assertThat(model1!!.editedItem).isEqualTo(model1!!.items[0])

    table1!!.tableChanged(TableModelEvent(table1!!.model))
    assertThat(table1!!.editingRow).isEqualTo(0)
  }

  @Test
  fun tableChangedWithoutEditing() {
    assertThat(model1!!.editedItem).isNull()

    table1!!.tableChanged(PTableModelEvent(table1!!.model, 3))

    // Since no row was being edited before the update, make sure no row is being edited after the
    // update:
    assertThat(table1!!.editingRow).isEqualTo(-1)
    assertThat(model1!!.editedItem).isNull()
  }

  @Test
  fun testSelectionRemainsAfterRepaintEvent() {
    table1!!.selectionModel.setSelectionInterval(2, 2)
    assertThat(table1!!.selectedRow).isEqualTo(2)

    table1!!.tableChanged(PTableModelRepaintEvent(table1!!.model))
    assertThat(table1!!.selectedRow).isEqualTo(2)
  }

  @Test
  fun testEditorIsRefreshedDuringRepaintEvent() {
    table1!!.setRowSelectionInterval(0, 0)
    dispatchAction(KeyStrokes.ENTER)
    assertThat(table1!!.editingRow).isEqualTo(0)

    table1!!.tableChanged(PTableModelRepaintEvent(table1!!.model))
    assertThat(table1!!.editingRow).isEqualTo(0)
    assertThat(editorProvider!!.editor.refreshCount).isEqualTo(1)
  }

  @Test
  fun testDeleteLastLineWhenEditing() {
    table1!!.setRowSelectionInterval(5, 5)
    dispatchAction(KeyStrokes.ENTER)

    model1!!.updateTo(
      true,
      Item("weight"),
      Item("size"),
      Item("readonly"),
      Item("visible"),
      Group("weiss", Item("siphon"), Item("extra")),
    )

    assertThat(table1!!.selectedRow).isEqualTo(-1)
    assertThat(table1!!.isEditing).isFalse()
  }

  @Test
  fun testNavigateForwardsIntoTable() {
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    val panel = createPanel()
    FakeUi(panel, createFakeWindow = true)
    focusManager.focusOwner = panel
    panel.components[0].transferFocus()
    assertThat(table1!!.editingRow).isEqualTo(0)
    assertThat(table1!!.editingColumn).isEqualTo(1)
    assertThat(focusManager.focusOwner?.name).isEqualTo(TEXT_CELL_EDITOR)
  }

  @Test
  fun testNavigateForwardsIntoReadOnlyTable() {
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    model1!!.readOnly = true
    val panel = createPanel()
    FakeUi(panel, createFakeWindow = true)
    focusManager.focusOwner = panel
    panel.components[0].transferFocus()
    assertThat(table1!!.isEditing).isFalse()
    assertThat(table1!!.selectedRow).isEqualTo(0)
    assertThat(focusManager.focusOwner?.name).isEqualTo(TABLE_NAME)
    focusManager.focusOwner?.transferFocus()
    assertThat(focusManager.focusOwner?.name).isEqualTo(LAST_FIELD_EDITOR)
  }

  @Test
  fun testNavigateForwardsThroughTable() {
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    val panel = createPanel()
    FakeUi(panel, createFakeWindow = true)
    panel.components[0].requestFocusInWindow()
    for (row in 0..5) {
      var column = 1
      if (row == 2) {
        // Make sure readonly rows are skipped
        continue
      } else if (row == 5) {
        // A row with an editable name should edit column 0
        column = 0
      }
      val name = "value in row $row"
      focusManager.focusOwner?.transferFocus()
      assertThat(table1!!.editingRow).named(name).isEqualTo(row)
      assertThat(table1!!.editingColumn).named(name).isEqualTo(column)
      assertThat(focusManager.focusOwner?.name).named(name).isEqualTo(TEXT_CELL_EDITOR)
      focusManager.focusOwner?.transferFocus()
      assertThat(table1!!.editingRow).named(name).isEqualTo(row)
      assertThat(table1!!.editingColumn).named(name).isEqualTo(column)
      assertThat(focusManager.focusOwner?.name).named(name).isEqualTo(ICON_CELL_EDITOR)
    }
    focusManager.focusOwner?.transferFocus()
    assertThat(table1!!.isEditing).isFalse()
    assertThat(focusManager.focusOwner?.name).isEqualTo(LAST_FIELD_EDITOR)
  }

  @Test
  fun testNavigateBackwardsThroughTable() {
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    val panel = createPanel()
    FakeUi(panel, createFakeWindow = true)
    panel.components[2].requestFocusInWindow()
    for (row in 5 downTo 0) {
      var column = 1
      if (row == 2) {
        // Make sure readonly rows are skipped
        continue
      } else if (row == 5) {
        // A row with an editable name should edit column 0
        column = 0
      }
      val name = "value in row $row"
      focusManager.focusOwner?.transferFocusBackward()
      assertThat(table1!!.editingRow).named(name).isEqualTo(row)
      assertThat(table1!!.editingColumn).named(name).isEqualTo(column)
      assertThat(focusManager.focusOwner?.name).named(name).isEqualTo(ICON_CELL_EDITOR)
      focusManager.focusOwner?.transferFocusBackward()
      assertThat(table1!!.editingRow).named(name).isEqualTo(row)
      assertThat(table1!!.editingColumn).named(name).isEqualTo(column)
      assertThat(focusManager.focusOwner?.name).named(name).isEqualTo(TEXT_CELL_EDITOR)
    }
    focusManager.focusOwner?.transferFocusBackward()
    assertThat(table1!!.isEditing).isFalse()
    assertThat(focusManager.focusOwner?.name).isEqualTo(FIRST_FIELD_EDITOR)
  }

  @Test
  fun testNavigateBackwardsIntoTable() {
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    val panel = createPanel()
    FakeUi(panel, createFakeWindow = true)
    panel.components[2].transferFocusBackward()
    assertThat(table1!!.editingRow).isEqualTo(5)
    assertThat(table1!!.editingColumn).isEqualTo(0)
    assertThat(focusManager.focusOwner?.name).isEqualTo(ICON_CELL_EDITOR)
  }

  @Test
  fun testNavigateBackwardsIntoReadOnlyTable() {
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    model1!!.readOnly = true
    val panel = createPanel()
    FakeUi(panel, createFakeWindow = true)
    panel.components[2].transferFocusBackward()
    assertThat(table1!!.isEditing).isFalse()
    assertThat(table1!!.selectedRow).isEqualTo(5)
    assertThat(focusManager.focusOwner?.name).isEqualTo(TABLE_NAME)
    focusManager.focusOwner?.transferFocus()
    assertThat(focusManager.focusOwner?.name).isEqualTo(LAST_FIELD_EDITOR)
  }

  @Test
  fun testNavigateForwardFromSelectedRow() {
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    val panel = createPanel()
    FakeUi(panel, createFakeWindow = true)
    focusManager.focusOwner = table1!!
    table1!!.changeSelection(3, 0, false, false)
    table1!!.transferFocus()
    assertThat(table1!!.editingRow).isEqualTo(3)
    assertThat(table1!!.editingColumn).isEqualTo(1)
    assertThat(focusManager.focusOwner?.name).isEqualTo(TEXT_CELL_EDITOR)
  }

  @Test
  fun testDepth() {
    table1!!.model.expand(4)
    table1!!.model.expand(7)
    assertThat(table1!!.rowCount).isEqualTo(10)
    assertThat(table1!!.depth(table1!!.item(0))).isEqualTo(0)
    assertThat(table1!!.depth(table1!!.item(5))).isEqualTo(1)
    assertThat(table1!!.depth(table1!!.item(8))).isEqualTo(2)
  }

  @Test
  fun testToggle() {
    table1!!.toggle(table1!!.item(4) as PTableGroupItem)
    assertThat(table1!!.rowCount).isEqualTo(9)
    table1!!.toggle(table1!!.item(7) as PTableGroupItem)
    assertThat(table1!!.rowCount).isEqualTo(10)
    table1!!.toggle(table1!!.item(4) as PTableGroupItem)
    assertThat(table1!!.rowCount).isEqualTo(6)
  }

  @Test
  fun testClickOnExpanderIcon() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }
    val fakeUI = FakeUi(table1!!)
    table1!!.setBounds(0, 0, 400, 4000)
    table1!!.doLayout()
    fakeUI.mouse.click(10, table1!!.rowHeight * 4 + 10)
    assertThat(table1!!.rowCount).isEqualTo(9)
    // Called from attempt to make cell editable & from expander icon check
    assertThat(model1!!.countOfIsCellEditable).isEqualTo(2)
  }

  @Test
  fun testClickOnValueColumnIgnored() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }
    val fakeUI = FakeUi(table1!!)
    table1!!.setBounds(0, 0, 400, 4000)
    table1!!.doLayout()
    fakeUI.mouse.click(210, table1!!.rowHeight * 4 + 10)
    // Called from attempt to make cell editable but NOT from expander icon check
    assertThat(model1!!.countOfIsCellEditable).isEqualTo(1)
  }

  @Test
  fun testCopy() {
    table1!!.setRowSelectionInterval(3, 3)
    val transferHandler = table1!!.transferHandler
    val clipboard: Clipboard = mock()
    transferHandler.exportToClipboard(table1!!, clipboard, TransferHandler.COPY)
    val transferableCaptor = ArgumentCaptor.forClass(Transferable::class.java)
    verify(clipboard).setContents(transferableCaptor.capture(), eq(null))
    val transferable = transferableCaptor.value
    assertThat(transferable.isDataFlavorSupported(DataFlavor.stringFlavor)).isTrue()
    assertThat(transferable.getTransferData(DataFlavor.stringFlavor)).isEqualTo("visible\ttrue")
  }

  @Test
  fun testCopyFromTextFieldEditor() {
    table1!!.setRowSelectionInterval(3, 3)
    table1!!.editCellAt(3, 1)
    val textField =
      (table1!!.editorComponent as SimpleEditorComponent).getComponent(0) as JTextField
    textField.text = "Text being edited"
    textField.select(5, 10)
    val transferHandler = table1!!.transferHandler
    val clipboard: Clipboard = mock()
    transferHandler.exportToClipboard(table1!!, clipboard, TransferHandler.COPY)
    val transferableCaptor = ArgumentCaptor.forClass(Transferable::class.java)
    verify(clipboard).setContents(transferableCaptor.capture(), eq(null))
    val transferable = transferableCaptor.value
    assertThat(transferable.isDataFlavorSupported(DataFlavor.stringFlavor)).isTrue()
    assertThat(transferable.getTransferData(DataFlavor.stringFlavor)).isEqualTo("being")
  }

  @Test
  fun testCut() {
    table1!!.setRowSelectionInterval(3, 3)
    val transferHandler = table1!!.transferHandler
    val clipboard: Clipboard = mock()
    transferHandler.exportToClipboard(table1!!, clipboard, TransferHandler.MOVE)

    // Deletes are not supported in the model, do not expect anything copied to the clipboard, and
    // the row should still exist:
    verifyNoInteractions(clipboard)
    assertThat(model1!!.items.size).isEqualTo(6)

    model1!!.supportDeletes = true
    transferHandler.exportToClipboard(table1!!, clipboard, TransferHandler.MOVE)

    val transferableCaptor = ArgumentCaptor.forClass(Transferable::class.java)
    verify(clipboard).setContents(transferableCaptor.capture(), eq(null))
    val transferable = transferableCaptor.value
    assertThat(transferable.isDataFlavorSupported(DataFlavor.stringFlavor)).isTrue()
    assertThat(transferable.getTransferData(DataFlavor.stringFlavor)).isEqualTo("visible\ttrue")
    assertThat(model1!!.items.size).isEqualTo(5)
  }

  @Test
  fun testPaste() {
    table1!!.setRowSelectionInterval(3, 3)
    val transferHandler = table1!!.transferHandler
    transferHandler.importData(table1!!, StringSelection("data\t123"))

    // Inserts are not supported in the model, do not expect the model to be changed:
    assertThat(model1!!.items.size).isEqualTo(6)

    model1!!.supportInserts = true
    transferHandler.importData(table1!!, StringSelection("data\t123"))
    assertThat(model1!!.items.size).isEqualTo(7)
    assertThat(model1!!.items[6].name).isEqualTo("data")
    assertThat(model1!!.items[6].value).isEqualTo("123")
  }

  @Test
  fun testBackgroundColor() {
    val hoverColor = JBUI.CurrentTheme.Table.Hover.background(true)
    val selectedColor = UIUtil.getTableBackground(true, true)

    // Without focus:
    assertThat(cellBackground(table1!!, selected = false, hovered = false))
      .isEqualTo(table1!!.background)
    assertThat(cellBackground(table1!!, selected = false, hovered = true)).isEqualTo(hoverColor)
    assertThat(cellBackground(table1!!, selected = true, hovered = false))
      .isEqualTo(table1!!.background)
    assertThat(cellBackground(table1!!, selected = true, hovered = true)).isEqualTo(hoverColor)

    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    focusManager.focusOwner = table1

    // With focus:
    assertThat(cellBackground(table1!!, selected = false, hovered = false))
      .isEqualTo(table1!!.background)
    assertThat(cellBackground(table1!!, selected = false, hovered = true)).isEqualTo(hoverColor)
    assertThat(cellBackground(table1!!, selected = true, hovered = false)).isEqualTo(selectedColor)
    assertThat(cellBackground(table1!!, selected = true, hovered = true)).isEqualTo(selectedColor)
  }

  @Suppress("UnstableApiUsage")
  private fun cellBackground(table: PTableImpl, selected: Boolean, hovered: Boolean): Color {
    val selectedRow = if (selected) 3 else 2
    table.setRowSelectionInterval(selectedRow, selectedRow)

    val hoverListener = TableHoverListener.DEFAULT
    val cell = table.getCellRect(3, 1, false)
    if (hovered) {
      hoverListener.mouseEntered(table, cell.centerX.toInt(), cell.centerY.toInt())
    } else {
      hoverListener.mouseExited(table)
    }
    val renderer = PTableCellRendererWrapper()
    val component = table.prepareRenderer(renderer, 3, 1)
    return component.background
  }

  @Test
  fun testCustomCursor() {
    val component = JPanel(BorderLayout())
    val left =
      JLabel(StudioIcons.Common.ANDROID_HEAD).also {
        it.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      }
    val middle =
      JLabel(StudioIcons.Common.CHECKED).also {
        it.cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
      }
    val right =
      JLabel(StudioIcons.Common.CLEAR).also {
        it.cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
      }
    component.add(left, BorderLayout.WEST)
    component.add(middle, BorderLayout.CENTER)
    component.add(right, BorderLayout.EAST)

    val specialRenderer =
      object : PTableCellRenderer {
        override fun getEditorComponent(
          table: PTable,
          item: PTableItem,
          column: PTableColumn,
          depth: Int,
          isSelected: Boolean,
          hasFocus: Boolean,
          isExpanded: Boolean,
        ): JComponent = component
      }
    val rendererProvider =
      object : PTableCellRendererProvider {
        override fun invoke(
          table: PTable,
          property: PTableItem,
          column: PTableColumn,
        ): PTableCellRenderer {
          return specialRenderer
        }
      }

    model1!!.hasCustomCursors = true
    table1 = PTableImpl(model1!!, null, rendererProvider, editorProvider!!)

    table1!!.size = Dimension(600, 800)
    val ui = FakeUi(table1!!)
    val cell = table1!!.getCellRect(3, 1, false)
    component.size = cell.size
    TreeWalker(component).descendantStream().forEach(Component::doLayout)
    ui.mouse.moveTo(cell.x + 8, cell.centerY.toInt())
    assertThat(table1?.cursor).isSameAs(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
    ui.mouse.moveTo(cell.centerX.toInt(), cell.centerY.toInt())
    assertThat(table1?.cursor).isSameAs(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
    ui.mouse.moveTo(cell.x + cell.width - 8, cell.centerY.toInt())
    assertThat(table1?.cursor).isSameAs(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR))
  }

  @Test
  fun testColumnResize() {
    table1!!.size = Dimension(600, 800)
    table1!!.setUI(HeadlessTableUI())
    val ui = FakeUi(table1!!)
    ui.mouse.moveTo(10, 5)
    assertThat(table1!!.cursor).isSameAs(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
    ui.mouse.moveTo(300, 5)
    assertThat(table1!!.cursor).isSameAs(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR))
    ui.mouse.press(300, 5)
    assertThat(nameColumnFraction!!.value).isEqualTo(0.5f)
    ui.mouse.dragTo(200, 5)
    assertThat(nameColumnFraction!!.value).isWithin(0.001f).of(0.333f)
    ui.mouse.release()
    assertThat(table1!!.cursor).isSameAs(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR))
    ui.mouse.moveTo(300, 5)
    assertThat(table1!!.cursor).isSameAs(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
  }

  @Test
  fun testExpandableItemHandler() {
    // The expandable item handler cannot be tested with FakeUi because
    // AbstractExpandableItemHandler is using
    // ScreenUtil.getScreenRectangle() which returns an empty rectangle on a headless system.

    // Instead check some direct calls to the expandable item handler.

    // First replace the renderer such that each cell is rendered with a panel with 2 child panels
    // of each size.
    // The left child panel will accept expansion request, the right child panel will not.
    // Make the preferred size of the child panels wider proportional with the row number.
    rendererProvider!!.renderer =
      object : PTableCellRenderer {
        override fun getEditorComponent(
          table: PTable,
          item: PTableItem,
          column: PTableColumn,
          depth: Int,
          isSelected: Boolean,
          hasFocus: Boolean,
          isExpanded: Boolean,
        ): JComponent {
          val row = table.tableModel.items.indexOf(item)
          val left =
            JPanel().apply {
              preferredSize = Dimension(5 * (row + 4), 16)
              name = "row: $row, left"
              ClientProperty.put(this, KEY_IS_VISUALLY_RESTRICTED) {
                width < preferredSize.width && !isExpanded
              }
            }
          val right =
            JPanel().apply {
              preferredSize = Dimension(5 * (row + 4), 16)
              name = "row: $row, right"
            }
          return JPanel(BorderLayout()).apply {
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
            bounds = (table.component as JTable).getCellRect(row, column.ordinal, true)
            left.bounds = Rectangle(0, 0, bounds.width / 2, bounds.height)
            right.bounds = Rectangle(bounds.width / 2, 0, bounds.width / 2, bounds.height)
          }
        }
      }
    table1!!.setSize(100, 1000)
    table1!!.doLayout()

    // The first rows should fit.
    checkAllRowsFit(0..1)
    // The remaining rows should all attempt to expand when hovering over the left sub child of the
    // renderer.
    checkNoRowsFit(2..3)
  }

  @Test
  fun testNavigateAbsoluteEnd() {
    scrollPane!!.size = Dimension(300, 60)
    table1!!.rowHeight = 20
    table2!!.rowHeight = 20
    table3!!.rowHeight = 20
    val ui = FakeUi(scrollPane!!, createFakeWindow = true)
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    focusManager.focusOwner = table1
    table1!!.changeSelection(1, 0, false, false)
    ui.keyboard.press(KeyEvent.VK_CONTROL)
    ui.keyboard.pressAndRelease(KeyEvent.VK_END)
    ui.keyboard.release(KeyEvent.VK_CONTROL)
    assertSelection(table3!!, 4)
    ui.keyboard.press(KeyEvent.VK_CONTROL)
    ui.keyboard.pressAndRelease(KeyEvent.VK_HOME)
    ui.keyboard.release(KeyEvent.VK_CONTROL)
    assertSelection(table1!!, 0)
  }

  @Test
  fun testNavigateUpDownAcrossTables() {
    scrollPane!!.size = Dimension(300, 400)
    val ui = FakeUi(scrollPane!!, createFakeWindow = true)
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    focusManager.focusOwner = table1
    table1!!.changeSelection(4, 0, false, false)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertSelection(table1!!, 5)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertSelection(table2!!, 0)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertSelection(table2!!, 1)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertSelection(table2!!, 2)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertSelection(table2!!, 3)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertSelection(table3!!, 0)
    ui.keyboard.pressAndRelease(KeyEvent.VK_UP)
    assertSelection(table2!!, 3)
  }

  @Test
  fun testNavigateUpDownAcrossTablesWithClosedSection() {
    scrollPane!!.size = Dimension(300, 400)
    val ui = FakeUi(scrollPane!!, createFakeWindow = true)
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    focusManager.focusOwner = table1

    // Close section 2:
    table2!!.isVisible = false

    table1!!.changeSelection(4, 0, false, false)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertSelection(table1!!, 5)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertSelection(table3!!, 0)
    ui.keyboard.pressAndRelease(KeyEvent.VK_UP)
    assertSelection(table1!!, 5)
  }

  @Test
  fun testNavigatePageUpDownAcrossTables() {
    scrollPane!!.size = Dimension(300, 60)
    val ui = FakeUi(scrollPane!!, createFakeWindow = true)
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    focusManager.focusOwner = table1
    table1!!.changeSelection(1, 0, false, false)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_DOWN)
    assertSelection(table1!!, 4)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_DOWN)
    assertSelection(table2!!, 1)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_DOWN)
    assertSelection(table3!!, 0)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_DOWN)
    assertSelection(table3!!, 3)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_DOWN)
    assertSelection(table3!!, 4)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_UP)
    assertSelection(table3!!, 1)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_UP)
    assertSelection(table2!!, 2)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_UP)
    assertSelection(table1!!, 5)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_UP)
    assertSelection(table1!!, 2)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_UP)
    assertSelection(table1!!, 0)
  }

  @Test
  fun testNavigatePageUpDownAcrossTablesWithClosedSection() {
    scrollPane!!.size = Dimension(300, 60)
    val ui = FakeUi(scrollPane!!, createFakeWindow = true)
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    focusManager.focusOwner = table1

    // Close section 2:
    table2!!.isVisible = false

    table1!!.changeSelection(1, 0, false, false)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_DOWN)
    assertSelection(table1!!, 4)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_DOWN)
    assertSelection(table3!!, 0)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_DOWN)
    assertSelection(table3!!, 3)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_DOWN)
    assertSelection(table3!!, 4)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_UP)
    assertSelection(table3!!, 1)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_UP)
    assertSelection(table1!!, 5)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_UP)
    assertSelection(table1!!, 2)
    ui.keyboard.pressAndRelease(KeyEvent.VK_PAGE_UP)
    assertSelection(table1!!, 0)
  }

  private fun assertSelection(table: PTableImpl, row: Int) {
    assertThat(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner).isSameAs(table)
    assertThat(table.selectedRow).isEqualTo(row)
  }

  private fun middleOf(row: Int, column: Int, left: Boolean): Point {
    val rect = table1!!.getCellRect(row, column, true)
    return if (left) Point(rect.x + rect.width / 4, rect.centerY.toInt())
    else Point(rect.x + 3 * rect.width / 4, rect.centerY.toInt())
  }

  private fun checkAllRowsFit(rows: IntRange) {
    val handler = table1!!.expandableItemsHandler as PTableExpandableItemsHandler
    for (row in rows) {
      for (column in 0..1) {
        for (left in listOf(true, false)) {
          assertThat(handler.getCellKeyForPoint(middleOf(row, column, left))).isNull()
        }
      }
    }
  }

  private fun checkNoRowsFit(rows: IntRange) {
    val handler = table1!!.expandableItemsHandler as PTableExpandableItemsHandler
    for (row in rows) {
      for (column in 0..1) {
        // The left side of the component is expandable:
        val cell = handler.getCellKeyForPoint(middleOf(row, column, left = true))
        assertThat(cell).isEqualTo(TableCell(row, column))
        assertThat(handler.expandedItems).containsExactly(cell)

        val pair = handler.computeCellRendererAndBounds(cell!!)
        val renderer = pair!!.first
        val bounds = pair.second
        assertThat(bounds.width)
          .isEqualTo(10 * (row + 4) + JBUIScale.scale(EXPANSION_RIGHT_PADDING))
        assertThat(renderer.firstChild().firstChild().name).isEqualTo("row: $row, left")

        // Check that the same cell is returned even when the cell is already expanded.
        val cell2 = handler.getCellKeyForPoint(middleOf(row, column, left = true))
        assertThat(cell2).isEqualTo(TableCell(row, column))

        // The right side is not:
        assertThat(handler.getCellKeyForPoint(middleOf(row, column, left = false))).isNull()
      }
    }
  }

  private fun Component.firstChild(): Component = (this as JComponent).getComponent(0)

  private fun createPanel(): JPanel {
    val panel = JPanel()
    panel.isFocusCycleRoot = true
    panel.focusTraversalPolicy = LayoutFocusTraversalPolicy()
    panel.add(JTextField())
    panel.add(table1)
    panel.add(JTextField())
    panel.components[0].name = FIRST_FIELD_EDITOR
    panel.components[1].name = TABLE_NAME
    panel.components[2].name = LAST_FIELD_EDITOR
    return panel
  }

  private fun dispatchAction(key: KeyStroke) {
    val action = table1!!.getActionForKeyStroke(key)
    action.actionPerformed(ActionEvent(table1, 0, action.toString()))
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

    override val editorComponent = SimpleEditorComponent()
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

  private class SimpleEditorComponent : JPanel() {
    override fun getPreferredSize(): Dimension {
      val insets = DarculaTextBorder().getBorderInsets(this)
      return Dimension(400 + insets.width, 400 + insets.height)
    }
  }

  private inner class SimplePTableCellEditorProvider : PTableCellEditorProvider {
    val editor = SimplePTableCellEditor()

    override fun invoke(
      table: PTable,
      property: PTableItem,
      column: PTableColumn,
    ): PTableCellEditor {
      editor.property = property
      editor.column = column
      return editor
    }
  }

  class SimplePTableCellRendererProvider : PTableCellRendererProvider {
    var renderer: PTableCellRenderer = DefaultPTableCellRenderer()

    override fun invoke(
      table: PTable,
      property: PTableItem,
      column: PTableColumn,
    ): PTableCellRenderer {
      return renderer
    }
  }
}
