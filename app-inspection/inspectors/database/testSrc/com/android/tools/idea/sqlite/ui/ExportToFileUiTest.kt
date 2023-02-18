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
package com.android.tools.idea.sqlite.ui

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.sqlite.mocks.OpenActionManager
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.ExportDialogParams
import com.android.tools.idea.sqlite.model.ExportDialogParams.ExportDatabaseDialogParams
import com.android.tools.idea.sqlite.model.ExportDialogParams.ExportTableDialogParams
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.mainView.DatabaseDiffOperation
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorView
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorViewImpl
import com.android.tools.idea.sqlite.ui.mainView.ViewDatabase
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent.Origin
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.ui.treeStructure.Tree
import java.awt.event.ActionEvent
import java.awt.event.InputEvent.BUTTON3_DOWN_MASK
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.event.MouseEvent.BUTTON3
import java.awt.event.MouseEvent.MOUSE_PRESSED
import javax.swing.AbstractButton
import javax.swing.JPopupMenu
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

private const val EXPORT_TO_FILE_ENABLED_DEFAULT = true
private const val TABLE_ACTION_PANEL_COMPONENT_NAME = "table-actions-panel"
private const val EXPORT_BUTTON_COMPONENT_NAME = "export-button"

/** Test suite verifying Export-to-File feature's UI layer */
class ExportToFileUiTest : LightPlatformTestCase() {
  private val databaseId =
    SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("name")))
  private val column1 =
    SqliteColumn("c1", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
  private val column2 =
    SqliteColumn("c2", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
  private val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
  private val schema = SqliteSchema(listOf(table1))

  fun test_tableView_exportButtonVisibleByDefault() {
    assertThat(findExportButtonInActionPanel(TableViewImpl())).isNotNull()
  }

  fun test_tableView_exportButton() {
    // given
    val listener = mock(TableView.Listener::class.java)
    val tableView = TableViewImpl().also { it.addListener(listener) }
    val exportButton = findExportButtonInActionPanel(tableView)!!

    // when
    exportButton.simulateClick()

    // then
    verify(listener).showExportToFileDialogInvoked()
  }

  /* Tests selecting a node in the schema-tree and clicking on the export-button in the action-panel */
  fun test_leftPanelView_actionsPanel_exportTable() {
    test_leftPanelView(
      treeNodePredicate = { it == table1 },
      uiInteractions = { nodePath, tree, exportButton, popUpMenuProvider ->
        tree.simulateClick(nodePath, BUTTON1) // select schema object in the schema tree
        assertThat(popUpMenuProvider()).isEmpty()
        exportButton.simulateClick()
      },
      expectedParams =
        ExportTableDialogParams(databaseId, table1.name, Origin.SCHEMA_TREE_EXPORT_BUTTON)
    )
  }

  /* Tests selecting a node in the schema-tree and clicking on the export-button in the action-panel */
  fun test_leftPanelView_actionsPanel_exportDatabase() {
    test_leftPanelView(
      treeNodePredicate = { (it as? ViewDatabase)?.databaseId == databaseId },
      uiInteractions = { nodePath, tree, exportButton, popUpMenuProvider ->
        tree.simulateClick(nodePath, BUTTON1) // select schema object in the schema tree
        assertThat(popUpMenuProvider()).isEmpty()
        exportButton.simulateClick()
      },
      expectedParams = ExportDatabaseDialogParams(databaseId, Origin.SCHEMA_TREE_EXPORT_BUTTON)
    )
  }

  /* Tests right-clicking on a node in the schema-tree and invoking an export-action through a pop-up menu that appears */
  fun test_leftPanelView_schemaTreePopUp_exportTable() {
    test_leftPanelView(
      treeNodePredicate = { it == table1 },
      uiInteractions = { nodePath, tree, _, popUpMenuProvider ->
        assertThat(popUpMenuProvider()).isEmpty()
        tree.simulateClick(nodePath, BUTTON3) // make pop-up show up
        popUpMenuProvider().single().invokeSingleOption() // execute the only pop-up item
      },
      expectedParams =
        ExportTableDialogParams(databaseId, table1.name, Origin.SCHEMA_TREE_CONTEXT_MENU)
    )
  }

  /* Tests right-clicking on a node in the schema-tree and invoking an export-action through a pop-up menu that appears */
  fun test_leftPanelView_schemaTreePopUp_exportDatabase() {
    test_leftPanelView(
      treeNodePredicate = { (it as? ViewDatabase)?.databaseId == databaseId },
      uiInteractions = { nodePath, tree, _, popUpMenuProvider ->
        assertThat(popUpMenuProvider()).isEmpty()
        tree.simulateClick(nodePath, BUTTON3) // make pop-up show up
        popUpMenuProvider().single().invokeSingleOption() // execute the only pop-up item
      },
      expectedParams = ExportDatabaseDialogParams(databaseId, Origin.SCHEMA_TREE_CONTEXT_MENU)
    )
  }

  private fun test_leftPanelView(
    treeNodePredicate: (userObject: Any) -> Boolean,
    uiInteractions:
      (
        nodePath: TreePath,
        tree: Tree,
        exportButton: CommonButton,
        popUpMenuProvider: () -> List<DefaultActionGroup>) -> Unit,
    expectedParams: ExportDialogParams
  ) {
    // Set up an ActionManager capturing pop-up menus
    val currentActionManager =
      ApplicationManager.getApplication().getService(ActionManager::class.java) as ActionManagerEx
    val testActionManager =
      object : OpenActionManager(currentActionManager) {
        val popUpMenuActionGroupList = mutableListOf<ActionGroup>()
        override fun createActionPopupMenu(place: String, group: ActionGroup): ActionPopupMenu {
          popUpMenuActionGroupList.add(group)
          val mockPopUpMenu = mock(ActionPopupMenu::class.java)
          whenever(mockPopUpMenu.component).thenReturn(mock(JPopupMenu::class.java))
          return mockPopUpMenu
        }
      }
    ApplicationManager.getApplication()
      .replaceService(ActionManager::class.java, testActionManager, testRootDisposable)

    // Create the UI
    val view = DatabaseInspectorViewImpl(project, testRootDisposable)
    val listener = mock(DatabaseInspectorView.Listener::class.java)
    view.listeners.add(listener)

    val tree = TreeWalker(view.component).descendants().filterIsInstance<Tree>().first()
    view.updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId, true), schema, 0))
    )

    // Interact with the UI
    val exportButton =
      TreeWalker(view.component).descendants().filterIsInstance(CommonButton::class.java).single {
        it.name == EXPORT_BUTTON_COMPONENT_NAME
      }
    val nodePath =
      (0 until tree.rowCount).map { tree.getPathForRow(it) }.single {
        treeNodePredicate((it.lastPathComponent as DefaultMutableTreeNode).userObject)
      }
    val popupMenuProvider: () -> List<DefaultActionGroup> = {
      testActionManager.popUpMenuActionGroupList.map { it as DefaultActionGroup }
    }
    uiInteractions(nodePath, tree, exportButton, popupMenuProvider)

    // Verify correct export parameters were passed to the listener
    verify(listener).showExportToFileDialogInvoked(expectedParams)
    verifyNoMoreInteractions(listener)
  }

  private fun findExportButtonInActionPanel(tableView: TableViewImpl): CommonButton? {
    val actionPanel =
      TreeWalker(tableView.component).descendants().first {
        it.name == TABLE_ACTION_PANEL_COMPONENT_NAME
      }
    return TreeWalker(actionPanel).descendants().filterIsInstance<CommonButton>().firstOrNull {
      it.name == EXPORT_BUTTON_COMPONENT_NAME
    }
  }

  private fun AbstractButton.simulateClick() {
    this.actionListeners.forEach { it.actionPerformed(mock(ActionEvent::class.java)) }
  }

  private fun DefaultActionGroup.invokeSingleOption() {
    this.childActionsOrStubs.single().actionPerformed(mock(AnActionEvent::class.java))
  }

  private fun Tree.simulateClick(nodePath: TreePath, button: Int) {
    when (button) {
      BUTTON1 -> {
        // in headless mode handling a left-click in a Tree is an issue, so we select the node
        // instead
        clearSelection()
        addSelectionPaths(arrayOf(nodePath))
      }
      BUTTON3 -> {
        val nodeBounds = getPathBounds(nodePath)!!
        val eventTime = System.currentTimeMillis()
        mouseListeners.forEach { listener ->
          listener.mousePressed(
            MouseEvent(
              this,
              MOUSE_PRESSED,
              eventTime,
              BUTTON3_DOWN_MASK,
              nodeBounds.x,
              nodeBounds.y,
              1,
              true,
              button
            )
          )
        }
      }
      else -> throw IllegalArgumentException()
    }
  }
}
