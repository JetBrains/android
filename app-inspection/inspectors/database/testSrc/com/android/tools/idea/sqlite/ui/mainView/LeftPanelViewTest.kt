/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui.mainView

import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.sqlite.controllers.TabId
import com.android.tools.idea.sqlite.model.ExportDialogParams
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class LeftPanelViewTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val project
    get() = projectRule.project

  private val disposable
    get() = disposableRule.disposable

  @get:Rule
  val rule = RuleChain(projectRule, disposableRule, WaitForIndexRule(projectRule), EdtRule())

  private val inspectorListener = FakeInspectorListener()
  private val inspectorView by lazy {
    DatabaseInspectorViewImpl(project, disposable).apply { listeners.add(inspectorListener) }
  }

  private val schema =
    SqliteSchema(
      listOf(
        SqliteTable("t1", emptyList(), null, false),
        SqliteTable("t2", emptyList(), null, false),
      )
    )
  private val database1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)
  private val database2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)
  private val database3 = SqliteDatabaseId.fromLiveDatabase("db3", 3)

  @Test
  fun runSql_emptyTree() {
    val view = LeftPanelView(inspectorView)
    val runSqlButton = view.component.descendantByName<JButton>("run-sql-button")

    runSqlButton.actionListeners.forEach { it.actionPerformed(ActionEvent(runSqlButton, 1, "")) }

    assertThat(inspectorListener.openSqliteEvaluatorTabActionsInvoked).containsExactly(null)
  }

  @Test
  fun runSql_tableSelected() {
    val view = LeftPanelView(inspectorView)
    val runSqlButton = view.component.descendantByName<JButton>("run-sql-button")
    view.addDatabaseSchema(ViewDatabase(database1, true), schema, 0)
    view.addDatabaseSchema(ViewDatabase(database2, true), schema, 1)
    view.addDatabaseSchema(ViewDatabase(database3, true), schema, 1)
    val tree = view.component.descendantByName<JTree>("left-panel-tree")
    tree.selectPath(database2, "t2")

    runSqlButton.actionListeners.forEach { it.actionPerformed(ActionEvent(runSqlButton, 1, "")) }

    assertThat(inspectorListener.openSqliteEvaluatorTabActionsInvoked).containsExactly(database2)
  }

  @Test
  fun runSql_databaseSelected() {
    val view = LeftPanelView(inspectorView)
    val runSqlButton = view.component.descendantByName<JButton>("run-sql-button")
    view.addDatabaseSchema(ViewDatabase(database1, true), schema, 0)
    view.addDatabaseSchema(ViewDatabase(database2, true), schema, 1)
    view.addDatabaseSchema(ViewDatabase(database3, true), schema, 1)
    val tree = view.component.descendantByName<JTree>("left-panel-tree")
    tree.selectPath(database3, null)

    runSqlButton.actionListeners.forEach { it.actionPerformed(ActionEvent(runSqlButton, 1, "")) }

    assertThat(inspectorListener.openSqliteEvaluatorTabActionsInvoked).containsExactly(database3)
  }

  @Test
  fun runSql_rootSelected() {
    val view = LeftPanelView(inspectorView)
    val runSqlButton = view.component.descendantByName<JButton>("run-sql-button")
    view.addDatabaseSchema(ViewDatabase(database1, true), schema, 0)
    view.addDatabaseSchema(ViewDatabase(database2, true), schema, 1)
    view.addDatabaseSchema(ViewDatabase(database3, true), schema, 1)
    val tree = view.component.descendantByName<JTree>("left-panel-tree")
    tree.selectPath(null, null)

    runSqlButton.actionListeners.forEach { it.actionPerformed(ActionEvent(runSqlButton, 1, "")) }

    assertThat(inspectorListener.openSqliteEvaluatorTabActionsInvoked).containsExactly(null)
  }

  private class FakeInspectorListener : DatabaseInspectorView.Listener {
    val openSqliteEvaluatorTabActionsInvoked = mutableListOf<SqliteDatabaseId?>()

    override fun openSqliteEvaluatorTabActionInvoked(databaseId: SqliteDatabaseId?) {
      openSqliteEvaluatorTabActionsInvoked.add(databaseId)
    }

    override fun tableNodeActionInvoked(databaseId: SqliteDatabaseId, table: SqliteTable) {
      TODO("Not yet implemented")
    }

    override fun closeTabActionInvoked(tabId: TabId) {
      TODO("Not yet implemented")
    }

    override fun refreshAllOpenDatabasesSchemaActionInvoked() {
      TODO("Not yet implemented")
    }

    override fun toggleKeepConnectionOpenActionInvoked() {
      TODO("Not yet implemented")
    }

    override fun cancelOfflineModeInvoked() {
      TODO("Not yet implemented")
    }

    override fun showExportToFileDialogInvoked(exportDialogParams: ExportDialogParams) {
      TODO("Not yet implemented")
    }
  }
}

private inline fun <reified T : Component> Component.descendantByName(name: String): T =
  getDescendant(T::class.java) { it.name == name }

private fun JTree.selectPath(db: SqliteDatabaseId?, table: String?) {
  val model = model as DefaultTreeModel
  val root = model.root
  val path = buildList {
    add(root)
    if (db != null) {
      val db = model.getChildren(root).first { (it.userObject as ViewDatabase).databaseId == db }
      add(db)
      if (table != null) {
        val table = model.getChildren(db).first { (it.userObject as SqliteTable).name == table }
        add(table)
      }
    }
  }
  selectionModel.selectionPath = TreePath(path.toTypedArray())
}

private fun DefaultTreeModel.getChildren(parent: Any) =
  List(getChildCount(parent)) { getChild(parent, it) as DefaultMutableTreeNode }
