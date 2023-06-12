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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.controllers.SqliteEvaluatorController
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
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
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.android.tools.idea.sqlite.utils.getJdbcDatabaseConnection
import com.android.tools.idea.testing.runDispatching
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
import junit.framework.TestCase
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class SqliteEvaluatorViewImplTest : LightPlatformTestCase() {
  private lateinit var view: SqliteEvaluatorViewImpl
  private lateinit var mockSchemaProvider: SchemaProvider

  private lateinit var sqliteUtil: SqliteTestUtil
  private var realDatabaseConnection: DatabaseConnection? = null

  private var dropPsiCachesCallCounter = 0

  override fun setUp() {
    super.setUp()
    mockSchemaProvider = mock(SchemaProvider::class.java)
    whenever(mockSchemaProvider.getSchema(any(SqliteDatabaseId::class.java)))
      .thenReturn(SqliteSchema(emptyList()))

    dropPsiCachesCallCounter = 0
    view =
      SqliteEvaluatorViewImpl(project, TableViewImpl(), mockSchemaProvider) {
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
    assertEquals(-1, comboBox.selectedIndex)

    val databaseId1 = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db1")))
    val databaseId2 = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db2")))

    view.setDatabases(listOf(databaseId1, databaseId2), databaseId1)
    assertEquals(0, comboBox.selectedIndex)

    view.setDatabases(emptyList(), null)
    assertEquals(-1, comboBox.selectedIndex)
  }

  fun testActiveDatabaseRemainsActiveWhenNewDbsAreAdded() {
    // Prepare
    val model = OpenDatabaseInspectorModel()
    val evaluatorController =
      sqliteEvaluatorController(
        model,
        DatabaseRepositoryImpl(project, EdtExecutorService.getInstance())
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
    assertEquals(null, activeDatabaseId)

    model.addDatabaseSchema(db2, SqliteSchema(emptyList()))
    assertEquals(db2, activeDatabaseId)

    model.addDatabaseSchema(db1, SqliteSchema(emptyList()))
    assertEquals(db2, activeDatabaseId)

    model.addDatabaseSchema(db0, SqliteSchema(emptyList()))
    assertEquals(db2, activeDatabaseId)

    model.removeDatabaseSchema(db2)
    assertEquals(db0, activeDatabaseId)
  }

  fun testPsiCacheIsDroppedWhenNewDatabaseIsSelected() {
    // Prepare
    val comboBox = TreeWalker(view.component).descendants().filterIsInstance<JComboBox<*>>().first()

    val database1 = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db1")))
    val database2 = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db2")))

    // Act/Assert
    view.setDatabases(listOf(database1, database2), database1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertEquals(1, dropPsiCachesCallCounter)

    view.setDatabases(listOf(database1, database2), database2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertEquals(3, dropPsiCachesCallCounter)

    comboBox.selectedIndex = 0
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertEquals(4, dropPsiCachesCallCounter)
  }

  fun testSchemaUpdatedDropsCachesAndGetsNewSchema() {
    // Prepare
    val database = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db1")))

    view.setDatabases(listOf(database), database)

    // Act
    view.schemaChanged(database)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertEquals(2, dropPsiCachesCallCounter)
    verify(mockSchemaProvider, times(2)).getSchema(database)
  }

  fun testRefreshButtonIsDisabledByDefault() {
    // Prepare
    val refreshButton =
      TreeWalker(view.tableView.component).descendants().first { it.name == "refresh-button" }

    val evaluatorController =
      sqliteEvaluatorController(
        OpenDatabaseInspectorModel(),
        DatabaseRepositoryImpl(project, EdtExecutorService.getInstance())
      )

    // Act
    evaluatorController.setUp()

    // Assert
    assertFalse(refreshButton.isEnabled)
  }

  fun testMultipleStatementAreRun() {
    // Prepare
    val sqliteFile = createAdHocSqliteDatabase()
    realDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          sqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
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
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1")
      )
    )

    // Assert
    val table = TreeWalker(view.component).descendants().filterIsInstance<JTable>().first()
    assertEquals(2, table.model.columnCount)
    assertEquals("c1", table.model.getColumnName(1))
    assertEquals(1, table.model.rowCount)
    assertEquals("42", table.model.getValueAt(0, 1))

    // Act
    pumpEventsAndWaitForFuture(
      controller.showAndExecuteSqlStatement(
        database,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1")
      )
    )

    // Assert
    assertEquals(2, table.model.columnCount)
    assertEquals("c1", table.model.getColumnName(1))
    assertEquals(1, table.model.rowCount)
    assertEquals("42", table.model.getValueAt(0, 1))
  }

  fun testEnableRunSqliteStatementsEnablesRunButton() {
    // Prepare
    val runButton = TreeWalker(view.component).descendants().first { it.name == "run-button" }

    // Act
    view.setRunSqliteStatementEnabled(true)

    // Assert
    TestCase.assertTrue(runButton.isEnabled)
  }

  fun testDisableRunSqliteStatementsDisablesRunButton() {
    // Prepare
    val runButton = TreeWalker(view.component).descendants().first { it.name == "run-button" }
    view.setRunSqliteStatementEnabled(true)

    // Act
    view.setRunSqliteStatementEnabled(false)

    // Assert
    assertFalse(runButton.isEnabled)
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
    assertEquals(listOf("test1", "test2"), invocations)
  }

  fun testTableIsEmptyWhenDbIsClosed() {
    // Prepare
    val sqliteFile = createAdHocSqliteDatabase()
    realDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          sqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
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
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1")
      )
    )
    val table = TreeWalker(view.component).descendants().filterIsInstance<JTable>().first()

    // check before that table isn't empty
    assertEquals(1, table.model.rowCount)

    // Act1
    model.removeDatabaseSchema(unrelated)

    // Assert that nothing changed
    assertEquals(1, table.model.rowCount)

    // Act2
    model.removeDatabaseSchema(database)

    // Assert that now it is empty
    assertEquals(0, table.model.rowCount)
  }

  fun testShowTableView() {
    val controller = sqliteEvaluatorController()
    controller.setUp()

    val table1 = TreeWalker(view.component).descendants().filterIsInstance<JTable>().firstOrNull()
    val messagePanel1 =
      TreeWalker(view.component).descendants().first { it.name == "message-panel" }

    assertNotNull(messagePanel1)
    assertNull(table1)

    view.showTableView()

    val table2 = TreeWalker(view.component).descendants().filterIsInstance<JTable>().firstOrNull()
    val messagePanel2 =
      TreeWalker(view.component).descendants().firstOrNull { it.name == "message-panel" }

    assertNull(messagePanel2)
    assertNotNull(table2)
  }

  fun testShowMessagePanel() {
    view.showTableView()

    val table1 = TreeWalker(view.component).descendants().filterIsInstance<JTable>().firstOrNull()
    val messagePanel1 =
      TreeWalker(view.component).descendants().firstOrNull { it.name == "message-panel" }

    assertNull(messagePanel1)
    assertNotNull(table1)

    view.showMessagePanel("message")

    val table2 = TreeWalker(view.component).descendants().filterIsInstance<JTable>().firstOrNull()
    val messagePanel2 =
      TreeWalker(view.component).descendants().first { it.name == "message-panel" }

    assertNotNull(messagePanel2)
    assertNull(table2)
  }

  private fun sqliteEvaluatorController(
    model: DatabaseInspectorModel = OpenDatabaseInspectorModel(),
    repository: DatabaseRepositoryImpl =
      DatabaseRepositoryImpl(project, EdtExecutorService.getInstance())
  ): SqliteEvaluatorController {
    return SqliteEvaluatorController(
        project,
        model,
        repository,
        view,
        {},
        {},
        {},
        EdtExecutorService.getInstance(),
        EdtExecutorService.getInstance()
      )
      .also { Disposer.register(testRootDisposable, it) }
  }

  private fun createAdHocSqliteDatabase(): VirtualFile {
    return sqliteUtil.createAdHocSqliteDatabase(
      createStatement = "CREATE TABLE t1 (c1 INT)",
      insertStatement = "INSERT INTO t1 (c1) VALUES (42)"
    )
  }
}
