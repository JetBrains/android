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

import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.controllers.SqliteEvaluatorController
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.mocks.OpenDatabaseInspectorModel
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.DatabaseInspectorModel
import com.android.tools.idea.sqlite.model.DatabaseInspectorModelImpl
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.repository.DatabaseRepositoryImpl
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorView
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorViewImpl
import com.android.tools.idea.sqlite.ui.tableView.TableView.TableViewType.TABLE
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.android.tools.idea.sqlite.utils.SqliteTestUtil
import com.android.tools.idea.sqlite.utils.getJdbcDatabaseConnection
import com.android.tools.idea.testing.runDispatching
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.ui.EditorTextField
import com.intellij.util.concurrency.EdtExecutorService
import java.awt.Dimension
import javax.swing.JComboBox
import javax.swing.JTable
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class SqliteEvaluatorViewImplTest : LightPlatformTestCase() {
  private lateinit var view: SqliteEvaluatorViewImpl
  private lateinit var mockSchemaProvider: SchemaProvider

  private lateinit var sqliteUtil: SqliteTestUtil
  private var realDatabaseConnection: DatabaseConnection? = null

  private var dropPsiCachesCallCounter = 0

  override fun setUp() {
    super.setUp()
    mockSchemaProvider = mock(SchemaProvider::class.java)
    whenever(mockSchemaProvider.getSchema(any())).thenReturn(SqliteSchema(emptyList()))

    dropPsiCachesCallCounter = 0
    view =
      SqliteEvaluatorViewImpl(project, TableViewImpl(TABLE), mockSchemaProvider) {
        dropPsiCachesCallCounter += 1
      }
    view.component.size = Dimension(600, 200)

    sqliteUtil =
      SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()
  }

  override fun tearDown() {
    try {
      if (realDatabaseConnection != null) {
        pumpEventsAndWaitForFuture(realDatabaseConnection!!.close())
      }
      sqliteUtil.tearDown()
    } finally {
      super.tearDown()
    }
  }

  fun testAddAndRemoveDatabases() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val comboBox = treeWalker.descendants().filterIsInstance<JComboBox<*>>().first()

    // Act/Assert
    assertThat(comboBox.selectedIndex).isEqualTo(-1)

    val databaseId1 = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db1")))
    val databaseId2 = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db2")))

    view.setDatabases(listOf(databaseId1, databaseId2), databaseId1)
    assertThat(comboBox.selectedIndex).isEqualTo(0)

    view.setDatabases(emptyList(), null)
    assertThat(comboBox.selectedIndex).isEqualTo(-1)
  }

  fun testActiveDatabaseRemainsActiveWhenNewDbsAreAdded() {
    // Prepare
    val model = OpenDatabaseInspectorModel()
    val evaluatorController =
      sqliteEvaluatorController(
        model,
        DatabaseRepositoryImpl(project, EdtExecutorService.getInstance()),
      )
    evaluatorController.setUp()

    val db0 = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db0")))
    val db1 = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db1")))
    val db2 = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db2")))
    var activeDatabaseId: SqliteDatabaseId? = null
    view.addListener(
      object : SqliteEvaluatorView.Listener {
        override fun onDatabaseSelected(databaseId: SqliteDatabaseId) {
          activeDatabaseId = databaseId
        }
      }
    )

    // Act/Assert
    assertThat(activeDatabaseId).isNull()

    model.addDatabaseSchema(db2, SqliteSchema(emptyList()))
    assertThat(activeDatabaseId).isEqualTo(db2)

    model.addDatabaseSchema(db1, SqliteSchema(emptyList()))
    assertThat(activeDatabaseId).isEqualTo(db2)

    model.addDatabaseSchema(db0, SqliteSchema(emptyList()))
    assertThat(activeDatabaseId).isEqualTo(db2)

    model.removeDatabaseSchema(db2)
    assertThat(activeDatabaseId).isEqualTo(db0)
  }

  fun testPsiCacheIsDroppedWhenNewDatabaseIsSelected() {
    // Prepare
    val comboBox = TreeWalker(view.component).descendants().filterIsInstance<JComboBox<*>>().first()

    val database1 = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db1")))
    val database2 = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db2")))

    // Act/Assert
    view.setDatabases(listOf(database1, database2), database1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(dropPsiCachesCallCounter).isEqualTo(1)

    view.setDatabases(listOf(database1, database2), database2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(dropPsiCachesCallCounter).isEqualTo(3)

    comboBox.selectedIndex = 0
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(dropPsiCachesCallCounter).isEqualTo(4)
  }

  fun testSchemaUpdatedDropsCachesAndGetsNewSchema() {
    // Prepare
    val database = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db1")))

    view.setDatabases(listOf(database), database)

    // Act
    view.schemaChanged(database)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertThat(dropPsiCachesCallCounter).isEqualTo(2)
    verify(mockSchemaProvider, times(2)).getSchema(database)
  }

  fun testRefreshButtonIsDisabledByDefault() {
    // Prepare
    val refreshButton =
      TreeWalker(view.tableView.component).descendants().first { it.name == "refresh-button" }

    val evaluatorController =
      sqliteEvaluatorController(
        OpenDatabaseInspectorModel(),
        DatabaseRepositoryImpl(project, EdtExecutorService.getInstance()),
      )

    // Act
    evaluatorController.setUp()

    // Assert
    assertThat(refreshButton.isEnabled).isFalse()
  }

  fun testMultipleStatementAreRun() {
    // Prepare
    val sqliteFile = createAdHocSqliteDatabase()
    realDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          sqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    val database = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(sqliteFile))

    val model = DatabaseInspectorModelImpl()
    val repository = DatabaseRepositoryImpl(project, EdtExecutorService.getInstance())
    runDispatching { repository.addDatabaseConnection(database, realDatabaseConnection!!) }

    val controller = sqliteEvaluatorController(model, repository)
    controller.setUp()

    model.addDatabaseSchema(database, SqliteSchema(emptyList()))

    // Act
    pumpEventsAndWaitForFuture(
      controller.showAndExecuteSqlStatement(
        database,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"),
      )
    )

    // Assert
    val table = TreeWalker(view.component).descendants().filterIsInstance<JTable>().first()
    assertThat(table.model.columnCount).isEqualTo(2)
    assertThat(table.model.getColumnName(1)).isEqualTo("c1")
    assertThat(table.model.rowCount).isEqualTo(1)
    assertThat(table.model.getValueAt(0, 1)).isEqualTo("42")

    // Act
    pumpEventsAndWaitForFuture(
      controller.showAndExecuteSqlStatement(
        database,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"),
      )
    )

    // Assert
    assertThat(table.model.columnCount).isEqualTo(2)
    assertThat(table.model.getColumnName(1)).isEqualTo("c1")
    assertThat(table.model.rowCount).isEqualTo(1)
    assertThat(table.model.getValueAt(0, 1)).isEqualTo("42")
  }

  fun testEnableRunSqliteStatementsEnablesRunButton() {
    // Prepare
    val runButton = TreeWalker(view.component).descendants().first { it.name == "run-button" }

    // Act
    view.setRunSqliteStatementEnabled(true)

    // Assert
    assertThat(runButton.isEnabled).isTrue()
  }

  fun testDisableRunSqliteStatementsDisablesRunButton() {
    // Prepare
    val runButton = TreeWalker(view.component).descendants().first { it.name == "run-button" }
    view.setRunSqliteStatementEnabled(true)

    // Act
    view.setRunSqliteStatementEnabled(false)

    // Assert
    assertThat(runButton.isEnabled).isFalse()
  }

  fun testSqliteStatementTextChanged() {
    // Prepare
    val collapsedEditor =
      TreeWalker(view.component).descendants().first { it.name == "editor" } as EditorTextField

    val invocations = mutableListOf<String>()
    val mockListener =
      object : SqliteEvaluatorView.Listener {
        override fun sqliteStatementTextChangedInvoked(newSqliteStatement: String) {
          invocations.add(newSqliteStatement)
        }
      }

    view.addListener(mockListener)

    // Act
    collapsedEditor.text = "test1"
    collapsedEditor.text = "test2"

    // Assert
    assertThat(invocations).containsExactly("test1", "test2").inOrder()
  }

  fun testTableIsEmptyWhenDbIsClosed() {
    // Prepare
    val sqliteFile = createAdHocSqliteDatabase()
    realDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          sqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    val database = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(sqliteFile))

    val model = DatabaseInspectorModelImpl()
    val repository = DatabaseRepositoryImpl(project, EdtExecutorService.getInstance())
    runDispatching { repository.addDatabaseConnection(database, realDatabaseConnection!!) }

    val controller = sqliteEvaluatorController(model, repository)
    controller.setUp()

    model.addDatabaseSchema(database, SqliteSchema(emptyList()))
    val unrelated = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db0")))
    model.addDatabaseSchema(unrelated, SqliteSchema(emptyList()))

    pumpEventsAndWaitForFuture(
      controller.showAndExecuteSqlStatement(
        database,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"),
      )
    )
    val table = TreeWalker(view.component).descendants().filterIsInstance<JTable>().first()

    // check before that table isn't empty
    assertThat(table.model.rowCount).isEqualTo(1)

    // Act1
    model.removeDatabaseSchema(unrelated)

    // Assert that nothing changed
    assertThat(table.model.rowCount).isEqualTo(1)

    // Act2
    model.removeDatabaseSchema(database)

    // Assert that now it is empty
    assertThat(table.model.rowCount).isEqualTo(0)
  }

  fun testShowTableView() {
    val controller = sqliteEvaluatorController()
    controller.setUp()

    val table1 = TreeWalker(view.component).descendants().filterIsInstance<JTable>().firstOrNull()
    val messagePanel1 =
      TreeWalker(view.component).descendants().first { it.name == "message-panel" }

    assertThat(messagePanel1).isNotNull()
    assertThat(table1).isNull()

    view.showTableView()

    val table2 = TreeWalker(view.component).descendants().filterIsInstance<JTable>().firstOrNull()
    val messagePanel2 =
      TreeWalker(view.component).descendants().firstOrNull { it.name == "message-panel" }

    assertThat(messagePanel2).isNull()
    assertThat(table2).isNotNull()
  }

  fun testShowMessagePanel() {
    view.showTableView()

    val table1 = TreeWalker(view.component).descendants().filterIsInstance<JTable>().firstOrNull()
    val messagePanel1 =
      TreeWalker(view.component).descendants().firstOrNull { it.name == "message-panel" }

    assertThat(messagePanel1).isNull()
    assertThat(table1).isNotNull()

    view.showMessagePanel("message")

    val table2 = TreeWalker(view.component).descendants().filterIsInstance<JTable>().firstOrNull()
    val messagePanel2 =
      TreeWalker(view.component).descendants().first { it.name == "message-panel" }

    assertThat(messagePanel2).isNotNull()
    assertThat(table2).isNull()
  }

  private fun sqliteEvaluatorController(
    model: DatabaseInspectorModel = OpenDatabaseInspectorModel(),
    repository: DatabaseRepositoryImpl =
      DatabaseRepositoryImpl(project, EdtExecutorService.getInstance()),
  ): SqliteEvaluatorController {
    return SqliteEvaluatorController(
        project,
        model,
        repository,
        initialDatabaseId = null,
        view,
        {},
        {},
        {},
        EdtExecutorService.getInstance(),
        EdtExecutorService.getInstance(),
      )
      .also { Disposer.register(testRootDisposable, it) }
  }

  private fun createAdHocSqliteDatabase(): VirtualFile {
    return sqliteUtil.createAdHocSqliteDatabase(
      createStatement = "CREATE TABLE t1 (c1 INT)",
      insertStatement = "INSERT INTO t1 (c1) VALUES (42)",
    )
  }
}
